#!/usr/bin/env bash
set -Eeuo pipefail
umask 077

CONFIG="/www/wwwroot/digital-doctor-hy3.yml"
STAGE="/root/codex-feibo-ai-model-avatars/20260825-113500"
KIMI_IMAGE="$STAGE/kimi-k3.png"
QIANFAN_IMAGE="$STAGE/baidu-qianfan.png"
PYTHON3="/www/server/panel/pyenv/bin/python3.7"

EXPECTED_KIMI_SHA="89f73365b057d440e14351fe8a6db041ab1dd5a99079c8e6beb2a24dee313e0e"
EXPECTED_QIANFAN_SHA="b5e4456d1ce9700f3341001ede53e80b2df812e055521bada8021f814525468b"
KIMI_OBJECT="ai-model-logo/provider-logos-20260825/kimi-k3.png"
QIANFAN_OBJECT="ai-model-logo/provider-logos-20260825/baidu-qianfan.png"
KIMI_URL="https://digital-doctor-oss.oss-cn-hangzhou.aliyuncs.com/$KIMI_OBJECT"
QIANFAN_URL="https://digital-doctor-oss.oss-cn-hangzhou.aliyuncs.com/$QIANFAN_OBJECT"
MODEL_IDS="9005300000000000000,9005300000000000001,9005400000000000000,9005400000000000001,9005400000000000002,9005400000000000003"

WORK=""
BACKUP=""
UPLOAD_STARTED=0
DB_MUTATED=0
DEPLOY_OK=0
MYSQL=()

die() { echo "AVATAR_DEPLOY_ERROR: $*" >&2; exit 1; }
file_sha() { sha256sum "$1" | awk '{print $1}'; }

rollback() {
  local failed=0
  echo "Avatar deployment failed; restoring model image fields." >&2
  if [[ "$DB_MUTATED" == 1 && -s "$WORK/rollback-images.sql" ]]; then
    "${MYSQL[@]}" "$DB_NAME" < "$WORK/rollback-images.sql" || failed=1
  fi
  if [[ "$UPLOAD_STARTED" == 1 && -s "$WORK/oss-config.json" ]]; then
    "$PYTHON3" - "$WORK/oss-config.json" "$KIMI_OBJECT" "$QIANFAN_OBJECT" <<'PY' || failed=1
import json
import oss2
import sys
with open(sys.argv[1], encoding="utf-8") as handle:
    config = json.load(handle)
bucket = oss2.Bucket(oss2.Auth(config["accessKeyId"], config["accessKeySecret"]), config["endpoint"], config["bucketName"])
for object_name in sys.argv[2:]:
    bucket.delete_object(object_name)
PY
  fi
  if [[ "$failed" == 0 ]]; then echo "AVATAR_ROLLBACK_OK" >&2; else echo "AVATAR_ROLLBACK_INCOMPLETE" >&2; fi
}

on_exit() {
  local status=$?
  trap - EXIT
  if [[ "$status" != 0 && "$DEPLOY_OK" != 1 ]]; then
    set +e
    rollback
    set -e
  fi
  rm -f -- "$KIMI_IMAGE" "$QIANFAN_IMAGE"
  if [[ -n "$WORK" ]]; then
    case "$WORK" in "$STAGE"/work.*) rm -rf -- "$WORK" ;; esac
  fi
  exit "$status"
}
trap on_exit EXIT

[[ "$EUID" == 0 ]] || die "must run as root"
exec 9>/var/lock/feibo-ai-model-avatar-deploy.lock
flock -n 9 || die "another avatar deployment is running"
[[ -x "$PYTHON3" && ! -L "$PYTHON3" ]] || die "trusted Python runtime unavailable"
[[ -s "$CONFIG" && ! -L "$CONFIG" && "$(stat -Lc '%U:%G:%a' "$CONFIG")" == "root:root:600" ]] || die "unsafe production config"
[[ -s "$KIMI_IMAGE" && ! -L "$KIMI_IMAGE" && "$(file_sha "$KIMI_IMAGE")" == "$EXPECTED_KIMI_SHA" ]] || die "Kimi image checksum mismatch"
[[ -s "$QIANFAN_IMAGE" && ! -L "$QIANFAN_IMAGE" && "$(file_sha "$QIANFAN_IMAGE")" == "$EXPECTED_QIANFAN_SHA" ]] || die "Qianfan image checksum mismatch"

WORK="$(mktemp -d "$STAGE/work.XXXXXX")"
"$PYTHON3" - "$CONFIG" "$WORK/mysql-client.cnf" "$WORK/database-name" "$WORK/oss-config.json" <<'PY'
import json
import os
import re
import sys
config_path, option_path, database_path, oss_path = sys.argv[1:]
db = {}
oss = {}
inside_datasource = False
with open(config_path, encoding="utf-8") as handle:
    for raw in handle:
        if re.match(r"^  datasource:\s*$", raw):
            inside_datasource = True
            continue
        if inside_datasource and re.match(r"^  \S", raw):
            inside_datasource = False
        if inside_datasource:
            match = re.match(r"^    (url|username|password):\s*(.*?)\s*$", raw)
            if match:
                value = match.group(2)
                if len(value) >= 2 and value[0] == value[-1] and value[0] in "\"'":
                    value = value[1:-1]
                db[match.group(1)] = value
        match = re.match(r"^    (endpoint|bucketName|baseUrl|accessKeyId|accessKeySecret):\s*(.*?)\s*$", raw)
        if match:
            value = match.group(2)
            if len(value) >= 2 and value[0] == value[-1] and value[0] in "\"'":
                value = value[1:-1]
            if match.group(1) in {"bucketName", "baseUrl", "accessKeyId", "accessKeySecret"} or value.startswith("oss-"):
                oss[match.group(1)] = value
db_match = re.match(r"^jdbc:mysql://([^/:?]+)(?::(\d+))?/([^?]+)", db.get("url", ""))
required_oss = {"endpoint", "bucketName", "baseUrl", "accessKeyId", "accessKeySecret"}
if not db_match or not db.get("username") or not db.get("password"):
    raise SystemExit("datasource configuration unavailable")
if required_oss - set(oss):
    raise SystemExit("OSS configuration unavailable")
host, port, database = db_match.group(1), db_match.group(2) or "3306", db_match.group(3)
def quote(value):
    return '"' + value.replace('\\', '\\\\').replace('"', '\\"') + '"'
with open(option_path, "w", encoding="utf-8") as handle:
    handle.write("[client]\n")
    handle.write("host=%s\nport=%s\nuser=%s\npassword=%s\ndefault-character-set=utf8mb4\n" %
                 tuple(quote(value) for value in (host, port, db["username"], db["password"])))
os.chmod(option_path, 0o600)
with open(database_path, "w", encoding="ascii") as handle:
    handle.write(database)
with open(oss_path, "w", encoding="utf-8") as handle:
    json.dump(oss, handle)
os.chmod(oss_path, 0o600)
PY
DB_NAME="$(cat "$WORK/database-name")"
[[ "$DB_NAME" =~ ^[A-Za-z0-9_$-]+$ ]] || die "invalid database name"
MYSQL=(mysql --defaults-extra-file="$WORK/mysql-client.cnf" --default-character-set=utf8mb4)

"${MYSQL[@]}" -N -B "$DB_NAME" -e "SELECT ai_model_config_id, TO_BASE64(COALESCE(image,'')), image IS NULL FROM t_ai_model_config WHERE ai_model_config_id IN ($MODEL_IDS) ORDER BY ai_model_config_id;" > "$WORK/images.before.tsv"
[[ "$(wc -l < "$WORK/images.before.tsv")" == 6 ]] || die "expected model rows are missing"
"$PYTHON3" - "$WORK/images.before.tsv" "$WORK/rollback-images.sql" <<'PY'
import base64
import sys
source, target = sys.argv[1:]
rows = []
with open(source, encoding="utf-8") as handle:
    for line in handle:
        model_id, encoded, was_null = line.rstrip("\n").split("\t")
        if not model_id.isdigit() or was_null not in {"0", "1"}:
            raise SystemExit("invalid image backup row")
        value = base64.b64decode(encoded).decode("utf-8") if encoded else ""
        rows.append((model_id, value, was_null == "1"))
with open(target, "w", encoding="utf-8") as handle:
    handle.write("START TRANSACTION;\n")
    for model_id, value, was_null in rows:
        expression = "NULL" if was_null else "'" + value.replace("'", "''") + "'"
        handle.write("UPDATE t_ai_model_config SET image=%s, update_time=NOW() WHERE ai_model_config_id=%s;\n" % (expression, model_id))
    handle.write("COMMIT;\n")
PY

STAMP="$(date +%Y%m%d-%H%M%S)"
BACKUP="/www/wwwroot/deploy-backups/ai-model-avatars-$STAMP"
install -d -m 700 -o root -g root "$BACKUP"
cp -p "$WORK/images.before.tsv" "$BACKUP/images.before.tsv"
cp -p "$WORK/rollback-images.sql" "$BACKUP/rollback-images.sql"
sha256sum "$KIMI_IMAGE" "$QIANFAN_IMAGE" > "$BACKUP/source-images.sha256"

UPLOAD_STARTED=1
"$PYTHON3" - "$WORK/oss-config.json" "$KIMI_IMAGE" "$KIMI_OBJECT" "$QIANFAN_IMAGE" "$QIANFAN_OBJECT" <<'PY'
import hashlib
import json
import oss2
import sys
config_path, kimi_path, kimi_object, qianfan_path, qianfan_object = sys.argv[1:]
with open(config_path, encoding="utf-8") as handle:
    config = json.load(handle)
bucket = oss2.Bucket(oss2.Auth(config["accessKeyId"], config["accessKeySecret"]), config["endpoint"], config["bucketName"])
headers = {"Content-Type": "image/png", "Cache-Control": "public, max-age=31536000, immutable"}
for local_path, object_name in ((kimi_path, kimi_object), (qianfan_path, qianfan_object)):
    result = bucket.put_object_from_file(object_name, local_path, headers=headers)
    if result.status != 200:
        raise SystemExit("OSS upload failed")
    metadata = bucket.head_object(object_name)
    if metadata.status != 200 or metadata.content_length <= 0 or metadata.content_type != "image/png":
        raise SystemExit("OSS object verification failed")
PY

KIMI_HTTP="$(curl -sS -o /dev/null -w '%{http_code}' --max-time 15 "$KIMI_URL")"
QIANFAN_HTTP="$(curl -sS -o /dev/null -w '%{http_code}' --max-time 15 "$QIANFAN_URL")"
[[ "$KIMI_HTTP" == 200 && "$QIANFAN_HTTP" == 200 ]] || die "public OSS image check failed"

DB_MUTATED=1
"${MYSQL[@]}" "$DB_NAME" <<SQL
START TRANSACTION;
UPDATE t_ai_model_config
SET image = '$KIMI_URL', update_time = NOW()
WHERE ai_model_config_id IN (9005300000000000000,9005300000000000001);
UPDATE t_ai_model_config
SET image = '$QIANFAN_URL', update_time = NOW()
WHERE ai_model_config_id IN (9005400000000000000,9005400000000000001,9005400000000000002,9005400000000000003);
COMMIT;
SQL

DB_CHECK="$("${MYSQL[@]}" -N -B "$DB_NAME" -e "SELECT CONCAT_WS('|', SUM(image='$KIMI_URL'), SUM(image='$QIANFAN_URL'), COUNT(*)) FROM t_ai_model_config WHERE ai_model_config_id IN ($MODEL_IDS);")"
[[ "$DB_CHECK" == "2|4|6" ]] || die "database avatar verification failed"
"${MYSQL[@]}" -N -B "$DB_NAME" -e "SELECT ai_model_config_id, image FROM t_ai_model_config WHERE ai_model_config_id IN ($MODEL_IDS) ORDER BY ai_model_config_id;" > "$BACKUP/images.after.tsv"

DEPLOY_OK=1
printf 'AI_MODEL_AVATARS_DEPLOY_OK\nBACKUP=%s\nKIMI_HTTP=%s\nQIANFAN_HTTP=%s\nDB_CHECK=%s\nKIMI_URL=%s\nQIANFAN_URL=%s\nSTAGED_IMAGES_REMOVED=true\n' \
  "$BACKUP" "$KIMI_HTTP" "$QIANFAN_HTTP" "$DB_CHECK" "$KIMI_URL" "$QIANFAN_URL"

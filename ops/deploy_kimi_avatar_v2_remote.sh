#!/usr/bin/env bash
set -Eeuo pipefail
umask 077

CONFIG="/www/wwwroot/digital-doctor-hy3.yml"
STAGE="/root/codex-feibo-kimi-avatar-v2/20260825-180000"
IMAGE="$STAGE/kimi-k3-v2.png"
PYTHON3="/www/server/panel/pyenv/bin/python3.7"
EXPECTED_SHA="64ad19c76c210d9ef25a0ae4a608c8f925d5508b2cfb648090381a94fcc98124"
OBJECT_NAME="ai-model-logo/provider-logos-20260825/kimi-k3-v2.png"
PUBLIC_URL="https://digital-doctor-oss.oss-cn-hangzhou.aliyuncs.com/$OBJECT_NAME"
MODEL_IDS="9005300000000000000,9005300000000000001"

WORK=""
BACKUP=""
UPLOADED=0
DB_MUTATED=0
DEPLOY_OK=0
MYSQL=()

die() { echo "KIMI_AVATAR_ERROR: $*" >&2; exit 1; }
file_sha() { sha256sum "$1" | awk '{print $1}'; }

rollback() {
  local failed=0
  if [[ "$DB_MUTATED" == 1 && -s "$WORK/rollback.sql" ]]; then
    "${MYSQL[@]}" "$DB_NAME" < "$WORK/rollback.sql" || failed=1
  fi
  if [[ "$UPLOADED" == 1 && -s "$WORK/oss.json" ]]; then
    "$PYTHON3" - "$WORK/oss.json" "$OBJECT_NAME" <<'PY' || failed=1
import json
import oss2
import sys
with open(sys.argv[1], encoding="utf-8") as handle:
    config = json.load(handle)
bucket = oss2.Bucket(oss2.Auth(config["accessKeyId"], config["accessKeySecret"]), config["endpoint"], config["bucketName"])
bucket.delete_object(sys.argv[2])
PY
  fi
  [[ "$failed" == 0 ]] && echo "KIMI_AVATAR_ROLLBACK_OK" >&2 || echo "KIMI_AVATAR_ROLLBACK_INCOMPLETE" >&2
}

on_exit() {
  local status=$?
  trap - EXIT
  if [[ "$status" != 0 && "$DEPLOY_OK" != 1 ]]; then
    set +e
    rollback
    set -e
  fi
  rm -f -- "$IMAGE"
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
[[ -s "$IMAGE" && ! -L "$IMAGE" && "$(file_sha "$IMAGE")" == "$EXPECTED_SHA" ]] || die "image checksum mismatch"

WORK="$(mktemp -d "$STAGE/work.XXXXXX")"
"$PYTHON3" - "$CONFIG" "$WORK/mysql.cnf" "$WORK/database" "$WORK/oss.json" <<'PY'
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
            if match.group(1) != "endpoint" or value.startswith("oss-"):
                oss[match.group(1)] = value
db_match = re.match(r"^jdbc:mysql://([^/:?]+)(?::(\d+))?/([^?]+)", db.get("url", ""))
if not db_match or not db.get("username") or not db.get("password"):
    raise SystemExit("datasource configuration unavailable")
if {"endpoint", "bucketName", "accessKeyId", "accessKeySecret"} - set(oss):
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
DB_NAME="$(cat "$WORK/database")"
[[ "$DB_NAME" =~ ^[A-Za-z0-9_$-]+$ ]] || die "invalid database name"
MYSQL=(mysql --defaults-extra-file="$WORK/mysql.cnf" --default-character-set=utf8mb4)

"${MYSQL[@]}" -N -B "$DB_NAME" -e "SELECT ai_model_config_id, HEX(COALESCE(image,'')), image IS NULL FROM t_ai_model_config WHERE ai_model_config_id IN ($MODEL_IDS) ORDER BY ai_model_config_id;" > "$WORK/before.tsv"
[[ "$(wc -l < "$WORK/before.tsv")" == 2 ]] || die "Kimi model rows are missing"
"$PYTHON3" - "$WORK/before.tsv" "$WORK/rollback.sql" <<'PY'
import binascii
import sys
source, target = sys.argv[1:]
rows = []
with open(source, encoding="utf-8") as handle:
    for line in handle:
        model_id, encoded, was_null = line.rstrip("\n").split("\t")
        value = binascii.unhexlify(encoded).decode("utf-8") if encoded else ""
        rows.append((model_id, value, was_null == "1"))
with open(target, "w", encoding="utf-8") as handle:
    handle.write("START TRANSACTION;\n")
    for model_id, value, was_null in rows:
        expression = "NULL" if was_null else "'" + value.replace("'", "''") + "'"
        handle.write("UPDATE t_ai_model_config SET image=%s, update_time=NOW() WHERE ai_model_config_id=%s;\n" % (expression, model_id))
    handle.write("COMMIT;\n")
PY

STAMP="$(date +%Y%m%d-%H%M%S)"
BACKUP="/www/wwwroot/deploy-backups/kimi-avatar-v2-$STAMP"
install -d -m 700 -o root -g root "$BACKUP"
cp -p "$WORK/before.tsv" "$BACKUP/images.before.tsv"
cp -p "$WORK/rollback.sql" "$BACKUP/rollback.sql"
sha256sum "$IMAGE" > "$BACKUP/source-image.sha256"

UPLOADED=1
"$PYTHON3" - "$WORK/oss.json" "$IMAGE" "$OBJECT_NAME" <<'PY'
import json
import oss2
import sys
with open(sys.argv[1], encoding="utf-8") as handle:
    config = json.load(handle)
bucket = oss2.Bucket(oss2.Auth(config["accessKeyId"], config["accessKeySecret"]), config["endpoint"], config["bucketName"])
result = bucket.put_object_from_file(sys.argv[3], sys.argv[2], headers={
    "Content-Type": "image/png",
    "Cache-Control": "public, max-age=31536000, immutable",
})
if result.status != 200:
    raise SystemExit("OSS upload failed")
metadata = bucket.head_object(sys.argv[3])
if metadata.status != 200 or metadata.content_length <= 0 or metadata.content_type != "image/png":
    raise SystemExit("OSS object verification failed")
PY

HTTP="$(curl -sS -o /dev/null -w '%{http_code}' --max-time 15 "$PUBLIC_URL")"
[[ "$HTTP" == 200 ]] || die "public image check failed"

DB_MUTATED=1
"${MYSQL[@]}" "$DB_NAME" -e "UPDATE t_ai_model_config SET image='$PUBLIC_URL', update_time=NOW() WHERE ai_model_config_id IN ($MODEL_IDS);"
DB_CHECK="$("${MYSQL[@]}" -N -B "$DB_NAME" -e "SELECT CONCAT_WS('|', SUM(image='$PUBLIC_URL'), COUNT(*)) FROM t_ai_model_config WHERE ai_model_config_id IN ($MODEL_IDS);")"
[[ "$DB_CHECK" == "2|2" ]] || die "database verification failed"
"${MYSQL[@]}" -N -B "$DB_NAME" -e "SELECT ai_model_config_id, image FROM t_ai_model_config WHERE ai_model_config_id IN ($MODEL_IDS) ORDER BY ai_model_config_id;" > "$BACKUP/images.after.tsv"

DEPLOY_OK=1
printf 'KIMI_AVATAR_V2_DEPLOY_OK\nBACKUP=%s\nHTTP=%s\nDB_CHECK=%s\nPUBLIC_URL=%s\nSTAGED_IMAGE_REMOVED=true\n' \
  "$BACKUP" "$HTTP" "$DB_CHECK" "$PUBLIC_URL"

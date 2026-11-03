#!/usr/bin/env bash
set -Eeuo pipefail
umask 077

APP="/www/wwwroot/digital-doctor-app.jar"
CONFIG="/www/wwwroot/digital-doctor-hy3.yml"
PYTHON3="/www/server/panel/pyenv/bin/python3.7"
MODEL_ID="9005300000000000001"

die() {
  echo "KIMI_QUOTA_FIX_ERROR=$*" >&2
  exit 1
}

[[ -r "$APP" && -r "$CONFIG" ]] || die "application_or_config_unavailable"
[[ -x "$PYTHON3" ]] || PYTHON3="$(command -v python3 || true)"
[[ -n "$PYTHON3" && -x "$PYTHON3" ]] || die "python3_unavailable"

PID="$(pgrep -f 'java .*digital-doctor-app\.jar' | head -n 1 || true)"
[[ "$PID" =~ ^[0-9]+$ && -r "/proc/$PID/environ" ]] || die "application_process_unavailable"

TEMP_DIR="$(mktemp -d /tmp/fix-kimi-platform-quota.XXXXXX)"
cleanup() {
  case "${TEMP_DIR:-}" in
    /tmp/fix-kimi-platform-quota.*) rm -rf -- "$TEMP_DIR" ;;
  esac
}
trap cleanup EXIT

"$PYTHON3" - "$CONFIG" "/proc/$PID/environ" "$TEMP_DIR/mysql-client.cnf" "$TEMP_DIR/database-name" <<'PY'
import os
import re
import sys

config_path, environ_path, option_path, database_path = sys.argv[1:]
with open(environ_path, "rb") as handle:
    runtime_env = {}
    for item in handle.read().split(b"\0"):
        if b"=" in item:
            key, value = item.split(b"=", 1)
            runtime_env[key.decode("utf-8", "replace")] = value.decode("utf-8", "replace")

values = {}
inside = False
with open(config_path, encoding="utf-8") as handle:
    for raw in handle:
        if re.match(r"^  datasource:\s*$", raw):
            inside = True
            continue
        if inside and re.match(r"^  \S", raw):
            break
        if not inside:
            continue
        match = re.match(r"^    (url|username|password):\s*(.*?)\s*$", raw)
        if match:
            value = match.group(2)
            if len(value) >= 2 and value[0] == value[-1] and value[0] in "\"'":
                value = value[1:-1]
            values[match.group(1)] = value

url = runtime_env.get("SPRING_DATASOURCE_URL") or values.get("url", "")
username = runtime_env.get("SPRING_DATASOURCE_USERNAME") or values.get("username", "")
password = runtime_env.get("SPRING_DATASOURCE_PASSWORD") or values.get("password", "")
match = re.match(r"^jdbc:mysql://([^/:?]+)(?::(\d+))?/([^?]+)", url)
if not match or not username or not password:
    raise SystemExit("datasource_configuration_unavailable")
host, port, database = match.group(1), match.group(2) or "3306", match.group(3)

def quote(value):
    return '"' + value.replace("\\", "\\\\").replace('"', '\\"') + '"'

with open(option_path, "w", encoding="utf-8") as handle:
    handle.write("[client]\n")
    handle.write("host=%s\n" % quote(host))
    handle.write("port=%s\n" % quote(port))
    handle.write("user=%s\n" % quote(username))
    handle.write("password=%s\n" % quote(password))
    handle.write("default-character-set=utf8mb4\n")
os.chmod(option_path, 0o600)
with open(database_path, "w", encoding="ascii") as handle:
    handle.write(database)
os.chmod(database_path, 0o600)
PY

DB_NAME="$(cat "$TEMP_DIR/database-name")"
[[ "$DB_NAME" =~ ^[A-Za-z0-9_\$-]+$ ]] || die "database_name_invalid"
MYSQL=(mysql --defaults-extra-file="$TEMP_DIR/mysql-client.cnf" --batch --skip-column-names --raw "$DB_NAME")
MYSQLDUMP=(mysqldump --defaults-extra-file="$TEMP_DIR/mysql-client.cnf" --single-transaction --skip-lock-tables "$DB_NAME")

exec 9>/var/lock/feibo-backend-deploy.lock
flock -n 9 || die "another_backend_deployment_is_running"
exec 8>/var/lock/feibo-kimi-quota-fix.lock
flock -n 8 || die "another_kimi_quota_fix_is_running"

STAMP="$(date +%Y%m%d-%H%M%S)"
BACKUP="/www/wwwroot/deploy-backups/kimi-platform-quota-fix-$STAMP"
install -d -m 700 -o root -g root "$BACKUP"
"${MYSQLDUMP[@]}" t_ai_model_config > "$BACKUP/t_ai_model_config.before.sql"
[[ -s "$BACKUP/t_ai_model_config.before.sql" ]] || die "database_backup_empty"

"${MYSQL[@]}" -e "
SELECT CONCAT_WS('\t', id, ai_model_config_id, name, description, type,
                 model_code, visible, is_deleted,
                 DATE_FORMAT(update_time, '%Y-%m-%d %H:%i:%s'))
FROM t_ai_model_config
WHERE ai_model_config_id = $MODEL_ID;
" > "$BACKUP/kimi.before.tsv"

PRECONDITION="$("${MYSQL[@]}" -e "
SELECT COUNT(*)
FROM t_ai_model_config
WHERE ai_model_config_id = $MODEL_ID
  AND is_leaf = 1
  AND name = 'kimi-k3'
  AND model_code = 'kimik3'
  AND type = 1
  AND COALESCE(visible, 1) = 1
  AND COALESCE(is_deleted, 0) = 0;
")"
[[ "$PRECONDITION" == "1" ]] || die "model_precondition_failed"

AFFECTED="$("${MYSQL[@]}" -e "
START TRANSACTION;
UPDATE t_ai_model_config
SET type = 0, update_time = NOW()
WHERE ai_model_config_id = $MODEL_ID
  AND is_leaf = 1
  AND name = 'kimi-k3'
  AND model_code = 'kimik3'
  AND type = 1
  AND COALESCE(visible, 1) = 1
  AND COALESCE(is_deleted, 0) = 0;
SELECT ROW_COUNT();
COMMIT;
")"
[[ "$AFFECTED" == "1" ]] || die "unexpected_affected_rows_$AFFECTED"

"${MYSQL[@]}" -e "
SELECT CONCAT_WS('\t', id, ai_model_config_id, name, description, type,
                 model_code, visible, is_deleted,
                 DATE_FORMAT(update_time, '%Y-%m-%d %H:%i:%s'))
FROM t_ai_model_config
WHERE ai_model_config_id = $MODEL_ID;
" > "$BACKUP/kimi.after.tsv"

VERIFY="$("${MYSQL[@]}" -e "
SELECT COUNT(*)
FROM t_ai_model_config
WHERE ai_model_config_id = $MODEL_ID
  AND name = 'kimi-k3'
  AND model_code = 'kimik3'
  AND type = 0
  AND COALESCE(visible, 1) = 1
  AND COALESCE(is_deleted, 0) = 0;
")"
[[ "$VERIFY" == "1" ]] || die "post_update_verification_failed"

sha256sum "$BACKUP/t_ai_model_config.before.sql" \
          "$BACKUP/kimi.before.tsv" \
          "$BACKUP/kimi.after.tsv" > "$BACKUP/SHA256SUMS"

echo "KIMI_QUOTA_FIX_OK"
echo "APP_PID=$PID"
echo "MODEL_ID=$MODEL_ID"
echo "AFFECTED_ROWS=$AFFECTED"
echo "MODEL_TYPE_BEFORE=1"
echo "MODEL_TYPE_AFTER=0"
echo "BACKUP=$BACKUP"

#!/usr/bin/env bash
set -Eeuo pipefail
umask 077

APP="/www/wwwroot/digital-doctor-app.jar"
CONFIG="/www/wwwroot/digital-doctor-hy3.yml"
LOG="/www/wwwroot/digital-doctor-app.szdoctor.out"
STAGE="/root/codex-feibo-qianfan/20260825-110500"
CANDIDATE="$STAGE/digital-doctor-app.qianfan.candidate.jar"
MIGRATION="$STAGE/add_baidu_qianfan_models.sql"
MANAGE_ARCHIVE="$STAGE/manage-qianfan.tar.gz"
KEY_FILE="$STAGE/qianfan.env"
KEY_ENCRYPTOR="$STAGE/QianfanKeyEncryptor.java"
FRONTEND_HELPER="$STAGE/deploy_feibo_frontends_remote.sh"
BT_GUARD="$STAGE/bt_java_project_guard.py"
RELEASE="20260825-110500-qianfan"
HEALTH_URL="http://127.0.0.1:8091/api/v1/ai/model/index"
PYTHON3="/www/server/panel/pyenv/bin/python3.7"

EXPECTED_OLD_APP_SHA="ee234e706633190b7e79d515f74a88a2833e3634c5d135f65146b4297dc9df47"
EXPECTED_NEW_APP_SHA="163254c4bd7ddc4153d03054e97021469c5e95f6e7f59f7b4d8082b57aff2166"
EXPECTED_MIGRATION_SHA="b5e0163b11f3760899df6cde8914408ac25fdfd97661753c1f27c7fc0d5f437d"
EXPECTED_MANAGE_SHA="a558d07917ac117f229b9383aff82dfdd0fd730f8a6e4e67080cdf5f39c038ec"
EXPECTED_ENCRYPTOR_SHA="da3eaf4e3f7192fcebccda8e4b924ce5916524340b2c1dcc90731f657ba3ca83"
EXPECTED_FRONTEND_HELPER_SHA="4e01aaa55875e185a3f6bc84be7ef066e64043922c6069a6eb1edbb9474b3c19"
EXPECTED_BT_GUARD_SHA="9a3f0cfbe9ec005d0a43ed19126605c85f34c53b3758e63b4455d4dace27c383"

GENERAL_ROOT_BIZ_ID="1970339278848659457"
SZDOCTOR_PROVIDER_BIZ_ID="9002500000000000100"
QIANFAN_PROVIDER_BIZ_ID="9005400000000000000"
QIANFAN_MODEL_IDS=("9005400000000000001" "9005400000000000002" "9005400000000000003")
QIANFAN_MODEL_NAMES=("ernie-5.1" "ernie-5.0" "ernie-4.5-turbo-128k")

WORK=""
BACKUP=""
OLD_PID=""
NEW_PID=""
PROC_CWD=""
OLD_DD=""
OLD_MANAGE=""
MUTATION_STARTED=0
DB_MUTATION_STARTED=0
MONITOR_PAUSED=0
DEPLOY_OK=0
HEALTH_HTTP=""
CMD=()
ENVV=()
MYSQL=()
MYSQLDUMP=()

die() { echo "ERROR: $*" >&2; exit 1; }
file_sha() { sha256sum "$1" | awk '{print $1}'; }

require_file() {
  local path="$1" expected="$2" label="$3"
  [[ -s "$path" && ! -L "$path" ]] || die "missing or unsafe $label"
  [[ "$(stat -Lc '%U:%G' "$path")" == "root:root" ]] || die "unexpected owner for $label"
  (( (8#$(stat -Lc '%a' "$path") & 0022) == 0 )) || die "$label is group/other writable"
  [[ "$(file_sha "$path")" == "$expected" ]] || die "$label checksum mismatch"
}

exact_app_pids() {
  "$PYTHON3" - "$APP" <<'PY'
import os
import sys
app = os.fsencode(sys.argv[1])
for name in os.listdir(b"/proc"):
    if not name.isdigit():
        continue
    try:
        with open(b"/proc/" + name + b"/cmdline", "rb") as handle:
            argv = [item for item in handle.read().split(b"\0") if item]
    except (FileNotFoundError, PermissionError, ProcessLookupError):
        continue
    if b"-jar" in argv and app in argv:
        print(name.decode("ascii"))
PY
}

listener_pids() {
  ss -H -ltnp 'sport = :8091' 2>/dev/null | grep -oE 'pid=[0-9]+' | cut -d= -f2 | sort -un || true
}

assert_single_runtime() {
  local apps listeners
  apps="$(exact_app_pids)"
  listeners="$(listener_pids)"
  [[ "$apps" =~ ^[0-9]+$ && "$listeners" == "$apps" ]] || die "unexpected production runtime state"
  OLD_PID="$apps"
}

stop_pid() {
  local pid="$1"
  [[ -n "$pid" ]] || return 0
  kill -0 "$pid" 2>/dev/null || return 0
  kill -TERM "$pid"
  for _ in $(seq 1 45); do
    kill -0 "$pid" 2>/dev/null || return 0
    sleep 1
  done
  kill -KILL "$pid"
  for _ in $(seq 1 10); do
    kill -0 "$pid" 2>/dev/null || return 0
    sleep 1
  done
  return 1
}

start_app() {
  (
    exec 9>&- 8>&-
    cd "$PROC_CWD"
    exec /usr/bin/nohup /usr/bin/env -i "${ENVV[@]}" "${CMD[@]}" >> "$LOG" 2>&1 < /dev/null
  ) &
  NEW_PID=$!
}

wait_for_health() {
  local pid="$1" code=""
  for _ in $(seq 1 120); do
    kill -0 "$pid" 2>/dev/null || return 1
    if [[ "$(exact_app_pids)" == "$pid" && "$(listener_pids)" == "$pid" ]]; then
      code="$(curl -sS -o /dev/null -w '%{http_code}' --max-time 3 "$HEALTH_URL" || true)"
      if [[ "$code" == "401" || "$code" == "403" ]]; then
        HEALTH_HTTP="$code"
        return 0
      fi
    fi
    sleep 1
  done
  return 1
}

switch_release() {
  local root="$1" target="$2"
  local link="$root/.current-qianfan-$$"
  [[ "$target" =~ ^releases/[0-9]{8}-[0-9]{6}[-A-Za-z0-9._]*$ ]] || return 1
  ln -s "$target" "$link"
  mv -Tf "$link" "$root/current"
  chown -h www:www "$root/current"
}

resume_monitoring() {
  if [[ "$MONITOR_PAUSED" == 1 ]]; then
    "$PYTHON3" "$BT_GUARD" resume-monitoring >/dev/null
    MONITOR_PAUSED=0
  fi
}

rollback() {
  local failed=0 running=""
  echo "Qianfan deployment failed; restoring production state." >&2
  if [[ "$MONITOR_PAUSED" != 1 ]]; then
    "$PYTHON3" "$BT_GUARD" pause-monitoring >/dev/null && MONITOR_PAUSED=1 || failed=1
  fi
  if [[ -n "$NEW_PID" ]]; then stop_pid "$NEW_PID" || failed=1; fi
  running="$(exact_app_pids)"
  if [[ -n "$running" ]]; then
    [[ "$running" =~ ^[0-9]+$ ]] && stop_pid "$running" || failed=1
  fi
  if [[ -n "$OLD_DD" ]]; then switch_release "/www/wwwroot/dd.hzmwmt.com" "$OLD_DD" || failed=1; fi
  if [[ -n "$OLD_MANAGE" ]]; then switch_release "/www/wwwroot/manage.hzmwmt.com" "$OLD_MANAGE" || failed=1; fi
  if [[ "$DB_MUTATION_STARTED" == 1 && -s "$BACKUP/t_ai_model_config.before.sql" ]]; then
    "${MYSQL[@]}" "$DB_NAME" < "$BACKUP/t_ai_model_config.before.sql" || failed=1
  fi
  if [[ -s "$BACKUP/digital-doctor-app.before.jar" ]]; then
    install -m 644 -o root -g root "$BACKUP/digital-doctor-app.before.jar" "$APP.qianfan-rollback"
    mv -f -- "$APP.qianfan-rollback" "$APP" || failed=1
  fi
  if [[ -s "$BACKUP/digital-doctor-hy3.before.yml" ]]; then
    install -m 600 -o root -g root "$BACKUP/digital-doctor-hy3.before.yml" "$CONFIG.qianfan-rollback"
    mv -f -- "$CONFIG.qianfan-rollback" "$CONFIG" || failed=1
  fi
  if [[ "$failed" == 0 ]]; then
    NEW_PID=""
    start_app
    wait_for_health "$NEW_PID" || failed=1
  fi
  if [[ "$failed" == 0 ]]; then resume_monitoring || failed=1; fi
  if [[ "$failed" == 0 ]]; then
    echo "ROLLBACK_OK BACKUP=$BACKUP" >&2
  else
    echo "ROLLBACK_INCOMPLETE BACKUP=${BACKUP:-none}" >&2
    return 1
  fi
}

on_exit() {
  local status=$?
  trap - EXIT
  if [[ "$status" != 0 && "$MUTATION_STARTED" == 1 && "$DEPLOY_OK" != 1 ]]; then
    set +e
    rollback || status=1
    set -e
  elif [[ "$MONITOR_PAUSED" == 1 ]]; then
    resume_monitoring || status=1
  fi
  rm -f -- "$KEY_FILE"
  if [[ -n "$WORK" ]]; then
    case "$WORK" in "$STAGE"/work.*) rm -rf -- "$WORK" ;; esac
  fi
  exit "$status"
}
trap on_exit EXIT

[[ "$EUID" == 0 ]] || die "must run as root"
exec 9>/var/lock/feibo-backend-deploy.lock
flock -n 9 || die "another backend deployment is running"

[[ -x "$PYTHON3" && ! -L "$PYTHON3" ]] || die "trusted Python runtime unavailable"
require_file "$APP" "$EXPECTED_OLD_APP_SHA" "production baseline JAR"
require_file "$CANDIDATE" "$EXPECTED_NEW_APP_SHA" "candidate JAR"
require_file "$MIGRATION" "$EXPECTED_MIGRATION_SHA" "database migration"
require_file "$MANAGE_ARCHIVE" "$EXPECTED_MANAGE_SHA" "manage frontend archive"
require_file "$KEY_ENCRYPTOR" "$EXPECTED_ENCRYPTOR_SHA" "key encryptor"
require_file "$FRONTEND_HELPER" "$EXPECTED_FRONTEND_HELPER_SHA" "frontend helper"
require_file "$BT_GUARD" "$EXPECTED_BT_GUARD_SHA" "BaoTa guard"
[[ -s "$KEY_FILE" && ! -L "$KEY_FILE" && "$(stat -Lc '%U:%G:%a' "$KEY_FILE")" == "root:root:600" ]] || die "unsafe Qianfan key file"
[[ "$(grep -c '^QIANFAN_API_KEY=' "$KEY_FILE" || true)" == 1 ]] || die "malformed Qianfan key file"
QIANFAN_KEY="$(sed -n 's/^QIANFAN_API_KEY=//p' "$KEY_FILE")"
[[ "$QIANFAN_KEY" =~ ^bce-v3/[A-Za-z0-9_-]+/[A-Fa-f0-9]{32,128}$ ]] || die "invalid Qianfan key format"

assert_single_runtime
while IFS= read -r -d '' item; do CMD+=("$item"); done < "/proc/$OLD_PID/cmdline"
while IFS= read -r -d '' item; do ENVV+=("$item"); done < "/proc/$OLD_PID/environ"
PROC_CWD="$(readlink -f "/proc/$OLD_PID/cwd")"
JAVA_EXE="$(readlink -f "/proc/$OLD_PID/exe")"
JAVAC_EXE="${JAVA_EXE%/java}/javac"
[[ -x "$JAVA_EXE" && -x "$JAVAC_EXE" && -d "$PROC_CWD" ]] || die "Java runtime capture failed"

WORK="$(mktemp -d "$STAGE/work.XXXXXX")"
"$PYTHON3" - "/proc/$OLD_PID/environ" "$CONFIG" "$WORK/encryption-secret" <<'PY'
import os
import re
import sys
source, config_path, target = sys.argv[1:]
value = None
with open(source, "rb") as handle:
    for item in handle.read().split(b"\0"):
        if item.startswith(b"AI_MODEL_KEY_ENCRYPTION_SECRET="):
            value = item.split(b"=", 1)[1]
            break
if not value:
    with open(config_path, encoding="utf-8") as handle:
        matches = [
            match.group(1).strip()
            for line in handle
            for match in [re.match(r"^  model-key-encryption-secret:\s*(.*?)\s*$", line)]
            if match
        ]
    if len(matches) == 1 and matches[0] and not matches[0].startswith("${"):
        configured = matches[0]
        if len(configured) >= 2 and configured[0] == configured[-1] and configured[0] in "\"'":
            configured = configured[1:-1]
        value = configured.encode("utf-8")
if not value:
    raise SystemExit("model encryption secret is unavailable")
with open(target, "wb") as handle:
    handle.write(value + b"\n")
os.chmod(target, 0o600)
PY

printf 'header = "Authorization: Bearer %s"\nheader = "Content-Type: application/json"\n' "$QIANFAN_KEY" > "$WORK/curl.conf"
chmod 600 "$WORK/curl.conf"
for index in "${!QIANFAN_MODEL_NAMES[@]}"; do
  model="${QIANFAN_MODEL_NAMES[$index]}"
  printf '{"model":"%s","messages":[{"role":"user","content":"Reply OK"}],"max_tokens":8,"temperature":0.1}' "$model" > "$WORK/request-$index.json"
  status="$(curl -sS -K "$WORK/curl.conf" -o "$WORK/response-$index.json" -w '%{http_code}' --max-time 60 \
    --data-binary "@$WORK/request-$index.json" https://qianfan.baidubce.com/v2/chat/completions)"
  [[ "$status" == 200 ]] || die "Qianfan preflight failed for model index $index with HTTP $status"
  "$PYTHON3" - "$WORK/response-$index.json" <<'PY'
import json
import sys
with open(sys.argv[1], encoding="utf-8") as handle:
    payload = json.load(handle)
if not payload.get("choices"):
    raise SystemExit("provider response contained no choices")
PY
done

"$PYTHON3" - "$CONFIG" "$WORK/mysql-client.cnf" "$WORK/database-name" <<'PY'
import os
import re
import sys
config_path, option_path, database_path = sys.argv[1:]
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
match = re.match(r"^jdbc:mysql://([^/:?]+)(?::(\d+))?/([^?]+)", values.get("url", ""))
if not match or not values.get("username") or not values.get("password"):
    raise SystemExit("datasource configuration unavailable")
host, port, database = match.group(1), match.group(2) or "3306", match.group(3)
def quote(value):
    return '"' + value.replace('\\', '\\\\').replace('"', '\\"') + '"'
with open(option_path, "w", encoding="utf-8") as handle:
    handle.write("[client]\n")
    handle.write("host=%s\nport=%s\nuser=%s\npassword=%s\ndefault-character-set=utf8mb4\n" %
                 tuple(quote(value) for value in (host, port, values["username"], values["password"])))
os.chmod(option_path, 0o600)
with open(database_path, "w", encoding="ascii") as handle:
    handle.write(database)
PY
DB_NAME="$(cat "$WORK/database-name")"
[[ "$DB_NAME" =~ ^[A-Za-z0-9_$-]+$ ]] || die "invalid database name"
MYSQL=(mysql --defaults-extra-file="$WORK/mysql-client.cnf" --default-character-set=utf8mb4)
MYSQLDUMP=(mysqldump --defaults-extra-file="$WORK/mysql-client.cnf" --default-character-set=utf8mb4)

"$JAVAC_EXE" -d "$WORK" "$KEY_ENCRYPTOR"
"$JAVA_EXE" -cp "$WORK" QianfanKeyEncryptor "$WORK/encryption-secret" "$KEY_FILE" "$WORK/ciphertexts.tsv" "${QIANFAN_MODEL_IDS[@]}"
[[ "$(wc -l < "$WORK/ciphertexts.tsv")" == 3 ]] || die "credential encryption output mismatch"
"$PYTHON3" - "$WORK/ciphertexts.tsv" "$WORK/apply-ciphertexts.sql" <<'PY'
import re
import sys
source, target = sys.argv[1:]
allowed_ids = {"9005400000000000001", "9005400000000000002", "9005400000000000003"}
rows = []
with open(source, encoding="utf-8") as handle:
    for line in handle:
        model_id, ciphertext = line.rstrip("\n").split("\t", 1)
        if model_id not in allowed_ids or not re.fullmatch(r"v1:[A-Za-z0-9+/=]+", ciphertext):
            raise SystemExit("invalid encrypted credential row")
        rows.append((model_id, ciphertext))
if {item[0] for item in rows} != allowed_ids:
    raise SystemExit("encrypted credential IDs mismatch")
with open(target, "w", encoding="utf-8") as handle:
    handle.write("START TRANSACTION;\n")
    for model_id, ciphertext in rows:
        handle.write("UPDATE t_ai_model_config SET api_key_ciphertext='%s', update_time=NOW() WHERE ai_model_config_id=%s AND model_code='qianfan' AND is_leaf=1;\n" % (ciphertext, model_id))
    handle.write("COMMIT;\n")
PY

OLD_DD="$(readlink /www/wwwroot/dd.hzmwmt.com/current || true)"
OLD_MANAGE="$(readlink /www/wwwroot/manage.hzmwmt.com/current || true)"
[[ "$OLD_DD" =~ ^releases/ && "$OLD_MANAGE" =~ ^releases/ ]] || die "invalid frontend symlinks"
[[ ! -e "/www/wwwroot/dd.hzmwmt.com/releases/$RELEASE" && ! -e "/www/wwwroot/manage.hzmwmt.com/releases/$RELEASE" ]] || die "frontend release already exists"

STAMP="$(date +%Y%m%d-%H%M%S)"
BACKUP="/www/wwwroot/deploy-backups/qianfan-$STAMP"
install -d -m 700 -o root -g root "$BACKUP"
cp -p "$APP" "$BACKUP/digital-doctor-app.before.jar"
cp -p "$CONFIG" "$BACKUP/digital-doctor-hy3.before.yml"
"${MYSQLDUMP[@]}" --single-transaction --skip-lock-tables "$DB_NAME" t_ai_model_config > "$BACKUP/t_ai_model_config.before.sql"
[[ -s "$BACKUP/t_ai_model_config.before.sql" ]] || die "database backup is empty"
printf '%s\n' "$OLD_DD" > "$BACKUP/dd.current.before"
printf '%s\n' "$OLD_MANAGE" > "$BACKUP/manage.current.before"
sha256sum "$BACKUP/digital-doctor-app.before.jar" "$BACKUP/digital-doctor-hy3.before.yml" > "$BACKUP/backend.before.sha256"

cp -p "$CONFIG" "$WORK/digital-doctor-hy3.new.yml"
"$PYTHON3" - "$WORK/digital-doctor-hy3.new.yml" <<'PY'
import re
import sys
path = sys.argv[1]
with open(path, encoding="utf-8") as handle:
    config = handle.read()
if config.count("    qianfan:\n") != 0:
    raise SystemExit("Qianfan config already exists")
pattern = re.compile(r"(^    kimik3:\n(?:^      .*\n)+)", re.MULTILINE)
matches = list(pattern.finditer(config))
if len(matches) != 1:
    raise SystemExit("unexpected Kimi config block")
block = ("    qianfan:\n"
         "      enabled: true\n"
         "      base-url: https://qianfan.baidubce.com/v2\n"
         "      api-key: ${QIANFAN_API_KEY:}\n")
updated = config[:matches[0].end()] + block + config[matches[0].end():]
with open(path, "w", encoding="utf-8", newline="\n") as handle:
    handle.write(updated)
PY
grep -q '^    qianfan:$' "$WORK/digital-doctor-hy3.new.yml"
grep -q '^      base-url: https://qianfan.baidubce.com/v2$' "$WORK/digital-doctor-hy3.new.yml"

tar -czf "$WORK/dd-current.tar.gz" -C "/www/wwwroot/dd.hzmwmt.com/current" .
[[ -s "$WORK/dd-current.tar.gz" ]] || die "current DD archive is empty"

"$PYTHON3" "$BT_GUARD" pause-monitoring >/dev/null
MONITOR_PAUSED=1
MUTATION_STARTED=1
stop_pid "$OLD_PID"

install -m 644 -o root -g root "$CANDIDATE" "$APP.qianfan-new"
mv -f -- "$APP.qianfan-new" "$APP"
install -m 600 -o root -g root "$WORK/digital-doctor-hy3.new.yml" "$CONFIG.qianfan-new"
mv -f -- "$CONFIG.qianfan-new" "$CONFIG"
[[ "$(file_sha "$APP")" == "$EXPECTED_NEW_APP_SHA" ]] || die "installed candidate checksum mismatch"

DB_MUTATION_STARTED=1
"${MYSQL[@]}" "$DB_NAME" < "$MIGRATION"
"${MYSQL[@]}" "$DB_NAME" < "$WORK/apply-ciphertexts.sql"

start_app
wait_for_health "$NEW_PID" || die "new application failed health check"

"${MYSQL[@]}" "$DB_NAME" -e "UPDATE t_ai_model_config SET visible=1, update_time=NOW() WHERE ai_model_config_id IN (9005400000000000001,9005400000000000002,9005400000000000003) AND model_code='qianfan' AND is_leaf=1 AND is_deleted=0 AND api_key_ciphertext IS NOT NULL;"

DB_CHECK="$("${MYSQL[@]}" -N -B "$DB_NAME" -e "SELECT CONCAT_WS('|', (SELECT name FROM t_ai_model_config WHERE ai_model_config_id=$SZDOCTOR_PROVIDER_BIZ_ID LIMIT 1), (SELECT COUNT(*) FROM t_ai_model_config WHERE ai_model_config_id=$QIANFAN_PROVIDER_BIZ_ID AND parent_id=(SELECT id FROM t_ai_model_config WHERE ai_model_config_id=$GENERAL_ROOT_BIZ_ID LIMIT 1) AND is_leaf=0 AND name='百度千帆'), (SELECT COUNT(*) FROM t_ai_model_config WHERE parent_id=(SELECT id FROM t_ai_model_config WHERE ai_model_config_id=$QIANFAN_PROVIDER_BIZ_ID LIMIT 1) AND model_code='qianfan' AND is_leaf=1 AND visible=1 AND is_deleted=0 AND api_key_ciphertext IS NOT NULL), (SELECT GROUP_CONCAT(sort ORDER BY sort) FROM t_ai_model_config WHERE parent_id=(SELECT id FROM t_ai_model_config WHERE ai_model_config_id=$QIANFAN_PROVIDER_BIZ_ID LIMIT 1) AND model_code='qianfan' AND is_leaf=1 AND is_deleted=0));")"
[[ "$DB_CHECK" == "木吾木同Medtalent|1|3|1,2,3" ]] || die "database verification failed"

bash "$FRONTEND_HELPER" "$WORK/dd-current.tar.gz" "$MANAGE_ARCHIVE" "$RELEASE"
[[ "$(readlink /www/wwwroot/manage.hzmwmt.com/current)" == "releases/$RELEASE" ]] || die "manage frontend symlink mismatch"
grep -R -F -l --include='*.js' '木吾木同Medtalent' /www/wwwroot/manage.hzmwmt.com/current/assets > "$WORK/manage-label-files"
[[ -s "$WORK/manage-label-files" ]] || die "deployed manage bundle lacks Medtalent label"
! grep -R -F -l --include='*.js' '苏州医生' /www/wwwroot/manage.hzmwmt.com/current/assets >/dev/null || die "deployed manage bundle still contains legacy provider label"

PUBLIC_API_HTTP="$(curl -sS -o /dev/null -w '%{http_code}' --max-time 10 --resolve api.hzmwmt.com:443:127.0.0.1 https://api.hzmwmt.com/api/v1/ai/model/index)"
[[ "$PUBLIC_API_HTTP" == "401" || "$PUBLIC_API_HTTP" == "403" ]] || die "public protected API health check failed"

"${MYSQLDUMP[@]}" --single-transaction --skip-lock-tables "$DB_NAME" t_ai_model_config > "$BACKUP/t_ai_model_config.after.sql"
sha256sum "$APP" "$CONFIG" > "$BACKUP/backend.after.sha256"
sha256sum "$BACKUP/t_ai_model_config.before.sql" "$BACKUP/t_ai_model_config.after.sql" > "$BACKUP/database.sha256"
printf '%s\n' "releases/$RELEASE" > "$BACKUP/dd.current.after"
printf '%s\n' "releases/$RELEASE" > "$BACKUP/manage.current.after"

resume_monitoring
DEPLOY_OK=1
printf 'QIANFAN_DEPLOY_OK\nRELEASE=%s\nBACKUP=%s\nOLD_PID=%s\nNEW_PID=%s\nAPP_SHA=%s\nHEALTH_HTTP=%s\nPUBLIC_API_HTTP=%s\nDB_CHECK=%s\nPROVIDER_PREFLIGHTS=3/3\nKEY_STAGED_REMOVED=true\n' \
  "$RELEASE" "$BACKUP" "$OLD_PID" "$NEW_PID" "$EXPECTED_NEW_APP_SHA" "$HEALTH_HTTP" "$PUBLIC_API_HTTP" "$DB_CHECK"

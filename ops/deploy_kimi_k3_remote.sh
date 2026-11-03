#!/usr/bin/env bash
set -Eeuo pipefail
umask 077

APP="/www/wwwroot/digital-doctor-app.jar"
CONFIG="/www/wwwroot/digital-doctor-hy3.yml"
LOG="/www/wwwroot/digital-doctor-app.szdoctor.out"
STAGE="/root/codex-feibo-kimi-k3/20260817-171145"
CANDIDATE="$STAGE/digital-doctor-app.kimi-k3.candidate-v2.jar"
SQL_FILE="$STAGE/add_kimi_k3_ai_model.sql"
DD_ARCHIVE="$STAGE/dd-kimi-k3-20260817-171145.tar.gz"
MANAGE_ARCHIVE="$STAGE/manage-baseline-20260817-171145.tar.gz"
KEY_FILE="$STAGE/kimi-k3.env"
FRONTEND_HELPER="/usr/local/sbin/deploy-feibo-frontends.sh"
RELEASE="20260817-171145-kimi-k3"
PYTHON3="/www/server/panel/pyenv/bin/python3.7"
HEALTH_URL="http://127.0.0.1:8091/api/v1/ai/model/index"

EXPECTED_OLD_APP_SHA="fbfc66d6aec1395e856e3b7b561c0da954ab27b1c6df3ea6deff97748e1c6177"
EXPECTED_NEW_APP_SHA="da9f5ce575818fa82157d738baafdc6d4dd8ecce7f3eccf4b94180f35909c696"
EXPECTED_SQL_SHA="20f71698a4bb5bcb9412a24ebcd3c3893a3d963b4119733090306ef7838fde08"
EXPECTED_DD_SHA="6bed510e47b8531c9cd559f73fc8bc4ce4ba7ff19f576d0018350e28dc26037b"
EXPECTED_MANAGE_SHA="4ddcccf283865580392aca8c5665f3af6081566fc3bab02d8b87873b940d1e7c"
EXPECTED_FRONTEND_HELPER_SHA="0c5f05238cb024c8231763d3f3e10448da45e5840f28d6728c4a761e982dbd3a"

KIMI_CATEGORY_BIZ_ID="9005300000000000000"
KIMI_LEAF_BIZ_ID="9005300000000000001"
GLM_CATEGORY_BIZ_ID="9005200000000000000"
COMMON_CATEGORY_BIZ_ID="1970339278848659457"

WORK=""
BACKUP=""
OLD_PID=""
NEW_PID=""
PROC_CWD=""
OLD_DD=""
OLD_MANAGE=""
MUTATION_STARTED=0
DB_MUTATION_ATTEMPTED=0
DEPLOY_OK=0
CMD=()
ENVV=()
ENVV_PATCHED=()
MYSQL=()
MYSQLDUMP=()

die() {
  echo "ERROR: $*" >&2
  exit 1
}

require_file() {
  local path="$1"
  [[ -s "$path" && ! -L "$path" ]] || die "missing or unsafe required file: $path"
}

file_sha() {
  sha256sum "$1" | awk '{print $1}'
}

wait_for_health() {
  local pid="$1"
  local code=""
  for _ in $(seq 1 90); do
    kill -0 "$pid" 2>/dev/null || return 1
    code="$(curl -sS -o /dev/null -w '%{http_code}' --max-time 3 "$HEALTH_URL" || true)"
    if [[ "$code" == "200" || "$code" == "401" || "$code" == "403" ]]; then
      HEALTH_HTTP="$code"
      return 0
    fi
    sleep 1
  done
  return 1
}

stop_pid() {
  local pid="$1"
  [[ -n "$pid" ]] || return 0
  kill -0 "$pid" 2>/dev/null || return 0
  kill -TERM "$pid" 2>/dev/null || true
  for _ in $(seq 1 30); do
    kill -0 "$pid" 2>/dev/null || return 0
    sleep 1
  done
  kill -KILL "$pid" 2>/dev/null || true
  sleep 1
}

start_with_environment() {
  local env_name="$1"
  cd "$PROC_CWD"
  case "$env_name" in
    ENVV)
      /usr/bin/nohup /usr/bin/env -i "${ENVV[@]}" "${CMD[@]}" > "$LOG" 2>&1 < /dev/null 9>&- &
      ;;
    ENVV_PATCHED)
      /usr/bin/nohup /usr/bin/env -i "${ENVV_PATCHED[@]}" "${CMD[@]}" > "$LOG" 2>&1 < /dev/null 9>&- &
      ;;
    *)
      return 1
      ;;
  esac
  STARTED_PID=$!
}

switch_release() {
  local root="$1"
  local target="$2"
  local link="$root/.current-kimi-rollback-$$"
  [[ "$target" =~ ^releases/[0-9]{8}-[0-9]{6}[-A-Za-z0-9._]*$ ]] || return 1
  ln -s "$target" "$link"
  mv -Tf "$link" "$root/current"
  chown -h www:www "$root/current"
}

rollback() {
  local rollback_failed=0
  echo "Deployment failed; restoring the previous production state." >&2

  if [[ -n "$OLD_DD" ]]; then
    switch_release "/www/wwwroot/dd.hzmwmt.com" "$OLD_DD" || rollback_failed=1
  fi
  if [[ -n "$OLD_MANAGE" ]]; then
    switch_release "/www/wwwroot/manage.hzmwmt.com" "$OLD_MANAGE" || rollback_failed=1
  fi

  if [[ -n "$NEW_PID" ]]; then
    stop_pid "$NEW_PID"
  else
    local running_pid
    running_pid="$(pgrep -f 'java .*digital-doctor-app\.jar' | head -n 1 || true)"
    if [[ -n "$running_pid" && "$running_pid" != "$OLD_PID" ]]; then stop_pid "$running_pid"; fi
  fi

  if [[ "$DB_MUTATION_ATTEMPTED" == 1 && -s "$BACKUP/t_ai_model_config.before.sql" ]]; then
    MYSQL_PWD="$DB_PASS" "${MYSQL[@]}" "$DB_NAME" < "$BACKUP/t_ai_model_config.before.sql" || rollback_failed=1
  fi

  if [[ -s "$BACKUP/digital-doctor-app.before.jar" ]]; then
    install -m "$(stat -Lc '%a' "$BACKUP/digital-doctor-app.before.jar")" -o root -g root \
      "$BACKUP/digital-doctor-app.before.jar" "$APP.rollback"
    mv -f -- "$APP.rollback" "$APP" || rollback_failed=1
  fi
  if [[ -s "$BACKUP/digital-doctor-hy3.before.yml" ]]; then
    install -m 600 -o root -g root "$BACKUP/digital-doctor-hy3.before.yml" "$CONFIG.rollback"
    mv -f -- "$CONFIG.rollback" "$CONFIG" || rollback_failed=1
  fi

  if ! pgrep -f 'java .*digital-doctor-app\.jar' >/dev/null; then
    start_with_environment ENVV
    local rollback_pid="$STARTED_PID"
    if ! wait_for_health "$rollback_pid"; then rollback_failed=1; fi
  fi

  if [[ "$rollback_failed" == 0 ]]; then
    echo "ROLLBACK_OK BACKUP=$BACKUP" >&2
  else
    echo "ROLLBACK_INCOMPLETE BACKUP=$BACKUP" >&2
  fi
}

cleanup_and_maybe_rollback() {
  local status=$?
  trap - EXIT
  if [[ "$status" != 0 && "$MUTATION_STARTED" == 1 && "$DEPLOY_OK" != 1 ]]; then
    set +e
    rollback
    set -e
  fi
  if [[ -n "$WORK" ]]; then
    case "$WORK" in
      "$STAGE"/work.*) rm -rf -- "$WORK" ;;
    esac
  fi
  exit "$status"
}
trap cleanup_and_maybe_rollback EXIT

exec 9>/var/lock/feibo-kimi-k3-deploy-v2.lock
flock -n 9 || die "another Kimi K3 deployment is already running"

require_file "$APP"
require_file "$CONFIG"
require_file "$CANDIDATE"
require_file "$SQL_FILE"
require_file "$DD_ARCHIVE"
require_file "$MANAGE_ARCHIVE"
require_file "$KEY_FILE"
require_file "$FRONTEND_HELPER"
[[ -x "$PYTHON3" && ! -L "$PYTHON3" ]] || die "trusted Python runtime unavailable"

[[ "$(file_sha "$APP")" == "$EXPECTED_OLD_APP_SHA" ]] || die "production JAR baseline changed"
[[ "$(file_sha "$CANDIDATE")" == "$EXPECTED_NEW_APP_SHA" ]] || die "candidate JAR checksum mismatch"
[[ "$(file_sha "$SQL_FILE")" == "$EXPECTED_SQL_SHA" ]] || die "SQL checksum mismatch"
[[ "$(file_sha "$DD_ARCHIVE")" == "$EXPECTED_DD_SHA" ]] || die "DD archive checksum mismatch"
[[ "$(file_sha "$MANAGE_ARCHIVE")" == "$EXPECTED_MANAGE_SHA" ]] || die "manage archive checksum mismatch"
[[ "$(file_sha "$FRONTEND_HELPER")" == "$EXPECTED_FRONTEND_HELPER_SHA" ]] || die "frontend helper checksum mismatch"

KEY_LINE_COUNT="$(grep -c '^KIMI_K3_API_KEY=' "$KEY_FILE" || true)"
[[ "$KEY_LINE_COUNT" == 1 ]] || die "Kimi key file is malformed"
KIMI_KEY="$(sed -n 's/^KIMI_K3_API_KEY=//p' "$KEY_FILE")"
[[ "$KIMI_KEY" =~ ^sk-[A-Za-z0-9_-]{20,}$ ]] || die "Kimi API key format is invalid"

WORK="$(mktemp -d "$STAGE/work.XXXXXX")"

KIMI_MODELS_HTTP="$(curl -sS -o "$WORK/kimi-models.json" -w '%{http_code}' --max-time 20 \
  -H "Authorization: Bearer $KIMI_KEY" https://api.moonshot.cn/v1/models)"
[[ "$KIMI_MODELS_HTTP" == 200 ]] || die "Kimi models preflight returned HTTP $KIMI_MODELS_HTTP"
"$PYTHON3" - "$WORK/kimi-models.json" <<'PY'
import json
import sys
with open(sys.argv[1], encoding="utf-8") as handle:
    payload = json.load(handle)
models = {item.get("id") for item in payload.get("data", []) if isinstance(item, dict)}
if "kimi-k3" not in models:
    raise SystemExit("kimi-k3 is absent from the provider model list")
PY

KIMI_CHAT_HTTP="$(curl -sS -o "$WORK/kimi-chat.json" -w '%{http_code}' --max-time 45 \
  -H "Authorization: Bearer $KIMI_KEY" -H 'Content-Type: application/json' \
  -d '{"model":"kimi-k3","messages":[{"role":"user","content":"Reply OK"}],"max_tokens":4,"temperature":1}' \
  https://api.moonshot.cn/v1/chat/completions)"
[[ "$KIMI_CHAT_HTTP" == 200 ]] || die "Kimi chat preflight returned HTTP $KIMI_CHAT_HTTP"
"$PYTHON3" - "$WORK/kimi-chat.json" <<'PY'
import json
import sys
with open(sys.argv[1], encoding="utf-8") as handle:
    payload = json.load(handle)
if not payload.get("choices"):
    raise SystemExit("Kimi chat preflight returned no choices")
PY

OLD_PID="$(pgrep -f 'java .*digital-doctor-app\.jar' | head -n 1 || true)"
[[ -n "$OLD_PID" && -r "/proc/$OLD_PID/cmdline" && -r "/proc/$OLD_PID/environ" ]] || die "running application process not found"
while IFS= read -r -d '' item; do CMD+=("$item"); done < "/proc/$OLD_PID/cmdline"
while IFS= read -r -d '' item; do ENVV+=("$item"); done < "/proc/$OLD_PID/environ"
PROC_CWD="$(readlink -f "/proc/$OLD_PID/cwd")"
[[ ${#CMD[@]} -gt 0 && -d "$PROC_CWD" ]] || die "could not capture application launch configuration"

for item in "${ENVV[@]}"; do
  case "$item" in
    KIMI_K3_API_KEY=*|KIMI_K3_ENABLED=*) ;;
    *) ENVV_PATCHED+=("$item") ;;
  esac
done
ENVV_PATCHED+=("KIMI_K3_ENABLED=true" "KIMI_K3_API_KEY=$KIMI_KEY")

OLD_DD="$(readlink /www/wwwroot/dd.hzmwmt.com/current || true)"
OLD_MANAGE="$(readlink /www/wwwroot/manage.hzmwmt.com/current || true)"
[[ "$OLD_DD" =~ ^releases/ && "$OLD_MANAGE" =~ ^releases/ ]] || die "invalid current frontend symlink state"
[[ ! -e "/www/wwwroot/dd.hzmwmt.com/releases/$RELEASE" ]] || die "DD release already exists"
[[ ! -e "/www/wwwroot/manage.hzmwmt.com/releases/$RELEASE" ]] || die "manage release already exists"

STAMP="$(date +%Y%m%d-%H%M%S)"
BACKUP="/www/wwwroot/deploy-backups/kimi-k3-$STAMP"
install -d -m 700 -o root -g root "$BACKUP"
cp -p "$APP" "$BACKUP/digital-doctor-app.before.jar"
cp -p "$CONFIG" "$BACKUP/digital-doctor-hy3.before.yml"
if [[ -f "$LOG" ]]; then cp -p "$LOG" "$BACKUP/digital-doctor-app.before.log"; fi
printf '%s\n' "$OLD_DD" > "$BACKUP/dd.current.before"
printf '%s\n' "$OLD_MANAGE" > "$BACKUP/manage.current.before"
sha256sum "$BACKUP/digital-doctor-app.before.jar" "$BACKUP/digital-doctor-hy3.before.yml" > "$BACKUP/backend.before.sha256"
cp -p "$SQL_FILE" "$BACKUP/add_kimi_k3_ai_model.sql"

unzip -p "$APP" BOOT-INF/classes/application-dev.yml > "$WORK/application-dev.yml"
"$PYTHON3" - "$WORK/application-dev.yml" "$WORK/db-values.b64" <<'PY'
import base64
import re
import sys

cfg, out = sys.argv[1:]
values = {}
inside = False
with open(cfg, encoding="utf-8") as handle:
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
            value = match.group(2).strip()
            if len(value) >= 2 and value[0] == value[-1] and value[0] in "\"'":
                value = value[1:-1]
            values[match.group(1)] = value

missing = {"url", "username", "password"} - values.keys()
if missing:
    raise SystemExit("missing datasource keys: " + ", ".join(sorted(missing)))
match = re.match(r"jdbc:mysql://([^/:?]+)(?::(\d+))?/([^?]+)", values["url"])
if not match:
    raise SystemExit("unsupported datasource URL")
host, port, database = match.group(1), match.group(2) or "3306", match.group(3)
with open(out, "w", encoding="ascii") as handle:
    for value in (host, port, database, values["username"], values["password"]):
        handle.write(base64.b64encode(value.encode()).decode() + "\n")
PY

decode_line() { sed -n "${1}p" "$WORK/db-values.b64" | base64 -d; }
DB_HOST="$(decode_line 1)"
DB_PORT="$(decode_line 2)"
DB_NAME="$(decode_line 3)"
DB_USER="$(decode_line 4)"
DB_PASS="$(decode_line 5)"
MYSQL=(mysql --default-character-set=utf8mb4 -h "$DB_HOST" -P "$DB_PORT" -u "$DB_USER")
MYSQLDUMP=(mysqldump --default-character-set=utf8mb4 -h "$DB_HOST" -P "$DB_PORT" -u "$DB_USER")

MYSQL_PWD="$DB_PASS" "${MYSQLDUMP[@]}" --single-transaction --skip-lock-tables "$DB_NAME" t_ai_model_config > "$BACKUP/t_ai_model_config.before.sql"
[[ -s "$BACKUP/t_ai_model_config.before.sql" ]] || die "database backup is empty"

COMMON_PARENT_ID="$(MYSQL_PWD="$DB_PASS" "${MYSQL[@]}" -N -B "$DB_NAME" -e \
  "SELECT id FROM t_ai_model_config WHERE ai_model_config_id=$COMMON_CATEGORY_BIZ_ID LIMIT 1;")"
GLM_SORT="$(MYSQL_PWD="$DB_PASS" "${MYSQL[@]}" -N -B "$DB_NAME" -e \
  "SELECT sort FROM t_ai_model_config WHERE ai_model_config_id=$GLM_CATEGORY_BIZ_ID AND parent_id=$COMMON_PARENT_ID LIMIT 1;")"
[[ "$COMMON_PARENT_ID" =~ ^[0-9]+$ && "$GLM_SORT" =~ ^[0-9]+$ ]] || die "general model or GLM baseline is missing"
TARGET_SORT=$((GLM_SORT + 1))
KIMI_EXISTING="$(MYSQL_PWD="$DB_PASS" "${MYSQL[@]}" -N -B "$DB_NAME" -e \
  "SELECT COUNT(*) FROM t_ai_model_config WHERE ai_model_config_id IN ($KIMI_CATEGORY_BIZ_ID,$KIMI_LEAF_BIZ_ID) OR BINARY model_code=BINARY 'kimik3';")"
[[ "$KIMI_EXISTING" == 0 ]] || die "Kimi model rows already exist; refusing a non-baseline deployment"

MYSQL_PWD="$DB_PASS" "${MYSQL[@]}" -N -B "$DB_NAME" -e \
  "SELECT id,ai_model_config_id,sort,name FROM t_ai_model_config WHERE parent_id=$COMMON_PARENT_ID AND is_leaf=0 ORDER BY sort,id;" \
  > "$BACKUP/general-model-order.before.tsv"
awk -F '\t' -v OFS='\t' -v target="$TARGET_SORT" '{ sort=$3+0; if (sort>=target) sort++; print $1,$2,sort,$4 }' \
  "$BACKUP/general-model-order.before.tsv" > "$WORK/general-model-order.expected.tsv"

cp -p "$CONFIG" "$WORK/digital-doctor-hy3.new.yml"
[[ "$(grep -c '^ai:$' "$WORK/digital-doctor-hy3.new.yml")" == 1 ]] || die "unexpected external config root"
[[ "$(grep -c '^  models:$' "$WORK/digital-doctor-hy3.new.yml")" == 1 ]] || die "unexpected external models config"
! grep -q '^    kimik3:' "$WORK/digital-doctor-hy3.new.yml" || die "Kimi config already exists"
cat >> "$WORK/digital-doctor-hy3.new.yml" <<'EOF'
    kimik3:
      enabled: true
      base-url: https://api.moonshot.cn/v1
      api-key: ${KIMI_K3_API_KEY:}
EOF

"$PYTHON3" - "$CANDIDATE" <<'PY'
import io
import zipfile
import sys

path = sys.argv[1]
with zipfile.ZipFile(path) as outer:
    names = outer.namelist()
    if len(names) != len(set(names)):
        raise SystemExit("duplicate entries in candidate JAR")
    config_name = "BOOT-INF/classes/com/hzmwmt/config/ChatModelConfiguration.class"
    outer.read(config_name)
    domains = [name for name in names if name.startswith("BOOT-INF/lib/digital-doctor-domain-") and name.endswith(".jar")]
    if len(domains) != 1:
        raise SystemExit("unexpected domain JAR count")
    domain_info = outer.getinfo(domains[0])
    if domain_info.compress_type != zipfile.ZIP_STORED:
        raise SystemExit("domain JAR must be stored in the executable archive")
    domain_data = outer.read(domains[0])
with zipfile.ZipFile(io.BytesIO(domain_data)) as inner:
    names = inner.namelist()
    if len(names) != len(set(names)):
        raise SystemExit("duplicate entries in domain JAR")
    inner.read("com/hzmwmt/domain/ai/service/chat/model/strategy/KimiK3ModelStrategyService.class")
PY

echo "Preflight complete; starting production mutation."
MUTATION_STARTED=1
stop_pid "$OLD_PID"

install -m 644 -o root -g root "$CANDIDATE" "$APP.kimi-new"
mv -f -- "$APP.kimi-new" "$APP"
install -m 600 -o root -g root "$WORK/digital-doctor-hy3.new.yml" "$CONFIG.kimi-new"
mv -f -- "$CONFIG.kimi-new" "$CONFIG"
[[ "$(file_sha "$APP")" == "$EXPECTED_NEW_APP_SHA" ]] || die "installed application checksum mismatch"

DB_MUTATION_ATTEMPTED=1
MYSQL_PWD="$DB_PASS" "${MYSQL[@]}" "$DB_NAME" < "$SQL_FILE"

start_with_environment ENVV_PATCHED
NEW_PID="$STARTED_PID"
wait_for_health "$NEW_PID" || die "new application failed its health check"

MYSQL_PWD="$DB_PASS" "${MYSQL[@]}" -N -B "$DB_NAME" -e \
  "SELECT id,ai_model_config_id,sort,name FROM t_ai_model_config WHERE parent_id=$COMMON_PARENT_ID AND is_leaf=0 AND ai_model_config_id<>$KIMI_CATEGORY_BIZ_ID ORDER BY sort,id;" \
  > "$WORK/general-model-order.actual-existing.tsv"
diff -u "$WORK/general-model-order.expected.tsv" "$WORK/general-model-order.actual-existing.tsv"

KIMI_CATEGORY_CHECK="$(MYSQL_PWD="$DB_PASS" "${MYSQL[@]}" -N -B "$DB_NAME" -e \
  "SELECT CONCAT_WS('|',COUNT(*),MIN(sort),MIN(name),MIN(CAST(is_leaf AS UNSIGNED))) FROM t_ai_model_config WHERE ai_model_config_id=$KIMI_CATEGORY_BIZ_ID AND parent_id=$COMMON_PARENT_ID;")"
[[ "$KIMI_CATEGORY_CHECK" == "1|$TARGET_SORT|Kimi|0" ]] || die "Kimi category verification failed"
KIMI_LEAF_CHECK="$(MYSQL_PWD="$DB_PASS" "${MYSQL[@]}" -N -B "$DB_NAME" -e \
  "SELECT CONCAT_WS('|',COUNT(*),MIN(name),MIN(model_code),MIN(CAST(type AS UNSIGNED)),MIN(CAST(is_leaf AS UNSIGNED))) FROM t_ai_model_config WHERE ai_model_config_id=$KIMI_LEAF_BIZ_ID AND parent_id=(SELECT id FROM t_ai_model_config WHERE ai_model_config_id=$KIMI_CATEGORY_BIZ_ID LIMIT 1);")"
[[ "$KIMI_LEAF_CHECK" == "1|kimi-k3|kimik3|1|1" ]] || die "Kimi leaf verification failed"

"$FRONTEND_HELPER" "$DD_ARCHIVE" "$MANAGE_ARCHIVE" "$RELEASE"

[[ "$(readlink /www/wwwroot/dd.hzmwmt.com/current)" == "releases/$RELEASE" ]] || die "DD release symlink did not switch"
[[ "$(readlink /www/wwwroot/manage.hzmwmt.com/current)" == "releases/$RELEASE" ]] || die "manage release symlink did not switch"
grep -R -F -l --include='*.js' '["GLM"],["Kimi"],["百川"' "/www/wwwroot/dd.hzmwmt.com/current/assets" > "$WORK/kimi-bundle-files.txt"
[[ -s "$WORK/kimi-bundle-files.txt" ]] || die "deployed DD bundle does not contain Kimi after GLM"

DD_HTTP="$(curl -sS -o /dev/null -w '%{http_code}' --max-time 10 --resolve dd.hzmwmt.com:443:127.0.0.1 https://dd.hzmwmt.com/)"
MANAGE_HTTP="$(curl -sS -o /dev/null -w '%{http_code}' --max-time 10 --resolve manage.hzmwmt.com:443:127.0.0.1 https://manage.hzmwmt.com/)"
API_HTTP="$(curl -sS -o /dev/null -w '%{http_code}' --max-time 10 --resolve api.hzmwmt.com:443:127.0.0.1 https://api.hzmwmt.com/api/v1/ai/model/index)"
[[ "$DD_HTTP" == 200 && "$MANAGE_HTTP" == 200 ]] || die "public frontend health check failed"
[[ "$API_HTTP" == 401 || "$API_HTTP" == 403 ]] || die "public protected API health check returned HTTP $API_HTTP"

MYSQL_PWD="$DB_PASS" "${MYSQLDUMP[@]}" --single-transaction --skip-lock-tables "$DB_NAME" t_ai_model_config > "$BACKUP/t_ai_model_config.after.sql"
sha256sum "$APP" "$CONFIG" > "$BACKUP/backend.after.sha256"
sha256sum "$BACKUP/t_ai_model_config.before.sql" "$BACKUP/t_ai_model_config.after.sql" > "$BACKUP/database-dumps.sha256"
printf '%s\n' "releases/$RELEASE" > "$BACKUP/dd.current.after"
printf '%s\n' "releases/$RELEASE" > "$BACKUP/manage.current.after"

DEPLOY_OK=1
printf 'DEPLOY_OK\nRELEASE=%s\nBACKUP=%s\nOLD_PID=%s\nNEW_PID=%s\nOLD_APP_SHA=%s\nNEW_APP_SHA=%s\nLOCAL_HEALTH_HTTP=%s\nDD_HTTP=%s\nMANAGE_HTTP=%s\nAPI_HTTP=%s\nKIMI_PROVIDER_MODELS_HTTP=%s\nKIMI_PROVIDER_CHAT_HTTP=%s\nKIMI_CATEGORY=%s\nKIMI_LEAF=%s\n' \
  "$RELEASE" "$BACKUP" "$OLD_PID" "$NEW_PID" "$EXPECTED_OLD_APP_SHA" "$EXPECTED_NEW_APP_SHA" \
  "$HEALTH_HTTP" "$DD_HTTP" "$MANAGE_HTTP" "$API_HTTP" "$KIMI_MODELS_HTTP" "$KIMI_CHAT_HTTP" \
  "$KIMI_CATEGORY_CHECK" "$KIMI_LEAF_CHECK"

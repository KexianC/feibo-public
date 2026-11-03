#!/usr/bin/env bash
set -Eeuo pipefail

# Database-only release gate for the copywriting workflow rollout.
#
# This script deliberately leaves the application stopped after a successful
# apply. The backend release script must consume the captured launch metadata,
# replace the JAR, and start the service. This script never starts a process and
# never replaces the production JAR.

APP="/www/wwwroot/digital-doctor-app.jar"
PORT="8091"
LOCK_FILE="/var/lock/feibo-copywriting-workflow-release.lock"
EXTERNAL_CONFIG="/www/wwwroot/digital-doctor-hy3.yml"
EXPECTED_EXTERNAL_CONFIG_ARGUMENT="--spring.config.additional-location=file:${EXTERNAL_CONFIG}"

# These are release authority, not defaults. The caller must repeat the
# baseline JAR SHA as a confirmation, but cannot select a different baseline.
PINNED_BASELINE_APP_SHA="9f98d5bb0dbbf5061a1f731bdee5eaacb135ec2081a90816c7e993b9dbb13503"
PINNED_EXTERNAL_CONFIG_SHA="f01266d3d7a6094e5205028ec1380bd1ad8d9d09c8e4f73c79ed1e42aab254f8"
PINNED_PROCESS_CMD_SHA="1b3fe4e5f6b3129b703a1d1625665df9552493425104340ec35daa483ebcc0ed"
PINNED_EXPERT_SQL_SHA="2a7b807e7eeb12e0068af1dd5084da048f759a6174b4749c1034e644ba3d284b"
PINNED_ORG_SQL_SHA="d5667d17f5289c75db1fa415200fde6800314a3a99082fa052ee7162609edf34"
PINNED_WORKFLOW_SQL_SHA="1b73bb00b601e49486cb7c23b231de84b932753f66925dbdda2b177979e4c909"
PINNED_CANDIDATE_APP_SHA="fbfc66d6aec1395e856e3b7b561c0da954ab27b1c6df3ea6deff97748e1c6177"
PINNED_PROTECTED_MANIFEST_SHA="ea5e721f43b1e1db43bc63b6f519622a1dacae5288e90dc6fa669c390af62487"
PINNED_INJECTED_MANIFEST_SHA="8f50afa7621581bb33f1767247b90f2bba320c96a8ddf859adb18c5952889c10"
# Filled after the fixed hook embedded at the end of this file is finalized.
PINNED_ROLLBACK_HOOK_SHA="971bcc689f7dab4a0f178dc7861cc7dc75b13fad29534ac09d830a64f7c6efd9"

MODE=""
RELEASE_DIR=""
SQL_DIR=""
EXPECTED_APP_SHA=""

TEMP_DIR=""
MYSQL_CNF=""
DUMP_PATH=""
APP_STOPPED="no"
MIGRATION_STARTED="no"
MIGRATION_VERIFIED="no"
DRILL_DB=""
DRILL_CREATED="no"

usage() {
  cat >&2 <<'EOF'
Usage:
  deploy_copywriting_workflow_database_remote.sh plan \
    --release-dir /absolute/release/version \
    --sql-dir /absolute/path/to/release-inputs \
    --expected-app-sha <64-character-sha256>

  deploy_copywriting_workflow_database_remote.sh apply \
    --release-dir /absolute/release/version \
    --sql-dir /absolute/path/to/release-inputs \
    --expected-app-sha <64-character-sha256>

  deploy_copywriting_workflow_database_remote.sh restore-drill \
    --release-dir /absolute/release/version \
    --sql-dir /absolute/path/to/release-inputs \
    --expected-app-sha <64-character-sha256>

"preflight" is accepted as an alias for "plan". Plan/preflight is read-only.
Restore-drill keeps the application online, makes a consistent temporary dump,
fully restores it into a uniquely named isolated database, verifies key counts,
and removes the isolated database. It never targets the production database.
Apply stops the current application, makes and verifies a full database dump,
runs the three reviewed migrations, and leaves the application stopped.
The input directory must also contain copywriting_workflow_database_rollback.sh.
EOF
}

fail() {
  echo "ERROR: $*" >&2
  exit 1
}

cleanup_and_report() {
  local exit_code=$?
  trap - EXIT

  if [[ "$MODE" == "restore-drill" && "$DRILL_CREATED" == "yes" && -n "$DRILL_DB" && -f "$MYSQL_CNF" ]]; then
    if mysql --defaults-extra-file="$MYSQL_CNF" --execute "DROP DATABASE IF EXISTS \`$DRILL_DB\`;"; then
      DRILL_CREATED="no"
      echo "RESTORE_DRILL_CLEANUP=isolated_database_removed" >&2
    else
      echo "RESTORE_DRILL_CLEANUP_FAILED=$DRILL_DB" >&2
      echo "RESTORE_DRILL_MANUAL_ACTION=drop_the_named_isolated_database_only" >&2
    fi
  fi

  if [[ -n "$TEMP_DIR" && -d "$TEMP_DIR" ]]; then
    case "$TEMP_DIR" in
      /tmp/copywriting-workflow-db.*)
        rm -rf -- "$TEMP_DIR"
        ;;
    esac
  fi

  if [[ $exit_code -ne 0 && "$MODE" == "apply" ]]; then
    echo "DATABASE_STAGE_FAILED=yes" >&2
    echo "APPLICATION_RESTARTED_BY_THIS_SCRIPT=no" >&2
    echo "APPLICATION_STOPPED=${APP_STOPPED}" >&2
    if [[ "$MIGRATION_STARTED" == "yes" && "$MIGRATION_VERIFIED" != "yes" ]]; then
      echo "DATABASE_RECOVERY_REQUIRED=yes" >&2
      echo "DATABASE_AUTO_RESTORE_ATTEMPTED=no" >&2
      if [[ -n "$DUMP_PATH" ]]; then
        echo "DATABASE_RECOVERY_DUMP=$DUMP_PATH" >&2
      fi
      echo "DATABASE_RECOVERY_ACTION=must_restore_the_verified_full_dump_before_retrying" >&2
    else
      echo "DATABASE_RECOVERY_REQUIRED=no_database_migration_started" >&2
    fi
  fi

  exit "$exit_code"
}
trap cleanup_and_report EXIT

[[ $# -ge 1 ]] || {
  usage
  exit 2
}
[[ "$EUID" == "0" ]] || fail "this release gate must run as root"

MODE="$1"
shift
case "$MODE" in
  plan|preflight)
    MODE="plan"
    ;;
  apply)
    ;;
  restore-drill)
    ;;
  -h|--help)
    usage
    exit 0
    ;;
  *)
    usage
    fail "mode must be plan, preflight, restore-drill, or apply"
    ;;
esac

while [[ $# -gt 0 ]]; do
  case "$1" in
    --release-dir)
      [[ $# -ge 2 ]] || fail "--release-dir requires a value"
      RELEASE_DIR="$2"
      shift 2
      ;;
    --sql-dir)
      [[ $# -ge 2 ]] || fail "--sql-dir requires a value"
      SQL_DIR="$2"
      shift 2
      ;;
    --expected-app-sha)
      [[ $# -ge 2 ]] || fail "--expected-app-sha requires a value"
      EXPECTED_APP_SHA="${2,,}"
      shift 2
      ;;
    -h|--help)
      usage
      exit 0
      ;;
    *)
      usage
      fail "unknown argument: $1"
      ;;
  esac
done

[[ -n "$RELEASE_DIR" ]] || fail "--release-dir is required"
[[ -n "$SQL_DIR" ]] || fail "--sql-dir is required"
[[ "$RELEASE_DIR" == /* ]] || fail "--release-dir must be an absolute path"
[[ "$SQL_DIR" == /* ]] || fail "--sql-dir must be an absolute path"
[[ "$RELEASE_DIR" != *$'\n'* && "$RELEASE_DIR" != *$'\r'* ]] || fail "--release-dir contains a control character"
[[ "$SQL_DIR" != *$'\n'* && "$SQL_DIR" != *$'\r'* ]] || fail "--sql-dir contains a control character"
[[ "$RELEASE_DIR" =~ ^/[A-Za-z0-9._/-]+$ ]] || fail "--release-dir may contain only safe ASCII path characters"
[[ "$EXPECTED_APP_SHA" =~ ^[0-9a-f]{64}$ ]] || fail "--expected-app-sha must be a SHA-256 digest"
[[ "$EXPECTED_APP_SHA" == "$PINNED_BASELINE_APP_SHA" ]] || fail "--expected-app-sha does not equal this release's pinned baseline"
[[ ! -L "$RELEASE_DIR" ]] || fail "--release-dir must not be a symlink"
RELEASE_DIR="$(readlink -m -- "$RELEASE_DIR")"

case "$RELEASE_DIR" in
  /|/root|/tmp|/var|/www|/www/wwwroot)
    fail "--release-dir is too broad; use a unique version directory"
    ;;
  /www/wwwroot/*)
    fail "--release-dir must not be inside the public application web root"
    ;;
esac

[[ -d "$SQL_DIR" && ! -L "$SQL_DIR" ]] || fail "--sql-dir must be an existing, non-symlink directory"
SQL_DIR="$(readlink -f -- "$SQL_DIR")"

EXPERT_SQL="$SQL_DIR/add_copywriting_expert_assignment.sql"
ORG_SQL="$SQL_DIR/add_copywriting_organization_admin_assignment.sql"
WORKFLOW_SQL="$SQL_DIR/migrate_copywriting_author_first_workflow.sql"
ROLLBACK_HOOK_SOURCE="$SQL_DIR/copywriting_workflow_database_rollback.sh"
MIGRATION_FILES=("$EXPERT_SQL" "$ORG_SQL" "$WORKFLOW_SQL")

for sql_file in "${MIGRATION_FILES[@]}"; do
  [[ -f "$sql_file" && ! -L "$sql_file" && -s "$sql_file" ]] || fail "missing required SQL file: $sql_file"
done
[[ -f "$ROLLBACK_HOOK_SOURCE" && ! -L "$ROLLBACK_HOOK_SOURCE" && -s "$ROLLBACK_HOOK_SOURCE" ]] || fail "missing fixed rollback hook source: $ROLLBACK_HOOK_SOURCE"

sha_file() {
  sha256sum "$1" | awk '{print $1}'
}

[[ "$(sha_file "$EXPERT_SQL")" == "$PINNED_EXPERT_SQL_SHA" ]] || fail "expert assignment SQL is not the pinned reviewed input"
[[ "$(sha_file "$ORG_SQL")" == "$PINNED_ORG_SQL_SHA" ]] || fail "organization assignment SQL is not the pinned reviewed input"
[[ "$(sha_file "$WORKFLOW_SQL")" == "$PINNED_WORKFLOW_SQL_SHA" ]] || fail "workflow migration SQL is not the pinned reviewed input"
[[ "$(sha_file "$ROLLBACK_HOOK_SOURCE")" == "$PINNED_ROLLBACK_HOOK_SHA" ]] || fail "rollback hook is not the pinned reviewed implementation"

for command_name in awk basename cat chmod cmp cp date df flock grep gzip head install kill mktemp mv mysql mysqldump pgrep readlink rm sed seq sha256sum sleep sort ss stat sync tail unzip; do
  command -v "$command_name" >/dev/null 2>&1 || fail "required command is unavailable: $command_name"
done

[[ -s "$APP" && ! -L "$APP" ]] || fail "production application JAR is missing or is a symlink"
[[ -s "$EXTERNAL_CONFIG" && ! -L "$EXTERNAL_CONFIG" ]] || fail "pinned external Spring configuration is missing or is a symlink"

find_server_pid() {
  local -a pids=()
  mapfile -t pids < <(
    ss -lntp 2>/dev/null |
      sed -n "s/.*:${PORT} .*pid=\([0-9][0-9]*\).*/\1/p" |
      sort -u
  )
  if [[ ${#pids[@]} -eq 0 ]]; then
    return 0
  fi
  [[ ${#pids[@]} -eq 1 ]] || fail "multiple processes are associated with application port $PORT"
  printf '%s' "${pids[0]}"
}

wait_for_exit() {
  local pid="$1"
  local attempt
  for attempt in $(seq 1 60); do
    kill -0 "$pid" 2>/dev/null || return 0
    sleep 1
  done
  return 1
}

pid_cmdline_has_app() {
  local pid="$1"
  local arg
  local -a command_line=()
  while IFS= read -r -d '' arg; do
    command_line+=("$arg")
  done < "/proc/$pid/cmdline"
  for arg in "${command_line[@]}"; do
    if [[ "$arg" == "$APP" ]]; then
      return 0
    fi
  done
  return 1
}

PID="$(find_server_pid)"
[[ -n "$PID" && -r "/proc/$PID/cmdline" && -r "/proc/$PID/environ" ]] || fail "cannot identify the application process listening on port $PORT"
pid_cmdline_has_app "$PID" || fail "the process on port $PORT is not the expected application"

verify_release_inputs() {
  [[ "$(sha_file "$APP")" == "$PINNED_BASELINE_APP_SHA" ]] || fail "production JAR SHA-256 does not match the pinned baseline"
  [[ "$(sha_file "$EXTERNAL_CONFIG")" == "$PINNED_EXTERNAL_CONFIG_SHA" ]] || fail "external Spring configuration SHA-256 mismatch"
  [[ "$(sha_file "$EXPERT_SQL")" == "$PINNED_EXPERT_SQL_SHA" ]] || fail "expert assignment SQL SHA-256 mismatch"
  [[ "$(sha_file "$ORG_SQL")" == "$PINNED_ORG_SQL_SHA" ]] || fail "organization assignment SQL SHA-256 mismatch"
  [[ "$(sha_file "$WORKFLOW_SQL")" == "$PINNED_WORKFLOW_SQL_SHA" ]] || fail "workflow migration SQL SHA-256 mismatch"
  [[ "$(sha_file "$ROLLBACK_HOOK_SOURCE")" == "$PINNED_ROLLBACK_HOOK_SHA" ]] || fail "rollback hook SHA-256 mismatch"
}

verify_release_inputs
CURRENT_APP_SHA="$(sha_file "$APP")"
CURRENT_PROCESS_CMD_SHA="$(sha_file "/proc/$PID/cmdline")"
[[ "$CURRENT_PROCESS_CMD_SHA" == "$PINNED_PROCESS_CMD_SHA" ]] || fail "application command line SHA-256 does not match the reviewed production launch configuration"
[[ "$(stat -c '%u:%a' "$APP")" == "0:600" ]] || fail "production JAR must be root-owned mode 600"
[[ "$(stat -c '%u:%a' "$EXTERNAL_CONFIG")" == "0:600" ]] || fail "external Spring configuration must be root-owned mode 600"

PROCESS_CWD="$(readlink -f "/proc/$PID/cwd")"
PROCESS_EXE="$(readlink -f "/proc/$PID/exe")"
[[ -n "$PROCESS_CWD" && -d "$PROCESS_CWD" && -n "$PROCESS_EXE" ]] || fail "cannot capture the application launch configuration"

PROCESS_CMD=()
while IFS= read -r -d '' item; do
  PROCESS_CMD+=("$item")
done < "/proc/$PID/cmdline"
[[ "${#PROCESS_CMD[@]}" -gt 0 ]] || fail "captured application command line is empty"

app_argument_count=0
external_config_argument_count=0
for item in "${PROCESS_CMD[@]}"; do
  [[ "$item" == "$APP" ]] && app_argument_count=$((app_argument_count + 1))
  [[ "$item" == "$EXPECTED_EXTERNAL_CONFIG_ARGUMENT" ]] && external_config_argument_count=$((external_config_argument_count + 1))
done
[[ "$app_argument_count" == "1" ]] || fail "application command line must reference the exact production JAR once"
[[ "$external_config_argument_count" == "1" ]] || fail "application command line must contain the exact pinned external-config argument once"

PROCESS_ENV=()
while IFS= read -r -d '' item; do
  PROCESS_ENV+=("$item")
done < "/proc/$PID/environ"

TEMP_DIR="$(mktemp -d /tmp/copywriting-workflow-db.XXXXXX)"
chmod 700 "$TEMP_DIR"
APP_CONFIG="$TEMP_DIR/application-dev.yml"
EXTERNAL_CONFIG_COPY="$TEMP_DIR/digital-doctor-hy3.yml"
unzip -p "$APP" BOOT-INF/classes/application-dev.yml > "$APP_CONFIG"
[[ -s "$APP_CONFIG" ]] || fail "application-dev.yml could not be extracted from the production JAR"
install -m 600 "$EXTERNAL_CONFIG" "$EXTERNAL_CONFIG_COPY"
[[ "$(sha_file "$EXTERNAL_CONFIG_COPY")" == "$PINNED_EXTERNAL_CONFIG_SHA" ]] || fail "external configuration changed while being read"
chmod 600 "$APP_CONFIG"

env_value() {
  local wanted="$1"
  local entry
  for entry in "${PROCESS_ENV[@]}"; do
    if [[ "$entry" == "$wanted="* ]]; then
      printf '%s' "${entry#*=}"
      return 0
    fi
  done
  return 1
}

yaml_config_value() {
  local config_file="$1"
  local section="$2"
  local wanted="$3"
  awk -v section="$section" -v wanted="$wanted" '
      $0 ~ "^  " section ":[[:space:]]*$" { inside=1; next }
      inside && /^  [^ ]/ { exit }
      inside && index($0, "    " wanted ":") == 1 {
        sub(/^[^:]*:[[:space:]]*/, "")
        sub(/[[:space:]]*$/, "")
        print
        exit
      }
    ' "$config_file"
}

spring_config_value() {
  local section="$1"
  local wanted="$2"
  local value
  value="$(yaml_config_value "$EXTERNAL_CONFIG_COPY" "$section" "$wanted")"
  if [[ -n "$value" ]]; then
    printf '%s' "$value"
  else
    yaml_config_value "$APP_CONFIG" "$section" "$wanted"
  fi
}

normalize_scalar() {
  local scalar="$1"
  local key fallback value
  if [[ ${#scalar} -ge 2 ]]; then
    if [[ "${scalar:0:1}" == '"' && "${scalar: -1}" == '"' ]]; then
      scalar="${scalar:1:${#scalar}-2}"
    elif [[ "${scalar:0:1}" == "'" && "${scalar: -1}" == "'" ]]; then
      scalar="${scalar:1:${#scalar}-2}"
    fi
  fi

  if [[ "$scalar" =~ ^\$\{([A-Za-z_][A-Za-z0-9_]*):(.*)\}$ ]]; then
    key="${BASH_REMATCH[1]}"
    fallback="${BASH_REMATCH[2]}"
  elif [[ "$scalar" =~ ^\$\{([A-Za-z_][A-Za-z0-9_]*)\}$ ]]; then
    key="${BASH_REMATCH[1]}"
    fallback=""
  else
    printf '%s' "$scalar"
    return 0
  fi

  if value="$(env_value "$key")"; then
    printf '%s' "$value"
  else
    printf '%s' "$fallback"
  fi
}

if runtime_url="$(env_value SPRING_DATASOURCE_URL)"; then
  URL="$runtime_url"
else
  URL="$(normalize_scalar "$(spring_config_value datasource url)")"
fi
if runtime_user="$(env_value SPRING_DATASOURCE_USERNAME)"; then
  DB_USER="$runtime_user"
else
  DB_USER="$(normalize_scalar "$(spring_config_value datasource username)")"
fi
if runtime_pass="$(env_value SPRING_DATASOURCE_PASSWORD)"; then
  DB_PASS="$runtime_pass"
else
  DB_PASS="$(normalize_scalar "$(spring_config_value datasource password)")"
fi

[[ "$URL" =~ ^jdbc:mysql://([^/:?]+)(:([0-9]+))?/([^?]+) ]] || fail "unsupported datasource URL"
DB_HOST="${BASH_REMATCH[1]}"
DB_PORT="${BASH_REMATCH[3]:-3306}"
DB_NAME="${BASH_REMATCH[4]}"
[[ "$DB_NAME" =~ ^[A-Za-z0-9_\$-]+$ ]] || fail "database name contains unsupported characters"
[[ -n "$DB_USER" && -n "$DB_PASS" ]] || fail "datasource credentials could not be resolved"

MYSQL_CNF="$TEMP_DIR/mysql-client.cnf"

mysql_option_value() {
  local value="$1"
  value="${value//\\/\\\\}"
  value="${value//\"/\\\"}"
  value="${value//$'\n'/\\n}"
  value="${value//$'\r'/\\r}"
  value="${value//$'\t'/\\t}"
  printf '"%s"' "$value"
}

{
  echo '[client]'
  printf 'host=%s\n' "$(mysql_option_value "$DB_HOST")"
  printf 'port=%s\n' "$(mysql_option_value "$DB_PORT")"
  printf 'user=%s\n' "$(mysql_option_value "$DB_USER")"
  printf 'password=%s\n' "$(mysql_option_value "$DB_PASS")"
  echo 'default-character-set=utf8mb4'
} > "$MYSQL_CNF"
chmod 600 "$MYSQL_CNF"

# Credentials now live only in a root-readable temporary option file and are
# intentionally never printed or copied into the release directory.
unset DB_PASS DB_USER URL runtime_pass runtime_user runtime_url

MYSQL_SERVER=(mysql --defaults-extra-file="$MYSQL_CNF" --batch --skip-column-names --raw)
MYSQL=("${MYSQL_SERVER[@]}" "$DB_NAME")
MYSQL_MIGRATE=(mysql --defaults-extra-file="$MYSQL_CNF" --show-warnings --abort-source-on-error "$DB_NAME")
MYSQLDUMP=(mysqldump --defaults-extra-file="$MYSQL_CNF")
MYSQLDUMP_HELP="$TEMP_DIR/mysqldump.help"
mysqldump --help > "$MYSQLDUMP_HELP"
chmod 600 "$MYSQLDUMP_HELP"
MYSQLDUMP_COMMON_ARGS=(
  --single-transaction
  --quick
  --routines
  --triggers
  --events
  --hex-blob
  --no-tablespaces
)
MYSQLDUMP_GTID_PURGED_OFF_SUPPORTED=no
if grep -Fq -- '--set-gtid-purged' "$MYSQLDUMP_HELP"; then
  MYSQLDUMP_COMMON_ARGS+=(--set-gtid-purged=OFF)
  MYSQLDUMP_GTID_PURGED_OFF_SUPPORTED=yes
fi

sql() {
  "${MYSQL[@]}" --execute "$1"
}

[[ "$(sql 'SELECT 1;')" == "1" ]] || fail "database connectivity check failed"

for required_table in t_copywriting t_copywriting_approval_flow t_copywriting_approval_history t_user t_role t_user_role_map; do
  table_count="$(sql "SELECT COUNT(*) FROM information_schema.tables WHERE table_schema=DATABASE() AND table_name='${required_table}';")"
  [[ "$table_count" == "1" ]] || fail "required database table is missing: $required_table"
done

MIGRATION_PREDICTION_SQL="$(cat <<'SQL'
SELECT CONCAT('MIGRATE_STATUS1_TO_AI=', COUNT(*))
FROM t_copywriting_approval_flow flow
WHERE flow.current_status = 1
  AND EXISTS (SELECT 1 FROM t_copywriting c WHERE c.copywriting_id = flow.copywriting_id)
  AND EXISTS (SELECT 1 FROM t_copywriting_approval_history h WHERE h.copywriting_approval_flow_id=flow.copywriting_approval_flow_id AND h.from_status=0 AND h.to_status=1 AND h.operation_status=1)
  AND NOT EXISTS (SELECT 1 FROM t_copywriting_approval_history h WHERE h.copywriting_approval_flow_id=flow.copywriting_approval_flow_id AND h.from_status=2 AND h.to_status=1 AND h.operation_status=1)
  AND NOT EXISTS (SELECT 1 FROM t_copywriting_approval_history h WHERE h.copywriting_approval_flow_id=flow.copywriting_approval_flow_id AND h.from_status=2 AND h.to_status=3 AND h.operation_status=1);
SELECT CONCAT('MIGRATE_STATUS1_TO_AUTHOR=', COUNT(*))
FROM t_copywriting_approval_flow flow
WHERE flow.current_status = 1
  AND EXISTS (SELECT 1 FROM t_copywriting c WHERE c.copywriting_id = flow.copywriting_id)
  AND NOT EXISTS (SELECT 1 FROM t_copywriting_approval_history h WHERE h.copywriting_approval_flow_id=flow.copywriting_approval_flow_id AND h.from_status=0 AND h.to_status=1 AND h.operation_status=1)
  AND NOT EXISTS (SELECT 1 FROM t_copywriting_approval_history h WHERE h.copywriting_approval_flow_id=flow.copywriting_approval_flow_id AND h.from_status=2 AND h.to_status=1 AND h.operation_status=1)
  AND NOT EXISTS (SELECT 1 FROM t_copywriting_approval_history h WHERE h.copywriting_approval_flow_id=flow.copywriting_approval_flow_id AND h.from_status=2 AND h.to_status=3 AND h.operation_status=1);
SELECT CONCAT('MIGRATE_STATUS2_TO_AUTHOR=', COUNT(*))
FROM t_copywriting_approval_flow flow
WHERE flow.current_status = 2
  AND EXISTS (SELECT 1 FROM t_copywriting c WHERE c.copywriting_id = flow.copywriting_id)
  AND NOT EXISTS (SELECT 1 FROM t_copywriting_approval_history h WHERE h.copywriting_approval_flow_id=flow.copywriting_approval_flow_id AND h.from_status=0 AND h.to_status=2 AND h.operation_status=1)
  AND NOT EXISTS (SELECT 1 FROM t_copywriting_approval_history h WHERE h.copywriting_approval_flow_id=flow.copywriting_approval_flow_id AND h.from_status=1 AND h.to_status=2 AND h.operation_status=1)
  AND NOT EXISTS (SELECT 1 FROM t_copywriting_approval_history h WHERE h.copywriting_approval_flow_id=flow.copywriting_approval_flow_id AND h.from_status=0 AND h.to_status=1 AND h.operation_status=1);
SELECT CONCAT('MIGRATE_STATUS3_TO_AI=', COUNT(*))
FROM t_copywriting_approval_flow flow
WHERE flow.current_status = 3
  AND EXISTS (SELECT 1 FROM t_copywriting c WHERE c.copywriting_id = flow.copywriting_id)
  AND EXISTS (SELECT 1 FROM t_copywriting_approval_history h WHERE h.copywriting_approval_flow_id=flow.copywriting_approval_flow_id AND h.from_status=2 AND h.to_status=0 AND h.operation_status=1)
  AND EXISTS (SELECT 1 FROM t_copywriting_approval_history h WHERE h.copywriting_approval_flow_id=flow.copywriting_approval_flow_id AND h.from_status=0 AND h.to_status=1 AND h.operation_status=1)
  AND EXISTS (SELECT 1 FROM t_copywriting_approval_history h WHERE h.copywriting_approval_flow_id=flow.copywriting_approval_flow_id AND h.from_status=1 AND h.to_status=3 AND h.operation_status=1)
  AND NOT EXISTS (SELECT 1 FROM t_copywriting_approval_history h WHERE h.copywriting_approval_flow_id=flow.copywriting_approval_flow_id AND h.from_status=0 AND h.to_status=2 AND h.operation_status=1)
  AND NOT EXISTS (SELECT 1 FROM t_copywriting_approval_history h WHERE h.copywriting_approval_flow_id=flow.copywriting_approval_flow_id AND h.from_status=2 AND h.to_status=1 AND h.operation_status=1);
SELECT CONCAT('MIGRATE_STATUS3_TO_EXPERT=', COUNT(*))
FROM t_copywriting_approval_flow flow
WHERE flow.current_status = 3
  AND EXISTS (SELECT 1 FROM t_copywriting c WHERE c.copywriting_id = flow.copywriting_id)
  AND EXISTS (SELECT 1 FROM t_copywriting_approval_history h WHERE h.copywriting_approval_flow_id=flow.copywriting_approval_flow_id AND h.from_status=2 AND h.to_status=3 AND h.operation_status=1)
  AND NOT EXISTS (SELECT 1 FROM t_copywriting_approval_history h WHERE h.copywriting_approval_flow_id=flow.copywriting_approval_flow_id AND h.from_status=1 AND h.to_status=3 AND h.operation_status=1);
SQL
)"

state_report() {
  local destination="$1"
  local temporary="${destination}.tmp"
  local expert_exists org_exists

  expert_exists="$(sql "SELECT COUNT(*) FROM information_schema.tables WHERE table_schema=DATABASE() AND table_name='t_copywriting_expert_assignment';")"
  org_exists="$(sql "SELECT COUNT(*) FROM information_schema.tables WHERE table_schema=DATABASE() AND table_name='t_copywriting_organization_admin_assignment';")"

  {
    sql "
SELECT CONCAT('DATABASE_SIZE_BYTES=', COALESCE(SUM(data_length + index_length), 0))
FROM information_schema.tables WHERE table_schema=DATABASE();
SELECT CONCAT('COPYWRITING_TOTAL=', COUNT(*)) FROM t_copywriting;
SELECT CONCAT('FLOW_TOTAL=', COUNT(*)) FROM t_copywriting_approval_flow;
SELECT CONCAT('HISTORY_TOTAL=', COUNT(*)) FROM t_copywriting_approval_history;
SELECT CONCAT('FLOW_STATUS_0=', COALESCE(SUM(current_status=0), 0)) FROM t_copywriting_approval_flow;
SELECT CONCAT('FLOW_STATUS_1=', COALESCE(SUM(current_status=1), 0)) FROM t_copywriting_approval_flow;
SELECT CONCAT('FLOW_STATUS_2=', COALESCE(SUM(current_status=2), 0)) FROM t_copywriting_approval_flow;
SELECT CONCAT('FLOW_STATUS_3=', COALESCE(SUM(current_status=3), 0)) FROM t_copywriting_approval_flow;
SELECT CONCAT('FLOW_STATUS_4=', COALESCE(SUM(current_status=4), 0)) FROM t_copywriting_approval_flow;
SELECT CONCAT('FLOW_STATUS_5=', COALESCE(SUM(current_status=5), 0)) FROM t_copywriting_approval_flow;
SELECT CONCAT('ORPHAN_FLOW_TOTAL=', COUNT(*))
FROM t_copywriting_approval_flow flow
LEFT JOIN t_copywriting c ON c.copywriting_id=flow.copywriting_id
WHERE c.copywriting_id IS NULL;
SELECT CONCAT('ORPHAN_FLOW_STATUS_', flow.current_status, '=', COUNT(*))
FROM t_copywriting_approval_flow flow
LEFT JOIN t_copywriting c ON c.copywriting_id=flow.copywriting_id
WHERE c.copywriting_id IS NULL
GROUP BY flow.current_status ORDER BY flow.current_status;
SELECT CONCAT('STATUS0_WRONG_PROCESSOR=', COUNT(*))
FROM t_copywriting_approval_flow flow
INNER JOIN t_copywriting c ON c.copywriting_id=flow.copywriting_id
WHERE flow.current_status=0
  AND (flow.current_processor_id IS NULL OR flow.current_processor_id<>c.user_id);"
    echo "TABLE_EXPERT_ASSIGNMENT=$expert_exists"
    if [[ "$expert_exists" == "1" ]]; then
      echo "EXPERT_ASSIGNMENT_ROWS=$(sql 'SELECT COUNT(*) FROM t_copywriting_expert_assignment;')"
    else
      echo "EXPERT_ASSIGNMENT_ROWS=0"
    fi
    echo "TABLE_ORG_ASSIGNMENT=$org_exists"
    if [[ "$org_exists" == "1" ]]; then
      echo "ORG_ASSIGNMENT_ROWS=$(sql 'SELECT COUNT(*) FROM t_copywriting_organization_admin_assignment;')"
    else
      echo "ORG_ASSIGNMENT_ROWS=0"
    fi
    sql "$MIGRATION_PREDICTION_SQL"
  } > "$temporary"
  chmod 600 "$temporary"
  mv -f -- "$temporary" "$destination"
}

PLAN_REPORT="$TEMP_DIR/plan-state.txt"
state_report "$PLAN_REPORT"

echo "DATABASE_RELEASE_PLAN_BEGIN"
echo "MODE=$MODE"
echo "RELEASE_DIR=$RELEASE_DIR"
echo "SQL_DIR=$SQL_DIR"
echo "APP=$APP"
echo "APP_SHA256=$CURRENT_APP_SHA"
echo "APP_PID=$PID"
echo "APP_PROCESS_CAPTUREABLE=yes"
echo "APP_PROCESS_CMD_SHA256=$CURRENT_PROCESS_CMD_SHA"
echo "EXTERNAL_CONFIG=$EXTERNAL_CONFIG"
echo "EXTERNAL_CONFIG_SHA256=$PINNED_EXTERNAL_CONFIG_SHA"
echo "EXTERNAL_CONFIG_ARGUMENT_VERIFIED=yes"
echo "MYSQL_REACHABLE=yes"
echo "MYSQLDUMP_GTID_PURGED_OFF_SUPPORTED=$MYSQLDUMP_GTID_PURGED_OFF_SUPPORTED"
echo "SQL_EXPERT_SHA256=$(sha_file "$EXPERT_SQL")"
echo "SQL_ORG_SHA256=$(sha_file "$ORG_SQL")"
echo "SQL_WORKFLOW_SHA256=$(sha_file "$WORKFLOW_SQL")"
echo "ROLLBACK_HOOK_SOURCE_SHA256=$(sha_file "$ROLLBACK_HOOK_SOURCE")"
cat "$PLAN_REPORT"
echo "APPLICATION_ACTION=$([[ "$MODE" == "apply" ]] && echo stop_and_leave_stopped || echo none)"
echo "DATABASE_RELEASE_PLAN_END"

if [[ "$MODE" == "plan" ]]; then
  exit 0
fi

if [[ "$MODE" == "restore-drill" ]]; then
  exec 9>"$LOCK_FILE"
  flock -n 9 || fail "another copywriting workflow database release is running"
  umask 077

  install -d -m 700 "$RELEASE_DIR"
  RELEASE_DIR="$(readlink -f -- "$RELEASE_DIR")"
  DRILL_MARKER="$RELEASE_DIR/database-restore-drill.complete"
  [[ ! -e "$DRILL_MARKER" && ! -L "$DRILL_MARKER" ]] || fail "restore-drill marker already exists; use a new version directory"

  DRILL_DB="copywriting_restore_drill_$(date -u +%Y%m%d%H%M%S)_$$"
  [[ "$DRILL_DB" =~ ^[A-Za-z0-9_]+$ ]] || fail "generated restore-drill database name is unsafe"
  [[ "$("${MYSQL_SERVER[@]}" --execute "SELECT COUNT(*) FROM information_schema.schemata WHERE schema_name='$DRILL_DB';")" == "0" ]] || fail "generated restore-drill database already exists"

  schema_metadata="$(sql "SELECT CONCAT(default_character_set_name, CHAR(9), default_collation_name) FROM information_schema.schemata WHERE schema_name=DATABASE();")"
  schema_charset="${schema_metadata%%$'\t'*}"
  schema_collation="${schema_metadata#*$'\t'}"
  [[ "$schema_charset" =~ ^[A-Za-z0-9_]+$ && "$schema_collation" =~ ^[A-Za-z0-9_]+$ ]] || fail "source database charset metadata is unsafe"

  echo "RESTORE_DRILL_BEGIN=yes"
  echo "RESTORE_DRILL_SOURCE_DATABASE=$DB_NAME"
  echo "RESTORE_DRILL_ISOLATED_DATABASE=$DRILL_DB"
  # Set the cleanup intent before CREATE so every normal shell failure after
  # the statement attempts DROP DATABASE IF EXISTS for this generated name.
  DRILL_CREATED="yes"
  "${MYSQL_SERVER[@]}" --execute "CREATE DATABASE \`$DRILL_DB\` CHARACTER SET $schema_charset COLLATE $schema_collation;"

  DRILL_DUMP="$TEMP_DIR/restore-drill.sql.gz"
  if ! "${MYSQLDUMP[@]}" \
      "${MYSQLDUMP_COMMON_ARGS[@]}" \
      "$DB_NAME" |
      gzip -1 > "$DRILL_DUMP"; then
    fail "restore-drill source dump failed"
  fi
  gzip -t "$DRILL_DUMP" || fail "restore-drill dump failed gzip integrity verification"
  [[ "$(gzip -cd "$DRILL_DUMP" | tail -n 1)" == "-- Dump completed on "* ]] || fail "restore-drill dump completion marker is absent"
  DRILL_SQL_FILE="$TEMP_DIR/restore-drill.sql"
  gzip -cd "$DRILL_DUMP" > "$DRILL_SQL_FILE"
  chmod 600 "$DRILL_SQL_FILE"
  if grep -Eq '^(CREATE DATABASE|DROP DATABASE|USE[[:space:]]+`)' "$DRILL_SQL_FILE"; then
    fail "restore-drill dump contains database-selection statements"
  fi

  if ! "${MYSQL_SERVER[@]}" "$DRILL_DB" < "$DRILL_SQL_FILE"; then
    fail "full restore into the isolated database failed"
  fi

  drill_sql() {
    "${MYSQL_SERVER[@]}" "$DRILL_DB" --execute "$1"
  }
  source_tables="$(sql 'SELECT COUNT(*) FROM information_schema.tables WHERE table_schema=DATABASE();')"
  drill_tables="$(drill_sql 'SELECT COUNT(*) FROM information_schema.tables WHERE table_schema=DATABASE();')"
  [[ "$drill_tables" == "$source_tables" ]] || fail "restore-drill table/view count mismatch"
  for required_table in t_copywriting t_copywriting_approval_flow t_copywriting_approval_history t_user t_role t_user_role_map; do
    source_rows="$(sql "SELECT COUNT(*) FROM \`$required_table\`;")"
    drill_rows="$(drill_sql "SELECT COUNT(*) FROM \`$required_table\`;")"
    [[ "$drill_rows" == "$source_rows" ]] || fail "restore-drill row-count mismatch for $required_table"
  done

  source_routines="$(sql 'SELECT COUNT(*) FROM information_schema.routines WHERE routine_schema=DATABASE();')"
  drill_routines="$(drill_sql 'SELECT COUNT(*) FROM information_schema.routines WHERE routine_schema=DATABASE();')"
  source_triggers="$(sql 'SELECT COUNT(*) FROM information_schema.triggers WHERE trigger_schema=DATABASE();')"
  drill_triggers="$(drill_sql 'SELECT COUNT(*) FROM information_schema.triggers WHERE trigger_schema=DATABASE();')"
  source_events="$(sql 'SELECT COUNT(*) FROM information_schema.events WHERE event_schema=DATABASE();')"
  drill_events="$(drill_sql 'SELECT COUNT(*) FROM information_schema.events WHERE event_schema=DATABASE();')"
  [[ "$drill_routines" == "$source_routines" ]] || fail "restore-drill routine count mismatch"
  [[ "$drill_triggers" == "$source_triggers" ]] || fail "restore-drill trigger count mismatch"
  [[ "$drill_events" == "$source_events" ]] || fail "restore-drill event count mismatch"

  "${MYSQL_SERVER[@]}" --execute "DROP DATABASE \`$DRILL_DB\`;"
  DRILL_CREATED="no"
  [[ "$("${MYSQL_SERVER[@]}" --execute "SELECT COUNT(*) FROM information_schema.schemata WHERE schema_name='$DRILL_DB';")" == "0" ]] || fail "restore-drill database was not removed"

  DRILL_MARKER_TEMP="$DRILL_MARKER.partial"
  {
    echo "RESTORE_DRILL_COMPLETE=yes"
    echo "SOURCE_DATABASE=$DB_NAME"
    echo "ISOLATED_DATABASE_REMOVED=yes"
    echo "SOURCE_DUMP_SHA256=$(sha_file "$DRILL_DUMP")"
    echo "TABLE_VIEW_COUNT=$source_tables"
    echo "ROUTINE_COUNT=$source_routines"
    echo "TRIGGER_COUNT=$source_triggers"
    echo "EVENT_COUNT=$source_events"
    echo "COMPLETED_AT_UTC=$(date -u +%Y-%m-%dT%H:%M:%SZ)"
  } > "$DRILL_MARKER_TEMP"
  chmod 600 "$DRILL_MARKER_TEMP"
  mv -f -- "$DRILL_MARKER_TEMP" "$DRILL_MARKER"
  sync
  echo "RESTORE_DRILL_COMPLETE=yes"
  echo "RESTORE_DRILL_MARKER=$DRILL_MARKER"
  echo "RESTORE_DRILL_MARKER_SHA256=$(sha_file "$DRILL_MARKER")"
  exit 0
fi

exec 9>"$LOCK_FILE"
flock -n 9 || fail "another copywriting workflow database release is running"
umask 077

install -d -m 700 "$RELEASE_DIR"
RELEASE_DIR="$(readlink -f -- "$RELEASE_DIR")"
DB_ARTIFACT_DIR="$RELEASE_DIR/database"
PROCESS_CAPTURE_DIR="$DB_ARTIFACT_DIR/process"
SNAPSHOT_SQL_DIR="$DB_ARTIFACT_DIR/sql"
RECOVERY_DIR="$DB_ARTIFACT_DIR/recovery"

[[ ! -e "$DB_ARTIFACT_DIR" && ! -L "$DB_ARTIFACT_DIR" ]] || fail "database artifact directory already exists; use a new version directory"
install -d -m 700 "$DB_ARTIFACT_DIR" "$PROCESS_CAPTURE_DIR" "$SNAPSHOT_SQL_DIR" "$RECOVERY_DIR"
chmod 700 "$RELEASE_DIR" "$DB_ARTIFACT_DIR" "$PROCESS_CAPTURE_DIR" "$SNAPSHOT_SQL_DIR" "$RECOVERY_DIR"

install -m 600 "$EXPERT_SQL" "$SNAPSHOT_SQL_DIR/add_copywriting_expert_assignment.sql"
install -m 600 "$ORG_SQL" "$SNAPSHOT_SQL_DIR/add_copywriting_organization_admin_assignment.sql"
install -m 600 "$WORKFLOW_SQL" "$SNAPSHOT_SQL_DIR/migrate_copywriting_author_first_workflow.sql"
install -m 700 "$ROLLBACK_HOOK_SOURCE" "$RECOVERY_DIR/rollback.sh"
(
  cd "$DB_ARTIFACT_DIR"
  sha256sum sql/*.sql > sql.sha256
)
chmod 600 "$DB_ARTIFACT_DIR/sql.sha256"
[[ "$(sha_file "$SNAPSHOT_SQL_DIR/add_copywriting_expert_assignment.sql")" == "$PINNED_EXPERT_SQL_SHA" ]] || fail "snapshotted expert SQL SHA-256 mismatch"
[[ "$(sha_file "$SNAPSHOT_SQL_DIR/add_copywriting_organization_admin_assignment.sql")" == "$PINNED_ORG_SQL_SHA" ]] || fail "snapshotted organization SQL SHA-256 mismatch"
[[ "$(sha_file "$SNAPSHOT_SQL_DIR/migrate_copywriting_author_first_workflow.sql")" == "$PINNED_WORKFLOW_SQL_SHA" ]] || fail "snapshotted workflow SQL SHA-256 mismatch"
[[ "$(sha_file "$RECOVERY_DIR/rollback.sh")" == "$PINNED_ROLLBACK_HOOK_SHA" ]] || fail "installed rollback hook SHA-256 mismatch"
[[ "$(stat -c '%u:%a' "$RECOVERY_DIR/rollback.sh")" == "0:700" ]] || fail "installed rollback hook must be root-owned mode 700"
verify_release_inputs

# Capture byte-for-byte launch metadata before stopping the process. The
# environment is secret-bearing and is therefore stored mode 0600 and never
# printed. The backend release stage may use these NUL-separated files.
cp --preserve=mode,timestamps "/proc/$PID/cmdline" "$PROCESS_CAPTURE_DIR/cmdline.nul"
cp --preserve=mode,timestamps "/proc/$PID/environ" "$PROCESS_CAPTURE_DIR/environ.nul"
chmod 600 "$PROCESS_CAPTURE_DIR/cmdline.nul" "$PROCESS_CAPTURE_DIR/environ.nul"
printf '%s\n' "$PID" > "$PROCESS_CAPTURE_DIR/pid.txt"
printf '%s\n' "$PROCESS_CWD" > "$PROCESS_CAPTURE_DIR/cwd.txt"
printf '%s\n' "$PROCESS_EXE" > "$PROCESS_CAPTURE_DIR/executable.txt"
readlink "/proc/$PID/fd/1" > "$PROCESS_CAPTURE_DIR/stdout-link.txt" 2>/dev/null || :
printf '%s\n' "$CURRENT_APP_SHA" > "$PROCESS_CAPTURE_DIR/app-sha256.txt"
chmod 600 "$PROCESS_CAPTURE_DIR"/*
(
  cd "$DB_ARTIFACT_DIR"
  sha256sum process/* > process-capture.sha256
)
chmod 600 "$DB_ARTIFACT_DIR/process-capture.sha256"

DB_SIZE_BYTES="$(sql "SELECT COALESCE(SUM(data_length + index_length),0) FROM information_schema.tables WHERE table_schema=DATABASE();")"
AVAILABLE_KB="$(df -Pk "$RELEASE_DIR" | awk 'NR==2 {print $4}')"
[[ "$DB_SIZE_BYTES" =~ ^[0-9]+$ && "$AVAILABLE_KB" =~ ^[0-9]+$ ]] || fail "cannot calculate backup disk-space requirements"
AVAILABLE_BYTES=$((AVAILABLE_KB * 1024))
REQUIRED_BYTES=$((DB_SIZE_BYTES * 2 + 536870912))
[[ "$AVAILABLE_BYTES" -ge "$REQUIRED_BYTES" ]] || fail "insufficient free space for the full database backup safety margin"

# Recheck both immutable release inputs immediately before freezing writes.
verify_release_inputs
[[ "$(find_server_pid)" == "$PID" ]] || fail "application PID changed after preflight"
[[ "$(sha_file "/proc/$PID/cmdline")" == "$PINNED_PROCESS_CMD_SHA" ]] || fail "application command line changed after preflight"
for original_sql in "${MIGRATION_FILES[@]}"; do
  snapshot_sql="$SNAPSHOT_SQL_DIR/$(basename "$original_sql")"
  [[ "$(sha_file "$original_sql")" == "$(sha_file "$snapshot_sql")" ]] || fail "SQL file changed while preparing the release"
done

echo "APPLICATION_STOP_BEGIN pid=$PID"
kill -TERM "$PID"
if ! wait_for_exit "$PID"; then
  fail "application did not stop after 60 seconds; no database migration was started"
fi
APP_STOPPED="yes"

require_application_stopped() {
  local listening_pid
  listening_pid="$(find_server_pid)"
  [[ -z "$listening_pid" ]] || fail "an unexpected process is listening on application port $PORT"
  if pgrep -f '[d]igital-doctor-app\.jar' >/dev/null 2>&1; then
    fail "an application process restarted while the database release was running"
  fi
}

require_application_stopped
echo "APPLICATION_STOPPED=yes"

BASELINE_REPORT="$DB_ARTIFACT_DIR/database-before.counts"
state_report "$BASELINE_REPORT"
chmod 600 "$BASELINE_REPORT"
echo "DATABASE_BASELINE_BEGIN"
cat "$BASELINE_REPORT"
echo "DATABASE_BASELINE_END"

DUMP_PATH="$DB_ARTIFACT_DIR/database-before.sql.gz"
DUMP_TEMP="$DB_ARTIFACT_DIR/database-before.sql.gz.partial"
[[ ! -e "$DUMP_TEMP" && ! -L "$DUMP_TEMP" ]] || fail "partial database backup already exists; use a new version directory"

echo "DATABASE_BACKUP_BEGIN"
if ! "${MYSQLDUMP[@]}" \
    "${MYSQLDUMP_COMMON_ARGS[@]}" \
    --add-drop-database \
    --databases "$DB_NAME" |
    gzip -9 > "$DUMP_TEMP"; then
  rm -f -- "$DUMP_TEMP"
  fail "full database backup failed; no database migration was started"
fi

gzip -t "$DUMP_TEMP" || fail "database backup failed gzip integrity verification"
DUMP_LAST_LINE="$(gzip -cd "$DUMP_TEMP" | tail -n 1)"
if [[ "$DUMP_LAST_LINE" != "-- Dump completed on "* ]]; then
  fail "database backup is missing the mysqldump completion marker"
fi
mv -f -- "$DUMP_TEMP" "$DUMP_PATH"
chmod 600 "$DUMP_PATH"
sha256sum "$DUMP_PATH" > "$DB_ARTIFACT_DIR/database-before.sql.gz.sha256"
chmod 600 "$DB_ARTIFACT_DIR/database-before.sql.gz.sha256"
echo "DATABASE_BACKUP_VERIFIED=yes"
echo "DATABASE_BACKUP_PATH=$DUMP_PATH"
echo "DATABASE_BACKUP_SHA256=$(sha_file "$DUMP_PATH")"
unset DUMP_LAST_LINE

# Persist a root-only recovery client configuration. This file contains DB
# credentials and is never logged. The fixed rollback hook validates its hash
# from a separately root-only manifest before using it.
install -m 600 "$MYSQL_CNF" "$RECOVERY_DIR/mysql-client.cnf"
[[ "$(stat -c '%u:%a' "$RECOVERY_DIR/mysql-client.cnf")" == "0:600" ]] || fail "recovery database option file must be root-owned mode 600"

require_application_stopped
[[ "$(sha_file "$APP")" == "$EXPECTED_APP_SHA" ]] || fail "production JAR changed while the database backup was running"

run_migration() {
  local label="$1"
  local migration_file="$2"
  echo "MIGRATION_BEGIN=$label"
  if ! "${MYSQL_MIGRATE[@]}" < "$migration_file"; then
    fail "migration failed: $label"
  fi
  echo "MIGRATION_OK=$label"
}

MIGRATION_STARTED="yes"
run_migration "expert_assignment" "$SNAPSHOT_SQL_DIR/add_copywriting_expert_assignment.sql"
require_application_stopped
run_migration "organization_admin_assignment" "$SNAPSHOT_SQL_DIR/add_copywriting_organization_admin_assignment.sql"
require_application_stopped
run_migration "author_first_workflow" "$SNAPSHOT_SQL_DIR/migrate_copywriting_author_first_workflow.sql"
require_application_stopped

POST_REPORT="$DB_ARTIFACT_DIR/database-after.counts"
state_report "$POST_REPORT"
echo "DATABASE_POST_STATE_BEGIN"
cat "$POST_REPORT"
echo "DATABASE_POST_STATE_END"

[[ "$(sql "SELECT COUNT(*) FROM information_schema.tables WHERE table_schema=DATABASE() AND table_name='t_copywriting_expert_assignment';")" == "1" ]] || fail "expert assignment table was not created"
[[ "$(sql "SELECT COUNT(*) FROM information_schema.tables WHERE table_schema=DATABASE() AND table_name='t_copywriting_organization_admin_assignment';")" == "1" ]] || fail "organization administrator assignment table was not created"

MIGRATION_REMAINING="$(awk -F= '/^MIGRATE_STATUS[0-9]+_/{sum += $2} END {print sum + 0}' "$POST_REPORT")"
WRONG_PROCESSOR="$(awk -F= '$1 == "STATUS0_WRONG_PROCESSOR" {print $2}' "$POST_REPORT")"
[[ "$MIGRATION_REMAINING" == "0" ]] || fail "post-migration verification found records that still require conversion"
[[ "$WRONG_PROCESSOR" == "0" ]] || fail "post-migration verification found status-0 tasks assigned to the wrong processor"

require_application_stopped
verify_release_inputs

# The first marker is intentionally complete except for the rollback fields.
# Its SHA is bound into the recovery manifest, then the final marker records
# the recovery manifest and fixed rollback implementation hashes.
APPLY_CORE="$RECOVERY_DIR/apply-core.env"
cat > "$APPLY_CORE.tmp" <<EOF
DATABASE_STAGE_COMPLETE=yes
EXPECTED_APP_SHA256=$EXPECTED_APP_SHA
ORIGINAL_APP_PID=$PID
APPLICATION_LEFT_STOPPED=yes
DATABASE_BACKUP=$DUMP_PATH
DATABASE_BACKUP_SHA256=$(sha_file "$DUMP_PATH")
DATABASE_BASELINE_COUNTS=$BASELINE_REPORT
DATABASE_BASELINE_COUNTS_SHA256=$(sha_file "$BASELINE_REPORT")
PROCESS_CAPTURE_MANIFEST_SHA256=$(sha_file "$DB_ARTIFACT_DIR/process-capture.sha256")
SQL_MANIFEST_SHA256=$(sha_file "$DB_ARTIFACT_DIR/sql.sha256")
SQL_EXPERT_SHA256=$PINNED_EXPERT_SQL_SHA
SQL_ORG_SHA256=$PINNED_ORG_SQL_SHA
SQL_WORKFLOW_SHA256=$PINNED_WORKFLOW_SQL_SHA
EXTERNAL_CONFIG_SHA256=$PINNED_EXTERNAL_CONFIG_SHA
EXTERNAL_CONFIG_PATH=$EXTERNAL_CONFIG
EXPECTED_CANDIDATE_SHA256=$PINNED_CANDIDATE_APP_SHA
PROTECTED_MANIFEST_SHA256=$PINNED_PROTECTED_MANIFEST_SHA
INJECTED_MANIFEST_SHA256=$PINNED_INJECTED_MANIFEST_SHA
MIGRATION_REMAINING=$MIGRATION_REMAINING
EOF
chmod 600 "$APPLY_CORE.tmp"
mv -f -- "$APPLY_CORE.tmp" "$APPLY_CORE"

RECOVERY_MANIFEST="$RECOVERY_DIR/manifest.env"
cat > "$RECOVERY_MANIFEST.tmp" <<EOF
MANIFEST_VERSION=1
PINNED_RELEASE_DIR=$RELEASE_DIR
PINNED_DATABASE_DIR=$DB_ARTIFACT_DIR
PINNED_DB_NAME=$DB_NAME
PINNED_BASELINE_APP_SHA=$PINNED_BASELINE_APP_SHA
PINNED_DUMP_SHA=$(sha_file "$DUMP_PATH")
PINNED_BASELINE_COUNTS_SHA=$(sha_file "$BASELINE_REPORT")
PINNED_APPLY_MARKER_SHA=$(sha_file "$APPLY_CORE")
PINNED_MYSQL_CNF_SHA=$(sha_file "$RECOVERY_DIR/mysql-client.cnf")
PINNED_EXPERT_SQL_SHA=$PINNED_EXPERT_SQL_SHA
PINNED_ORG_SQL_SHA=$PINNED_ORG_SQL_SHA
PINNED_WORKFLOW_SQL_SHA=$PINNED_WORKFLOW_SQL_SHA
EOF
chmod 600 "$RECOVERY_MANIFEST.tmp"
mv -f -- "$RECOVERY_MANIFEST.tmp" "$RECOVERY_MANIFEST"

ROLLBACK_HOOK="$RECOVERY_DIR/rollback.sh"
[[ "$(sha_file "$ROLLBACK_HOOK")" == "$PINNED_ROLLBACK_HOOK_SHA" ]] || fail "rollback hook changed after installation"
[[ "$(stat -c '%u:%a' "$ROLLBACK_HOOK")" == "0:700" ]] || fail "rollback hook must be root-owned mode 700"

cat "$APPLY_CORE" > "$DB_ARTIFACT_DIR/apply.complete.tmp"
cat >> "$DB_ARTIFACT_DIR/apply.complete.tmp" <<EOF
RECOVERY_MANIFEST=$RECOVERY_MANIFEST
RECOVERY_MANIFEST_SHA256=$(sha_file "$RECOVERY_MANIFEST")
DATABASE_ROLLBACK_HOOK=$ROLLBACK_HOOK
DATABASE_ROLLBACK_HOOK_SHA256=$PINNED_ROLLBACK_HOOK_SHA
ROLLBACK_REASON=automatic-backend-release-failure
EOF
chmod 600 "$DB_ARTIFACT_DIR/apply.complete.tmp"
mv -f -- "$DB_ARTIFACT_DIR/apply.complete.tmp" "$DB_ARTIFACT_DIR/apply.complete"
[[ "$(stat -c '%u:%a' "$DB_ARTIFACT_DIR/apply.complete")" == "0:600" ]] || fail "apply marker must be root-owned mode 600"

[[ "$(sha_file "$ROLLBACK_HOOK")" == "$PINNED_ROLLBACK_HOOK_SHA" ]] || fail "rollback hook must remain byte-for-byte pinned"
MIGRATION_VERIFIED="yes"

echo "DATABASE_STAGE_OK=yes"
echo "RELEASE_DIR=$RELEASE_DIR"
echo "PROCESS_CAPTURE_DIR=$PROCESS_CAPTURE_DIR"
echo "PROCESS_CAPTURE_MANIFEST_SHA256=$(sha_file "$DB_ARTIFACT_DIR/process-capture.sha256")"
echo "DATABASE_ROLLBACK_HOOK=$ROLLBACK_HOOK"
echo "DATABASE_ROLLBACK_HOOK_SHA256=$PINNED_ROLLBACK_HOOK_SHA"
echo "RECOVERY_MANIFEST_SHA256=$(sha_file "$RECOVERY_MANIFEST")"
echo "APPLICATION_LEFT_STOPPED=yes"
echo "APPLICATION_JAR_UNCHANGED=yes"
echo "DATABASE_AUTO_RESTORE_ATTEMPTED=no"
echo "NEXT_ACTION=run_the_pinned_backend_release_script"

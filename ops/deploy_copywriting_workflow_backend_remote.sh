#!/usr/bin/env bash
set -Eeuo pipefail

# Installs the prebuilt, locally audited copywriting-workflow candidate only.
# This script intentionally does not run the forward database migrations. It
# must be invoked after the separate database gate has completed and produced a
# pinned rollback hook capable of restoring the pre-migration database state.

APP="/www/wwwroot/digital-doctor-app.jar"
PORT="8091"
HEALTH_URL="http://127.0.0.1:${PORT}/api/admin/function/copywriting-approval/page?pageNum=1&pageSize=1"
FALLBACK_LOG="/www/wwwlogs/digital-doctor-app.log"
EXPECTED_JAVA_EXE="/www/server/java/jdk-17.0.8/bin/java"
EXPECTED_PROCESS_CWD="/root"
EXPECTED_PROCESS_CMD_SHA256="1b3fe4e5f6b3129b703a1d1625665df9552493425104340ec35daa483ebcc0ed"
EXPECTED_BASELINE_SHA256="9f98d5bb0dbbf5061a1f731bdee5eaacb135ec2081a90816c7e993b9dbb13503"
EXPECTED_CANDIDATE_SHA256="fbfc66d6aec1395e856e3b7b561c0da954ab27b1c6df3ea6deff97748e1c6177"
EXPECTED_DEV_CONFIG_SHA256="0918280810c0835d56e07bab2fa3b19a0f397498273bffb49095c099953bf65b"
EXPECTED_PROD_CONFIG_SHA256="50c66e95bb543668cdc1bee90ab82d0096e37ae85ee2e889de04d67ad848be10"
EXTERNAL_CONFIG="/www/wwwroot/digital-doctor-hy3.yml"
EXPECTED_EXTERNAL_CONFIG_SHA256="f01266d3d7a6094e5205028ec1380bd1ad8d9d09c8e4f73c79ed1e42aab254f8"
EXPECTED_DEVICE="64769"

CANDIDATE="${1:-}"
INJECTED_MANIFEST="${2:-}"
PROTECTED_MANIFEST="${3:-}"
PROCESS_CAPTURE_DIR="${4:-}"
DB_ROLLBACK_HOOK="${5:-}"
CONFIRMATION="${6:-}"

fail() {
  echo "DEPLOY_ERROR=$*" >&2
  return 1
}

sha_file() {
  sha256sum "$1" | awk '{print $1}'
}

sha_entry() {
  unzip -p "$1" "$2" | sha256sum | awk '{print $1}'
}

require_equal() {
  local actual="$1" expected="$2" label="$3"
  [[ "$actual" == "$expected" ]] || fail "$label SHA-256 mismatch"
}

file_value() {
  local file="$1" key="$2" count value
  count="$(awk -F= -v wanted="$key" '$1 == wanted {count++} END {print count + 0}' "$file")"
  [[ "$count" == "1" ]] || fail "$file key must occur exactly once: $key"
  value="$(awk -v wanted="$key" 'index($0, wanted "=") == 1 {sub(/^[^=]*=/, ""); print; exit}' "$file")"
  [[ -n "$value" ]] || fail "$file key has an empty value: $key"
  printf '%s' "$value"
}

require_file_value() {
  local file="$1" key="$2" expected="$3" actual
  actual="$(file_value "$file" "$key")"
  [[ "$actual" == "$expected" ]] || fail "$file key value mismatch: $key"
}

require_exact_keys() {
  local file="$1"
  shift
  local actual_keys expected_keys
  actual_keys="$(awk -F= 'NF >= 2 {print $1}' "$file" | LC_ALL=C sort)"
  expected_keys="$(printf '%s\n' "$@" | LC_ALL=C sort)"
  [[ "$actual_keys" == "$expected_keys" ]] || fail "$file key set or key multiplicity mismatch"
}

require_entry_once() {
  local archive="$1" entry="$2" count
  count="$(unzip -Z1 "$archive" | grep -Fxc "$entry" || true)"
  [[ "$count" == "1" ]] || fail "$entry must occur exactly once in $archive"
}

require_no_zip_name_collisions() {
  local archive="$1" duplicate folded
  duplicate="$(unzip -Z1 "$archive" | LC_ALL=C sort | uniq -d | head -n 1)"
  [[ -z "$duplicate" ]] || fail "duplicate ZIP entry in $archive: $duplicate"
  folded="$(unzip -Z1 "$archive" | awk '{ folded=tolower($0); if (seen[folded]++ == 1) { print; exit } }')"
  [[ -z "$folded" ]] || fail "case-fold ZIP collision in $archive: $folded"
}

require_all_nested_stored() {
  local archive="$1" entry count=0
  while IFS= read -r entry; do
    [[ "$entry" == BOOT-INF/lib/*.jar && "$entry" != BOOT-INF/lib/*/*.jar ]] || continue
    count=$((count + 1))
    unzip -lv "$archive" | awk -v wanted="$entry" '
      $NF == wanted { found += 1; if ($2 != "Stored") bad = 1 }
      END { exit !(found == 1 && bad != 1) }
    ' || fail "$entry is missing, duplicated, or not Stored"
  done < <(unzip -Z1 "$archive")
  (( count > 4 )) || fail "unexpectedly few BOOT-INF/lib/*.jar entries"
}

find_unique_listener_pid() {
  local rows=() pids=()
  mapfile -t rows < <(ss -H -ltnp "sport = :${PORT}" || true)
  ((${#rows[@]} > 0)) || return 1
  mapfile -t pids < <(printf '%s\n' "${rows[@]}" | sed -n 's/.*pid=\([0-9][0-9]*\).*/\1/p' | sort -u)
  ((${#pids[@]} == 1)) || return 1
  printf '%s' "${pids[0]}"
}

wait_for_exit() {
  local pid="$1" i
  for i in $(seq 1 45); do
    kill -0 "$pid" 2>/dev/null || return 0
    sleep 1
  done
  return 1
}

wait_for_ready() {
  local pid="$1" i listener status
  for i in $(seq 1 60); do
    kill -0 "$pid" 2>/dev/null || return 1
    listener="$(find_unique_listener_pid || true)"
    if [[ "$listener" == "$pid" ]]; then
      status="$(curl -sS --max-time 3 -o /dev/null -w '%{http_code}' "$HEALTH_URL" || true)"
      [[ "$status" == "401" ]] && return 0
    fi
    sleep 1
  done
  return 1
}

verify_manifest_entry() {
  local manifest_line="$1" source_label="$2"
  local expected_sha logical module path archive actual_sha
  expected_sha="${manifest_line%%  *}"
  logical="${manifest_line#*  }"
  [[ "$expected_sha" =~ ^[0-9a-f]{64}$ && "$logical" == *:* ]] || fail "invalid $source_label manifest line"
  module="${logical%%:*}"
  path="${logical#*:}"
  case "$module" in
    digital-doctor-app)
      archive="$CANDIDATE"
      path="BOOT-INF/classes/$path"
      ;;
    digital-doctor-api|digital-doctor-domain|digital-doctor-infrastructure|digital-doctor-trigger)
      archive="$VERIFY_DIR/${module}.jar"
      ;;
    *) fail "unknown module in $source_label manifest: $module" ;;
  esac
  require_entry_once "$archive" "$path"
  actual_sha="$(sha_entry "$archive" "$path")"
  require_equal "$actual_sha" "$expected_sha" "$source_label $logical"
}

start_captured_process() {
  local pid_file="$STAGE/new.pid"
  (
    cd "$PROCESS_CWD"
    nohup env -i "${PROCESS_ENV[@]}" "${PROCESS_CMD[@]}" 9>&- \
      >>"$PROCESS_STDOUT" 2>>"$PROCESS_STDERR" </dev/null &
    echo "$!" >"$pid_file"
  )
  cat "$pid_file"
}

SWAPPED=no
OLD_STOPPED=no
NEW_PID=""
OLD_PID=""
BACKUP=""
STAGED_APP=""
DB_ROLLBACK_NEEDED=no
DB_ROLLBACK_DONE=no
ROLLBACK_AUTHORIZED=no
LOCK_ACQUIRED=no
STAGE=""
PROCESS_CWD=""
PROCESS_STDOUT="$FALLBACK_LOG"
PROCESS_STDERR="$FALLBACK_LOG"
PROCESS_CMD=()
PROCESS_ENV=()

run_db_rollback() {
  if [[ "$DB_ROLLBACK_DONE" == yes ]]; then
    return 0
  fi
  echo "ROLLBACK_DATABASE_BEGIN" >&2
  if /bin/bash "$DB_ROLLBACK_HOOK" automatic-backend-release-failure; then
    DB_ROLLBACK_DONE=yes
    DB_ROLLBACK_NEEDED=no
    echo "ROLLBACK_DATABASE_OK" >&2
    return 0
  fi
  echo "CRITICAL=database_rollback_hook_failed" >&2
  return 1
}

restore_baseline_jar_atomically() {
  local restore_tmp="${APP}.restore-cw-${STAMP}"
  install -o root -g root -m 600 "$BACKUP" "$restore_tmp"
  sync
  mv -fT "$restore_tmp" "$APP"
  require_equal "$(sha_file "$APP")" "$EXPECTED_BASELINE_SHA256" "restored baseline JAR"
}

on_exit() {
  local status="$1" rollback_ok=no rollback_pid=""
  trap - EXIT ERR
  if (( status == 0 )); then
    return 0
  fi
  set +e
  if [[ "$LOCK_ACQUIRED" != yes ]]; then
    echo "BACKEND_PREFLIGHT_FAILED_BEFORE_RELEASE_LOCK=yes" >&2
    echo "AUTOMATIC_ROLLBACK_ATTEMPTED=no" >&2
    exit "$status"
  fi
  if [[ "$ROLLBACK_AUTHORIZED" != yes ]]; then
    echo "CRITICAL=backend_preflight_failed_before_rollback_hook_and_release_marker_were_fully_verified" >&2
    echo "DATABASE_FORWARD_STATE_MAY_BE_ACTIVE=yes" >&2
    echo "APPLICATION_MUST_REMAIN_STOPPED=yes" >&2
    echo "AUTOMATIC_ROLLBACK_ATTEMPTED=no" >&2
    exit "$status"
  fi
  echo "AUTOMATIC_ROLLBACK_BEGIN" >&2
  if [[ -n "$NEW_PID" ]] && kill -0 "$NEW_PID" 2>/dev/null; then
    kill -TERM "$NEW_PID" 2>/dev/null
    wait_for_exit "$NEW_PID" || true
  fi
  if [[ "$DB_ROLLBACK_NEEDED" == yes ]]; then
    run_db_rollback && rollback_ok=yes
  else
    rollback_ok=yes
  fi
  if [[ "$rollback_ok" == yes && "$SWAPPED" == yes && -n "$BACKUP" ]]; then
    restore_baseline_jar_atomically || rollback_ok=no
  fi
  if [[ "$rollback_ok" == yes && ( "$OLD_STOPPED" == yes || -z "$OLD_PID" || ! -d "/proc/$OLD_PID" ) && ${#PROCESS_CMD[@]} -gt 0 ]]; then
    rollback_pid="$(start_captured_process)"
    if wait_for_ready "$rollback_pid"; then
      echo "AUTOMATIC_ROLLBACK_OK baseline_pid=$rollback_pid" >&2
    else
      echo "CRITICAL=baseline_restored_but_process_not_ready" >&2
      rollback_ok=no
    fi
  elif [[ "$rollback_ok" == yes ]]; then
    echo "CRITICAL=database_and_jar_rollback_succeeded_but_captured_process_launch_data_is_unavailable" >&2
    rollback_ok=no
  fi
  if [[ "$rollback_ok" != yes ]]; then
    echo "CRITICAL=release_failed_and_consistent_automatic_rollback_not_completed" >&2
    echo "CRITICAL_NOTE=baseline_JAR_is_restored_only_after_database_rollback_succeeds" >&2
  fi
  exit "$status"
}
trap 'on_exit $?' EXIT

[[ "$CONFIRMATION" == "schema-migrated-and-rollback-ready" ]] || \
  fail "usage: $0 candidate.jar injected-entries.sha256 protected-hotpatch.sha256 process-capture-dir db-rollback-hook.sh schema-migrated-and-rollback-ready"
exec 9>/var/lock/feibo-copywriting-workflow-release.lock
flock -n 9 || fail "another copywriting-workflow release is running"
LOCK_ACQUIRED=yes
[[ "$EUID" == "0" ]] || fail "backend release must run as root"
for command_name in awk basename cat cmp cp curl dirname env flock grep gzip head install kill mv nohup pgrep \
  readlink sed seq sha256sum sleep sort ss stat sync tail uniq unzip; do
  command -v "$command_name" >/dev/null 2>&1 || fail "required command is unavailable: $command_name"
done
[[ "$PROCESS_CAPTURE_DIR" == /* && -d "$PROCESS_CAPTURE_DIR" && ! -L "$PROCESS_CAPTURE_DIR" ]] || fail "process capture must be an absolute regular directory"
DB_ARTIFACT_DIR="$(dirname "$PROCESS_CAPTURE_DIR")"
[[ "$(basename "$PROCESS_CAPTURE_DIR")" == "process" ]] || fail "process capture directory basename must be process"
for required in "$APP" "$CANDIDATE" "$INJECTED_MANIFEST" "$PROTECTED_MANIFEST" "$DB_ROLLBACK_HOOK" "$EXTERNAL_CONFIG" \
  "$DB_ARTIFACT_DIR/process-capture.sha256" "$DB_ARTIFACT_DIR/sql.sha256" "$DB_ARTIFACT_DIR/database-before.sql.gz.sha256" \
  "$DB_ARTIFACT_DIR/database-before.sql.gz" "$DB_ARTIFACT_DIR/apply.complete" \
  "$PROCESS_CAPTURE_DIR/cmdline.nul" "$PROCESS_CAPTURE_DIR/environ.nul" "$PROCESS_CAPTURE_DIR/cwd.txt" \
  "$PROCESS_CAPTURE_DIR/executable.txt" "$PROCESS_CAPTURE_DIR/stdout-link.txt" "$PROCESS_CAPTURE_DIR/pid.txt" \
  "$PROCESS_CAPTURE_DIR/app-sha256.txt"; do
  [[ -f "$required" && ! -L "$required" ]] || fail "required regular non-symlink file is missing: $required"
done
[[ "$(stat -c '%u:%a' "$PROCESS_CAPTURE_DIR")" == "0:700" ]] || fail "process capture directory must be root-owned mode 700"
[[ "$(stat -c '%u:%a' "$DB_ARTIFACT_DIR")" == "0:700" ]] || fail "database artifact directory must be root-owned mode 700"
for capture_file in "$PROCESS_CAPTURE_DIR"/* "$DB_ARTIFACT_DIR/process-capture.sha256" "$DB_ARTIFACT_DIR/apply.complete"; do
  [[ "$(stat -c '%u:%a' "$capture_file")" == "0:600" ]] || fail "database-stage capture must be root-owned mode 600: $capture_file"
done
(cd "$DB_ARTIFACT_DIR" && sha256sum -c process-capture.sha256 >/dev/null) || fail "process capture manifest verification failed"
(cd "$DB_ARTIFACT_DIR" && sha256sum -c sql.sha256 >/dev/null) || fail "database SQL snapshot manifest verification failed"
(cd "$DB_ARTIFACT_DIR" && sha256sum -c database-before.sql.gz.sha256 >/dev/null) || fail "database dump manifest verification failed"
gzip -t "$DB_ARTIFACT_DIR/database-before.sql.gz" || fail "database rollback dump gzip integrity failed"
APPLY_COMPLETE="$DB_ARTIFACT_DIR/apply.complete"
require_exact_keys "$APPLY_COMPLETE" \
  DATABASE_STAGE_COMPLETE EXPECTED_APP_SHA256 ORIGINAL_APP_PID APPLICATION_LEFT_STOPPED \
  DATABASE_BACKUP DATABASE_BACKUP_SHA256 DATABASE_BASELINE_COUNTS DATABASE_BASELINE_COUNTS_SHA256 \
  PROCESS_CAPTURE_MANIFEST_SHA256 SQL_MANIFEST_SHA256 SQL_EXPERT_SHA256 SQL_ORG_SHA256 \
  SQL_WORKFLOW_SHA256 EXTERNAL_CONFIG_SHA256 EXTERNAL_CONFIG_PATH EXPECTED_CANDIDATE_SHA256 \
  PROTECTED_MANIFEST_SHA256 INJECTED_MANIFEST_SHA256 MIGRATION_REMAINING RECOVERY_MANIFEST \
  RECOVERY_MANIFEST_SHA256 DATABASE_ROLLBACK_HOOK DATABASE_ROLLBACK_HOOK_SHA256 ROLLBACK_REASON
require_file_value "$APPLY_COMPLETE" DATABASE_STAGE_COMPLETE yes
require_file_value "$APPLY_COMPLETE" APPLICATION_LEFT_STOPPED yes
require_file_value "$APPLY_COMPLETE" EXPECTED_APP_SHA256 "$EXPECTED_BASELINE_SHA256"
require_file_value "$APPLY_COMPLETE" MIGRATION_REMAINING 0
require_file_value "$APPLY_COMPLETE" ROLLBACK_REASON automatic-backend-release-failure
require_file_value "$APPLY_COMPLETE" DATABASE_BACKUP "$DB_ARTIFACT_DIR/database-before.sql.gz"
require_file_value "$APPLY_COMPLETE" DATABASE_BACKUP_SHA256 "$(sha_file "$DB_ARTIFACT_DIR/database-before.sql.gz")"
require_file_value "$APPLY_COMPLETE" PROCESS_CAPTURE_MANIFEST_SHA256 "$(sha_file "$DB_ARTIFACT_DIR/process-capture.sha256")"
require_file_value "$APPLY_COMPLETE" SQL_MANIFEST_SHA256 "$(sha_file "$DB_ARTIFACT_DIR/sql.sha256")"
require_file_value "$APPLY_COMPLETE" EXTERNAL_CONFIG_PATH "$EXTERNAL_CONFIG"
require_file_value "$APPLY_COMPLETE" EXTERNAL_CONFIG_SHA256 "$EXPECTED_EXTERNAL_CONFIG_SHA256"
[[ "$(cat "$PROCESS_CAPTURE_DIR/app-sha256.txt")" == "$EXPECTED_BASELINE_SHA256" ]] || fail "captured application SHA mismatch"

BASELINE_COUNTS_PATH="$(file_value "$APPLY_COMPLETE" DATABASE_BASELINE_COUNTS)"
EXPECTED_BASELINE_COUNTS_SHA256="$(file_value "$APPLY_COMPLETE" DATABASE_BASELINE_COUNTS_SHA256)"
[[ "$BASELINE_COUNTS_PATH" == "$DB_ARTIFACT_DIR/database-before.counts" \
    && -f "$BASELINE_COUNTS_PATH" && ! -L "$BASELINE_COUNTS_PATH" \
    && "$(stat -c '%u:%a' "$BASELINE_COUNTS_PATH")" == "0:600" ]] || fail "database baseline counts path/owner/mode binding mismatch"
require_equal "$(sha_file "$BASELINE_COUNTS_PATH")" "$EXPECTED_BASELINE_COUNTS_SHA256" "database baseline counts"

EXPECTED_PROTECTED_MANIFEST_SHA256="$(file_value "$APPLY_COMPLETE" PROTECTED_MANIFEST_SHA256)"
EXPECTED_INJECTED_MANIFEST_SHA256="$(file_value "$APPLY_COMPLETE" INJECTED_MANIFEST_SHA256)"
EXPECTED_CANDIDATE_FROM_DB_STAGE="$(file_value "$APPLY_COMPLETE" EXPECTED_CANDIDATE_SHA256)"
EXPECTED_DB_ROLLBACK_HOOK_SHA256="$(file_value "$APPLY_COMPLETE" DATABASE_ROLLBACK_HOOK_SHA256)"
EXPECTED_DB_ROLLBACK_HOOK_PATH="$(file_value "$APPLY_COMPLETE" DATABASE_ROLLBACK_HOOK)"
RECOVERY_MANIFEST_PATH="$(file_value "$APPLY_COMPLETE" RECOVERY_MANIFEST)"
EXPECTED_RECOVERY_MANIFEST_SHA256="$(file_value "$APPLY_COMPLETE" RECOVERY_MANIFEST_SHA256)"
EXPECTED_EXPERT_SQL_SHA256="$(file_value "$APPLY_COMPLETE" SQL_EXPERT_SHA256)"
EXPECTED_ORG_SQL_SHA256="$(file_value "$APPLY_COMPLETE" SQL_ORG_SHA256)"
EXPECTED_WORKFLOW_SQL_SHA256="$(file_value "$APPLY_COMPLETE" SQL_WORKFLOW_SHA256)"
for bound_sha in "$EXPECTED_PROTECTED_MANIFEST_SHA256" "$EXPECTED_INJECTED_MANIFEST_SHA256" \
  "$EXPECTED_CANDIDATE_FROM_DB_STAGE" "$EXPECTED_DB_ROLLBACK_HOOK_SHA256" "$EXPECTED_RECOVERY_MANIFEST_SHA256" \
  "$EXPECTED_BASELINE_COUNTS_SHA256" "$EXPECTED_EXPERT_SQL_SHA256" "$EXPECTED_ORG_SQL_SHA256" \
  "$EXPECTED_WORKFLOW_SQL_SHA256"; do
  [[ "$bound_sha" =~ ^[0-9a-f]{64}$ ]] || fail "database apply marker contains a missing/invalid release SHA binding"
done
require_equal "$(sha_file "$DB_ARTIFACT_DIR/sql/add_copywriting_expert_assignment.sql")" "$EXPECTED_EXPERT_SQL_SHA256" "expert assignment SQL snapshot"
require_equal "$(sha_file "$DB_ARTIFACT_DIR/sql/add_copywriting_organization_admin_assignment.sql")" "$EXPECTED_ORG_SQL_SHA256" "organization administrator SQL snapshot"
require_equal "$(sha_file "$DB_ARTIFACT_DIR/sql/migrate_copywriting_author_first_workflow.sql")" "$EXPECTED_WORKFLOW_SQL_SHA256" "workflow SQL snapshot"
[[ "$EXPECTED_CANDIDATE_FROM_DB_STAGE" == "$EXPECTED_CANDIDATE_SHA256" ]] || fail "database stage candidate SHA binding mismatch"
[[ "$EXPECTED_DB_ROLLBACK_HOOK_PATH" == "$DB_ARTIFACT_DIR/recovery/rollback.sh" && "$DB_ROLLBACK_HOOK" == "$EXPECTED_DB_ROLLBACK_HOOK_PATH" ]] || \
  fail "rollback hook is not the fixed path bound by the database apply marker"
[[ "$RECOVERY_MANIFEST_PATH" == "$DB_ARTIFACT_DIR/recovery/manifest.env" \
    && -f "$RECOVERY_MANIFEST_PATH" && ! -L "$RECOVERY_MANIFEST_PATH" \
    && "$(stat -c '%u:%a' "$RECOVERY_MANIFEST_PATH")" == "0:600" ]] || fail "recovery manifest path/owner/mode binding mismatch"
require_equal "$(sha_file "$RECOVERY_MANIFEST_PATH")" "$EXPECTED_RECOVERY_MANIFEST_SHA256" "recovery manifest"
RECOVERY_DIR="$DB_ARTIFACT_DIR/recovery"
APPLY_CORE="$RECOVERY_DIR/apply-core.env"
MYSQL_RECOVERY_CNF="$RECOVERY_DIR/mysql-client.cnf"
[[ "$(stat -c '%u:%a' "$RECOVERY_DIR")" == "0:700" ]] || fail "recovery directory must be root-owned mode 700"
for recovery_file in "$APPLY_CORE" "$MYSQL_RECOVERY_CNF"; do
  [[ -f "$recovery_file" && ! -L "$recovery_file" && "$(stat -c '%u:%a' "$recovery_file")" == "0:600" ]] || \
    fail "recovery file path/owner/mode binding mismatch: $recovery_file"
done
require_exact_keys "$RECOVERY_MANIFEST_PATH" \
  MANIFEST_VERSION PINNED_RELEASE_DIR PINNED_DATABASE_DIR PINNED_DB_NAME PINNED_BASELINE_APP_SHA \
  PINNED_DUMP_SHA PINNED_BASELINE_COUNTS_SHA PINNED_APPLY_MARKER_SHA PINNED_MYSQL_CNF_SHA \
  PINNED_EXPERT_SQL_SHA PINNED_ORG_SQL_SHA PINNED_WORKFLOW_SQL_SHA
require_file_value "$RECOVERY_MANIFEST_PATH" MANIFEST_VERSION 1
require_file_value "$RECOVERY_MANIFEST_PATH" PINNED_DATABASE_DIR "$DB_ARTIFACT_DIR"
require_file_value "$RECOVERY_MANIFEST_PATH" PINNED_RELEASE_DIR "$(dirname "$DB_ARTIFACT_DIR")"
require_file_value "$RECOVERY_MANIFEST_PATH" PINNED_BASELINE_APP_SHA "$EXPECTED_BASELINE_SHA256"
require_file_value "$RECOVERY_MANIFEST_PATH" PINNED_DUMP_SHA "$(sha_file "$DB_ARTIFACT_DIR/database-before.sql.gz")"
require_file_value "$RECOVERY_MANIFEST_PATH" PINNED_BASELINE_COUNTS_SHA "$EXPECTED_BASELINE_COUNTS_SHA256"
require_file_value "$RECOVERY_MANIFEST_PATH" PINNED_APPLY_MARKER_SHA "$(sha_file "$APPLY_CORE")"
require_file_value "$RECOVERY_MANIFEST_PATH" PINNED_MYSQL_CNF_SHA "$(sha_file "$MYSQL_RECOVERY_CNF")"
require_file_value "$RECOVERY_MANIFEST_PATH" PINNED_EXPERT_SQL_SHA "$(file_value "$APPLY_COMPLETE" SQL_EXPERT_SHA256)"
require_file_value "$RECOVERY_MANIFEST_PATH" PINNED_ORG_SQL_SHA "$(file_value "$APPLY_COMPLETE" SQL_ORG_SHA256)"
require_file_value "$RECOVERY_MANIFEST_PATH" PINNED_WORKFLOW_SQL_SHA "$(file_value "$APPLY_COMPLETE" SQL_WORKFLOW_SHA256)"
apply_core_size="$(stat -c '%s' "$APPLY_CORE")"
[[ "$apply_core_size" =~ ^[0-9]+$ ]] || fail "apply-core size is invalid"
cmp -n "$apply_core_size" "$APPLY_CORE" "$APPLY_COMPLETE" || fail "final apply marker does not contain the byte-identical pinned apply-core prefix"
[[ "$(stat -c '%u:%a' "$DB_ROLLBACK_HOOK")" == "0:700" ]] || fail "database rollback hook must be root-owned mode 700"
require_equal "$(sha_file "$DB_ROLLBACK_HOOK")" "$EXPECTED_DB_ROLLBACK_HOOK_SHA256" "database rollback hook"
umask 077
STAMP="$(date +%Y%m%d-%H%M%S)"
STAGE="/www/wwwroot/.copywriting-workflow-backend-${STAMP}"
VERIFY_DIR="$STAGE/verify"
BACKUP="${APP}.bak.copywriting-workflow-${STAMP}"
STAGED_APP="${APP}.candidate.copywriting-workflow-${STAMP}"
FAILED_APP="${APP}.failed.copywriting-workflow-${STAMP}"
install -d -o root -g root -m 700 "$STAGE" "$VERIFY_DIR"

require_equal "$(sha_file "$APP")" "$EXPECTED_BASELINE_SHA256" "production baseline JAR"
require_equal "$(sha_file "$CANDIDATE")" "$EXPECTED_CANDIDATE_SHA256" "candidate JAR"
require_equal "$(sha_file "$PROTECTED_MANIFEST")" "$EXPECTED_PROTECTED_MANIFEST_SHA256" "protected manifest"
require_equal "$(sha_file "$INJECTED_MANIFEST")" "$EXPECTED_INJECTED_MANIFEST_SHA256" "injected manifest"
require_equal "$(sha_file "$EXTERNAL_CONFIG")" "$EXPECTED_EXTERNAL_CONFIG_SHA256" "external configuration"
[[ "$(stat -c '%u:%a:%d' "$APP")" == "0:600:${EXPECTED_DEVICE}" ]] || fail "production JAR owner/mode/device mismatch"
[[ "$(stat -c '%u:%a:%d' "$EXTERNAL_CONFIG")" == "0:600:${EXPECTED_DEVICE}" ]] || fail "external config owner/mode/device mismatch"

unzip -tq "$APP" >/dev/null
unzip -tq "$CANDIDATE" >/dev/null
require_no_zip_name_collisions "$CANDIDATE"
require_all_nested_stored "$CANDIDATE"
require_equal "$(sha_entry "$CANDIDATE" 'BOOT-INF/classes/application-dev.yml')" "$EXPECTED_DEV_CONFIG_SHA256" "candidate application-dev.yml"
require_equal "$(sha_entry "$CANDIDATE" 'BOOT-INF/classes/application-prod.yml')" "$EXPECTED_PROD_CONFIG_SHA256" "candidate application-prod.yml"

for module in digital-doctor-api digital-doctor-domain digital-doctor-infrastructure digital-doctor-trigger; do
  nested="BOOT-INF/lib/${module}-1.0.jar"
  require_entry_once "$CANDIDATE" "$nested"
  unzip -p "$CANDIDATE" "$nested" >"$VERIFY_DIR/${module}.jar"
  unzip -tq "$VERIFY_DIR/${module}.jar" >/dev/null
  require_no_zip_name_collisions "$VERIFY_DIR/${module}.jar"
done

mapfile -t protected_lines < <(grep -E '^[0-9a-f]{64}  [^:]+:.+$' "$PROTECTED_MANIFEST")
mapfile -t injected_lines < <(grep -E '^[0-9a-f]{64}  [^:]+:.+$' "$INJECTED_MANIFEST")
[[ "${#protected_lines[@]}" == "21" ]] || fail "protected manifest must contain exactly 21 valid entries"
[[ "${#injected_lines[@]}" == "77" ]] || fail "injected manifest must contain exactly 77 valid entries"
for line in "${protected_lines[@]}"; do verify_manifest_entry "$line" protected; done
for line in "${injected_lines[@]}"; do verify_manifest_entry "$line" injected; done

[[ -z "$(find_unique_listener_pid || true)" ]] || fail "application port ${PORT} must remain stopped after the database gate"
if pgrep -f '[d]igital-doctor-app\.jar' >/dev/null 2>&1; then
  fail "a digital-doctor-app process exists after the database gate; refusing to swap or start"
fi
OLD_PID="$(cat "$PROCESS_CAPTURE_DIR/pid.txt")"
[[ "$OLD_PID" =~ ^[0-9]+$ ]] || fail "captured original PID is invalid"
require_file_value "$APPLY_COMPLETE" ORIGINAL_APP_PID "$OLD_PID"
require_equal "$(sha_file "$PROCESS_CAPTURE_DIR/cmdline.nul")" "$EXPECTED_PROCESS_CMD_SHA256" "captured process cmdline"
[[ "$(cat "$PROCESS_CAPTURE_DIR/executable.txt")" == "$EXPECTED_JAVA_EXE" ]] || fail "captured Java executable mismatch"
PROCESS_CWD="$(cat "$PROCESS_CAPTURE_DIR/cwd.txt")"
[[ "$PROCESS_CWD" == "$EXPECTED_PROCESS_CWD" && -d "$PROCESS_CWD" ]] || fail "captured process cwd mismatch"

while IFS= read -r -d '' item; do PROCESS_CMD+=("$item"); done <"$PROCESS_CAPTURE_DIR/cmdline.nul"
while IFS= read -r -d '' item; do PROCESS_ENV+=("$item"); done <"$PROCESS_CAPTURE_DIR/environ.nul"
captured_spring_profile=""
for item in "${PROCESS_ENV[@]}"; do
  if [[ "$item" == SPRING_PROFILES_ACTIVE=* ]]; then
    captured_spring_profile="${item#*=}"
    break
  fi
done
[[ "$captured_spring_profile" == "prod" ]] || fail "production process must explicitly set SPRING_PROFILES_ACTIVE=prod"
jar_arg_count=0
for item in "${PROCESS_CMD[@]}"; do [[ "$item" == "$APP" ]] && jar_arg_count=$((jar_arg_count + 1)); done
[[ "$jar_arg_count" == "1" ]] || fail "captured command line does not reference the exact production JAR once"

stdout_target="$(cat "$PROCESS_CAPTURE_DIR/stdout-link.txt")"
stderr_target="$stdout_target"
stdout_target="${stdout_target% (deleted)}"
stderr_target="${stderr_target% (deleted)}"
[[ "$stdout_target" == /* && "$stdout_target" != /dev/* ]] && PROCESS_STDOUT="$stdout_target"
[[ "$stderr_target" == /* && "$stderr_target" != /dev/* ]] && PROCESS_STDERR="$stderr_target"
OLD_STOPPED=yes
DB_ROLLBACK_NEEDED=yes
ROLLBACK_AUTHORIZED=yes

install -o root -g root -m 600 "$CANDIDATE" "$STAGED_APP"
[[ "$(stat -c '%d' "$STAGED_APP")" == "$EXPECTED_DEVICE" ]] || fail "staged candidate is not on the application filesystem"
require_equal "$(sha_file "$STAGED_APP")" "$EXPECTED_CANDIDATE_SHA256" "same-filesystem staged candidate"
sync

cp --reflink=auto --preserve=all "$APP" "$BACKUP"
require_equal "$(sha_file "$BACKUP")" "$EXPECTED_BASELINE_SHA256" "backup JAR"
[[ "$(stat -c '%u:%a:%d' "$BACKUP")" == "0:600:${EXPECTED_DEVICE}" ]] || fail "backup JAR owner/mode/device mismatch"
sync

mv -fT "$STAGED_APP" "$APP"
SWAPPED=yes
require_equal "$(sha_file "$APP")" "$EXPECTED_CANDIDATE_SHA256" "atomically installed candidate"

NEW_PID="$(start_captured_process)"
if ! wait_for_ready "$NEW_PID"; then
  fail "candidate process failed unique-port and protected-401 readiness"
fi
[[ "$(readlink -f "/proc/$NEW_PID/exe")" == "$EXPECTED_JAVA_EXE" ]] || fail "candidate Java executable mismatch after readiness"
[[ "$(readlink -f "/proc/$NEW_PID/cwd")" == "$PROCESS_CWD" ]] || fail "candidate cwd mismatch after readiness"
[[ "$(awk '{print $4}' "/proc/$NEW_PID/stat")" == "1" ]] || fail "candidate parent PID is not 1 after readiness"
require_equal "$(sha_file "/proc/$NEW_PID/cmdline")" "$EXPECTED_PROCESS_CMD_SHA256" "candidate process cmdline"

DB_ROLLBACK_NEEDED=no
trap - EXIT
echo "BACKEND_DEPLOY_OK"
echo "BASELINE_SHA256=$EXPECTED_BASELINE_SHA256"
echo "CANDIDATE_SHA256=$(sha_file "$APP")"
echo "BACKUP=$BACKUP"
echo "OLD_PID=$OLD_PID"
echo "NEW_PID=$NEW_PID"
echo "LISTENER_PID=$(find_unique_listener_pid)"
echo "PROCESS_CWD=$PROCESS_CWD"
echo "STDOUT_LOG=$PROCESS_STDOUT"
echo "STDERR_LOG=$PROCESS_STDERR"
echo "PROTECTED_HEALTH_HTTP=401"
echo "PROTECTED_HOTPATCH_CLASSES_VERIFIED=21"
echo "INJECTED_RUNTIME_ENTRIES_VERIFIED=77"
echo "PROTECTED_MANIFEST_SHA256=$EXPECTED_PROTECTED_MANIFEST_SHA256"
echo "INJECTED_MANIFEST_SHA256=$EXPECTED_INJECTED_MANIFEST_SHA256"
echo "DATABASE_FORWARD_MIGRATION_EXECUTED_BY_THIS_SCRIPT=no"

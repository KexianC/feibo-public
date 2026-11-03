#!/usr/bin/env bash
set -Eeuo pipefail

# Fixed recovery implementation installed by the database release gate.
# Dynamic release values are read from the root-only recovery manifest.

EXPECTED_REASON="automatic-backend-release-failure"
SELF_DIR="$(cd "$(dirname "$0")" && pwd -P)"
DATABASE_DIR="$(cd "$SELF_DIR/.." && pwd -P)"
RELEASE_DIR="$(cd "$DATABASE_DIR/.." && pwd -P)"
MANIFEST="$SELF_DIR/manifest.env"
MYSQL_CNF="$SELF_DIR/mysql-client.cnf"
DUMP="$DATABASE_DIR/database-before.sql.gz"
BASELINE_COUNTS="$DATABASE_DIR/database-before.counts"
APPLY_MARKER="$SELF_DIR/apply-core.env"
ROLLBACK_MARKER="$SELF_DIR/rollback.complete"

fail() {
  echo "DATABASE_ROLLBACK_ERROR=$*" >&2
  exit 1
}

sha_file() {
  sha256sum "$1" | awk '{print $1}'
}

[[ "$EUID" == "0" ]] || fail "rollback must run as root"
[[ "${1:-}" == "$EXPECTED_REASON" && $# == 1 ]] || fail "only reason $EXPECTED_REASON is accepted"
[[ ! -e "$ROLLBACK_MARKER" ]] || fail "database rollback has already completed"

for command_name in awk cat chmod date grep gzip head mv mysql pgrep sed sha256sum ss stat tail; do
  command -v "$command_name" >/dev/null 2>&1 || fail "required command is unavailable: $command_name"
done
for required in "$MANIFEST" "$MYSQL_CNF" "$DUMP" "$BASELINE_COUNTS" "$APPLY_MARKER"; do
  [[ -f "$required" && ! -L "$required" ]] || fail "required regular non-symlink file is missing: $required"
done

[[ "$(stat -c '%u:%a' "$SELF_DIR")" == "0:700" ]] || fail "recovery directory must be root-owned mode 700"
[[ "$(stat -c '%u:%a' "$MANIFEST")" == "0:600" ]] || fail "recovery manifest must be root-owned mode 600"
[[ "$(stat -c '%u:%a' "$MYSQL_CNF")" == "0:600" ]] || fail "database option file must be root-owned mode 600"
[[ "$(stat -c '%u:%a' "$DUMP")" == "0:600" ]] || fail "database dump must be root-owned mode 600"
[[ "$(stat -c '%u:%a' "$BASELINE_COUNTS")" == "0:600" ]] || fail "baseline report must be root-owned mode 600"
[[ "$(stat -c '%u:%a' "$APPLY_MARKER")" == "0:600" ]] || fail "apply marker must be root-owned mode 600"

EXPECTED_MANIFEST_KEYS=(
  MANIFEST_VERSION
  PINNED_RELEASE_DIR
  PINNED_DATABASE_DIR
  PINNED_DB_NAME
  PINNED_BASELINE_APP_SHA
  PINNED_DUMP_SHA
  PINNED_BASELINE_COUNTS_SHA
  PINNED_APPLY_MARKER_SHA
  PINNED_MYSQL_CNF_SHA
  PINNED_EXPERT_SQL_SHA
  PINNED_ORG_SQL_SHA
  PINNED_WORKFLOW_SQL_SHA
)
while IFS= read -r manifest_key; do
  allowed=no
  for expected_key in "${EXPECTED_MANIFEST_KEYS[@]}"; do
    [[ "$manifest_key" == "$expected_key" ]] && allowed=yes
  done
  [[ "$allowed" == "yes" ]] || fail "recovery manifest contains an unknown key: $manifest_key"
done < <(awk -F= '{print $1}' "$MANIFEST")

manifest_value() {
  local key="$1"
  local count value
  count="$(awk -F= -v wanted="$key" '$1 == wanted {count++} END {print count + 0}' "$MANIFEST")"
  [[ "$count" == "1" ]] || fail "recovery manifest key must occur exactly once: $key"
  value="$(awk -v wanted="$key" 'index($0, wanted "=") == 1 {sub(/^[^=]*=/, ""); print; exit}' "$MANIFEST")"
  [[ -n "$value" ]] || fail "recovery manifest value is empty: $key"
  printf '%s' "$value"
}

MANIFEST_VERSION="$(manifest_value MANIFEST_VERSION)"
PINNED_RELEASE_DIR="$(manifest_value PINNED_RELEASE_DIR)"
PINNED_DATABASE_DIR="$(manifest_value PINNED_DATABASE_DIR)"
PINNED_DB_NAME="$(manifest_value PINNED_DB_NAME)"
PINNED_BASELINE_APP_SHA="$(manifest_value PINNED_BASELINE_APP_SHA)"
PINNED_DUMP_SHA="$(manifest_value PINNED_DUMP_SHA)"
PINNED_BASELINE_COUNTS_SHA="$(manifest_value PINNED_BASELINE_COUNTS_SHA)"
PINNED_APPLY_MARKER_SHA="$(manifest_value PINNED_APPLY_MARKER_SHA)"
PINNED_MYSQL_CNF_SHA="$(manifest_value PINNED_MYSQL_CNF_SHA)"
PINNED_EXPERT_SQL_SHA="$(manifest_value PINNED_EXPERT_SQL_SHA)"
PINNED_ORG_SQL_SHA="$(manifest_value PINNED_ORG_SQL_SHA)"
PINNED_WORKFLOW_SQL_SHA="$(manifest_value PINNED_WORKFLOW_SQL_SHA)"
[[ "$MANIFEST_VERSION" == "1" ]] || fail "unsupported recovery manifest version"
[[ "$PINNED_RELEASE_DIR" =~ ^/[A-Za-z0-9._/-]+$ && "$PINNED_RELEASE_DIR" != *'/../'* && "$PINNED_RELEASE_DIR" != */.. ]] || fail "pinned release path is unsafe"
[[ "$PINNED_DATABASE_DIR" =~ ^/[A-Za-z0-9._/-]+$ && "$PINNED_DATABASE_DIR" != *'/../'* && "$PINNED_DATABASE_DIR" != */.. ]] || fail "pinned database path is unsafe"
[[ "$PINNED_RELEASE_DIR" == "$RELEASE_DIR" ]] || fail "release directory binding mismatch"
[[ "$PINNED_DATABASE_DIR" == "$DATABASE_DIR" ]] || fail "database directory binding mismatch"
[[ "$PINNED_DB_NAME" =~ ^[A-Za-z0-9_\$-]+$ ]] || fail "pinned database name contains unsupported characters"
for digest in "$PINNED_BASELINE_APP_SHA" "$PINNED_DUMP_SHA" "$PINNED_BASELINE_COUNTS_SHA" "$PINNED_APPLY_MARKER_SHA" "$PINNED_MYSQL_CNF_SHA" "$PINNED_EXPERT_SQL_SHA" "$PINNED_ORG_SQL_SHA" "$PINNED_WORKFLOW_SQL_SHA"; do
  [[ "$digest" =~ ^[0-9a-f]{64}$ ]] || fail "recovery manifest contains an invalid SHA-256 digest"
done
[[ "$(sha_file "$DUMP")" == "$PINNED_DUMP_SHA" ]] || fail "database dump SHA-256 mismatch"
[[ "$(sha_file "$BASELINE_COUNTS")" == "$PINNED_BASELINE_COUNTS_SHA" ]] || fail "baseline report SHA-256 mismatch"
[[ "$(sha_file "$APPLY_MARKER")" == "$PINNED_APPLY_MARKER_SHA" ]] || fail "apply marker SHA-256 mismatch"
[[ "$(sha_file "$MYSQL_CNF")" == "$PINNED_MYSQL_CNF_SHA" ]] || fail "database option file SHA-256 mismatch"
grep -Fqx 'DATABASE_STAGE_COMPLETE=yes' "$APPLY_MARKER" || fail "database stage completion marker is absent"
grep -Fqx 'APPLICATION_LEFT_STOPPED=yes' "$APPLY_MARKER" || fail "database stage did not leave the application stopped"
grep -Fqx "EXPECTED_APP_SHA256=$PINNED_BASELINE_APP_SHA" "$APPLY_MARKER" || fail "apply marker baseline SHA mismatch"
grep -Fqx "SQL_EXPERT_SHA256=$PINNED_EXPERT_SQL_SHA" "$APPLY_MARKER" || fail "apply marker expert SQL SHA mismatch"
grep -Fqx "SQL_ORG_SHA256=$PINNED_ORG_SQL_SHA" "$APPLY_MARKER" || fail "apply marker organization SQL SHA mismatch"
grep -Fqx "SQL_WORKFLOW_SHA256=$PINNED_WORKFLOW_SQL_SHA" "$APPLY_MARKER" || fail "apply marker workflow SQL SHA mismatch"

listener_pid="$(ss -lntp 2>/dev/null | sed -n 's/.*:8091 .*pid=\([0-9][0-9]*\).*/\1/p' | head -n 1)"
[[ -z "$listener_pid" ]] || fail "application port 8091 must be stopped before full database recovery"
if pgrep -f '[d]igital-doctor-app\.jar' >/dev/null 2>&1; then
  fail "application process must be stopped before full database recovery"
fi

gzip -t "$DUMP" || fail "database dump failed gzip integrity verification"
DUMP_LAST_LINE="$(gzip -cd "$DUMP" | tail -n 1)"
[[ "$DUMP_LAST_LINE" == "-- Dump completed on "* ]] || fail "database dump completion marker is absent"

MYSQL=(mysql --defaults-extra-file="$MYSQL_CNF" --batch --skip-column-names --raw)
[[ "$("${MYSQL[@]}" --execute 'SELECT 1;')" == "1" ]] || fail "database connectivity check failed"

echo "DATABASE_FULL_RESTORE_BEGIN"
echo "DATABASE_FULL_RESTORE_WARNING=the_current_database_will_be_replaced_by_the_verified_pre_migration_dump"
if ! gzip -cd "$DUMP" | "${MYSQL[@]}"; then
  fail "full database restore failed; database state requires immediate manual recovery"
fi

SQL=("${MYSQL[@]}" "$PINNED_DB_NAME")
sql() {
  "${SQL[@]}" --execute "$1"
}

for required_table in t_copywriting t_copywriting_approval_flow t_copywriting_approval_history; do
  [[ "$(sql "SELECT COUNT(*) FROM information_schema.tables WHERE table_schema=DATABASE() AND table_name='${required_table}';")" == "1" ]] || \
    fail "restored database is missing required table: $required_table"
done

baseline_value() {
  local key="$1"
  awk -F= -v wanted="$key" '$1 == wanted {print $2; exit}' "$BASELINE_COUNTS"
}

verify_count() {
  local key="$1"
  local query="$2"
  local expected actual
  expected="$(baseline_value "$key")"
  [[ "$expected" =~ ^[0-9]+$ ]] || fail "baseline key is missing or invalid: $key"
  actual="$(sql "$query")"
  [[ "$actual" == "$expected" ]] || fail "restored baseline count mismatch: $key"
}

verify_count COPYWRITING_TOTAL 'SELECT COUNT(*) FROM t_copywriting;'
verify_count FLOW_TOTAL 'SELECT COUNT(*) FROM t_copywriting_approval_flow;'
verify_count HISTORY_TOTAL 'SELECT COUNT(*) FROM t_copywriting_approval_history;'
for status in 0 1 2 3 4 5; do
  verify_count "FLOW_STATUS_${status}" "SELECT COUNT(*) FROM t_copywriting_approval_flow WHERE current_status=${status};"
done

expected_expert_table="$(baseline_value TABLE_EXPERT_ASSIGNMENT)"
expected_org_table="$(baseline_value TABLE_ORG_ASSIGNMENT)"
[[ "$expected_expert_table" =~ ^[01]$ && "$expected_org_table" =~ ^[01]$ ]] || fail "baseline assignment-table markers are invalid"
[[ "$(sql "SELECT COUNT(*) FROM information_schema.tables WHERE table_schema=DATABASE() AND table_name='t_copywriting_expert_assignment';")" == "$expected_expert_table" ]] || fail "expert assignment table restore mismatch"
[[ "$(sql "SELECT COUNT(*) FROM information_schema.tables WHERE table_schema=DATABASE() AND table_name='t_copywriting_organization_admin_assignment';")" == "$expected_org_table" ]] || fail "organization assignment table restore mismatch"
if [[ "$expected_expert_table" == "1" ]]; then
  verify_count EXPERT_ASSIGNMENT_ROWS 'SELECT COUNT(*) FROM t_copywriting_expert_assignment;'
fi
if [[ "$expected_org_table" == "1" ]]; then
  verify_count ORG_ASSIGNMENT_ROWS 'SELECT COUNT(*) FROM t_copywriting_organization_admin_assignment;'
fi

ROLLBACK_RECORD="$SELF_DIR/rollback.complete.tmp"
{
  echo 'DATABASE_ROLLBACK_COMPLETE=yes'
  echo "REASON=$EXPECTED_REASON"
  echo "RELEASE_DIR=$RELEASE_DIR"
  echo "DATABASE_NAME=$PINNED_DB_NAME"
  echo "DUMP_SHA256=$PINNED_DUMP_SHA"
  echo "BASELINE_COUNTS_SHA256=$PINNED_BASELINE_COUNTS_SHA"
  echo "COMPLETED_UTC=$(date -u +%Y-%m-%dT%H:%M:%SZ)"
} > "$ROLLBACK_RECORD"
chmod 600 "$ROLLBACK_RECORD"
mv -f -- "$ROLLBACK_RECORD" "$ROLLBACK_MARKER"
echo "DATABASE_FULL_RESTORE_OK=yes"

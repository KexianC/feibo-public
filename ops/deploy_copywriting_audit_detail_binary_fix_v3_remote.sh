#!/usr/bin/env bash
set -Eeuo pipefail
umask 077

APP="/www/wwwroot/digital-doctor-app.jar"
LOG="/www/wwwroot/digital-doctor-app.szdoctor.out"
STAGE="/root/codex-copywriting-audit-detail-binary-fix-v3/20260818-130803"
CANDIDATE="$STAGE/digital-doctor-app.copywriting-audit-detail.candidate.jar"
LOGICAL_DIFF="$STAGE/logical-diff.tsv"
INJECTED_MANIFEST="$STAGE/injected-entries.sha256"
PROTECTED_MANIFEST="$STAGE/protected-entries.sha256"
RAW_AUDIT="$STAGE/raw-preservation.txt"
RELEASE_ALLOWLIST="$STAGE/release-files.txt"
SOURCE_MANIFEST="$STAGE/source-files.sha256"
BT_GUARD="$STAGE/bt_java_project_guard.py"
PYTHON3="/www/server/panel/pyenv/bin/python3.7"
HEALTH_URL="http://127.0.0.1:8091/api/v1/copywriting/list/by-approval-status?page=1&size=1&status=5"
BACKUP_ROOT="/www/backup/copywriting-audit-detail-binary-fix-v3"

EXPECTED_OLD_APP_SHA="65368b3d5746d8284d8154bf70f073acad49b052c5bcc29071a0b1432d31ef68"
EXPECTED_OLD_APP_SIZE="213838200"
EXPECTED_NEW_APP_SHA="3d683e45f976189d545f8367c98dee487766e873fac0c052cc345d4df975565d"
EXPECTED_NEW_APP_SIZE="213869417"
EXPECTED_LOGICAL_DIFF_SHA="cee86b8d928b8703d63e1ac3fd97e70f16c775d037313e38c8b55e48a03880d9"
EXPECTED_INJECTED_MANIFEST_SHA="d8a36d536e5048bb0761b82ae627ba1763637c827b0fd6d2cf4805468330047c"
EXPECTED_PROTECTED_MANIFEST_SHA="b5eb01527a6342a2ad300e5c194517d8f8aa3b48c15f77ba6c7ca511de534ad7"
EXPECTED_RAW_AUDIT_SHA="747d58f702ef72e6e48a506ae800a35d3421f3f390720953e256c4c0be26d712"
EXPECTED_RELEASE_ALLOWLIST_SHA="ebf2f36075421030497ef8defc5be8e21ded1e2f562cf49d263e243abe6d9a0f"
EXPECTED_SOURCE_MANIFEST_SHA="08bf1e2d44e442fcf19d9f416adbfedda13592c7c60a18431803cd0d3b0cc485"
EXPECTED_BT_GUARD_SHA="9a3f0cfbe9ec005d0a43ed19126605c85f34c53b3758e63b4455d4dace27c383"

OLD_PID=""
NEW_PID=""
PROC_CWD=""
BACKUP=""
MONITOR_PAUSED=0
MUTATION_STARTED=0
DEPLOY_OK=0
HEALTH_HTTP=""
CMD=()
ENVV=()

die() {
  echo "ERROR: $*" >&2
  exit 1
}

file_sha() {
  sha256sum "$1" | awk '{print $1}'
}

file_size() {
  stat -Lc '%s' "$1"
}

require_regular_file() {
  local path="$1"
  [[ -f "$path" && ! -L "$path" ]] || die "missing or unsafe regular file: $path"
  [[ "$(stat -Lc '%U:%G' "$path")" == "root:root" ]] || die "unexpected owner for $path"
  local mode
  mode="$(stat -Lc '%a' "$path")"
  (( (8#$mode & 0022) == 0 )) || die "file is group/other writable: $path"
}

require_sha() {
  local path="$1"
  local expected="$2"
  local label="$3"
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
    if any(argv[index] == b"-jar" and argv[index + 1] == app for index in range(len(argv) - 1)):
        print(name.decode("ascii"))
PY
}

listener_pids() {
  ss -H -ltnp 'sport = :8091' 2>/dev/null \
    | grep -oE 'pid=[0-9]+' \
    | cut -d= -f2 \
    | sort -un || true
}

assert_single_running_app() {
  local app_list listener_list
  app_list="$(exact_app_pids)"
  listener_list="$(listener_pids)"
  [[ "$app_list" =~ ^[0-9]+$ ]] || die "expected exactly one application process, found: ${app_list:-none}"
  [[ "$listener_list" =~ ^[0-9]+$ ]] || die "expected exactly one 8091 listener, found: ${listener_list:-none}"
  [[ "$app_list" == "$listener_list" ]] || die "application PID and 8091 listener PID differ"
  OLD_PID="$app_list"
}

assert_exact_runtime() {
  local pid="$1"
  [[ "$(exact_app_pids)" == "$pid" ]] || return 1
  [[ "$(listener_pids)" == "$pid" ]] || return 1
  kill -0 "$pid" 2>/dev/null || return 1
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

start_captured_process() {
  (
    exec 9>&- 8>&- 7>&-
    cd "$PROC_CWD"
    exec /usr/bin/nohup /usr/bin/env -i "${ENVV[@]}" "${CMD[@]}" >> "$LOG" 2>&1 < /dev/null
  ) &
  NEW_PID=$!
}

wait_for_health() {
  local pid="$1"
  local code=""
  for _ in $(seq 1 120); do
    kill -0 "$pid" 2>/dev/null || return 1
    if [[ "$(exact_app_pids)" == "$pid" && "$(listener_pids)" == "$pid" ]]; then
      code="$(curl -sS -o /dev/null -w '%{http_code}' --max-time 3 "$HEALTH_URL" || true)"
      if [[ "$code" == "401" ]]; then
        HEALTH_HTTP="$code"
        return 0
      fi
    fi
    sleep 1
  done
  return 1
}

resume_monitoring() {
  if [[ "$MONITOR_PAUSED" == 1 ]]; then
    if ! "$PYTHON3" "$BT_GUARD" resume-monitoring; then
      return 1
    fi
    MONITOR_PAUSED=0
  fi
}

rollback() {
  local failed=0
  local running=""
  echo "Binary-fix v3 deployment failed; pausing BaoTa monitoring again and restoring the exact 65368 baseline." >&2

  if "$PYTHON3" "$BT_GUARD" pause-monitoring; then
    MONITOR_PAUSED=1
  else
    echo "ROLLBACK_INCOMPLETE BaoTa monitoring could not be paused" >&2
    return 1
  fi

  if [[ -n "$NEW_PID" ]]; then
    stop_pid "$NEW_PID" || failed=1
  fi
  running="$(exact_app_pids)"
  if [[ -n "$running" ]]; then
    if [[ "$running" =~ ^[0-9]+$ ]]; then
      stop_pid "$running" || failed=1
    else
      failed=1
    fi
  fi
  [[ -z "$(listener_pids)" ]] || failed=1

  if [[ -n "$BACKUP" && -f "$BACKUP/digital-doctor-app.before.jar" && ! -L "$BACKUP/digital-doctor-app.before.jar" ]]; then
    if [[ "$(file_sha "$BACKUP/digital-doctor-app.before.jar")" != "$EXPECTED_OLD_APP_SHA" ||
          "$(file_size "$BACKUP/digital-doctor-app.before.jar")" != "$EXPECTED_OLD_APP_SIZE" ]]; then
      failed=1
    else
      install -m 644 -o root -g root "$BACKUP/digital-doctor-app.before.jar" "$APP.audit-detail-rollback.$$"
      mv -f -- "$APP.audit-detail-rollback.$$" "$APP"
      sync
    fi
  else
    failed=1
  fi

  if [[ "$failed" == 0 ]]; then
    NEW_PID=""
    start_captured_process
    if ! wait_for_health "$NEW_PID" || ! assert_exact_runtime "$NEW_PID"; then
      failed=1
    fi
  fi

  if [[ "$failed" == 0 && "$(file_sha "$APP")" == "$EXPECTED_OLD_APP_SHA" &&
        "$(file_size "$APP")" == "$EXPECTED_OLD_APP_SIZE" ]]; then
    resume_monitoring || failed=1
  else
    failed=1
  fi

  if [[ "$failed" == 0 ]]; then
    sleep 5
    if assert_exact_runtime "$NEW_PID" && [[ "$(file_sha "$APP")" == "$EXPECTED_OLD_APP_SHA" ]]; then
      echo "ROLLBACK_OK APP_SHA=$EXPECTED_OLD_APP_SHA PID=$NEW_PID HEALTH_HTTP=$HEALTH_HTTP" >&2
    else
      failed=1
    fi
  fi

  if [[ "$failed" != 0 ]]; then
    if [[ "$MONITOR_PAUSED" != 1 ]]; then
      if "$PYTHON3" "$BT_GUARD" pause-monitoring; then
        MONITOR_PAUSED=1
      fi
    fi
    echo "ROLLBACK_INCOMPLETE BACKUP=${BACKUP:-not-created}" >&2
    return 1
  fi
}

on_exit() {
  local status=$?
  trap - EXIT
  if [[ "$status" != 0 && "$MUTATION_STARTED" == 1 && "$DEPLOY_OK" != 1 ]]; then
    if ! rollback; then
      echo "ROLLBACK_FAILURE_PROPAGATED" >&2
      status=1
    fi
  elif [[ "$MONITOR_PAUSED" == 1 ]]; then
    if ! resume_monitoring; then
      echo "MONITORING_RESUME_FAILURE_PROPAGATED" >&2
      status=1
    fi
  fi
  exit "$status"
}
trap on_exit EXIT

[[ "$EUID" == 0 ]] || die "must run as root"

exec 9>/var/lock/feibo-backend-deploy.lock
flock -n 9 || die "another backend deployment is already running"
exec 8>/var/lock/feibo-copywriting-workflow-release.lock
flock -n 8 || die "another copywriting deployment is already running"
exec 7>/var/lock/feibo-copywriting-audit-detail-binary-fix-v3.lock
flock -n 7 || die "another audit-detail binary-fix deployment is already running"

[[ -x "$PYTHON3" && ! -L "$PYTHON3" ]] || die "trusted Python runtime is unavailable"
[[ "$(stat -Lc '%U:%G' "$PYTHON3")" == "root:root" ]] || die "trusted Python runtime owner changed"
[[ -d "$STAGE" && ! -L "$STAGE" ]] || die "unsafe stage directory"
[[ "$(stat -Lc '%U:%G' "$STAGE")" == "root:root" ]] || die "unexpected stage owner"
stage_mode="$(stat -Lc '%a' "$STAGE")"
(( (8#$stage_mode & 0022) == 0 )) || die "stage directory is group/other writable"

for path in "$APP" "$CANDIDATE" "$LOGICAL_DIFF" "$INJECTED_MANIFEST" \
  "$PROTECTED_MANIFEST" "$RAW_AUDIT" "$RELEASE_ALLOWLIST" "$SOURCE_MANIFEST" "$BT_GUARD"; do
  require_regular_file "$path"
done

[[ "$(file_size "$APP")" == "$EXPECTED_OLD_APP_SIZE" ]] || die "production JAR baseline size changed"
require_sha "$APP" "$EXPECTED_OLD_APP_SHA" "production JAR baseline"
[[ "$(file_size "$CANDIDATE")" == "$EXPECTED_NEW_APP_SIZE" ]] || die "candidate size mismatch"
require_sha "$CANDIDATE" "$EXPECTED_NEW_APP_SHA" "candidate"
require_sha "$LOGICAL_DIFF" "$EXPECTED_LOGICAL_DIFF_SHA" "logical diff manifest"
require_sha "$INJECTED_MANIFEST" "$EXPECTED_INJECTED_MANIFEST_SHA" "injected-entry manifest"
require_sha "$PROTECTED_MANIFEST" "$EXPECTED_PROTECTED_MANIFEST_SHA" "protected-entry manifest"
require_sha "$RAW_AUDIT" "$EXPECTED_RAW_AUDIT_SHA" "raw preservation audit"
require_sha "$RELEASE_ALLOWLIST" "$EXPECTED_RELEASE_ALLOWLIST_SHA" "release allowlist"
require_sha "$SOURCE_MANIFEST" "$EXPECTED_SOURCE_MANIFEST_SHA" "source manifest"
require_sha "$BT_GUARD" "$EXPECTED_BT_GUARD_SHA" "BaoTa guard"

"$PYTHON3" - "$APP" "$CANDIDATE" "$LOGICAL_DIFF" "$INJECTED_MANIFEST" "$PROTECTED_MANIFEST" <<'PY'
import csv
import hashlib
import io
import re
import sys
import zipfile

baseline_path, candidate_path, diff_path, injected_path, protected_path = sys.argv[1:]
modules = {
    "digital-doctor-api": "BOOT-INF/lib/digital-doctor-api-1.0.jar",
    "digital-doctor-domain": "BOOT-INF/lib/digital-doctor-domain-1.0.jar",
    "digital-doctor-infrastructure": "BOOT-INF/lib/digital-doctor-infrastructure-1.0.jar",
    "digital-doctor-trigger": "BOOT-INF/lib/digital-doctor-trigger-1.0.jar",
}
outer_allowed = set(modules.values()) | {
    "BOOT-INF/classes/mybatis/mapper/CopywritingApprovalFlowMapper.xml",
    "BOOT-INF/classes/mybatis/mapper/CopywritingApprovalHistoryMapper.xml",
}

def fail(message):
    raise SystemExit(message)

def digest_bytes(payload):
    return hashlib.sha256(payload).hexdigest()

def digest_entry(archive, info):
    result = hashlib.sha256()
    with archive.open(info, "r") as handle:
        while True:
            block = handle.read(131072)
            if not block:
                break
            result.update(block)
    return result.hexdigest()

def method(info):
    if info.compress_type == zipfile.ZIP_STORED:
        return "stored"
    if info.compress_type == zipfile.ZIP_DEFLATED:
        return "deflated"
    return str(info.compress_type)

def metadata(info):
    return (
        info.compress_type, info.CRC, info.file_size, info.compress_size,
        info.flag_bits, info.date_time, info.external_attr, info.internal_attr,
        info.create_system, info.create_version, info.extract_version,
        info.extra, info.comment,
    )

def safe_infos(archive, label):
    infos = archive.infolist()
    names = [item.filename for item in infos]
    if not infos:
        fail(label + " is empty")
    if len(names) != len(set(names)):
        fail(label + " has duplicate entries")
    if len(names) != len(set(name.casefold() for name in names)):
        fail(label + " has case-folding collisions")
    for info in infos:
        name = info.filename
        if (not name or name.startswith("/") or "\\" in name or
                re.match(r"^[A-Za-z]:", name) or ".." in name.split("/") or
                any(ord(ch) < 32 or ord(ch) == 127 for ch in name)):
            fail(label + " has unsafe entry: " + repr(name))
        if info.flag_bits & ((1 << 0) | (1 << 6) | (1 << 13)):
            fail(label + " has encrypted entry: " + name)
        if info.compress_type not in (zipfile.ZIP_STORED, zipfile.ZIP_DEFLATED):
            fail(label + " has unsupported compression: " + name)
    return infos

def parse_sha_manifest(path, expected_count):
    result = {}
    with open(path, "r", encoding="utf-8", newline="") as handle:
        for raw in handle:
            line = raw.rstrip("\r\n")
            if not line:
                continue
            parts = line.split("  ", 1)
            if len(parts) != 2 or not re.fullmatch(r"[0-9a-f]{64}", parts[0]):
                fail("malformed SHA manifest line: " + repr(line))
            if parts[1] in result:
                fail("duplicate SHA manifest label: " + parts[1])
            result[parts[1]] = parts[0]
    if len(result) != expected_count:
        fail("unexpected SHA manifest count: {} != {}".format(len(result), expected_count))
    return result

def diff_row(scope, action, name, before_info, after_info, before_sha, after_sha):
    return {
        "scope": scope,
        "action": action,
        "entry": name,
        "before_sha256": before_sha,
        "after_sha256": after_sha,
        "before_size": "0" if before_info is None else str(before_info.file_size),
        "after_size": str(after_info.file_size),
        "before_method": "-" if before_info is None else method(before_info),
        "after_method": method(after_info),
    }

injected = parse_sha_manifest(injected_path, 21)
protected = parse_sha_manifest(protected_path, 26)
expected_module_counts = {
    "digital-doctor-api": 5,
    "digital-doctor-domain": 9,
    "digital-doctor-infrastructure": 4,
    "digital-doctor-trigger": 1,
    "digital-doctor-app": 2,
}
for module_name, expected_count in expected_module_counts.items():
    actual = sum(1 for label in injected if label.startswith(module_name + ":"))
    if actual != expected_count:
        fail("injected module count mismatch for {}: {}".format(module_name, actual))

with open(diff_path, "r", encoding="utf-8", newline="") as handle:
    reader = csv.DictReader(handle, delimiter="\t")
    fields = ["scope", "action", "entry", "before_sha256", "after_sha256",
              "before_size", "after_size", "before_method", "after_method"]
    if reader.fieldnames != fields:
        fail("logical diff header mismatch")
    manifest_rows = list(reader)
if len(manifest_rows) != 25:
    fail("logical diff row count mismatch")

computed_rows = []
with zipfile.ZipFile(baseline_path, "r") as baseline, zipfile.ZipFile(candidate_path, "r") as candidate:
    before_infos = safe_infos(baseline, "baseline outer JAR")
    after_infos = safe_infos(candidate, "candidate outer JAR")
    if len(before_infos) != 564 or len(after_infos) != 564:
        fail("unexpected outer JAR entry count")
    before_names = [item.filename for item in before_infos]
    after_names = [item.filename for item in after_infos]
    if before_names != after_names:
        fail("candidate outer entry set/order changed")
    before_by_name = {item.filename: item for item in before_infos}
    after_by_name = {item.filename: item for item in after_infos}

    nested_names = [name for name in before_names if name.startswith("BOOT-INF/lib/") and name.endswith(".jar")]
    if len(nested_names) != 353:
        fail("nested library count mismatch")
    for name in nested_names:
        if (before_by_name[name].compress_type != zipfile.ZIP_STORED or
                after_by_name[name].compress_type != zipfile.ZIP_STORED):
            fail("nested library is not Stored: " + name)

    outer_changed = set()
    for name in before_names:
        old_info = before_by_name[name]
        new_info = after_by_name[name]
        old_sha = digest_entry(baseline, old_info)
        new_sha = digest_entry(candidate, new_info)
        if old_sha != new_sha:
            outer_changed.add(name)
            if name not in outer_allowed:
                fail("non-allowlisted outer content changed: " + name)
            if old_info.compress_type != new_info.compress_type:
                fail("outer compression method changed: " + name)
            computed_rows.append(diff_row("outer", "replace", name, old_info, new_info, old_sha, new_sha))
        elif metadata(old_info) != metadata(new_info):
            fail("non-changed outer metadata changed: " + name)
    if outer_changed != outer_allowed:
        fail("outer diff does not equal allowlist: " + repr(sorted(outer_changed)))

    for module_name, outer_name in modules.items():
        before_payload = baseline.read(before_by_name[outer_name])
        after_payload = candidate.read(after_by_name[outer_name])
        with zipfile.ZipFile(io.BytesIO(before_payload), "r") as before_nested, \
                zipfile.ZipFile(io.BytesIO(after_payload), "r") as after_nested:
            old_infos = safe_infos(before_nested, module_name + " baseline")
            new_infos = safe_infos(after_nested, module_name + " candidate")
            old_by_name = {item.filename: item for item in old_infos}
            new_by_name = {item.filename: item for item in new_infos}
            if not set(old_by_name).issubset(set(new_by_name)):
                fail(module_name + " deleted nested entries")
            allowed = {label.split(":", 1)[1] for label in injected if label.startswith(module_name + ":")}
            changed = set()
            for name, new_info in new_by_name.items():
                old_info = old_by_name.get(name)
                new_sha = digest_entry(after_nested, new_info)
                if old_info is None:
                    changed.add(name)
                    if name not in allowed:
                        fail(module_name + " added non-allowlisted entry: " + name)
                    computed_rows.append(diff_row("nested:" + module_name, "add", name,
                                                  None, new_info, "-", new_sha))
                else:
                    old_sha = digest_entry(before_nested, old_info)
                    if old_sha != new_sha:
                        changed.add(name)
                        if name not in allowed:
                            fail(module_name + " changed non-allowlisted entry: " + name)
                        if old_info.compress_type != new_info.compress_type:
                            fail(module_name + " changed compression method: " + name)
                        computed_rows.append(diff_row("nested:" + module_name, "replace", name,
                                                      old_info, new_info, old_sha, new_sha))
                    elif metadata(old_info) != metadata(new_info):
                        fail(module_name + " changed non-content metadata: " + name)
            if changed != allowed:
                fail(module_name + " nested diff does not equal allowlist")
            for name in allowed:
                label = module_name + ":" + name
                if digest_entry(after_nested, new_by_name[name]) != injected[label]:
                    fail("injected payload checksum mismatch: " + label)

    for label, expected_sha in injected.items():
        if not label.startswith("digital-doctor-app:"):
            continue
        resource = label.split(":", 1)[1]
        outer_name = "BOOT-INF/classes/" + resource
        if digest_entry(candidate, after_by_name[outer_name]) != expected_sha:
            fail("injected XML checksum mismatch: " + label)

    nested_cache = {}
    for label, expected_sha in protected.items():
        scope, name = label.split(":", 1)
        if scope == "outer":
            if (digest_entry(baseline, before_by_name[name]) != expected_sha or
                    digest_entry(candidate, after_by_name[name]) != expected_sha):
                fail("protected outer entry changed: " + name)
            continue
        if scope not in modules:
            fail("unknown protected module: " + scope)
        if scope not in nested_cache:
            old_payload = baseline.read(before_by_name[modules[scope]])
            new_payload = candidate.read(after_by_name[modules[scope]])
            nested_cache[scope] = (zipfile.ZipFile(io.BytesIO(old_payload), "r"),
                                   zipfile.ZipFile(io.BytesIO(new_payload), "r"))
        old_archive, new_archive = nested_cache[scope]
        try:
            old_info = old_archive.getinfo(name)
            new_info = new_archive.getinfo(name)
        except KeyError:
            fail("missing protected entry: " + label)
        if (digest_entry(old_archive, old_info) != expected_sha or
                digest_entry(new_archive, new_info) != expected_sha):
            fail("protected nested entry changed: " + label)
    for old_archive, new_archive in nested_cache.values():
        old_archive.close()
        new_archive.close()

computed_rows.sort(key=lambda row: (row["scope"], row["entry"]))
manifest_rows.sort(key=lambda row: (row["scope"], row["entry"]))
if computed_rows != manifest_rows:
    fail("logical diff manifest does not match recomputed two-layer diff")

print("REMOTE_TWO_LAYER_DIFF_OK outer_entries=564 outer_changed=6 nested_changed=19 nested_libraries=353 protected=26")
PY

assert_single_running_app
while IFS= read -r -d '' item; do CMD+=("$item"); done < "/proc/$OLD_PID/cmdline"
while IFS= read -r -d '' item; do ENVV+=("$item"); done < "/proc/$OLD_PID/environ"
PROC_CWD="$(readlink -f "/proc/$OLD_PID/cwd")"
[[ ${#CMD[@]} -gt 2 && ${#ENVV[@]} -gt 0 && -d "$PROC_CWD" ]] || die "could not capture process launch state"

jar_arg_ok=0
for ((index = 0; index + 1 < ${#CMD[@]}; index++)); do
  if [[ "${CMD[$index]}" == "-jar" && "${CMD[$((index + 1))]}" == "$APP" ]]; then
    jar_arg_ok=1
  fi
done
[[ "$jar_arg_ok" == 1 ]] || die "captured command does not launch the pinned application JAR"

mkdir -p "$BACKUP_ROOT"
[[ -d "$BACKUP_ROOT" && ! -L "$BACKUP_ROOT" ]] || die "unsafe backup root"
[[ "$(stat -Lc '%U:%G' "$BACKUP_ROOT")" == "root:root" ]] || die "unexpected backup-root owner"
chmod 700 "$BACKUP_ROOT"
BACKUP="$BACKUP_ROOT/$(date -u +%Y%m%dT%H%M%SZ)-$$"
mkdir "$BACKUP"
chmod 700 "$BACKUP"
install -m 600 -o root -g root "$APP" "$BACKUP/.digital-doctor-app.before.jar.tmp"
mv -- "$BACKUP/.digital-doctor-app.before.jar.tmp" "$BACKUP/digital-doctor-app.before.jar"
sync
[[ "$(file_sha "$BACKUP/digital-doctor-app.before.jar")" == "$EXPECTED_OLD_APP_SHA" ]] || die "backup checksum mismatch"
[[ "$(file_size "$BACKUP/digital-doctor-app.before.jar")" == "$EXPECTED_OLD_APP_SIZE" ]] || die "backup size mismatch"
printf '%s\n' "$OLD_PID" > "$BACKUP/old.pid"
printf '%s\n' "$PROC_CWD" > "$BACKUP/process.cwd"
sha256sum "$BACKUP/digital-doctor-app.before.jar" > "$BACKUP/before.sha256"

"$PYTHON3" "$BT_GUARD" pause-monitoring
MONITOR_PAUSED=1
MUTATION_STARTED=1
# BaoTa only pauses its watchdog.  The deployer never calls stop_project;
# it signals only the exact application PID captured and revalidated above.
assert_exact_runtime "$OLD_PID" || die "application runtime changed before stop"
stop_pid "$OLD_PID" || die "old application process did not stop"
[[ -z "$(exact_app_pids)" ]] || die "application process remains after stop"
[[ -z "$(listener_pids)" ]] || die "port 8091 remains occupied after stop"

install -m 644 -o root -g root "$CANDIDATE" "$APP.audit-detail.$$"
mv -f -- "$APP.audit-detail.$$" "$APP"
sync
[[ "$(file_sha "$APP")" == "$EXPECTED_NEW_APP_SHA" ]] || die "installed JAR checksum mismatch"
[[ "$(file_size "$APP")" == "$EXPECTED_NEW_APP_SIZE" ]] || die "installed JAR size mismatch"

start_captured_process
wait_for_health "$NEW_PID" || die "audit-detail application did not become healthy with HTTP 401"
assert_exact_runtime "$NEW_PID" || die "runtime PID/8091 uniqueness check failed"
[[ "$(file_sha "$APP")" == "$EXPECTED_NEW_APP_SHA" ]] || die "production JAR drifted after start"
[[ "$(file_size "$APP")" == "$EXPECTED_NEW_APP_SIZE" ]] || die "production JAR size drifted after start"

resume_monitoring || die "BaoTa monitoring resume failed"
sleep 5
assert_exact_runtime "$NEW_PID" || die "runtime changed after BaoTa monitoring resumed"
FINAL_APP_SHA="$(file_sha "$APP")"
FINAL_APP_SIZE="$(file_size "$APP")"
[[ "$FINAL_APP_SHA" == "$EXPECTED_NEW_APP_SHA" && "$FINAL_APP_SIZE" == "$EXPECTED_NEW_APP_SIZE" ]] \
  || die "production JAR drifted after final checks"

printf '%s\n' "$FINAL_APP_SHA  $APP" > "$BACKUP/after.sha256"
DEPLOY_OK=1
printf 'AUDIT_DETAIL_BINARY_FIX_V3_DEPLOY_OK\nOLD_PID=%s\nNEW_PID=%s\nOLD_APP_SHA=%s\nNEW_APP_SHA=%s\nNEW_APP_SIZE=%s\nHEALTH_HTTP=%s\nBACKUP=%s\nBT_STOP_USED=no\n' \
  "$OLD_PID" "$NEW_PID" "$EXPECTED_OLD_APP_SHA" "$FINAL_APP_SHA" "$FINAL_APP_SIZE" \
  "$HEALTH_HTTP" "$BACKUP"

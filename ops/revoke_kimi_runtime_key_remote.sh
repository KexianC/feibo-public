#!/usr/bin/env bash
set -euo pipefail
umask 077

APP="/www/wwwroot/digital-doctor-app.jar"
CONFIG="/www/wwwroot/digital-doctor-hy3.yml"
STAGED_PROD_CONFIG="/root/codex-rbac-no-mfa/20260822-023057/digital-doctor-runtime-prod.yml"
BT_GUARD="/root/codex-rbac-no-mfa/20260822-023057/bt_java_project_guard.py"
PYTHON3="/www/server/panel/pyenv/bin/python3.7"
LOG="/www/wwwroot/digital-doctor-app.szdoctor.out"
EXPECTED_APP_SHA="c8dbf309fc8cf5e64b1e3d0813a8d1d91a8a97f11bc4ba1e0fb413015b29bbe3"
EXPECTED_CONFIG_SHA="3c491588bd40b6baa479e6e2d75b2eb4a24a1f4542a8cbb78b6e05c8d3b07ce5"
EXPECTED_GUARD_SHA="9a3f0cfbe9ec005c85f34c53b3758e63b4455d4dace27c383"

WORK="$(mktemp -d /tmp/revoke-kimi-key.XXXXXX)"
OLD_PID=""
NEW_PID=""
PROC_CWD=""
MONITOR_PAUSED=0
MUTATION_STARTED=0
DEPLOY_OK=0
CMD=()
START_ENV=()

die() { echo "ERROR: $*" >&2; exit 1; }
file_sha() { sha256sum "$1" | awk '{print $1}'; }

exact_app_pids() {
  for proc in /proc/[0-9]*; do
    [[ -r "$proc/cmdline" ]] || continue
    mapfile -d '' -t args < "$proc/cmdline" || true
    for ((i=1; i<${#args[@]}; i++)); do
      if [[ "${args[$((i-1))]}" == "-jar" && "${args[$i]}" == "$APP" ]]; then
        basename "$proc"
        break
      fi
    done
  done
}

stop_pid() {
  local pid="$1"
  [[ -n "$pid" && -d "/proc/$pid" ]] || return 0
  kill -TERM "$pid"
  for _ in $(seq 1 45); do
    [[ ! -d "/proc/$pid" ]] && return 0
    sleep 1
  done
  return 1
}

start_safe_process() {
  (
    exec 9>&- 8>&- 7>&-
    cd "$PROC_CWD"
    exec /usr/bin/nohup /usr/bin/env -i "${START_ENV[@]}" "${CMD[@]}" >> "$LOG" 2>&1 < /dev/null
  ) &
  NEW_PID=$!
}

wait_for_health() {
  local pid="$1" code=""
  for _ in $(seq 1 90); do
    [[ -d "/proc/$pid" ]] || return 1
    code="$(curl -sS -o /dev/null -w '%{http_code}' --max-time 3 \
      http://127.0.0.1:8091/api/v1/ai/model/index || true)"
    if [[ "$code" == "401" || "$code" == "403" ]]; then
      return 0
    fi
    sleep 1
  done
  return 1
}

scrub_kimi_key() {
  local source="$1" target="$2"
  awk '
    BEGIN { in_kimi=0; changed=0 }
    /^    kimik3:[[:space:]]*$/ { in_kimi=1; print; next }
    in_kimi && /^    [A-Za-z0-9_-]+:[[:space:]]*$/ { in_kimi=0 }
    in_kimi && /^      api-key:/ {
      print "      api-key: \"\""
      changed++
      next
    }
    { print }
    END { if (changed != 1) exit 42 }
  ' "$source" > "$target"
}

resume_monitoring() {
  if [[ "$MONITOR_PAUSED" == 1 ]]; then
    "$PYTHON3" "$BT_GUARD" resume-monitoring
    MONITOR_PAUSED=0
  fi
}

on_exit() {
  local status=$?
  if [[ "$status" != 0 && "$MUTATION_STARTED" == 1 ]]; then
    if [[ -z "$(exact_app_pids)" ]]; then
      start_safe_process || true
      wait_for_health "$NEW_PID" || true
    fi
  fi
  resume_monitoring || status=1
  rm -rf -- "$WORK"
  exit "$status"
}
trap on_exit EXIT

exec 9>/var/lock/feibo-backend-deploy.lock
flock -n 9 || die "another backend deployment is running"
exec 8>/var/lock/feibo-rbac-no-mfa-release.lock
flock -n 8 || die "another RBAC release is running"
exec 7>/var/lock/feibo-frontends-deploy.lock
flock -n 7 || die "a frontend deployment is running"

for path in "$APP" "$CONFIG" "$STAGED_PROD_CONFIG" "$BT_GUARD"; do
  [[ -f "$path" && ! -L "$path" ]] || die "unsafe or missing file: $path"
  [[ "$(stat -Lc '%U:%G' "$path")" == "root:root" ]] || die "unexpected owner: $path"
done
[[ "$(file_sha "$APP")" == "$EXPECTED_APP_SHA" ]] || die "application baseline drifted"
[[ "$(file_sha "$CONFIG")" == "$EXPECTED_CONFIG_SHA" ]] || die "external config baseline drifted"
[[ "$(file_sha "$BT_GUARD")" == "$EXPECTED_GUARD_SHA" ]] || die "monitor guard drifted"
[[ "$(stat -Lc '%a' "$CONFIG")" == "600" ]] || die "external config permissions are unsafe"

mapfile -t pids < <(exact_app_pids)
[[ ${#pids[@]} == 1 ]] || die "expected exactly one application process"
OLD_PID="${pids[0]}"
while IFS= read -r -d '' item; do CMD+=("$item"); done < "/proc/$OLD_PID/cmdline"
while IFS= read -r -d '' item; do
  case "$item" in
    KIMI_K3_API_KEY=*) ;;
    *) START_ENV+=("$item") ;;
  esac
done < "/proc/$OLD_PID/environ"
PROC_CWD="$(readlink -f "/proc/$OLD_PID/cwd")"
[[ -d "$PROC_CWD" && ${#CMD[@]} -gt 2 && ${#START_ENV[@]} -gt 0 ]] || die "cannot capture runtime"

scrub_kimi_key "$CONFIG" "$WORK/digital-doctor-hy3.safe.yml"
scrub_kimi_key "$STAGED_PROD_CONFIG" "$WORK/digital-doctor-runtime-prod.safe.yml"
grep -q '^      api-key: ""$' "$WORK/digital-doctor-hy3.safe.yml" || die "active config was not scrubbed"
grep -q '^      api-key: ""$' "$WORK/digital-doctor-runtime-prod.safe.yml" || die "staged config was not scrubbed"
[[ "$(grep -c '^    kimik3:' "$WORK/digital-doctor-hy3.safe.yml")" == 1 ]] || die "unexpected Kimi structure"

"$PYTHON3" "$BT_GUARD" pause-monitoring
MONITOR_PAUSED=1
MUTATION_STARTED=1
stop_pid "$OLD_PID" || die "application did not stop cleanly"

install -m 600 -o root -g root "$WORK/digital-doctor-hy3.safe.yml" "$CONFIG.next.$$"
mv -f -- "$CONFIG.next.$$" "$CONFIG"
install -m 600 -o root -g root "$WORK/digital-doctor-runtime-prod.safe.yml" "$STAGED_PROD_CONFIG.next.$$"
mv -f -- "$STAGED_PROD_CONFIG.next.$$" "$STAGED_PROD_CONFIG"
sync

start_safe_process
wait_for_health "$NEW_PID" || die "application did not become healthy after Kimi key removal"
tr '\0' '\n' < "/proc/$NEW_PID/environ" | grep -q '^KIMI_K3_API_KEY=' \
  && die "Kimi key is still present in the new process environment"

resume_monitoring
sleep 8
mapfile -t final_pids < <(exact_app_pids)
[[ ${#final_pids[@]} == 1 && "${final_pids[0]}" == "$NEW_PID" ]] \
  || die "application runtime changed after monitoring resumed"
[[ "$(file_sha "$APP")" == "$EXPECTED_APP_SHA" ]] || die "application JAR changed unexpectedly"
[[ "$(stat -Lc '%a:%U:%G' "$CONFIG")" == "600:root:root" ]] || die "external config permissions drifted"

DEPLOY_OK=1
printf 'KIMI_KEY_REMOVAL_OK\nOLD_PID=%s\nNEW_PID=%s\nAPP_SHA=%s\nCONFIG_SHA=%s\nPROFILE_UNCHANGED=dev\n' \
  "$OLD_PID" "$NEW_PID" "$EXPECTED_APP_SHA" "$(file_sha "$CONFIG")"

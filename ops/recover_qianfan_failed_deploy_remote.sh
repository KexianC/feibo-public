#!/usr/bin/env bash
set -Eeuo pipefail
umask 077

APP="/www/wwwroot/digital-doctor-app.jar"
CONFIG="/www/wwwroot/digital-doctor-hy3.yml"
BACKUP="/www/wwwroot/deploy-backups/qianfan-20260825-111247"
GUARD="/root/codex-feibo-qianfan/20260825-110500/bt_java_project_guard.py"
PYTHON3="/www/server/panel/pyenv/bin/python3.7"
EXPECTED_BASELINE_SHA="ee234e706633190b7e79d515f74a88a2833e3634c5d135f65146b4297dc9df47"
WORK=""

die() { echo "RECOVERY_ERROR: $*" >&2; exit 1; }
cleanup() {
  if [[ -n "$WORK" ]]; then
    case "$WORK" in /tmp/feibo-qianfan-recovery.*) rm -rf -- "$WORK" ;; esac
  fi
}
trap cleanup EXIT

[[ "$EUID" == 0 ]] || die "must run as root"
exec 9>/var/lock/feibo-backend-deploy.lock
flock -n 9 || die "backend deployment lock is busy"
[[ -s "$BACKUP/digital-doctor-app.before.jar" && -s "$BACKUP/digital-doctor-hy3.before.yml" && -s "$BACKUP/t_ai_model_config.before.sql" ]] || die "recovery backup is incomplete"
[[ "$(sha256sum "$BACKUP/digital-doctor-app.before.jar" | awk '{print $1}')" == "$EXPECTED_BASELINE_SHA" ]] || die "backup baseline checksum mismatch"
[[ -x "$PYTHON3" && ! -L "$PYTHON3" ]] || die "trusted Python runtime unavailable"

WORK="$(mktemp -d /tmp/feibo-qianfan-recovery.XXXXXX)"
"$PYTHON3" - "$BACKUP/digital-doctor-hy3.before.yml" "$WORK/mysql-client.cnf" "$WORK/database-name" <<'PY'
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

for pid in $(pgrep -f 'java .*digital-doctor-app\.jar' || true); do
  kill -TERM "$pid" 2>/dev/null || true
done
for _ in $(seq 1 30); do
  [[ -z "$(pgrep -f 'java .*digital-doctor-app\.jar' || true)" ]] && break
  sleep 1
done

mysql --defaults-extra-file="$WORK/mysql-client.cnf" --default-character-set=utf8mb4 "$DB_NAME" < "$BACKUP/t_ai_model_config.before.sql"
install -m 644 -o root -g root "$BACKUP/digital-doctor-app.before.jar" "$APP.recovery"
mv -f -- "$APP.recovery" "$APP"
install -m 600 -o root -g root "$BACKUP/digital-doctor-hy3.before.yml" "$CONFIG.recovery"
mv -f -- "$CONFIG.recovery" "$CONFIG"
[[ "$(sha256sum "$APP" | awk '{print $1}')" == "$EXPECTED_BASELINE_SHA" ]] || die "restored JAR checksum mismatch"

"$PYTHON3" "$GUARD" resume-monitoring >/dev/null
"$PYTHON3" - <<'PY'
import os
import sys
PANEL_ROOT = "/www/server/panel"
sys.path.insert(0, PANEL_ROOT)
sys.path.insert(0, os.path.join(PANEL_ROOT, "class"))
os.chdir(PANEL_ROOT)
import public
from projectModel.javaModel import main as JavaProjectModel
model = JavaProjectModel()
project = model.get_project_find("digital-doctor-app")
if not project or int(project.get("id")) != 6 or project.get("path") != "/www/wwwroot/digital-doctor-app.jar":
    raise SystemExit("pinned BaoTa project mismatch")
request = public.dict_obj()
request.project_name = "digital-doctor-app"
result = model.start_project(request)
if not isinstance(result, dict) or not result.get("status"):
    raise SystemExit("BaoTa start_project failed")
PY

PID=""
HEALTH=""
for _ in $(seq 1 120); do
  PID="$(pgrep -f 'java .*digital-doctor-app\.jar' | head -n 1 || true)"
  if [[ "$PID" =~ ^[0-9]+$ ]] && ss -H -ltnp 'sport = :8091' 2>/dev/null | grep -q "pid=$PID"; then
    HEALTH="$(curl -sS -o /dev/null -w '%{http_code}' --max-time 3 http://127.0.0.1:8091/api/v1/ai/model/index || true)"
    if [[ "$HEALTH" == 401 || "$HEALTH" == 403 ]]; then break; fi
  fi
  sleep 1
done
[[ "$PID" =~ ^[0-9]+$ && ( "$HEALTH" == 401 || "$HEALTH" == 403 ) ]] || die "restored application failed health check"
rm -f -- /root/codex-feibo-qianfan/20260825-110500/qianfan.env
echo "RECOVERY_OK APP_SHA=$EXPECTED_BASELINE_SHA PID=$PID HEALTH_HTTP=$HEALTH BACKUP=$BACKUP"

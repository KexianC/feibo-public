#!/usr/bin/env bash
set -Eeuo pipefail
umask 077

CONFIG="/www/wwwroot/digital-doctor-hy3.yml"
APP="/www/wwwroot/digital-doctor-app.jar"
KEY_FILE="/root/codex-feibo-kimi-k3/20260817-171145/kimi-k3.env"
BACKUP="/www/wwwroot/deploy-backups/kimi-k3-20260817-173635"
PYTHON3="/www/server/panel/pyenv/bin/python3.7"
TEMP=""

cleanup() {
  if [[ -n "$TEMP" ]]; then
    case "$TEMP" in
      /www/wwwroot/digital-doctor-hy3.yml.kimi.*) rm -f -- "$TEMP" ;;
    esac
  fi
}
trap cleanup EXIT

[[ -s "$CONFIG" && ! -L "$CONFIG" ]] || { echo "unsafe production config" >&2; exit 1; }
[[ -s "$KEY_FILE" && ! -L "$KEY_FILE" ]] || { echo "missing Kimi key file" >&2; exit 1; }
[[ -x "$PYTHON3" && ! -L "$PYTHON3" ]] || { echo "trusted Python runtime unavailable" >&2; exit 1; }
[[ "$(stat -Lc '%U:%G:%a' "$CONFIG")" == "root:root:600" ]] || { echo "unexpected config permissions" >&2; exit 1; }
[[ "$(stat -Lc '%U:%G:%a' "$KEY_FILE")" == "root:root:600" ]] || { echo "unexpected key-file permissions" >&2; exit 1; }

TEMP="$(mktemp /www/wwwroot/digital-doctor-hy3.yml.kimi.XXXXXX)"
"$PYTHON3" - "$CONFIG" "$KEY_FILE" "$TEMP" <<'PY'
import re
import sys

config_path, key_path, output_path = sys.argv[1:]
with open(key_path, encoding="utf-8") as handle:
    key_lines = [line.rstrip("\r\n") for line in handle if line.startswith("KIMI_K3_API_KEY=")]
if len(key_lines) != 1:
    raise SystemExit("malformed Kimi key file")
key = key_lines[0].split("=", 1)[1]
if not re.fullmatch(r"sk-[A-Za-z0-9_-]{20,}", key):
    raise SystemExit("invalid Kimi key format")

with open(config_path, encoding="utf-8") as handle:
    config = handle.read()
marker = "      api-key: ${KIMI_K3_API_KEY:}"
if config.count(marker) != 1:
    raise SystemExit("unexpected Kimi API-key placeholder count")
if config.count("    kimik3:\n") != 1:
    raise SystemExit("unexpected Kimi config section count")
updated = config.replace(marker, "      api-key: " + key)
with open(output_path, "w", encoding="utf-8", newline="\n") as handle:
    handle.write(updated)
PY

chown root:root "$TEMP"
chmod 600 "$TEMP"
mv -f -- "$TEMP" "$CONFIG"
TEMP=""

grep -q '^    kimik3:$' "$CONFIG"
grep -q '^      api-key: sk-' "$CONFIG"
sha256sum "$APP" "$CONFIG" > "$BACKUP/backend.after.sha256"

rm -f -- "$KEY_FILE"
[[ ! -e "$KEY_FILE" ]] || { echo "failed to remove staged key file" >&2; exit 1; }

printf 'KIMI_KEY_PERSIST_OK\nCONFIG_OWNER_MODE=%s\nSTAGED_KEY_REMOVED=true\n' "$(stat -Lc '%U:%G:%a' "$CONFIG")"

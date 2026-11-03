#!/usr/bin/env bash
set -Eeuo pipefail
umask 022

exec 9>/var/lock/feibo-frontends-deploy.lock
if ! flock -n 9; then
  echo "another frontend deployment is already running" >&2
  exit 1
fi

if [[ $# -lt 2 || $# -gt 3 ]]; then
  echo "usage: $0 <dd.tar.gz> <manage.tar.gz> [release-id]" >&2
  exit 2
fi

DD_ARCHIVE="$1"
MANAGE_ARCHIVE="$2"
RELEASE="${3:-$(date +%Y%m%d-%H%M%S)}"
DD_ROOT="/www/wwwroot/dd.hzmwmt.com"
MANAGE_ROOT="/www/wwwroot/manage.hzmwmt.com"
PYTHON3="/www/server/panel/pyenv/bin/python3.7"

[[ "$RELEASE" =~ ^[0-9]{8}-[0-9]{6}(-[A-Za-z0-9._-]+)?$ ]] || {
  echo "invalid release id: $RELEASE" >&2
  exit 2
}
[[ -s "$DD_ARCHIVE" && -s "$MANAGE_ARCHIVE" ]] || {
  echo "both deployment archives must exist and be non-empty" >&2
  exit 2
}

[[ -x "$PYTHON3" && ! -L "$PYTHON3" ]] || {
  echo "trusted Python 3 runtime is unavailable: $PYTHON3" >&2
  exit 1
}
IFS=: read -r PYTHON3_OWNER PYTHON3_MODE < <(stat -Lc '%u:%a' -- "$PYTHON3")
[[ "$PYTHON3_OWNER" == "0" && "$PYTHON3_MODE" =~ ^[0-7]{3,4}$ ]] || {
  echo "invalid Python 3 runtime owner or mode: $PYTHON3" >&2
  exit 1
}
(( (8#$PYTHON3_MODE & 022) == 0 )) || {
  echo "Python 3 runtime is group/other writable: $PYTHON3" >&2
  exit 1
}

validate_archive() {
  local archive="$1"
  local listing="$2"
  "$PYTHON3" - "$archive" "$listing" <<'PY'
import pathlib
import posixpath
import sys
import tarfile

archive = pathlib.Path(sys.argv[1])
listing = pathlib.Path(sys.argv[2])
seen = set()
normalized_names = []

with tarfile.open(archive, mode="r:gz") as tar:
    members = tar.getmembers()
    if not members:
        raise SystemExit(f"archive contains no entries: {archive}")
    for member in members:
        name = member.name.replace("\\", "/")
        if any(ord(char) < 32 or ord(char) == 127 for char in name):
            raise SystemExit(f"control character in archive path: {name!r}")
        if name.startswith("/") or (len(name) >= 2 and name[1] == ":"):
            raise SystemExit(f"absolute archive path: {name!r}")
        normalized = posixpath.normpath(name)
        if normalized in ("", "."):
            if not member.isdir():
                raise SystemExit(f"invalid root archive entry: {name!r}")
            continue
        if normalized == ".." or normalized.startswith("../"):
            raise SystemExit(f"path traversal in archive: {name!r}")
        if normalized in seen:
            raise SystemExit(f"duplicate normalized archive path: {normalized!r}")
        seen.add(normalized)
        if not (member.isfile() or member.isdir()):
            raise SystemExit(f"unsupported archive member type: {name!r}")
        normalized_names.append(normalized + ("/" if member.isdir() else ""))

listing.write_text("\n".join(normalized_names) + "\n", encoding="utf-8")
PY
}

WORK="$(mktemp -d "/www/wwwroot/.feibo-deploy-$RELEASE.XXXXXX")"
cleanup() {
  case "${WORK:-}" in
    /www/wwwroot/.feibo-deploy-*) rm -rf -- "$WORK" ;;
  esac
}
trap cleanup EXIT

validate_archive "$DD_ARCHIVE" "$WORK/dd.list"
validate_archive "$MANAGE_ARCHIVE" "$WORK/manage.list"
mkdir -p "$WORK/dd" "$WORK/manage"
tar -xzf "$DD_ARCHIVE" -C "$WORK/dd"
tar -xzf "$MANAGE_ARCHIVE" -C "$WORK/manage"

[[ -s "$WORK/dd/index.html" && -d "$WORK/dd/assets" ]] || {
  echo "invalid dd artifact" >&2
  exit 1
}
[[ -s "$WORK/manage/index.html" && -d "$WORK/manage/assets" && -s "$WORK/manage/geo-agent-app/index.html" ]] || {
  echo "invalid manage artifact" >&2
  exit 1
}

install -d -m 755 -o www -g www "$DD_ROOT/releases" "$MANAGE_ROOT/releases"
DD_RELEASE="$DD_ROOT/releases/$RELEASE"
MANAGE_RELEASE="$MANAGE_ROOT/releases/$RELEASE"
[[ ! -e "$DD_RELEASE" && ! -e "$MANAGE_RELEASE" ]] || {
  echo "release already exists: $RELEASE" >&2
  exit 1
}

chown -R www:www "$WORK/dd" "$WORK/manage"
find "$WORK/dd" "$WORK/manage" -type d -exec chmod 755 {} +
find "$WORK/dd" "$WORK/manage" -type f -exec chmod 644 {} +
mv "$WORK/dd" "$DD_RELEASE"
mv "$WORK/manage" "$MANAGE_RELEASE"

OLD_DD="$(readlink "$DD_ROOT/current" || true)"
OLD_MANAGE="$(readlink "$MANAGE_ROOT/current" || true)"

switch_release() {
  local root="$1"
  local target="$2"
  local link="$root/.current-$RELEASE"
  ln -s "$target" "$link"
  mv -Tf "$link" "$root/current"
  chown -h www:www "$root/current"
}

rollback() {
  if [[ -n "$OLD_DD" ]]; then switch_release "$DD_ROOT" "$OLD_DD"; fi
  if [[ -n "$OLD_MANAGE" ]]; then switch_release "$MANAGE_ROOT" "$OLD_MANAGE"; fi
}

switch_release "$DD_ROOT" "releases/$RELEASE"
if ! switch_release "$MANAGE_ROOT" "releases/$RELEASE"; then
  if [[ -n "$OLD_DD" ]]; then switch_release "$DD_ROOT" "$OLD_DD"; fi
  exit 1
fi

DD_HTTP="$(curl -sS -o /dev/null -w '%{http_code}' --resolve dd.hzmwmt.com:443:127.0.0.1 https://dd.hzmwmt.com/ || true)"
MANAGE_HTTP="$(curl -sS -o /dev/null -w '%{http_code}' --resolve manage.hzmwmt.com:443:127.0.0.1 https://manage.hzmwmt.com/ || true)"
MANAGE_DEEP="$(curl -sS -o /dev/null -w '%{http_code}' --resolve manage.hzmwmt.com:443:127.0.0.1 https://manage.hzmwmt.com/users/list || true)"
MANAGE_GEO="$(curl -sS -o /dev/null -w '%{http_code}' --resolve manage.hzmwmt.com:443:127.0.0.1 https://manage.hzmwmt.com/geo-agent-app/index.html || true)"

if [[ "$DD_HTTP" != 200 || "$MANAGE_HTTP" != 200 || "$MANAGE_DEEP" != 200 || "$MANAGE_GEO" != 200 ]]; then
  echo "release health check failed; restoring previous symlinks" >&2
  rollback
  exit 1
fi

printf 'FRONTEND_DEPLOY_OK\nRELEASE=%s\nDD_SHA256=%s\nMANAGE_SHA256=%s\nDD_HTTP=%s\nMANAGE_HTTP=%s\nMANAGE_DEEP=%s\nMANAGE_GEO=%s\n' \
  "$RELEASE" \
  "$(sha256sum "$DD_ARCHIVE" | awk '{print $1}')" \
  "$(sha256sum "$MANAGE_ARCHIVE" | awk '{print $1}')" \
  "$DD_HTTP" "$MANAGE_HTTP" "$MANAGE_DEEP" "$MANAGE_GEO"

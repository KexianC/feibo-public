#!/usr/bin/env bash
set -Eeuo pipefail

APP="/www/wwwroot/digital-doctor-app.jar"
PID="$(pgrep -o -f 'digital-doctor-app.jar' || true)"
[[ -s "$APP" && -n "$PID" && -r "/proc/$PID/environ" ]] || {
  echo "PREFLIGHT_ERROR=running_application_not_found"
  exit 1
}

ENVV=()
while IFS= read -r -d '' item; do ENVV+=("$item"); done < "/proc/$PID/environ"

config_value() {
  local section="$1" wanted="$2"
  unzip -p "$APP" BOOT-INF/classes/application-dev.yml |
    awk -v section="$section" -v wanted="$wanted" '
      $0 ~ "^  " section ":[[:space:]]*$" { inside=1; next }
      inside && /^  [^ ]/ { exit }
      inside && index($0, "    " wanted ":") == 1 {
        sub(/^[^:]*:[[:space:]]*/, "")
        sub(/[[:space:]]*$/, "")
        print
        exit
      }
    '
}

qwen_value() {
  local wanted="$1"
  unzip -p "$APP" BOOT-INF/classes/application-dev.yml |
    awk -v wanted="$wanted" '
      /^ai:[[:space:]]*$/ { ai=1; next }
      ai && /^[^ ]/ { exit }
      ai && /^  models:[[:space:]]*$/ { models=1; next }
      models && /^  [^ ]/ { exit }
      models && /^    qwen:[[:space:]]*$/ { qwen=1; next }
      qwen && /^    [^ ]/ { exit }
      qwen && index($0, "      " wanted ":") == 1 {
        sub(/^[^:]*:[[:space:]]*/, "")
        sub(/[[:space:]]*$/, "")
        print
        exit
      }
    '
}

normalize_scalar() {
  local scalar="$1" key fallback entry
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
    return
  fi
  for entry in "${ENVV[@]}"; do
    if [[ "$entry" == "$key="* ]]; then
      printf '%s' "${entry#*=}"
      return
    fi
  done
  printf '%s' "$fallback"
}

URL="$(normalize_scalar "$(config_value datasource url)")"
DB_USER="$(normalize_scalar "$(config_value datasource username)")"
DB_PASS="$(normalize_scalar "$(config_value datasource password)")"
QWEN_KEY="$(normalize_scalar "$(qwen_value api-key)")"

[[ "$URL" =~ ^jdbc:mysql://([^/:?]+)(:([0-9]+))?/([^?]+) ]] || {
  echo "PREFLIGHT_ERROR=unsupported_datasource_url"
  exit 1
}
DB_HOST="${BASH_REMATCH[1]}"
DB_PORT="${BASH_REMATCH[3]:-3306}"
DB_NAME="${BASH_REMATCH[4]}"
[[ -n "$DB_USER" && -n "$DB_PASS" ]] || {
  echo "PREFLIGHT_ERROR=missing_datasource_credentials"
  exit 1
}

MYSQL=(mysql --default-character-set=utf8mb4 -h "$DB_HOST" -P "$DB_PORT" -u "$DB_USER" -N -B "$DB_NAME")
sql() {
  MYSQL_PWD="$DB_PASS" "${MYSQL[@]}" -e "$1"
}

echo "PREFLIGHT_BEGIN"
echo "APP_SHA256=$(sha256sum "$APP" | awk '{print $1}')"
echo "APP_PID=$PID"
echo "QWEN_CONFIGURED=$([[ -n "$QWEN_KEY" ]] && echo yes || echo no)"
echo "MYSQL_REACHABLE=$([[ "$(sql 'SELECT 1;' || true)" == 1 ]] && echo yes || echo no)"
echo "MYSQL_VERSION=$(sql 'SELECT VERSION();')"

sql "
SELECT CONCAT('DATABASE_SIZE_BYTES=', COALESCE(SUM(data_length + index_length), 0))
FROM information_schema.tables
WHERE table_schema = DATABASE();
SELECT CONCAT('TABLE_EXPERT_ASSIGNMENT=', COUNT(*))
FROM information_schema.tables
WHERE table_schema = DATABASE() AND table_name = 't_copywriting_expert_assignment';
SELECT CONCAT('TABLE_ORG_ASSIGNMENT=', COUNT(*))
FROM information_schema.tables
WHERE table_schema = DATABASE() AND table_name = 't_copywriting_organization_admin_assignment';
SELECT CONCAT('FLOW_TOTAL=', COUNT(*)) FROM t_copywriting_approval_flow;
SELECT CONCAT('FLOW_STATUS_', current_status, '=', COUNT(*))
FROM t_copywriting_approval_flow GROUP BY current_status ORDER BY current_status;
SELECT CONCAT('ORPHAN_FLOW_STATUS_', flow.current_status, '=', COUNT(*))
FROM t_copywriting_approval_flow flow
LEFT JOIN t_copywriting c ON c.copywriting_id = flow.copywriting_id
WHERE c.copywriting_id IS NULL
GROUP BY flow.current_status ORDER BY flow.current_status;
SELECT CONCAT('ACTIVE_ROLE_ADMIN=', COUNT(DISTINCT user_role.user_id))
FROM t_user_role_map user_role
INNER JOIN t_role r ON r.role_id = user_role.role_id
INNER JOIN t_user active_user ON active_user.user_id = user_role.user_id
WHERE COALESCE(active_user.status, 0) = 0 AND UPPER(r.name) = 'ADMIN';
SELECT CONCAT('ACTIVE_ROLE_EXPERT=', COUNT(DISTINCT user_role.user_id))
FROM t_user_role_map user_role
INNER JOIN t_role r ON r.role_id = user_role.role_id
INNER JOIN t_user active_user ON active_user.user_id = user_role.user_id
WHERE COALESCE(active_user.status, 0) = 0 AND UPPER(r.name) = 'EXPERT';
SELECT CONCAT('ACTIVE_ROLE_ORG_ADMIN=', COUNT(DISTINCT user_role.user_id))
FROM t_user_role_map user_role
INNER JOIN t_role r ON r.role_id = user_role.role_id
INNER JOIN t_user active_user ON active_user.user_id = user_role.user_id
WHERE COALESCE(active_user.status, 0) = 0 AND UPPER(r.name) = 'ORG_ADMIN';
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
SELECT CONCAT('STATUS0_WRONG_PROCESSOR=', COUNT(*))
FROM t_copywriting_approval_flow flow
INNER JOIN t_copywriting c ON c.copywriting_id=flow.copywriting_id
WHERE flow.current_status=0 AND (flow.current_processor_id IS NULL OR flow.current_processor_id<>c.user_id);
SELECT CONCAT('ACTIVE_AUTHOR_ACCOUNTS=', COUNT(DISTINCT c.user_id))
FROM t_copywriting_approval_flow flow INNER JOIN t_copywriting c ON c.copywriting_id=flow.copywriting_id
WHERE flow.current_status IN (0,1,2,3);
SELECT CONCAT(
    'ADMIN_OPTION=', active_user.user_id, '|',
    LEFT(COALESCE(active_user.nick_name, ''), 8), '|',
    CASE
      WHEN active_user.phone_number IS NULL OR active_user.phone_number = '' THEN ''
      WHEN CHAR_LENGTH(active_user.phone_number) < 7 THEN '***'
      ELSE CONCAT(LEFT(active_user.phone_number, 3), '****', RIGHT(active_user.phone_number, 4))
    END
  )
FROM t_user_role_map user_role
INNER JOIN t_role r ON r.role_id = user_role.role_id
INNER JOIN t_user active_user ON active_user.user_id = user_role.user_id
WHERE COALESCE(active_user.status, 0) = 0 AND UPPER(r.name) = 'ADMIN'
ORDER BY active_user.user_id;
SELECT CONCAT(
    'AUTHOR_OPTION=', author.user_id, '|',
    LEFT(COALESCE(author.nick_name, ''), 8), '|',
    CASE
      WHEN author.phone_number IS NULL OR author.phone_number = '' THEN ''
      WHEN CHAR_LENGTH(author.phone_number) < 7 THEN '***'
      ELSE CONCAT(LEFT(author.phone_number, 3), '****', RIGHT(author.phone_number, 4))
    END, '|active_flows=', COUNT(*)
  )
FROM t_copywriting_approval_flow flow
INNER JOIN t_copywriting c ON c.copywriting_id=flow.copywriting_id
INNER JOIN t_user author ON author.user_id=c.user_id
WHERE flow.current_status IN (0,1,2,3)
GROUP BY author.user_id, author.nick_name, author.phone_number
ORDER BY author.user_id;
"
echo "PREFLIGHT_END"

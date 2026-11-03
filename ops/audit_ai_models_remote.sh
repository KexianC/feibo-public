#!/usr/bin/env bash
set -Eeuo pipefail
umask 077

# Read-only production audit for the managed "general model" tree. This script
# deliberately reports only model metadata and credential-presence booleans.
# It never prints datasource credentials, API keys, message content, or raw
# application log lines.

APP="/www/wwwroot/digital-doctor-app.jar"
CONFIG="/www/wwwroot/digital-doctor-hy3.yml"
LOG="/www/wwwroot/digital-doctor-app.szdoctor.out"
GENERAL_ROOT_BUSINESS_ID="1970339278848659457"
PYTHON3="/www/server/panel/pyenv/bin/python3.7"

die() {
  echo "AUDIT_ERROR=$*" >&2
  exit 1
}

[[ -r "$APP" && -r "$CONFIG" ]] || die "application_or_config_unavailable"
[[ -x "$PYTHON3" ]] || PYTHON3="$(command -v python3 || true)"
[[ -n "$PYTHON3" && -x "$PYTHON3" ]] || die "python3_unavailable"

PID="$(pgrep -f 'java .*digital-doctor-app\.jar' | head -n 1 || true)"
[[ "$PID" =~ ^[0-9]+$ && -r "/proc/$PID/environ" ]] || die "application_process_unavailable"

TEMP_DIR="$(mktemp -d /tmp/feibo-ai-model-audit.XXXXXX)"
cleanup() {
  case "$TEMP_DIR" in
    /tmp/feibo-ai-model-audit.*) rm -rf -- "$TEMP_DIR" ;;
  esac
}
trap cleanup EXIT

"$PYTHON3" - "$CONFIG" "/proc/$PID/environ" "$TEMP_DIR/mysql-client.cnf" "$TEMP_DIR/database-name" "$TEMP_DIR/provider-status" <<'PY'
import os
import re
import sys

config_path, environ_path, option_path, database_path, provider_status_path = sys.argv[1:]

with open(environ_path, "rb") as handle:
    runtime_env = {}
    for item in handle.read().split(b"\0"):
        if b"=" in item:
            key, value = item.split(b"=", 1)
            runtime_env[key.decode("utf-8", "replace")] = value.decode("utf-8", "replace")

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

url = runtime_env.get("SPRING_DATASOURCE_URL") or values.get("url", "")
username = runtime_env.get("SPRING_DATASOURCE_USERNAME") or values.get("username", "")
password = runtime_env.get("SPRING_DATASOURCE_PASSWORD") or values.get("password", "")
match = re.match(r"^jdbc:mysql://([^/:?]+)(?::(\d+))?/([^?]+)", url)
if not match or not username or not password:
    raise SystemExit("datasource_configuration_unavailable")

host, port, database = match.group(1), match.group(2) or "3306", match.group(3)

def quote(value):
    return '"' + value.replace("\\", "\\\\").replace('"', '\\"') + '"'

with open(option_path, "w", encoding="utf-8") as handle:
    handle.write("[client]\n")
    handle.write("host=%s\n" % quote(host))
    handle.write("port=%s\n" % quote(port))
    handle.write("user=%s\n" % quote(username))
    handle.write("password=%s\n" % quote(password))
    handle.write("default-character-set=utf8mb4\n")
os.chmod(option_path, 0o600)
with open(database_path, "w", encoding="ascii") as handle:
    handle.write(database)
os.chmod(database_path, 0o600)

# Parse only the ai.models subsection. API key values are reduced to a
# presence boolean before anything is written to the report file.
providers = {}
inside_ai = False
inside_models = False
current_provider = None
with open(config_path, encoding="utf-8") as handle:
    for raw in handle:
        if re.match(r"^ai:\s*$", raw):
            inside_ai = True
            inside_models = False
            current_provider = None
            continue
        if inside_ai and re.match(r"^\S", raw):
            inside_ai = False
            inside_models = False
            current_provider = None
        if not inside_ai:
            continue
        if re.match(r"^  models:\s*$", raw):
            inside_models = True
            continue
        if inside_models and re.match(r"^  \S", raw):
            inside_models = False
            current_provider = None
        if not inside_models:
            continue
        provider_match = re.match(r"^    ([A-Za-z0-9_-]+):\s*$", raw)
        if provider_match:
            current_provider = provider_match.group(1)
            providers.setdefault(current_provider, {})
            continue
        value_match = re.match(r"^      (base-url|api-key|enabled):\s*(.*?)\s*$", raw)
        if current_provider and value_match:
            value = value_match.group(2)
            if len(value) >= 2 and value[0] == value[-1] and value[0] in "\"'":
                value = value[1:-1]
            providers[current_provider][value_match.group(1)] = value

def resolve_placeholder(value):
    match = re.fullmatch(r"\$\{([A-Za-z_][A-Za-z0-9_]*)(?::(.*))?\}", value or "")
    if not match:
        return value or ""
    return runtime_env.get(match.group(1), match.group(2) or "")

def enabled_value(value):
    return resolve_placeholder(value or "true").strip().lower() not in ("false", "0", "no", "off")

from urllib.parse import urlsplit
with open(provider_status_path, "w", encoding="utf-8") as handle:
    for provider in sorted(providers):
        config = providers[provider]
        base_url = resolve_placeholder(config.get("base-url", ""))
        parsed = urlsplit(base_url)
        endpoint = parsed.hostname or "missing"
        key_configured = bool(resolve_placeholder(config.get("api-key", "")).strip())
        handle.write("PROVIDER_STATUS\t%s\t%s\t%s\t%s\n" % (
            provider,
            "enabled" if enabled_value(config.get("enabled")) else "disabled",
            endpoint,
            "key_yes" if key_configured else "key_no"))
os.chmod(provider_status_path, 0o600)

# Provider model-list endpoints are metadata-only and do not invoke inference.
# Query only public vendors whose OpenAI-compatible list route is documented;
# return IDs/status only, never raw bodies or error payloads.
import json
import urllib.error
import urllib.request

list_routes = {
    "ant": "/v1/models",
    "deepseek": "/v1/models",
    "doubao": "/api/v3/models",
    "qwen": "/compatible-mode/v1/models",
    "qianfan": "/models",
    "baichuan": "/v1/models",
    "glm52": "/models",
    "hy3": "/models",
    "szdoctor": "/v1/models",
    "wisediag": "/v1/models",
}

def relevant_model(provider, model_id):
    lower = model_id.lower()
    if provider == "deepseek":
        return lower.startswith("deepseek-")
    if provider == "doubao":
        return lower.startswith("doubao-seed-")
    if provider == "qwen":
        return lower.startswith("qwen3.8-") or lower.startswith("qwen3.7-") or lower in {
            "qwen3-max-preview", "qwen-max", "qwen-flash", "qwen3.5-plus", "qwen3.5-flash"
        }
    if provider == "qianfan":
        return lower.startswith("ernie-")
    if provider == "baichuan":
        return lower.startswith("baichuan")
    if provider == "glm52":
        return lower.startswith("glm-5")
    if provider == "hy3":
        return lower.startswith("hy3")
    if provider in {"ant", "szdoctor", "wisediag"}:
        return True
    return False

with open(provider_status_path, "a", encoding="utf-8") as handle:
    for provider in sorted(list_routes):
        config = providers.get(provider, {})
        base_url = resolve_placeholder(config.get("base-url", "")).rstrip("/")
        api_key = resolve_placeholder(config.get("api-key", "")).strip()
        if not base_url or not api_key or not enabled_value(config.get("enabled")):
            handle.write("PROVIDER_MODELS\t%s\tskipped\t0\t\n" % provider)
            continue
        url = base_url + list_routes[provider]
        request = urllib.request.Request(url, headers={
            "Authorization": "Bearer " + api_key,
            "Accept": "application/json",
            "User-Agent": "feibo-read-only-model-audit/1.0",
        })
        try:
            with urllib.request.urlopen(request, timeout=15) as response:
                payload = json.loads(response.read().decode("utf-8"))
                data = payload.get("data", []) if isinstance(payload, dict) else []
                ids = sorted({
                    str(item.get("id"))
                    for item in data
                    if isinstance(item, dict) and item.get("id")
                    and relevant_model(provider, str(item.get("id")))
                })
                handle.write("PROVIDER_MODELS\t%s\thttp_%s\t%s\t%s\n" % (
                    provider, response.getcode(), len(ids), ",".join(ids[:100])))
        except urllib.error.HTTPError as error:
            handle.write("PROVIDER_MODELS\t%s\thttp_%s\t0\t\n" % (provider, error.code))
        except Exception as error:
            handle.write("PROVIDER_MODELS\t%s\t%s\t0\t\n" % (
                provider, error.__class__.__name__))
PY

DB_NAME="$(cat "$TEMP_DIR/database-name")"
[[ "$DB_NAME" =~ ^[A-Za-z0-9_\$-]+$ ]] || die "database_name_invalid"
MYSQL=(mysql --defaults-extra-file="$TEMP_DIR/mysql-client.cnf" --batch --skip-column-names --raw "$DB_NAME")

echo "AUDIT_BEGIN"
echo "AUDIT_TIME_UTC=$(date -u +%Y-%m-%dT%H:%M:%SZ)"
echo "APP_PID=$PID"
echo "APP_SHA256=$(sha256sum "$APP" | awk '{print $1}')"
cat "$TEMP_DIR/provider-status"

"${MYSQL[@]}" <<SQL
SET SESSION TRANSACTION READ ONLY;
START TRANSACTION READ ONLY;
SELECT CONCAT_WS('\t', 'DATABASE_RUNTIME', VERSION());

SELECT CONCAT_WS('\t', 'TABLE_ENGINE', table_name, engine)
FROM information_schema.tables
WHERE table_schema = DATABASE()
  AND table_name IN ('t_ai_model_config', 't_ai_message')
ORDER BY table_name;

SELECT CONCAT_WS('\t',
    'MODEL',
    provider.ai_model_config_id,
    provider.sort,
    REPLACE(REPLACE(COALESCE(provider.name, ''), '\t', ' '), '\n', ' '),
    leaf.ai_model_config_id,
    leaf.sort,
    REPLACE(REPLACE(COALESCE(leaf.name, ''), '\t', ' '), '\n', ' '),
    REPLACE(REPLACE(COALESCE(leaf.display_name, ''), '\t', ' '), '\n', ' '),
    LEFT(REPLACE(REPLACE(COALESCE(leaf.description, ''), '\t', ' '), '\n', ' '), 500),
    LEFT(REPLACE(REPLACE(COALESCE(leaf.display_description, ''), '\t', ' '), '\n', ' '), 500),
    COALESCE(leaf.model_code, ''),
    COALESCE(CAST(leaf.type AS SIGNED), ''),
    COALESCE(CAST(leaf.visible AS UNSIGNED), 1),
    CASE WHEN NULLIF(leaf.api_key_ciphertext, '') IS NULL THEN 'no' ELSE 'yes' END,
    COALESCE(usage_stats.total_messages, 0),
    COALESCE(usage_stats.messages_30d, 0),
    COALESCE(DATE_FORMAT(usage_stats.last_used_at, '%Y-%m-%dT%H:%i:%s'), 'never'))
FROM t_ai_model_config AS leaf
INNER JOIN t_ai_model_config AS provider ON provider.id = leaf.parent_id
INNER JOIN t_ai_model_config AS root ON root.id = provider.parent_id
LEFT JOIN (
    SELECT ai_model_config_id,
           COUNT(*) AS total_messages,
           SUM(create_time >= DATE_SUB(NOW(), INTERVAL 30 DAY)) AS messages_30d,
           MAX(create_time) AS last_used_at
    FROM t_ai_message
    GROUP BY ai_model_config_id
) AS usage_stats ON usage_stats.ai_model_config_id = leaf.ai_model_config_id
WHERE root.ai_model_config_id = $GENERAL_ROOT_BUSINESS_ID
  AND leaf.is_leaf = 1
ORDER BY (provider.sort IS NULL), provider.sort, provider.ai_model_config_id,
         (leaf.sort IS NULL), leaf.sort, leaf.ai_model_config_id;

SELECT CONCAT_WS('\t', 'COLUMN_REFERENCE', table_name, column_name, data_type)
FROM information_schema.columns
WHERE table_schema = DATABASE()
  AND column_name = 'ai_model_config_id'
ORDER BY table_name;

SELECT CONCAT_WS('\t', 'FOREIGN_KEY', table_name, column_name,
                 referenced_table_name, referenced_column_name)
FROM information_schema.key_column_usage
WHERE table_schema = DATABASE()
  AND referenced_table_name = 't_ai_model_config'
ORDER BY table_name, constraint_name;

SELECT CONCAT_WS('\t', 'MESSAGE_INDEX', index_name, seq_in_index,
                 column_name, non_unique)
FROM information_schema.statistics
WHERE table_schema = DATABASE()
  AND table_name = 't_ai_message'
ORDER BY index_name, seq_in_index;

SELECT CONCAT_WS('\t', 'ORPHAN_MESSAGE_MODEL_REFERENCES', COUNT(*))
FROM t_ai_message AS message_reference
LEFT JOIN t_ai_model_config AS model
  ON model.ai_model_config_id = message_reference.ai_model_config_id
WHERE message_reference.ai_model_config_id IS NOT NULL
  AND model.id IS NULL;

SELECT CONCAT_WS('\t', 'ORPHAN_MESSAGE_MODEL',
                 message_reference.ai_model_config_id,
                 COUNT(*),
                 COALESCE(DATE_FORMAT(MAX(message_reference.create_time),
                                      '%Y-%m-%dT%H:%i:%s'), 'never'))
FROM t_ai_message AS message_reference
LEFT JOIN t_ai_model_config AS model
  ON model.ai_model_config_id = message_reference.ai_model_config_id
WHERE message_reference.ai_model_config_id IS NOT NULL
  AND model.id IS NULL
GROUP BY message_reference.ai_model_config_id
ORDER BY COUNT(*) DESC, message_reference.ai_model_config_id;
COMMIT;
SQL

if [[ -r "$LOG" ]]; then
  TAIL_FILE="$TEMP_DIR/log-tail"
  tail -n 250000 "$LOG" > "$TAIL_FILE"
  count_pattern() {
    local label="$1"
    local pattern="$2"
    local count
    count="$(grep -Eic -- "$pattern" "$TAIL_FILE" || true)"
    echo "LOG_SIGNAL\t${label}\t${count}"
  }
  count_pattern "model_not_found" "model[^[:cntrl:]]*(not[ _-]?found|does not exist|invalid|不存在|无效|下线)|模型[^[:cntrl:]]*(不存在|无效|下线)"
  count_pattern "http_400" "(^|[^0-9])400([^0-9]|$)|Bad Request"
  count_pattern "http_401_403" "(^|[^0-9])(401|403)([^0-9]|$)|Unauthorized|Forbidden"
  count_pattern "http_404" "(^|[^0-9])404([^0-9]|$)|Not Found"
  count_pattern "quota_or_balance" "insufficient[_ -]?(quota|balance)|quota[^[:cntrl:]]*exceed|余额不足|配额不足"
  count_pattern "provider_call_failure" "API调用失败|模型调用失败|流式问答请求失败"
else
  echo "LOG_SIGNAL\tlog_unavailable\t1"
fi

echo "AUDIT_END"
exit 0

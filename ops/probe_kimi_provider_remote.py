#!/usr/bin/env python3
import base64
import json
import os
import re
import subprocess
import sys
import tempfile
import urllib.error
import urllib.request

from cryptography.hazmat.primitives.ciphers.aead import AESGCM


APP_CONFIG = "/www/wwwroot/digital-doctor-hy3.yml"
MODEL_ID = "9005300000000000001"
PROCESS_ENVIRON = "/proc/{}/environ"


def fail(reason):
    print("KIMI_PROBE_ERROR=" + reason)
    raise SystemExit(1)


def runtime_pid():
    output = subprocess.check_output(
        ["pgrep", "-f", "java .*digital-doctor-app\\.jar"],
        text=True,
    ).splitlines()
    candidates = [value.strip() for value in output if value.strip().isdigit()]
    if not candidates:
        fail("application_process_unavailable")
    return candidates[0]


def runtime_environment(pid):
    with open(PROCESS_ENVIRON.format(pid), "rb") as handle:
        result = {}
        for item in handle.read().split(b"\0"):
            if b"=" in item:
                key, value = item.split(b"=", 1)
                result[key.decode("utf-8", "replace")] = value.decode("utf-8", "replace")
        return result


def unquote(value):
    value = value.strip()
    if len(value) >= 2 and value[0] == value[-1] and value[0] in "\"'":
        return value[1:-1]
    return value


def resolve(value, environment):
    value = unquote(value or "")
    match = re.fullmatch(r"\$\{([A-Za-z_][A-Za-z0-9_]*)(?::(.*))?\}", value)
    if not match:
        return value
    return environment.get(match.group(1), match.group(2) or "")


def read_configuration(environment):
    datasource = {}
    encryption_secret = ""
    kimi = {}
    section = None
    in_ai = False
    in_models = False
    in_kimi = False
    with open(APP_CONFIG, encoding="utf-8") as handle:
        for raw in handle:
            if re.match(r"^  datasource:\s*$", raw):
                section = "datasource"
                continue
            if section == "datasource" and re.match(r"^  \S", raw):
                section = None
            if section == "datasource":
                match = re.match(r"^    (url|username|password):\s*(.*?)\s*$", raw)
                if match:
                    datasource[match.group(1)] = match.group(2)

            if re.match(r"^ai:\s*$", raw):
                in_ai = True
                in_models = False
                in_kimi = False
                continue
            if in_ai and re.match(r"^\S", raw):
                in_ai = False
                in_models = False
                in_kimi = False
            if not in_ai:
                continue
            match = re.match(r"^  model-key-encryption-secret:\s*(.*?)\s*$", raw)
            if match:
                encryption_secret = match.group(1)
                continue
            if re.match(r"^  models:\s*$", raw):
                in_models = True
                continue
            if in_models and re.match(r"^    kimik3:\s*$", raw):
                in_kimi = True
                continue
            if in_kimi and re.match(r"^    [A-Za-z0-9_-]+:\s*$", raw):
                in_kimi = False
            if in_kimi:
                match = re.match(r"^      (base-url|enabled):\s*(.*?)\s*$", raw)
                if match:
                    kimi[match.group(1)] = match.group(2)

    url = environment.get("SPRING_DATASOURCE_URL") or resolve(datasource.get("url"), environment)
    username = environment.get("SPRING_DATASOURCE_USERNAME") or resolve(datasource.get("username"), environment)
    password = environment.get("SPRING_DATASOURCE_PASSWORD") or resolve(datasource.get("password"), environment)
    secret = resolve(encryption_secret, environment)
    base_url = resolve(kimi.get("base-url"), environment)
    enabled = resolve(kimi.get("enabled", "true"), environment).lower() not in {"false", "0", "no", "off"}
    match = re.match(r"^jdbc:mysql://([^/:?]+)(?::(\d+))?/([^?]+)", url or "")
    if not match or not username or not password or not secret or not base_url or not enabled:
        fail("runtime_configuration_unavailable")
    return {
        "host": match.group(1),
        "port": match.group(2) or "3306",
        "database": match.group(3),
        "username": username,
        "password": password,
        "secret": secret,
        "base_url": base_url,
    }


def mysql_row(configuration):
    fd, option_path = tempfile.mkstemp(prefix="kimi-probe-mysql-", suffix=".cnf")
    os.close(fd)
    try:
        os.chmod(option_path, 0o600)
        quote = lambda value: '"' + value.replace("\\", "\\\\").replace('"', '\\"') + '"'
        with open(option_path, "w", encoding="utf-8") as handle:
            handle.write("[client]\n")
            handle.write("host=%s\n" % quote(configuration["host"]))
            handle.write("port=%s\n" % quote(configuration["port"]))
            handle.write("user=%s\n" % quote(configuration["username"]))
            handle.write("password=%s\n" % quote(configuration["password"]))
            handle.write("default-character-set=utf8mb4\n")
        query = (
            "SELECT api_key_ciphertext,name,model_code,CAST(type AS UNSIGNED) "
            "FROM t_ai_model_config WHERE ai_model_config_id=%s "
            "AND is_leaf=1 AND COALESCE(visible,1)=1 AND COALESCE(is_deleted,0)=0" % MODEL_ID
        )
        output = subprocess.check_output([
            "mysql", "--defaults-extra-file=" + option_path,
            "--batch", "--skip-column-names", "--raw",
            configuration["database"], "-e", query,
        ], text=True).rstrip("\r\n")
    finally:
        try:
            os.remove(option_path)
        except FileNotFoundError:
            pass
    parts = output.split("\t")
    if len(parts) != 4:
        fail("model_row_unavailable")
    return parts


def decrypt_key(secret, ciphertext):
    if not ciphertext.startswith("v1:"):
        fail("credential_format_invalid")
    try:
        key = base64.b64decode(secret.strip(), validate=True)
        envelope = base64.b64decode(ciphertext[3:], validate=True)
        if len(key) != 32 or len(envelope) <= 12:
            fail("credential_envelope_invalid")
        return AESGCM(key).decrypt(
            envelope[:12],
            envelope[12:],
            ("ai-model-key:v1:" + MODEL_ID).encode("utf-8"),
        ).decode("utf-8")
    except Exception:
        fail("credential_decryption_failed")


def probe(base_url, api_key, model_name):
    payload = json.dumps({
        "model": model_name,
        "messages": [{"role": "user", "content": "仅回复 OK"}],
        "temperature": 1,
        "max_tokens": 256,
        "stream": False,
    }, ensure_ascii=False).encode("utf-8")
    request = urllib.request.Request(
        base_url.rstrip("/") + "/chat/completions",
        data=payload,
        method="POST",
        headers={
            "Authorization": "Bearer " + api_key,
            "Content-Type": "application/json",
            "Accept": "application/json",
            "User-Agent": "feibo-kimi-production-probe/1.0",
        },
    )
    try:
        with urllib.request.urlopen(request, timeout=45) as response:
            status = response.getcode()
            body = json.loads(response.read().decode("utf-8"))
    except urllib.error.HTTPError as error:
        status = error.code
        try:
            body = json.loads(error.read().decode("utf-8"))
        except Exception:
            body = {}
    except Exception as error:
        print("KIMI_PROVIDER_RESULT=transport_error")
        print("ERROR_CLASS=" + error.__class__.__name__)
        raise SystemExit(1)

    print("KIMI_PROVIDER_HTTP=%s" % status)
    if 200 <= status < 300:
        choices = body.get("choices") if isinstance(body, dict) else None
        content = ""
        reasoning_content = ""
        finish_reason = ""
        if isinstance(choices, list) and choices and isinstance(choices[0], dict):
            finish_reason = str(choices[0].get("finish_reason") or "")
            message = choices[0].get("message")
            if isinstance(message, dict):
                content = str(message.get("content") or "").strip()
                reasoning_content = str(message.get("reasoning_content") or "").strip()
        usage = body.get("usage") if isinstance(body, dict) else None
        if not isinstance(usage, dict):
            usage = {}
        print("KIMI_PROVIDER_RESULT=" + ("success" if content else "empty_response"))
        print("ASSISTANT_REPLY=" + content[:100].replace("\n", " "))
        print("REASONING_PRESENT=" + ("yes" if reasoning_content else "no"))
        print("FINISH_REASON=" + finish_reason[:50])
        print("COMPLETION_TOKENS=" + str(usage.get("completion_tokens") or 0))
        return 0 if content else 1
    error = body.get("error") if isinstance(body, dict) else None
    if not isinstance(error, dict):
        error = {}
    print("KIMI_PROVIDER_RESULT=http_error")
    print("PROVIDER_ERROR_CODE=" + str(error.get("code") or "unknown")[:100])
    print("PROVIDER_ERROR_MESSAGE=" + str(error.get("message") or "unavailable")[:300].replace("\n", " "))
    return 1


pid = runtime_pid()
environment = runtime_environment(pid)
configuration = read_configuration(environment)
ciphertext, model_name, model_code, model_type = mysql_row(configuration)
if model_code != "kimik3" or model_type != "0":
    fail("model_runtime_state_invalid")
api_key = decrypt_key(configuration["secret"], ciphertext)
print("KIMI_PLATFORM_QUOTA=disabled")
print("KIMI_MODEL=" + model_name)
raise SystemExit(probe(configuration["base_url"], api_key, model_name))

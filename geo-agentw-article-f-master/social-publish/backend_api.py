import asyncio
import hmac
import os
import sqlite3
import threading
import time
import uuid
from contextlib import contextmanager
from pathlib import Path
from queue import Empty, Queue

from flask import Flask, Response, abort, g, jsonify, request
from flask_cors import CORS
from werkzeug.utils import secure_filename

from config import BASE_DIR


DATABASE_PATH = Path(BASE_DIR) / "db" / "database.db"
ALLOWED_IMAGE_EXTENSIONS = {"jpg", "jpeg", "png", "webp"}


@contextmanager
def connect_database():
    connection = sqlite3.connect(DATABASE_PATH, timeout=10)
    try:
        with connection:
            yield connection
    finally:
        connection.close()


def initialize_storage():
    """Create an isolated publish data root without overwriting existing data."""
    base_path = Path(BASE_DIR)
    base_path.mkdir(parents=True, exist_ok=True)
    for directory in ("db", "cookiesFiles", "imgFiles", "logs"):
        (base_path / directory).mkdir(parents=True, exist_ok=True)

    with connect_database() as conn:
        conn.executescript(
            """
            CREATE TABLE IF NOT EXISTS user_info (
                id INTEGER PRIMARY KEY AUTOINCREMENT,
                type INTEGER NOT NULL,
                filePath TEXT NOT NULL,
                userName TEXT NOT NULL,
                status INTEGER DEFAULT 0,
                user_id TEXT
            );

            CREATE TABLE IF NOT EXISTS file_records (
                id INTEGER PRIMARY KEY AUTOINCREMENT,
                filename TEXT NOT NULL,
                filesize REAL,
                upload_time DATETIME DEFAULT CURRENT_TIMESTAMP,
                file_path TEXT,
                user_id TEXT
            );

            CREATE INDEX IF NOT EXISTS idx_user_info_user_id
                ON user_info (user_id);
            CREATE INDEX IF NOT EXISTS idx_file_records_user_id
                ON file_records (user_id);
            """
        )


# Storage must exist before importing browser modules because their loggers write
# under BASE_DIR during module initialization.
initialize_storage()

from login.baijiahao import baijiahao_cookie_gen
from login.jinritoutiao import jrtt_cookie_gen
from login.xiaohongshu import xiaohongshu_cookie_gen
from login.zhihu import zhihu_cookie_gen
from postArtical.baijiahao.baijiahao import post_artical_baijiahao
from postArtical.jinritoutiao.jinritoutiao import post_artical_jinritoutiao
from postArtical.xiaohongshu.xiaohongshu import post_artical_xiaohongshu
from postArtical.zhihu.zhihu import post_artical_zhihu
from utils.auth import check_cookie


active_queues = {}
active_queues_lock = threading.Lock()

app = Flask(__name__)
allowed_origins = [
    origin.strip()
    for origin in os.getenv("PUBLISH_ALLOWED_ORIGINS", "").split(",")
    if origin.strip()
]
if allowed_origins:
    CORS(app, origins=allowed_origins, supports_credentials=True)

INTERNAL_TOKEN = os.getenv("PUBLISH_INTERNAL_TOKEN", "").strip()
if len(INTERNAL_TOKEN) < 32:
    raise RuntimeError("PUBLISH_INTERNAL_TOKEN must be configured with at least 32 characters")
app.config["MAX_CONTENT_LENGTH"] = int(
    os.getenv("PUBLISH_MAX_UPLOAD_BYTES", str(20 * 1024 * 1024))
)


def error_response(code, message, status):
    return jsonify({"code": code, "msg": message, "data": None}), status


def log_internal_exception(operation, exc):
    """Record a diagnosable failure without persisting exception secrets."""
    app.logger.error(
        "%s failed; exception_type=%s", operation, type(exc).__name__
    )


@app.before_request
def require_request_identity():
    if request.method == "OPTIONS":
        return None
    provided_token = request.headers.get("X-Internal-Token", "")
    if not hmac.compare_digest(provided_token, INTERNAL_TOKEN):
        abort(403)

    user_id = (request.headers.get("userid") or "").strip()
    if not user_id:
        return error_response(400, "缺少用户ID", 400)
    g.user_id = user_id
    return None


def resolve_under_base(base_dir, *parts):
    base_path = Path(base_dir).resolve()
    target_path = base_path.joinpath(*parts).resolve()
    if target_path != base_path and base_path not in target_path.parents:
        raise ValueError("path outside storage directory")
    return target_path


def allowed_image_filename(filename):
    suffix = Path(filename).suffix.lower().lstrip(".")
    return suffix in ALLOWED_IMAGE_EXTENSIONS


def parse_positive_id(raw_value):
    try:
        value = int(raw_value)
    except (TypeError, ValueError):
        return None
    return value if value > 0 else None


def find_owned_account_by_id(user_id, account_id):
    with connect_database() as conn:
        conn.row_factory = sqlite3.Row
        return conn.execute(
            "SELECT * FROM user_info WHERE id = ? AND user_id = ?",
            (account_id, user_id),
        ).fetchone()


def find_owned_account_by_cookie(user_id, cookie_file, platform_type):
    with connect_database() as conn:
        conn.row_factory = sqlite3.Row
        return conn.execute(
            """
            SELECT * FROM user_info
            WHERE user_id = ? AND filePath = ? AND type = ?
            LIMIT 1
            """,
            (user_id, cookie_file, platform_type),
        ).fetchone()


def find_owned_file_by_path(user_id, file_path):
    with connect_database() as conn:
        conn.row_factory = sqlite3.Row
        return conn.execute(
            """
            SELECT * FROM file_records
            WHERE user_id = ? AND file_path = ?
            LIMIT 1
            """,
            (user_id, file_path),
        ).fetchone()


@app.route("/getAccounts", methods=["GET"])
def getAccounts():
    """Return only accounts owned by the authenticated upstream user."""
    try:
        with connect_database() as conn:
            conn.row_factory = sqlite3.Row
            rows = conn.execute(
                "SELECT * FROM user_info WHERE user_id = ?", (g.user_id,)
            ).fetchall()
        rows_dict = [dict(row) for row in rows]
        print(f"accounts fetched: count={len(rows_dict)}")
        return jsonify({"code": 200, "msg": None, "data": rows_dict}), 200
    except Exception as exc:
        log_internal_exception("get accounts", exc)
        return error_response(500, "获取账号列表失败", 500)


@app.route("/verifyAccount", methods=["POST"])
def verifyAccount():
    account_id = parse_positive_id(request.args.get("id"))
    if account_id is None:
        return error_response(400, "无效的账号ID", 400)

    account = find_owned_account_by_id(g.user_id, account_id)
    if account is None:
        return error_response(404, "account not found", 404)

    try:
        cookie_path = resolve_under_base(
            Path(BASE_DIR) / "cookiesFiles", account["filePath"]
        )
    except (TypeError, ValueError):
        return error_response(404, "account not found", 404)
    if not cookie_path.is_file():
        return error_response(404, "account cookie not found", 404)

    print(f"verifying account: accountId={account_id}")
    is_valid = asyncio.run(check_cookie(account["type"], account["filePath"]))
    if not is_valid:
        with connect_database() as conn:
            conn.execute(
                "UPDATE user_info SET status = ? WHERE id = ? AND user_id = ?",
                (0, account_id, g.user_id),
            )
        print(f"account marked invalid: accountId={account_id}")
        return jsonify({"code": 500, "msg": "账号验证失败", "data": None}), 200

    print(f"account verified: accountId={account_id}")
    return jsonify({"code": 200, "msg": "账号验证通过", "data": None}), 200


@app.route("/deleteAccount", methods=["DELETE"])
def delete_account():
    account_id = parse_positive_id(request.args.get("id"))
    if account_id is None:
        return error_response(400, "无效的账号ID", 400)

    try:
        with connect_database() as conn:
            conn.row_factory = sqlite3.Row
            record = conn.execute(
                "SELECT * FROM user_info WHERE id = ? AND user_id = ?",
                (account_id, g.user_id),
            ).fetchone()
            if record is None:
                return error_response(404, "account not found", 404)

            try:
                file_path = resolve_under_base(
                    Path(BASE_DIR) / "cookiesFiles", record["filePath"]
                )
            except (TypeError, ValueError):
                return error_response(404, "account not found", 404)

            deleted = conn.execute(
                "DELETE FROM user_info WHERE id = ? AND user_id = ?",
                (account_id, g.user_id),
            ).rowcount
            if deleted != 1:
                return error_response(404, "account not found", 404)

        try:
            file_path.unlink()
            print(f"account cookie file deleted: accountId={account_id}")
        except FileNotFoundError:
            print(f"account cookie file missing: accountId={account_id}")
        except PermissionError:
            print(f"account cookie file delete denied: accountId={account_id}")
        except Exception as exc:
            log_internal_exception("delete account cookie file", exc)

        return jsonify(
            {"code": 200, "msg": "account deleted successfully", "data": None}
        ), 200
    except Exception as exc:
        log_internal_exception("delete account", exc)
        return error_response(500, "delete failed!", 500)


@app.route("/uploadSave", methods=["POST"])
def upload_save():
    if "file" not in request.files:
        return error_response(400, "未检测到上传文件，请先上传", 400)

    uploaded_file = request.files["file"]
    if not uploaded_file.filename:
        return error_response(400, "文件名称不能为空", 400)

    filename = secure_filename(uploaded_file.filename)
    if not filename or not allowed_image_filename(filename):
        return error_response(400, "unsupported file type", 400)

    final_filename = f"{uuid.uuid4()}_{filename}"
    storage_dir = resolve_under_base(BASE_DIR, "imgFiles")
    filepath = resolve_under_base(storage_dir, final_filename)

    try:
        uploaded_file.save(filepath)
        with connect_database() as conn:
            cursor = conn.execute(
                """
                INSERT INTO file_records (filename, filesize, file_path, user_id)
                VALUES (?, ?, ?, ?)
                """,
                (
                    filename,
                    round(float(filepath.stat().st_size) / (1024 * 1024), 2),
                    final_filename,
                    g.user_id,
                ),
            )
            file_id = cursor.lastrowid
        print(f"upload file recorded: fileId={file_id}")
        return jsonify(
            {
                "code": 200,
                "msg": "文件上传并保存成功",
                "data": {
                    "id": file_id,
                    "filename": filename,
                    "filepath": final_filename,
                },
            }
        ), 200
    except Exception as exc:
        try:
            filepath.unlink(missing_ok=True)
        except OSError:
            pass
        log_internal_exception("upload file", exc)
        return error_response(500, "上传文件失败", 500)


@app.route("/getFiles", methods=["GET"])
def get_all_files():
    try:
        with connect_database() as conn:
            conn.row_factory = sqlite3.Row
            rows = conn.execute(
                "SELECT * FROM file_records WHERE user_id = ?", (g.user_id,)
            ).fetchall()
        return jsonify(
            {"code": 200, "msg": "success", "data": [dict(row) for row in rows]}
        ), 200
    except Exception as exc:
        log_internal_exception("get files", exc)
        return error_response(500, "获取文件失败", 500)


@app.route("/deleteFile", methods=["DELETE"])
def delete_file():
    file_id = parse_positive_id(request.args.get("id"))
    if file_id is None:
        return error_response(400, "缺少参数，文件id！", 400)

    try:
        with connect_database() as conn:
            conn.row_factory = sqlite3.Row
            record = conn.execute(
                "SELECT * FROM file_records WHERE id = ? AND user_id = ?",
                (file_id, g.user_id),
            ).fetchone()
            if record is None:
                return error_response(404, "文件未找到！", 404)

            try:
                file_path = resolve_under_base(
                    Path(BASE_DIR) / "imgFiles", record["file_path"]
                )
            except (TypeError, ValueError):
                return error_response(404, "文件未找到！", 404)

            deleted = conn.execute(
                "DELETE FROM file_records WHERE id = ? AND user_id = ?",
                (file_id, g.user_id),
            ).rowcount
            if deleted != 1:
                return error_response(404, "文件未找到！", 404)

        try:
            file_path.unlink(missing_ok=True)
            print(f"asset file deleted: fileId={file_id}")
        except OSError as exc:
            log_internal_exception("delete asset file", exc)

        return jsonify({"code": 200, "msg": "文件删除成功！", "data": None}), 200
    except Exception as exc:
        log_internal_exception("delete file", exc)
        return error_response(500, "文件删除失败", 500)


@app.route("/postArticle", methods=["POST"])
def postArtical():
    data = request.get_json(silent=True)
    if not isinstance(data, dict):
        return error_response(400, "无效的发布请求", 400)

    try:
        platform_type = int(data.get("type"))
    except (TypeError, ValueError):
        return error_response(400, "无效的平台类型", 400)
    if platform_type not in {1, 2, 3, 4}:
        return error_response(400, "无效的平台类型", 400)

    cookie_file = data.get("cookie_file")
    if not isinstance(cookie_file, str) or not cookie_file.strip():
        return error_response(400, "缺少账号 Cookie 文件", 400)
    cookie_file = cookie_file.strip()

    account = find_owned_account_by_cookie(g.user_id, cookie_file, platform_type)
    if account is None:
        return error_response(404, "account not found", 404)

    try:
        cookie_path = resolve_under_base(Path(BASE_DIR) / "cookiesFiles", account["filePath"])
    except ValueError:
        return error_response(404, "account not found", 404)
    if not cookie_path.is_file():
        return error_response(404, "account cookie not found", 404)

    title = data.get("title")
    content = data.get("content")
    image = data.get("img")
    tags = data.get("tags")
    text = data.get("text")
    owned_cookie_file = account["filePath"]

    if image is not None and not isinstance(image, str):
        return error_response(400, "无效的素材文件", 400)
    owned_image = image.strip() if isinstance(image, str) else image
    if platform_type in {1, 3, 4} and not owned_image:
        return error_response(400, "缺少发布素材", 400)
    if owned_image:
        image_record = find_owned_file_by_path(g.user_id, owned_image)
        if image_record is None:
            return error_response(404, "asset not found", 404)
        try:
            image_path = resolve_under_base(
                Path(BASE_DIR) / "imgFiles", image_record["file_path"]
            )
        except (TypeError, ValueError):
            return error_response(404, "asset not found", 404)
        if not image_path.is_file():
            return error_response(404, "asset file not found", 404)
        owned_image = image_record["file_path"]

    print(
        "publish request received: "
        f"platform={platform_type}, titleLength={len(title or '')}, "
        f"contentLength={len(content or '')}"
    )
    match platform_type:
        case 1:
            post_artical_zhihu(title, content, owned_image, tags, owned_cookie_file)
        case 2:
            post_artical_xiaohongshu(
                title, content, text, tags, owned_cookie_file
            )
        case 3:
            post_artical_jinritoutiao(
                title, content, owned_image, tags, owned_cookie_file
            )
        case 4:
            post_artical_baijiahao(
                title, content, owned_image, tags, owned_cookie_file
            )

    return jsonify({"code": 200, "msg": "发布成功", "data": None})


@app.route("/login")
def login():
    platform_type = request.args.get("type")
    username = (request.args.get("username") or "").strip()
    if platform_type not in {"1", "2", "3", "4"}:
        return error_response(400, "无效的平台类型", 400)
    if not username:
        return error_response(400, "账号名不能为空", 400)

    status_queue = Queue()
    queue_key = (g.user_id, username)
    with active_queues_lock:
        active_queues[queue_key] = status_queue

    thread = threading.Thread(
        target=run_async_function,
        args=(platform_type, username, status_queue, g.user_id),
        daemon=True,
    )
    thread.start()

    response = Response(
        sse_stream(status_queue, queue_key), mimetype="text/event-stream"
    )
    response.headers["Cache-Control"] = "no-cache"
    response.headers["X-Accel-Buffering"] = "no"
    response.headers["Content-Type"] = "text/event-stream"
    response.headers["Connection"] = "keep-alive"
    return response


def run_async_function(platform_type, username, status_queue, user_id):
    generators = {
        "1": zhihu_cookie_gen,
        "2": xiaohongshu_cookie_gen,
        "3": jrtt_cookie_gen,
        "4": baijiahao_cookie_gen,
    }
    generator = generators.get(platform_type)
    if generator is None:
        status_queue.put("400")
        return

    loop = asyncio.new_event_loop()
    try:
        asyncio.set_event_loop(loop)
        loop.run_until_complete(generator(username, status_queue, user_id))
    except Exception as exc:
        log_internal_exception("account login", exc)
        status_queue.put("500")
    finally:
        loop.close()


def sse_stream(status_queue, queue_key, timeout_seconds=200):
    last_activity = time.time()
    try:
        while True:
            try:
                message = status_queue.get(timeout=1)
                last_activity = time.time()
                yield f"data:{message}\n\n"
            except Empty:
                if time.time() - last_activity > timeout_seconds:
                    print("SSE connection closed after inactivity timeout")
                    break
    finally:
        with active_queues_lock:
            if active_queues.get(queue_key) is status_queue:
                active_queues.pop(queue_key, None)
                print("SSE queue cleanup completed")


if __name__ == "__main__":
    app.run(
        host=os.getenv("PUBLISH_HOST", "127.0.0.1"),
        port=int(os.getenv("PUBLISH_PORT", "5409")),
    )

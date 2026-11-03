import io
import os
import shutil
import subprocess
import sys
import tempfile
import unittest
from pathlib import Path
from unittest.mock import AsyncMock, patch


SERVICE_ROOT = Path(__file__).resolve().parents[1]
TEST_STORAGE = Path(tempfile.mkdtemp(prefix="social-publish-test-"))
os.environ["SOCIAL_PUBLISH_BASE_DIR"] = str(TEST_STORAGE)
os.environ["PUBLISH_INTERNAL_TOKEN"] = "test-only-publish-token-32-characters"
sys.path.insert(0, str(SERVICE_ROOT))

import backend_api


class BackendApiIsolationTest(unittest.TestCase):
    @classmethod
    def setUpClass(cls):
        backend_api.app.config.update(TESTING=True)

    def setUp(self):
        self.client = backend_api.app.test_client()
        with backend_api.connect_database() as conn:
            conn.execute("DELETE FROM user_info")
            conn.execute("DELETE FROM file_records")
        for directory in ("cookiesFiles", "imgFiles"):
            for path in (TEST_STORAGE / directory).iterdir():
                if path.is_file():
                    path.unlink()
        with backend_api.active_queues_lock:
            backend_api.active_queues.clear()

    def headers(self, user_id):
        return {
            "userid": user_id,
            "X-Internal-Token": os.environ["PUBLISH_INTERNAL_TOKEN"],
        }

    def insert_account(self, user_id, platform_type=1, cookie_file=None):
        cookie_file = cookie_file or f"{user_id}-{platform_type}.json"
        (TEST_STORAGE / "cookiesFiles" / cookie_file).write_text(
            "{}", encoding="utf-8"
        )
        with backend_api.connect_database() as conn:
            cursor = conn.execute(
                """
                INSERT INTO user_info (type, filePath, userName, status, user_id)
                VALUES (?, ?, ?, ?, ?)
                """,
                (platform_type, cookie_file, f"account-{user_id}", 1, user_id),
            )
            return cursor.lastrowid, cookie_file

    def insert_file(self, user_id, file_path=None):
        file_path = file_path or f"{user_id}.png"
        (TEST_STORAGE / "imgFiles" / file_path).write_bytes(b"image")
        with backend_api.connect_database() as conn:
            cursor = conn.execute(
                """
                INSERT INTO file_records (filename, filesize, file_path, user_id)
                VALUES (?, ?, ?, ?)
                """,
                ("cover.png", 0.01, file_path, user_id),
            )
            return cursor.lastrowid, file_path

    def test_initializes_isolated_storage_and_schema(self):
        for directory in ("db", "cookiesFiles", "imgFiles", "logs"):
            self.assertTrue((TEST_STORAGE / directory).is_dir())
        self.assertTrue((TEST_STORAGE / "db" / "database.db").is_file())

        with backend_api.connect_database() as conn:
            tables = {
                row[0]
                for row in conn.execute(
                    "SELECT name FROM sqlite_master WHERE type = 'table'"
                )
            }
            account_columns = {
                row[1] for row in conn.execute("PRAGMA table_info(user_info)")
            }
            file_columns = {
                row[1] for row in conn.execute("PRAGMA table_info(file_records)")
            }

        self.assertIn("user_info", tables)
        self.assertIn("file_records", tables)
        self.assertEqual(
            {"id", "type", "filePath", "userName", "status", "user_id"},
            account_columns,
        )
        self.assertEqual(
            {
                "id",
                "filename",
                "filesize",
                "upload_time",
                "file_path",
                "user_id",
            },
            file_columns,
        )

        account_id, _ = self.insert_account("preserved-user")
        backend_api.initialize_storage()
        with backend_api.connect_database() as conn:
            preserved = conn.execute(
                "SELECT id FROM user_info WHERE user_id = ?", ("preserved-user",)
            ).fetchone()
        self.assertEqual(account_id, preserved[0])

    def test_missing_internal_token_fails_service_startup(self):
        environment = os.environ.copy()
        environment.pop("PUBLISH_INTERNAL_TOKEN", None)
        process = subprocess.run(
            [sys.executable, "-c", "import backend_api"],
            cwd=SERVICE_ROOT,
            env=environment,
            capture_output=True,
            text=True,
            check=False,
        )
        self.assertNotEqual(0, process.returncode)
        self.assertIn("PUBLISH_INTERNAL_TOKEN", process.stderr)

    def test_all_routes_require_userid(self):
        internal_only = {
            "X-Internal-Token": os.environ["PUBLISH_INTERNAL_TOKEN"],
        }
        responses = [
            self.client.get("/getAccounts", headers=internal_only),
            self.client.post("/verifyAccount?id=1", headers=internal_only),
            self.client.delete("/deleteAccount?id=1", headers=internal_only),
            self.client.post("/uploadSave", headers=internal_only),
            self.client.get("/getFiles", headers=internal_only),
            self.client.delete("/deleteFile?id=1", headers=internal_only),
            self.client.post("/postArticle", json={}, headers=internal_only),
            self.client.get("/login?type=1&username=same", headers=internal_only),
        ]

        for response in responses:
            self.assertEqual(400, response.status_code)
            self.assertEqual(400, response.get_json()["code"])

    def test_missing_or_wrong_internal_token_is_forbidden(self):
        self.assertEqual(403, self.client.get("/getAccounts", headers={"userid": "user-a"}).status_code)
        self.assertEqual(
            403,
            self.client.get(
                "/getAccounts",
                headers={"userid": "user-a", "X-Internal-Token": "x" * 32},
            ).status_code,
        )

    def test_accounts_verify_and_delete_are_owner_scoped(self):
        account_a, _ = self.insert_account("user-a")
        account_b, _ = self.insert_account("user-b")

        response_a = self.client.get("/getAccounts", headers=self.headers("user-a"))
        response_b = self.client.get("/getAccounts", headers=self.headers("user-b"))
        self.assertEqual([account_a], [row["id"] for row in response_a.get_json()["data"]])
        self.assertEqual([account_b], [row["id"] for row in response_b.get_json()["data"]])

        check_cookie = AsyncMock(return_value=True)
        with patch.object(backend_api, "check_cookie", check_cookie):
            denied = self.client.post(
                f"/verifyAccount?id={account_a}", headers=self.headers("user-b")
            )
            self.assertEqual(404, denied.status_code)
            check_cookie.assert_not_awaited()

            allowed = self.client.post(
                f"/verifyAccount?id={account_a}", headers=self.headers("user-a")
            )
            self.assertEqual(200, allowed.status_code)
            check_cookie.assert_awaited_once()

        denied_delete = self.client.delete(
            f"/deleteAccount?id={account_a}", headers=self.headers("user-b")
        )
        self.assertEqual(404, denied_delete.status_code)

        allowed_delete = self.client.delete(
            f"/deleteAccount?id={account_a}", headers=self.headers("user-a")
        )
        self.assertEqual(200, allowed_delete.status_code)
        remaining = self.client.get(
            "/getAccounts", headers=self.headers("user-b")
        ).get_json()["data"]
        self.assertEqual([account_b], [row["id"] for row in remaining])

    def test_upload_list_and_delete_files_are_owner_scoped_and_use_lastrowid(self):
        upload_a = self.client.post(
            "/uploadSave",
            headers=self.headers("user-a"),
            data={"file": (io.BytesIO(b"a"), "same.png")},
            content_type="multipart/form-data",
        )
        upload_b = self.client.post(
            "/uploadSave",
            headers=self.headers("user-b"),
            data={"file": (io.BytesIO(b"b"), "same.png")},
            content_type="multipart/form-data",
        )
        self.assertEqual(200, upload_a.status_code)
        self.assertEqual(200, upload_b.status_code)

        data_a = upload_a.get_json()["data"]
        data_b = upload_b.get_json()["data"]
        self.assertNotEqual(data_a["id"], data_b["id"])

        files_a = self.client.get(
            "/getFiles", headers=self.headers("user-a")
        ).get_json()["data"]
        files_b = self.client.get(
            "/getFiles", headers=self.headers("user-b")
        ).get_json()["data"]
        self.assertEqual([data_a["id"]], [row["id"] for row in files_a])
        self.assertEqual([data_b["id"]], [row["id"] for row in files_b])

        denied = self.client.delete(
            f"/deleteFile?id={data_a['id']}", headers=self.headers("user-b")
        )
        self.assertEqual(404, denied.status_code)
        self.assertTrue((TEST_STORAGE / "imgFiles" / data_a["filepath"]).is_file())

        allowed = self.client.delete(
            f"/deleteFile?id={data_a['id']}", headers=self.headers("user-a")
        )
        self.assertEqual(200, allowed.status_code)
        self.assertFalse((TEST_STORAGE / "imgFiles" / data_a["filepath"]).exists())

    def test_internal_exceptions_are_logged_without_leaking_into_http_500_responses(self):
        cases = [
            (
                "get accounts",
                lambda: self.client.get(
                    "/getAccounts", headers=self.headers("user-a")
                ),
                "SQLITE: SELECT api_key FROM secrets",
                "获取账号列表失败",
            ),
            (
                "upload file",
                lambda: self.client.post(
                    "/uploadSave",
                    headers=self.headers("user-a"),
                    data={"file": (io.BytesIO(b"image"), "safe.png")},
                    content_type="multipart/form-data",
                ),
                "PATH: /srv/private/.env",
                "上传文件失败",
            ),
            (
                "get files",
                lambda: self.client.get("/getFiles", headers=self.headers("user-a")),
                "SECRET: sk-live-do-not-return",
                "获取文件失败",
            ),
            (
                "delete file",
                lambda: self.client.delete(
                    "/deleteFile?id=1", headers=self.headers("user-a")
                ),
                "HEADER:\nAuthorization: Bearer do-not-return",
                "文件删除失败",
            ),
        ]

        for name, make_request, secret, public_message in cases:
            with self.subTest(name=name), patch.object(
                backend_api, "connect_database", side_effect=RuntimeError(secret)
            ), patch.object(backend_api.app.logger, "error") as log_error:
                response = make_request()

            self.assertEqual(500, response.status_code)
            self.assertEqual(
                {"code": 500, "msg": public_message, "data": None},
                response.get_json(),
            )
            self.assertNotIn(secret, response.get_data(as_text=True))
            log_error.assert_called_once()
            self.assertNotIn(secret, repr(log_error.call_args))

    def test_publish_requires_owned_cookie_type_and_image(self):
        _, cookie_a = self.insert_account("user-a", platform_type=1)
        _, image_a = self.insert_file("user-a")
        _, image_b = self.insert_file("user-b")
        payload = {
            "type": 1,
            "title": "title",
            "content": "content",
            "img": image_a,
            "tags": [],
            "cookie_file": cookie_a,
            "text": "content",
        }

        with patch.object(backend_api, "post_artical_zhihu") as publish:
            wrong_user = self.client.post(
                "/postArticle", json=payload, headers=self.headers("user-b")
            )
            self.assertEqual(404, wrong_user.status_code)

            wrong_type = self.client.post(
                "/postArticle",
                json={**payload, "type": 2},
                headers=self.headers("user-a"),
            )
            self.assertEqual(404, wrong_type.status_code)

            missing_image = self.client.post(
                "/postArticle",
                json={**payload, "img": ""},
                headers=self.headers("user-a"),
            )
            self.assertEqual(400, missing_image.status_code)

            wrong_image = self.client.post(
                "/postArticle",
                json={**payload, "img": image_b},
                headers=self.headers("user-a"),
            )
            self.assertEqual(404, wrong_image.status_code)

            allowed = self.client.post(
                "/postArticle", json=payload, headers=self.headers("user-a")
            )
            self.assertEqual(200, allowed.status_code)
            publish.assert_called_once_with(
                "title", "content", image_a, [], cookie_a
            )

    def test_publish_rejects_owned_records_with_paths_outside_storage(self):
        with backend_api.connect_database() as conn:
            conn.execute(
                """
                INSERT INTO user_info (type, filePath, userName, status, user_id)
                VALUES (?, ?, ?, ?, ?)
                """,
                (1, "../outside.json", "account", 1, "user-a"),
            )
            conn.execute(
                """
                INSERT INTO file_records (filename, filesize, file_path, user_id)
                VALUES (?, ?, ?, ?)
                """,
                ("outside.png", 0.01, "../outside.png", "user-a"),
            )

        payload = {
            "type": 1,
            "title": "title",
            "content": "content",
            "cookie_file": "../outside.json",
        }
        with patch.object(backend_api, "post_artical_zhihu") as publish:
            bad_cookie = self.client.post(
                "/postArticle", json=payload, headers=self.headers("user-a")
            )
            self.assertEqual(404, bad_cookie.status_code)

            _, safe_cookie = self.insert_account("user-a", cookie_file="safe.json")
            bad_image = self.client.post(
                "/postArticle",
                json={
                    **payload,
                    "cookie_file": safe_cookie,
                    "img": "../outside.png",
                },
                headers=self.headers("user-a"),
            )
            self.assertEqual(404, bad_image.status_code)
            publish.assert_not_called()

    def test_delete_file_rejects_invalid_stored_path_without_deleting_row(self):
        with backend_api.connect_database() as conn:
            cursor = conn.execute(
                """
                INSERT INTO file_records (filename, filesize, file_path, user_id)
                VALUES (?, ?, ?, ?)
                """,
                ("outside.png", 0.01, "../outside.png", "user-a"),
            )
            file_id = cursor.lastrowid

        response = self.client.delete(
            f"/deleteFile?id={file_id}", headers=self.headers("user-a")
        )
        self.assertEqual(404, response.status_code)
        with backend_api.connect_database() as conn:
            remaining = conn.execute(
                "SELECT id FROM file_records WHERE id = ? AND user_id = ?",
                (file_id, "user-a"),
            ).fetchone()
        self.assertEqual(file_id, remaining[0])

    def test_login_queues_are_scoped_by_user_and_username(self):
        with patch.object(backend_api.threading, "Thread") as thread_class, patch.object(
            backend_api,
            "sse_stream",
            side_effect=lambda *_args, **_kwargs: iter(["data:test\n\n"]),
        ):
            response_a = self.client.get(
                "/login?type=1&username=same", headers=self.headers("user-a")
            )
            response_b = self.client.get(
                "/login?type=1&username=same", headers=self.headers("user-b")
            )

        self.assertEqual(200, response_a.status_code)
        self.assertEqual(200, response_b.status_code)
        with backend_api.active_queues_lock:
            self.assertIn(("user-a", "same"), backend_api.active_queues)
            self.assertIn(("user-b", "same"), backend_api.active_queues)
            self.assertEqual(2, len(backend_api.active_queues))
        self.assertEqual(2, thread_class.call_count)


def tearDownModule():
    from utils.log import logger

    logger.remove()
    shutil.rmtree(TEST_STORAGE, ignore_errors=True)


if __name__ == "__main__":
    unittest.main()

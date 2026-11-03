import ast
import contextlib
import importlib.util
import io
import sys
import types
import unittest
from pathlib import Path
from unittest.mock import patch


SERVICE_ROOT = Path(__file__).resolve().parents[1]
sys.path.insert(0, str(SERVICE_ROOT))

LOGIN_FILES = tuple(
    SERVICE_ROOT / "login" / filename
    for filename in ("baijiahao.py", "jinritoutiao.py", "xiaohongshu.py", "zhihu.py")
)
PUBLISH_ENTRY_FILES = tuple(
    SERVICE_ROOT / "postArtical" / platform / f"{platform}.py"
    for platform in ("baijiahao", "jinritoutiao", "xiaohongshu", "zhihu")
)
UPLOADER_FILES = tuple(
    SERVICE_ROOT / "postArtical" / platform / "main_uploader.py"
    for platform in ("baijiahao", "jinritoutiao", "xiaohongshu", "zhihu")
)
BACKEND_API_FILE = SERVICE_ROOT / "backend_api.py"
LOG_METHODS = {
    "trace",
    "debug",
    "info",
    "success",
    "warning",
    "warn",
    "error",
    "exception",
    "critical",
}
SAFE_METADATA_TRANSFORMS = {"bool", "len", "type"}


def parse_source(path: Path) -> ast.AST:
    return ast.parse(path.read_text(encoding="utf-8"), filename=str(path))


def is_output_sink(call: ast.Call) -> bool:
    return (
        isinstance(call.func, ast.Name)
        and call.func.id == "print"
        or isinstance(call.func, ast.Attribute)
        and call.func.attr in LOG_METHODS
    )


def unsafe_references(
    node: ast.AST,
    *,
    sensitive_names: set[str],
    sensitive_self_attributes: set[str] | None = None,
) -> list[str]:
    sensitive_self_attributes = sensitive_self_attributes or set()

    if (
        isinstance(node, ast.Call)
        and isinstance(node.func, ast.Name)
        and node.func.id in SAFE_METADATA_TRANSFORMS
    ):
        return []
    if isinstance(node, ast.Name) and node.id in sensitive_names:
        return [node.id]
    if (
        isinstance(node, ast.Attribute)
        and isinstance(node.value, ast.Name)
        and node.value.id == "self"
        and node.attr in sensitive_self_attributes
    ):
        return [f"self.{node.attr}"]

    references = []
    for child in ast.iter_child_nodes(node):
        references.extend(
            unsafe_references(
                child,
                sensitive_names=sensitive_names,
                sensitive_self_attributes=sensitive_self_attributes,
            )
        )
    return references


def assert_sinks_are_redacted(
    test_case: unittest.TestCase,
    path: Path,
    *,
    sensitive_names: set[str],
    sensitive_self_attributes: set[str] | None = None,
) -> None:
    tree = parse_source(path)
    for call in (node for node in ast.walk(tree) if isinstance(node, ast.Call)):
        if not is_output_sink(call):
            continue
        leaked = []
        for argument in (*call.args, *(keyword.value for keyword in call.keywords)):
            leaked.extend(
                unsafe_references(
                    argument,
                    sensitive_names=sensitive_names,
                    sensitive_self_attributes=sensitive_self_attributes,
                )
            )
        test_case.assertEqual(
            [],
            leaked,
            f"{path.relative_to(SERVICE_ROOT)}:{call.lineno} logs sensitive values: {leaked}",
        )


class SensitiveLoggingContractTest(unittest.TestCase):
    def test_sse_queue_logs_do_not_emit_user_or_account_keys(self):
        assert_sinks_are_redacted(
            self,
            BACKEND_API_FILE,
            sensitive_names={"queue_key", "username"},
        )

    def test_login_logs_do_not_emit_qr_payload_or_cookie_state_id(self):
        for path in LOGIN_FILES:
            with self.subTest(path=path.name):
                assert_sinks_are_redacted(
                    self,
                    path,
                    sensitive_names={"src", "uuid_v1"},
                )

    def test_publish_entry_logs_only_safe_metadata(self):
        for path in PUBLISH_ENTRY_FILES:
            with self.subTest(path=path.parent.name):
                assert_sinks_are_redacted(
                    self,
                    path,
                    sensitive_names={
                        "content",
                        "cookie_file",
                        "img",
                        "tags",
                        "text",
                        "title",
                    },
                )

    def test_uploaders_do_not_emit_paths_content_or_raw_exceptions(self):
        sensitive_self_attributes = {
            "content",
            "cookie_file",
            "img",
            "tags",
            "text",
            "title",
        }
        for path in UPLOADER_FILES:
            tree = parse_source(path)
            exception_names = {
                handler.name
                for handler in ast.walk(tree)
                if isinstance(handler, ast.ExceptHandler) and handler.name
            }
            with self.subTest(path=path.parent.name):
                assert_sinks_are_redacted(
                    self,
                    path,
                    sensitive_names=exception_names,
                    sensitive_self_attributes=sensitive_self_attributes,
                )

    def test_loguru_handlers_disable_variable_diagnostics(self):
        path = SERVICE_ROOT / "utils" / "log.py"
        tree = parse_source(path)
        add_calls = [
            node
            for node in ast.walk(tree)
            if isinstance(node, ast.Call)
            and isinstance(node.func, ast.Attribute)
            and isinstance(node.func.value, ast.Name)
            and node.func.value.id == "logger"
            and node.func.attr == "add"
        ]
        self.assertEqual(2, len(add_calls))

        for call in add_calls:
            keywords = {keyword.arg: keyword.value for keyword in call.keywords}
            for setting in ("backtrace", "diagnose"):
                self.assertIn(setting, keywords, f"logger.add at line {call.lineno}")
                self.assertIsInstance(keywords[setting], ast.Constant)
                self.assertIs(
                    False,
                    keywords[setting].value,
                    f"logger.add at line {call.lineno} must set {setting}=False",
                )

    def test_publish_entries_preserve_arguments_without_printing_secrets(self):
        title = "private-medical-title-7b6a"
        content = "private-medical-content-4d91"
        text = "private-social-copy-e2c8"
        image = "private-cover-a95f.png"
        tags = ["private-topic-1f03"]
        cookie_file = "private-cookie-state-839d.json"
        cases = (
            (
                "baijiahao",
                "BJHArtical",
                "post_artical_baijiahao",
                (title, content, image, tags, cookie_file),
            ),
            (
                "jinritoutiao",
                "JRTTArtical",
                "post_artical_jinritoutiao",
                (title, content, image, tags, cookie_file),
            ),
            (
                "xiaohongshu",
                "XHSArtical",
                "post_artical_xiaohongshu",
                (title, content, text, tags, cookie_file),
            ),
            (
                "zhihu",
                "ZHArtical",
                "post_artical_zhihu",
                (title, content, image, tags, cookie_file),
            ),
        )

        for platform, class_name, function_name, arguments in cases:
            captured = {}

            class FakeUploader:
                def __init__(self, *uploader_arguments):
                    captured["arguments"] = uploader_arguments

                async def main(self):
                    captured["ran"] = True

            fake_uploader_module = types.ModuleType(
                f"postArtical.{platform}.main_uploader"
            )
            setattr(fake_uploader_module, class_name, FakeUploader)
            entry_path = SERVICE_ROOT / "postArtical" / platform / f"{platform}.py"
            spec = importlib.util.spec_from_file_location(
                f"logging_contract_{platform}", entry_path
            )
            self.assertIsNotNone(spec)
            self.assertIsNotNone(spec.loader)
            module = importlib.util.module_from_spec(spec)

            output = io.StringIO()
            with self.subTest(platform=platform), patch.dict(
                sys.modules,
                {f"postArtical.{platform}.main_uploader": fake_uploader_module},
            ), contextlib.redirect_stdout(output):
                spec.loader.exec_module(module)
                getattr(module, function_name)(*arguments)

            emitted = output.getvalue()
            for secret in (title, content, text, image, *tags, cookie_file):
                self.assertNotIn(secret, emitted)
            self.assertIn(f"platform={platform}", emitted)
            self.assertTrue(captured.get("ran"))
            self.assertEqual(title, captured["arguments"][0])
            self.assertEqual(content, captured["arguments"][1])
            self.assertEqual(tags, captured["arguments"][-2])
            self.assertTrue(str(captured["arguments"][-1]).endswith(cookie_file))


if __name__ == "__main__":
    unittest.main()

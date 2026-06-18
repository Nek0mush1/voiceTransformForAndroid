import json
import os
import unittest
from io import BytesIO
from pathlib import Path
from unittest.mock import patch
from urllib.error import HTTPError


class LLMWireApiTest(unittest.TestCase):
    def setUp(self):
        db_path = Path.cwd().parent / ".test-data" / f"{self.id().split('.')[-1]}.db"
        db_path.parent.mkdir(parents=True, exist_ok=True)
        if db_path.exists():
            db_path.unlink()
        os.environ["VOICE_TRANSFORM_DB"] = str(db_path)

    def test_responses_wire_api_uses_responses_endpoint(self):
        from app.services.llm_rewrite import LLMRewriteTool

        captured = {}

        class FakeResponse:
            def __enter__(self):
                return self

            def __exit__(self, exc_type, exc, traceback):
                return False

            def read(self):
                return b'{"output_text":"LLM config works"}'

        def fake_urlopen(request, timeout):
            captured["url"] = request.full_url
            captured["body"] = json.loads(request.data.decode("utf-8"))
            captured["authorization"] = request.headers["Authorization"]
            captured["user_agent"] = request.headers["User-agent"]
            captured["timeout"] = timeout
            return FakeResponse()

        with patch("urllib.request.urlopen", fake_urlopen):
            result = LLMRewriteTool().call_model(
                base_url="https://www.micuapi.ai/v1",
                api_key="test-key",
                model="gpt-5.5",
                wire_api="responses",
                messages=[
                    {"role": "system", "content": "Return a health check."},
                    {"role": "user", "content": "Say: LLM config works"},
                ],
                timeout=15,
            )

        self.assertTrue(result.success)
        self.assertEqual(result.text, "LLM config works")
        self.assertEqual(captured["url"], "https://www.micuapi.ai/v1/responses")
        self.assertEqual(captured["authorization"], "Bearer test-key")
        self.assertIn("Mozilla/5.0", captured["user_agent"])
        self.assertEqual(captured["timeout"], 15)
        self.assertEqual(captured["body"]["model"], "gpt-5.5")
        self.assertEqual(captured["body"]["instructions"], "Return a health check.")
        self.assertEqual(captured["body"]["input"], "user: Say: LLM config works")

    def test_chat_wire_api_uses_chat_completions_endpoint(self):
        from app.services.llm_rewrite import LLMRewriteTool

        captured = {}

        class FakeResponse:
            def __enter__(self):
                return self

            def __exit__(self, exc_type, exc, traceback):
                return False

            def read(self):
                return b'{"choices":[{"message":{"content":"ok"}}]}'

        def fake_urlopen(request, timeout):
            captured["url"] = request.full_url
            captured["body"] = json.loads(request.data.decode("utf-8"))
            return FakeResponse()

        with patch("urllib.request.urlopen", fake_urlopen):
            result = LLMRewriteTool().call_model(
                base_url="https://api.example.com/v1",
                api_key="test-key",
                model="example-chat",
                wire_api="chat_completions",
                messages=[{"role": "user", "content": "ping"}],
            )

        self.assertTrue(result.success)
        self.assertEqual(result.text, "ok")
        self.assertEqual(captured["url"], "https://api.example.com/v1/chat/completions")
        self.assertEqual(captured["body"]["messages"], [{"role": "user", "content": "ping"}])

    def test_http_error_returns_response_body(self):
        from app.services.llm_rewrite import LLMRewriteTool

        def fake_urlopen(request, timeout):
            raise HTTPError(
                request.full_url,
                403,
                "Forbidden",
                {},
                BytesIO(b'{"error":{"message":"model not allowed"}}'),
            )

        with patch("urllib.request.urlopen", fake_urlopen):
            result = LLMRewriteTool().call_model(
                base_url="https://www.micuapi.ai/v1",
                api_key="test-key",
                model="gpt-5.5",
                wire_api="responses",
                messages=[{"role": "user", "content": "ping"}],
            )

        self.assertFalse(result.success)
        self.assertIn("HTTP 403 Forbidden", result.error)
        self.assertIn("model not allowed", result.error)


if __name__ == "__main__":
    unittest.main()

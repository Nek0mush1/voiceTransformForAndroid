import importlib
import os
import unittest
from pathlib import Path
from unittest.mock import patch


class CorrectionFlowTest(unittest.TestCase):
    def setUp(self):
        db_name = f"{self.id().split('.')[-1]}.db"
        db_path = Path.cwd().parent / ".test-data" / db_name
        db_path.parent.mkdir(parents=True, exist_ok=True)
        if db_path.exists():
            db_path.unlink()

        os.environ["VOICE_TRANSFORM_DB"] = str(db_path)
        os.environ.pop("LLM_BASE_URL", None)
        os.environ.pop("LLM_API_KEY", None)
        os.environ.pop("LLM_MODEL", None)

        import app.storage as storage
        import app.services.llm_rewrite as llm_rewrite
        import app.services.context_agent as context_agent
        import app.services.text_corrector as text_corrector

        self.storage = importlib.reload(storage)
        importlib.reload(llm_rewrite)
        importlib.reload(context_agent)
        self.text_corrector = importlib.reload(text_corrector)
        self.storage.init_db()

    def correct(self, raw_text, user_id="local_user", app_context="study"):
        from app.schemas.text_correction import TextCorrectionRequest

        return self.text_corrector.correct_text(
            TextCorrectionRequest(
                user_id=user_id,
                raw_text=raw_text,
                app_context=app_context,
            )
        )

    def test_default_term_corrects_jizu_course(self):
        payload = self.correct("今天上午上了两节祭祖课")

        self.assertEqual(payload.corrected_text, "今天上午上了两节计组课")
        self.assertEqual(payload.matched_terms, ["计组"])
        self.assertFalse(payload.agent_trace["llm_success"])
        self.assertTrue(payload.trace_id)

    def test_regular_course_is_not_over_corrected(self):
        payload = self.correct("今天上午上了两节基础课")

        self.assertEqual(payload.corrected_text, "今天上午上了两节基础课")
        self.assertEqual(payload.matched_terms, [])

    def test_added_term_participates_in_later_correction(self):
        self.storage.create_term(
            user_id="local_user",
            term="线程",
            category="system",
            aliases=["现金"],
            weight=1.0,
        )

        payload = self.correct("老师讲了现金调度")

        self.assertEqual(payload.corrected_text, "老师讲了线程调度")
        self.assertEqual(payload.matched_terms, ["线程"])

    def test_llm_fallback_preserves_service_when_unconfigured(self):
        payload = self.correct("下节课是计网实验")

        self.assertEqual(payload.corrected_text, "下节课是计网实验")
        self.assertFalse(payload.agent_trace["llm_success"])
        self.assertIn("LLM", payload.reason)

    def test_profile_terms_and_trace_storage(self):
        profile = self.storage.update_profile(
            "local_user",
            "计算机专业学生，正在学习计组、Cache 和 Agent 开发。",
        )
        self.assertIn("Cache", profile.profile_text)
        self.assertTrue(any(term.term == "计组" for term in self.storage.list_terms("local_user")))

        self.correct("老师讲了 cash 命中率")
        traces = self.storage.list_traces(limit=5)

        self.assertTrue(traces)
        self.assertEqual(traces[0].raw_text, "老师讲了 cash 命中率")
        self.assertEqual(traces[0].tools[0]["name"], "MemoryTool")


    def test_unconfigured_llm_sets_method_and_logs_call(self):
        payload = self.correct("plain text", user_id="plain_user")

        self.assertEqual(payload.corrected_text, "plain text")
        self.assertFalse(payload.llm_used)
        self.assertEqual(payload.correction_method, "raw_text")
        self.assertEqual(payload.llm_error, "LLM not configured")
        logs = self.storage.list_llm_call_logs()
        self.assertEqual(len(logs), 1)
        self.assertFalse(logs[0].success)
        self.assertEqual(logs[0].trace_id, payload.trace_id)
        self.assertEqual(logs[0].correction_method, "raw_text")

    def test_llm_success_sets_method_and_logs_call(self):
        self.storage.update_llm_config(
            base_url="https://api.example.com/v1",
            api_key="test-key",
            model="example-model",
            wire_api="responses",
        )

        def fake_call_model(self, *args, **kwargs):
            from app.services.llm_rewrite import LLMResult

            return LLMResult(text="LLM fixed text", success=True)

        with patch("app.services.llm_rewrite.LLMRewriteTool.call_model", fake_call_model):
            payload = self.correct("raw typo", user_id="plain_user")

        self.assertEqual(payload.corrected_text, "LLM fixed text")
        self.assertTrue(payload.llm_used)
        self.assertEqual(payload.correction_method, "llm")
        logs = self.storage.list_llm_call_logs()
        self.assertEqual(len(logs), 1)
        self.assertTrue(logs[0].success)
        self.assertEqual(logs[0].trace_id, payload.trace_id)
        self.assertEqual(logs[0].output_text, "LLM fixed text")
        self.assertEqual(logs[0].model, "example-model")
        self.assertEqual(logs[0].wire_api, "responses")

    def test_llm_call_logs_keep_latest_50(self):
        for index in range(55):
            self.storage.save_llm_call_log(
                trace_id=f"trace-{index}",
                user_id="plain_user",
                raw_text=f"raw {index}",
                fallback_text=f"fallback {index}",
                output_text=f"output {index}",
                success=True,
                error="",
                correction_method="llm",
                base_url="https://api.example.com/v1",
                model="example-model",
                wire_api="responses",
                duration_ms=index,
            )

        logs = self.storage.list_llm_call_logs(limit=50)
        self.assertEqual(len(logs), 50)
        self.assertEqual(logs[0].trace_id, "trace-54")
        self.assertEqual(logs[-1].trace_id, "trace-5")


if __name__ == "__main__":
    unittest.main()

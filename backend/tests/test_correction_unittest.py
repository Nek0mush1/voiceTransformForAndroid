import importlib
import os
import unittest
from pathlib import Path


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


if __name__ == "__main__":
    unittest.main()

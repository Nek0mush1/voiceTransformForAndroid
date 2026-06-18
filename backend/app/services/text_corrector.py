from dataclasses import dataclass

from app.schemas.text_correction import TextCorrectionRequest, TextCorrectionResponse


DEFAULT_REASON = "未命中用户专业词库，保留原文。"
MATCHED_REASON = "根据用户专业词库和同音匹配修正。"


@dataclass(frozen=True)
class CorrectionRule:
    wrong_text: str
    corrected_text: str
    matched_term: str
    contexts: frozenset[str] | None = None

    def applies_to(self, app_context: str) -> bool:
        return self.contexts is None or app_context in self.contexts


RULES: tuple[CorrectionRule, ...] = (
    CorrectionRule(
        wrong_text="祭祖课",
        corrected_text="计组课",
        matched_term="计组",
        contexts=frozenset({"chat", "note", "study"}),
    ),
)


def correct_text(payload: TextCorrectionRequest) -> TextCorrectionResponse:
    corrected_text = payload.raw_text
    matched_terms: list[str] = []

    for rule in RULES:
        if rule.applies_to(payload.app_context) and rule.wrong_text in corrected_text:
            corrected_text = corrected_text.replace(rule.wrong_text, rule.corrected_text)
            matched_terms.append(rule.matched_term)

    return TextCorrectionResponse(
        raw_text=payload.raw_text,
        corrected_text=corrected_text,
        matched_terms=matched_terms,
        reason=MATCHED_REASON if matched_terms else DEFAULT_REASON,
    )

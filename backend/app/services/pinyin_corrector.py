from __future__ import annotations

from dataclasses import dataclass

from app.storage import TermRecord

try:
    from pypinyin import Style, lazy_pinyin
except ImportError:  # pragma: no cover - exercised only when dependency is missing.
    Style = None
    lazy_pinyin = None


PINYIN_ALIASES: dict[str, set[str]] = {
    "计组": {"祭祖", "祭祖课", "计组课"},
    "计网": {"鸡王", "计网课"},
    "操作系统": {"炒作系统"},
    "数据结构": {"数据解构"},
    "数据库": {"书局库", "数据哭"},
    "Agent": {"agent", "真特", "智能体"},
    "RAG": {"rag", "拉格"},
    "Cache": {"cache", "cash", "快取"},
    "Transformer": {"transformer"},
}


FALLBACK_CHAR_PINYIN: dict[str, str] = {
    "计": "ji",
    "祭": "ji",
    "鸡": "ji",
    "组": "zu",
    "祖": "zu",
    "课": "ke",
    "网": "wang",
    "王": "wang",
    "操": "cao",
    "作": "zuo",
    "炒": "chao",
    "系": "xi",
    "统": "tong",
    "数": "shu",
    "书": "shu",
    "竖": "shu",
    "据": "ju",
    "局": "ju",
    "锯": "ju",
    "结": "jie",
    "解": "jie",
    "构": "gou",
    "库": "ku",
    "哭": "ku",
    "苦": "ku",
    "线": "xian",
    "现": "xian",
    "程": "cheng",
    "金": "jin",
    "快": "kuai",
    "取": "qu",
    "智": "zhi",
    "能": "neng",
    "体": "ti",
}


@dataclass(frozen=True)
class PinyinCandidate:
    source_text: str
    target_term: str
    replacement_text: str
    score: float
    reason: str

    def to_dict(self) -> dict[str, str | float]:
        return {
            "source_text": self.source_text,
            "target_term": self.target_term,
            "replacement_text": self.replacement_text,
            "score": self.score,
            "reason": self.reason,
        }


class PinyinCorrectorTool:
    def suggest(self, raw_text: str, terms: list[TermRecord]) -> list[PinyinCandidate]:
        candidates: list[PinyinCandidate] = []
        lowered_text = raw_text.lower()

        for term in terms:
            sources = set(term.aliases)
            sources.update(PINYIN_ALIASES.get(term.term, set()))
            sources.discard(term.term)

            for source in sources:
                if not source:
                    continue
                if source in raw_text or source.lower() in lowered_text:
                    candidates.append(
                        PinyinCandidate(
                            source_text=source,
                            target_term=term.term,
                            replacement_text=self._replacement_for(source, term.term),
                            score=min(1.0, 0.60 + (term.weight * 0.35)),
                            reason="命中用户词库别名或内置同音候选",
                        )
                    )

            candidates.extend(self._suggest_by_pinyin(raw_text, term))

        return self._dedupe(candidates)

    def apply(self, raw_text: str, candidates: list[PinyinCandidate]) -> tuple[str, list[str]]:
        corrected_text = raw_text
        matched_terms: list[str] = []

        for candidate in sorted(candidates, key=lambda item: len(item.source_text), reverse=True):
            if candidate.score < 0.75:
                continue

            before = corrected_text
            corrected_text = corrected_text.replace(candidate.source_text, candidate.replacement_text)
            corrected_text = corrected_text.replace(
                candidate.source_text.lower(),
                candidate.replacement_text,
            )
            if corrected_text != before:
                matched_terms.append(candidate.target_term)

        return corrected_text, list(dict.fromkeys(matched_terms))

    def _suggest_by_pinyin(self, raw_text: str, term: TermRecord) -> list[PinyinCandidate]:
        candidates: list[PinyinCandidate] = []
        target_variants = [term.term]
        if not term.term.endswith("课"):
            target_variants.append(f"{term.term}课")

        for target in target_variants:
            target_pinyin = self._text_pinyin(target)
            if not target_pinyin:
                continue
            size = len(target)
            for index in range(0, len(raw_text) - size + 1):
                source = raw_text[index : index + size]
                if source == target:
                    continue
                source_pinyin = self._text_pinyin(source)
                if source_pinyin and source_pinyin == target_pinyin:
                    candidates.append(
                        PinyinCandidate(
                            source_text=source,
                            target_term=term.term,
                            replacement_text=target,
                            score=min(1.0, 0.62 + (term.weight * 0.33)),
                            reason="短语拼音完全一致，命中用户专业词库",
                        )
                    )
        return candidates

    def _text_pinyin(self, text: str) -> tuple[str, ...]:
        if lazy_pinyin is not None and Style is not None:
            return tuple(
                lazy_pinyin(
                    text,
                    style=Style.NORMAL,
                    errors=lambda item: [char.lower() for char in item],
                    strict=False,
                )
            )

        syllables: list[str] = []
        for char in text:
            if char.isascii():
                syllables.append(char.lower())
                continue
            pinyin = FALLBACK_CHAR_PINYIN.get(char)
            if not pinyin:
                return ()
            syllables.append(pinyin)
        return tuple(syllables)

    def _replacement_for(self, source: str, target_term: str) -> str:
        if source.endswith("课") and not target_term.endswith("课"):
            return f"{target_term}课"
        return target_term

    def _dedupe(self, candidates: list[PinyinCandidate]) -> list[PinyinCandidate]:
        seen: set[tuple[str, str, str]] = set()
        unique: list[PinyinCandidate] = []
        for candidate in candidates:
            key = (candidate.source_text, candidate.target_term, candidate.replacement_text)
            if key in seen:
                continue
            seen.add(key)
            unique.append(candidate)
        return unique

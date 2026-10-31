"""Stage 1: fetch an article and give its source passages stable positions.

No model or search API is called here. Offsets are zero-based Python string
indices in ``body``; ``end`` is exclusive. The extracted body is not rewritten.
"""

from __future__ import annotations

import re
from typing import Any

from .article_fetch import ArticleFetchError, fetch_article


# An extracted "paragraph" spanning dozens of pages with very sparse raw
# punctuation is usually page markup or boilerplate, not article text. Count
# punctuation before sentence segmentation: quoted interview sentences may
# legitimately be protected from splitting but must not be rejected here.
_IMPLAUSIBLE_SINGLE_PARAGRAPH_CHARS = 32_000
_IMPLAUSIBLE_CHARS_PER_TERMINATOR = 1_000


_PROTECTED_SPANS = re.compile(
    r'“[^”]*”|‘[^’]*’|"[^"]*"|(?<!\w)\x27[^\x27]*\x27(?!\w)|\([^)]*\)|（[^）]*）'
)
_TERMINATORS = re.compile(r"[.!?。！？]+")
_ABBREVIATIONS = {"mr.", "mrs.", "ms.", "dr.", "prof.", "e.g.", "i.e.", "vs."}


def _trim_span(body: str, start: int, end: int) -> tuple[int, int]:
    while start < end and body[start].isspace():
        start += 1
    while end > start and body[end - 1].isspace():
        end -= 1
    return start, end


def _is_sentence_end(text: str, match: re.Match[str]) -> bool:
    """Use conservative boundaries around decimals, URLs and abbreviations."""
    end = match.end()
    if end < len(text) and not text[end].isspace():
        # Korean copy often drops the space after a sentence-final ending:
        # '…아니다.실제로'. Decimals and URLs never follow '다/요/죠'.
        return (match.group() == "." and match.start() > 0 and text[match.start() - 1] in "다요죠"
                and (text[end] in "“‘" or "가" <= text[end] <= "힣"))
    if match.group() != ".":
        return match.group() not in ("..", "...")
    token = re.search(r"\S+$", text[:end])
    word = token.group() if token else ""
    if word.lower() in _ABBREVIATIONS or re.fullmatch(r"(?:[A-Za-z]\.){2,}", word):
        return False
    if word.startswith(("https://", "http://", "www.")):
        return False
    if re.fullmatch(r"\d+\.", text[:end].strip()):
        return False  # An initial list number, such as '1. ...'.
    return True


def structure_article_body(body: str) -> dict[str, list[dict[str, Any]]]:
    """Number paragraphs and sentences without dropping or changing source text.

    Quoted passages and parentheses stay with their surrounding attribution.
    A paragraph without final punctuation still receives a sentence number.
    This is rule-based segmentation, not a complete Korean sentence parser.
    """
    if not isinstance(body, str) or not body.strip():
        raise ValueError("Article text must be a non-empty string")

    paragraphs = []
    sentences = []
    for line in re.finditer(r"[^\r\n]+", body):
        start, end = _trim_span(body, line.start(), line.end())
        if start == end:
            continue
        text = body[start:end]
        paragraph_index = len(paragraphs)
        paragraphs.append({
            "paragraph_index": paragraph_index, "text": text,
            "start": start, "end": end,
        })
        protected = [(span.start(), span.end()) for span in _PROTECTED_SPANS.finditer(text)]
        boundaries = [
            match.end() for match in _TERMINATORS.finditer(text)
            if not any(left <= match.start() < right for left, right in protected)
            and _is_sentence_end(text, match)
        ]
        if not boundaries or boundaries[-1] != len(text):
            boundaries.append(len(text))
        previous = 0
        for boundary in boundaries:
            sentence_start, sentence_end = _trim_span(body, start + previous, start + boundary)
            previous = boundary
            if sentence_start == sentence_end:
                continue
            sentences.append({
                "sentence_index": len(sentences),
                "paragraph_index": paragraph_index,
                "text": body[sentence_start:sentence_end],
                "start": sentence_start,
                "end": sentence_end,
            })
    return {"paragraphs": paragraphs, "sentences": sentences}


def prepare_article(url: str) -> dict[str, Any]:
    """Collect one article and prepare the numbered input for later LLM stages."""
    article = fetch_article(url)
    body_chars = len(article["body"])
    # This uses the same newline rule as structure_article_body() and rejects
    # only when even the raw punctuation is too sparse for a plausible story.
    if (body_chars > _IMPLAUSIBLE_SINGLE_PARAGRAPH_CHARS
            and sum(bool(line.group().strip()) for line in re.finditer(
                r"[^\r\n]+", article["body"])) == 1
            and body_chars > _IMPLAUSIBLE_CHARS_PER_TERMINATOR * (
                1 + sum(1 for _ in _TERMINATORS.finditer(article["body"])))):
        raise ArticleFetchError("기사 본문 추출 결과에 비정상적으로 긴 단일 문단이 있습니다.",
                                stage="extraction_quality")
    return {**article, **structure_article_body(article["body"])}

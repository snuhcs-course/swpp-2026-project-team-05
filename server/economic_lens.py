"""Economic lens for economy articles: validation of reviewed dataset rows and the reader projection.

An economy article may carry `economic_context` (which economic channels it puts first, its tone per channel, the
sentences that show it, who it quotes, the numbers it picks) and its cluster may carry `economic_comparison` (where the
cluster's articles differ). Both are produced offline by the economic pipeline and reviewed before import; the API
never computes them. Contract: specs/features/economic-lens.md.
"""

from __future__ import annotations

from .models import Article, ArticleVisibility

CHANNELS = frozenset({
    "fx", "capital_market", "household_credit", "housing", "macro_growth", "prices", "trade", "corporate", "fiscal",
    "labor",
})
TONES = frozenset({"CONCERN", "OPTIMISM", "LIMITED", "OBSERVED"})
ROLES = frozenset({"EFFECT_REALIZED", "EFFECT_FORECAST"})
VOICES = frozenset({"QUOTE", "UNATTRIBUTED", "REPORTER"})
SOURCES = frozenset({"BANK_OF_KOREA", "GOVERNMENT", "FOREIGN_AUTHORITY", "EXPERT", "INDUSTRY", "ANONYMOUS"})
REASONS = frozenset({"FOCUS", "FOCUS_TONE", "TONE_CONTRAST"})
NUMBER_KINDS = frozenset({"LEVEL", "CHANGE"})
COMPARISON_TYPES = frozenset({"FOCUS", "TONE", "SOURCES", "NUMBERS"})
SCOPES = frozenset({"TITLE", "BODY"})


class EconomicLensError(ValueError):
    """An economic lens row that does not match its contract or its article text."""


def _check(condition: bool, context: str, message: str) -> None:
    if not condition:
        raise EconomicLensError(f"{context}: {message}")


def _channels(value, context: str) -> list[str]:
    _check(isinstance(value, list) and all(c in CHANNELS for c in value) and len(value) == len(set(value)),
           context, "channels must be distinct known channel codes")
    return list(value)


def _sentence(value, *, title: str, body: str, context: str) -> dict:
    _check(isinstance(value, dict), context, "sentence must be an object")
    scope, start, end, text = value.get("scope"), value.get("start"), value.get("end"), value.get("text")
    _check(scope in SCOPES, context, "sentence scope must be TITLE or BODY")
    _check(isinstance(start, int) and isinstance(end, int) and isinstance(text, str), context,
           "sentence needs integer start / end and its text")
    selected = title if scope == "TITLE" else body
    _check(0 <= start < end <= len(selected) and selected[start:end] == text, context,
           "sentence text does not match its offsets")
    return {"scope": scope, "start": start, "end": end, "text": text}


def _counts(value, context: str) -> dict:
    _check(isinstance(value, dict) and all(k in SOURCES and isinstance(n, int) and n >= 0 for k, n in value.items()),
           context, "source counts must map known source codes to counts")
    return dict(value)


def _numbers(value, context: str) -> list[dict]:
    _check(isinstance(value, list), context, "numbers must be a list")
    out = []
    for n, item in enumerate(value):
        c = f"{context} number {n}"
        _check(isinstance(item, dict) and isinstance(item.get("value"), str) and item["value"], c, "value is required")
        _check(item.get("kind") in NUMBER_KINDS, c, "kind must be LEVEL or CHANGE")
        basis = item.get("basis", [])
        _check(isinstance(basis, list) and all(isinstance(b, str) for b in basis), c, "basis must be a list of words")
        indicator = item.get("indicator")
        _check(indicator is None or isinstance(indicator, str), c, "indicator must be text")
        out.append({"indicator": indicator, "value": item["value"], "basis": basis, "kind": item["kind"]})
    return out


def validate_context(value, *, title: str, body: str, context: str) -> dict | None:
    """The reviewed `economic_context` of one article, checked against its title and body."""
    if value is None:
        return None
    _check(isinstance(value, dict), context, "economic_context must be an object")
    focus = _channels(value.get("focus", []), f"{context} focus")
    tone = value.get("tone", {})
    _check(isinstance(tone, dict) and all(c in CHANNELS and t in TONES for c, t in tone.items()), context,
           "tone must map channel codes to tones")
    evidence = []
    for n, item in enumerate(value.get("evidence", [])):
        c = f"{context} evidence {n}"
        _check(isinstance(item, dict), c, "evidence must be an object")
        channels = _channels(item.get("channels"), c)
        _check(bool(channels), c, "evidence needs a channel")
        _check(item.get("role") in ROLES, c, "role must be EFFECT_REALIZED or EFFECT_FORECAST")
        _check(item.get("tone") in TONES, c, "unknown tone")
        _check(item.get("voice") in VOICES, c, "unknown voice")
        _check(item.get("source") is None or item.get("source") in SOURCES, c, "unknown source")
        reasons = item.get("reasons", [])
        _check(isinstance(reasons, list) and bool(reasons) and set(reasons) <= REASONS, c, "unknown reasons")
        evidence.append({"sentence": _sentence(item.get("sentence"), title=title, body=body, context=c),
                         "channels": channels, "role": item["role"], "tone": item["tone"], "voice": item["voice"],
                         "source": item.get("source"), "reasons": list(reasons),
                         "decided_by": item.get("decided_by") if isinstance(item.get("decided_by"), dict) else {}})
    return {"focus": focus, "tone": dict(tone), "evidence": evidence,
            "sources": _counts(value.get("sources", {}), f"{context} sources"),
            "numbers": _numbers(value.get("numbers", []), f"{context} numbers")}


def validate_comparison(value, *, article_by_key: dict[str, Article], context: str) -> list[dict] | None:
    """The reviewed `economic_comparison` of one cluster; each side's article key becomes the stored article id."""
    if value is None:
        return None
    _check(isinstance(value, list), context, "economic_comparison must be a list")
    out = []
    for n, item in enumerate(value):
        c = f"{context} economic comparison {n}"
        _check(isinstance(item, dict) and item.get("type") in COMPARISON_TYPES, c, "unknown comparison type")
        kind = item["type"]
        row = {"type": kind}
        if kind == "TONE":
            _check(item.get("channel") in CHANNELS, c, "tone comparison needs a channel")
            row["channel"] = item["channel"]
        if kind == "NUMBERS":
            _check(isinstance(item.get("indicator"), str) and item["indicator"], c, "numbers comparison needs an indicator")
            row["indicator"] = item["indicator"]
        sides = []
        for m, side in enumerate(item.get("sides", [])):
            sc = f"{c} side {m}"
            _check(isinstance(side, dict) and side.get("article") in article_by_key, sc, "unknown article")
            article = article_by_key[side["article"]]
            stored = {"article_id": str(article.id)}
            if kind == "FOCUS":
                stored["channels"] = _channels(side.get("channels"), sc)
            elif kind == "TONE":
                _check(side.get("tone") in TONES, sc, "unknown tone")
                stored["tone"] = side["tone"]
                stored["sentence"] = None if side.get("sentence") is None else _sentence(
                    side["sentence"], title=article.title, body=article.body_text, context=sc)
            elif kind == "SOURCES":
                stored["counts"] = _counts(side.get("counts", {}), sc)
            else:
                stored["values"] = _numbers(side.get("values", []), sc)
            sides.append(stored)
        _check(bool(sides), c, "a comparison needs sides")
        row["sides"] = sides
        out.append(row)
    return out


def _sentence_matches(article: Article, sentence: dict | None) -> bool:
    if sentence is None:
        return True
    text = article.title if sentence["scope"] == "TITLE" else article.body_text
    return text[sentence["start"]:sentence["end"]] == sentence["text"]


def economic_payload(article: Article, publisher_payload) -> dict | None:
    """The reader's economic lens for one article, or None when the article has none. Evidence whose text no longer
    matches the stored article is dropped; comparison sides of hidden articles or inactive publishers are dropped."""
    context = article.economic_context
    if not context:
        return None
    evidence = [{k: e[k] for k in ("sentence", "channels", "role", "tone", "voice", "source", "reasons")}
                for e in context.get("evidence", []) if _sentence_matches(article, e["sentence"])]
    comparison = []
    stored = (article.story_cluster.economic_comparison if article.story_cluster_id else None) or []
    ids = {side["article_id"] for row in stored for side in row["sides"]}
    others = {str(a.id): a for a in Article.objects.filter(
        id__in=ids, visibility=ArticleVisibility.VISIBLE, publisher__is_active=True).select_related("publisher")}
    for row in stored:
        sides = []
        for side in row["sides"]:
            other = others.get(side["article_id"])
            if other is None or not _sentence_matches(other, side.get("sentence")):
                continue
            sides.append({**side, "publisher": publisher_payload(other.publisher), "title": other.title,
                          "is_current": other.id == article.id})
        if sides:
            comparison.append({**{k: v for k, v in row.items() if k != "sides"}, "sides": sides})
    return {"focus": context.get("focus", []), "tone": context.get("tone", {}), "evidence": evidence,
            "sources": context.get("sources", {}), "numbers": context.get("numbers", []), "comparison": comparison}

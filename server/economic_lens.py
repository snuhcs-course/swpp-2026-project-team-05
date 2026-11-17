"""Economic lens for economy articles: validation of reviewed dataset rows and the reader projection.

An economy article may carry `economic_context`: the economic event as the articles name it, and the article's view
of it in one or two phrases of its own words (which economic channel, with which stance). The reader sees that view
beside the other outlets' views of the same event. The lens is produced offline by the economic pipeline and reviewed
before import; the API never computes it. Contract: specs/features/economic-lens.md.
"""

from __future__ import annotations

from .models import Article, ArticleVisibility

CHANNELS = frozenset({
    "fx", "capital_market", "household_credit", "housing", "macro_growth", "prices", "trade", "corporate", "fiscal",
    "labor",
})
STANCES = frozenset({"CONCERN", "OPTIMISM", "LIMITED", "OBSERVED"})
DIRECTIONS = frozenset({"UP", "DOWN", "SWING"})
SCOPES = frozenset({"TITLE", "BODY"})
MAX_VIEWS = 2
MAX_EVENT = 200


class EconomicLensError(ValueError):
    """An economic lens that does not match its contract or its article text."""


def _check(condition: bool, context: str, message: str) -> None:
    if not condition:
        raise EconomicLensError(f"{context}: {message}")


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


def validate_context(value, *, title: str, body: str, context: str) -> dict | None:
    """The reviewed `economic_context` of one article, checked against its title and body."""
    if value is None:
        return None
    _check(isinstance(value, dict), context, "economic_context must be an object")
    event = value.get("event")
    _check(isinstance(event, str) and 0 < len(event.strip()) <= MAX_EVENT, context, "event must be a short text")
    views = value.get("views")
    _check(isinstance(views, list) and 0 < len(views) <= MAX_VIEWS, context,
           f"views must hold one to {MAX_VIEWS} views")
    out = []
    for n, item in enumerate(views):
        c = f"{context} view {n}"
        _check(isinstance(item, dict), c, "view must be an object")
        channels = item.get("channels")
        _check(isinstance(channels, list) and channels and all(ch in CHANNELS for ch in channels)
               and len(channels) == len(set(channels)), c, "channels must be distinct known channel codes")
        _check(item.get("stance") in STANCES, c, "unknown stance")
        direction = item.get("direction")
        _check(direction is None or direction in DIRECTIONS, c, "unknown direction")
        sentence = _sentence(item.get("sentence"), title=title, body=body, context=c)
        phrase = item.get("phrase")
        _check(isinstance(phrase, str) and phrase.strip() and phrase in sentence["text"], c,
               "phrase must be words of its sentence")
        out.append({"channels": list(channels), "stance": item["stance"], "direction": direction, "phrase": phrase,
                    "sentence": sentence})
    return {"event": event.strip(), "views": out}


def _view_matches(article: Article, view: dict) -> bool:
    sentence = view["sentence"]
    text = article.title if sentence["scope"] == "TITLE" else article.body_text
    return text[sentence["start"]:sentence["end"]] == sentence["text"]


def _views(article: Article) -> list[dict]:
    return [view for view in (article.economic_context or {}).get("views", []) if _view_matches(article, view)]


def economic_payload(article: Article, publisher_payload) -> dict | None:
    """The reader's economic lens for one article, or None when the article has none.

    `others` are the other visible articles of the cluster that carry a lens, newest first; `contrasts` name each
    channel this article views on which another outlet takes a different stance. Views whose sentence no longer
    matches the stored article are left out."""
    context = article.economic_context
    if not context:
        return None
    views = _views(article)
    others = []
    if article.story_cluster_id:
        siblings = (Article.objects
                    .filter(story_cluster_id=article.story_cluster_id, visibility=ArticleVisibility.VISIBLE,
                            publisher__is_active=True, economic_context__isnull=False)
                    .exclude(id=article.id).select_related("publisher").order_by("-published_at", "id"))
        for other in siblings:
            other_views = _views(other)
            if other_views:
                others.append({"article_id": str(other.id), "publisher": publisher_payload(other.publisher),
                               "title": other.title, "views": other_views})
    contrasts = []
    for channel in dict.fromkeys(ch for view in views for ch in view["channels"]):
        mine = next(view["stance"] for view in views if channel in view["channels"])
        differing = [{"article_id": other["article_id"], "publisher": other["publisher"], "stance": view["stance"]}
                     for other in others for view in other["views"]
                     if channel in view["channels"] and view["stance"] != mine]
        if differing:
            contrasts.append({"channel": channel, "stance": mine, "others": differing})
    return {"event": context.get("event", ""), "views": views, "others": others, "contrasts": contrasts}

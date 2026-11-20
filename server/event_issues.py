"""Event issue view: validation of reviewed dataset rows and the reader projection.

A cluster may carry an `issue_table`: the event in one line, its issues (each a question, the positions outlets take
on it and which outlet takes which, or only reports it as fact), the facts the outlets report alike, and inside each
the wording differences between two outlets (A-G). An article may carry a `frame_context`: its frame of the event
and one sentence of its own text that shows it. Both are produced offline and reviewed before import; the reader's
view of the table is computed per article on request, so one table serves every article of the event.
Contract: specs/features/event-issue-view.md.
"""

from __future__ import annotations

import re

from .models import EXPRESSION_CATEGORIES, Article, ArticleVisibility

KINDS = ("contested", "one_side", "common_fact")
SCOPES = frozenset({"TITLE", "BODY"})
FACT_ONLY = "F"
POSITION_ID = re.compile(r"^P\d{1,2}$")
MAX_ISSUES = 12
MAX_FACTS = 12
MAX_WORDINGS = 40
MAX_POSITIONS_SHOWN = 3  # per other outlet in the frame box


class IssueTableError(ValueError):
    """An issue table or frame context that does not match its contract or its articles."""


def _check(condition: bool, context: str, message: str) -> None:
    if not condition:
        raise IssueTableError(f"{context}: {message}")


def _text(value, context: str, field: str, limit: int) -> str:
    _check(isinstance(value, str) and 0 < len(value.strip()) <= limit, context, f"{field} must be a short text")
    return value.strip()


def validate_frame_context(value, *, title: str, body: str, context: str) -> dict | None:
    """The reviewed `frame_context` of one article, checked against its title and body."""
    if value is None:
        return None
    _check(isinstance(value, dict), context, "frame_context must be an object")
    frame = _text(value.get("frame"), context, "frame", 40)
    sentence = value.get("sentence")
    _check(isinstance(sentence, dict), context, "sentence must be an object")
    scope, start, end, text = (sentence.get(k) for k in ("scope", "start", "end", "text"))
    _check(scope in SCOPES, context, "sentence scope must be TITLE or BODY")
    _check(isinstance(start, int) and isinstance(end, int) and isinstance(text, str), context,
           "sentence needs integer start / end and its text")
    selected = title if scope == "TITLE" else body
    _check(0 <= start < end <= len(selected) and selected[start:end] == text, context,
           "sentence text does not match its offsets")
    return {"frame": frame, "sentence": {"scope": scope, "start": start, "end": end, "text": text}}


def validate_issue_table(value, *, articles: dict[str, Article], context: str) -> dict | None:
    """The reviewed `issue_table` of one cluster. `articles` maps the dataset's article keys to the stored articles;
    the stored table names articles by id. Every wording phrase must be words of its article."""
    if value is None:
        return None
    _check(isinstance(value, dict), context, "issue_table must be an object")
    event = _text(value.get("event"), context, "event", 200)

    def article_id(key, c: str) -> str:
        _check(isinstance(key, str) and key in articles, c, f"unknown article {key!r}")
        return str(articles[key].id)

    def wordings(items, c: str) -> list[dict]:
        _check(isinstance(items, list) and len(items) <= MAX_WORDINGS, c, "wordings must be a short array")
        out = []
        for n, item in enumerate(items):
            wc = f"{c} wording {n}"
            _check(isinstance(item, dict), wc, "wording must be an object")
            sides = {}
            for side in ("left", "right"):
                s = item.get(side)
                _check(isinstance(s, dict), wc, f"{side} must be an object")
                key = s.get("article")
                aid = article_id(key, wc)
                phrase = _text(s.get("phrase"), wc, f"{side} phrase", 120)
                stored = articles[key]
                _check(phrase in stored.title or phrase in stored.body_text, wc,
                       f"{side} phrase is not words of its article")
                sides[side] = {"article": aid, "phrase": phrase}
            _check(sides["left"]["article"] != sides["right"]["article"], wc, "a wording compares two articles")
            _check(item.get("code") in EXPRESSION_CATEGORIES, wc, "code must be one of A-G")
            out.append({**sides, "code": item["code"],
                        "explanation": _text(item.get("explanation"), wc, "explanation", 300)})
        return out

    issues = value.get("issues")
    _check(isinstance(issues, list) and len(issues) <= MAX_ISSUES, context, f"issues must hold at most {MAX_ISSUES}")
    keys: set[str] = set()
    out_issues = []
    for n, issue in enumerate(issues):
        c = f"{context} issue {n}"
        _check(isinstance(issue, dict), c, "issue must be an object")
        key = _text(issue.get("key"), c, "key", 32)
        _check(key not in keys, c, f"duplicate key {key}")
        keys.add(key)
        _check(issue.get("kind") in KINDS, c, "unknown kind")
        positions = issue.get("positions")
        _check(isinstance(positions, dict) and all(POSITION_ID.match(p) for p in positions), c,
               "positions must map P1, P2, ... to labels")
        positions = {p: _text(label, c, f"position {p}", 120) for p, label in positions.items()}
        outlets = issue.get("outlets")
        _check(isinstance(outlets, list) and outlets, c, "outlets must be a non-empty array")
        seen, out_outlets = set(), []
        for o in outlets:
            _check(isinstance(o, dict), c, "outlet must be an object")
            aid = article_id(o.get("article"), c)
            _check(aid not in seen, c, "an article appears once per issue")
            seen.add(aid)
            _check(o.get("position") == FACT_ONLY or o.get("position") in positions, c,
                   "outlet position must be one of the issue's positions or F")
            out_outlets.append({"article": aid, "position": o["position"]})
        out_issues.append({"key": key, "question": _text(issue.get("question"), c, "question", 300),
                           "kind": issue["kind"], "positions": positions, "outlets": out_outlets,
                           "wordings": wordings(issue.get("wordings", []), c)})

    facts = value.get("facts", [])
    _check(isinstance(facts, list) and len(facts) <= MAX_FACTS, context, f"facts must hold at most {MAX_FACTS}")
    out_facts = []
    for n, fact in enumerate(facts):
        c = f"{context} fact {n}"
        _check(isinstance(fact, dict), c, "fact must be an object")
        key = _text(fact.get("key"), c, "key", 32)
        _check(key not in keys, c, f"duplicate key {key}")
        keys.add(key)
        members = fact.get("outlets")
        _check(isinstance(members, list) and members, c, "outlets must be a non-empty array")
        ids = [article_id(k, c) for k in members]
        _check(len(ids) == len(set(ids)), c, "an article appears once per fact")
        out_facts.append({"key": key, "label": _text(fact.get("label"), c, "label", 300), "outlets": ids,
                          "wordings": wordings(fact.get("wordings", []), c)})
    return {"event": event, "issues": out_issues, "facts": out_facts}


def _sentence_matches(article: Article, sentence: dict) -> bool:
    text = article.title if sentence["scope"] == "TITLE" else article.body_text
    return text[sentence["start"]:sentence["end"]] == sentence["text"]


def frame_payload(article: Article, publisher_payload) -> dict | None:
    """The reader's frame box and issue view for one article, or None when the article or its cluster has none.

    Only visible articles of active publishers appear; positions, outlets and wordings naming any other article are
    left out. Issues come in the order the reader meets them: the ones this article takes a position on, then the
    ones it reports only as fact, then the ones it does not cover; contested before one-sided before common facts."""
    cluster = article.story_cluster
    context = article.frame_context
    if cluster is None or not context or not cluster.issue_table:
        return None
    if not _sentence_matches(article, context["sentence"]):
        return None
    siblings = list(Article.objects
                    .filter(story_cluster_id=cluster.id, visibility=ArticleVisibility.VISIBLE,
                            publisher__is_active=True)
                    .select_related("publisher").order_by("published_at", "id"))
    by_id = {str(a.id): a for a in siblings}
    own_id = str(article.id)
    table = cluster.issue_table

    def ref(aid: str) -> dict:
        return {"article_id": aid, "publisher": publisher_payload(by_id[aid].publisher)}

    def wordings(items: list) -> list:
        return [{"left": {**ref(w["left"]["article"]), "phrase": w["left"]["phrase"]},
                 "right": {**ref(w["right"]["article"]), "phrase": w["right"]["phrase"]},
                 "code": w["code"], "explanation": w["explanation"]}
                for w in items if w["left"]["article"] in by_id and w["right"]["article"] in by_id]

    issues = []
    for issue in table["issues"]:
        outlets = [o for o in issue["outlets"] if o["article"] in by_id]
        own = next((o for o in outlets if o["article"] == own_id), None)
        groups = []
        for pid, label in issue["positions"].items():
            members = [o for o in outlets if o["position"] == pid]
            if members:
                groups.append({"label": label, "is_own": bool(own and own["position"] == pid),
                               "outlets": [ref(o["article"]) for o in members if o["article"] != own_id]})
        issues.append({
            "question": issue["question"], "kind": issue["kind"],
            "own": {"covered": own is not None, "fact_only": bool(own and own["position"] == FACT_ONLY),
                    "position": issue["positions"].get(own["position"]) if own else None},
            "positions": groups,
            "fact_only_outlets": [ref(o["article"]) for o in outlets
                                  if o["position"] == FACT_ONLY and o["article"] != own_id],
            "wordings": wordings(issue["wordings"]),
        })
    rank = {kind: n for n, kind in enumerate(KINDS)}
    issues.sort(key=lambda v: (not v["own"]["covered"], v["own"]["fact_only"], rank[v["kind"]]))

    others = []
    for other in siblings:
        frame = other.frame_context
        if other.id == article.id or not frame or not _sentence_matches(other, frame["sentence"]):
            continue
        aid = str(other.id)
        positions = [{"issue": i["question"], "position": i["positions"][o["position"]]}
                     for i in table["issues"] for o in i["outlets"]
                     if o["article"] == aid and o["position"] != FACT_ONLY]
        others.append({**ref(aid), "title": other.title, "frame": frame["frame"],
                       "sentence": frame["sentence"]["text"], "positions": positions[:MAX_POSITIONS_SHOWN]})
    others.sort(key=lambda o: not o["positions"])
    facts = [{"label": f["label"], "outlets": sum(1 for aid in f["outlets"] if aid in by_id),
              "wordings": wordings(f["wordings"])} for f in table["facts"]]
    return {"event": table["event"], "frame": context["frame"], "sentence": context["sentence"],
            "others": others, "issues": issues, "facts": facts}

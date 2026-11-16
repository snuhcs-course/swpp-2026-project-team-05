"""Staged analysis results in the news dataset schema (schema_version 1) that news_import.import_dataset loads.

One cluster per analysed link. Every article is stored with is_feed_visible False: a link someone analysed opens in
the reader but does not join the shared feed. One finding per source marker (overlapping spans of one sentence are
one marker, widened by a word until its text occurs once in the sentence); at most one comparison per finding and
comparison sentence (the product's unique key). No model calls and no database writes.
"""

from __future__ import annotations

import hashlib
import re
from datetime import datetime
from urllib.parse import parse_qs, urlsplit

ANALYSIS_VERSION = "staged-v6"
NAVER_SECTIONS = {"100": "정치", "101": "경제", "102": "사회", "103": "생활/문화", "104": "세계", "105": "IT/과학"}


def cluster_key(source_url: str) -> str:
    return "staged-" + hashlib.sha256((source_url or "").encode("utf-8")).hexdigest()[:16]


def section_of(urls: list[str]) -> dict:
    """The Naver section most links name (`sid`); politics stays politics, every other section is filed under society."""
    sids = [s for s in ((parse_qs(urlsplit(u or "").query).get("sid") or [""])[0] for u in urls) if s in NAVER_SECTIONS]
    sid = max(set(sids), key=sids.count) if sids else ""
    return {"category": "POLITICS" if sid == "100" else "SOCIETY", "sid": sid, "name": NAVER_SECTIONS.get(sid, "")}


def _timestamp(value: str, fallback: str) -> str:
    value = re.sub(r"([+-]\d{2})(\d{2})$", r"\1:\2", (value or "").strip())
    try:
        parsed = datetime.fromisoformat(value.replace("Z", "+00:00"))
    except ValueError:
        return fallback
    return parsed.isoformat() + ("+09:00" if parsed.tzinfo is None else "")


def _publisher(article: dict) -> tuple[str, str, str]:
    from ...gold_dataset import KNOWN_PUBLISHER_CODES, PUBLISHER_NAME_BY_DOMAIN, _fallback_publisher_code

    host = urlsplit(article.get("url") or "").netloc.lower()
    name = (article.get("publisher") or "").strip() or PUBLISHER_NAME_BY_DOMAIN.get(host, host) or "출처 미상"
    return KNOWN_PUBLISHER_CODES.get(name, _fallback_publisher_code(name)), name, host


def _widen(sentence: str, start: int, end: int) -> tuple[int, int] | None:
    while sentence.count(sentence[start:end]) > 1:
        if start > 0:
            start = sentence.rfind(" ", 0, max(0, start - 1)) + 1
        elif end < len(sentence):
            nxt = sentence.find(" ", end + 1)
            end = len(sentence) if nxt < 0 else nxt
        else:
            return None
    return start, end


def build_dataset(articles: dict, shown: list[dict], *, now: str) -> dict:
    """`articles` as analysis.analyze takes them, `shown` its result, `now` an ISO time for pages with no date."""
    source = articles["S"]
    key = cluster_key(source.get("url") or "")
    section = section_of([a.get("url") or "" for a in articles.values()])

    def article_key(tag: str) -> str:
        return f"{key}-{'source' if tag == 'S' else tag.lower()}"

    publishers, rows = {}, []
    for tag, article in articles.items():
        code, name, host = _publisher(article)
        publishers.setdefault(code, {"code": code, "name_ko": name, "domain": host or "n.news.naver.com",
                                     "display_order": len(publishers) + 1})
        rows.append({"key": article_key(tag), "publisher": code, "canonical_url": article.get("url") or "",
                     "title": article.get("title") or "", "body_text": article.get("body") or "",
                     "category": section["category"], "published_at": _timestamp(article.get("published_at"), now),
                     "visibility": "VISIBLE", "is_feed_visible": False})

    groups = []
    for x in sorted(shown, key=lambda x: (x["source_start"], x["source_end"])):
        if groups and x["source_start"] < groups[-1]["end"] and x["source_sentence"] == groups[-1]["sentence"]:
            groups[-1]["end"] = max(groups[-1]["end"], x["source_end"])
            groups[-1]["items"].append(x)
        else:
            groups.append({"start": x["source_start"], "end": x["source_end"], "sentence": x["source_sentence"],
                           "sentence_start": x["source_start"] - x["a0"], "items": [x]})
    findings, propositions = [], []
    for g in groups:
        sentence, s0 = g["sentence"], g["sentence_start"]
        span = _widen(sentence, g["start"] - s0, g["end"] - s0)
        if source["body"].count(sentence) != 1 or span is None:
            continue
        comparisons, seen = [], set()
        for x in sorted(g["items"], key=lambda x: (x["target"], x["b0"])):
            target_sentence = x["target_sentence"]
            tspan = _widen(target_sentence, x["b0"], x["b1"])
            if ((x["target"], target_sentence) in seen or tspan is None
                    or articles[x["target"]]["body"].count(target_sentence) != 1):
                continue
            seen.add((x["target"], target_sentence))
            comparisons.append({"target_article": article_key(x["target"]), "target_scope": "BODY",
                                "target_sentence_text": target_sentence,
                                "target_marker_text": target_sentence[tspan[0]:tspan[1]], "target_owner": "REPORTER",
                                "target_speaker_label": None, "comparison_summary": x["explanation"],
                                "display_rank": len(comparisons), "_code": x["code"]})
        if not comparisons:
            continue
        start, end = s0 + span[0], s0 + span[1]
        overview = " ".join(dict.fromkeys(c["comparison_summary"] for c in comparisons))
        propositions.append({"key": f"proposition-{start}-{end}", "normalized_proposition": sentence,
                             "comparison_overview": overview})
        findings.append({"key": f"finding-{start}-{end}", "article": article_key("S"), "source_scope": "BODY",
                         "source_sentence_text": sentence, "marker_text": sentence[span[0]:span[1]],
                         "categories": sorted({c.pop("_code") for c in comparisons}), "explanation": overview,
                         "display_order": len(findings), "shared_proposition": f"proposition-{start}-{end}",
                         "comparisons": comparisons})
    return {"schema_version": 1, "analysis_version": ANALYSIS_VERSION, "publishers": list(publishers.values()),
            "clusters": [{"key": key, "internal_label": source.get("title") or "", "category": section["category"],
                          "naver_section": {"sid": section["sid"], "name": section["name"]},
                          "articles": rows, "propositions": propositions, "findings": findings}]}

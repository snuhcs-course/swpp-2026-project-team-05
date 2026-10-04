"""Trace claims, arguments, and wording back to a news article's source text.

Results describe what an article says. They do not verify a claim's truth or
assign an outlet a neutrality score.
"""

from __future__ import annotations

import re
from typing import Any

from .model_client import GeminiJSONClient


ROLES = {
    "event_report": "사건 서술",
    "attributed_statement": "발언 전달",
    "support": "근거 제시",
    "evaluation": "평가·결론",
    "context": "배경 설명",
}
CLAIM_KINDS = ("reported_fact", "attributed_statement", "editorial_position")
ARGUMENT_SCHEMES = ("none", "causal", "comparison", "authority", "opposition", "expectation")

# These are candidates for review, not automatic judgements of bias. Negation
# and uncertainty in particular do not imply negative or positive sentiment.
_MARKER_RULES = (
    ("engagement", "denial", r"지 않(?:다|는다|았다|습니다)|아니(?:다|다며|라고)|없(?:다|다고|습니다)|못(?:하다|한다|했다)"),
    ("engagement", "counter", r"하지만|그러나|그런데|다만|반면"),
    ("certainty", "hedged", r"가능성|추정|것으로 보(?:인다|였)|수 있(?:다|다고|습니다)|것 같(?:다|습니다)"),
    ("certainty", "categorical", r"명백|확실|분명|반드시|절대|추호도|틀림없"),
    ("stance", "obligation", r"해야 (?:한다|합니다)|하여야 (?:한다|합니다)|할 필요가 있"),
    ("evaluation", "positive", r"바람직|적임자|성과|성공적|환영|존중"),
    ("evaluation", "negative", r"부당|터무니|실패|논란|면죄부"),
    ("intensity", "amplified", r"매우|크게|엄정|철저|전혀|극히|충격|폭탄"),
)


def clean_article_body(text: str) -> str:
    """Normalize paragraphs and remove obvious captions and a leading subhead."""
    if not isinstance(text, str) or not text.strip():
        raise ValueError("Article text must be a non-empty string")
    paragraphs = [" ".join(line.split()) for line in text.splitlines()]
    paragraphs = [line for line in paragraphs if line]
    paragraphs = [
        line for line in paragraphs
        if not (len(line) < 100 and re.search(r"사진\s*=", line))
    ]
    if (
        len(paragraphs) > 1
        and len(paragraphs[0]) < 60
        and len(paragraphs[1]) >= 60
        and not re.search(r"[.!?。]$", paragraphs[0])
    ):
        paragraphs.pop(0)
    if not paragraphs:
        raise ValueError("No article body remained after cleaning")
    return "\n".join(paragraphs)


def _numbered_sentences(body: str) -> list[dict[str, Any]]:
    sentences = []
    for paragraph_index, paragraph in enumerate(clean_article_body(body).splitlines()):
        for part in re.split(r'(?<=[.!?])\s+(?=[가-힣A-Za-z"“‘])', paragraph):
            if part.strip():
                sentences.append({
                    "sentence_index": len(sentences),
                    "paragraph_index": paragraph_index,
                    "text": part.strip(),
                })
    return sentences


def extract_core_event(body: str, title: str = "") -> dict[str, str]:
    """Identify one search event and distinguish reporting from editorial text."""
    lead = clean_article_body(body).splitlines()[:3]
    response = GeminiJSONClient().generate_json(
        name="article_event_and_genre",
        instructions=(
            "제목과 첫 문단을 중심으로 기사의 핵심 사건 하나를 한국어 한 문장으로 쓰세요. "
            "발언 내용을 독립적으로 확인된 사실처럼 단정하지 마세요. "
            "글 종류는 news(보도), editorial(사설·칼럼), other 중 하나로 분류하세요."
        ),
        input_data={"title": title, "lead_paragraphs": lead},
        schema={
            "type": "object",
            "properties": {
                "event": {"type": "string"},
                "genre": {"type": "string", "enum": ["news", "editorial", "other"]},
            },
            "required": ["event", "genre"],
            "additionalProperties": False,
        },
    )
    event = response.get("event")
    if not isinstance(event, str) or not event.strip():
        raise ValueError("Gemini returned no core event")
    genre = response.get("genre")
    if genre not in ("news", "editorial", "other"):
        genre = "other"
    if re.search(r"(?:\[|【)\s*(?:사설|칼럼|오피니언)", title):
        genre = "editorial"
    return {"event": " ".join(event.split()), "evidence": lead[0], "genre": genre}


def _quoted_ranges(text: str) -> list[tuple[int, int, bool]]:
    """Locate quotations; only double quotes may inherit the sentence speaker."""
    ranges = []
    start = None
    opener = ""
    for index, char in enumerate(text):
        if char in ('"', '“', "'", '‘') and start is None:
            start = index
            opener = char
        elif start is not None and (
            (opener == '"' and char == '"')
            or (opener == '“' and char == '”')
            or (opener == "'" and char == "'")
            or (opener == '‘' and char == '’')
        ):
            ranges.append((start, index + 1, opener in ('"', '“')))
            start = None
            opener = ""
    return ranges


def _scan_markers(text: str, sentence_index: int, speaker: str = "") -> list[dict]:
    """Find exact Korean wording markers and mark whose voice contains them."""
    quoted = _quoted_ranges(text)
    markers = []
    seen = set()
    for dimension, label, pattern in _MARKER_RULES:
        for match in re.finditer(pattern, text):
            key = (match.start(), match.end(), dimension, label)
            if key in seen:
                continue
            seen.add(key)
            matching_quote = next(
                (item for item in quoted if item[0] <= match.start() and match.end() <= item[1]),
                None,
            )
            in_quote = matching_quote is not None
            markers.append({
                "sentence_index": sentence_index,
                "text": match.group(),
                "start": match.start(),
                "end": match.end(),
                "dimension": dimension,
                "label": label,
                "voice": "quoted_voice" if in_quote else "article_voice",
                "speaker": speaker if in_quote and matching_quote[2] else "",
            })
    return sorted(markers, key=lambda marker: (marker["start"], marker["end"]))


def _analysis_schema() -> dict:
    return {
        "type": "object",
        "properties": {
            "analyses": {
                "type": "array",
                "items": {
                    "type": "object",
                    "properties": {
                        "sentence_index": {"type": "integer"},
                        "role": {"type": "string", "enum": list(ROLES)},
                        "speaker": {"type": "string"},
                        "claims": {
                            "type": "array",
                            "items": {
                                "type": "object",
                                "properties": {
                                    "claim": {"type": "string"},
                                    "claim_kind": {"type": "string", "enum": list(CLAIM_KINDS)},
                                    "attributed_to": {"type": "string"},
                                    "target": {"type": "string"},
                                    "evidence_quote": {"type": "string"},
                                    "reason_sentence_index": {"type": "integer"},
                                    "reason_quote": {"type": "string"},
                                    "argument_scheme": {
                                        "type": "string", "enum": list(ARGUMENT_SCHEMES)
                                    },
                                },
                                "required": [
                                    "claim", "claim_kind", "attributed_to", "target",
                                    "evidence_quote", "reason_sentence_index",
                                    "reason_quote", "argument_scheme",
                                ],
                                "additionalProperties": False,
                            },
                        },
                    },
                    "required": ["sentence_index", "role", "speaker", "claims"],
                    "additionalProperties": False,
                },
            }
        },
        "required": ["analyses"],
        "additionalProperties": False,
    }


def analyze_sentences(body: str, core_event: str, genre: str = "news") -> dict[str, list[dict]]:
    """Analyse every sentence, grounding claims and explicit reasons in text."""
    if not isinstance(core_event, str) or not core_event.strip():
        raise ValueError("Core event must be a non-empty string")
    sentences = _numbered_sentences(body)
    client = GeminiJSONClient()
    by_index = {}
    # Batching keeps the JSON response bounded while still covering the whole article.
    for start in range(0, len(sentences), 12):
        batch = sentences[start:start + 12]
        response = client.generate_json(
            name="source_grounded_claims_and_arguments",
            instructions=(
                "모든 입력 문장의 역할을 event_report, attributed_statement, support, "
                "evaluation, context 중 하나로 표시하세요. 발언자는 없으면 빈 문자열입니다. "
                "핵심 사건과 관계있는 원자적 주장만 추출하고, 기사 후반에도 나타나면 포함하세요. "
                "사설의 평가·요구는 editorial_position, 보도된 행위는 reported_fact, "
                "누군가의 발언 내용은 attributed_statement로 구분하세요. "
                "evidence_quote는 해당 문장에서 연속된 원문 일부를 그대로 복사하세요. "
                "주장을 뒷받침하는 이유가 입력 문장에 명시된 경우에만 reason_quote와 "
                "reason_sentence_index를 채우고 논증 방식을 선택하세요. "
                "발언하게 된 계기나 질문·논란이 발생한 시점은 주장 자체의 이유가 아닙니다. "
                "그 이유가 왜 해당 주장이나 평가를 뒷받침하는지 설명할 수 있을 때만 연결하세요. "
                "이유가 없으면 reason_quote는 빈 문자열, 인덱스는 -1, 방식은 none입니다. "
                "이유와 근거는 같은 문장에 있을 수도 있습니다. "
                "어휘 하나만 보고 인과관계를 만들어내거나 발언을 확인된 사실로 바꾸지 마세요. "
                "평가 대상이 명시되지 않으면 target은 빈 문자열입니다."
            ),
            input_data={"core_event": core_event, "genre": genre, "sentences": batch},
            schema=_analysis_schema(),
            max_output_tokens=8192,
        )
        rows = response.get("analyses")
        if not isinstance(rows, list):
            raise ValueError("Gemini returned no sentence analyses")
        for row in rows:
            if isinstance(row, dict) and type(row.get("sentence_index")) is int:
                index = row["sentence_index"]
                if start <= index < start + len(batch):
                    by_index[index] = row

    labeled = []
    claims = []
    markers = []
    seen = set()
    for index, source in enumerate(sentences):
        row = by_index.get(index, {})
        role = row.get("role") if row.get("role") in ROLES else "unclassified"
        speaker = row.get("speaker")
        speaker = " ".join(speaker.split()) if isinstance(speaker, str) else ""
        text = source["text"]
        if re.search(r"평가했(?:습니)?다", text):
            role = "evaluation"
        elif re.search(r'["“].+["”].*(?:밝혔|말했|설명했|강조했|부연했|전했)', text):
            role = "attributed_statement"
        if role == "attributed_statement" and not speaker:
            name = re.match(r"^([가-힣]{2,4})\s", text)
            if name:
                speaker = name.group(1)
        labeled.append({**source, "role": role, "speaker": speaker})
        markers.extend(_scan_markers(text, index, speaker))

        raw_claims = row.get("claims")
        if not isinstance(raw_claims, list):
            continue
        for item in raw_claims:
            if not isinstance(item, dict):
                continue
            claim, quote = item.get("claim"), item.get("evidence_quote")
            kind = item.get("claim_kind")
            if not isinstance(claim, str) or not isinstance(quote, str) or kind not in CLAIM_KINDS:
                continue
            claim, quote = " ".join(claim.split()), " ".join(quote.split())
            if not claim or not quote or quote not in text:
                continue
            if kind == "reported_fact":
                quote_is_speech = any(
                    span_start <= match.start() and match.end() <= span_end
                    for match in re.finditer(re.escape(quote), text)
                    for span_start, span_end, direct_speech in _quoted_ranges(text)
                    if direct_speech
                )
                if quote_is_speech:
                    kind = "attributed_statement"
            if (kind, claim) in seen:
                continue
            seen.add((kind, claim))
            attributed_to = item.get("attributed_to")
            attributed_to = (
                " ".join(attributed_to.split()) if isinstance(attributed_to, str) else ""
            )
            if kind == "attributed_statement":
                attributed_to = attributed_to or speaker or "발언자 미상"
            else:
                attributed_to = ""
            target = item.get("target")
            target = " ".join(target.split()) if isinstance(target, str) else ""

            reason = None
            reason_index = item.get("reason_sentence_index")
            reason_quote = item.get("reason_quote")
            batch_start = (index // 12) * 12
            if (
                type(reason_index) is int
                and batch_start <= reason_index < min(batch_start + 12, len(sentences))
                and isinstance(reason_quote, str)
            ):
                reason_quote = " ".join(reason_quote.split())
                if reason_quote and reason_quote in sentences[reason_index]["text"]:
                    reason = {"sentence_index": reason_index, "quote": reason_quote}
            scheme = item.get("argument_scheme")
            if reason is None or scheme not in ARGUMENT_SCHEMES or scheme == "none":
                reason, scheme = None, "none"
            claims.append({
                "claim_index": len(claims),
                "claim": claim,
                "claim_kind": kind,
                "speaker": attributed_to,
                "target": target,
                "evidence": {"sentence_index": index, "quote": quote},
                "reason": reason,
                "argument_scheme": scheme,
            })
    for claim in claims:
        grounded_quotes = [claim["evidence"]]
        if claim["reason"] is not None:
            grounded_quotes.append(claim["reason"])

        def belongs_to_grounded_quote(marker: dict) -> bool:
            for grounded in grounded_quotes:
                if marker["sentence_index"] != grounded["sentence_index"]:
                    continue
                source_text = sentences[grounded["sentence_index"]]["text"]
                for match in re.finditer(re.escape(grounded["quote"]), source_text):
                    if match.start() <= marker["start"] and marker["end"] <= match.end():
                        return True
            return False

        claim["markers"] = [marker for marker in markers if belongs_to_grounded_quote(marker)]
    return {"sentences": labeled, "claims": claims, "markers": markers}


def extract_claims(body: str, core_event: str) -> list[dict]:
    """Compatibility helper for callers that only need the article's claims."""
    return analyze_sentences(body, core_event)["claims"]


def analyze_article(body: str, title: str = "", url: str = "") -> dict:
    """Run the single-article pipeline and return JSON-serializable data."""
    clean_body = clean_article_body(body)
    event = extract_core_event(clean_body, title)
    analysis = analyze_sentences(clean_body, event["event"], event["genre"])
    return {
        "url": url,
        "title": title,
        "genre": event["genre"],
        "core_event": event["event"],
        "event_evidence": event["evidence"],
        "body": clean_body,
        "title_markers": _scan_markers(title, -1),
        **analysis,
    }


def compare_articles(articles: list[dict]) -> dict[str, list[dict]]:
    """Align claims by issue and keep each article's evidence and wording."""
    if len(articles) < 2:
        raise ValueError("At least two article analyses are required")
    items = [
        {
            "article_index": article_index,
            "claim_index": claim_index,
            "claim": claim["claim"],
            "claim_kind": claim["claim_kind"],
            "speaker": claim["speaker"],
            "target": claim["target"],
        }
        for article_index, article in enumerate(articles)
        for claim_index, claim in enumerate(article["claims"])
    ]
    if not items:
        return {"issues": [], "unmatched": []}
    response = GeminiJSONClient().generate_json(
        name="cross_article_issue_alignment",
        instructions=(
            "서로 다른 기사에서 동일한 구체적 쟁점에 답하는 주장들을 묶으세요. "
            "issue는 그 쟁점을 중립적인 질문 한 문장으로 쓰세요. "
            "각 묶음의 첫 주장을 reference로 두고, 다른 주장이 첫 주장과 의미가 같으면 same, "
            "명백히 양립할 수 없으면 opposes, 같은 사건에 대한 원인·평가·해석이 다르면 "
            "different_interpretation으로 표시하세요. 단순한 추가 정보나 다른 사건은 묶지 마세요. "
            "주체·시점·대상이 다르면 같은 쟁점인지 특히 조심하세요. "
            "발언 내용은 발언자의 주장으로만 취급하고 확인된 사실로 바꾸지 마세요. "
            "서로 다른 기사 두 개 이상의 주장이 있는 묶음만 반환하세요. "
            "기사와 주장 번호는 입력에서만 가져오고 진실성·중립성은 판정하지 마세요."
        ),
        input_data={"claims": items},
        schema={
            "type": "object",
            "properties": {
                "issues": {
                    "type": "array",
                    "items": {
                        "type": "object",
                        "properties": {
                            "issue": {"type": "string"},
                            "members": {
                                "type": "array",
                                "items": {
                                    "type": "object",
                                    "properties": {
                                        "article_index": {"type": "integer"},
                                        "claim_index": {"type": "integer"},
                                        "relation_to_reference": {
                                            "type": "string",
                                            "enum": ["reference", "same", "opposes", "different_interpretation"],
                                        },
                                    },
                                    "required": ["article_index", "claim_index", "relation_to_reference"],
                                    "additionalProperties": False,
                                },
                            }
                        },
                        "required": ["issue", "members"],
                        "additionalProperties": False,
                    },
                }
            },
            "required": ["issues"],
            "additionalProperties": False,
        },
        max_output_tokens=4096,
    )
    issues = []
    used = set()
    groups = response.get("issues")
    if not isinstance(groups, list):
        raise ValueError("Gemini returned no issue groups")
    for group in groups:
        if (
            not isinstance(group, dict)
            or not isinstance(group.get("issue"), str)
            or not group["issue"].strip()
            or not isinstance(group.get("members"), list)
        ):
            continue
        members = []
        local_seen = set()
        for ref in group["members"]:
            if not isinstance(ref, dict):
                continue
            a, c = ref.get("article_index"), ref.get("claim_index")
            if type(a) is not int or type(c) is not int or not 0 <= a < len(articles):
                continue
            if not 0 <= c < len(articles[a]["claims"]) or (a, c) in used or (a, c) in local_seen:
                continue
            relation = ref.get("relation_to_reference")
            if relation not in ("reference", "same", "opposes", "different_interpretation"):
                continue
            local_seen.add((a, c))
            claim = articles[a]["claims"][c]
            voice = (
                "attributed_voice" if claim["claim_kind"] == "attributed_statement"
                else "editorial_voice" if claim["claim_kind"] == "editorial_position"
                else "article_voice"
            )
            members.append({
                "article_index": a,
                "title": articles[a]["title"],
                "url": articles[a].get("url", ""),
                **claim,
                "voice": voice,
                "relation_to_reference": relation,
            })
        if len({member["article_index"] for member in members}) >= 2:
            members[0]["relation_to_reference"] = "reference"
            issues.append({"issue": group["issue"].strip(), "members": members})
            used.update((member["article_index"], member["claim_index"]) for member in members)
    unmatched = [
        {"article_index": a, "title": article["title"], "url": article.get("url", ""), **claim}
        for a, article in enumerate(articles)
        for claim in article["claims"]
        if (a, claim["claim_index"]) not in used
    ]
    return {"issues": issues, "unmatched": unmatched}


def _source_issues(source: dict) -> tuple[list[dict], list[dict]]:
    """Group the source article's existing claims into concrete questions."""
    claims = source["claims"]
    if not claims:
        return [], []
    response = GeminiJSONClient().generate_json(
        name="source_article_issues",
        instructions=(
            "입력 기사 핵심 사건과 관련된 주장만 구체적인 쟁점별로 묶으세요. "
            "issue는 각 묶음이 답하는 질문 한 문장입니다. 같은 인물이나 주제라도 "
            "서로 다른 행위·판단이면 다른 쟁점입니다. 기사에 없는 쟁점은 만들지 마세요. "
            "각 claim_index는 최대 한 묶음에만 넣고, 핵심 사건과 관계없는 주장은 제외하세요."
        ),
        input_data={
            "core_event": source["core_event"],
            "claims": [
                {"claim_index": index, "claim": claim["claim"],
                 "speaker": claim["speaker"], "target": claim["target"]}
                for index, claim in enumerate(claims)
            ],
        },
        schema={
            "type": "object",
            "properties": {
                "issues": {"type": "array", "items": {
                    "type": "object",
                    "properties": {
                        "issue": {"type": "string"},
                        "claim_indices": {"type": "array", "items": {"type": "integer"}},
                    },
                    "required": ["issue", "claim_indices"],
                    "additionalProperties": False,
                }},
            },
            "required": ["issues"],
            "additionalProperties": False,
        },
        max_output_tokens=1024,
    )
    raw_issues = response.get("issues")
    if not isinstance(raw_issues, list):
        raise ValueError("Gemini returned no source issue list")
    issues = []
    used = set()
    for raw in raw_issues:
        if not isinstance(raw, dict) or not isinstance(raw.get("issue"), str):
            continue
        question = " ".join(raw["issue"].split())
        indices = raw.get("claim_indices")
        if not question or not isinstance(indices, list):
            continue
        valid = []
        for index in indices:
            if type(index) is int and 0 <= index < len(claims) and index not in used:
                valid.append(index)
                used.add(index)
        if valid:
            members = []
            for position, index in enumerate(valid):
                claim = claims[index]
                voice = (
                    "attributed_voice" if claim["claim_kind"] == "attributed_statement"
                    else "editorial_voice" if claim["claim_kind"] == "editorial_position"
                    else "article_voice"
                )
                members.append({
                    "article_index": 0, "title": source["title"], "url": source.get("url", ""),
                    **claim, "voice": voice,
                    "relation_to_reference": "reference" if position == 0 else "source_context",
                })
            issues.append({"issue": question, "source_claim_indices": valid,
                           "members": members, "not_found_in": []})
    unmatched = [claim for index, claim in enumerate(claims) if index not in used]
    return issues, unmatched


def _source_claim_issues(source: dict) -> list[dict]:
    """Use each source claim as its own comparison target."""
    issues = []
    for index, claim in enumerate(source["claims"]):
        voice = (
            "attributed_voice" if claim["claim_kind"] == "attributed_statement"
            else "editorial_voice" if claim["claim_kind"] == "editorial_position"
            else "article_voice"
        )
        member = {
            "article_index": 0, "title": source["title"], "url": source.get("url", ""),
            **claim, "voice": voice, "relation_to_reference": "reference",
        }
        issues.append({
            "issue": claim["claim"], "source_claim_indices": [index],
            "members": [member], "not_found_in": [],
        })
    return issues


def _retrieve_issue_sentences(body: str, issue: dict, source: dict) -> tuple[list[dict], list[dict]]:
    """Rank sentences locally, then include neighbours to preserve attribution."""
    sentences = _numbered_sentences(body)
    query = issue["issue"] + " " + " ".join(
        source["claims"][index]["claim"] for index in issue["source_claim_indices"]
    )
    terms = {
        word for word in re.findall(r"[가-힣A-Za-z0-9]{2,}", query)
        if word not in {"기사", "주장", "사건", "대한", "대해", "어떻게", "무엇", "있는가", "하는가"}
    }
    query_compact = re.sub(r"[^가-힣A-Za-z0-9]", "", query).lower()
    query_grams = {query_compact[i:i + 2] for i in range(max(0, len(query_compact) - 1))}

    def score(sentence: dict) -> float:
        compact = re.sub(r"[^가-힣A-Za-z0-9]", "", sentence["text"]).lower()
        token_hits = sum(
            2 if term.lower() in compact else 1 if re.fullmatch(r"[가-힣]+", term) and term[:2] in compact else 0
            for term in terms
        )
        grams = {compact[i:i + 2] for i in range(max(0, len(compact) - 1))}
        overlap = len(grams & query_grams) / max(1, len(grams))
        return token_hits * 2 + overlap

    ranked = sorted(range(len(sentences)), key=lambda index: (-score(sentences[index]), index))
    positive = [index for index in ranked if score(sentences[index]) > 0][:4]
    centers = positive or list(range(min(3, len(sentences))))
    chosen = set()
    for center in centers:
        chosen.update(range(max(0, center - 1), min(len(sentences), center + 2)))
    return sentences, [sentences[index] for index in sorted(chosen)]


def _grounded_markers(sentence: dict, quote: str, speaker: str) -> list[dict]:
    markers = _scan_markers(sentence["text"], sentence["sentence_index"], speaker)
    return [
        marker for marker in markers
        if any(
            match.start() <= marker["start"] and marker["end"] <= match.end()
            for match in re.finditer(re.escape(quote), sentence["text"])
        )
    ]


def _issue_match_schema() -> dict:
    return {
        "type": "object",
        "properties": {"results": {"type": "array", "items": {
            "type": "object",
            "properties": {
                "issue_index": {"type": "integer"},
                "matches": {"type": "array", "items": {
                    "type": "object",
                    "properties": {
                        "claim": {"type": "string"},
                        "source_claim_index": {"type": "integer"},
                        "relation": {"type": "string", "enum": ["same", "opposes", "different_interpretation"]},
                        "claim_kind": {"type": "string", "enum": list(CLAIM_KINDS)},
                        "speaker": {"type": "string"},
                        "target": {"type": "string"},
                        "evidence_sentence_index": {"type": "integer"},
                        "evidence_quote": {"type": "string"},
                        "reason_sentence_index": {"type": "integer"},
                        "reason_quote": {"type": "string"},
                        "argument_scheme": {"type": "string", "enum": list(ARGUMENT_SCHEMES)},
                    },
                    "required": ["claim", "source_claim_index", "relation", "claim_kind", "speaker", "target",
                                 "evidence_sentence_index", "evidence_quote",
                                 "reason_sentence_index", "reason_quote", "argument_scheme"],
                    "additionalProperties": False,
                }},
            },
            "required": ["issue_index", "matches"],
            "additionalProperties": False,
        }}},
        "required": ["results"],
        "additionalProperties": False,
    }


def _strong_issue_overlap(issue: dict, selected: list[dict]) -> bool:
    terms = {
        token[:2] for token in re.findall(
            r"[가-힣A-Za-z0-9]{2,}", issue["issue"] + " " + issue["members"][0]["claim"]
        )
        if token not in {"기사", "주장", "사건", "대한", "대해", "어떻게", "무엇"}
    }
    passage = re.sub(r"[^가-힣A-Za-z0-9]", "", " ".join(row["text"] for row in selected)).lower()
    return sum(term.lower() in passage for term in terms) >= 2


def compare_issue_passages(source: dict, related_articles: list[dict]) -> dict:
    """Compare each source claim with retrieved passages from related articles."""
    issues = _source_claim_issues(source)
    unmatched = []
    retrieval = []
    for article_index, article in enumerate(related_articles, 1):
        if not issues:
            break
        tasks = []
        sentence_maps = {}
        allowed_indices = {}
        for issue_index, issue in enumerate(issues):
            sentences, selected = _retrieve_issue_sentences(article["body"], issue, source)
            sentence_maps[issue_index] = {row["sentence_index"]: row for row in sentences}
            allowed_indices[issue_index] = {row["sentence_index"] for row in selected}
            retrieval.append({
                "article_index": article_index, "issue_index": issue_index,
                "sentence_indices": sorted(allowed_indices[issue_index]),
            })
            tasks.append({
                "issue_index": issue_index,
                "issue": issue["issue"],
                "source_claims": [
                    {"source_claim_index": index, "claim": source["claims"][index]["claim"],
                     "source_quote": source["claims"][index]["evidence"]["quote"]}
                    for index in issue["source_claim_indices"]
                ],
                "sentences": selected,
            })
        response = GeminiJSONClient().generate_json(
            name="compare_retrieved_issue_passages",
            instructions=(
                "각 원 기사 주장과 제공된 후보 문장만 읽고 대응 표현을 찾으세요. "
                "각 match에 대응하는 source_claim_index를 입력 목록에서 골라 넣으세요. "
                "같은 내용은 same, 선택한 원 기사 주장과 양립할 수 없는 반박은 opposes, "
                "동일한 구체적 행위·발언·판단에 대한 다른 원인·평가·해석만 different_interpretation입니다. "
                "인물·넓은 주제만 같고 행위·발언·판단이 다르면 matches를 비우세요. "
                "한 주장에 서로 다른 발언자나 입장이 있으면 각각 최대 두 개까지 반환하세요. "
                "evidence_quote는 지정한 문장에 연속으로 있는 원문을 그대로 복사하세요. "
                "발언은 확인된 사실처럼 쓰지 말고 발언자를 구분하세요. 기자의 서술과 인용된 발언을 혼동하지 마세요. "
                "주장을 뒷받침하는 이유가 제공된 문장에 명시된 경우에만 reason_quote를 넣으세요. "
                "찾지 못한 쟁점은 matches를 빈 배열로 두세요. 없는 내용을 추측하지 마세요."
            ),
            input_data={"article_title": article["title"], "tasks": tasks},
            schema=_issue_match_schema(),
            max_output_tokens=4096,
        )
        rows = response.get("results")
        if not isinstance(rows, list):
            raise ValueError("Gemini returned no issue passage results")
        grouped = {index: [] for index in range(len(issues))}
        for row in rows:
            if not isinstance(row, dict) or type(row.get("issue_index")) is not int:
                continue
            issue_index = row["issue_index"]
            if issue_index not in grouped or not isinstance(row.get("matches"), list):
                continue
            for match in row["matches"][:2]:
                if isinstance(match, dict):
                    grouped[issue_index].append(match)
        for issue_index, issue in enumerate(issues):
            added = 0
            seen_quotes = set()
            has_grounded_match = any(
                type(match.get("evidence_sentence_index")) is int
                and match["evidence_sentence_index"] in allowed_indices[issue_index]
                and type(match.get("source_claim_index")) is int
                and match["source_claim_index"] in issue["source_claim_indices"]
                and isinstance(match.get("evidence_quote"), str)
                and bool(match["evidence_quote"].strip())
                and match["evidence_quote"].strip() in sentence_maps[issue_index][match["evidence_sentence_index"]]["text"]
                for match in grouped[issue_index]
            )
            if not has_grounded_match and _strong_issue_overlap(issue, tasks[issue_index]["sentences"]):
                retry = GeminiJSONClient().generate_json(
                    name="retry_specific_issue_passage",
                    instructions=(
                        "하나의 원 기사 주장을 다시 확인하세요. 후보 문장에 같은 구체적 행위·발언·판단 또는 그 반박이 "
                        "있으면 matches에 원문 그대로 인용한 근거를 넣으세요. 같은 인물이나 넓은 주제만으로 연결하지 마세요. "
                        "대응하는 source_claim_index를 원 기사 주장 목록에서 선택하세요. "
                        "발언자와 기자의 서술을 구분하고, 명시되지 않은 이유는 비워 두세요. "
                        "정말 찾을 수 없을 때만 matches를 빈 배열로 반환하세요."
                    ),
                    input_data={"article_title": article["title"], "tasks": [tasks[issue_index]]},
                    schema=_issue_match_schema(),
                    max_output_tokens=1024,
                )
                for row in retry.get("results", []):
                    if (
                        isinstance(row, dict) and row.get("issue_index") == issue_index
                        and isinstance(row.get("matches"), list)
                    ):
                        grouped[issue_index].extend(
                            match for match in row["matches"][:2] if isinstance(match, dict)
                        )
            for match in grouped[issue_index]:
                evidence_index = match.get("evidence_sentence_index")
                source_claim_index = match.get("source_claim_index")
                quote = match.get("evidence_quote")
                claim = match.get("claim")
                if (
                    type(evidence_index) is not int
                    or evidence_index not in allowed_indices[issue_index]
                    or type(source_claim_index) is not int
                    or source_claim_index not in issue["source_claim_indices"]
                    or not isinstance(quote, str) or not isinstance(claim, str)
                ):
                    continue
                quote, claim = quote.strip(), " ".join(claim.split())
                sentence = sentence_maps[issue_index][evidence_index]
                if not quote or quote not in sentence["text"] or not claim or (source_claim_index, evidence_index, quote) in seen_quotes:
                    continue
                kind = match.get("claim_kind")
                relation = match.get("relation")
                if kind not in CLAIM_KINDS or relation not in ("same", "opposes", "different_interpretation"):
                    continue
                seen_quotes.add((source_claim_index, evidence_index, quote))
                direct_speech = any(
                    start <= occurrence.start() and occurrence.end() <= end and is_direct
                    for occurrence in re.finditer(re.escape(quote), sentence["text"])
                    for start, end, is_direct in _quoted_ranges(sentence["text"])
                )
                if direct_speech:
                    kind = "attributed_statement"
                speaker = match.get("speaker")
                speaker = " ".join(speaker.split()) if isinstance(speaker, str) else ""
                if kind == "attributed_statement":
                    speaker = speaker or "발언자 미상"
                else:
                    speaker = ""
                reason = None
                reason_index = match.get("reason_sentence_index")
                reason_quote = match.get("reason_quote")
                scheme = match.get("argument_scheme")
                if (
                    type(reason_index) is int and reason_index in allowed_indices[issue_index]
                    and isinstance(reason_quote, str) and reason_quote.strip()
                    and reason_quote.strip() in sentence_maps[issue_index][reason_index]["text"]
                    and scheme in ARGUMENT_SCHEMES and scheme != "none"
                    and not (
                        reason_index == evidence_index
                        and reason_quote.strip() in quote
                        and len(reason_quote.strip()) >= 0.7 * len(quote)
                    )
                ):
                    reason = {"sentence_index": reason_index, "quote": reason_quote.strip()}
                else:
                    scheme = "none"
                markers = _grounded_markers(sentence, quote, speaker)
                if reason:
                    markers += _grounded_markers(sentence_maps[issue_index][reason_index], reason["quote"], speaker)
                markers = list({
                    (marker["sentence_index"], marker["start"], marker["end"], marker["dimension"], marker["label"]): marker
                    for marker in markers
                }.values())
                target = match.get("target")
                target = " ".join(target.split()) if isinstance(target, str) else ""
                issue["members"].append({
                    "article_index": article_index,
                    "title": article["title"], "url": article.get("url", ""),
                    "claim": claim, "claim_kind": kind, "speaker": speaker, "target": target,
                    "source_claim_index": source_claim_index,
                    "evidence": {"sentence_index": evidence_index, "quote": quote},
                    "reason": reason, "argument_scheme": scheme, "markers": markers,
                    "voice": (
                        "attributed_voice" if kind == "attributed_statement"
                        else "editorial_voice" if kind == "editorial_position"
                        else "article_voice"
                    ),
                    "relation_to_reference": relation,
                    "relation_to_source": relation,
                })
                added += 1
            if added == 0:
                issue["not_found_in"].append({
                    "article_index": article_index, "title": article["title"],
                    "status": "not_found_in_retrieved_passages",
                })
    by_sentence = {}
    for issue in issues:
        for claim_index in issue["source_claim_indices"]:
            claim = source["claims"][claim_index]
            sentence_index = claim["evidence"]["sentence_index"]
            if not 0 <= sentence_index < len(source["sentences"]):
                continue
            row = by_sentence.setdefault(sentence_index, {
                "source_sentence_index": sentence_index,
                "source_sentence": source["sentences"][sentence_index]["text"],
                "source_claims": [], "related": [],
            })
            row["source_claims"].append({"claim_index": claim_index, "claim": claim["claim"]})
        for member in issue["members"]:
            if member["article_index"] == 0:
                continue
            claim_index = member["source_claim_index"]
            sentence_index = source["claims"][claim_index]["evidence"]["sentence_index"]
            if sentence_index in by_sentence:
                by_sentence[sentence_index]["related"].append(member)
    sentence_comparisons = [by_sentence[index] for index in sorted(by_sentence)]
    return {
        "issues": issues,
        "sentence_comparisons": sentence_comparisons,
        "unmatched_source_claims": unmatched,
        "retrieval": retrieval,
    }

"""Staged A-G analysis (v6): code builds every structure, the model only picks from options.

  S0  code                 eojeol with Kiwi tokens (content words, parts of speech, quotes, clause ends)
  S1  code                 source / comparison body sentence pairs by letter-pair similarity (plain and idf-weighted)
  S2  model, effort low    per pair: actor / act / scene / kind of each sentence (prompts/same.md); code joins them into
                           the same event, a naming pair (both sentences carry a name mark), or no pair
  S3  code                 candidates: runs of up to three words of one sentence that the other lacks
  S4a model, effort low    per candidate: counterpart words, whose words, kind of difference (prompts/gate.md)
  S5a code                 the reporter's attitude / name / certainty differences with a counterpart; word lists hide
                           what is no difference and show what is one; the A-G letter from lists and parts of speech;
                           one item per difference found from both sentences
  S4b model, effort medium per item: whether the two expressions name the same thing, which is stronger, whether a
                           reader notices it (prompts/judge.md)
  S5b code                 shown items, at most two per sentence pair, one per source span and comparison article
  S6  code                 one explanation per item from a template

`analyze(articles, ask)` takes articles keyed 'S' (the source) and 'A1'.. ({title, body, sentences, publisher, url,
published_at}, sentences with text / start / end / sentence_index) and `ask(step, prompt, schema, effort) -> dict`.
No database writes; the only outside calls are `ask`.
"""

from __future__ import annotations

import hashlib
import math
import re
from collections import Counter
from concurrent.futures import ThreadPoolExecutor
from functools import lru_cache
from pathlib import Path
from typing import Callable

from . import lexicon as X

PROMPTS = Path(__file__).resolve().parent / "prompts"
SAME_BRIEF = (PROMPTS / "same.md").read_text(encoding="utf-8")
GATE_BRIEF = (PROMPTS / "gate.md").read_text(encoding="utf-8")
JUDGE_BRIEF = (PROMPTS / "judge.md").read_text(encoding="utf-8")
EFFORT = {"s2": "low", "s4a": "low", "s4b": "medium"}
PAIRS_PER_S2_CALL, PAIRS_PER_S4A_CALL, ITEMS_PER_S4B_CALL, CALLS_AT_ONCE = 20, 6, 8, 8
PER_SENTENCE_PAIR = 2
TOP, MIN_SCORE, MIN_IDF_SCORE = 2, 0.08, 0.04

Ask = Callable[[str, str, dict, str], dict]

# ---------- S0 ----------

CONTENT_TAGS = ("NNG", "NNP", "VV", "VA", "MAG", "XR", "NR", "SN", "SL", "SH", "MM")
NAME_OR_NUMBER = {"NNP", "NR", "SN", "SL", "SH", "MM"}
SPEECH = re.compile(r"[“\"][^“”\"]*[”\"]")
NAMING = re.compile(r"['‘’]|이른바|소위|일명")
QUOTE_EC = {"다고", "라고", "냐고", "느냐고", "자고", "다며", "라며", "냐며", "느냐며", "자며", "다면서", "라면서", "냐면서", "자면서"}
_kiwi = None


def kiwi():
    global _kiwi
    if _kiwi is None:
        from kiwipiepy import Kiwi
        _kiwi = Kiwi()
    return _kiwi


def eojeols(text: str) -> list[dict]:
    """Whitespace words with their offsets, Kiwi content words (tag suffixes like VV-R dropped), whether they sit in
    double quotes, whether they end a clause (a connective), their last tag, and whether they hold a predicate.

    Kept per sentence text: a sentence sits in many pairs, and analysing it once instead of once per pair took a
    large event from about 11 s to under 1 s. The word dicts are shared, so callers only read them."""
    return list(_eojeols(text))


@lru_cache(maxsize=4096)
def _eojeols(text: str) -> tuple[dict, ...]:
    quoted = [(m.start(), m.end()) for m in SPEECH.finditer(text)]
    tokens = kiwi().tokenize(text)
    out = []
    for m in re.finditer(r"\S+", text):
        start, end = m.start(), m.end()
        toks = [t for t in tokens if start <= t.start < end]
        content = [t for t in toks if t.tag.split("-")[0] in CONTENT_TAGS]
        inside = any(a <= start and end <= b for a, b in quoted) or (content and all(
            any(a <= t.start and t.start + t.len <= b for a, b in quoted) for t in content))
        final = next((t for t in reversed(toks) if not t.tag.startswith("S")), None)
        ends_clause = final is not None and final.tag == "EC"
        out.append({"start": start, "end": end, "text": m.group(), "content": [t.form for t in content],
                    "tags": [t.tag.split("-")[0] for t in content], "quoted": bool(inside), "ends_clause": ends_clause,
                    "ending": final.form if ends_clause else "", "final_tag": final.tag if final else "",
                    "pred": any(t.tag.split("-")[0] in ("VV", "VA", "XSV", "XSA") for t in toks)})
    return tuple(out)


# ---------- S1 ----------

NUM = re.compile(r"\d[\d,.]*")


def units(articles: dict) -> dict:
    """'S#3', 'A2#5' -> (tag, sentence index, text): body sentences with ten Hangul letters or more."""
    return {f"{tag}#{s['sentence_index']}": (tag, s["sentence_index"], s["text"])
            for tag, a in articles.items() for s in a["sentences"] if len(re.findall(r"[가-힣]", s["text"])) >= 10}


def _grams(text: str) -> set:
    t = re.sub(r"[^\w]", "", text)
    return {t[i:i + 2] for i in range(len(t) - 1)}


def _number_bonus(a: str, b: str) -> float:
    na, nb = set(NUM.findall(a)), set(NUM.findall(b))
    return 0.3 * len(na & nb) / len(na | nb) if na or nb else 0.0


def _top_pairs(sentences: dict, sim: Callable[[str, str], float], minimum: float) -> dict:
    by_article: dict[str, list[str]] = {}
    for k, v in sentences.items():
        by_article.setdefault(v[0], []).append(k)
    found = {}
    for k, v in sentences.items():
        for other, keys in by_article.items():
            if other == v[0]:
                continue
            for s, j in sorted(((sim(k, j), j) for j in keys), reverse=True)[:TOP]:
                if s >= minimum:
                    pair = tuple(sorted((k, j)))
                    found[pair] = max(found.get(pair, 0.0), s)
    return found


def aligned(sentences: dict) -> list[tuple[str, str]]:
    """(source sentence, comparison sentence): for each sentence and each other article, the two most similar
    sentences by letter-pair overlap and by idf-weighted overlap; identical sentences have nothing to compare."""
    g = {k: _grams(v[2]) for k, v in sentences.items()}
    df = Counter(b for gs in g.values() for b in gs)
    n = len(g)
    w = {k: {b: math.log(n / df[b]) for b in gs} for k, gs in g.items()}
    norm = {k: math.sqrt(sum(x * x for x in v.values())) or 1.0 for k, v in w.items()}

    def plain(a, b):
        return (len(g[a] & g[b]) / len(g[a] | g[b]) if g[a] and g[b] else 0.0) + _number_bonus(sentences[a][2], sentences[b][2])

    def idf(a, b):
        return sum(w[a][x] * w[b][x] for x in g[a] & g[b]) / (norm[a] * norm[b]) + _number_bonus(sentences[a][2], sentences[b][2])

    found = set(_top_pairs(sentences, plain, MIN_SCORE)) | set(_top_pairs(sentences, idf, MIN_IDF_SCORE))
    pairs = {(a, b) if a.startswith("S#") else (b, a) for a, b in found
             if a.startswith("S#") != b.startswith("S#") and sentences[a][2] != sentences[b][2]}
    return sorted(pairs, key=lambda p: (int(p[0][2:]), p[1]))


# ---------- schemas ----------

def _obj(**props) -> dict:
    return {"type": "object", "properties": props, "required": list(props), "additionalProperties": False}


def _enum(*values) -> dict:
    return {"type": "string", "enum": list(values)}


_S = {"type": "string"}
SAME_SCHEMA = _obj(pairs={"type": "array", "items": _obj(
    pair=_S, actor=_enum("same", "different", "missing"), act=_enum("same", "partial", "different"),
    scene=_enum("same", "different", "unknown"), kind_l=_enum("fact", "forecast", "interpretation", "ought"),
    kind_r=_enum("fact", "forecast", "interpretation", "ought"))})
GATE_SCHEMA = _obj(pairs={"type": "array", "items": _obj(pair=_S, candidates={"type": "array", "items": _obj(
    id=_S, cp={"type": "array", "items": _S}, who=_enum("rep", "quote", "unclear"),
    kind=_enum("other", "fact", "syn", "att", "name", "cert"))})})
JUDGE_SCHEMA = _obj(items={"type": "array", "items": _obj(n={"type": "integer"}, same=_enum("y", "n"),
                                                           str=_enum("1", "2", "sim"), seen=_enum("y", "n"))})


def _in_parallel(fn, jobs: list) -> list:
    with ThreadPoolExecutor(CALLS_AT_ONCE) as pool:
        return list(pool.map(fn, jobs))


# ---------- S2 ----------

def s2(sentences: dict, pairs: list, ask: Ask) -> dict:
    batches = [pairs[k:k + PAIRS_PER_S2_CALL] for k in range(0, len(pairs), PAIRS_PER_S2_CALL)]

    def one(batch):
        text = "\n\n".join(f"### P{n}\nL: {sentences[a][2]}\nR: {sentences[b][2]}" for n, (a, b) in enumerate(batch, 1))
        answer = ask("s2", f"{SAME_BRIEF}\n\n# 짝\n{text}", SAME_SCHEMA, EFFORT["s2"])
        got = {x["pair"]: x for x in answer.get("pairs", [])}
        return {pair: got.get(f"P{n}") for n, pair in enumerate(batch, 1)}

    routes = {}
    for part in _in_parallel(one, batches):
        for pair, x in part.items():
            if not x:
                continue
            kinds = {x["kind_l"], x["kind_r"]}
            # The same act as a fact and as a forecast is a certainty difference; a forecast or demand beside a fact
            # that only shares part of it is another proposition.
            same = (x["act"] in ("same", "partial") and x["actor"] != "different" and x["scene"] != "different"
                    and not (x["act"] == "partial" and len(kinds) > 1 and kinds & {"forecast", "ought"}))
            named = NAMING.search(sentences[pair[0]][2]) and NAMING.search(sentences[pair[1]][2])
            route = "event" if same else "name" if named else None
            if route:
                routes[pair] = route
    return routes


# ---------- S3 ----------

def has_word(word: dict, other: str) -> bool:
    core = "".join(word["content"])
    return not core or core in other or any(len(c) >= 2 and c in other for c in word["content"])


def candidates(left: str, right: str) -> tuple[list[dict], list[dict], list[dict]]:
    words = {"L": eojeols(left), "R": eojeols(right)}
    other = {"L": right, "R": left}
    groups = []
    for side in ("L", "R"):
        run = []
        for i, w in enumerate(words[side]):
            keep = (not w["quoted"] and w["content"] and not has_word(w, other[side])
                    and not set(w["tags"]) <= NAME_OR_NUMBER and not set(w["content"]) <= X.STOP)
            if keep and len(run) < 3 and (not run or (run[-1] == i - 1 and not words[side][run[-1]]["ends_clause"])):
                run.append(i)
                continue
            if run:
                groups.append((side, run))
            run = [i] if keep else []
        if run:
            groups.append((side, run))
    cands = [{"id": f"C{n}", "side": side, "words": idx, "text": " ".join(words[side][i]["text"] for i in idx)}
             for n, (side, idx) in enumerate(groups, 1)]
    return words["L"], words["R"], cands


# ---------- S4a ----------

def _named_candidate(c: dict, lw: list, rw: list) -> bool:
    words = lw if c["side"] == "L" else rw
    return bool(NAMING.search(" ".join(words[i]["text"] for i in range(max(0, c["words"][0] - 1), c["words"][-1] + 1))))


def s4a(sentences: dict, routes: dict, ask: Ask) -> dict:
    prepared = {}
    for pair, route in routes.items():
        lw, rw, cands = candidates(sentences[pair[0]][2], sentences[pair[1]][2])
        if route == "name":
            cands = [c for c in cands if _named_candidate(c, lw, rw)]
        if cands:
            prepared[pair] = (lw, rw, cands)
    by_source = {}
    for pair in prepared:
        by_source.setdefault(pair[0], []).append(pair)
    batches = [group[k:k + PAIRS_PER_S4A_CALL] for group in by_source.values()
               for k in range(0, len(group), PAIRS_PER_S4A_CALL)]

    def block(n, pair):
        lw, rw, cands = prepared[pair]
        lines = [f"### P{n}" + (" (이름만)" if routes[pair] == "name" else ""),
                 "L: " + " ".join(f"L{i + 1}:{w['text']}" for i, w in enumerate(lw)),
                 "R: " + " ".join(f"R{i + 1}:{w['text']}" for i, w in enumerate(rw)), "후보:"]
        lines += [f"- {c['id']} ({c['side']}{c['words'][0] + 1}" + (f"-{c['side']}{c['words'][-1] + 1}" if len(c["words"]) > 1 else "")
                  + f") «{c['text']}»" for c in cands]
        return "\n".join(lines)

    def one(batch):
        text = "\n\n".join(block(n, pair) for n, pair in enumerate(batch, 1))
        answer = ask("s4a", f"{GATE_BRIEF}\n\n# 짝과 후보\n{text}", GATE_SCHEMA, EFFORT["s4a"])
        got = {x["pair"]: {c["id"]: c for c in x["candidates"]} for x in answer.get("pairs", [])}
        return {pair: got.get(f"P{n}", {}) for n, pair in enumerate(batch, 1)}

    answers = {}
    for part in _in_parallel(one, batches):
        answers.update(part)
    for pair, (_, _, cands) in prepared.items():
        for c in cands:
            c["gate"] = answers.get(pair, {}).get(c["id"])
    return prepared


# ---------- S5a ----------

UNITS = {"포인트", "퍼센트", "p", "원", "만원", "억", "조", "명", "건", "가구", "배", "년", "개월", "월", "일", "시간", "회",
         "차례", "대", "위", "세", "살", "층", "평", "톤"}
ARGUMENT = ("JKO", "JKS", "JKG", "JKB", "JKC", "JX")
B_NOUNS = {"발언", "막말", "망언", "폭언", "실언", "궤변", "망발", "해명", "변명", "항변", "입장", "주장", "지적", "언급", "답변",
           "반박", "고백", "설명", "비판"}


def _forms(words: list, ids: list) -> list:
    return [f for i in ids for f in words[i]["content"]]


def _text(words: list, ids: list) -> str:
    return " ".join(words[i]["text"] for i in ids)


def _pred_forms(words: list, ids: list) -> list:
    return [f for i in ids if words[i]["pred"] for f, t in zip(words[i]["content"], words[i]["tags"])
            if t in ("NNG", "VV", "VA", "XR")]


def numeric(w: dict) -> bool:
    """A word that is only a number with its unit; a number with 뿐 or a degree word is not."""
    return (bool(w["content"]) and all(t in ("SN", "NR") or f in UNITS for f, t in zip(w["content"], w["tags"]))
            and "뿐" not in w["text"] and not X.d_word(w["text"]))


def _counterpart_words(ids: list, words: list) -> list:
    """Not stop words only («대한»), not the predicate of a reported clause («유포했다며»), not a bare number; names
    stay (a naming comparison compares them)."""
    def finite(w):
        return w["pred"] and w["final_tag"] not in ("ETM", "EC")
    return [i for i in ids if (finite(words[i]) or not set(words[i]["content"]) <= X.STOP)
            and words[i]["ending"] not in QUOTE_EC and not numeric(words[i])]


def counterpart_run(ids: list, words: list) -> list:
    ids = [i for i in sorted(set(ids)) if not words[i]["quoted"] and words[i]["content"]]
    runs, run = [], []
    for i in ids:
        if run and i != run[-1] + 1:
            runs.append(run)
            run = []
        run.append(i)
    if run:
        runs.append(run)
    return (max(runs, key=len) if runs else [])[:3]


def trim(words: list, ids: list) -> list:
    """A reporting verb sheds its arguments («개혁의 불가피성을 강조했다» -> «강조했다»); «반응을 보였다» stays whole."""
    last = words[ids[-1]]
    if len(ids) < 2 or not last["pred"] or last["final_tag"] == "ETM" or X.a_group(last["text"], last["content"]) is None:
        return ids
    tail = [ids[-1]]
    for i in reversed(ids[:-1]):
        if words[i]["final_tag"] in ARGUMENT:
            break
        tail.insert(0, i)
    return tail


def word_list(this_w: list, this_ids: list, other_w: list, other_ids: list) -> tuple | None:
    """('hide',) / ('show', code, 'this' | 'other') from the word lists, or None when they say nothing."""
    tt, ot = _text(this_w, this_ids), _text(other_w, other_ids)
    tf, of = _forms(this_w, this_ids), _forms(other_w, other_ids)
    if X.d_word(tt) != X.d_word(ot):
        return "show", "D", "this" if X.d_word(tt) else "other"
    ga, gb = X.a_group(tt, tf), X.a_group(ot, of)
    predicate = any(this_w[i]["pred"] for i in this_ids) or any(other_w[i]["pred"] for i in other_ids)
    if ga is not None and gb is not None and predicate:
        if max(ga, gb) < 2 and X.verb_only(tf) and X.verb_only(of):
            return ("hide",)
        if {ga, gb} == {0, 2}:
            return "show", "A", "this" if ga > gb else "other"
    ma, mb = X.g_mode(tt), X.g_mode(ot)
    if ma is not None and mb is not None:
        if ma == mb:
            return ("hide",)
        return "show", "G", "this" if ma > mb else "other"
    if X.plain_predicates(_pred_forms(this_w, this_ids), tt) and X.plain_predicates(_pred_forms(other_w, other_ids), ot):
        return ("hide",)
    if X.plans_only(tf, of):  # 방침이다 / 예정이다: the same plan
        return ("hide",)
    return None


def code_letter(kind: str, this_w: list, this_ids: list, other_w: list, other_ids: list) -> str:
    tt, ot = _text(this_w, this_ids), _text(other_w, other_ids)
    tf, of = _forms(this_w, this_ids), _forms(other_w, other_ids)
    if kind == "name":
        return "F"
    if kind == "cert" or X.g_mode(tt) is not None or X.g_mode(ot) is not None:
        return "G"
    if X.d_word(tt) or X.d_word(ot):
        return "D"
    predicate = any(this_w[i]["pred"] for i in this_ids) or any(other_w[i]["pred"] for i in other_ids)
    if X.a_group(tt, tf) is not None or X.a_group(ot, of) is not None or (set(tf) | set(of)) & B_NOUNS:
        return "A" if predicate else "B"
    return "C" if predicate else "E"


def gate(c: dict, lw: list, rw: list, route: str) -> dict | None:
    g = c.get("gate")
    if not g or g["who"] != "rep" or g["kind"] not in ("att", "name", "cert"):
        return None
    if route == "name" and g["kind"] != "name":
        return None
    this_w, other_w = (lw, rw) if c["side"] == "L" else (rw, lw)
    other_side = "R" if c["side"] == "L" else "L"
    ids = counterpart_run(_counterpart_words([int(x[1:]) - 1 for x in g["cp"] if re.fullmatch(r"[LR]\d+", x)
                                              and x[0] == other_side and 0 < int(x[1:]) <= len(other_w)], other_w), other_w)
    if not ids:
        return None
    this_ids, ids = trim(this_w, c["words"]), trim(other_w, ids)
    if all(numeric(this_w[i]) for i in this_ids):
        return None
    rule = word_list(this_w, this_ids, other_w, ids)
    if rule and rule[0] == "hide":
        return None
    source_ids, target_ids = (this_ids, ids) if c["side"] == "L" else (ids, this_ids)
    item = {"source_ids": source_ids, "target_ids": target_ids, "kind": g["kind"], "by_list": bool(rule),
            "code": rule[1] if rule else code_letter(g["kind"], this_w, this_ids, other_w, ids)}
    if rule:
        item["marked_side"] = "source" if (rule[2] == "this") == (c["side"] == "L") else "target"
    return item


def _merge(items: list) -> list:
    kept = []
    for x in sorted(items, key=lambda x: (not x["by_list"], x["a1"] - x["a0"] + x["b1"] - x["b0"])):
        if not any(y["pair"] == x["pair"] and y["a0"] < x["a1"] and x["a0"] < y["a1"] and y["b0"] < x["b1"]
                   and x["b0"] < y["b1"] for y in kept):
            kept.append(x)
    return kept


# ---------- S4b ----------

AUXILIARY = ("그쳤", "그친", "그치", "달했", "달한", "달하", "머물", "머문")


def _bracket(sentence: str, a: int, b: int) -> str:
    if sentence[a:b].startswith(AUXILIARY) and a > 0:
        a = sentence.rfind(" ", 0, a - 1) + 1
        if sentence[a:a + 2] == "데 " and a > 0:
            a = sentence.rfind(" ", 0, a - 1) + 1
    return f"{sentence[:a]}[[{sentence[a:b]}]]{sentence[b:]}"


def s4b(items: list, ask: Ask) -> None:
    todo = sorted(items, key=lambda x: (x["source_start"], x["target"], x["b0"]))
    for x in todo:
        x["flip"] = int(hashlib.sha256(f"{x['source_text']}|{x['target_text']}".encode("utf-8")).hexdigest(), 16) % 2 == 1
    batches = [todo[k:k + ITEMS_PER_S4B_CALL] for k in range(0, len(todo), ITEMS_PER_S4B_CALL)]

    def one(batch):
        blocks = []
        for n, x in enumerate(batch, 1):
            src = _bracket(x["source_sentence"], x["a0"], x["a1"])
            tgt = _bracket(x["target_sentence"], x["b0"], x["b1"])
            first, second = (tgt, src) if x["flip"] else (src, tgt)
            blocks.append(f"### {n}\n문장 1: {first}\n문장 2: {second}")
        answer = ask("s4b", f"{JUDGE_BRIEF}\n\n# 표현 짝\n" + "\n\n".join(blocks), JUDGE_SCHEMA, EFFORT["s4b"])
        got = {a["n"]: a for a in answer.get("items", [])}
        for n, x in enumerate(batch, 1):
            x["judge"] = got.get(n)

    _in_parallel(one, batches)
    for x in todo:
        j = x.get("judge")
        if x["by_list"]:
            x["shown"] = not (j and j.get("same") == "n")
        elif not j or j.get("same") == "n" or j["str"] == "sim" or j["seen"] != "y":
            x["shown"] = False
        else:
            x["shown"] = True
            x["marked_side"] = "source" if (j["str"] == "1") == (not x["flip"]) else "target"


# ---------- S6 ----------

EXPLAIN = {
    "A": "발언을 전하는 동사에서 {m} 쪽이 말한 사람의 태도를 더 드러내는 말을 골랐습니다.",
    "B": "발언을 가리키는 말에서 {m} 쪽에 평가가 더 실려 있습니다.",
    "C": "같은 행위를 서술하면서 {m} 쪽이 더 센 말을 골랐습니다.",
    "D": "같은 수치나 정도를 전하면서 {m} 쪽이 크기를 더 강조합니다.",
    "E": "같은 장면을 전하면서 {m} 쪽에 평가나 감정이 담긴 말이 들어 있습니다.",
    "F": "같은 대상을 부르면서 {m} 쪽 이름에 판단이 더 담겨 있습니다.",
    "G": "확신과 출처를 나타내는 말에서 {m} 쪽이 더 단정적으로 씁니다.",
}


def explanation(x: dict, source_name: str, target_name: str) -> str:
    marked = source_name if x["marked_side"] == "source" else target_name
    return (f"{source_name} «{x['source_text'].strip(' .,')}» / {target_name} «{x['target_text'].strip(' .,')}». "
            + EXPLAIN.get(x["code"], EXPLAIN["E"]).format(m=marked))


# ---------- run ----------

def _span(words: list, ids: list) -> tuple[int, int]:
    return words[min(ids)]["start"], words[max(ids)]["end"]


def analyze(articles: dict, ask: Ask) -> list[dict]:
    """Shown comparisons: source / target spans (offsets in each article body), sentences, A-G code, marked side and
    explanation, at most two per sentence pair and one per source span and comparison article."""
    sentences = units(articles)
    routes = s2(sentences, aligned(sentences), ask)
    prepared = s4a(sentences, routes, ask)
    items = []
    for pair, (lw, rw, cands) in prepared.items():
        tag, s_index, r_index = pair[1].split("#")[0], int(pair[0][2:]), int(pair[1].split("#")[1])
        s_sent = next(s for s in articles["S"]["sentences"] if s["sentence_index"] == s_index)
        r_sent = next(s for s in articles[tag]["sentences"] if s["sentence_index"] == r_index)
        for c in cands:
            item = gate(c, lw, rw, routes[pair])
            if not item:
                continue
            a0, a1 = _span(lw, item["source_ids"])
            b0, b1 = _span(rw, item["target_ids"])
            item.update(pair=pair, target=tag, a0=a0, a1=a1, b0=b0, b1=b1,
                        source_start=s_sent["start"] + a0, source_end=s_sent["start"] + a1,
                        target_start=r_sent["start"] + b0, target_end=r_sent["start"] + b1,
                        source_text=s_sent["text"][a0:a1], target_text=r_sent["text"][b0:b1],
                        source_sentence=s_sent["text"], target_sentence=r_sent["text"])
            items.append(item)
    items = _merge(items)
    s4b(items, ask)
    per_pair, kept = Counter(), []
    for x in sorted((x for x in items if x["shown"]),
                    key=lambda x: (not x["by_list"], x["source_end"] - x["source_start"], x["source_start"])):
        if per_pair[x["pair"]] < PER_SENTENCE_PAIR:
            per_pair[x["pair"]] += 1
            kept.append(x)
    unique = []
    for x in sorted(kept, key=lambda x: (x["source_end"] - x["source_start"], x["source_start"])):
        if not any(y["target"] == x["target"] and y["source_start"] < x["source_end"] and x["source_start"] < y["source_end"]
                   for y in unique):
            unique.append(x)
    unique.sort(key=lambda x: (x["source_start"], x["target"]))
    names = {tag: a.get("publisher") or tag for tag, a in articles.items()}
    for x in unique:
        x["explanation"] = explanation(x, names["S"], names[x["target"]])
    return unique

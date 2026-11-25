# Economic lens

| Field | Value |
| --- | --- |
| Status | Implemented; formal intent review not recorded |
| Owner | Analysis |
| Clients | Web and Android |
| Test specification | `specs/testing/economic-lens.spec.md` |

## Purpose

Economy articles about one event (a rate decision, a data release, a market move) often agree on the facts and differ
in what they put in front of them: one says the rate cut will revive consumption, another that it will stir house
prices; one headline says delinquency fell, another that it hit a ten-year high for the month. Next to the A-G
expression highlights, the economic lens shows two things:

1. the event, as a keyword (`한은 기준금리 2.50%로 인하`, `은행 대출 연체율 0.59%`);
2. how this article sees it: one phrase in the article's own words, usually from its headline, with how it sees the
   event (concern, optimism, limited effect, or the rise, fall or swing it reports); a second phrase only when it adds
   another evaluation.

The other outlets on the same event are listed with the same two things. The lens does not judge whether an article
is right or biased.

## Where it comes from

The lens is produced offline by the economic pipeline and reviewed before import; the API never computes it and never
calls a model for it. Code does the work: the event keyword is cut from the clause most headlines share; the view is
the headline clause (else a subtitle, else an evaluated sentence of the first paragraphs), with its stance from
explicit evaluative words only and its direction from change words. A model only answers the closed questions the
sentence rules leave open.

## Categories

`ECONOMY` is a third news category (경제). The feed tabs, search filters and section navigation of the Web and Android
clients include it.
Only economy clusters and their articles may carry the lens; the importer rejects it elsewhere.

## Stored data

`Article.economic_context` (nullable JSON):

```json
{
  "event": "한은 기준금리 2.50%로 인하",
  "views": [
    {"channels": ["macro_growth"], "stance": "OPTIMISM", "direction": null, "phrase": "소비·투자 살린다",
     "sentence": {"scope": "TITLE", "start": 0, "end": 39, "text": "..."}}
  ]
}
```

| Field | Values |
| --- | --- |
| channel | `fx`, `capital_market`, `household_credit`, `housing`, `macro_growth`, `prices`, `trade`, `corporate`, `fiscal`, `labor`, `credit_quality` (loan delinquency and soundness), `monetary_policy` (policy stance such as tightening) |
| stance | `CONCERN`, `OPTIMISM`, `LIMITED`, `OBSERVED` (a change or fact reported with no evaluative word) |
| direction | `UP`, `DOWN`, `SWING` or `null`; given for `OBSERVED` views that state a change |

One or two views per article. The channels are stored for review and analysis; the clients do not show them. The
sentence offsets are Unicode code points in the stored title or body, and the phrase is words of that sentence. The
importer rejects a lens with no event, no view or more than two, an unknown code, a sentence that does not match its
offsets, or a phrase that is not in its sentence; the import is atomic.

### Position

A lens may also carry `position`: the article's stance toward the event and one reviewed summary line.

```json
"position": {"stance": "FAVOR", "summary": "20년간 유예로 제도가 무력화됐다며 유예 없는 부과에 찬성함"}
```

| stance | meaning | Android |
| --- | --- | --- |
| `FAVOR` | supports or urges the decision or policy | 찬성 |
| `AGAINST` | opposes it, or asks to withdraw, defer or repeal it | 반대 |
| `CONCERN` | stresses its harms or risks without opposing it; for a market or economic development, its risks | 우려 |
| `OPTIMISM` | for a market or economic development, its positive effects | 기대 |
| `NEUTRAL` | reports facts, or quotes others without taking a side | 중립 |

The stance follows target-specific stance detection (SemEval-2016 Task 6: favor / against / none toward a stated
target); `FAVOR` and `AGAINST` apply to decisions and policies, `OPTIMISM` and `CONCERN` to developments nobody is for
or against. The summary is at most 120 characters. A lens with a position may have no views.

## Reader API

`GET /api/v1/articles/{id}/analysis` adds `economic`:

```json
{
  "event": "한은 기준금리 2.50%로 인하",
  "views": [{"channels": ["macro_growth"], "stance": "OPTIMISM", "direction": null, "phrase": "...", "sentence": {...}}],
  "others": [
    {"article_id": "uuid", "publisher": {"code": "yna", "name": "연합뉴스", "logo_url": null}, "title": "...",
     "views": [{"channels": ["housing", "household_credit"], "stance": "CONCERN", "...": "..."}]}
  ]
}
```

It is `null` when the article has no lens. `others` are the other visible articles of the cluster (active publishers)
that carry a lens, newest first. Views whose sentence no longer matches the stored article are left out. The lens and
each outlet also carry `position` (an object as above, or `null`); an outlet with a position and no views is listed.

## How a view is labelled

| stance / direction | Web | Android |
| --- | --- | --- |
| `CONCERN` | Concern | 우려 |
| `OPTIMISM` | Optimism | 기대 |
| `LIMITED` | Limited effect | 제한적 |
| `OBSERVED` + `UP` / `DOWN` / `SWING` | Rise / Fall / Swing | 상승 / 하락 / 등락 |
| `OBSERVED` + `null` | Neutral | 중립 |

## Web display

- Header, under the publisher line: an Economic lens box with the event and this article's views (phrase and label).
- Title and body: each view sentence is underlined in teal, and its phrase is marked more strongly. A-G markers keep
  their amber highlight inside them.
- Analysis rail, below the A-G findings: "How other outlets see this event", each other outlet (linked to its
  article) with its views.

## Android display

- Reader, under the analysis notice: a "경제 관점" card with the event (사건) and this article's view (이 기사의 시각):
  the position's stance label and summary when it has one, otherwise its views.
- Title and body: with 표현 분석 on, each view sentence is underlined and its phrase is tinted.
- Below the body: "다른 언론사는 이 사건을 이렇게 봐요", a table of the other outlets, each row the publisher and its
  stance label with the summary (its views when it has no position); tapping a row opens its article (back returns to
  this one).

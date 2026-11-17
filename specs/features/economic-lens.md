# Economic lens

| Field | Value |
| --- | --- |
| Status | Draft for review |
| Owner | Analysis |
| Clients | Web (Android keeps POLITICS / SOCIETY) |
| Test specification | `specs/testing/economic-lens.spec.md` |

## Purpose

Economy articles about one event (a rate decision, an exchange-rate move) often agree on the facts and differ in how
they see the event: one says the rate cut will revive consumption, another that it will stir house prices, a third
that its effect on house prices will be small. The economic lens shows, next to the A-G expression highlights:

1. the event, as the articles name it (`한은, 기준금리 2.50%로 0.25%p 인하`);
2. this article's view of it: one or two phrases in the article's own words, each with the economic channel it
   concerns and its stance (`부동산 «집값엔 큰 영향을 주지 않을 것» Limited effect`);
3. how the other outlets on the same event see it, with a mark where one takes another stance on the same channel.

It does not judge whether an article is right or biased, and it shows nothing else (no source counts, no numbers).

## Where it comes from

The lens is produced offline by the economic pipeline and reviewed before import; the API never computes it and never
calls a model for it. Code does the work: channel, time, modality, causal and tone word lists and rules decide each
sentence, and a model only answers the closed questions the rules leave open. The event span and the view phrases are
cut by code from the articles' sentences.

## Categories

`ECONOMY` is a third news category. The feed, search filters and section navigation of the Web client include it.
Only economy clusters and their articles may carry the lens; the importer rejects it elsewhere.

## Stored data

`Article.economic_context` (nullable JSON):

```json
{
  "event": "한은, 기준금리 2.50%로 0.25%p 인하",
  "views": [
    {"channels": ["housing"], "stance": "LIMITED", "direction": null, "phrase": "집값엔 큰 영향을 주지 않을 것",
     "sentence": {"scope": "BODY", "start": 138, "end": 220, "text": "..."}}
  ]
}
```

| Field | Values |
| --- | --- |
| channel | `fx`, `capital_market`, `household_credit`, `housing`, `macro_growth`, `prices`, `trade`, `corporate`, `fiscal`, `labor` |
| stance | `CONCERN`, `OPTIMISM`, `LIMITED`, `OBSERVED` (a change reported with no evaluative word) |
| direction | `UP`, `DOWN`, `SWING` or `null`; given for `OBSERVED` views that state a change |

One or two views per article. The sentence offsets are Unicode code points in the stored title or body, and the phrase
is words of that sentence. The importer rejects a lens with no event, no view or more than two, an unknown code, a
sentence that does not match its offsets, or a phrase that is not in its sentence; the import is atomic.

## Reader API

`GET /api/v1/articles/{id}/analysis` adds `economic`:

```json
{
  "event": "한은, 기준금리 2.50%로 0.25%p 인하",
  "views": [{"channels": ["housing"], "stance": "LIMITED", "direction": null, "phrase": "...", "sentence": {...}}],
  "others": [
    {"article_id": "uuid", "publisher": {"code": "yna", "name": "연합뉴스", "logo_url": null}, "title": "...",
     "views": [{"channels": ["housing", "household_credit"], "stance": "CONCERN", "...": "..."}]}
  ],
  "contrasts": [
    {"channel": "housing", "stance": "LIMITED",
     "others": [{"article_id": "uuid", "publisher": {"code": "yna", "name": "연합뉴스"}, "stance": "CONCERN"}]}
  ]
}
```

It is `null` when the article has no lens. `others` are the other visible articles of the cluster (active publishers)
that carry a lens, newest first. `contrasts` name each channel of this article's views on which another outlet takes a
different stance. Views whose sentence no longer matches the stored article are left out.

## Web display

- Header, under the publisher line: an Economic lens box with the event and this article's views (channel, phrase,
  stance).
- Title and body: each view sentence is underlined in teal, and its phrase is marked more strongly. A-G markers keep
  their amber highlight inside them.
- Analysis rail, below the A-G findings: "How other outlets see this event". It lists the contrasts first, then each
  other outlet (linked to its article) with its views. A view on a channel where that outlet differs is marked
  "Different view".

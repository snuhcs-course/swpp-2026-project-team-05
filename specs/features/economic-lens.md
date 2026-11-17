# Economic lens

| Field | Value |
| --- | --- |
| Status | Draft for review |
| Owner | Analysis |
| Clients | Web (Android keeps POLITICS / SOCIETY) |
| Test specification | `specs/testing/economic-lens.spec.md` |

## Purpose

Economy articles about one event (a rate decision, an exchange-rate move) often agree on the facts and differ in what
they put first. One article leads with consumption and investment, another with the exchange rate, a third with house
prices. The economic lens shows, next to the A-G expression highlights, which economic channel each article puts
first, the tone in which it reports that channel's effect, whose words it uses, and which numbers it chooses. It does
not judge whether an article is right or biased.

## Where it comes from

The lens is produced offline by the economic pipeline (code first: channel, time, modality, causal and tone word lists
and rules; a model answers only closed questions the rules leave open), reviewed, and imported with the cluster. The
API never computes it and never calls a model for it.

## Categories

`ECONOMY` is a third news category. The feed, search filters and section navigation of the Web client include it.
Only economy clusters and their articles may carry the lens; the importer rejects it elsewhere.

## Stored data

- `Article.economic_context` (nullable JSON): `focus` (channel codes, front-page channels first), `tone` (channel →
  tone), `evidence` (sentences to highlight), `sources` (source code → count), `numbers`.
- `StoryCluster.economic_comparison` (nullable JSON): rows of type `FOCUS`, `TONE`, `SOURCES` or `NUMBERS`, each with
  sides that name a stored article id.

Codes:

| Field | Values |
| --- | --- |
| channel | `fx`, `capital_market`, `household_credit`, `housing`, `macro_growth`, `prices`, `trade`, `corporate`, `fiscal`, `labor` |
| tone | `CONCERN`, `OPTIMISM`, `LIMITED`, `OBSERVED` (an effect reported with no evaluative word) |
| role | `EFFECT_REALIZED`, `EFFECT_FORECAST` |
| voice | `QUOTE`, `UNATTRIBUTED`, `REPORTER` |
| source | `BANK_OF_KOREA`, `GOVERNMENT`, `FOREIGN_AUTHORITY`, `EXPERT`, `INDUSTRY`, `ANONYMOUS` |
| reason | `FOCUS` (front sentence of a focus channel), `FOCUS_TONE` (shows the article's tone on it), `TONE_CONTRAST` (the article's side of a tone difference) |

An evidence or comparison sentence is `{scope: TITLE|BODY, start, end, text}` with Unicode code-point offsets into the
stored title or body. The importer rejects a lens whose sentence text does not match its offsets, an unknown code, or a
comparison side naming an article outside the cluster; the import is atomic.

## Reader API

`GET /api/v1/articles/{id}/analysis` adds `economic`:

```json
{
  "focus": ["housing", "household_credit"],
  "tone": {"housing": "LIMITED", "household_credit": "LIMITED"},
  "evidence": [
    {"sentence": {"scope": "BODY", "start": 138, "end": 220, "text": "..."}, "channels": ["housing"],
     "role": "EFFECT_FORECAST", "tone": "LIMITED", "voice": "UNATTRIBUTED", "source": "ANONYMOUS",
     "reasons": ["TONE_CONTRAST", "FOCUS_TONE"]}
  ],
  "sources": {"EXPERT": 8, "ANONYMOUS": 5},
  "numbers": [{"indicator": "가계대출", "value": "747조2423억원", "basis": [], "kind": "LEVEL"}],
  "comparison": [
    {"type": "TONE", "channel": "housing", "sides": [
      {"article_id": "uuid", "publisher": {"code": "yna", "name": "연합뉴스", "logo_url": null}, "title": "...",
       "is_current": false, "tone": "CONCERN", "sentence": {"scope": "BODY", "start": 0, "end": 40, "text": "..."}}
    ]}
  ]
}
```

It is `null` when the article has no lens. Evidence whose text no longer matches the stored article is left out, and
comparison sides of hidden articles or inactive publishers are left out.

## Web display

- Header: `Economic lens · <focus channels>` and `Tone · <tone>` under the publisher line.
- Body and title: evidence sentences are underlined in teal; A-G markers keep their amber highlight inside them.
  Selecting an underlined sentence shows it in the Economic lens panel.
- Analysis rail: below the A-G findings, the selected sentence (channels, tone, expected or reported effect, voice
  and source) and the other outlets' sentences on the same channel when their tone differs, then "Across this
  event": what each outlet puts first, same effect with different tone, who is quoted, and numbers per indicator.

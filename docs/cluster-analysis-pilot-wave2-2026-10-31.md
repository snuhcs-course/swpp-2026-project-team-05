# FrameLESS Cluster Analysis Pilot — Wave 2

Date: 2026-10-31

Scope: five new politics and society clusters, reviewed against `news-app-v1-spec.md` after the Wave 1 P0 changes

## 1. Goal

Wave 2 tests whether the revised contract rejects attractive but invalid comparisons without eliminating useful article-specific analysis. Three independent agents selected new clusters. A fourth pass cross-reviewed disputed findings instead of accepting raw agent counts.

This is a manual design evaluation, not a measured model benchmark. `VERIFIED` below means suitable for a product `ExpressionFinding`; rejected and uncertain candidates remain evaluation-only.

## 2. Rules under test

- Headline and body offsets use separate scopes.
- A marker may have multiple A-G categories but produces one finding.
- Only reporter-owned, salient expressions become product findings.
- A comparison requires the exact same proposition, a meaningful and role-aligned wording contrast, and exact source and target markers.
- Reporting-verb comparisons require the same speaker and quoted content.
- Numeric claims must match value, unit, geography, incremental/cumulative scope, period, project stage, and denominator.
- Contested crime or responsibility labels must refer to the same event, time, and legal or procedural stage.
- Identical wording, compact notation versus prose, and ordinary neutral verbs are not comparison evidence.
- Zero visible findings or zero comparisons is a valid completed result.

## 3. Results

### 3.1 Capital-region public housing-site announcement

Internal v1 category: `SOCIETY`.

All nine URLs were evaluated as potential run members: [News1 initial](https://www.news1.kr/realestate/general/6335851), [News1 follow-up](https://www.news1.kr/realestate/general/6336119), [Newsis overview](https://www.newsis.com/view/NISX20261029_0003846170), [Newsis analysis](https://files.newsis.com/view/NISX20261029_0003846956), [MBC](https://imnews.imbc.com/replay/2026/nwdesk/article/6861753_37004.html), [Hankyoreh breaking](https://www.hani.co.kr/arti/economy/property/1284748.html), [Hankyoreh expanded](https://www.hani.co.kr/arti/economy/property/1284843.html), [Hankyoreh regional response](https://www.hani.co.kr/arti/area/capital/1284854.html), and [Kyunghyang](https://www.khan.co.kr/article/202610292153005). The same-publisher pairs were intentionally retained to test article membership versus one-card-per-publisher presentation; grouping is applied per finding at read time, not by deleting cluster articles.

High-confidence visible findings included Hankyoreh's `온도차가 있었다` as a source-only characterization and Newsis's `속도전에 나선다` as one `[C,E]` marker. The latter must not create duplicate findings or comparisons for its two categories.

The revised rules correctly rejected:

- additional `7만6700가구` versus cumulative `10만3900가구`;
- five cities versus six sites;
- attributed headline quotations;
- neutral `밝혔다` used for different speakers and statements;
- a source marker without an exact, same-role target marker;
- the same publisher's breaking, expanded, and revised articles as separate UI cards.

No new product column was needed. The representative same-publisher article must be selected by proposition match, context sufficiency, finality, publication time, and stable ID, in that order.

### 3.2 CSAT smart-glasses response plan

Sources included [News1](https://www.news1.kr/society/education/6336026), [Newsis](https://www.newsis.com/view/?id=NISX20261029_0003846009), [Hankyoreh](https://www.hani.co.kr/arti/society/schooling/1284765.html), and [SBS](https://news.sbs.co.kr/news/endPage.do?news_id=N1008820677).

Strict cross-review retained one high-confidence source-only finding: News1's headline `강수` `[E]`. It characterizes the intensity of the announced response.

It rejected the preliminary comparisons:

- `1년→3년` is compact policy notation, not by itself a meaningful D finding;
- `1년→3년` versus `1년에서 3년으로 늘리는 방안` is formatting versus prose, not a product-worthy contrast;
- `강수` versus `형사고발` compares an evaluation with its underlying action, so the markers do not play the same semantic role;
- `엄정 대응` remains attributed to the Ministry of Education even when several outlets repeat it.

Final high-confidence pilot result: one visible finding and zero comparisons. This is a successful conservative result, not an analysis failure.

### 3.3 Jeju stalking-retaliation murder suspect disclosure

Sources included [Yonhap](https://www.yna.co.kr/view/AKR20261029071700056), [MBC](https://imnews.imbc.com/news/2026/society/article/6861601_36918.html), [News1](https://www.news1.kr/local/jeju/6335978), and [Chosun](https://www.chosun.com/national/regional/2026/10/29/VHF4I4RXLBEN5PZARQ2AYW53WU/).

High-confidence results:

- Yonhap `스토킹 신고 여성` `[G]`, source-only: the headline foregrounds the victim's prior report history without implying additional causation.
- News1 `보복살해범` `[F,G]` and Chosun `보복 살인범` `[F,G]`: article-specific findings that contrast with Yonhap and MBC's `피의자` at the same disclosure event and procedural stage.

The comparison explanation must describe only the difference between an assertive crime label and a legally staged suspect label. It must not decide guilt or outlet bias. Effectively identical `보복살해범` and `보복 살인범` do not compare with each other. Each article still owns its own finding; findings are not deduplicated across publishers.

The pilot also rejected mismatched victim attributes, inconsistent 30-day date expressions, and differently precise incident times.

### 3.4 Supreme Court chief-justice investigation decision

Sources included [Yonhap](https://www.yna.co.kr/view/AKR20261027128452001), [YTN](https://www.ytn.co.kr/_ln/0101_202610272157345332), [MBC](https://imnews.imbc.com/replay/2026/nw930/article/6861266_36996.html), and [Kyunghyang](https://www.khan.co.kr/article/202610272157015).

After cross-review, eight visible findings and nine comparison rows remained in this cluster. Useful examples were:

- `공식화했습니다` `[G]` against three formulations of the same decision;
- `헌정사 첫` and `초유의 국조` as separate article findings reusing one shared proposition and comparing with `이번이 처음이다`;
- `절충안` against reporter-owned `대안으로` and attributed `타협점`, with attribution shown on the latter card;
- reporter-owned `역풍` and `부정적인 기류` as source-only findings.

Cross-review removed `반발했다` because it is a routine political-reaction verb, reduced overbroad category labels, and rejected procedure dates that referred to different stages and forecast articles that preceded the decision.

### 3.5 Blue House audit disruption

Sources included [Yonhap](https://www.yna.co.kr/amp/view/AKR20261029170651001), [YTN](https://www.ytn.co.kr/_ln/0101_202610292202240167), [MBC](https://imnews.imbc.com/news/2026/politics/article/6861782_36911.html), and [Newsis](https://m.newsis.com/view/NISX20261029_0003847435).

The first pass found several reporter-owned expressions, including `파행`, `아수라장`, `날 선 공방`, and `맞불을 놓으면서`, while withholding quoted partisan labels. It also correctly excluded the morning National Human Rights Commission audit and forecast articles from the afternoon Blue House audit result.

This cluster remains provisional as a golden fixture. Some long source markers combine an evaluation with the underlying event, while their proposed targets contain only the event. The new role-alignment validator must split or reject those candidates before a comparison is published. The cluster is useful as a negative regression fixture precisely because it exposed this boundary.

## 4. What changed after Wave 2

The product spec now states that:

1. only verified, displayable findings are persisted; withheld candidates stay in evaluation fixtures;
2. F is verified per category, so an unsupported F label can be removed without discarding independently valid C or E labels on the same marker;
3. D excludes raw numbers and compact numeric notation without a meaningful modifier;
4. comparisons require same-proposition, role-aligned wording contrast, not merely related sentences;
5. number validation includes denominator and incremental/cumulative scope;
6. crime and responsibility labels require the same procedural stage;
7. same-publisher article selection has a deterministic priority order;
8. reporter-written decks and internal subheadings remain in `body_text` rather than creating another span scope;
9. the example no longer uses a politically loaded assertion.

## 5. Verdict

The cluster-centered analysis and lean schema remain implementable. Wave 2 did not justify a new table or core field. The important remaining work is prompt design, deterministic validators, and fixed regression fixtures.

The strongest evidence that the design improved is not a larger output count. The smart-glasses cluster shrank from a permissive preliminary result to one source-only finding, while the Jeju disclosure cluster retained a legally and semantically grounded naming contrast. The rules therefore distinguish useful absence from pipeline failure.

Before automatic publication, the Blue House audit cluster should be rerun through an implemented role-alignment validator, and every accepted fixture should be independently reviewed once more. This pilot supports implementation; it does not claim that manual analysis or a future LLM is infallible.

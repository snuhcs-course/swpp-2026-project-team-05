# FrameLESS Cluster Analysis Design Pilot

| Field | Value |
| --- | --- |
| Date | 2026-10-31 |
| Scope | Five recent Korean politics/society story clusters |
| Articles reviewed | 20 articles from supported publishers |
| Evaluation target | Cluster-wide A-G findings, sentence mapping, database fit, and article-centered UI projection |
| I-series | Excluded; to be specified separately |

## 1. Purpose and limitation

This pilot applies the proposed cluster-centered design to recent real articles before implementation. Multiple agents reviewed different clusters, and follow-up agents challenged the proposed schema changes.

The new pipeline does not exist yet. This is a structured manual/agent simulation of its expected inputs and outputs, not a benchmark run of production code. It can expose modeling and UX failures, but it does not establish model precision, latency, or cost.

The prior 30-article Opus experiment is used as additional error evidence. In that run, both judges accepted 22 of 48 A-G displays, with major failures caused by matching different quoted statements and treating low-value synonymous wording as a meaningful difference. That result is not directly comparable to this pilot because its S0-centered pipeline and rubric differ.

## 2. Evaluation method

For each story cluster:

1. Confirm that all selected articles cover the same event, not merely the same broad topic.
2. Identify source-only A-G expression candidates in every article.
3. Exclude direct quotations and separate attributed-source language from reporter narration.
4. Group sentences only when they express the same detailed proposition.
5. Attach cross-article comparisons only when source and target are semantically comparable.
6. Project the shared cluster result onto each article's Reader view.
7. Record analysis, UI, and database failures.

The review intentionally accepts zero visible findings or zero visible comparisons as valid outcomes.

## 3. Pilot clusters

### 3.1 North Korean short-range ballistic-missile launch

Articles:

- [MBC, 2026-10-29](https://imnews.imbc.com/news/2026/politics/article/6861638_36912.html)
- [YTN, 2026-10-29](https://www.ytn.co.kr/_ln/0101_202610291431560773)
- [News1, 2026-10-29](https://www.news1.kr/nk/military/6336333)

Observed behavior:

- Source-only candidates included uncertainty markers and the expressions corresponding to `도발`, `침묵으로 일관`, and `무력시위`.
- A useful sentence group connected the timing of the launch with the earlier military-talk proposal.
- MBC's comparatively dry timeline was useful evidence for YTN/News1 findings even though it did not require its own A-G finding.
- A superficially similar `무력시위` sentence about artillery exposure concerned a different action and must not join the missile-purpose group.

Design lesson: ownership and attribution are essential. A government assessment must not be displayed as the reporter's expression.

### 3.2 Korea-Germany summit

Articles:

- [MBC, 2026-10-29](https://imnews.imbc.com/news/2026/politics/article/6861783_36911.html)
- [News1, 2026-10-29](https://www.news1.kr/politics/president/6337015)
- [SBS, 2026-10-29](https://news.sbs.co.kr/news/endPage.do?cooper=SBSNEWSAMP&news_id=N1008821928)

Observed behavior:

- Most strong wording came from direct quotations or the joint statement, not reporter narration.
- `화답했습니다` was a plausible source-only A finding when no exact comparison sentence was available.
- Broadly grouping every semiconductor, defense, energy, and digital commitment under “economic cooperation” would produce unusably vague sentence groups.
- A low or zero finding count was the correct result for some articles.

Design lesson: do not force a fixed number of findings. Routine `말했다/밝혔다/전했다` reporting verbs need a salience gate.

### 3.3 Halloween crowd-safety operations

Articles:

- [YTN, 2026-10-30](https://www.ytn.co.kr/_ln/0103_202610302353381403_001)
- [MBC, 2026-10-30](https://imnews.imbc.com/replay/2026/nw1400/article/6861998_36974.html)
- [Newsis, 2026-10-30](https://www.newsis.com/view/NISX20261030_0003849012)
- [News1, 2026-10-31](https://www.news1.kr/society/incident-accident/6338465)

Observed behavior:

- Strong findings frequently appeared in titles, including expressions corresponding to `총력전`.
- Police special-resource deployment and road/subway control formed useful multi-article sentence groups.
- A forecast of more than 30,000 visitors and an observed simultaneous crowd of 12,000–14,000 were different metrics and times. They must not be compared as a framing difference.
- MBC correctly produced no visible source-only finding rather than highlighting ordinary verbs.

Design lesson: TITLE/BODY scope is mandatory, and same-number topics need time, metric, and forecast/observation validation.

### 3.4 Incheon Airport check-in and ticketing outage

Articles:

- [News1, 2026-10-30](https://www.news1.kr/local/incheon/6337930)
- [MBC, 2026-10-30](https://imnews.imbc.com/replay/2026/nw1700/article/6862026_36981.html)
- [YTN, 2026-10-30](https://www.ytn.co.kr/_ln/0102_202610302146573132)
- [Hankyoreh, 2026-10-30](https://www.hani.co.kr/arti/area/capital/1285021.html)
- [Dong-A Ilbo, 2026-10-30](https://www.donga.com/news/amp/all/20261030/134940153/1)
- [Newsis, 2026-10-30](https://www.newsis.com/view/NISX20261030_0003849049)

Observed behavior:

- Source-only findings included title/body expressions corresponding to `마비`, `먹통`, `발 동동`, `분통`, and `가까스로 복구`.
- Neutral `장애/중단` sentences were useful comparison evidence without needing their own visible A-G finding.
- The same span could reasonably receive more than one category, such as C and E.
- Twelve/thirteen-hour outage durations and a five-hour service-interruption duration likely measured different scopes. They must not become a visible comparison.
- The same publisher had multiple updates; showing each as an independent outlet card would distort publisher diversity.

Design lesson: comparisons need exact proposition/scope validation, multiple categories per marker, and publisher-level UI grouping.

### 3.5 Itaewon disaster fourth-anniversary memorial

Articles:

- [News1, 2026-10-29](https://www.news1.kr/society/incident-accident/6336284)
- [MBC, 2026-10-29](https://imnews.imbc.com/replay/2026/nw930/article/6861560_36997.html)
- [SBS, 2026-10-29](https://news.sbs.co.kr/news/endPage.do?news_id=N1008820875)
- [Hankyoreh, 2026-10-29](https://www.hani.co.kr/arti/society/society_general/1284804.html)

Observed behavior:

- Two reporter-owned emotional descriptions were plausible source-only E findings.
- Most prominent title wording was attributed speech and could not be treated as reporter framing.
- Passage groups covered the memorial opening, the president's attendance, the state-responsibility promise, and a bereaved-family statement.
- Event-preview and post-event wording differed by time, not framing.
- All valid sentence groups produced zero displayable expression comparisons. This was a successful negative result.

Design lesson: an analysis can be READY with a few source-only findings and no comparison cards. Similar keywords must not merge the memorial, current Halloween policing, and investigation updates into one cluster.

## 4. Findings across all clusters

### 4.1 What worked

- `StoryCluster → Article → per-article findings` represented every cluster without a reference article.
- Every article could be projected independently from one shared run.
- Source-only findings and optional comparison evidence matched real editorial patterns.
- One finding could use ordinary wording from several other articles as evidence.
- Zero findings and zero comparisons were legitimate, useful negative controls.
- Keeping previously committed rows readable until an incremental update succeeds remained appropriate when cluster membership changes.

No cluster required a pairwise-analysis table or an article-level active-analysis pointer.

### 4.2 Repeated failure modes

1. Ordinary verbs and event descriptions can flood the Reader if every detected A-G category is published.
2. Direct quotations, attributed paraphrases, official statements, and reporter narration are easily confused.
3. Same-event sentences may differ in actor, action, proposition, metric, time, or scope.
4. An A reporting-verb comparison can connect different quoted statements from the same speaker.
5. Titles contain many of the strongest features but also many attributed quotations.
6. A target sentence may be semantically comparable without containing its own A-G finding.
7. Multiple categories may apply to one exact marker span.
8. Same-publisher updates can masquerade as independent outlet diversity.

## 5. Recommended changes

### 5.1 P0: change before implementation

1. Require A-G extraction to return one exact source sentence with `scope: TITLE | BODY`, offsets, and text, and store it directly on `ExpressionFinding`.
2. Classify candidate ownership before persistence. Only verified reporter-owned findings become `ExpressionFinding` rows; attributed and uncertain candidates remain evaluation-only.
3. Add required `normalized_proposition` to `SharedProposition`. It states the exact shared proposition, not a broad topic label.
4. Replace singular `ExpressionFinding.category` with a validated category array, or an equivalent join table. The lean recommendation is a PostgreSQL enum array because v1 does not query findings by category.
5. Require every `ExpressionFinding` to store one grounded source sentence. Persist a `SharedProposition` only when at least one displayable cross-article comparison exists, and store the grounded target sentence directly on each `FindingComparison`. Same-meaning candidates without a displayable contrast remain diagnostic-only.
6. Add verified `target_owner` and nullable `target_speaker_label` to the comparison marker so the UI does not attribute quoted language to the outlet. Uncertain ownership remains diagnostic-only.
7. Publish only representative findings. Grounded A-G candidates pass ownership, direct-quote, context, and salience gates before becoming `VERIFIED`.
8. For A, require the same speaker and the same quoted content before comparing reporting verbs.
9. Validate proposition, actor, action, metric, time, scope, and forecast/observation mode before persisting a comparison.
10. Persist every valid comparison, but return at most three publisher-diverse cards to the Reader.

### 5.2 Keep lean: do not add yet

- Do not add `detection_basis`; comparison-row presence already distinguishes source-only from enriched findings.
- Do not add a successful-comparison `comparability` enum. A persisted comparison means it passed equivalence validation.
- Keep rejected comparison candidates and reasons such as `DIFFERENT_TIME`, `DIFFERENT_SCOPE`, `FORECAST_VS_OBSERVED`, `CONFLICTING`, and `UNCERTAIN` in evaluation fixtures rather than product tables.
- Do not add a speaker/person ontology, salience table, or aspect taxonomy.
- Do not add same-publisher update tables in v1. Group comparison cards by publisher and choose the highest-ranked/latest valid article in the API.
- Do not store candidate or withheld findings as product rows. Every persisted `ExpressionFinding` is verified and displayable.

### 5.3 UI changes

- Support tappable markers in both title and body.
- Show `이 표현에서 드러난 특징` for source-only findings.
- Add `같은 내용을 다른 기사에서는` only when validated comparisons exist.
- Highlight both source and target markers.
- Display a small attribution label only when language belongs to an attributed source, official statement, or uncertain owner.
- Show target article publication time, especially for evolving stories.
- Group cards by publisher; multiple sentences stay inside one publisher card.
- Merge overlapping category labels into one tappable marker.
- Do not claim neutrality when no finding is displayed.
- Do not create a difference card for identical wording, a temporal update, or a scope mismatch.

## 6. Schema delta from v1.2 draft

The large structure remains unchanged. Only the following core fields change:

```text
ExpressionFinding
  article
  source_scope
  source_sentence_start/end/text
  marker_start/end/text
  categories[]
  explanation
  display_order
  shared_proposition?  # null for a source-only finding

SharedProposition
  normalized_proposition

FindingComparison
  target_article
  target_scope
  target_sentence_start/end/text
  target_marker_start/end/text   # required for a stored comparison
  target_owner
  target_speaker_label?
  comparison_summary
  display_rank
```

Candidate extraction and rejection details stay in evaluation fixtures or safe structured logs instead of new product tables.

## 7. Conclusion

The cluster-centered architecture survived all five pilots. The design does not need to return to a reference-article or pairwise model.

The main risk is not the top-level schema. It is the promotion rule from an extracted A-G candidate to a user-visible finding and the semantic-equivalence gate before creating a comparison. The P0 changes above are small, implementable additions that prevent the observed attribution, title, time, scope, and UI-overload failures without expanding v1 into a general knowledge graph or workflow platform.

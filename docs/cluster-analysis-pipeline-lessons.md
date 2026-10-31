# Cluster Analysis Pipeline Lessons

| Field | Value |
| --- | --- |
| Date | 2026-10-31 |
| Evidence | Two manual pilot waves and one reviewed production-DB fixture |
| Reviewed fixture | 5 clusters, 19 articles, 10 findings, 2 shared propositions, 3 comparisons |
| Scope | Deferred future cluster-centered A–G pipeline; not part of the v1 read experience |

## 1. Purpose and evidence boundary

This document records implementation lessons for a future automatic analysis pipeline. It is not another product specification and is not part of the current v1 implementation order. The product and schema contracts remain in [`news-app-v1-spec.md`](news-app-v1-spec.md), while the reviewed examples live in [`reviewed_news_pilot_v1.json`](../server/fixtures/reviewed_news_pilot_v1.json).

The conclusions come from manually and agent-assisted review of recent politics and society clusters. They validate the shape of the data and expose common semantic errors. They do **not** establish the precision, latency, or cost of an automatic model pipeline.

## 2. Core conclusion

The correct unit of product output is a verified expression finding on one article sentence. Cross-article comparison is optional evidence attached afterward.

```text
Story cluster
  ├─ Article
  │    └─ ExpressionFinding: exact source sentence + exact marker + A–G explanation
  └─ SharedProposition
       └─ FindingComparison: one finding → one grounded target sentence
```

The pipeline must therefore keep two decisions separate:

1. **Article analysis:** Is this expression independently meaningful and displayable in its own article?
2. **Cross-article mapping:** Does another article express the exact same proposition using a meaningfully different, role-aligned expression?

A finding can pass the first decision and fail the second. That is a valid completed result, not a pipeline failure.

## 3. Recommended pipeline

### Stage 1 — Ingest and normalize articles

- Canonicalize the URL before deduplication.
- Store the exact title and body used for analysis and calculate `content_hash`.
- Preserve title and body as separate coordinate spaces.
- Normalize publisher identity so mobile, AMP, and canonical domains do not appear as different outlets.
- Reject incomplete extraction before analysis; an approximate or truncated body must not produce offsets.

### Stage 2 — Build or update one story cluster

- Cluster by one concrete event, not a broad topic or shared keyword.
- Check actor, action, object, place, time, and event stage.
- Do not merge a preview article with a later result article merely because they share keywords.
- Do not treat multiple updates from one publisher as independent publisher diversity.
- When a new article arrives, analyze it against the current cluster and re-evaluate only affected mappings.

### Stage 3 — Extract per-article candidate findings

Run A–G candidate extraction independently for every article. There is no reference article.

Each candidate must contain:

- `source_scope`: title or body;
- the complete source sentence;
- the shortest continuous marker that preserves the expression;
- proposed A–G categories;
- a short explanation of what the wording adds;
- ownership evidence: reporter, attributed source, official statement, or uncertain.

Do not ask the model for a fixed number of findings. Zero is valid.

### Stage 4 — Apply ownership, grounding, and salience gates

A candidate becomes a product finding only when all gates pass.

1. **Ownership:** The expression belongs to reporter-authored narration. Direct quotations, attributed paraphrases, and official wording are not silently assigned to the outlet.
2. **Exact grounding:** Sentence and marker text occur exactly once in the selected title or body, and marker offsets are inside sentence offsets.
3. **Context:** The marker retains the proposed meaning in its complete sentence.
4. **Category fit:** Every category is validated independently; remove only an unsupported category rather than discarding an otherwise valid marker.
5. **Salience:** The wording adds meaningful attitude, evaluation, intensity, agency, responsibility, legality, or certainty. Ordinary reporting language is withheld.

Rejected and uncertain candidates belong in evaluation fixtures or safe diagnostics, not product tables.

### Stage 5 — Build the cluster-wide finding union

After every article has been analyzed, take the union of verified findings across the cluster. This set drives cross-article mapping.

Do not generate findings by reversing an existing comparison. A neutral target sentence can be useful comparison evidence without becoming its own finding.

### Stage 6 — Propose exact shared propositions

For a finding that may have comparison evidence, produce one neutral proposition specific enough to verify:

- actor;
- action;
- object;
- metric and denominator;
- time and event stage;
- geography and scope;
- forecast, plan, allegation, decision, or observed result.

The proposition is an internal semantic contract, not a broad topic label or user-facing summary.

### Stage 7 — Retrieve and align target sentences

Search only within other articles in the same cluster. Candidate retrieval may use embeddings or an LLM, but acceptance requires exact grounded text.

Before creating a comparison, verify:

1. the source and target express the same proposition;
2. their markers play the same semantic role;
3. the wording difference is meaningful to a reader;
4. the target marker's owner is known;
5. the target is not merely a later update, different metric, different scope, or different procedural stage.

One finding may map to several publisher-diverse target articles. All comparisons of that finding must share its one proposition.

### Stage 8 — Rank for presentation

- Persist every validated comparison.
- Return at most three publisher-diverse cards for one finding.
- Group multiple articles from the same publisher.
- Prefer exact proposition match, sufficient context, final/non-breaking coverage, later publication time, and stable ID—in that order.
- Never fill an empty carousel with merely related sentences.

### Stage 9 — Commit atomically

Network and model work occurs outside the database transaction.

Before writing:

1. lock the cluster;
2. recompute the input hash from pipeline version and article content hashes;
3. discard stale output if membership or content changed;
4. replace only affected findings, propositions, and comparisons in one transaction;
5. mark the cluster `READY` only after every persisted row passes deterministic validation.

A failed update must leave the previous readable analysis intact.

## 4. Lessons from concrete cases

### 4.1 Strong comparison: `보복살해범` versus `피의자`

This is the clearest positive example.

- All articles covered the same disclosure event and legal stage.
- The markers referred to the same person in the same role.
- `보복살해범` used an assertive crime label; `피의자` used a procedural legal status.
- One source finding correctly mapped to two other publishers.

Lesson: contested naming comparisons require event and procedural-stage equality, not just shared identity keywords.

### 4.2 Reject equivalent wording: `헌정사 첫` versus `이번이 처음이다`

Both expressions state the same fact with little meaningful expression contrast. The reviewed fixture retains `헌정사 첫` as a source-only finding but does not create a comparison.

Lesson: headline placement or compact wording alone does not justify a comparison card. Effectively equivalent wording should be withheld unless the product later introduces a separate prominence analysis.

### 4.3 Reject mismatched roles: `절충안` versus `대안으로`

The sentences are related, but the markers do different work. `절충안` evaluates a choice as a compromise between political demands; `대안으로` identifies it as an alternative to impeachment.

Lesson: same event and related reasoning are insufficient. Source and target markers must characterize the same semantic slot.

### 4.4 Prefer an unambiguous source: `속도전에 나선다`

The first fixture used a less clear sentence that could be read as paraphrasing the government's self-presentation. It was replaced with an article where the exact reporter-authored wording and sentence were unambiguous.

Lesson: when ownership is debatable, find a better-grounded source or withhold the finding. Do not repair ambiguity through a confident explanation.

### 4.5 Apply a salience threshold: `파행`

`파행` can characterize abnormal proceedings, but it is also routine political-reporting language. It was removed from the reviewed display set, while the more distinctive `아수라장` remained.

Lesson: lexical eligibility is not enough. The pipeline needs a representative-value gate so frequent conventional terms do not flood the Reader.

### 4.6 Categories are independent judgments

- `속도전에 나선다` retained both C and E because it frames the action and adds an evaluative, competitive manner.
- `절충안`, `역풍`, and `부정적인 기류` are better represented as E than C.
- Unsupported F must be removed without removing another independently valid category on the same marker.

Lesson: multi-label output is useful, but each label needs its own rationale and validation result.

## 5. Deterministic checks versus model judgments

### Deterministic code must verify

- URL and publisher normalization;
- article completeness and content hash;
- exact sentence and marker occurrence;
- offset containment;
- valid, distinct A–G category codes;
- source and target articles differ and share a cluster;
- a comparison has a shared proposition;
- uniqueness constraints;
- stale input hashes before commit.

### A model or human reviewer must judge

- cluster membership for ambiguous stories;
- expression ownership when attribution is implicit;
- category meaning in context;
- finding salience;
- proposition equivalence;
- marker role alignment;
- whether a difference is useful enough to display.

Passing deterministic checks proves grounding, not semantic correctness.

## 6. Prompt design requirements

Prompts should ask for structured decisions in this order:

1. exact source sentence and marker;
2. ownership and speaker, with evidence;
3. per-category yes/no judgments and rationales;
4. salience and display decision;
5. neutral proposition;
6. target sentence and marker;
7. proposition-equivalence fields;
8. semantic-role alignment;
9. final comparison decision and concise user-facing explanation.

Do not ask one unconstrained call to simultaneously discover articles, classify A–G, group propositions, compare wording, and write UI copy. Separate high-recall candidate generation from conservative verification.

## 7. Evaluation plan

Use the reviewed fixture as an initial regression set, not as a complete benchmark.

Track at least:

- article extraction success;
- finding grounding accuracy;
- ownership accuracy;
- finding precision and recall against human review;
- per-category precision;
- proposition-match precision;
- comparison precision;
- correct source-only completion rate;
- false comparisons caused by time, metric, scope, quotation, or legal-stage mismatch;
- latency, model calls, token use, and cost per cluster.

Comparison precision should be prioritized over comparison count. A wrong comparison damages trust more than an omitted carousel.

Regression fixtures should include both accepted and rejected examples with reason codes such as:

- `ATTRIBUTED_LANGUAGE`;
- `LOW_SALIENCE`;
- `EQUIVALENT_WORDING`;
- `DIFFERENT_PROPOSITION`;
- `ROLE_MISMATCH`;
- `DIFFERENT_TIME_OR_STAGE`;
- `DIFFERENT_METRIC_OR_SCOPE`;
- `UNCERTAIN_OWNERSHIP`.

These reasons remain evaluation data; they do not need product columns.

## 8. Efficiency without weakening the contract

- Analyze each article's findings once and reuse them when the cluster grows.
- Use embeddings or inexpensive models only to generate candidate sentence pairs.
- Reserve the quality model for ownership, category, proposition, and final comparison verification.
- Batch compatible per-article judgments, but keep each finding independently recoverable when one item fails.
- Re-analyze only new articles and existing rows affected by their propositions.
- Cache article extraction and stable model outputs by content hash and pipeline version.
- Never skip exact grounding or final semantic verification to save latency.

## 9. Implementation priorities

1. Turn the reviewed fixture into automated golden tests for accepted and rejected cases.
2. Implement per-article candidate extraction with ownership and salience gates.
3. Implement exact grounding and category validation.
4. Implement proposition generation and cluster-wide target retrieval.
5. Implement role-aligned comparison verification.
6. Implement atomic incremental persistence using the existing schema.
7. Measure quality, latency, and cost on multiple clusters before enabling automatic publication.

## 10. Decision summary

- Keep the current lean schema.
- Treat findings and comparisons as separate outputs.
- Accept zero findings and zero comparisons as valid.
- Require exact grounding for every stored sentence and marker.
- Optimize for trustworthy, representative output rather than maximum count.
- Keep uncertain and rejected candidates outside product tables.
- Do not automatically publish model output until the reviewed regression set and additional human evaluation show sufficient semantic precision.

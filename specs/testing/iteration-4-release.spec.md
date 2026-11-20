# Iteration 4 release verification

| Field | Value |
| --- | --- |
| Status | Drafted for the final Iteration 4 release run |
| Owner | Release and testing owner |
| Source requirements | [`acceptance-v1.spec.md`](acceptance-v1.spec.md), [`acceptance-v2.spec.md`](acceptance-v2.spec.md), [`../product/news-app-v1.md`](../product/news-app-v1.md) |
| Last reviewed | 2026-11-20 |

## Purpose and boundary

This specification defines one reproducible verification pass over the final
Iteration 4 commit, deployments, and Android APK. It combines three kinds of
evidence without treating them as interchangeable:

1. deterministic CI and coverage results;
2. the per-story results required for the six acceptance stories frozen in
   Iteration 3; and
3. focused regression checks for user-visible behavior added during Iteration
   4.

The run does not establish the editorial accuracy of every generated analysis.
AI-output quality remains a separate human-evaluation activity. Demo footage
may support a result, but it is not the test oracle.

## Release identity

Before the run, record all of the following in `docs/testing-documentation.md`:

- final `main` commit and GitHub Actions run;
- Web and API deployment revisions and URLs;
- APK SHA-256, device, Android version, and installation method;
- database dataset or seed revision;
- test date and tester; and
- any unavailable external dependency or intentionally disabled feature.

Evidence from different revisions must not be combined into one passing release
claim. If a blocking fix changes the candidate, rerun the affected acceptance
story and the full required CI workflow against the new revision.

## 1. Deterministic release gate

The final commit passes all required jobs in `.github/workflows/verify.yml`:

- Django configuration, automated tests, and every enforced backend coverage
  group;
- Web tests, lint, and production build; and
- Android JVM tests, every enforced Android coverage group, lint, and APK
  assembly.

The result records the exact commands, test counts, failures or skips, measured
coverage against the pre-existing thresholds, and links to the CI run and
coverage artifacts. A skipped required job is not a pass.

### Production API smoke

Run `python scripts/smoke_release_api.py --output <evidence.json>` against the
final API deployment. Unlike the historical Iteration 3 smoke, this check must
not assume that mutable production data still contains six old article titles.
It discovers the public corpus and accepts six different articles only when all
of the following hold:

- the article body and content identity are complete;
- the analysis is `READY` and refers to the same content hash;
- every stored source and comparison sentence/marker matches its offsets;
- at least one finding and one comparison-backed article are present; and
- a compared article can be fetched as a first-class Reader article with a
  `READY` analysis.

The evidence file records the exact sampled IDs, content hashes, analysis
versions, category counts, latencies, and a sample fingerprint. A later dataset
change may select different valid stories, but a particular run remains
auditable from that evidence. The six-story Iteration 3 script remains unchanged
as historical evidence and is not the release oracle for a replaced dataset.

## 2. Frozen Iteration 3 acceptance results

Run `UAT-01` through `UAT-06` exactly as written in
[`acceptance-v1.spec.md`](acceptance-v1.spec.md). For every story, record:

| Field | Required value |
| --- | --- |
| Identity | UAT ID and title |
| Environment | client, build, API revision, dataset, device or browser |
| Execution | tester and date |
| Observation | concise expected and observed behavior |
| Outcome | Pass or Fail |
| Follow-up | deviation, issue/PR, and rerun result when applicable |

The product now permits public guest reading, which differs from the final
signed-out expectation frozen in `UAT-01`. Do not rewrite the historical
criterion. Record the observed deviation against v1, then evaluate the current
guest contract separately under the Iteration 4 checks below.

## 3. Iteration 4 focused regression

These checks supplement the frozen v1 results. They do not retroactively replace
or weaken that plan. The guest/offline cases correspond to the draft
[`acceptance-v2.spec.md`](acceptance-v2.spec.md); team review is required before
calling v2 a frozen acceptance plan.

### R4-01 — Public reading without an account

On clean Web and Android sessions, open Latest as a guest, switch Politics and
Society, search a known title, open its complete article, and inspect one
comparison-backed finding. No user or bearer token is created. Personal actions
explain that sign-in is required without losing the current public article.

Tip: execute on the final Web deployment and installed APK and record the
observed route, identity state, and selected finding in the Iteration 4 result.

### R4-02 — Personal state and offline Saved boundary

Using two accounts, confirm that Saved and followed publishers do not cross
account boundaries on Web or Android. On Android, save one article with complete
analysis, enter airplane mode, restart, and verify that only locally complete
Saved rows are shown and can be opened. Search, Following, and Saved mutations
must not claim success offline.

Tip: execute with two disposable test accounts and record the online/offline
transition, locally visible article IDs, and cross-account observations.

### R4-03 — Compared coverage opens as a first-class article

From a finding with multiple compared publishers, open one compared article in
the internal Reader. Its title, publisher, body, findings, and save state must
belong to the selected article. Saving it must use the same account-owned Saved
contract as an article opened from the feed, and returning must preserve a
coherent navigation path.

Tip: execute once from a multi-publisher finding on each client and record the
source and destination article IDs and Saved state.

### R4-04 — Staged link analysis

Run one already-stored URL, one valid new public URL, one invalid or unsupported
URL, and one controlled upstream failure. Existing content reuses stored data;
new content produces a bounded staged result; invalid and failed requests show
recoverable errors; credentials and internal error details are not exposed.
Record model-backed output separately from deterministic endpoint checks.

Tip: execute the four URL partitions against the final deployment and retain
request IDs, terminal states, and sanitized error evidence.

### R4-05 — First-run guidance and analysis vocabulary

On a clean install, complete the tutorial and confirm that it can be replayed.
Open a Reader finding and confirm that the visible explanation, paired wording,
and source identity are understandable without exposing internal record IDs or
legacy A-G codes as the primary label. The tutorial must not block ordinary
guest reading after dismissal.

Tip: execute from a clean Android app state and a clean Web storage state and
record the replay path and one reviewed finding.

### R4-06 — Feed and economic-lens projection

Open every visible category supported by the release and verify that a selected
row opens the same article. For an article with an economic lens, confirm the
event summary and each publisher's view map to the stored API fields on both
clients without duplicating or replacing the article body.

Tip: execute one article with a stored economic lens on Web and Android and
compare the displayed fields with the final API response.

## 4. AI-module update

Keep deterministic structure checks separate from editorial judgments. The
Iteration 4 record must identify the exact reviewed case set, model/provider,
prompt and pipeline revision, decoding settings where supported, command, and
reviewer procedure. Report per-metric denominators and failures rather than only
an overall percentage.

If no new frozen semantic evaluation is completed, report only the structural,
offset, provenance, and failure-handling results that were actually measured and
state explicitly that no new semantic-accuracy claim is made.

## Result policy

- Append results; do not replace an earlier failure or modify a criterion after
  observing the outcome.
- Record `Fail` when an observable criterion is not met, even when the product
  change was intentional; explain the deviation and successor behavior.
- Link issues or pull requests for defects and record the candidate revision used
  for every rerun.
- Export the final English Wiki page to PDF only after the same release identity
  appears in the automated, acceptance, and device evidence.

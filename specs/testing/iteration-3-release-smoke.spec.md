# Iteration 3 Android release smoke

| Field | Value |
| --- | --- |
| Status | Frozen before the first release-candidate run |
| Owner | Android release owner |
| Frozen on | 2026-11-05 |
| Dataset | `server/fixtures/gold_v5/` |

## Purpose and boundary

This is a small release smoke test for the final Android APK, production API,
and reviewed database seed. It catches broken integration, navigation, and
Reader rendering before the demo. It does **not** measure AI accuracy and does
not replace the 30-case AI module evaluation or the six frozen UAT stories in
`acceptance-v1.spec.md`.

## Frozen cases

The cases are the six reviewed stories already placed first in the Android and
Web feeds. Expected counts come from the reviewed `gold_v5` fixtures. A finding
is one distinct reviewed source proposition; comparison evidence counts the
linked publisher passages beneath those findings.

| Case | Reviewed source story | Expected findings | Expected comparison passages |
| --- | --- | ---: | ---: |
| `b08` | HD Hyundai Electric quarterly revenue | 2 | 3 |
| `b12` | Shubao's first public appearance | 1 | 4 |
| `b16` | Hong Joon-pyo resumes legal practice | 3 | 9 |
| `b33` | Hongdae and Itaewon right-side walking | 3 | 3 |
| `b34` | Hanwha's first U.S. Navy ship order | 3 | 6 |
| `b11` | Goheung space-launch industrial complex | 1 | 1 |

## Preconditions

- Record the exact `main` commit, CI run, APK SHA-256, API URL, database seed,
  device model, Android version, network, date, and tester.
- Install the APK produced from that commit without changing its backend URL.
- Use a team-owned test account. Do not record credentials in evidence.
- Start from Latest with analysis enabled. Preserve existing user data unless a
  clean-install behavior is explicitly being checked.

## Common procedure for all six cases

1. Locate the story in Latest and open it.
2. Confirm that the Reader title and publisher match the selected row and that
   the stored article can be scrolled to its end without blank or duplicated
   blocks.
3. Confirm that the analysis is ready and the number of findings and comparison
   passages matches the frozen case table.
4. Select at least one marked phrase. Confirm that the compact preview shows the
   shared explanation without replacing or moving the article text.
5. Open the full comparison view. Confirm the publisher, compared passage, and
   original-link action are present; close it and return to the same Reader.
6. Return to the feed. The app must not crash, hang, or lose the selected scope.

## Case-specific coverage

- `b12`, `b16`, and `b34`: page through every publisher for one multi-source
  finding. The overview appears once and each page has a distinct publisher and
  grounded passage.
- `b11`: a single-source finding has no misleading multi-page control or empty
  header.
- `b33`: changing among three findings never leaves the previous finding's
  explanation or passage selected.
- `b08`: both its multi-source and single-source findings render with the same
  hierarchy.
- Once during the run, turn analysis off and on, reopen the story, and confirm
  the preference is remembered without changing the Reader position.
- Once during the run, save a story, reopen it from Saved, then remove it and
  confirm that it disappears from Saved.

## Frozen success criteria

- Core story flow: `6/6` pass.
- Expected finding and comparison structure: `6/6` exact match.
- Multi-source navigation: all applicable checked pages pass.
- Crash, ANR, unrecoverable blank state, or infinite loading: `0` occurrences.
- Analysis preference and save/remove state checks: both pass.
- Record tap-to-interactive Reader latency for each story and report median and
  maximum. This run is exploratory for latency; no threshold is invented after
  observing the values.

Any core-flow or data-structure failure blocks the candidate. Fix the product,
build a new APK, and rerun all six cases. A non-blocking visual defect is kept in
the result with severity and an issue or follow-up reference; the criterion is
not rewritten to turn the run into a pass.

## Result record

Results belong in `docs/testing-documentation.md`. Record one row per case with
observed counts, latency, pass/fail, and a concise deviation. A screen recording
may support the run, but the demo video alone is not the test oracle.

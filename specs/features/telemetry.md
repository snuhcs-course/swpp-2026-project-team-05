# Telemetry (Android)

| Field | Value |
| --- | --- |
| Status | Draft for review |
| Owner | Android |
| Clients | Android only |
| Service | Firebase Analytics (GA4) |

## Purpose

Measure how the app behaves for real readers during alpha and beta testing: which paths lead to an article, how
often readers open a comparison, how the link analysis ends, and where requests fail. The events answer the
golden-signal and HEART questions of the beta readiness report without recording who the reader is or what they
read or typed.

## Setup

- Firebase starts only when the build has `android/app/google-services.json`. The Gradle file applies the
  `com.google.gms.google-services` plugin only when that file exists, and the file is ignored by git
  (`**/google-services.json`). CI and any build without it run with telemetry off (`Telemetry.None`).
- The Firebase project registers the Android package that is installed (`com.swpp.team5.frameless`, and any test
  package such as `com.swpp.team5.frameless.v7`).
- Debug view on a test device: `adb shell setprop debug.firebase.analytics.app <package>`, then Firebase Console →
  Analytics → DebugView.

## Events

| Event | When | Parameters |
| --- | --- | --- |
| `screen_view` | the main route changes | `screen_name`: feed, saved, reader |
| `category_select` | a feed category is chosen | `category`: POLITICS, SOCIETY, ECONOMY |
| `search_submit` | a search query becomes active | — |
| `article_open` | an article opens in the Reader | `source`: feed, search, saved, link, comparison, related, reader |
| `analysis_view` | an article's analysis has loaded | `findings`, `with_comparison` (counts), `economic` (flag) |
| `comparison_open` | the comparison sheet opens for a finding | `pages` (count) |
| `save_toggle` | saving or removing succeeds | `saved` (flag) |
| `analysis_toggle` | the 표현 분석 switch changes | `enabled` (flag) |
| `link_analysis_result` | a link analysis ends | `result`: invalid, opened, error, cancelled; `code` (server error code); `seconds` |
| `tutorial_intro_done`, `tutorial_replay` | the intro is finished, or replayed from the feed | — |
| `api_error` | a request fails | `where`: feed, article, analysis, save; `code` (error code) |

## Privacy

Every parameter is a fixed code, a count, a duration in seconds, or a flag. No account id, user name, e-mail,
search text, pasted link, article id, title or body is recorded, and no user id is set on Firebase.

## Measures

| Question | Events |
| --- | --- |
| Traffic: how many readers and sessions, which screens | `screen_view`, `article_open` |
| Errors: how often requests fail, and where | `api_error`, `link_analysis_result` (error) |
| Latency: how long a link analysis takes | `link_analysis_result.seconds` |
| Adoption and engagement: do readers open comparisons | `analysis_view`, `comparison_open`, `article_open.source` |
| Task success: does a pasted link end in an article | `link_analysis_result.result` |

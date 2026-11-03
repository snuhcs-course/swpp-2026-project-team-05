# URL analysis mock test specification

| Field | Value |
| --- | --- |
| Status | Mock-only client flow |
| Owner | Article discovery feature owner |
| Source requirements | [`../features/url-analysis-mock.md`](../features/url-analysis-mock.md) |
| Last reviewed | 2026-11-03 |

## Scope

This specification covers the local Web and Android interaction exposed after a
completed zero-result article search. There is intentionally no backend or data
layer contract in this iteration.

## Postconditions

### Web

- [Component] A completed zero-result search exposes the article-link action;
  an ordinary empty feed does not.\
  Tested by: `web/src/pages/FeedPage.test.tsx > previews URL analysis only after a zero-result search without sending the URL`
- [Component] Loading and request-error states do not expose the article-link
  action.\
  Tip: add focused component cases alongside the existing feed failure tests.
- [Component] Invalid input remains in URL entry with an associated error; a
  valid HTTP(S) URL reaches the preparation preview, sends no API request, and
  returning restores the same zero-result search.\
  Tested by: `web/src/pages/FeedPage.test.tsx > previews URL analysis only after a zero-result search without sending the URL`

### Android

- [Component] A completed zero-result search opens the URL-analysis bottom
  sheet, rejects an invalid URL, previews preparation for a valid HTTP(S) URL,
  and returns to the unchanged search state.\
  Tested by: `com.swpp.team5.frameless.ui.screens.NewsScreensInstrumentedTest.zeroResultSearchPreviewsUrlAnalysisWithoutSubmitting`
- [Unit] URL validation accepts trimmed absolute HTTP(S) URLs with a host and
  rejects empty, relative, hostless, and non-HTTP(S) values.\
  Tested by: `com.swpp.team5.frameless.ui.screens.UrlAnalysisMockTest.acceptsOnlyAbsoluteHttpUrls`

## Boundaries

| Partition | Representative values |
| --- | --- |
| Search state | results / zero results / loading / error / ordinary empty feed |
| URL | empty / relative / `ftp` / hostless HTTP / valid HTTP / valid HTTPS with surrounding spaces |
| Mock stage | URL entry / validation error / preparation preview / returned |

No test may expect a network request, stored URL, generated article, completed
analysis, or fake analysis result from this mock.


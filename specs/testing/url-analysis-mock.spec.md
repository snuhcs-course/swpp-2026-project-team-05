# Retired URL analysis mock test specification

| Field | Value |
| --- | --- |
| Status | Retired UI; regression boundary only |
| Owner | Article discovery feature owner |
| Source requirements | [`../features/url-analysis-mock.md`](../features/url-analysis-mock.md) |
| Last reviewed | 2026-11-25 |

## Scope

This specification prevents the retired mock from returning to ordinary Web or
Android navigation. It does not change the compatibility endpoint or its data
tests.

## Postconditions

### Web

- [Component] A completed zero-result search shows a plain recovery message and
  no URL-analysis action.\
  Tested by: `web/src/pages/FeedPage.test.tsx > shows a plain recovery message after a zero-result search`

### Android

- [Acceptance] A completed zero-result search keeps its editable field and clear
  control, with no separate re-enter button, URL-analysis action, or sheet.\
  Tested by: `com.swpp.team5.frameless.ui.screens.NewsScreensInstrumentedTest.zeroResultSearchOffersOnlyARegularSearchRecovery`
- [Acceptance] The home menu exposes tutorial replay but no link-analysis
  entry.\
  Tested by: `com.swpp.team5.frameless.ui.screens.FeedHomeRefreshInstrumentedTest.headerMenuOffersOnlyTutorialReplay`

## Boundaries

| Partition | Representative values |
| --- | --- |
| Search state | results / zero results / loading / error / ordinary empty feed |
| Empty-state recovery | editable field / existing clear control / no URL input / no analysis action |

No ordinary-client test may expect URL input, an analysis request, a generated
article, or a fake analysis result from this retired mock.


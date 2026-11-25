# Publisher identity and navigation refinement

Status: Implemented; component/ViewModel coverage and remaining app-shell/device gaps are recorded below.

## Intent

Make the Android publisher directory recognizable by real publisher identity,
remove the redundant News-within-News tab label, use a consistent vector icon
family for root navigation, and keep subscription management in a dedicated MY
settings screen instead of sending readers to the publisher article directory.

## Preconditions and scope

- News remains a root destination. Its uncategorized section is labeled `전체 뉴스`.
- Publisher identity prefers the bundled genuine brand image for a known outlet,
  uses the server logo URL as a fallback, and keeps a neutral fallback for an
  unknown or failed logo. This avoids downloading a logo already bundled locally.
- Publisher directories keep real, individually navigable article headlines;
  no stock photos or fabricated article thumbnails are introduced.
- Account subscription state and authentication requirements are unchanged.
- The screenshot supplied by the user is the visual reference: a publisher
  identity header, separated headline rows, and clear block spacing.

## Postconditions

1. The News section row contains `전체 뉴스`, `언론사`, `정치`, `사회`, and
   `경제`, with a programmatically selected tab. Root navigation still says
   `뉴스`. Tested by: `PortalNavigationInstrumentedTest.newsTabsNameTheirScopeAndExposeSelection`.
2. Publisher blocks show a real logo where available, identity, up to three unnumbered headlines,
   and a separate subscription action. Identity and headline taps preserve
   their distinct destinations. Tested by:
   `PortalNavigationInstrumentedTest.publisherIdentityAndHeadlinesKeepTheirDestinations`.
3. MY → `구독한 언론사 관리` opens a dedicated `구독 관리` screen for guests
   and authenticated readers. It never selects the News/publisher directory.
   Back returns to MY and nested settings hide the root bar.
   Tip: check this app-shell route on the connected phone, including guest entry.
4. The management screen contains publisher logos, names, and subscription
   switches without article previews or a batch-save action. A successful toggle
   stays on the screen. Tested by:
   `PortalNavigationInstrumentedTest.managementTogglesSubscriptionsWithoutLeavingTheList`.
5. Saving disables only the affected publisher; others remain actionable.
   Errors appear only on the affected row or that publisher's own detail page.
   Confirmed state remains unchanged on failure. Tested by:
   `PortalNavigationInstrumentedTest.managementPreservesConfirmedStateOnFailure`,
   `PortalNavigationInstrumentedTest.subscriptionProgressAndErrorsBelongOnlyToTheirPublisher`,
   `PortalNavigationInstrumentedTest.managementKeepsOtherPublishersActionable`.
6. A guest toggle starts the contextual authentication flow and sends no
   subscription mutation before authentication. Cancelling leaves management
   visible. Tip: verify guest subscription → auth-sheet dismissal on the phone;
   the protected mutation remains covered by existing authentication API tests.
7. Root navigation uses four Lucide vector drawable icons on one 24-unit grid, with
   consistent stroke, alignment, light/dark contrast, selected semantics, and
   touch targets of at least 48 dp. Tested by:
   `PortalNavigationInstrumentedTest.rootTabsExposeSelectionAndKeepTheirActions`.
8. Missing or failed logos preserve the identity layout and do not block
   subscription or reading. Arbitrary new publisher codes remain supported.
   Tip: include a missing-logo publisher in the deterministic UI fixture; compare
   light and dark captures on the connected Android device.
9. Home keeps `주요 뉴스` and `구독한 언론사` as independent sections,
   with ALL topics and a selected-publisher article request. Updating subscriptions reloads the
   personalized section; guests and empty subscription sets issue no subscribed-
   publisher request. Tested by:
   `NewsViewModelTest.homeLoadsPublicAndSubscribedStoriesIndependently`,
   `NewsViewModelTest.homeDoesNotRequestSubscribedStoriesWithoutSubscriptions`.
10. The public section stays visible when the subscribed request fails, and
    overlapping topics in both sections remain independently navigable.
    Tested by: `NewsViewModelTest.subscribedHomeFailureKeepsPublicStories`,
    `PortalNavigationInstrumentedTest.homeShowsPublicThenSubscribedNews`.
11. A publisher-directory failure ends loading, exposes retry, and does not
    prevent the ordinary article feed from loading. A successful retry clears
    the directory error. Tested by:
    `NewsViewModelTest.publisherFailureEndsLoadingAndCanBeRetried`.
12. Home defaults to the first subscription, presents rounded-square logos as a
    single-select row, and updates the articles below when an outlet is selected.
    A visited outlet uses its cached first page. Superseded responses cannot
    overwrite the latest selection. Tested by:
    `NewsViewModelTest.homePublisherSelectionUsesExactQueriesAndRevalidatesCachedPages`,
    `NewsViewModelTest.lateHomePublisherResponseCannotReplaceTheSelectedOutlet`,
    `PortalNavigationInstrumentedTest.homePublisherLogosChangeTheArticlesBelow`.

## Edge cases and undefined behavior

- No recent articles: show a calm empty-articles line and do not invent a time.
- Null logo and unknown code: use a neutral vector identity placeholder.
- Directory subscriptions and MY management share confirmed account state.
- The screenshots' unrelated categories, story photography, and overflow-menu
  behavior are not added without a corresponding product/data requirement.
- Live logo requests are excluded from deterministic tests; bundled fixtures
  and offline fallbacks provide repeatable rendering.

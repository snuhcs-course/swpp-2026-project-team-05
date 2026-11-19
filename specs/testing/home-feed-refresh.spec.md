# Home feed visual refresh test specification

| Field | Value |
| --- | --- |
| Status | Draft |
| Owner | Android UI owner |
| Source requirements | [`../features/home-feed-refresh.md`](../features/home-feed-refresh.md) |
| Last reviewed | 2026-11-19 |

## Scope

The Android home app bar menu, the directly reachable app-bar actions, the
lead article cover (phases 2–3), the editorial feed rows and the per-category
art direction (phase 3).

### Non-goals

- Reader screens, the comparison sheet, search behaviour, feed loading and
  pagination logic (covered by their own specifications).
- Pixel-level visual appearance; check it by hand on an emulator.

## Preconditions

- `NewsFeedScreen` rendered with a signed-in session, search closed, and one
  stored article.

## Postconditions

### Success

- [Unit] The menu offers exactly `기사 링크로 분석하기` then
  `튜토리얼 다시 보기`.\
  Tested by: `com.swpp.team5.frameless.ui.screens.FeedHomeChromeTest.menuKeepsBothMovedActionsWithTheirExistingLabels`
- [Acceptance] Link analysis and tutorial replay are not app-bar buttons; opening
  `메뉴` and choosing each item calls the existing callback once and closes the
  menu.\
  Tested by: `com.swpp.team5.frameless.ui.screens.FeedHomeRefreshInstrumentedTest.headerMenuOpensLinkAnalysisAndTutorialReplay`
- [Acceptance] `기사 검색`, `저장한 기사` and `계정 메뉴` stay directly in the app
  bar and still work.\
  Tested by: `com.swpp.team5.frameless.ui.screens.FeedHomeRefreshInstrumentedTest.appBarKeepsSearchSavedAndAccountDirectlyReachable`
- [Unit] The row metadata order is category, publisher, time.\
  Tested by: `com.swpp.team5.frameless.ui.screens.FeedHomeChromeTest.rowMetadataIsCategoryThenPublisherThenTime`
- [Unit] The byline is publisher then time, without the category.\
  Tested by: `com.swpp.team5.frameless.ui.screens.FeedHomeChromeTest.bylineIsPublisherThenTimeWithoutTheCategory`
- [Unit] Politics, Society and Economy each have a distinct palette and kicker
  ink.\
  Tested by: `com.swpp.team5.frameless.ui.screens.FeedHomeChromeTest.eachCategoryHasItsOwnArtDirection`
- [Acceptance] Each row is one tap target showing the category kicker, the
  stored title and the byline, with no content description from decorative
  drawing; tapping opens that article.\
  Tested by: `com.swpp.team5.frameless.ui.screens.FeedFeaturedArticleInstrumentedTest.rowsShowCategoryKickerTitleAndBylineAsOneTapTarget`
- [Acceptance] The cover announces the click label `기사 열기`, and its artwork
  adds no content description.\
  Tested by: `com.swpp.team5.frameless.ui.screens.FeedFeaturedArticleInstrumentedTest.coverAnnouncesAnOpenActionAndHidesItsArtwork`
- [Acceptance] A single-article feed shows the stored title character for
  character, the category kicker and the `publisher · time` byline, opens the
  article on tap, and shows no comparison badge, issue card or outlet count.
  (Since phase 2 that article is rendered by the lead cover; rows are covered by
  `rowsShowCategoryKickerTitleAndBylineAsOneTapTarget`.)\
  Tested by: `com.swpp.team5.frameless.ui.screens.FeedHomeRefreshInstrumentedTest.redesignedRowShowsExactTitleCategoryMetadataAndNoInventedBadges`
- [Unit] The lead split takes the first article once and keeps the rest in
  their original order.\
  Tested by: `com.swpp.team5.frameless.ui.screens.FeedHomeChromeTest.featuredSplitTakesTheFirstArticleOnceAndKeepsTheRestInOrder`
- [Acceptance] The lead cover shows the first article's stored title, excerpt,
  category kicker and `publisher · time` byline, shows no badge or issue name, and
  opens that article on tap.\
  Tested by: `com.swpp.team5.frameless.ui.screens.FeedFeaturedArticleInstrumentedTest.featuredCardShowsTheFirstArticlesExactStoredDataAndOpensIt`
- [Acceptance] The lead article appears once; the remaining articles are listed
  under `최신 기사` and open their own article.\
  Tested by: `com.swpp.team5.frameless.ui.screens.FeedFeaturedArticleInstrumentedTest.firstArticleIsNotRepeatedInTheListBelow`

- [Acceptance] In the normal feed, the reading guide shows its fixed copy above
  the lead cover, and tapping it calls the tutorial callback exactly once and
  nothing else.\
  Tested by: `com.swpp.team5.frameless.ui.screens.FeedReadingGuideInstrumentedTest.guideAppearsAboveTheCoverInTheNormalFeedAndReplaysTheTutorialOnce`

### Errors and boundaries

- [Unit] With the lead disabled or an empty feed, the list is returned
  untouched and there is no lead.\
  Tested by: `com.swpp.team5.frameless.ui.screens.FeedHomeChromeTest.featuredSplitLeavesTheListUntouchedWhenDisabledOrEmpty`
- [Acceptance] With one article, only the lead card shows; there is no
  `최신 기사` title, and `새로고침` stays available.\
  Tested by: `com.swpp.team5.frameless.ui.screens.FeedFeaturedArticleInstrumentedTest.singleArticleShowsOnlyTheFeaturedCardWithoutAnEmptyLatestSection`
- [Acceptance] Offline, there is no lead card and every stored article is a row.\
  Tested by: `com.swpp.team5.frameless.ui.screens.FeedFeaturedArticleInstrumentedTest.offlineFeedHasNoFeaturedCardAndListsEveryArticle`
- [Acceptance] With search open, and with active search results, there is no
  lead card.\
  Tested by: `com.swpp.team5.frameless.ui.screens.FeedFeaturedArticleInstrumentedTest.searchModeHasNoFeaturedCard`
- [Acceptance] Loading, error and empty feeds show no lead card.\
  Tip: render `isLoading`, `error` and empty-following states and assert no node
  tagged `feed-featured-article` exists (these branches never compose the feed
  list today).
- [Unit] A blank publisher name or time is dropped from the metadata line, not
  replaced with placeholder text.\
  Tested by: `com.swpp.team5.frameless.ui.screens.FeedHomeChromeTest.rowMetadataDropsBlankPartsInsteadOfInventingThem`
- [Acceptance] Existing feed tests (category tabs of equal width, scope sheet,
  `기사 더 보기`, account sheet, search filters, no-result link analysis) pass
  unchanged.\
  Tested by: `com.swpp.team5.frameless.ui.screens.NewsScreensInstrumentedTest`
- [Acceptance] With a blank publisher and time, a row shows no byline line.\
  Tip: render a row whose publisher name is blank and whose time cannot be
  formatted, and assert only the kicker and title texts are present.
- [Manual] Cover artwork, kicker marks and title contrast look right for all
  three categories at 360dp width and at 1.3× font scale.\
  Tip: capture screenshots of a feed whose first article is each category in
  turn; add screenshot tests if the project adopts them.

- [Acceptance] The reading guide is absent with search open and with active
  search results.\
  Tested by: `com.swpp.team5.frameless.ui.screens.FeedReadingGuideInstrumentedTest.guideIsAbsentWhileSearching`
- [Acceptance] The reading guide is absent offline and when the feed is empty.\
  Tested by: `com.swpp.team5.frameless.ui.screens.FeedReadingGuideInstrumentedTest.guideIsAbsentOfflineAndWhenTheFeedIsEmpty`
- [Manual] On a 360dp-wide phone, the lead cover's title is visible on the first
  screen below the app bar, tabs and reading guide.\
  Tip: emulator check at 360×640dp and 360×800dp.

### State transitions

- [Acceptance] Dismissing the menu without choosing an item calls neither
  callback.\
  Tip: open `메뉴`, press back, and assert both counters stay 0.
- [Acceptance] Loading the next page keeps the lead card on the first article
  and appends new rows below.\
  Tip: render a feed, replace `items` with a longer list sharing the same first
  article, and assert the lead is unchanged and the new titles appear once.

## Undefined behavior

- Menu placement on very narrow screens or large font scales beyond the
  Material dropdown defaults.

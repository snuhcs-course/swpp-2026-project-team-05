# Topic-grouped story feed test specification

Status: Backend contract coverage implemented; Android interaction coverage remains manual

## Intent

Verify that discovery feeds collapse repeated coverage before pagination while
preserving individual article behavior elsewhere and selecting a representative
predictably.

## Postconditions

1. Two eligible articles in one cluster produce one story item with two distinct
   publishers and the same representative on repeated requests.  
   Tested by: `tests.test_news_api.NewsApiTests.test_story_feed_groups_coverage_and_is_stable`
2. When a signed-in viewer follows an eligible publisher in a topic, that
   publisher supplies the representative.  
   Tested by: `tests.test_news_api.NewsApiTests.test_story_feed_prefers_a_followed_publisher`
3. `scope=FOLLOWING` rejects a guest and excludes topics not covered by the
   viewer's followed publishers.  
   Tested by: `tests.test_news_api.NewsApiTests.test_following_story_feed_requires_auth_and_filters_topics`
4. Category filtering and consecutive cursors return non-overlapping topics;
   reusing a cursor under another category fails with `INVALID_CURSOR`.  
   Tested by: `tests.test_news_api.NewsApiTests.test_story_feed_cursor_is_stable_and_context_bound`
5. An eligible article with no cluster remains visible as a singleton story.  
   Tested by: `tests.test_news_api.NewsApiTests.test_story_feed_keeps_unclustered_articles`
6. Ordinary article search remains article-based when multiple matching
   articles share a cluster.  
   Tested by: `tests.test_news_api.NewsApiTests.test_story_feed_does_not_change_article_search`
7. Android parses the story response and encodes nullable category, scope,
   cursor, and limit correctly.  
   Tested by: `NewsApiClientTest.newsEndpointsParseFeedDetailAndGroundedComparison`
8. News-section selection maps `전체 뉴스` to an uncategorized story request,
   category tabs to their category, and `언론사` to the publisher directory.  
   Tip: add `NewsViewModelTest` cases when the section state is implemented.
9. Tapping a story row opens its representative article and Back restores the
   originating list state.  
   Tip: add Compose navigation coverage when the story row is implemented.
10. Android has no Latest/Following control; the visible News sections are
    `전체 뉴스`, `언론사`, `정치`, `사회`, and `경제`, and the tutorial entry appears
    only in `전체 뉴스`.
    Tip: add semantics-driven Compose coverage for the section row.
11. The publisher directory shows publisher identity, subscription state, and
    at most three recent headlines; selecting a publisher opens its exact
    article-level list.  
    Tip: add repository/ViewModel and Compose tests with publishers that have
    zero, one, three, and more than three eligible articles.
12. A guest subscription action issues no mutation and opens the contextual
    authentication sheet above the publisher directory. Dismissal preserves the
    directory and subscription state.  
    Tip: add a FrameLESSApp navigation test with an unauthenticated session.
13. When that guest explicitly authenticates, Android waits for the account's
    publisher directory to load and then applies only the subscription that
    opened the sheet. Cancelling authentication clears the pending intent.  
    Tip: add an app-shell test that delays the authenticated publisher response.
14. Light and dark mode use the same hierarchy; dark mode uses the approved
    charcoal background rather than pure black, and Home's search field does
    not repeat the product logo.  
    Tip: cover both UI modes with semantics and screenshot checks when the
    screenshot harness is available.
15. When an older production deployment returns a non-JSON 400/404/405 for the
    grouped-story route, Android retries the article feed and exposes each
    article as a singleton story; it does not mask structured API failures.
    Tested by: `NewsApiClientTest.storyFeedFallsBackToArticlesDuringServerRollout`
16. Appearance defaults to the device setting, explicit Light/Dark selections
    override it, and the selection is stored locally.
    Tested by: `AppearanceViewModelTest`

## Boundaries

- Saved, search, and publisher-page results remain article-based.
- The stable hash is tested for determinism and eligibility, not for editorial
  quality.
- Live external publisher pages, AI calls, weather, and market providers are
  excluded from deterministic CI.


# Link analysis test specification

| Field | Value |
| --- | --- |
| Status | Reviewed for merge |
| Owner | Analysis |
| Source requirements | `POST /api/v1/link-analyses`; feed screen `+` button; reader screen |
| Last reviewed | 2026-11-16 |

## Scope

A reader, signed in or not, pastes an article link from the feed screen's `+` button. The server fetches the article, finds
up to three same-event articles from other outlets, runs the staged A-G analysis, and stores the result as a
cluster whose articles are hidden from the feed. The app then opens the source article in the reader.

### Non-goals

- The quality of the A-G judgments (measured offline against the answer-key events, not in CI).
- Live Naver search, publisher pages and model calls (CI stays offline).

## Preconditions

- No sign-in is needed; a developer account has no daily limit.
- `OPENROUTER_API_KEY` is set for the staged analysis; the discovery pipeline has its own model key.

## Postconditions

### Success

- [Integration] A reader who posts a public article link receives the stored source article's id.\
  Tested by: `tests.test_link_analysis.LinkAnalysesEndpointTests.test_signed_in_reader_gets_the_stored_article_id`
- [Integration] The stored cluster opens in the reader with every marker at its offsets, and none of its articles
  appears in the feed or feed search.\
  Tested by: `tests.test_link_analysis.StoredClusterTests.test_imported_cluster_opens_in_the_reader_but_not_in_the_feed`
- [Unit] An evaluative word on one side against a plain report of the same act is shown, with its code, the stronger
  side and an explanation naming the outlets.\
  Tested by: `tests.test_link_analysis.StagedAnalysisTests.test_one_evaluative_word_against_a_plain_report_is_shown_with_code_and_explanation`
- [Unit] Plain predicates for the same fact on both sides (노출되다 / 게시되다) are no difference; an evaluative word
  (무더기로) keeps the pair.\
  Tested by: `tests.test_link_analysis.StagedAnalysisTests.test_plain_predicates_on_both_sides_are_no_difference`,
  `tests.test_link_analysis.StagedAnalysisTests.test_an_evaluative_word_keeps_a_plain_predicate`
- [Unit] A bare number is never a highlight; a number with a degree word or 뿐 can be.\
  Tested by: `tests.test_link_analysis.StagedAnalysisTests.test_a_bare_number_is_not_a_highlight_but_a_number_with_degree_word_is`

### Errors and boundaries

- [Integration] A guest and an ordinary account can start an analysis, and the usage rows are tied to no account.\
  Tested by: `tests.test_link_analysis.LinkAnalysesEndpointTests.test_a_guest_and_an_ordinary_account_can_analyse_a_link`
- [Integration] A client that is not a developer account gets `429 DAILY_LIMIT` after its daily limit, counted per
  client address whether signed in or not, while another address can still analyse.\
  Tested by: `tests.test_link_analysis.LinkAnalysesEndpointTests.test_a_client_that_is_not_a_developer_stops_at_its_daily_limit`
- [Integration] Every client that is not a developer account shares one daily total.\
  Tested by: `tests.test_link_analysis.LinkAnalysesEndpointTests.test_every_client_that_is_not_a_developer_shares_one_daily_total`
- [Integration] A developer account has no daily limit and records no usage.\
  Tested by: `tests.test_link_analysis.LinkAnalysesEndpointTests.test_a_developer_account_has_no_daily_limit`
- [Integration] A stored link opens after the limit is used up, and a link whose page could not be read does not use
  up the limit.\
  Tested by: `tests.test_link_analysis.LinkAnalysesEndpointTests.test_a_stored_link_opens_even_after_the_daily_limit`,
  `tests.test_link_analysis.LinkAnalysesEndpointTests.test_a_link_that_cannot_be_read_does_not_use_up_the_limit`
- [Integration] An empty, missing or local link receives 400.\
  Tested by: `tests.test_link_analysis.LinkAnalysesEndpointTests.test_a_missing_or_local_link_is_rejected`
- [Integration] A new link without the model key answers 503, while an already stored exact URL still opens without
  a fetch, discovery call or configured model.\
  Tested by: `tests.test_link_analysis.StoredClusterTests.test_a_new_link_without_the_staged_model_key_is_unavailable`,
  `tests.test_link_analysis.StoredClusterTests.test_a_stored_link_answers_at_once_without_discovery`
- [Integration] No same-event article answers 422 with the reader's message; a model outage answers 503 with
  `Retry-After: 30`.\
  Tested by: `tests.test_link_analysis.LinkAnalysesEndpointTests.test_an_analysis_error_reaches_the_reader`,
  `tests.test_link_analysis.StoredClusterTests.test_no_same_event_article_is_an_error_for_the_reader`
- [Unit] Model answers that are missing or empty show nothing instead of failing.\
  Tested by: `tests.test_link_analysis.StagedAnalysisTests.test_no_model_answer_shows_nothing`
- [Unit] The app posts the link to `/api/v1/link-analyses`; a server without the API is explained as a coming
  server update, and the API's own errors reach the reader unchanged.\
  Tested by: `com.swpp.team5.frameless.data.NewsApiClientTest.linkAnalysisPostsTheLinkAndExplainsAServerWithoutTheApi`
- [Unit] The sheet rejects an empty or non-http(s) field without a request, keeps a server error with the entered
  link for a retry, opens the reader on success, and stops waiting when closed.\
  Tested by: `com.swpp.team5.frameless.ui.viewmodel.NewsViewModelTest.anAnalysedLinkOpensItsArticleInTheReader`,
  `com.swpp.team5.frameless.ui.viewmodel.NewsViewModelTest.aFieldThatIsNotAnHttpLinkStaysInTheSheetWithoutARequest`,
  `com.swpp.team5.frameless.ui.viewmodel.NewsViewModelTest.aServerErrorStaysInTheSheetAndCanBeRetried`,
  `com.swpp.team5.frameless.ui.viewmodel.NewsViewModelTest.closingWhileTheServerAnalysesStopsWaiting`
- [Integration] A second analysis while one runs answers 429.\
  Tested by: `tests.test_link_analysis.LinkAnalysesEndpointTests.test_one_analysis_at_a_time`

### State transitions

- [Integration] A link already stored answers at once with status `existing` and no discovery or model call.\
  Tested by: `tests.test_link_analysis.StoredClusterTests.test_a_stored_link_answers_at_once_without_discovery`
- [Integration] A comparison article already stored in another cluster is not used, so an analysis never moves
  stored articles.\
  Tip: add a test with a discovery result whose first candidate URL is stored and check that the stored article keeps
  its cluster.
- [Integration] Runtime import preserves an existing publisher's reviewed name, domain, logo and display order.\
  Tested by: `tests.test_link_analysis.StoredClusterTests.test_runtime_import_preserves_existing_publisher_metadata`
- [Acceptance] The feed `+` button opens the link dialog; a successful analysis closes it and opens the reader; an
  error stays in the dialog.\
  Tip: add a Compose UI test for the dialog states.

## Undefined behavior

- Articles whose body extraction differs between fetches.
- Links outside news articles (the discovery pipeline answers that no same-event article was found).

## Test design

| Risk or partition | Representative cases | Oracle source |
| --- | --- | --- |
| Happy path | stored id returned; reader-only cluster | Scope |
| Boundary | stored link; no answers from the model | State transitions |
| Error | daily limit per client and in total, bad link, no key, no same event, model outage, busy | Errors and boundaries |
| State interaction | feed and search exclude the cluster | Scope |

## Human review checkpoint

- [x] Product intent and non-goals were reviewed.
- [x] Assertions come from the specification, not the implementation output.
- [x] Missing coverage is recorded as `Tip`, not hidden.
- [x] Open access is bounded by the daily limits; developer accounts are exempt.

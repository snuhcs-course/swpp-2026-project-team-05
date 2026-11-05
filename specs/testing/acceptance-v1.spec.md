# News app acceptance plan v1

| Field | Value |
| --- | --- |
| Status | Intent-reviewed and frozen for Iteration 3 |
| Owner | Team |
| Source requirements | [`../product/news-app-v1.md`](../product/news-app-v1.md), [`../ui/news-app-v1-ui.md`](../ui/news-app-v1-ui.md), [`../features/news-app-features.md`](../features/news-app-features.md) |
| Last reviewed | 2026-11-05 |
| Frozen against | `main` at `ab8d9bf` |

This plan defines stable user-facing procedures. After the team freezes it for
Iteration 3, do not rewrite a failed criterion; fix the product and rerun it.
Iteration result dates, testers, build identifiers, and evidence belong in
`docs/testing-documentation.md`.

## UAT-01 — Create and restore an account session

**Given** a signed-out clean install or browser session, **when** a tester creates
a valid ordinary account and reopens the client, **then** the account is signed in
again without using a developer credential.

Procedure: register; verify the feed appears; close and reopen the client; verify
the same user is restored; log out; verify protected news content is no longer
available. Pass only if no secret is displayed or logged.

## UAT-02 — Browse the reviewed news feed

**Given** a signed-in user, **when** the tester changes between Politics and
Society and between Latest and Following, **then** every visible row satisfies
the selected category and scope and opening a row shows that exact article.

Procedure: record the initial Latest rows; switch each category; follow at least
one publisher and open Following. If the selected category has a followed-source
article, load the next page when available, open one article, and compare its
publisher and title with the selected row. Otherwise, verify the distinct
no-matching-article empty state and its return-to-Latest action; do not require
an article to open from an empty result.

## UAT-03 — Search the complete feed-visible corpus

**Given** a signed-in user, **when** the tester searches a title term, publisher,
and body-only term, **then** matching feed-visible rows appear in deterministic
pages, a comparison-only article does not appear, and clearing search restores
the ordinary feed.

Procedure: perform all four searches on Web and Android; for each query verify
that Politics and Society are both present with counts equal to their displayed
matching rows plus any remaining paginated rows; load more in one category and
verify the other category is unchanged; open a result; reload Web and verify
search resets without rewriting the URL while typing; clear or close search;
verify the pre-search category and feed scope are retained.

## UAT-04 — Read an article with optional analysis

**Given** an article whose analysis is ready, **when** the tester opens it and
scrolls the article, **then** the complete stored article remains readable and
the platform's analysis presentation does not replace or corrupt the article
text. Android additionally supports turning markers off and on; Web presents
the stored findings in its persistent analysis rail.

Procedure: on both clients, compare the opening article metadata with the feed
row and scroll to the end. On Android, toggle analysis twice, reopen the article,
and verify the saved preference. On Web, select a title/body marker and verify
that the corresponding finding is selected in the always-visible analysis rail.

## UAT-05 — Inspect a grounded cross-publisher comparison

**Given** a reviewed finding with comparison evidence, **when** the tester selects
the marked source phrase, **then** the app shows its explanation, the other
publisher and exact corresponding passage, supports multiple comparison articles
when present, and opens the selected article without losing context.

Procedure: select a known multi-article finding; verify the source marker and each
target passage against stored text; on Android, confirm the compact preview opens
before the maximum-height comparison sheet, then page between publishers and
confirm the common overview appears only once, use the bottom previous/next
navigator, scroll one comparison article, and pull
down from its top to return to the clean Reader without restoring the preview;
repeat once with the fixed-heading close action; open one original article link
and return.

## UAT-06 — Keep saved articles synchronized per account

**Given** the same signed-in account on Android and Web, **when** the tester saves
an ordinary article on Android and later removes it on Web, **then** each client
shows the same server-owned state after refresh without affecting another
account.

Procedure: open two Android articles and save each from Reader; refresh Web and
verify both appear in recent-save order; open one on Web and remove it from its
Reader; refresh Android and verify it disappears; sign out and use a second
account on each client; verify neither saved row nor saved indicator leaks
across accounts.

## Freeze and results policy

- Team approval changes status to `Intent-reviewed (frozen)` and records the date
  and commit here.
- Later cases may be appended with IDs and dates. Existing cases and criteria are
  not removed or weakened during the iteration.
- Each run records client/build, API revision, database seed version, tester,
  date, pass/fail, and evidence link in the living testing document.

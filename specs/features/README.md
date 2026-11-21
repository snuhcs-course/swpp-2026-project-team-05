# Feature specifications

Feature specifications extend, but do not silently override, the core contracts
in `../product/` and `../ui/`. Give a new substantial feature its own file when
it has an independent API, data model, lifecycle, or acceptance boundary.

`news-app-features.md` currently keeps article search, saved articles, and
following sources together because they were implemented as adjacent news-app
slices and already share one reviewed history. Future unrelated features should
not be appended to that file by default.

`url-analysis-mock.md` defines the client-only bridge from an empty article
search to the future URL ingestion experience. It deliberately creates no
backend or persistence contract.

`guest-offline-reading.md` defines the guest read boundary shared by Android and
Web and the deliberately bounded Android Room cache. It does not introduce a
guest server identity, backend migration, or general synchronization system.

`first-run-tutorial.md` proposes Android first-launch onboarding, first-article
coach marks, and a label-guide dialog. It is a draft awaiting the separate
product decision that `news-app-features.md` §12 requires for onboarding.

`scroll-synced-analysis-peek.md` defines the Android-only reading aid that
selects a body finding after a stable reading pause. It preserves the existing
article-level comparison entry and comparison sheet and adds no backend or
analysis contract.

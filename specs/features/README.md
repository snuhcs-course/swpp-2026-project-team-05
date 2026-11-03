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

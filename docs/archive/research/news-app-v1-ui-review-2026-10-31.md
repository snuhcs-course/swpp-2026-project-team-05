# FrameLESS news UI review — 2026-10-31

Scope: production APK against `https://frameless-team05-api.vercel.app` on the API 36 emulator. The review covered login, category switching, a READY article with zero findings, a title finding, two comparison publishers, horizontal paging, and three expanded ↔ middle sheet gesture cycles.

## Resolved in this pass

| Priority | Observation | Resolution |
| --- | --- | --- |
| P1 | The feed used oversized filled pills and repeated large analysis badges, so only a few headlines fit on screen and the app read as a prototype. | Replaced category pills with compact editorial tabs, reduced row height and headlines to two lines, moved category into metadata, and changed analysis state to a quiet dot-and-label treatment. |
| P1 | `표현 분석 가능` implied every READY article contained a visible finding. Some reviewed articles correctly have zero findings. | READY now says `분석 완료`; PARTIAL and UPDATING have distinct labels. |
| P2 | The system-colored analysis switch introduced an unrelated purple accent and the avatar/back/toggle semantics were weak. | Applied the FrameLESS navy switch colors and added Korean control descriptions/state semantics. |
| P1 | A hidden or inactive comparison target could be loaded from a stale stored relation. | The API now excludes hidden/inactive/cross-cluster/self targets and fails closed on invalid text offsets. |
| P1 | Re-importing a changed title or body at the same canonical URL could invalidate every stored offset. | The importer now rejects changed stored content instead of silently overwriting it, and its hash covers both title and body. |
| P1 | The new feed and authentication flows had no focused state tests, causing the existing Android coverage gate to fail. | Added repository interfaces and focused tests for authentication, filters, pagination, refresh, article/analysis loading, API errors, and session expiry. Data coverage is 82.7% and ViewModel coverage is 91.2%. |
| P2 | The feed and Reader had no device-level Compose contract test. | Added emulator tests for editorial feed selection, account access, readable zero-finding articles, and the analysis toggle. |

## Verified behavior

- Login reaches the saved all-publisher/category feed on the production backend.
- Politics/society switching resets the feed correctly.
- READY with zero findings keeps the full article readable and states that no visible feature was found.
- The News1 `보복살해범` title marker opens its explanation and two publisher-diverse comparison pages (MBC and Yonhap).
- Horizontal paging keeps the selected comparison publisher while expanding and collapsing.
- Three expanded ↔ middle drag cycles reached stable anchors; no app exception or oscillation was observed.
- API 36 emulator Compose tests pass for the feed and Reader contracts.

## Remaining product/data feedback

| Priority | Observation | Recommended follow-up |
| --- | --- | --- |
| P1 | Some stored article bodies still contain scraped related-keyword and page-chrome text. This is visible in the News1 Jeju sample and violates the stored-text contract. | Freeze reviewed `title`/`body_text` in the dataset instead of re-fetching it during import, clean the affected pilot bodies, and re-ground offsets before publishing another dataset revision. Do not clean text after analysis offsets have been stored. |
| P2 | The feed is deliberately text-first because the reviewed schema has no image contract. | Add thumbnails only after publisher image rights, fallback behavior, and API fields are specified; do not use generic generated imagery. |
| P2 | The most complex comparison interactions still rely on the manual emulator walkthrough. | Add focused semantics-driven tests for marker opening, pager movement, and middle ↔ expanded sheet anchors when the sheet interaction is next changed. |
| P3 | The refresh action is explicit rather than a pull gesture. | Add pull-to-refresh later only if it preserves filters, scroll restoration, and accessible refresh status. |

## Reference direction

- Use established editorial apps for typography, density, and uninterrupted reading.
- Use comparison products only for the contextual `compare coverage` affordance; FrameLESS must not inherit political-bias scores or truth labels.
- Keep FrameLESS distinctive through exact in-article expression markers and a publisher pager, not through decorative cards or gradients.

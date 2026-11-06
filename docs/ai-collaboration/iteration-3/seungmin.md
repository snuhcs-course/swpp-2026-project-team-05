# AI Collaboration Contribution — Iteration 3

|                     |                                                                 |
| ------------------- | --------------------------------------------------------------- |
| **Team**            | Team 5 — FrameLESS                                              |
| **Iteration**       | Iteration 3 — Advanced Testing & Evaluation                     |
| **Period**          | October 24, 2026 – November 6, 2026                             |
| **Iteration goals** | Turn FrameLESS into an article-first news app, refine the Reader from HE/device feedback, and produce reproducible release evidence. |
| **Author**          | Kim Seungmin — Individual contribution                          |

---

## 1. Summary of AI Usage

I used Codex as a development partner throughout Iteration 3. The work began
with a large product change: FrameLESS would no longer begin with a URL input.
It would behave like a news application, show articles from multiple publishers,
and attach already-computed analysis to the article that the reader chose. I
used Codex to explore that design, write product and test specifications, and
implement the resulting backend, database, Android, and Web changes.

My role was not limited to approving generated code. I decided the product
boundaries and repeatedly changed or rejected AI proposals after using the app.
For example, I kept the article as the primary reading unit rather than turning
the feed into a cluster dashboard; asked for a two-stage finding interaction
instead of an unstable draggable sheet; removed internal A–G and article-ID
labels from ordinary Reader language; and changed search after the first
implementation broke Korean IME input and later versions destabilized the
category controls.

I also used Codex to make our testing process more systematic. Before the final
release run, we froze six representative stories and their observable Reader
criteria. Codex generated and ran tests, but the test oracles came from the
reviewed product/UI specifications and the release criteria that I accepted.
Automated integration evidence, the six-story smoke test, HE findings, and
human evaluation of AI comparison quality remained separate claims.

### AI tool used

| **Tool** | **Model / version** | **Used by** | **Main purpose** |
| --- | --- | --- | --- |
| Codex | Not reliably recorded | Kim Seungmin | Product/specification work, cross-stack implementation, debugging, test generation and execution, release documentation |

### Deliberately not delegated

- The team, not Codex, decided to pivot to an article-first news product.
- I decided which interactions were acceptable after checking the Android app.
- Codex did not decide whether a comparison was semantically correct or whether
  an article was politically neutral, truthful, or balanced.
- The 30-article HE dataset and the separate human evaluation of comparison
  quality were not replaced by generated unit tests or the six-story smoke run.

---

## 2. Major Tasks Where AI Contributed

| **#** | **Task** | **Related deliverable** | **AI role** | **My role** |
| --- | --- | --- | --- | --- |
| T1 | Convert the URL-analysis prototype into an article-first news application | News domain/database, authenticated API, Android app, desktop Web client, product/UI specifications | Proposed structures; drafted specifications; implemented and revised code across the stack | Set the product direction, scope, data relationships, and what remained out of scope |
| T2 | Refine the Reader and search experience through repeated device feedback | Two-stage Android finding interaction, shared comparison overview, global article search, Korean IME and stable filter behavior | Produced UI alternatives, implementation changes, regression tests, and documentation updates | Used the app, identified unstable or confusing behavior, chose the interaction model, and rejected regressions |
| T3 | Establish a specification-driven implementation and testing workflow | `specs/`, pull-request template, CI coverage gates, six-story release smoke | Drafted traceable specifications/tests; ran deterministic checks and the smoke procedure; diagnosed failures | Reviewed intent and success criteria, kept test types separate, and decided what evidence was sufficient to release |
| T4 | Prepare the final Iteration 3 build and submission evidence | `iteration-3-demo`, README, recording plan, APK/build evidence, Testing Documentation | Prepared the branch, documentation, commands, and evidence tables | Recorded the demo, checked the physical-device experience, and reviewed the final claims and submission scope |

---

## 3. Representative Prompts, Outputs, and Human Decisions

The excerpts below are from the actual development conversation. Spacing and
obvious typing errors are normalized, and omitted text is marked with `…`.

### Example 1 — Changing the product from URL input to a news app (T1)

**Define** — The previous application asked the user to paste one article URL
and wait for analysis. After a team discussion, we wanted normal news-app
navigation: users should choose an outlet/article first and see analysis as an
additional reading layer. The first priority was not a new automatic collection
engine, but a robust way to store and display already-analyzed results.

**Initial prompt**

```text
우리 회의 결과 앱의 방향성이 바뀌었는데, 약간 뉴스 앱처럼 가기로 했어.
우리가 정한 여러 언론사들의 뉴스를 볼 수 있게 하고, 이후에는 개인
맞춤화 기능까지 넣을 거긴 한데, 미리 분석을 뒤에서 돌려 놓는 거야.
사용자가 링크를 입력하게 하는 게 아니고. 이해돼?
```

**Constraints added after the first discussion**

```text
언론사 분석을 하는 AI 엔진까지 지금 깎을 필요는 없어. UI가 우선이야.
정치랑 사회만 해. 기사 본문은 전부 보여주고, 분석 중은 따로 띄우자.
URL 분석은 기능 자체는 남겨두되 UI에서만 없애고, 수집 주기는 나중에
다루자. 일단 분석이 끝난 기사라고 가정하고 그걸 어떻게 저장할지,
디비 구조를 자세히 고민해보자.

뭘 구현하든 프로페셔널한 개발 팀처럼 확장성 있고 robust한 코드로
가야 해. 다만 처음부터 몸집이 너무 커지지는 않게 하자.
```

**AI-assisted output**

Codex first turned the discussion into a product specification and a candidate
schema. It then implemented authenticated news APIs, article and publisher
records, stored findings and cross-article comparisons, Android feed/Reader
screens, and a separate desktop Web client. Saved articles, publisher follows,
and global article search were added on top of the same authenticated API rather
than as client-only data.

Key implementation evidence:

- article-first Android/API baseline: [`0ddaa20`](https://github.com/snuhcs-course/swpp-2026-project-team-05/commit/0ddaa20);
- desktop Web client: [`04a3937`](https://github.com/snuhcs-course/swpp-2026-project-team-05/commit/04a3937);
- saved articles: [`9a38587`](https://github.com/snuhcs-course/swpp-2026-project-team-05/commit/9a38587);
- followed publishers: [`a5c58f9`](https://github.com/snuhcs-course/swpp-2026-project-team-05/commit/a5c58f9);
- server-side article search: [`efa9c90`](https://github.com/snuhcs-course/swpp-2026-project-team-05/commit/efa9c90).

**Human review and decisions**

The schema was not accepted in one pass. I repeatedly asked what each table was
for and removed structures that were useful for an experimental pipeline but
not necessary for the product. In particular:

- every article became a normal entry point; there is no permanent “reference
  article” in the user experience;
- an article finding owns its exact marked phrase and explanation;
- a shared proposition groups findings/passages that discuss the same issue;
- pair-specific comparison detail remains separate because two articles can
  differ in different ways even inside one shared issue;
- pipeline-run history was not made part of the v1 product model merely for
  hypothetical future regeneration;
- URL analysis remained available internally, but the normal feed did not ask
  users to paste a link.

This review kept the implementation extensible without making the first news
app release depend on the unfinished automatic collection pipeline.

**What I kept, changed, and rejected**

- **Kept:** a shared database/API for Android and Web, authenticated users,
  article-first navigation, full article text, stored analysis, and explicit
  relationships between findings and compared passages.
- **Changed:** the initial broad schema into a smaller product-facing model and
  renamed internal concepts when they were exposed to users.
- **Rejected/deferred:** a cluster dashboard as the main screen, automatic
  personalization, automatic ingestion scheduling, and an implication that
  every related article had already been semantically validated.

### Example 2 — Replacing an unstable Reader sheet and correcting search regressions (T2)

**Define** — The first mobile comparison UI relied heavily on a draggable
bottom sheet. It was acceptable at one height, but became unstable when dragged
past the middle anchor. Opening a full comparison surface immediately also
interrupted normal reading. I wanted the article to remain primary and the
analysis to unfold only when requested.

**Reader prompt revision**

```text
차라리 문장을 누르면 그 옆에 뭔가 말풍선처럼 뜨고, 그걸 누르면
전체 화면이 뜨는 2단계 방식이 좋을 것 같아. 읽다가 바로 다른 화면으로
가면 정신이 산만하니까, 이걸 고려해서 최선의 방식을 생각해봐.
```

After trying the first revision, I narrowed the behavior again:

```text
첫 번째로 뜨는 건 지금 작은 미리보기 방식이 좋은 것 같고, 그걸 눌러서
뜨는 전체 화면은 이전 방식이 낫다. 다른 기사가 없으면 굳이 전체 화면을
보여줄 필요가 있을까? 전체 화면을 닫았으면 1차 미리보기도 다시 뜨지
않는 게 맞는 것 같아.
```

**AI-assisted output**

Codex implemented progressive disclosure on Android:

1. tapping an analyzed expression opens a compact preview without moving the
   article text;
2. only findings with comparison evidence expose a detail action;
3. the expanded comparison sheet opens directly at full height;
4. the shared issue explanation appears once above publisher pages;
5. multiple publishers can be changed by swipe or a compact navigator;
6. closing detail clears the preview and returns to a clean Reader.

The data contract was also changed so that one proposition-level
`comparison_overview` was stored separately instead of copying the same broad
explanation into every publisher pair. Relevant changes include
[`93416e9`](https://github.com/snuhcs-course/swpp-2026-project-team-05/commit/93416e9)
and PR [#77](https://github.com/snuhcs-course/swpp-2026-project-team-05/pull/77).

**Search prompt and correction loop**

Search produced a second useful example of why generated UI needed direct
review. The initial Web version synchronized input with the URL while the user
typed. On a Korean keyboard, composing characters were submitted too early and
appeared as separated input.

```text
검색 기능 피드백: 왜 입력할 때 한글이 다 떨어져서 쳐지지? 그리고 상단
URL도 실시간으로 바뀌는데 별로야. 굳이 URL이 바뀌는 방식이어야 하나?
가장 일반적인 방식인지 보고 고쳐보자.
```

Codex moved the input to local state, waited for IME composition to finish, and
debounced only the completed query ([`74f1d47`](https://github.com/snuhcs-course/swpp-2026-project-team-05/commit/74f1d47)).
Later, adding global counts/filters caused the Politics/Society controls to
change width and, on the phone, pushed Society partly off-screen. I rejected the
count-badge design and separated ordinary section navigation from search-only
`All / Politics / Society` filters. The final version keeps fixed text tabs,
starts every new query from the full corpus, and requests one category only when
the user selects it ([`ab8d9bf`](https://github.com/snuhcs-course/swpp-2026-project-team-05/commit/ab8d9bf),
[`c038d60`](https://github.com/snuhcs-course/swpp-2026-project-team-05/commit/c038d60)).

**Human review and decisions**

These corrections came from interaction, not from reading the generated code
alone. I used the app on the connected phone, described the exact movement or
state transition that felt wrong, and decided whether the next version was
actually simpler. I also removed instructional copy such as “swipe to view the
next article” and “pull down to close” once the gestures and explicit close
button were clear enough.

**What I kept, changed, and rejected**

- **Kept:** in-place expression markers, a small first-stage preview, full
  source-linked comparison detail, swipe navigation, and server-side search.
- **Changed:** the draggable multi-anchor sheet into preview → full detail;
  repeated pair explanations into one shared overview; URL-coupled search into
  local IME-safe input; result-count pills into stable text tabs.
- **Rejected:** opening full detail for a finding with no compared article,
  restoring the preview after closing detail, gesture-instruction clutter, and
  layout-changing count badges.

### Example 3 — Plan first, write the specification, then implement search (T3)

**Define** — As the news-app surface grew, I did not want Codex to move directly
from a one-line feature request to code. Article search was the clearest example
of the workflow I wanted: discuss the product and scaling choices first, review
a concrete plan, turn the accepted decisions into a specification, and only
then generate the implementation and tests.

**Planning prompt**

```text
일단 검색부터 하자. 그런데 바로 구현하지 말고 계획부터 세워보자.
우리 DB에 있는 보여지는 기사를 전부 검색하게 해도 효율적인 구현이
가능한가? 아니면 최근 며칠로 제한해야 하나? 장기적으로 봤을 때까지
고려하되, 지금 필요 이상으로 복잡하게 만들지는 마.
```

The first answer was still only a plan. I then confirmed the ownership boundary
and asked for another review before the specification was written:

```text
그럼 검색은 백엔드에서 수행하는 거지? 프론트가 최근 기사만 가지고
있어도 전체 보여지는 기사를 검색할 수 있게. 최종 검색 기능 스펙
계획을 다시 정리해봐. 개선할 부분이 더 없으면 그때 스펙을 작성하자.
```

Only after reviewing that specification did I authorize implementation:

```text
그럼 이제 구현해볼까? UI 부분도 스펙에 정리해 놓은 거 맞지?
```

**AI-assisted output**

During planning, Codex separated backend and client responsibilities instead of
searching only the articles already loaded in the UI. The reviewed design made
Django responsible for normalization, the feed-visible corpus, deterministic
title/publisher/body ranking, grounded excerpts, filtering, and cursor binding.
Android and Web owned local input, debounce, active-filter state, pagination,
and restoration of the ordinary feed.

The first specification was committed before implementation
([`e8b2f90`](https://github.com/snuhcs-course/swpp-2026-project-team-05/commit/e8b2f90)).
The implementation then added the backend query and both clients with tests
([`efa9c90`](https://github.com/snuhcs-course/swpp-2026-project-team-05/commit/efa9c90)).
The repository workflow was subsequently made explicit in `AGENTS.md`, the
testing-spec template, and the pull-request checklist
([`481dd5b`](https://github.com/snuhcs-course/swpp-2026-project-team-05/commit/481dd5b)).

**Human review and decisions**

I chose to search every article that the product can actually show rather than
silently restricting results to the current client page or an arbitrary recent
date. Hidden and comparison-only records remain outside the search corpus. The
first version uses bounded server pagination and deterministic database queries;
semantic search, typo correction, suggestions, and premature search-service
infrastructure were left out. If the corpus grows enough to require a database
full-text index later, the API contract can remain stable.

This workflow did not mean the specification could never change. Direct use
after implementation exposed the Korean IME and category-control problems in
Example 2. Those failures were handled by changing the reviewed behavior,
updating the specification, and adding regression tests—not by silently changing
the code while leaving the document behind.

**Why this mattered**

- Planning exposed a fundamental responsibility question before code existed:
  search belongs to the server-side visible corpus, not the client cache.
- Reviewing the plan prevented a premature date limit or external search
  engine from becoming an accidental product constraint.
- Writing the specification before tests gave the generated tests an
  independently reviewed oracle.
- Requiring specification updates with later fixes kept the written behavior,
  tests, and implementation aligned.

### Example 4 — Freezing the release test before running it (T3, T4)

**Define** — The Iteration 3 announcement asked for clear success criteria and
experimental evidence. My assigned release work was initially summarized as
“final build/test/APK and a six-article smoke test,” but “smoke test” did not yet
define what had to pass. We needed useful evidence without pretending that a
small integration test measured the semantic accuracy of the analysis model.

**Prompt and course context**

```text
- HE 기반 UI 수정 2~3개
- 최종 main build/test/APK, 6기사 smoke test
- iteration-3-demo branch + README + demo video

수업 공지는 key functionality의 success criteria를 명확히 정의하고,
accuracy, success rate, latency 같은 근거를 수집하라고 한다. 영상은 내가
찍을 테니 촬영 플로우를 정리하고, 나머지 테스트와 제출 준비는 실제로
필요한 것만 체계적으로 해줘. 다른 팀 예시에 억지로 맞출 필요는 없다.
```

**AI-assisted output**

Codex first translated the assignment into a frozen release specification. The
six stories were selected to exercise different stored structures: one or
multiple findings, one or multiple comparison publishers, analysis toggle
state, save/remove state, and return navigation. The specification fixed the
expected finding/comparison counts and pass/fail rules before execution. A
read-only API projection script checked structure, while the installed Reader
procedure checked the actual UI flow.

Evidence:

- frozen plan and reproducible script:
  [`ff16cd1`](https://github.com/snuhcs-course/swpp-2026-project-team-05/commit/ff16cd1);
- recorded release result:
  [`409074e`](https://github.com/snuhcs-course/swpp-2026-project-team-05/commit/409074e);
- final HE follow-up and test-evidence integration:
  PR [#87](https://github.com/snuhcs-course/swpp-2026-project-team-05/pull/87);
- final CI run:
  [GitHub Actions 42159845335](https://github.com/snuhcs-course/swpp-2026-project-team-05/actions/runs/42159845335).

**Results and an important distinction**

- On November 5, the release candidate passed Django 381, Web 25, and Android
  JVM 84 tests. The production API and installed Reader smoke both passed 6/6.
- On November 6, after the final HE/search changes, deterministic verification
  passed Django 381, Web 27, and Android JVM 84 tests, Web lint/build, Android
  lint/APK build, and all enforced coverage gates.
- The live six-story smoke was not repeated on November 6, so I kept the
  November 5 result and did not rewrite it as if it covered the later commit.
- A clean Python environment exposed a real dependency defect: our OpenRouter
  path imported `httpx` directly, but `requirements.txt` did not declare it.
  Twelve tests failed until the direct dependency was added
  ([`b9d03ef`](https://github.com/snuhcs-course/swpp-2026-project-team-05/commit/b9d03ef)).

**Human review and decisions**

I approved the fixed cases and criteria before the recorded run, checked the
phone experience while refining the release, and recorded the final demo. I
also required the documentation to say exactly what each result proved:

- deterministic tests and coverage support implementation correctness;
- the six-story smoke supports release integration and navigation;
- HE supports usability findings;
- a separate human-labelled evaluation is required for semantic comparison
  quality.

The criteria were not weakened after observing a failure. When the clean
environment failed, the dependency was fixed and the complete deterministic
suite was rerun.

---

## 4. What Worked Well and What Required Human Verification

### What worked well

- **Cross-stack continuity.** Once the product boundary was explicit, Codex
  could carry the same article, authentication, saved-state, and comparison
  contracts through Django, Android, Web, tests, and specifications.
- **Fast UI alternatives.** I could describe a specific problem observed on the
  phone, try a concrete alternative, and reject or refine it without waiting
  for a separate implementation cycle.
- **Traceable testing.** Writing the behavior specification before generated
  tests made the intended oracle visible. Test names and postconditions can be
  traced back to reviewed product/UI behavior.
- **Failure diagnosis.** Codex was effective at moving from an observed symptom
  (Korean composition, sheet wobble, clean-environment test failure) to the
  responsible state or dependency and adding a regression check.

### What required human verification or correction

| **Task** | **Problem in the AI-assisted output** | **How I found it** | **Correction** |
| --- | --- | --- | --- |
| T1 | Early designs risked making a topic cluster, rather than an article, the main unit of the product. | Compared the proposal with the team’s decision and the intended ordinary-news reading flow. | Kept an article-first feed/Reader; clusters and shared propositions support comparison behind the article. |
| T1 | Several schema ideas anticipated pipeline versions and regeneration before the product needed them. | Asked what each table was used for in the current UI/API. | Removed the run-centric product model and kept only article, finding, shared proposition, and comparison relationships needed by stored results. |
| T2 | The draggable comparison sheet wobbled around its intermediate state and consumed too much reading space. | Reproduced it on the connected Android phone. | Replaced it with a compact preview followed by an explicit full-detail surface; removed the middle anchor. |
| T2 | Pair pages repeated a broad explanation, making one issue appear to have several different explanations. | Read multi-publisher examples in the actual Reader and inspected stored payloads. | Added one proposition-level overview and kept only source-specific passages/details inside publisher pages. |
| T2 | Web search submitted incomplete Korean IME composition and rewrote the URL while typing. | Typed Korean search terms in the deployed Web client. | Kept input local, waited for composition end, debounced the final query, and stopped rewriting the URL. |
| T2 | Search-count/filter iterations changed tab widths and hid the Society control on mobile. | Checked successive APKs on the phone. | Removed result-count badges, preserved ordinary category navigation, and used fixed search-only text tabs. |
| T3 | Existing environments hid an undeclared direct Python dependency. | Recreated the backend in a clean virtual environment; 12 tests failed on `import httpx`. | Declared `httpx` explicitly and reran all backend tests and coverage gates. |
| T3 | It was easy to overstate a 6-case integration smoke as AI-quality evidence. | Compared the planned claim with the course testing categories and the separate human-evaluation work. | Documented it only as release/API/Reader integration evidence. |

### Verification practices

- [x] Reviewed product, UI, and test intent before keeping generated tests.
- [x] Used the connected Android phone and emulator for interaction feedback.
- [x] Checked Android and Web against the same authenticated API/database contract.
- [x] Kept regression tests with the IME, search-state, Reader-hierarchy, and
      state-transition fixes.
- [x] Ran the full deterministic Django, Web, and Android verification suites.
- [x] Froze six release stories and success criteria before the recorded smoke run.
- [x] Recorded exact commits, CI runs, APK hashes, and limitations in Testing Documentation.
- [x] Kept HE and human semantic evaluation separate from generated test evidence.

---

## 5. Reflection on How AI Affected My Development Process

**Productivity.** AI made the scale of this iteration possible. In one week, the
prototype became a cross-platform news application with an authenticated
backend, a structured analysis store, two clients, specifications, and broad
test coverage. It was especially effective when one change had to remain
consistent across the API, Android, Web, database migrations, and tests.

**Quality.** The quality did not come from one large prompt. It came from a
cycle of specifying, trying, observing, and narrowing. Several generated
solutions were technically plausible but wrong for the product: a cluster-first
information architecture, a draggable sheet with too many states, URL-coupled
search, and count badges that destabilized the mobile header. Direct use on the
phone was more informative than accepting a clean screenshot or a passing unit
test.

**Testing.** AI helped create far more tests, but generated tests are not useful
merely because they pass. The important change was to write down intent and
success criteria first, then map generated tests to those decisions. The clean
environment failure was also a reminder that a green working directory is not
the same as a reproducible build.

**Evidence boundaries.** This iteration clarified four different kinds of
evidence for me. CI checks code and contracts. Release smoke checks that a small
set of real product flows still work together. HE identifies usability
problems. Human evaluation judges the semantic quality of AI analysis. Combining
them into one success percentage would make the report look simpler but less
truthful.

**My main lesson.** AI was most valuable when I treated it as a fast
implementation and investigation partner, not as the owner of product taste or
the source of truth. I needed to understand enough of the architecture and UI
state to challenge its suggestions, reproduce failures, and decide when the
result was genuinely finished.

### Changes I will carry into the next iteration

| **What I learned** | **What I will do next** |
| --- | --- |
| A broad product prompt creates too many plausible architectures. | State the user’s primary object, non-goals, and current release boundary before asking for a schema or UI. |
| A UI can look reasonable and still feel unstable. | Keep checking interaction and navigation on a physical phone before merging gesture/state changes. |
| Generated tests can merely encode the generated implementation. | Continue freezing human-reviewed intent and test oracles before generating tests. |
| Existing environments can hide undeclared dependencies. | Include a clean-environment installation in release verification. |
| Small integration tests and AI-quality evaluation answer different questions. | Report them separately and preserve the dataset, rubric, model/prompt version, and human labels for semantic evaluation. |


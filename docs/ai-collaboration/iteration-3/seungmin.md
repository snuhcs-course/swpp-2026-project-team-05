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

The prompt blocks below consolidate the actual development conversation into
readable task instructions. Repeated follow-ups, shorthand, and typing errors
are normalized, but the requirements and decisions are limited to what I
actually provided during the work. They are not presented as verbatim one-shot
prompts.

### Example 1 — Changing the product from URL input to a news app (T1)

**Define** — The previous application asked the user to paste one article URL
and wait for analysis. After a team discussion, we wanted normal news-app
navigation: users should choose an outlet/article first and see analysis as an
additional reading layer. The first priority was not a new automatic collection
engine, but a robust way to store and display already-analyzed results.

**Consolidated initial direction**

```text
팀 회의 결과에 따라 FrameLESS를 URL 입력형 분석 도구에서 기사 중심
뉴스 앱으로 전환하려고 한다. 사용자는 우리가 선정한 여러 언론사의
기사를 일반 뉴스 앱처럼 탐색하고, 선택한 기사에 대해 미리 생성된
분석을 추가 레이어로 확인해야 한다.

먼저 이 전환이 기존 Android UI, 백엔드, 분석 결과 구조에 미치는
영향을 정리하고, 기사를 기본 탐색 단위로 두는 사용자 흐름과 데이터
모델을 제안해줘. 자동 수집·개인화는 후속 범위로 두되, 현재 설계가
향후 확장을 불필요하게 막지는 않아야 한다.
```

**Consolidated scope and implementation constraints**

```text
이번 이터레이션의 우선순위는 자동 분석 엔진 고도화가 아니라,
이미 분석된 기사와 결과를 안정적으로 저장·제공하는 제품 구조다.

현재 범위와 제약은 다음과 같다.
- 모든 사용자는 로그인한다. 개발자 계정과 일반 계정을 구분할 수 있어야 한다.
- 우선 정치·사회 기사만 다룬다.
- Reader는 기사 전문을 보여주고, 분석 상태와 관계없이 원문을 읽을 수 있어야 한다.
- 분석이 있는 기사는 저장된 finding과 타 기사 근거를 표시한다.
- 기존 URL 분석 기능은 남겨두지만 일반 사용자의 기본 탐색 UI에서는 제외한다.
- 수집 주기, 자동 클러스터링, 개인화는 이번 범위에서 제외한다.

구현 전에 필요한 핵심 엔티티, 관계, API 경계와 주요 UI 상태를 먼저 정리해줘.
향후 확장성은 유지하되, 현재 화면·API에서 사용하지 않는 추상화나
테이블을 미리 추가하지는 마.
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

**Consolidated Reader interaction prompt**

```text
현재 Android Reader의 multi-anchor bottom sheet는 중간 높이 전환에서
흔들림이 있고, 분석 문장을 선택하자마자 큰 분석 화면이 열려
기사 읽기를 방해한다. 기사 본문을 주된 화면으로 유지하면서 분석을
단계적으로 확인할 수 있는 progressive-disclosure 상호작으로 재설계해줘.

필수 사용자 흐름:
1. 본문의 분석 표현을 누르면 본문 위치를 유지한 채 compact preview를 보여준다.
2. preview에는 표현 설명과 필요한 최소 분석 정보만 보여준다.
3. 유효한 비교 기사가 있을 때만 사용자가 명시적인 action을 눌러 전체 비교 화면을 연다.
4. 전체 비교 화면에서는 언론사별 근거, 원문 링크, 이동·닫기 동작을 제공한다.
5. 불안정한 중간 sheet anchor에 의존하지 않는다.

preview·전체 화면·Reader 사이의 뒤로 가기, 바깥 영역 터치,
드래그 닫기, 비교 기사가 없는 경우의 상태 전환까지 함께 정의해줘.
```

After trying the first revision, I narrowed the behavior again:

```text
1차 구현을 확인한 결과, compact preview는 유지하되 전체 비교 화면은
기존의 정보 구성을 재사용하는 편이 낫다. 다음 상태 규칙을 반영해줘.
- 비교 기사가 없는 finding은 preview에서 설명을 완결하고 전체 화면 action을 제공하지 않는다.
- 사용자가 전체 비교 화면을 닫으면 preview까지 해제하고 깨끗한 Reader로 복귀한다.
- 기사 스크롤 위치와 분석 토글 상태는 유지한다.
- 스와이프·닫기 동작이 자명하면 별도의 제스처 안내 문구는 놓지 않는다.
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
현재 Web 검색에서 한글 IME 조합 중인 문자가 검색 상태와 URL에
즉시 반영되면서 입력이 분리되는 문제가 있다. 입력값·제출된 검색어·
페이지 URL의 소유권을 구분하고, 다음 조건을 만족하도록 수정해줘.
- composition 중에는 미완성 검색어를 제출하지 않는다.
- composition이 끝난 최종 입력만 debounce 후 검색한다.
- 검색어는 컴포넌트의 local state로 관리하고, 입력 매 타자마다 URL을 수정하지 않는다.
- 기존 피드의 category, scope, scroll 상태는 검색 종료 후 복원되어야 한다.

원인을 설명하고, 일반적인 React 검색 입력 패턴에 맞게 구현한 뒤
한국어 composition 회귀 테스트를 추가해줘.
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

**Consolidated planning prompt**

```text
다음 기능으로 글로벌 기사 검색을 추가하려고 한다. 아직 스펙이나 코드를
작성하지 말고, 먼저 제품 범위와 기술 계획을 제안해줘.

사용자에게 실제로 노출되는 DB의 모든 기사가 검색 대상이어야 한다. 현재
클라이언트에 로드된 페이지만 필터링하거나, 명확한 근거 없이 최근 며칠로
범위를 제한하면 안 된다.

계획에는 다음을 포함해줘.
- 검색 수행 주체를 백엔드와 클라이언트 중 어디로 둘지
- 검색 대상 필드와 피드 노출 조건
- 결과 정렬, excerpt, category filter, pagination/cursor 계약
- Android·Web의 입력, debounce, loading/empty/error 상태
- 현재 DB 규모에서의 실용성과 기사 수가 커졌을 때의 확장 경로
- 이번 버전에서 명시적으로 제외할 기능

장기적인 확장은 고려하되, semantic search나 별도 검색 인프라를 현재
규모에 필요하다고 가정하지 마. 대안별 trade-off와 추천안을 먼저 보고해줘.
```

The first answer was still only a plan. I then confirmed the ownership boundary
and asked for another review before the specification was written:

```text
검색은 백엔드가 전체 feed-visible corpus를 대상으로 수행하고, 클라이언트는
현재 로드된 기사 목록과 무관하게 결과를 요청하는 구조로 하자.

이 결정을 기준으로 최종 구현 계획을 다시 점검해줘. 특히 query 정규화,
일치 대상·우선순위, 숨김/comparison-only 기사 제외, cursor 무결성, 클라이언트
상태 복원과 경계값을 확인해줘.

설계에 불필요한 복잡성이 없다면 다음 단계로 제품·UI·테스트 스펙을
작성해줘. 스펙에는 precondition, postcondition, 오류·경계 케이스, non-goal,
검증 방법을 포함하고 아직 구현은 시작하지 마.
```

Only after reviewing that specification did I authorize implementation:

```text
검토한 검색 스펙을 기준으로 구현을 시작해줘. Django API, Android, Web,
관련 테스트와 문서를 한 PR 범위에서 일관되게 반영해야 한다.

구현 시 지켜야 할 사항:
- 테스트 oracle은 현재 코드가 아니라 검토된 스펙에서 가져온다.
- success, failure, boundary, pagination/state transition을 모두 다룬다.
- 검색이 없는 기존 피드 API의 응답 계약을 바꾸지 않는다.
- 스펙과 충돌하는 정보가 발견되면 임의로 구현을 선택하지 말고 충돌을 먼저 보고한다.
- 작업 후 스펙↔테스트↔구현 추적성과 실행 결과를 정리한다.
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

**Consolidated release and evidence prompt**

```text
이번 릴리스 작업 범위는 다음과 같다.
1. HE 피드백을 반영한 핵심 UI 수정 2~3개
2. 최종 main 기준 Django·Web·Android test/lint/build·APK 검증
3. 대표 기사 6건에 대한 production API + 설치된 Android Reader smoke test
4. `iteration-3-demo` branch, README, 데모 녹화 절차 및 제출 산출물 준비

수업 공지의 핵심은 key functionality의 success criteria를 실행 전에 명확히
정의하고, 적절한 success rate·latency·build/test 근거를 제시하는 것이다.

다음 원칙으로 진행해줘.
- 6개 기사와 각 케이스의 기대 구조·사용자 흐름·pass/fail 기준을 먼저 고정한다.
- 결과를 본 후 기준을 낮추거나 통과하도록 재정의하지 않는다.
- deterministic CI, API/Reader integration smoke, HE usability, AI semantic quality evaluation을 분리해 기록한다.
- smoke test를 분석 정확도 근거로 해석하지 않는다.
- 실패 시 테스트를 제거하거나 threshold를 낮추지 말고 원인을 수정한 뒤 재실행한다.
- 각 결과에 commit, CI run, APK hash, device/environment, 미실행 항목과 limitation을 남긴다.

데모 영상은 내가 직접 녹화할 것이므로, 핵심 기능이 가장 잘 드러나는
촬영 플로우를 제안해줘. 영상을 테스트 oracle로 대체하지는 마.
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


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

The prompt blocks below consolidate the actual development conversation while
preserving my conversational tone. Repeated follow-ups and obvious typing
errors are normalized, but the requirements and decisions are limited to what
I actually provided during the work. They are not presented as verbatim
one-shot prompts.

### Example 1 — Changing the product from URL input to a news app (T1)

**Define** — The previous application asked the user to paste one article URL
and wait for analysis. After a team discussion, we wanted normal news-app
navigation: users should choose an outlet/article first and see analysis as an
additional reading layer. The first priority was not a new automatic collection
engine, but a robust way to store and display already-analyzed results.

**Consolidated initial direction**

```text
팀 회의 결과 앱 방향이 좀 바뀌었어. 기존처럼 사용자가 URL을 직접
넣고 분석을 기다리는 구조보다, 이제는 여러 언론사의 기사를 일반 뉴스
앱처럼 보고 원하는 기사를 누르면 미리 분석된 결과가 같이 보이는 방식으로
가려고 해.

일단 이 방향으로 가면 기존 Android UI, 백엔드, 분석 결과 구조에서
뭐를 바꿔야 하는지부터 정리해봐. 기사가 기본 단위가 되는 사용자 흐름과
데이터 구조가 어떤 모양이어야 하는지도 같이 보고 싶어. 자동 수집이나 개인화는
나중 일이니까 지금 굳이 다 만들 필요는 없지만, 나중에 확장할 때 완전히
다 뜯어고쳐야 하는 구조는 피하자. 이해됨?
```

**Consolidated scope and implementation constraints**

```text
언론사 기사를 자동으로 수집하고 분석하는 엔진까지 지금 다 깎을 필요는
없어. 이번에는 이미 분석된 기사와 결과를 제대로 저장하고, 사용자가
뉴스 앱처럼 자연스럽게 보는 것까지가 우선이야.

범위는 이 정도로 잡자.
- 비로그인 사용자는 없고, 개발자 계정과 일반 계정은 구분할 수 있게 하자.
- 일단 정치·사회 기사만 다루자.
- 기사 본문은 전부 보여주고, 분석이 없어도 기사는 읽을 수 있어야 해.
- 분석이 있으면 해당 문장의 finding과 다른 기사 근거를 연결해 보여주자.
- URL 분석 기능은 없애지 말고, 기본 UI에서만 빼자.
- 수집 주기, 자동 클러스터링, 개인화는 나중에 다루자.

바로 코드부터 짜지 말고, 핵심 엔티티와 관계, API 경계, 주요 UI 상태를
먼저 정리해봐. 확장성은 있어야 하지만 괜히 10년 뒤를 가정해서 지금 안 쓰는
테이블이나 추상화를 막 만들지는 마. 적당히 robust하게 가면 돼.
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
지금 Android Reader에서 하단 시트를 중간 정도까지 올릴 때는 괜찮은데,
그 이상 드래그하면 창이 위아래로 워블하면서 불안정해. 그리고 기사를 읽다가
문장 하나 눌렀다고 바로 큰 화면으로 가는 것도 조금 정신사나운 것 같아.

내 생각에는 문장을 누르면 일단 기사 위에 작은 미리보기가 뜨고, 거기서
더 보기를 눌렀을 때만 전체 비교 화면이 열리는 2단계 방식이 좋을 것 같은데
어때? 기사가 주인 화면인 건 유지하면서 분석만 점진적으로 펼칠 수 있게
전반적인 흐름을 재설계해봐.

일단 생각한 흐름은 이런 느낌이야.
1. 본문의 분석 표현을 누르면 스크롤 위치를 유지한 채 작은 preview를 보여준다.
2. preview에서는 표현 설명과 필수 정보만 빠르게 확인한다.
3. 실제 비교 기사가 있을 때만 사용자가 명시적으로 눌러서 전체 화면을 연다.
4. 전체 화면에서는 언론사별 근거와 원문 링크까지 제대로 본다.
5. 지금처럼 애매한 중간 sheet anchor는 없애자.

이 방식으로 할 거면 preview, 전체 화면, Reader 사이에서 뒤로 가기나 바깥 영역
터치, 드래그 닫기가 각각 어떻게 동작할지도 말 되게 정리해줘.
```

After trying the first revision, I narrowed the behavior again:

```text
직접 써보니 첫 번째로 뜨는 compact preview는 지금 방식이 괜찮아. 다만 그걸
눌러서 뜨는 전체 비교 화면은 이전의 정보 구성을 살리는 게 낫겠어.

세부 동작은 이렇게 가자.
- 비교 기사가 없으면 preview에서 설명이 끝나야 하고, 굳이 전체 화면을 열 필요는 없어.
- 전체 비교 화면을 닫았다는 건 이미 충분히 보았다는 뜻이니까 preview도 다시 뜨지 않게 해.
- 기사 스크롤 위치와 분석 토글 상태는 그대로 유지해야 해.
- 스와이프나 닫기 동작이 보면 알 수 있는 수준이면 '좌우로 넘기세요' 같은 안내 문구는 빼자.

이 규칙들이 서로 충돌하지 않는지 상태 전환을 한 번 더 보고 반영해줘.
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
검색을 직접 해보니 한글이 조합되기도 전에 검색이 나가서 글자가 따로따로
쳐져. 그리고 키를 하나 누를 때마다 상단 URL이 바뀌는 것도 굳이 필요한지
모르겠어. 이게 정말 정석적인 방식인지부터 다시 보자.

내 생각에는 사용자가 입력 중인 값과 실제로 제출된 검색어, 페이지 URL 상태를
분리해야 할 것 같아. 한국어 composition 중에는 요청을 보내지 말고, 조합이 끝난
최종 검색어만 debounce하자. 검색어는 local state로 관리하고 URL은 그냥 유지하는
게 더 깔끔한 것 같아.

본질적인 원인을 확인한 뒤 고치고, 다시 깨지지 않게 한국어 IME 회귀 테스트도 넣어줘.
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
일단 다음 기능은 기사 검색으로 하자. 근데 바로 스펙이나 코드부터 쓰지 말고,
어떻게 하는 게 맞는지 계획부터 세워보자.

내가 궁금한 건 이거야. 우리 DB에서 실제로 사용자에게 보여주는 기사는 전부
검색 대상이어야 할 것 같은데, 프론트가 지금 가지고 있는 몇 개만 필터링하면
안 되는 거 맞지? 그렇다고 최근 며칠로 임의로 잘라버리는 것도 이상하고.

검색을 백엔드와 클라이언트 중 어디서 해야 하는지, 제목·언론사·본문 중
어디까지 찾을지, 정렬·excerpt·category filter·pagination을 어떻게 할지,
Android와 Web은 어떤 상태를 가져야 하는지를 정리해봐.

나중에 기사가 엄청 많아졌을 때 어떻게 확장할지도 보긴 보되, 지금부터 semantic
search나 별도 검색 서비스까지 들이밀 필요는 없어. 지금 규모에서 정석적이고
깔끔하게 시작하면서 확장 경로는 남기는 방식으로 추천해줘.
```

The first answer was still only a plan. I then confirmed the ownership boundary
and asked for another review before the specification was written:

```text
오케이. 그럼 검색은 백엔드가 전체 feed-visible corpus를 대상으로 하고,
프론트는 지금 로드한 기사와 관계없이 결과를 요청하는 구조로 가는 게 맞네.

그 기준으로 최종 계획을 한 번 더 점검해봐. query 정규화, 제목·언론사·본문
일치 우선순위, 숨김 기사와 comparison-only 기사 제외, cursor가 다른 검색어에
재사용되지 않게 하는 것, 검색을 닫았을 때 기존 피드 상태가 복원되는 것까지
빠진 게 없는지 보자.

더 고칠 부분이 없으면 그때 product, UI, test 스펙을 작성해줘. precondition,
postcondition, 오류·경계 케이스, 이번에 안 할 것, 각 항목을 어떻게 검증할지까지
들어가야 해. 아직 구현은 하지 말고 스펙까지만 가자.
```

Only after reviewing that specification did I authorize implementation:

```text
오케이, 스펙은 이 정도면 괜찮은 것 같아. 이제 그 스펙 기준으로 구현하자.
Django API, Android, Web, 관련 테스트와 문서까지 한 번에 일관되게 반영해줘.

다만 테스트를 지금 구현한 코드에 맞춰서 만들지 말고 방금 검토한 스펙을
기준으로 해. 정상 케이스만 보지 말고 실패, 경계값, pagination과 상태 전환도
보고, 검색을 쓰지 않는 기존 피드 계약은 깨지 않게 해.

하다가 스펙과 충돌하는 게 나오면 네 편한 대로 구현하지 말고 먼저 알려줘.
마지막에 스펙, 테스트, 구현이 서로 어떻게 연결되는지와 실행 결과도 정리해줘.
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
자, 이번에 내가 마무리할 범위는 HE 피드백 UI 수정 2~3개, 최종 main
test/lint/build와 APK, 대표 6기사 smoke test, iteration-3-demo branch와 README·데모
준비야.

근데 그냥 '테스트 많이 했다'로 쓰면 의미가 없으니까, 수업 공지처럼 핵심
기능별 success criteria를 실행 전에 먼저 고정하고 success rate·latency·
build/test 근거를 남기자.

6개 기사는 왜 골랐는지, 각각 어떤 데이터 구조와 사용자 흐름을 보는지,
뭐가 통과와 실패인지를 먼저 쓰고 시작해. 결과를 본 다음에 통과하도록 기준을
바꾸면 안 돼. 실패하면 테스트를 빼지 말고 원인을 고친 다음 다시 돌리자.

그리고 CI, API/Reader smoke, HE 사용성, AI 분석 품질 평가는 서로 증명하는 게
다르니까 나눠서 쓰자. 6기사 smoke를 분석 정확도 근거로 포장하면 안 돼.
각 결과에 정확한 commit, CI run, APK hash, 기기·환경, 안 돌린 항목과 limitation까지
남겨줘.

데모 영상은 내가 직접 찍을 테니까 핵심 기능이 잘 보이는 촬영 순서만 정리해줘.
영상을 테스트 증거 자체로 대체하지는 말고.
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


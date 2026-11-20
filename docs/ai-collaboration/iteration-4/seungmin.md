# AI Collaboration Contribution — Iteration 4

|                     |                                                                 |
| ------------------- | --------------------------------------------------------------- |
| **Team**            | Team 5 — FrameLESS                                              |
| **Iteration**       | Iteration 4 — Guest/Offline Access and Analysis Integration     |
| **Period**          | November 7, 2026 – November 20, 2026                            |
| **Iteration goals** | Make the news reader useful before sign-in, preserve selected reading offline, and connect stored cross-article analysis to normal article navigation. |
| **Author**          | Kim Seungmin — Individual contribution                          |

---

## 1. Summary of AI Usage

I used Codex as a specification, implementation, and review partner for three
cross-layer changes in Iteration 4. First, I designed guest access and Android
offline reading around the smallest product boundary we actually needed.
Second, I made articles referenced by a comparison behave like normal
first-class Reader articles instead of dead-end evidence cards. Third, I
reviewed and hardened a teammate's pasted-link analysis branch before it was
merged and deployed.

The collaboration followed a repeated sequence: inspect the current product and
specifications, discuss the minimum acceptable behavior, record the decision in
feature and test specifications, implement it across the affected layers, and
then validate the result with automated checks and a physical Android device.
I did not accept generated architecture merely because it was extensible. When
an early proposal introduced state or synchronization that the current product
did not need, I narrowed it before implementation.

AI output was also not treated as deployment or UX evidence. I checked the
final diffs, ran the relevant Django/Web/Android commands, installed builds on a
physical phone, and separately checked the production Vercel deployment. The
resulting PR descriptions report those actual checks rather than a model's
prediction that the code should work.

### AI tool used

| **Tool** | **Model / version** | **Used by** | **Main purpose** |
| --- | --- | --- | --- |
| Codex | Not reliably recorded | Kim Seungmin | Architecture discussion, specification drafting, cross-stack implementation, review, testing, and release verification |

### Deliberately not delegated

- I decided which guest actions remain unavailable and which data may be kept
  locally without an account.
- I decided not to add anonymous server users, account-merging APIs, an offline
  write queue, or background synchronization in this iteration.
- Codex did not decide whether an A–G finding or comparison was editorially
  correct. The live analysis quality and the grouped-analysis pipeline remained
  separate human/team review concerns.
- I made the final mobile interaction and release decisions after using the app
  on a physical Android device.
- The team, not the tool, owns the production credentials, model-cost policy,
  and decision to deploy.

---

## 2. Major Tasks Where AI Contributed

| **#** | **Task** | **Related deliverable** | **AI role** | **My role** |
| --- | --- | --- | --- | --- |
| T1 | Support useful news reading before sign-in and offline access to locally saved Android articles | `guest-offline-reading` feature/test specs, Android Room cache, guest Web/Android flow, [PR #89](https://github.com/snuhcs-course/swpp-2026-project-team-05/pull/89), [PR #90](https://github.com/snuhcs-course/swpp-2026-project-team-05/pull/90) | Compared design choices, drafted specs/code/tests, diagnosed state regressions, and prepared verification steps | Set the product boundary, rejected unnecessary server/sync machinery, tested online/offline state transitions on a phone, and accepted the final behavior |
| T2 | Open compared coverage as a normal Reader article and allow it to be saved | Reader/API/navigation changes on Android and Web, [PR #91](https://github.com/snuhcs-course/swpp-2026-project-team-05/pull/91), [PR #92](https://github.com/snuhcs-course/swpp-2026-project-team-05/pull/92) | Traced the existing data model, drafted navigation/state changes and tests, and iterated on passage positioning | Required first-class article behavior, chose internal navigation over external-link-first UX, and verified the referenced passage and back behavior on a phone |
| T3 | Review and harden the pasted-link analysis before merge | `specs/features/link-analysis.md`, `specs/testing/link-analysis.spec.md`, commit [`dc394d5`](https://github.com/snuhcs-course/swpp-2026-project-team-05/commit/dc394d5), [PR #93](https://github.com/snuhcs-course/swpp-2026-project-team-05/pull/93) | Audited the branch against existing data, auth, error, and deployment contracts; drafted focused fixes and regression tests | Chose the release-blocking findings, kept the feature developer-only, reviewed the final patch, and verified CI/deployment separately |
| T4 | Keep implementation, specifications, tests, and production claims aligned | PR descriptions, canonical checks, physical-device smoke, and Vercel verification | Maintained a checklist across the affected layers and summarized evidence | Ran or inspected the actual commands, distinguished deterministic tests from live-model quality, and decided when the work was ready to merge |

---

## 3. Representative Prompts, Outputs, and Human Decisions

The prompt blocks below consolidate consecutive development requests while
preserving my conversational tone and the constraints I actually supplied.
Obvious repetition and typing errors are normalized. They are not presented as
verbatim one-shot prompts. The final team report can link the original-input
history separately in the team's prompt log.

### Example 1 — Guest access and Saved-only offline reading (T1)

**Define** — The app required sign-in even to read public news, and Android had
no dependable offline path. I wanted both Web and Android to remain useful
before sign-in, but I did not want a speculative anonymous-account system or a
large synchronization subsystem.

**Consolidated planning direction**

```text
비로그인 사용과 로컬 DB를 이번 iteration에 넣으려고 하는데, 바로 구조부터
크게 만들지는 말고 현재 Android, Web, Django 흐름을 먼저 확인해봐.

원하는 핵심 동작은 이 정도야.
- 로그인하지 않아도 공개 피드, 검색, 기사 본문과 저장된 분석은 읽을 수 있다.
- 저장, 팔로우 같은 개인 기능은 로그인한 사용자에게만 제공한다.
- Android는 사용자가 저장한 기사의 본문과 분석을 로컬에 보관하고, 오프라인일
  때는 실제로 읽을 수 있는 저장 기사만 보여준다.
- 웹은 게스트 읽기를 지원하지만 굳이 오프라인 저장소까지 만들 필요는 없다.

서버 guest 계정, 로그인 뒤 자동 병합, background sync나 offline write queue가
정말 필요한지도 따져봐. 지금 필요 없으면 과감히 빼고, 데이터 소유권과 화면
상태가 헷갈리지 않는 최소 구조로 계획을 먼저 줘. 아직 구현부터 하지 말고.
```

After reviewing the first plan, I narrowed the state transitions further:

```text
오프라인 화면에서는 캐시가 있다고 아무 기사나 보여주면 안 되고, 현재 사용자가
Saved로 확정해서 완전한 본문과 분석이 저장된 기사만 보여주자. 로그인한 사용자의
Saved 상태와 게스트 상태가 섞이면 안 되고, 오프라인에서 저장/해제 같은 mutation은
성공한 것처럼 보이면 안 돼.

이 기준으로 product/UI/test 스펙을 먼저 확정하고, 기사 상세와 분석, 비교 기사까지
오프라인에서 읽히는지 검증할 수 있게 구현해. 쓸데없는 백엔드 테이블이나 API는
추가하지 말고, 실제 폰에서 온라인 → 오프라인 → 온라인 전환을 확인할 수 있게 해.
```

**AI-assisted output**

Codex drafted the guest/offline specifications and implemented a local Android
Room cache for complete Saved article copies, their stored analysis, and the
active user's Saved membership. The online feed and search became public read
operations; account-bound mutations remained gated. In the offline state,
Android uses the Saved-only local projection rather than presenting every
article that happened to be cached.

The implementation was revised through three focused fixes after state-level
review: limiting offline results to eligible Saved articles, tightening active
user Saved state, and preserving the Saved state when a cached article opens in
the offline Reader. These changes are visible in commits `3933661`, `9c4c302`,
and `038c662` within [PR #89](https://github.com/snuhcs-course/swpp-2026-project-team-05/pull/89).

**Human verification and integration**

- **Kept:** read-only guest access, complete Saved article snapshots on
  Android, Saved-only offline feed, and clear authentication gates around
  personal mutations.
- **Changed:** cache eligibility and ownership after testing online/offline and
  signed-in/signed-out transitions.
- **Rejected:** anonymous server identities, guest-to-account merging, queued
  offline writes, broad article downloads, and a Web offline database.
- **Evidence:** 104 Android unit tests, Android lint, a built and installed
  debug APK, and physical-device smoke covering online feed/search, Saved
  navigation, offline Saved-only feed, cached article/analysis/comparison
  reading, and rejected offline mutations. The follow-up specification audit is
  recorded in [PR #90](https://github.com/snuhcs-course/swpp-2026-project-team-05/pull/90).

### Example 2 — Treating compared coverage as a first-class article (T2)

**Define** — The stored analysis already referenced other articles, but the
Reader treated those sources mainly as evidence fragments or external links.
Because those compared articles are part of the same reviewed dataset, I wanted
the user to open them in the normal Reader, see their own available analysis,
and save them like any other visible article.

**Consolidated direction**

```text
지금 A기사의 분석에서 비교 대상으로 보이는 B기사도 우리 DB에 들어있는 정상
기사잖아. 그러면 외부 원문 링크만 보여주고 끝내지 말고, B기사 자체를 Reader로
열 수 있어야 하는 게 자연스러운 것 같아.

다만 feed에 일부러 노출하지 않는 comparison-only 기사까지 갑자기 검색 결과에
나오게 하지는 말고, 분석 문맥 안에서 보이는 기사만 내부 이동을 허용하자. 이동한
기사에서도 본문, 그 기사에 속한 분석, 스크랩이 정상적으로 동작해야 하고 Web과
Android가 같은 article id/API 계약을 써야 해.

현재 DB 구조를 억지로 우회하지 말고, 기존 관계로 가능한지 먼저 확인해. 필요한
백엔드/serializer/navigation 변경만 하고 product, UI, testing 스펙도 같이 맞춰줘.
```

The first implementation opened the correct Reader but did not reliably place
the referenced passage in view on Android. I refined the requirement after
testing it:

```text
비교 기사로 넘어가는 액션은 외부 원문보다 더 명확한 primary action이어야 해.
그런데 지금은 Reader가 열려도 참조한 문장으로 바로 안 가는 경우가 있어.

Compose layout이 실제로 측정되기 전에 scroll을 요청하는 건 아닌지 확인하고,
기사 내용을 그린 뒤 해당 passage가 보이도록 이동하게 해. 독립 finding이 없는
비교 기사도 왜 이 기사로 넘어왔는지 문맥은 보여줘야 하고, 뒤로 가면 원래 분석
화면으로 자연스럽게 돌아와야 해. 실제 기기에서 passage가 화면에 들어오는 것까지
확인해줘.
```

**AI-assisted output**

Codex traced the existing comparison target back to a stored `Article`, exposed
an internal Reader action on Android and Web, kept external originals as a
secondary source action, and allowed a comparison-only article reached through
that path to participate in Saved. The Android follow-up waits for layout
measurement before scrolling to the referenced passage and carries comparison
context when a target article has no standalone finding of its own.

**Human verification and integration**

- **Kept:** internal article IDs as the navigation contract, the existing
  source-linked comparison evidence, normal Reader/Saved behavior, and a
  secondary original-source link.
- **Changed:** the primary CTA, target context, and Android scroll timing after
  physical-device testing.
- **Rejected:** exposing comparison-only articles indiscriminately in the normal
  feed/search or treating the external publisher page as the only destination.
- **Evidence:** [PR #91](https://github.com/snuhcs-course/swpp-2026-project-team-05/pull/91)
  passed 383 Django tests, 32 Web tests plus lint/build, and the Android unit,
  lint, debug APK, and instrumentation APK builds. [PR #92](https://github.com/snuhcs-course/swpp-2026-project-team-05/pull/92)
  added focused Android device verification that the referenced passage was in
  view.

### Example 3 — Reviewing and hardening the pasted-link analysis branch (T3)

**Define** — A teammate implemented a developer flow that discovers related
coverage, runs staged A–G analysis, stores the result, and opens the resulting
Reader article. The happy path worked, but merging a model- and search-backed
runtime import required review against our existing publisher data, auth/cost
policy, error contract, and deployment model.

**Consolidated review request**

```text
link-analysis-v6 브랜치는 준서님이 구현했고 우리가 검토한 다음 머지하기로 했어.
기능이 한 번 된다는 것만 보지 말고 현재 main의 데이터 모델, 기존 import 규칙,
인증/비용 정책, Vercel 배포 환경 기준으로 제대로 리뷰해봐.

특히 확인할 건 이거야.
- 이미 저장된 URL이면 fetch/search/model을 다시 호출하지 않고 기존 기사를 여는지
- 실행 중 만들어진 작은 dataset이 기존 언론사 이름, domain, logo, display order를
  덮어쓰지 않는지
- 비용이 드는 분석을 누가 실행할 수 있는지
- fetch, model, validation, DB 저장 실패가 구분 가능한 API 오류로 내려오는지
- 동시 실행 제한이 Vercel 여러 instance에서도 어떤 의미인지
- 자동 테스트는 네트워크와 실제 model 없이도 중요한 계약을 잡는지

바로 갈아엎지 말고 release-blocking인 것과 후속 한계를 나눠서 보고하고, 필요한
부분만 고쳐. 스펙과 테스트도 실제 동작에 맞추고, 마지막에는 CI와 실제 Vercel
배포가 main merge 뒤 갱신되는지도 별도로 확인하자.
```

**AI-assisted review and output**

The review identified five concrete release risks:

1. a known canonical URL could still be fetched before the existing record was
   reused;
2. the endpoint could reject even an existing URL when no model key was
   configured;
3. a runtime dataset import could overwrite reviewed publisher metadata with
   incomplete values discovered through an aggregator;
4. an ordinary signed-in account could start a paid model-backed analysis;
5. validation or database failures during import could escape as generic server
   errors.

Commit [`dc394d5`](https://github.com/snuhcs-course/swpp-2026-project-team-05/commit/dc394d5)
added an early exact-URL reuse path, moved model availability behind that path,
introduced `preserve_existing_publishers=True` for incremental runtime imports,
restricted new analyses to developer accounts, translated expected storage
failures into a retryable `storage_unavailable` response, and added regression
tests for those contracts. The single-process semaphore was retained as an
explicit limitation rather than misrepresented as a distributed lock.

**Human verification and integration**

- **Kept:** the teammate's staged discovery/analysis pipeline, grounded dataset
  conversion, progress UI, and hidden-from-feed Reader result.
- **Changed:** only the production data-policy, authorization, failure, and
  reuse boundaries needed for safe integration.
- **Deferred:** distributed job coordination and automatic ingestion at scale;
  those were not necessary to validate the Iteration 4 developer flow.
- **Evidence:** the final [PR #93](https://github.com/snuhcs-course/swpp-2026-project-team-05/pull/93)
  passed 397 deterministic Django tests and the Android unit/lint/debug build.
  Live model/search execution and the production deployment were checked
  separately because deterministic CI intentionally contains no credentials.

---

## 4. What Worked Well and What Required Human Verification

### What worked well

- Codex could follow one behavior across Django, Web, Android, specifications,
  and tests, which made cross-layer contract changes substantially faster.
- Asking for a plan before code exposed unnecessary guest-account and sync
  machinery early, when it was cheap to remove.
- It was effective as a review assistant for an unfamiliar teammate branch:
  URL reuse, publisher metadata preservation, authorization, and failure
  mapping were easier to inspect as one system rather than as isolated files.
- It produced focused regression-test scaffolding tied to reviewed
  specifications instead of requiring live model calls in CI.
- Repeated short feedback from a physical phone was enough to refine subtle
  state and layout behavior without rewriting the whole Reader flow.

### What required human verification or correction

| **Task** | **Issue or limitation** | **How it was detected** | **Correction / decision** |
| --- | --- | --- | --- |
| T1 | A broadly extensible design could have added anonymous server identities, account merging, and synchronization before the product needed them. | Product-scope review before implementation | Restricted v1 to public reads, account-gated mutations, and Android Saved snapshots; omitted speculative backend state. |
| T1 | A cache-based offline feed could accidentally expose any previously cached article or mix Saved membership between users. | State-transition tests and physical-device online/offline checks | Made offline data Saved-only, user-scoped, and read-only; added focused regressions in PR #89. |
| T2 | Opening the correct article was not sufficient when the referenced passage was still off-screen. | Physical-device Reader test | Waited for layout measurement before scrolling and carried comparison context into the target Reader. |
| T3 | The original happy-path link-analysis branch could mutate reviewed publisher metadata and repeat paid work for a known URL. | Cross-layer code/data review | Added safe incremental import and early exact-URL reuse with regression tests. |
| T3 | An in-process semaphore can serialize one server process but is not a distributed job lock across Vercel instances. | Deployment-model review | Documented the limitation and deferred distributed job infrastructure instead of claiming stronger protection. |
| T4 | A merged commit or green local test does not prove that production was updated or that live LLM output is correct. | Separate GitHub Actions, Vercel, and live/device checks | Kept deterministic CI, deployment verification, and editorial quality evaluation as separate evidence. |

### Verification practices I used

- [x] Reviewed the final Git diff and specification changes before merge
- [x] Required regression tests for offline ownership/state, internal Reader
  navigation, and link-analysis data/auth/error contracts
- [x] Ran or inspected the canonical Django, Web, and Android checks recorded in
  PRs #89–#93
- [x] Built and installed the Android debug APK on a physical device
- [x] Tested guest, signed-in, online, offline, Saved, Reader, analysis, and
  compared-article navigation state transitions
- [x] Checked production Vercel behavior separately from deterministic CI
- [x] Kept live model quality claims separate from API/schema correctness

---

## 5. Reflection on How AI Affected My Development Process

**Productivity** — AI was most useful after the ownership and invariants of a
feature were explicit. It shortened the path from an accepted state model to
changes across Android, Web, Django, specs, and tests. It was also useful for
reviewing a teammate's branch because it could trace a runtime import through
the API, database, and Reader rather than checking only the visible happy path.

**Quality** — The largest quality gain did not come from generating more code.
It came from forcing a design to state what it would not build and from turning
physical-device failures into explicit regression contracts. Conversely, an
apparently complete architecture was not necessarily the right architecture for
this iteration. Guest accounts, sync queues, and distributed jobs would have
expanded the failure surface without improving the demonstrated reading flow.

**Team workflow** — Keeping specifications, tests, and PR evidence together
made AI-assisted work reviewable by the rest of the team. For the link-analysis
integration, I preserved the teammate's analysis pipeline and limited my patch
to production safety and integration boundaries. This was more useful than
rewriting the branch under a different design and made authorship clearer.

**Learning** — My most reliable prompt pattern was: describe the user-visible
goal, state which existing contracts must remain true, list tempting but
out-of-scope infrastructure, ask for a plan/specification first, and only then
allow implementation. For deployment- or device-sensitive changes, the prompt
cannot substitute for actual environment evidence.

### Takeaway for the next iteration

- For cross-layer features, ask for the state model and excluded scope before
  asking for code.
- Treat generated tests as proposed claims until their oracles are checked
  against the reviewed specification.
- Use AI to inspect breadth; use a human to decide product boundaries, data and
  cost policy, and whether the installed experience is acceptable.
- Keep deterministic CI, production deployment checks, physical-device UX, and
  human evaluation of analysis quality as separate evidence.

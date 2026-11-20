# AI Collaboration Contribution — Iteration 4

|                     |                                                                    |
| ------------------- | ------------------------------------------------------------------ |
| **Team**            | Team 5 — FrameLESS                                                 |
| **Iteration**       | Iteration 4 — UAT Preparation and First-run Tutorial               |
| **Period**          | November 7, 2026 – November 20, 2026                               |
| **Iteration goals** | Finalize the five UAT user stories, add a first-run tutorial that explains highlights and labels, and keep the team's meeting log current. |
| **Authors**         | Doh Seoyoung — Individual contribution                             |

---

## 1. Summary of AI Usage

I used Claude (claude.ai) for four things in Iteration 4:

- writing the five user stories on our UAT sheet;
- designing and implementing the first-run tutorial;
- drafting the 11-09 Daily Standup Meeting Log entry;
- understanding the UAT format.

The tutorial was the largest piece. Claude produced a design mock-up and then a Kotlin/Compose implementation (1,082 lines including tests and specs). I pushed it from my own account, and it reached `main` through PR #95.

I gave Claude the facts it could not know: which features the app still has, what the UI looks like, and how many label types there are. It repeatedly assumed features from our old proposal, so most of my corrections were about scope rather than wording.

**Deliberately not used.** AI did not run or score the UAT sessions, fill in tester results, or decide which features the app keeps. Those were team decisions recorded in the 11-09 meeting.

### AI tools used

| **Tool** | **Model / version** | **Used by** | **Main purpose** |
| --- | --- | --- | --- |
| Claude (claude.ai) | Not recorded | Doh Seoyoung | UAT user stories, tutorial design and implementation, meeting log draft, UAT explanations |

---

## 2. Major Tasks Where AI Contributed

| **#** | **Task** | **Related deliverable** | **AI role** | **Contribution level** | **Human owner** |
| --- | --- | --- | --- | --- | --- |
| T1 | Write five non-overlapping user stories ordered by risk | UAT sheet, User Story tab (11-13 draft) | Drafted and revised stories | Medium | Doh Seoyoung |
| T2 | Design and implement the first-run tutorial: two onboarding pages, three coach marks, a `?` label guide | Commit [`0ce31eb`](https://github.com/snuhcs-course/swpp-2026-project-team-05/commit/0ce31eb) on `feature/first-run-tutorial`, merged via PR #95 | Design mock-up, Compose code, unit tests, feature/test specs | High | Doh Seoyoung |
| T3 | Turn the 11-09 meeting transcript into a log entry matching the existing format | Wiki "Daily Standup Meeting Log" (11-09 entry, pasted 11-20) | Drafted the entry and a "확인 필요" list | High | Doh Seoyoung |
| T4 | Explain UAT and the sheet's Pos/Neg column | Background for T1 | Explained course material | Low | — |

---

## 3. Representative Prompts and Outputs

Prompts are quoted verbatim.

### Example 1 — UAT user stories (T1)

**Define** — The sheet needed five risk-ordered stories that do not test the same thing. Stories cannot change after Iteration 3.

**Context provided** — The team's UAT sheet (three stories) and the Week 11-1 slides.

**Prompt** (11-09)

```text
근데 혹시 이렇게 완성된 5개가 내용이 좀 겹치지 않아 ? 그건 상관없나 ?
```

**Output** — Claude split overlaps into two kinds:

- dependencies, which are fine and become a Given;
- the same result checked twice, which violates INVEST's "Independent" and should be fixed.

It then proposed dropping sign-in as a separate story.

**Verify** — Claude's drafts kept assuming features we had dropped. I corrected it three times:

```text
우리 근데 임포트 안 하기로 했어,, 그냥 약간 언론사 앱처럼 서비스 들어가면 첫 페이지에서 여러 bunch of 기사들이 보이게끔 하려고 해서. 다른 requirement가 필요할 거 같아
```

```text
아니야 근데 covereages covering the same event를 보여주는 건 아니고, 그냥 일반 뉴스 앱처럼 다양한 주제의 기사들을 보여주는 거야
```

```text
여기서 말하는 source traceability는 original article이 아니라 other coverage covering the same passage 인거지 ?
```

**Integrate**

- **Kept:** Five stories in risk order:
  1. Expression comparison
  2. Source traceability
  3. Home feed
  4. Search and filter
  5. Save
- **Modified:** Article import was removed. Sign-in became the Given of the Save story.
- **Result:** These five requirements are on the submitted sheet. Teammates wrote the 48 test cases.

### Example 2 — First-run tutorial, from mock-up to code (T2)

**Define** — HE feedback said the meaning of highlights and labels was not obvious. At the 11-09 meeting I took on the tutorial content.

**Context provided** — Four app screenshots, the rubric spec, and the repository (read-only).

**Prompts** (11-16)

```text
UI야. 살펴보고 튜토리얼에서 다뤄야 할 후보 기능들을 분석해봐
```

```text
1. '로그인하러 가기'야
2. 73은 에뮬레이터 화면이라 신경 안 써도 돼
3. 라벨 칩은 6-7가지고, 이에 대해서 따로 알려주긴 해야될 거 같아. 약간 'help' 버튼 (or '?' 버튼) 누르면 팝업창으로 판정 루브릭 정보 등장하게끔
```

```text
일단 좋은 거 같아. 그럼 이제 기존 github에 올라와있는 내용들을 보고, 이 튜토리얼 페이지들을 앱에 추가로 구현해넣어야 할 거 같아. 확인해봐
```

**Output**

- **A six-screen design mock-up:** two onboarding pages, three coach marks, and the `?` pop-up. It included the line "FrameLESS는 기사가 편향됐다고 판정하지 않아요. 같은 사건을 다르게 표현한 부분을 보여줄 뿐이에요."
- **A patch with the implementation:**
  - `OnboardingScreen`, `CoachMarkOverlay`, `ExpressionLabelGuide`, `TutorialViewModel`, and `TutorialStore`;
  - two unit-test files;
  - feature and test specs.
- **A note on the label names:** the mock-up's label names were Claude's own drafts, but the code used the seven real chip names already in the app (`categoryLabel`, A–G).

**Verify**

- Claude said plainly that it could not build the code: no Android SDK and no Gradle access.
- I could not build it locally either. Java was missing, and then Gradle could not download a plugin on my network.
- The code was checked by CI and teammates after the merge.
- Claude also flagged a conflict: `news-app-features.md` §12 says "do not add an onboarding flow". It wrote this into the new spec as a team decision instead of ignoring it.

**Integrate**

- **Kept:** The whole flow and the "판정하지 않는다" line.
- **Result:**
  - I pushed the branch from my account (`0ce31eb`, 13 files, +1,082/−21).
  - A teammate merged it into `feat/tutorial-replay` and added a "replay tutorial" entry from the feed (`2b5ea2a`).
  - It reached `main` through PR #95 on 11-16.

### Example 3 — Meeting log entry (T3)

**Context provided** — My standing log prompt (format, areas, "do not invent anything", mark unclear items with (?)), the Wiki page, and the 11-09 Clova transcript.

**Verify** — Claude first said the log ended on 10-05. Its web fetch had returned an old cached page. I corrected it:

```text
엥 10/31까지 채워져있는데 ? 다시 봐봐 (leej32님이 2주 전에 마지막으로 수정했다고 나와
)
```

Claude then read the Wiki repository directly, found nine entries up to 10-31, and matched the newer format (attendees line, "📎 이전 로그"). The transcript labeled speakers only as "참석자 1–8", so Claude inferred names and listed every guess under "확인 필요".

**Integrate** — I pasted the entry into the Wiki on 11-20 (`b316d9a`).

---

## 4. What Worked Well and What Required Human Verification

### What worked well

- **From feedback to working code in one day.** HE feedback became a design mock-up, then a merged feature with tests and specs, on 11-16.
- **Honest limits.** Claude said it had not built the code, left a spec conflict for the team to decide, and listed uncertain speaker names instead of guessing silently.
- **Story quality.** The INVEST check found real overlaps, such as import vs. comparison and import vs. search, before the stories were frozen.

### What required human verification or correction

| **Task** | **Issue in AI output** | **How it was detected** | **Correction made** |
| --- | --- | --- | --- |
| T1, T2, T4 | Repeatedly assumed proposal features the app no longer has: share-sheet import, same-event article list, archive. | My corrections (11-09) and screenshots (11-16) | Stories and tutorial rebuilt around the home feed → highlight → compare flow |
| T2 | Mock-up label names were invented. | Claude's own note | Code uses the real chip names from `categoryLabel` |
| T2 | Code was never built before the push. | Claude's statement; my local build failed for environment reasons | Verified through CI and teammates after the merge |
| T2 | Claude asked for write access when I had only asked it to review the repository. | The access prompt (11-16) | I chose read-only; I pushed from my own account |
| T3 | A cached web fetch made Claude report the log as ending on 10-05. | My correction (11-20) | Claude re-read the Wiki repository directly |
| T3 | Speaker names were inferred from "참석자 1–8". | Claude's "확인 필요" list |  |

### Verification practices I used

- [x] Checked every story against the app's current features
- [x] Confirmed label names against the app's code, not the mock-up
- [x] Pushed only from my own account; Claude had no write access
- [ ] Ran a local build before pushing (blocked by my environment)

**AI markers.** Commit `0ce31eb` was generated by Claude but has no in-code "AI-generated" comment and no `Co-Authored-By` trailer. This note records that, and I will add markers to the tutorial files in a follow-up commit.

---

## 5. Reflection on How AI Affected Our Development Process

**Productivity** — Without AI, the tutorial (design, code, tests, specs) would not have fit before the UAT session. 

**Quality** — The model's picture of our app was out of date. It drew on the proposal and the rubric spec, not the current UI. Screenshots and direct corrections fixed this faster than long explanations.

**Team workflow** — Pushing an unbuilt branch only worked because a teammate integrated it and CI checked it. Next time I should run the build first, or open a draft PR so CI runs before anyone merges.

**Learning** — For product work, the most useful input to the model was the current state of the app: screenshots, real names, and dropped features.

### Lessons and plan for next iteration

| **What I learned** | **What I will change next iteration** |
| --- | --- |
| The model assumes features from old documents. | Start with screenshots and a list of dropped features. |
| Generated code can be merged without ever being built. | Open a draft PR so CI builds it before review. |
| AI-generated commits lose attribution if I commit by hand. | Add a marker comment and a `Co-Authored-By` trailer when committing. |
| Cached web pages can be stale. | Point the model at the Wiki repository, not the rendered page. |

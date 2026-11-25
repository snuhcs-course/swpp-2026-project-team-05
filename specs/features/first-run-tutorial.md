# First-run tutorial (Android)

| Field | Value |
| --- | --- |
| Status | Implemented |
| Owner | Reader / onboarding feature owner |
| Related behavior | [`Publisher subscriptions`](news-app-features.md#11-publisher-subscriptions); [`../ui/news-app-v1-ui.md`](../ui/news-app-v1-ui.md) §§4–6 |
| Last reviewed | 2026-11-25 |

## 0. Product decision

The tutorial is a separate feature from publisher subscriptions: it is skippable,
adds no server-side tutorial state, and does not change subscription or topic
selection rules. Its replay entry follows the current portal navigation.

## 1. Purpose

A first-time reader does not know that purple highlights are tappable, what a
label chip means, or how the comparison sheet relates to the article. The
tutorial teaches the core loop once, in place, and states the product stance:
FrameLESS does not judge an article as biased; it shows where the same event
was worded differently.

## 2. Onboarding (first launch)

- Shown once per installation, after session restore, before the feed. The
  feed still initializes in the background.
- One quiet help action in the all-topics `전체 뉴스` section shows it again and
  re-arms the reader coach marks for the next article with findings. It is not
  repeated in `정치`, `사회`, `경제`, or `언론사`. MY also exposes
  `튜토리얼 다시 보기`. On a replay while signed in, `로그인하러 가기` is
  hidden.
- Three pages in a horizontal pager:
  1. *같은 사건, 다른 표현* — two example sentences with the differing phrase
     highlighted, and the stance sentence above.
  2. *이렇게 읽어요* — three steps (choose an article, tap a highlighted
     expression, compare with other articles) and a note that signing in
     enables saving.
  3. *7가지 표현 차이* — the A–G chip labels, each with a one-line summary.
- `건너뛰기`, `시작하기`, and `로그인하러 가기` all mark onboarding as seen.
  `로그인하러 가기` then opens the existing login screen.
- System Back goes to the previous onboarding page. On the first page it closes
  the intro through the same completion callback and returns to the preserved
  origin, including MY when replay started there.

## 3. Reader coach marks (first analyzed article)

Start when the reader opens an article that shows at least one finding, analysis
is enabled, onboarding has been seen, the coach has never completed, and the
reader was not opened from a comparison sheet. Articles without findings do not
start or consume the coach.

| Step | Spotlight | Primary action |
| --- | --- | --- |
| 1 / 3 | First finding (title first, otherwise body; the body scrolls it into view). Copy mentions that turning off `표현 분석` hides highlights. | Tapping the spotlight or `다음` selects that finding in the annotation dock. |
| 2 / 3 | The fixed annotation dock. Copy points to the `?` button. | `다음` opens the comparison sheet. If the finding has no comparisons the label reads `완료` (2 / 2) and ends the coach. |
| 3 / 3 | Inline callout at the top of the comparison sheet: compare the two expressions and their context previews; `이 기사 읽기` = read the full article in the app; `원문 보기` = open the publisher site. | `완료` ends the coach. |

- Outside the spotlight, touches are blocked while a coach mark is visible.
- Scroll-synced automatic dock replacement is suppressed while any coach step is active;
  the tutorial continues to demonstrate the explicit marker-tap path.
- `건너뛰기`, system Back (steps 1–2), or dismissing the sheet (step 3) end the
  coach and mark it seen.
- If findings disappear while step 1 is visible, the coach ends.

## 4. Label guide (`?` button)

- The annotation dock shows a `?` button beside its natural-language labels, during and
  after the tutorial.
- It opens a dialog listing the seven expression-difference categories A–G,
  using the same names as the chips, each with one baseline → marked example
  from the judging rubric v0.2. Labels from the current card are marked
  `현재 기준`.
- The introduction states that labels describe differences rather than judge
  right or wrong. Do not add a separate notice box below the examples.

## 5. Persistence

Device-local `SharedPreferences` (`frameless_tutorial`): `intro_seen`,
`reader_coach_seen`. Not tied to an account and not synced to Web.

## 6. Non-goals

- Web onboarding; server-side tutorial state.
- Changing finding, comparison, or analysis behavior.
- Coach marks on the feed, search, saved list, or Following controls.

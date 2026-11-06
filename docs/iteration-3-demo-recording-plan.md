# Iteration 3 demo recording plan

Target length: 75–90 seconds. Record a real Android device in portrait with the
production backend. Start signed in so no credential appears. Do not edit away a
failed interaction; restart the recording instead.

## Before recording

- Install the APK from the Iteration 3 demo commit and confirm the six-story
  release smoke has passed.
- Use the Society feed and the reviewed Shubao story. It has one clear finding
  with three displayed comparison publishers, so the hierarchy is visible without
  making the viewer decode several findings at once.
- Enable analysis and remove the story from Saved if it is already saved.
- Close notifications, use a stable network, and wait for the feed to finish
  loading before recording.

## Shot list

| Time | Action | What it demonstrates |
| --- | --- | --- |
| 0–6 s | Show the signed-in Society / Latest feed, then briefly switch to Following and back. | News-app feed and mutually exclusive scope selection. |
| 6–16 s | Open search and enter `슈바오`; briefly show the All, Politics, and Society filters, then open the Society result. | Server-side search across sections with a compact recovery path between filters. |
| 16–27 s | Open the Shubao result; show matching title/publisher and scroll enough to reveal the marked phrase. | Complete stored article and analysis integrated in the Reader. |
| 27–38 s | Tap the marked phrase and pause on the compact preview. | Analysis can be inspected without leaving the reading context. |
| 38–58 s | Open the full comparison, page through at least three publishers, and pause on one grounded passage. | Shared explanation, cross-publisher evidence, and multi-source navigation. |
| 58–67 s | Close the comparison, turn analysis off and on, and show that the same paragraph remains in place. | Saved analysis preference and HE-driven position stability. |
| 67–78 s | Save the article, return to Saved, and reopen the same row. | Account-owned saved article flow. |
| 78–85 s | End on the reopened Reader or the FrameLESS feed. | Clean final state. |

## Recording notes

- Screen recording may be silent. If narration is used, describe observations;
  do not claim that FrameLESS decides which publisher is correct.
- Do not show passwords, API keys, debug menus, or notification content.
- Keep taps deliberate and pause about one second after each state transition.
- The demo video illustrates the product; the six-case result table and CI run
  remain the testing evidence.

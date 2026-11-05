# FrameLESS — Iteration 3 Working Demo

FrameLESS is an Android news-reading app that helps users inspect how different outlets describe the same event. It presents source-linked wording differences without judging which outlet is correct or biased.

## Environment and how to run

### Environment used

- macOS on Apple silicon
- Android Studio 2025.3 with JetBrains Runtime 21
- Android SDK 37 and the project Gradle wrapper
- Android 16 / API 36 emulator
- Samsung Galaxy S22 (`SM-S901N`) running Android 15
- Production Django API and shared PostgreSQL database

### Run the app

1. Clone the repository and switch to the `iteration-3-demo` branch.
2. Open the `android/` directory in Android Studio.
3. Let Gradle sync, select the `app` run configuration, and run it on an Android phone or emulator.
4. Sign in with a team-provided demo account or register a normal account. Keep the device online so the configured API can load the demo content.

The demo build uses `https://frameless-team05-api.vercel.app`; no local backend or API key is required. Credentials are not stored in this repository.

To build a debug APK from the repository root:

```bash
cd android
./gradlew :app:assembleDebug
```

The generated APK is located at:

```text
android/app/build/outputs/apk/debug/app-debug.apk
```

## What the demo demonstrates

The accompanying video demonstrates the following current Android implementation.

| Flow | Demonstrated behavior |
| --- | --- |
| Account access | Signs in to the app with the prepared demo account. |
| Source management | Opens **Manage sources**, selects or deselects news outlets, and saves the chosen source set. |
| Feed exploration | Browses the curated feed, switches between topic tabs, and opens a selected article. |
| Reader | Displays the full article, supports bookmarking, and exposes the **expression analysis** control. |
| Highlighted expression | Turns analysis on and selects a highlighted expression in the Reader. |
| Cross-source comparison | Opens a comparison sheet that groups passages about the same content from other outlets; the viewer can page through the related articles. |
| Source traceability | Opens the original linked source for a comparison passage, then returns to the app. |
| Saved articles | Saves an article and verifies that it appears in the Saved Articles screen. |

The main end-to-end user flow is:

```text
Manage sources → Feed → Reader → expression analysis → highlighted expression
→ comparison sheet → original source → return to Reader / Saved Articles
```

## Iteration 3 progress shown in this demo

- Used the fixed, curated 30-article dataset for a stable Heuristic Evaluation (HE) environment.
- Refined the Reader comparison UX: a compact in-context preview, clearer sheet wording, an explicit close action, predictable dismissal, and a readable source hierarchy.
- Applied additional HE follow-ups: mutually exclusive feed-scope selection, stable Reader position when analysis is toggled, keyboard dismissal outside inputs, and recovery from an empty category search without result-count badges.
- Added source-selection and saved-article flows around the core reading experience.
- Confirmed the Android debug APK and manually smoke-tested the Feed → Reader → highlight → comparison → source-link path on a physical Android device.

The frozen six-article release smoke covered single-source, multi-source, multiple-finding, and mixed-comparison structures. The production API structure and installed Android Reader flow both passed all six cases. Full procedures and evidence are recorded in [`docs/testing-documentation.md`](docs/testing-documentation.md).

## Demo video

The video is included in this branch at:

[`demo/iteration-3-demo.mp4`](./demo/iteration-3-demo.mp4)

It is approximately 1 minute 47 seconds and intentionally walks through the full interaction flow, including source management and saved articles as well as the core comparison feature.

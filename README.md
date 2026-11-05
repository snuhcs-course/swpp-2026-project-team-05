# FrameLESS — Iteration 3 Demo

FrameLESS is an article-first news reader for comparing how different
publishers describe the same event. Readers can browse a familiar news feed,
read the stored article in full, and inspect selected wording only when they
want more context. FrameLESS presents source passages and differences; it does
not decide which publisher is correct.

## How to run the demo

### Android Studio

1. Clone this branch and open the `android/` directory in Android Studio.

   ```sh
   git clone --branch iteration-3-demo \
     https://github.com/snuhcs-course/swpp-2026-project-team-05.git
   cd swpp-2026-project-team-05/android
   ```

2. Use JDK 21 and install Android SDK 37. Let Gradle finish syncing.
3. Start an Android emulator or connect an Android device with USB debugging
   enabled.
4. Run the `app` configuration. No local backend or API key is required; the
   demo build uses `https://frameless-team05-api.vercel.app`.
5. Sign in with a team test account supplied separately, or register a normal
   account in the app. Credentials are not stored in this repository.

The CI-built debug APK is also available from the latest successful `Verify`
workflow artifact named `android-debug-apk-default-backend-no-live-check`.

### Environment used

- macOS on Apple silicon
- Android Studio 2025.3 with JetBrains Runtime 21
- Android SDK 37
- Android 16 / API 36 emulator
- Samsung Galaxy S22 (`SM-S901N`) running Android 15
- Production Django API and shared PostgreSQL database

## What the demo demonstrates

- Required sign-in and account-owned preferences
- Politics and Society feeds with mutually exclusive Latest and Following
  scopes
- Server-side article search across both sections, including per-section result
  counts
- Full stored article reading with optional expression-analysis markers
- A compact in-context explanation followed by a full comparison view
- Grounded passages from up to three distinct comparison publishers
- Saved articles that can be reopened and removed
- Heuristic-evaluation follow-ups: clearer feed scope, steadier Reader position,
  and predictable comparison navigation and dismissal

The demo dataset contains 30 reviewed articles. A frozen six-article release
smoke covers single-source, multi-source, multiple-finding, and mixed comparison
structures. On 2026-11-05, the production API structure matched all six cases;
the Android Reader flow also passed all six without a crash, blank article, or
infinite loading. Full evidence is recorded in
[`docs/testing-documentation.md`](docs/testing-documentation.md).

## Demo video

The final short recording will be added here before submission:

- **Video:** _pending recording_
- **Recording flow:**
  [`docs/iteration-3-demo-recording-plan.md`](docs/iteration-3-demo-recording-plan.md)

The planned 75–90 second recording shows the Society feed, cross-section search,
the reviewed Shubao article, compact and full comparison views, analysis-toggle
position stability, and the Saved flow. It starts signed in and does not expose
credentials or notification content.

## Repository layout

| Path | Purpose |
| --- | --- |
| `android/` | Kotlin and Jetpack Compose Android client |
| `server/` | Django API, persistence, analysis adapters, and reviewed fixtures |
| `web/` | Vite and TypeScript desktop web client |
| `specs/` | Product, UI, and behavioral testing specifications |
| `docs/` | Architecture, testing evidence, and demo guidance |

## Verification

The `Verify` workflow runs Django tests, Web tests/lint/build, Android JVM tests,
Android lint, and APK assembly. The recorded Iteration 3 candidate passed:

- Django: 381 tests
- Web: 25 tests
- Android JVM: 84 tests, 0 failures
- Android lint: 0 errors
- Production six-article API projection: 6/6 exact matches
- Installed Android Reader smoke: 6/6 core flows

The reproducible read-only API projection is
`scripts/smoke_iteration3_release.py`; credentials are supplied through local
environment variables and never committed.

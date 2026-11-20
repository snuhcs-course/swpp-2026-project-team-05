# FrameLESS — Iteration 4 Working Demo

FrameLESS is a news-reading application that lets people read a normal article
first and inspect how other outlets describe the same issue when they choose to.
It presents source-linked wording differences without deciding which report is
correct, neutral, or biased.

## Environment and how to run

### Environment used

- macOS on Apple silicon
- Android Studio 2025.3 with JDK 21
- Android SDK 37 and the project Gradle wrapper
- Samsung Galaxy S22 (`SM-S901N`) running Android 15
- Production Django API and shared PostgreSQL database
- Production Web client on Vercel

### Run the Android app

1. Clone the repository and switch to the `iteration-4-demo` branch.
2. Open the `android/` directory in Android Studio.
3. Let Gradle sync, select the `app` run configuration, and run it on an
   Android phone or emulator.
4. Continue as a guest to browse public news, or sign in with a team-provided
   demo account to use Saved and other account-specific actions.

The demo build uses the deployed team API at
`https://frameless-team05-api.vercel.app`; no local backend or model key is
required for the normal feed and Reader flow. Credentials are not stored in
this repository.

To build a debug APK from the repository root:

```bash
cd android
./gradlew :app:assembleDebug
```

The generated APK is located at:

```text
android/app/build/outputs/apk/debug/app-debug.apk
```

### Run the Web client

The deployed client is available at:

<https://frameless-team05-web.vercel.app>

To run it locally:

```bash
cd web
npm install
npm run dev
```

The local client uses the deployed team API by default. To use another backend,
copy `web/.env.example` to `web/.env.local` and set
`VITE_API_BASE_URL`.

### Run the backend locally

```bash
python3.12 -m venv .venv
.venv/bin/pip install -r requirements.txt
FRAMELESS_USE_SQLITE=1 .venv/bin/python manage.py migrate
FRAMELESS_USE_SQLITE=1 .venv/bin/python manage.py runserver
```

SQLite is sufficient for an empty local environment and deterministic tests.
The shared article and analysis dataset uses the migrated PostgreSQL database.
External search/model credentials are needed only for the developer-only pasted
link analysis flow, not for reading the prepared demo articles.

## What the demo demonstrates

The Iteration 4 demo focuses on one coherent reading journey rather than a list
of disconnected screens.

| Flow | Demonstrated behavior |
| --- | --- |
| Guest access | Enters the public feed without creating an account and reads public articles and stored analysis. |
| First-run guidance | Explains that FrameLESS highlights wording differences and does not judge which outlet is correct or biased. |
| News exploration | Browses Politics, Society, and Economy sections and opens an article from the normal feed. |
| Article-first Reader | Keeps the article body primary and reveals prepared analysis only when the reader enables it. |
| Expression analysis | Selects a highlighted expression and reads the issue-level explanation and source-linked comparison evidence. |
| Cross-article navigation | Opens a compared source as a normal internal Reader article, keeps the relevant passage in view, and can inspect that article's own available analysis. |
| Saved articles | Saves the internally opened comparison article and finds it in Saved. |
| Android offline reading | Reopens a fully cached Saved article, including stored analysis and comparison context, while offline. |
| Economic lens | Reads the event-level economic context and each outlet's reported perspective without presenting a winner or numerical score. |
| Developer link analysis | With a developer account, pastes an article URL, reuses an existing stored result when available, or creates a hidden Reader result through the staged analysis pipeline. |

The primary end-to-end demo flow is:

```text
Guest feed → article Reader → enable analysis → highlighted expression
→ compared coverage → open compared article → save article
→ Saved → reopen the cached article offline
```

The Economy section and developer-only pasted-link analysis are shown as short
follow-up flows after the core Reader journey.

## Major Iteration 4 progress

- Added public guest reading on Android and Web while keeping personal
  mutations account-bound.
- Added Android Saved-only offline reading using complete local article and
  analysis snapshots, without introducing anonymous server accounts or an
  offline write queue.
- Promoted compared coverage from an evidence-only card to a first-class
  internal Reader destination that can be saved normally.
- Improved comparison navigation so Android waits for layout before bringing
  the referenced passage into view and preserves the reason for navigation.
- Added first-run guidance and a replayable explanation of highlights and
  expression labels.
- Added Politics, Society, and Economy feed support plus an event-level economic
  lens that separates common context from each outlet's perspective.
- Added a developer-only pasted-link analysis flow with URL reuse, guarded model
  access, safe incremental publisher import, grounded storage, and deterministic
  regression tests.
- Refined the staged expression pipeline's A, G, and D phrase decisions without
  moving live model/search calls into deterministic CI.

## Verification

The release branch is verified with the repository's canonical checks:

```bash
FRAMELESS_USE_SQLITE=1 python3.12 manage.py test
cd web && npm test && npm run lint && npm run build
cd android && ./gradlew :app:jacocoDebugUnitTestReport :app:lintDebug :app:assembleDebug
```

Focused Iteration 4 evidence also includes:

- guest, signed-in, online, offline, Saved, and Reader state-transition tests;
- internal compared-article navigation and Saved behavior on Android and Web;
- physical-device verification that the referenced comparison passage is in
  view;
- deterministic link-analysis tests with external fetch/search/model calls
  replaced;
- separate production checks for the deployed API and Web client.

Live model output quality is not inferred from unit tests. The prepared demo
content and any live pasted-link result are reviewed separately.

## Demo video

[Watch the Iteration 4 demo](demo/iteration-4-demo.mp4) (`1:56`, silent screen
recording).

The recording uses the prepared dataset rather than waiting for a live model
call. It shows global article search, article-first reading, analysis markers,
source-linked compared coverage, saving an article, and reopening the complete
Saved article offline. The recording contains no account password or other
credential.

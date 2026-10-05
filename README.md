# FrameLESS

**An Android app for comparing reports about the same news event, starting from one article URL.**

FrameLESS finds related reporting and compares passages that address the same claims. It shows the source sentences and article links so readers can inspect the context themselves. The app does not decide which publisher is correct.

## Working Demo (Iteration 1 MVP)

### How to run the demo

1. Check out this branch: `git clone -b iteration-1-demo https://github.com/snuhcs-course/swpp-2026-project-team-05.git`.
2. Open the `android/` directory in Android Studio. Install JDK 21 and Android SDK 37, then let Gradle sync.
3. Run the `app` configuration on an Android emulator or device with internet access. The app uses the [shared Django backend](https://frameless-team05-api.vercel.app/api/health) by default; no local API keys or server setup are needed to run the app.
4. Paste a public Korean news article URL and tap **이 기사 비교하기** (“Compare this article”). One example is `https://www.yna.co.kr/view/AKR20261005027751001?input=1195m`.
5. Wait for the analysis, then inspect the core event, number of related articles, comparison cards, quoted passages, and links to the original articles. A contrast section can be empty when no supported rebuttal or different interpretation is found.

**Environment used:** macOS on Apple Silicon, Android Studio with its bundled JDK 21, Android SDK 37, and an Android Emulator running API 36. The shared backend runs Django on Python 3.12 in Vercel. For optional local backend setup, see [Backend development and deployment](docs/backend.md).

### What the demo demonstrates

- End-to-end flow from an article URL in the Android app to a result returned by the Django API.
- Extraction of the input article's core event and grounded claims, NAVER news search, same-event screening, and comparison with up to three related articles.
- Display of shared claims and, when evidence exists, rebuttals or different interpretations with quoted passages and source links.
- The prototype's goal is to validate that the search and comparison flow works on real articles and that readers can trace displayed claims back to their sources.

**Current scope:** The overview shows the number of selected related articles and the passages used in comparison. It does not yet show a separate list of every discovered article or every article-only detail. FrameLESS does not automatically judge truth or bias.

### Short demo video

A short emulator recording of the key flow will be added here before submission.

## Technology

| Component | Technology |
| --- | --- |
| Android app | Kotlin, Jetpack Compose |
| Analysis API | Python, Django |
| Related news search and analysis | NAVER API HUB, Gemini |

## Documentation

- [Requirements and Specifications](https://github.com/snuhcs-course/swpp-2026-project-team-05/wiki/Requirements-and-Specifications)
- [Design Documentation](https://github.com/snuhcs-course/swpp-2026-project-team-05/wiki/Design-Documentation)
- [Backend development and deployment](docs/backend.md)

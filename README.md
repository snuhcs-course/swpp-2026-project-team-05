# FrameLESS

**하나의 기사에서 시작해, 같은 사건을 다룬 다른 보도를 함께 읽는 Android 앱**

FrameLESS는 뉴스 기사 URL을 입력받아 같은 사건을 다룬 보도를 찾고, 각 기사에서 실제로 대응하는 문장을 비교합니다. 여러 출처가 공통으로 언급한 내용과 반박하거나 다르게 해석한 내용을 원문 링크와 함께 보여줍니다. 어느 보도가 옳은지 대신 판정하기보다, 사용자가 근거와 맥락을 직접 확인하도록 돕는 것이 목표입니다.

## 주요 기능

- **기사 비교:** 입력한 기사의 핵심 사건을 파악하고 관련 보도를 찾습니다.
- **주장별 비교:** 출처들이 공통으로 말하는 내용과 반박·해석 차이를 구분해 보여줍니다.
- **원문 확인:** 비교에 사용된 문장과 기사 링크를 제공해 전체 맥락을 확인할 수 있습니다.

## 앱 실행

1. Android Studio에서 `android/` 폴더를 엽니다. JDK 21과 Android SDK 37이 필요합니다.
2. 에뮬레이터 또는 Android 기기에서 앱을 실행합니다.
3. 뉴스 기사 URL을 입력하고 **이 기사 비교하기**를 누릅니다.

앱은 기본으로 팀 백엔드에 연결됩니다. 앱 실행을 위해 별도의 API 키나 로컬 서버 설정은 필요하지 않습니다.

## 기술 구성

| 구성 | 기술 |
| --- | --- |
| Android 앱 | Kotlin, Jetpack Compose |
| 분석 API | Python, Django |
| 관련 기사 검색·분석 | NAVER API HUB, Gemini |

## 문서

- [Requirements and Specifications](https://github.com/snuhcs-course/swpp-2026-project-team-05/wiki/Requirements-and-Specifications)
- [Design Documentation](https://github.com/snuhcs-course/swpp-2026-project-team-05/wiki/Design-Documentation)
- [백엔드 개발·배포 안내](docs/backend.md)

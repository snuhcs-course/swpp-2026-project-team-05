# FrameLESS

**하나의 기사에서 시작해, 같은 사건을 다룬 다른 보도를 함께 읽는 Android 앱**

FrameLESS는 뉴스 기사 URL을 입력받아 같은 사건을 다룬 보도를 찾고, 추출한 기사 본문에서 실제로 대응하는 문장을 연결합니다. 검증된 표현 차이(A–G)는 본문에 표시하고, 원문에만 두드러지는 내용(I1)은 비교 범위를 명시한 별표로 보여줍니다. 사용자는 관련 기사의 대응 구절과 해당 기사 페이지를 확인할 수 있습니다. 어느 보도가 옳은지 대신 판정하지 않습니다.

## 주요 기능

- **기사 비교:** 입력 기사의 중심 사건을 파악하고 같은 사건의 다른 보도를 찾습니다.
- **전문 읽기:** 광고와 사진 등을 제외한 추출 텍스트 본문에서 A–G 표현 차이와 I1 별표를 살펴봅니다.
- **근거 확인:** 표시된 문장을 누르면 관련 기사의 대응 구절과 차이 설명을 보고, 해당 기사 페이지를 열 수 있습니다.
- **계정 기반 이전 분석:** 로그인한 계정의 분석 기록을 어느 기기에서나 최근 분석 목록에서 다시 열어, 새로 분석한 결과와 같은 Reader 화면으로 확인할 수 있습니다.

## 앱 실행

1. Android Studio에서 `android/` 폴더를 엽니다. JDK 21과 Android SDK 37이 필요합니다.
2. 에뮬레이터 또는 Android 기기에서 앱을 실행합니다.
3. 서버에서 발급한 팀 계정으로 로그인합니다. 공개 회원가입 기능은 없으며, 계정 정보는 저장소에 포함하지 않습니다.
4. 뉴스 기사 URL을 입력하고 **이 기사 비교하기**를 누릅니다.

앱은 기본으로 팀 백엔드에 연결됩니다. 앱 실행을 위해 별도의 API 키나 로컬 서버 설정은 필요하지 않습니다.

## Iteration 2 Working Demo

### How to run the demo

1. 이 브랜치를 clone합니다: `git clone -b iteration-2-demo https://github.com/snuhcs-course/swpp-2026-project-team-05.git`.
2. Android Studio에서 저장소의 `android/` 폴더를 열고 Gradle sync를 마칩니다.
3. 인터넷이 연결된 Android 에뮬레이터 또는 기기에서 `app` 구성을 실행합니다. 앱은 기본으로 팀의 Django API(`https://frameless-team05-api.vercel.app`)에 연결됩니다.
4. 서버에서 발급한 팀 계정으로 로그인합니다. 계정 정보는 저장소에 포함하지 않습니다.
5. 공개 뉴스 기사 URL을 입력하고 **이 기사 비교하기**를 누른 뒤 결과를 확인합니다. 분석은 몇 분 걸릴 수 있고 일부 결과에는 경고가 표시될 수 있습니다.

**Environment used:** macOS on Apple Silicon, Android Studio with JDK 21, Android SDK 37, and an Android Emulator running API 36. 로컬 백엔드 실행 방법과 상세 설정은 [백엔드 개발·배포 안내](docs/backend.md)를 참고하세요.

### What the demo demonstrates

- 실제 기사 URL을 입력해 분석하고 추출된 기사 본문을 Reader에서 읽는 흐름.
- 근거가 확인된 C 프레이밍 차이를 눌러 다른 보도의 대응 표현과 설명을 확인하는 기능.
- I1 별표를 눌러 비교 범위 안에서 이 기사에만 두드러진 내용과 비교 근거를 확인하는 기능.
- Iteration 2에서 Android Reader와 Django 분석 API를 연결하고, 근거가 있는 표시와 부분 실패 경고를 실제 결과에 함께 보여주는 통합 진전.

**Recording scope:** 아래 영상은 2026-10-19 중간 발표 슬라이드 4에 연결한 실제 TheFact 기사 시연입니다. 영상에서는 입력과 로딩 장면을 빠르게 편집했고, 긴 분석 대기는 생략했다고 표시했습니다. 해당 실행에서 C 프레이밍 표시 2개, I1 별표 3개, 일부 검색 실패 경고를 확인할 수 있습니다. 다른 유형의 표시나 관련 기사 좌우 넘기기는 이 영상에서 시연하지 않습니다.

### Short demo video

[Watch the 68.5-second Reader demo](docs/demo/iteration-2-demo.mp4)

### 10월 19일 통합 상태 스냅샷

`main`은 Android Reader와 Django 분석 API를 연결합니다. 앱은 기본으로 `https://frameless-team05-api.vercel.app`을 사용합니다. 운영 백엔드는 수동 배포하므로 Git 커밋만으로 배포를 확인할 수 없습니다. 실제 분석은 기사와 모델 응답에 따라 몇 분 걸리거나 부분 결과가 될 수 있습니다. 데모에 쓸 URL·APK·백엔드 리비전은 함께 확인하세요. 로컬 백엔드에는 팀에서 허용한 OpenRouter 키가 필요합니다. NAVER API HUB 키는 있으면 우선 검색에 쓰며, 없거나 인증이 거부되면 OpenRouter Exa 웹 검색으로 후보를 찾습니다. Exa도 실패하면 Google News RSS를 사용합니다. 직접 Gemini 키는 대체 모델 경로로 쓸 수 있습니다. 키는 저장소나 APK에 넣지 않습니다.

```sh
python3.12 -m venv .venv
.venv/bin/pip install -r requirements.txt
cp .env.example .env
# .env에 OPENROUTER_API_KEY 설정; NAVER_CLIENT_ID·NAVER_CLIENT_SECRET은 선택
set -a; source .env; set +a
.venv/bin/python manage.py runserver
```

에뮬레이터에서 로컬 서버를 사용할 경우 Android 프로젝트에서 다음처럼 빌드합니다. `10.0.2.2`는 에뮬레이터에서 호스트 컴퓨터로 접속하는 주소입니다.

```sh
cd android
./gradlew :app:installDebug -PframelessBackendUrl=http://10.0.2.2:8000
```

`GET /api/health`는 설정 여부만 알려 줍니다. 실제 분석은 `POST /api/analyze`와 Android 화면에서 확인해야 합니다. Reader는 I1과 A–G를 다루며 I2–I5는 이전 루브릭 경로에만 남아 있습니다. 오프라인 계약 검사는 `./gradlew :app:jacocoDebugUnitTestReport :app:assembleDebug`와 `.venv/bin/python manage.py test`로 실행할 수 있습니다. API 상세는 [백엔드 개발·배포 안내](docs/backend.md)를 참고하세요.

## 기술 구성

| 구성 | 기술 |
| --- | --- |
| Android 앱 | Kotlin, Jetpack Compose |
| 분석 API | Python, Django |
| 관련 기사 검색·분석 | NAVER API HUB 또는 OpenRouter Exa·Google News RSS, OpenRouter/Gemini |

## 문서

- [Requirements and Specifications](https://github.com/snuhcs-course/swpp-2026-project-team-05/wiki/Requirements-and-Specifications)
- [Design Documentation](https://github.com/snuhcs-course/swpp-2026-project-team-05/wiki/Design-Documentation)
- [Testing Documentation](https://github.com/snuhcs-course/swpp-2026-project-team-05/wiki/Testing-Documentation)
- [AI Collaboration Report – Iteration 2](https://github.com/snuhcs-course/swpp-2026-project-team-05/wiki/AI-Collaboration-Report-Iteration-2)
- [Iteration 2 제출 상태 (23 October)](docs/iteration-2-submission-checklist.md)
- [10월 19일 통합 상태 스냅샷](docs/iteration-2-integration-status.md)
- [백엔드 개발·배포 안내](docs/backend.md)

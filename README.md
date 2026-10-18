# FrameLESS

**하나의 기사에서 시작해, 같은 사건을 다룬 다른 보도를 함께 읽는 Android 앱**

FrameLESS는 뉴스 기사 URL을 입력받아 같은 사건을 다룬 보도를 찾고, 추출한 기사 본문에서 실제로 대응하는 문장을 연결합니다. 검증된 표현 차이(A–G)는 본문에 표시하고, 원문에만 두드러지는 내용(I1)은 비교 범위를 명시한 별표로 보여줍니다. 사용자는 관련 기사의 대응 구절과 원문 링크를 확인할 수 있습니다. 어느 보도가 옳은지 대신 판정하지 않습니다.

## 주요 기능

- **기사 비교:** 입력 기사의 중심 사건을 파악하고 같은 사건의 다른 보도를 찾습니다.
- **전문 읽기:** 광고와 사진 등을 제외한 추출 텍스트 본문에서 A–G 표현 차이와 I1 별표를 살펴봅니다.
- **근거 확인:** 표시된 문장을 누르면 관련 기사의 대응 구절과 차이 설명을 보고, 원문 링크를 열 수 있습니다.

## 앱 실행

1. Android Studio에서 `android/` 폴더를 엽니다. JDK 21과 Android SDK 37이 필요합니다.
2. 에뮬레이터 또는 Android 기기에서 앱을 실행합니다.
3. 뉴스 기사 URL을 입력하고 **이 기사 비교하기**를 누릅니다.

앱은 기본으로 팀 백엔드에 연결됩니다. 앱 실행을 위해 별도의 API 키나 로컬 서버 설정은 필요하지 않습니다.

### Iteration 2 통합 버전 확인

`main`은 Android Reader와 Django 분석 API를 연결합니다. 앱은 기본으로 `https://frameless-team05-api.vercel.app`을 사용합니다. 운영 백엔드는 수동 배포하므로 Git 커밋만으로 배포를 확인할 수 없습니다. 실제 분석은 기사와 모델 응답에 따라 몇 분 걸리거나 부분 결과가 될 수 있습니다. 데모에 쓸 URL·APK·백엔드 리비전은 함께 확인하세요. 로컬 백엔드에는 팀에서 허용한 OpenRouter 키와 NAVER API HUB 키가 필요합니다. 직접 Gemini 키는 대체 모델 경로로 쓸 수 있습니다. 키는 저장소나 APK에 넣지 않습니다.

```sh
python3.12 -m venv .venv
.venv/bin/pip install -r requirements.txt
cp .env.example .env
# .env에 OPENROUTER_API_KEY, NAVER_CLIENT_ID, NAVER_CLIENT_SECRET 설정
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
| 관련 기사 검색·분석 | NAVER API HUB, OpenRouter/Gemini |

## 문서

- [Requirements and Specifications](https://github.com/snuhcs-course/swpp-2026-project-team-05/wiki/Requirements-and-Specifications)
- [Design Documentation](https://github.com/snuhcs-course/swpp-2026-project-team-05/wiki/Design-Documentation)
- [Iteration 2 제출 준비 체크리스트](docs/iteration-2-submission-checklist.md)
- [Iteration 2 통합 상태](docs/iteration-2-integration-status.md)
- [백엔드 개발·배포 안내](docs/backend.md)

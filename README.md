# FrameLESS

**한 기사를 읽으면서, 같은 사건을 다룬 다른 언론사의 표현을 함께 확인하는 뉴스 앱**

FrameLESS는 정치·사회 기사를 일반 뉴스 앱처럼 탐색하고 읽는 경험 위에
문장 단위 표현 분석을 더합니다. 분석된 표현을 선택하면 그 표현의 설명과
같은 내용을 다룬 다른 언론사의 근거 문장을 확인할 수 있습니다. FrameLESS는
어느 보도가 옳거나 편향되었다고 판정하지 않습니다.

## 주요 기능

- 정치·사회 기사 피드와 언론사 팔로우 피드
- 전체 기사 본문 검색과 분야별 결과 필터
- 기사 전문 읽기와 저장 목록
- 검토된 표현 분석 표시와 설명
- 같은 쟁점을 다룬 다른 언론사의 대응 문장 및 원문 링크
- Android와 Web에서 공유하는 계정·기사·분석 데이터

일반 사용자의 읽기 경로는 미리 검토해 데이터베이스에 저장한 분석 결과를
사용합니다. 피드나 Reader를 여는 동작은 LLM 분석을 새로 시작하지 않습니다.
기존 URL 분석 API는 호환성과 내부 실험을 위해 분리된 상태로 남아 있으며,
일반 사용자 UI에서는 노출하지 않습니다.

## Android 실행

개발 환경은 JDK 21과 Android SDK 37을 기준으로 합니다.

1. Android Studio에서 `android/`를 엽니다.
2. Gradle 동기화를 완료합니다.
3. Android 기기 또는 에뮬레이터에서 `app` 구성을 실행합니다.

기본 빌드는 팀 API인 `https://frameless-team05-api.vercel.app`을 사용하므로
앱 실행에 로컬 API 키가 필요하지 않습니다. 다른 백엔드를 사용할 때만 다음과
같이 빌드 속성을 지정합니다.

```sh
cd android
./gradlew :app:installDebug \
  -PframelessBackendUrl=http://10.0.2.2:8000
```

## Web 실행

```sh
cd web
npm install
npm run dev
```

개발 서버는 기본적으로 배포된 팀 API에 요청을 전달합니다. 다른 백엔드를
사용하려면 `web/.env.example`을 `web/.env.local`로 복사하고
`VITE_API_BASE_URL`을 설정합니다.

## Backend 실행

```sh
python3.12 -m venv .venv
.venv/bin/pip install -r requirements.txt
FRAMELESS_USE_SQLITE=1 .venv/bin/python manage.py migrate
FRAMELESS_USE_SQLITE=1 .venv/bin/python manage.py runserver
```

SQLite는 테스트와 빈 로컬 환경에 적합합니다. 실제 공유 기사 데이터에는
마이그레이션이 적용된 PostgreSQL `DATABASE_URL`이 필요합니다. OpenRouter,
Gemini, NAVER 키는 일반 뉴스 읽기 API에는 필요하지 않으며, 분리된 기존 URL
분석 경로를 로컬에서 실행할 때만 사용합니다.

자세한 환경변수와 API 경계는 [백엔드 개발·배포 안내](docs/backend.md)를
참고하세요.

## 검증

```sh
FRAMELESS_USE_SQLITE=1 .venv/bin/python manage.py test
cd web && npm test && npm run lint && npm run build
cd android && ./gradlew :app:jacocoDebugUnitTestReport :app:lintDebug :app:assembleDebug
```

GitHub Actions의 `Verify` workflow가 pull request와 `main`의 최종 병합
게이트입니다.

## 문서

- [제품·UI·기능 스펙](specs/README.md)
- [테스트 전략과 결과](docs/testing-documentation.md)
- [Iteration 3 데모 촬영 흐름](docs/iteration-3-demo-recording-plan.md)
- [백엔드 개발·배포 안내](docs/backend.md)
- [현재 문서와 보관 자료 안내](docs/README.md)
- [Requirements and Specifications Wiki](https://github.com/snuhcs-course/swpp-2026-project-team-05/wiki/Requirements-and-Specifications)
- [Design Documentation Wiki](https://github.com/snuhcs-course/swpp-2026-project-team-05/wiki/Design-Documentation)

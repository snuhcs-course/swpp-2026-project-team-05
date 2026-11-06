# Backend development and deployment

FrameLESS의 일반 사용자 경로는 Django `/api/v1`과 PostgreSQL에 저장된 기사·
분석 결과를 사용합니다. Android와 Web은 같은 API와 데이터베이스를 공유하며,
피드·Reader 요청은 LLM이나 외부 기사 수집을 시작하지 않습니다.

기본 운영 주소는 `https://frameless-team05-api.vercel.app`입니다.

## Local setup

Python 3.12를 사용합니다.

```sh
python3.12 -m venv .venv
.venv/bin/pip install -r requirements.txt
```

빈 SQLite 데이터베이스로 API와 테스트를 실행할 때:

```sh
FRAMELESS_USE_SQLITE=1 .venv/bin/python manage.py migrate
FRAMELESS_USE_SQLITE=1 .venv/bin/python manage.py runserver
```

공유 기사 데이터를 사용할 때는 Git에서 제외된 `.env` 또는 셸 환경에
PostgreSQL `DATABASE_URL`을 설정하고 마이그레이션을 적용합니다.

```sh
set -a; source .env; set +a
.venv/bin/python manage.py migrate
.venv/bin/python manage.py runserver
```

비밀번호, 세션 토큰, API 키, 데이터베이스 URL은 저장소나 APK에 넣지 않습니다.

## Current product API

일반 사용자 UI가 사용하는 주요 경로는 다음과 같습니다.

- `POST /api/v1/auth/register`, `POST /api/v1/auth/login`
- `POST /api/v1/auth/logout`, `GET /api/v1/auth/me`
- `GET /api/v1/publishers`, `PUT /api/v1/me/followed-publishers`
- `GET /api/v1/articles`
- `GET /api/v1/articles/{id}`
- `GET /api/v1/articles/{id}/analysis`
- `GET /api/v1/saved-articles`
- `PUT|DELETE /api/v1/saved-articles/{id}`

등록과 로그인을 제외한 제품 API는 bearer session을 요구합니다. 기사 목록은
피드에 공개된 기사만 반환하고, 분석 endpoint는 이미 저장되고 검토된 finding과
비교 근거만 투영합니다. 자세한 JSON 계약은
[`specs/product/news-app-v1.md`](../specs/product/news-app-v1.md)를 따릅니다.

## Reviewed dataset import

`server/fixtures/gold_v5/`에는 30개 source case와 148개 comparison-support
article reference가 있습니다. Import 과정은 기사 전문을 수집하고 모든 문장·
marker offset을 검증한 뒤 news-domain row를 교체합니다. 계정, 세션, URL 분석
cache/history는 보존하지만, article과 publisher를 교체하면서 `SavedArticle`과
`UserPublisherFollow` 관계는 cascade로 초기화됩니다.

```sh
.venv/bin/python manage.py import_gold_dataset \
  server/fixtures/gold_v5 --dry-run

.venv/bin/python manage.py import_gold_dataset \
  server/fixtures/gold_v5 --replace-news
```

운영 데이터 교체 전에는 반드시 dry run과 데이터베이스 백업을 확인하고,
저장 기사와 팔로우 설정이 초기화되어도 되는지 확인합니다.

## Legacy URL-analysis path

`POST /api/analyze`와 관련 history API는 Iteration 1–2 호환 및 내부 실험을 위해
남아 있습니다. 일반 사용자 뉴스 UI에서는 이 경로에 접근하지 않습니다. 이
경로를 로컬에서 실행할 때만 OpenRouter 또는 Gemini key가 필요하며, NAVER
credentials는 선택적인 검색 경로입니다. 환경변수 목록은 `.env.example`을
기준으로 합니다.

`GET /api/health`의 `analysis_ready=true`는 필요한 key가 설정되었다는 뜻일
뿐, 모델 접근 권한·잔액·실제 분석 성공을 보장하지 않습니다. 기존 파이프라인의
Iteration 2 구조와 과거 지연 기록은
[`archive/iteration-2/`](archive/iteration-2/)에 보관합니다.

## Verification

```sh
FRAMELESS_USE_SQLITE=1 .venv/bin/python manage.py test
cd web && npm test && npm run lint && npm run build
cd android && ./gradlew :app:jacocoDebugUnitTestReport :app:lintDebug :app:assembleDebug
```

실제 배포와 데이터베이스를 사용하는 smoke test는 deterministic CI와 분리해
기록합니다. 현재 절차와 결과는
[`testing-documentation.md`](testing-documentation.md)에 있습니다.

## Vercel deployment

`vercel.json`은 Django 함수와 최대 실행 시간을 정의합니다. 배포 전에는 운영
환경변수와 PostgreSQL migration 상태를 확인합니다.

```sh
npx vercel link --project frameless-team05-api \
  --scope kim-seungmins-projects
npx vercel deploy --prod
```

배포 후 `/api/health`, 인증, feed, article detail, stored analysis를 차례로
확인합니다. GitHub의 `main` 커밋과 실제 Vercel deployment가 같은 리비전인지
별도로 확인하며, 연결 상태를 확인하지 않고 자동 배포되었다고 가정하지 않습니다.

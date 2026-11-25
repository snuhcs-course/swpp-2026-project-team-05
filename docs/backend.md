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
- `GET /api/v1/stories` (Android의 주제별 피드)
- `GET /api/v1/articles/{id}`
- `GET /api/v1/articles/{id}/analysis`
- `GET /api/v1/saved-articles`
- `PUT|DELETE /api/v1/saved-articles/{id}`
- `POST /api/v1/articles/{id}/view`, `GET /api/v1/trends`
- `GET|PUT|DELETE /api/v1/articles/{id}/reactions`
- `GET /api/v1/home-highlights`

공개 피드·검색·언론사·기사·분석·트렌드·반응 조회에는 로그인이 필요하지
않습니다. `FOLLOWING` 조회, 구독 변경, Saved 조회·변경, 반응 변경과 `auth/me`는
유효한 bearer session이 필요합니다. 잘못된 토큰을 보낸 요청은 비로그인 요청으로
전환하지 않습니다. 기사 조회는 이미 저장된 본문·finding·비교 근거를 반환합니다.
자세한 JSON 계약은
[`specs/product/news-app-v1.md`](../specs/product/news-app-v1.md)를 따릅니다.

최근 조회는 별도 게스트 계정 없이 로컬 익명 식별자 또는 로그인 계정으로
집계합니다. 일일 만료 데이터 정리 경로 `/api/internal/trends/cleanup`은
`CRON_SECRET`을 사용하는 운영 전용 경로입니다. 홈 날씨·시장 카드는 아직 고정
데모 값이며 이 뉴스 API에 실시간 제공자가 연결된 것은 아닙니다.

## Authentication and database access

계정·세션과 공개 기사 조회는 Django API가 처리합니다. Supabase의 `anon`/
`authenticated` 역할로 테이블을 직접 읽거나 변경하는 방식은 사용하지 않습니다.
`0016_auth_throttle`은 인증 시도 제한 테이블을 추가하고,
`0017_backend_only_tables`는 Django 관리 테이블과 migration 기록의 RLS를
활성화하고 공개 클라이언트 역할의 테이블 권한을 회수합니다. 기존 backend의
테이블 소유자/service 권한은 유지됩니다. 새 테이블도 이 경계를 유지해야 합니다.

공유 IP·계정별 로그인 제한, 가입 제한, `429`의 `Retry-After`, 인증 응답의
`no-store`와 설정 기본값은 [계정 보안 스펙](../specs/features/account-security.md)에
정의되어 있습니다. 제한 저장소 장애는 인증을 우회하지 않고 `503`으로 처리하며
공개 뉴스 읽기는 독립적입니다. 기존 계정·비밀번호는 유지합니다.

현재 Web도 bearer session을 사용합니다. 브라우저 쿠키·CSRF 전환은 보류했으며,
`/api/v1/web/auth/*` 경로는 구현되어 있지 않습니다.

## Reviewed dataset import

현재 일반 import는 `schema_version: 1`의 정규화된 데이터셋을 받습니다.
`publishers`, `clusters`와 그 안의 기사·finding·비교 근거를 검증하며,
오프셋은 저장된 정확한 제목/본문을 기준으로 합니다. 모델 원본 JSON은 먼저
이 스키마로 변환해야 합니다.

```sh
.venv/bin/python manage.py import_news_dataset /path/to/reviewed-news.json --dry-run
.venv/bin/python manage.py import_news_dataset /path/to/reviewed-news.json
```

`--fetch-articles`는 제목이나 본문이 빠진 기사만 수집하는 선택 사항입니다.
명령은 트랜잭션 안에서 import하고 dry run은 롤백합니다. 일반적인 추가·수정에
전체 뉴스 삭제를 사용하지 않습니다. 공개 기사 수는 데이터셋의 visibility/
feed membership으로 결정하며 30개로 제한하지 않습니다.

### Legacy gold adapter

`server/fixtures/gold_v5/`에는 30개 source case와 148개 comparison-support
article reference가 보존되어 있습니다. 현재 운영 데이터셋을 뜻하지는 않습니다.
이전 gold adapter는 기사 전문을 수집하고 모든 문장·
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

`POST /api/v1/link-analyses`는 직접 API 호출을 위한 staged 호환 경로입니다.
유효한 저장 URL은 재사용하고, 신규 URL은 별도 제한 아래 수집·분석·검증한 뒤
일반 피드/검색에 노출하지 않는 Reader용 데이터를 저장합니다. OpenRouter 설정과
실행·실패 경계는 [link-analysis 스펙](../specs/features/link-analysis.md)을 따릅니다.
Android/Web 일반 탐색과 검색 빈 결과 화면에는 URL 입력 UI가 없습니다.

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
(cd web && npm test && npm run lint && npm run build)
(cd android && ./gradlew :app:jacocoDebugUnitTestReport :app:lintDebug :app:assembleDebug)
```

실제 배포와 데이터베이스를 사용하는 smoke test는 deterministic CI와 분리해
기록합니다. 현재 절차와 결과는
[`testing-documentation.md`](testing-documentation.md)에 있습니다.

## Vercel deployment

`vercel.json`은 Django 함수와 최대 실행 시간을 정의합니다. 배포 전에는 운영
환경변수와 PostgreSQL migration 상태를 확인합니다. 현재 API와 Web 프로젝트는
GitHub `main` 병합에 연결되어 있습니다. CI 성공과 DB 마이그레이션 적용은 별개이며,
자동 배포 전에 필요한 migration을 적용합니다. Web의 `/api` 요청은
`web/vercel.json`을 통해 팀 API로 전달됩니다.

아래 명령은 별도 수동 배포가 필요할 때의 운영 경로입니다.

```sh
npx vercel link --project frameless-team05-api \
  --scope kim-seungmins-projects
npx vercel deploy --prod
```

배포 후 `/api/health`, 인증, feed, article detail, stored analysis를 차례로
확인합니다. GitHub의 `main` 커밋과 실제 Vercel deployment가 같은 리비전인지
별도로 확인하며, 연결 상태를 확인하지 않고 자동 배포되었다고 가정하지 않습니다.

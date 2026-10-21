# 백엔드 개발·배포

Android 앱은 기본적으로 `https://frameless-team05-api.vercel.app`을 사용합니다. 로컬 백엔드는 API나 분석 코드를 수정할 때만 실행하면 됩니다.
단계별 입력·출력 계약과 실패 처리 원칙은 [분석 파이프라인 구조](analysis-pipeline-architecture.md)에 정리했습니다.

## 로컬 실행

Vercel과 로컬 모두 Python 3.12를 권장합니다. `.env`에는 팀 OpenRouter 키를 넣습니다. 직접 Gemini API 키는 OpenRouter를 쓰지 않는 로컬 실행의 대체 경로입니다. `NAVER_CLIENT_ID`와 `NAVER_CLIENT_SECRET`은 선택 사항이며, 있으면 [NAVER API HUB 뉴스 검색](https://api.ncloud-docs.com/docs/naver-api-hub-search-news)을 먼저 사용합니다. 키가 없거나 인증이 거부되면 공개 Google News RSS로 후보를 찾고, 이후 동일한 전문 검증을 진행합니다.

```sh
python3.12 -m venv .venv
source .venv/bin/activate
pip install -r requirements.txt
cp .env.example .env
# .env에 OPENROUTER_API_KEY 입력; NAVER_CLIENT_ID·NAVER_CLIENT_SECRET은 선택
chmod 600 .env
set -a; source .env; set +a
python manage.py runserver
```

서버 주소는 `http://127.0.0.1:8000`입니다. 실제 분석에는 모델 키 한 개가 필요합니다. NAVER 자격 증명은 선택 사항입니다. 비밀값과 `.env`는 Git·Android 앱에 넣지 않습니다.

## API

```sh
curl http://127.0.0.1:8000/api/health
curl -X POST http://127.0.0.1:8000/api/analyze \
  -H 'Content-Type: application/json' \
  -d '{"url":"https://n.news.naver.com/article/057/0001971678","max_related":3}'
```

`GET /api/health`의 `analysis_ready: true`는 OpenRouter 또는 직접 Gemini 키가 설정됐다는 뜻입니다. 빠진 필수 키는 `missing_configuration`에 표시됩니다. 키 존재만 확인하며 모델 접근 권한이나 남은 크레딧까지 보장하지 않습니다.

`POST /api/analyze`는 JSON 객체의 `url`을 받습니다. `max_related`는 생략 가능하며 범위는 1~3입니다. 성공 응답에는 `source_analysis`, `related_articles`, `comparison`, `analysis_status`, 추출 전문과 문장 위치를 담은 `reader`가 들어갑니다. Reader 분석은 I1과 A–G에 집중합니다. 검증하지 못한 문장이나 비교 기사는 별표·표현 차이를 만들어 표시하지 않으며, `analysis_status.execution_status=partial`일 때 앱은 분석 미완료를 알려야 합니다. 검색 실패와 분석 실패는 비교 결과 없음으로 표시하지 않고 오류를 반환합니다. `FRAMELESS_PIPELINE=v2`가 기본이며 `v1`은 로컬에서 Iteration 1 동작을 재현할 때 `FRAMELESS_ALLOW_LEGACY_V1=1`을 함께 설정해야 합니다. 운영 서버는 `v1` 요청을 거부합니다. 오류는 `{"error":{"code":"...","message":"..."}}` 형식입니다. 분석 중에는 동일 프로세스의 다른 요청에 429와 `Retry-After`를 반환합니다. 기사 URL은 공개 HTTP(S) 도메인이어야 하며 localhost, IP 주소, 별도 포트는 허용하지 않습니다.

### 분석 기록 API

Android 앱은 아이디·비밀번호로 로그인하고 서버가 발급한 30일 만료 bearer token을 `Authorization` 헤더에 보냅니다. DB에는 비밀번호 해시와 토큰 SHA-256 해시만 저장합니다. 분석 기록은 `UserAccount` 외래키에 연결되므로 같은 계정으로 로그인한 다른 기기에서도 확인할 수 있습니다. 기존 설치별 기록은 첫 로그인 때 `X-FrameLESS-Client-ID`로 계정에 한 번 이관합니다.

- `POST /api/auth/login`: 아이디·비밀번호를 확인하고 토큰을 발급합니다. 공개 회원가입은 제공하지 않으며 개발자 계정은 관리 명령으로 만듭니다.
- `POST /api/analyze`: 로그인한 계정으로 비교 가능한 결과가 나오면 기록을 저장합니다. 같은 URL을 다시 분석하면 새 행을 만들지 않고 기존 결과와 갱신 시각을 바꿉니다.
- `GET /api/analyses?limit=20`: 최근 기록의 가벼운 목록을 반환합니다. `limit`은 1~50입니다.
- `GET /api/analyses/<record UUID>`: 요청한 설치가 소유한 기록의 완전한 분석 응답을 그대로 반환합니다. 다른 설치의 UUID는 404입니다.

기록 API가 DB 오류를 반환해도 분석 요청 자체는 계속 처리합니다. 저장 대상은 `analysis_status.comparison_status`가 `available` 또는 `no_matches`이고 실행 상태가 `success` 또는 `partial`인 결과뿐입니다. 전문 기사와 분석 JSON이 저장되므로 운영 DB와 백업에는 서버 운영자만 접근해야 합니다.

분석 응답의 `X-Analysis-ID` 헤더를 [Vercel Logs](https://vercel.com/kim-seungmins-projects/frameless-team05-api/logs)의 `analysis_id`로 검색하면 한 요청의 단계별 소요 시간, 고정 오류 코드, 모델 작업별 호출 시간·성공·실패 수를 확인할 수 있습니다. OpenRouter가 응답에 제공한 토큰 수와 비용은 합산되며, 사용량이 빠진 응답은 0달러로 간주하지 않습니다. Android Logcat의 `FrameLESS.Analysis` 태그에도 응답 상태와 같은 ID가 기록됩니다. 현재 `c8cb5ae`의 `analysis_completed` 이벤트는 실행 상태, 허용된 단계 시간, 고정된 부분 실패 코드, 후보 검증 수, 모델 작업별 횟수·시간만 요약합니다. `discovery`에는 하위 단계가 포함되므로 단계 시간은 더하지 않습니다. 로그에는 기사 본문, 프롬프트, 모델 응답, API 키를 기록하지 않습니다. 모델 서비스의 일시적인 5xx 오류는 제한적으로 재시도하며, 끝내 실패하면 `503 analysis_unavailable`과 `Retry-After`를 반환합니다. 429는 서버 대기 지시와 실행 기한을 지키며 최대 두 번 재시도합니다. 서버 지시가 60초를 넘거나 일일 할당량 초과가 확인되면 현재 요청에서 재시도하지 않습니다.

운영 API는 프롬프트·모델 응답의 전체 스냅샷을 저장하지 않고 호출 횟수, 소요 시간과 제공자 사용량만 집계합니다. `DATABASE_URL`을 설정하면 최종 API 응답을 PostgreSQL에 짧게 저장합니다. 결과 캐시는 동일 URL·설정의 완전한 결과를 5분, 부분 결과를 1분 동안 다른 Vercel 인스턴스에서도 재사용합니다. 분석 기록은 캐시와 별개로, 앱이 다시 열 수 있는 마지막 분석 결과를 설치별로 보관합니다. 응답에는 기사 내용이 포함되므로 DB 접속 정보는 서버에만 보관합니다. DB가 없거나 일시적으로 실패하면 분석은 그대로 진행합니다. 기사 URL 수집은 DNS의 모든 주소를 공개 IP로 확인한 뒤 해당 주소에 직접 연결하므로, 로컬 네트워크가 직접 연결을 막고 프록시만 허용하면 수집이 실패할 수 있습니다. 이 코드는 컴퓨터의 프록시 설정을 변경하지 않습니다. API에는 인스턴스 간 공유되는 호출량 제한이 없습니다.

분석 품질 브랜치는 팀 OpenRouter 키가 있으면 이를 우선 사용합니다. 중심 사건·후보 본문·루브릭 작업에는 기본적으로 `google/gemini-3.8-flash`, 제목·설명 순위에는 `google/gemini-3.1-flash-lite`를 사용합니다. `OPENROUTER_QUALITY_MODEL`과 `OPENROUTER_METADATA_MODEL`로 각각 변경할 수 있습니다. 직접 Gemini 키만 있을 때는 `GEMINI_QUALITY_MODEL`과 `GEMINI_METADATA_MODEL`이 적용됩니다. 팀 키의 한도는 $100이며 추가 충전이 없으므로 실험 전후 `/api/v1/key` 또는 팀 Slack의 `/usage 5`로 잔액을 확인합니다. 배포 전 **운영 환경의 실제 모델 키**로 같은 기사 한 건을 끝까지 시험해야 합니다. Google Search grounding을 두 번째 후보 발견 경로로 시험하려면 직접 Gemini 키와 `ENABLE_GOOGLE_GROUNDED_SEARCH=1`이 필요합니다. 이 호출은 OpenRouter 예산과 별개의 Gemini 할당량을 쓰고, 오류가 나면 네이버 검색 결과를 계속 사용합니다. 기본값은 꺼짐입니다. 검색어와 결과는 판정 근거가 아니라 본문 수집 후보입니다.

함수 실행 제한 300초 아래에서 분석의 소프트 실행 예산은 기본 240초입니다. 운영 API는 대표 검색 주장 최대 3개·검색 후보 최대 8건을 사용하고, 확인된 비교 기사에서 전문 내용 단위 정렬과 A–G 분석을 수행합니다. 8건 설정을 고른 근거인 과거 실기사 시도에서는 12건이 240초를 넘고 8건이 124초에 완료됐습니다. **이는 현재 운영 지연 시간의 보장이 아닙니다.** 아래의 `53a5118` 실측은 244.599초였습니다. 후보 본문 검증은 남은 예산 중 Reader 몫을 남기고 중단될 수 있습니다. 로컬 스냅샷 캐시는 성공 결과도 최대 300초만 재사용합니다. 운영 Django 경로는 서버리스 파일 저장소를 공유 캐시로 사용하지 않고 PostgreSQL 결과 캐시를 선택적으로 사용합니다. 사용량 제한이 지속되면 검증한 기사만 결과에 남기고 부분 실패를 표시합니다.

### 운영 실측 예시와 해석

2026년 10월 19일 **이전 백엔드 `53a5118`**의 요청 `1f354ba5774b`에 대해 Vercel 로그는 전체 **244.599초**, `discovery` **220.732초**, 그 안의 `candidate_collection` **184.874초**, Reader 분석 **23.389초**를 기록했습니다. 앱의 첫 로딩 화면 캡처부터 HTTP 200까지는 약 239초였지만 측정 시작점이 달라 전체 서버 시간과 같은 수치로 취급하지 않습니다. `SEARCH_HTTP_ERROR`와 `GOOGLE_SEARCH_HTTP_ERROR`가 부분 결과의 확인된 원인입니다. 후보 검증 호출 두 건은 97.655초/84.758초, 추론 토큰 13,196/13,850개였으나 서로 다른 검증 wave에 있어 4-worker 변경의 실제 속도 개선을 입증하지 않습니다. 이 기록은 안전한 시간·코드·사용량만 요약하며 기사나 모델 내용을 포함하지 않습니다. PR #43의 새 완료 요약은 **이전 요청에는 적용되지 않았습니다**.

같은 날 **현재 운영 백엔드 `c8cb5ae`**의 요청 `7cd3876ac7f7`은 Android `402ce46` debug APK에 HTTP 200으로 돌아왔습니다. 서버 완료 요약의 전체 시간은 **118.6초**, `discovery` **87.8초**(그 안의 `candidate_collection` **57.2초**), Reader 분석 **30.3초**였습니다. 후보 검증 8건, 연기 0건이었고 실행 상태는 `partial`입니다. `SEARCH_HTTP_ERROR`와 `GOOGLE_SEARCH_HTTP_ERROR`가 확인됐습니다. 앱 화면이 나중에 다른 URL의 오류로 바뀌어 이 요청의 Reader 시트는 확인하지 못했습니다. 한 번의 118.6초 결과는 NFR-02의 10회 측정 기준을 충족하지 않으며, 앞선 244.599초 요청과 후보·실행 조건이 달라 병렬화에 따른 속도 개선 근거로 쓰지 않습니다.

이어 같은 기사 재시도 `43635d9499e5`는 Android logcat에 08:49:52 KST HTTP 200으로 기록됐고, Reader가 원시 문장 쌍 15/15개를 파싱했습니다. 원시 하이라이트는 0개였습니다. 앱의 관련 기사 시트 이동·확장·스크롤은 실제 화면에서 확인됐습니다. PR #43 완료 요약은 이 재시도에 대해 **178.970초**, `discovery` **113.411초**(내부 `candidate_collection` **80.302초**), Reader 분석 **65.383초**, 검증 8건·연기 0건을 기록했습니다. 실행은 `partial`이었고 `SEARCH_HTTP_ERROR`와 `GOOGLE_SEARCH_HTTP_ERROR`가 있었습니다. 이 실행은 120초 목표를 넘었으며 앞 요청의 118.6초를 재시도의 시간으로 사용하지 않습니다. 두 요청의 후보와 조건이 같았는지 확인하지 않아 속도 회귀도 단정하지 않습니다.

두 `c8cb5ae` 요청의 안전한 운영 진단에서는 NAVER 검색 6회와 Google grounded search가 HTTP 429를 반환했습니다. Exa 대체 검색 경로는 동작했고 후보 8건에 대한 검증 호출 8회가 기록됐습니다. HTTP 429는 공급자 제한 응답이라는 사실만 확인하며, 정확한 할당량·요금 원인은 이 로그만으로 단정하지 않습니다.

## PostgreSQL 캐시·분석 기록 설정

개인 Supabase 프로젝트 `frameless-team05`의 **Transaction pooler** URI(포트 6543)를 사용합니다. 서울 리전 프로젝트이며 Data API는 꺼두었습니다. URI의 비밀번호는 URL 인코딩하고, `DATABASE_URL`은 로컬 비밀 파일과 Vercel 서버 환경변수에만 둡니다. Android 앱과 Git에는 넣지 않습니다. 마이그레이션은 새 프로젝트에 한 번 실행합니다.

```sh
export DATABASE_URL='postgresql://postgres.PROJECT_REF:ENCODED_PASSWORD@POOLER_HOST:6543/postgres'
python manage.py migrate
```

그 다음 Vercel의 `DATABASE_URL`을 같은 URI로 설정하고 백엔드를 재배포합니다. 캐시가 켜진 요청의 응답 헤더는 `X-Analysis-Cache: MISS` 또는 `HIT`입니다. 같은 URL이라도 모델 설정이나 분석 코드가 바뀌면 다른 키를 사용합니다. 캐시는 입력 기사를 새로 가져오지 않으므로 완전한 결과도 5분 후 다시 분석합니다. 새 URL의 첫 분석 속도는 줄이지 않으며, 반복 분석의 지연과 모델 비용을 줄이는 첫 단계입니다. 앱 분석 기록은 이 TTL과 무관하며 별도 `frameless_analysis_record` 테이블에 저장됩니다.

스키마는 `server/models.py`와 `server/migrations/`에서 관리합니다. `frameless_cached_analysis`는 캐시 키를 기본키로, 만료 시각을 인덱스로 두고 결과를 JSONB에 저장합니다. `frameless_analysis_record`는 설치 해시·URL 해시의 유니크 제약과 `(owner_key, updated_at DESC)` 인덱스로 최근 기록 조회와 동일 URL 갱신을 지원합니다. 스키마를 바꿀 때는 새 마이그레이션을 만들어 검토하고 DB에 적용한 뒤 코드를 배포하며, 이미 적용한 마이그레이션은 수정하지 않습니다. 만료된 캐시 행은 다음 저장 시 삭제되므로 조회 가능 기간과 물리적 보관 기간은 다를 수 있습니다. 기록 규모가 커지면 설치별 보관 기간·최대 개수와 삭제 작업을 명시적으로 추가합니다.

## Vercel 운영 배포

기존 프로젝트는 [frameless-team05-api](https://vercel.com/kim-seungmins-projects/frameless-team05-api)이고, `vercel.json`이 서울 리전의 Django 함수를 설정합니다. 함수 실행 시간은 최대 300초로 설정했습니다.

| 환경변수 | 용도 |
| --- | --- |
| `DJANGO_ENV=production` | 운영 모드 |
| `DJANGO_ALLOWED_HOSTS=.vercel.app` | 허용 호스트 |
| `DJANGO_SECRET_KEY` | Django 비밀키 |
| `DATABASE_URL` | PostgreSQL 결과 캐시·앱 분석 기록. Supabase Transaction pooler URI, 서버 전용 |
| `OPENROUTER_API_KEY` | 팀 OpenRouter 키, 있으면 우선 사용 |
| `GOOGLE_API_KEY` | 직접 Gemini API 대체 경로, 선택 |
| `NAVER_CLIENT_ID`, `NAVER_CLIENT_SECRET` | 선택: NAVER API HUB 키. 없거나 인증 거부 시 Google News RSS 사용 |
| `OPENROUTER_QUALITY_MODEL` | 의미 판정 모델. 기본 `google/gemini-3.8-flash` |
| `OPENROUTER_METADATA_MODEL` | 후보 제목·설명 순위 모델. 기본 `google/gemini-3.1-flash-lite` |
| `OPENROUTER_VERIFIER_REASONING_EFFORT` | Gemini 3.6+ 후보 본문 검증의 사고 수준. 기본 `medium`, 비교 실험용 `low` 허용. 사고 토큰의 정확한 상한은 아님 |
| `OPENROUTER_SEED` | 재현성 실험에서만 명시. 기본 요청에는 보내지 않음 |
| `GEMINI_QUALITY_MODEL` | 의미 판정 모델. 기본 `gemini-3.8-flash` |
| `GEMINI_METADATA_MODEL` | 후보 제목·설명 순위 모델. 기본 `gemini-3.1-flash-lite` |
| `ENABLE_GOOGLE_GROUNDED_SEARCH` | 선택적 Google Search 후보 발견. 기본 꺼짐 |
| `GOOGLE_GROUNDED_SEARCH_MODEL` | 선택적 검색 모델. 기본 `gemini-3.8-flash` |

2026년 10월 18일 `main`의 Iteration 2 통합 코드를 이 프로젝트에 수동 배포했습니다. 이후 `c8cb5ae`(PR #43 진단 추가)를 운영 배포 `dpl_DXs23uScGDwNdJ7wy2QrwUaHLVHH`로 갱신했고 `/api/health`는 HTTP 200, `analysis_ready=true`였습니다. 이 배포의 첫 Android 요청 `7cd3876ac7f7`은 부분 결과로 응답했지만 시트는 확인하지 못했고, 두 번째 `43635d9499e5`에서는 관련 기사 시트 이동·확장·스크롤을 실제 화면에서 확인했습니다. 앞 절의 244.599초 기록은 이전 `53a5118` 배포의 것입니다. Git 자동 연결은 확인되지 않았으므로 **`main`에 푸시한 것만으로 운영 백엔드가 갱신됐다고 가정하지 마세요.** Vercel 프로젝트 관리 권한이 있는 계정으로 로그인한 뒤 저장소 루트에서 연결하고 수동 배포합니다.

```sh
npx vercel link --project frameless-team05-api --scope kim-seungmins-projects
npx vercel deploy --prod
```

배포 후 운영 주소의 `/api/health`를 확인합니다. `.vercelignore`가 로컬 비밀값과 Android 빌드 파일을 배포 대상에서 제외합니다. 기사 분석은 몇 분이 걸릴 수 있고, 제한 시간을 넘으면 실패할 수 있습니다.

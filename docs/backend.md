# 백엔드 개발·배포

Android 앱은 기본적으로 `https://frameless-team05-api.vercel.app`을 사용합니다. 로컬 백엔드는 API나 분석 코드를 수정할 때만 실행하면 됩니다.

## 로컬 실행

Vercel에서는 Python 3.12, 로컬에서는 Python 3.14로 실행한 적이 있습니다. `.env`에는 본인에게 허용된 Gemini 키와 NAVER API HUB 키를 넣습니다. `NAVER_CLIENT_ID`와 `NAVER_CLIENT_SECRET`은 일반 NAVER Developers Open API가 아닌 [NAVER API HUB 뉴스 검색](https://api.ncloud-docs.com/docs/naver-api-hub-search-news)용입니다.

```sh
python3 -m venv .venv
source .venv/bin/activate
pip install -r requirements.txt
cp .env.example .env
# .env에 GOOGLE_API_KEY, NAVER_CLIENT_ID, NAVER_CLIENT_SECRET 입력
chmod 600 .env
set -a; source .env; set +a
python manage.py runserver
```

서버 주소는 `http://127.0.0.1:8000`입니다. 실제 분석에는 세 API 키가 모두 필요하지만, 키가 없어도 서버 시작과 입력 검증은 가능합니다. 비밀값과 `.env`는 Git·Android 앱에 넣지 않습니다.

## API

```sh
curl http://127.0.0.1:8000/api/health
curl -X POST http://127.0.0.1:8000/api/analyze \
  -H 'Content-Type: application/json' \
  -d '{"url":"https://n.news.naver.com/article/057/0001971678","max_related":3}'
```

`GET /api/health`의 `analysis_ready: true`는 Gemini와 NAVER 키가 설정됐다는 뜻입니다. 빠진 키는 `missing_configuration`에 표시됩니다.

`POST /api/analyze`는 JSON 객체의 `url`을 받습니다. `max_related`는 생략 가능하며 범위는 1~3입니다. 성공 응답에는 `source_analysis`, `related_articles`, `comparison` 등이 들어갑니다. 오류는 `{"error":{"code":"...","message":"..."}}` 형식입니다. 분석 중에는 동일 프로세스의 다른 요청에 429와 `Retry-After`를 반환합니다. 기사 URL은 공개 HTTP(S) 도메인이어야 하며 localhost, IP 주소, 별도 포트는 허용하지 않습니다.

분석 응답의 `X-Analysis-ID` 헤더를 [Vercel Logs](https://vercel.com/kim-seungmins-projects/frameless-team05-api/logs)의 `analysis_id`로 검색하면 한 요청의 단계별 소요 시간과 모델 호출 이름·HTTP 오류 코드·응답 종료 사유·토큰 사용량을 확인할 수 있습니다. Android Logcat의 `FrameLESS.Analysis` 태그에도 응답 상태와 같은 ID가 기록됩니다. 로그에는 기사 본문, 프롬프트, API 키를 기록하지 않습니다. 모델 서비스의 일시적인 5xx 오류는 제한적으로 재시도하며, 끝내 실패하면 `503 analysis_unavailable`과 `Retry-After`를 반환합니다.

분석 결과를 저장하지 않으므로 현재 데이터베이스는 사용하지 않습니다. API에는 사용자 인증과 지속적인 호출량 제한이 없습니다.

## Vercel 운영 배포

기존 프로젝트는 [frameless-team05-api](https://vercel.com/kim-seungmins-projects/frameless-team05-api)이고, `vercel.json`이 서울 리전의 Django 함수를 설정합니다. 함수 실행 시간은 최대 300초로 설정했습니다.

| 환경변수 | 용도 |
| --- | --- |
| `DJANGO_ENV=production` | 운영 모드 |
| `DJANGO_ALLOWED_HOSTS=.vercel.app` | 허용 호스트 |
| `DJANGO_SECRET_KEY` | Django 비밀키 |
| `GOOGLE_API_KEY` | Gemini API 키 |
| `NAVER_CLIENT_ID`, `NAVER_CLIENT_SECRET` | NAVER API HUB 키 |

GitHub 조직의 Vercel 앱 설치 승인을 기다리고 있습니다(2026-10-04 기준).
**연결되기 전에는 `main` 푸시만으로 재배포되지 않습니다.** Vercel 프로젝트
관리 권한이 있는 계정으로 로그인한 뒤 저장소 루트에서 연결하고 수동 배포합니다.

```sh
npx vercel link --project frameless-team05-api --scope kim-seungmins-projects
npx vercel deploy --prod
```

배포 후 운영 주소의 `/api/health`를 확인합니다. `.vercelignore`가 로컬 비밀값과 Android 빌드 파일을 배포 대상에서 제외합니다. 기사 분석은 몇 분이 걸릴 수 있고, 제한 시간을 넘으면 실패할 수 있습니다.

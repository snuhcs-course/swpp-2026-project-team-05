# FrameLESS

뉴스 기사 하나를 분석하고 같은 사건의 다른 보도와 문장 표현을 비교하는 프로젝트입니다.
Android 화면에서 기사 URL을 입력하면 로컬 Django API를 호출하고 실제 분석 결과의
원문 인용과 출처 링크를 보여줍니다.

## 로컬 백엔드 실행

Python 3.14에서 확인했습니다. Gemini 키는 `GOOGLE_API_KEY` 또는
`GEMINI_API_KEY`로 설정할 수 있습니다. Gemini와 NAVER API HUB 키는 서버 프로세스에만
설정하고 Git이나 Android 앱에 넣지 마세요. `NAVER_CLIENT_ID`와
`NAVER_CLIENT_SECRET`은 일반 NAVER Developers Open API가 아닌
[NAVER API HUB](https://api.ncloud-docs.com/docs/naver-api-hub-search-news)의 값입니다.

```sh
python3 -m venv .venv
source .venv/bin/activate
pip install -r requirements.txt
cp .env.example .env
# .env 파일에 세 키의 실제 값을 입력한 뒤:
chmod 600 .env
set -a
source .env
set +a
python manage.py runserver
```

서버는 기본적으로 이 컴퓨터의 `127.0.0.1:8000`에서 실행됩니다. 다른 터미널에서
상태 확인과 분석 요청을 보낼 수 있습니다.

```sh
curl http://127.0.0.1:8000/api/health
curl -X POST http://127.0.0.1:8000/api/analyze \
  -H 'Content-Type: application/json' \
  -d '{"url":"https://n.news.naver.com/article/057/0001971678","max_related":3}'
```

`/api/health`의 `analysis_ready`가 `true`면 Gemini 키와 NAVER 키 두 개가 설정된 상태입니다.
키가 빠졌다면 `missing_configuration`에 해당 환경변수 이름이 표시됩니다.

`max_related`는 생략할 수 있으며 1~3 사이의 정수입니다. 성공 응답은 기존 분석
함수의 결과를 그대로 JSON으로 반환합니다. 주요 필드는 `source_analysis`,
`related_articles`, `candidates`, `screened`, `comparison`입니다. 오류는
`{"error":{"code":"...","message":"..."}}` 형태입니다. 분석 중에는 다른
요청에 HTTP 429와 `Retry-After` 헤더를 반환합니다. 여러 기사와 Gemini 응답을
기다려야 하므로 한 번의 분석에 시간이 걸릴 수 있습니다.

키가 없어도 서버 시작, `/api/health`, 입력 검증 테스트는 실행할 수 있습니다.
실제 비교 결과를 받으려면 위 두 서비스의 유효한 키가 필요합니다.

```sh
python manage.py check
python manage.py test
```

현재 분석 결과를 저장하지 않아 데이터베이스는 사용하지 않습니다. 기사 URL은
HTTP(S) 도메인 주소를 받으며 localhost, IP 주소, 별도 포트는 거부합니다.
이 서버는 **로컬 개발용**입니다. 인증이 없으므로 인터넷에 공개하지 마세요.

## Cloud Run 배포 준비

루트의 `Dockerfile`은 Django API를 Gunicorn으로 실행합니다. Cloud Run에서는
`DJANGO_ENV=production`으로 실행되며 `DJANGO_SECRET_KEY`가 반드시 필요합니다.
Gemini 키(`GOOGLE_API_KEY` 또는 `GEMINI_API_KEY`)와 `NAVER_CLIENT_ID`,
`NAVER_CLIENT_SECRET`도 런타임에 Secret Manager에서 주입해야 합니다. API 키와
`.env` 파일은 컨테이너 이미지나 Git에 포함하지 않습니다.

현재 `/api/analyze`에는 사용자 인증과 공유 저장소 기반의 호출량 제한이 없습니다.
이 기능을 추가하고 Android 앱을 HTTPS 주소로 연결하기 전에는 Cloud Run 서비스를
**비공개**로 유지해야 합니다. 결과 저장을 하지 않으므로 별도 DB는 아직 필요하지
않습니다.

## Android 에뮬레이터에서 확인

위 명령으로 Django 서버를 켠 상태에서 `android/` 프로젝트를 Android Studio로
열어 디버그 앱을 에뮬레이터에서 실행하세요. 앱은 에뮬레이터의 호스트 주소인
`http://10.0.2.2:8000`에 분석을 요청합니다. 인터넷 권한을 사용하며 로컬 HTTP
접속은 디버그 빌드에서만 허용합니다. Android 앱에는 API 키를 넣지 않습니다.
Android 빌드에는 JDK 21이 필요합니다.

기사 URL을 입력해 비교를 시작하면 관련 기사를 최대 세 개 분석합니다(입력 기사
포함 최대 네 개). 분석에는
몇 분이 걸릴 수 있습니다. 결과 화면은 백엔드가 실제로 확인한 대응 문장만
표시하며, 대응 문장이 없으면 빈 결과를 알려줍니다. 연결 오류는 입력 화면에
표시됩니다. 현재 설정은 Android 에뮬레이터용이며 실제 기기에서는 서버 주소를
기기에 맞게 바꿔야 합니다.

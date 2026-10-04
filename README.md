# FrameLESS

뉴스 기사 하나를 분석하고 같은 사건의 다른 보도와 문장 표현을 비교하는 프로젝트입니다.
Android 화면에서 기사 URL을 입력하면 Django API를 호출하고 실제 분석 결과의
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
로컬 개발에서는 인증 없이 요청할 수 있습니다. 원격 배포에서는 팀 테스트 코드가
필수이며, 키와 코드는 서버 환경변수로만 설정합니다.

## AWS EC2 배포 준비

루트의 `compose.yaml`은 Django API와 HTTPS 프록시(Caddy)를 실행합니다. EC2에
Docker Compose를 설치하고, 도메인이 인스턴스의 공인 IP를 가리키도록 설정한 뒤
보안 그룹에서 HTTP 80, HTTPS 443을 열어야 합니다. SSH 22는 관리자 IP에만
허용합니다. EC2를 중지했다 다시 켜면 공인 IP가 바뀔 수 있으므로 DNS도 갱신해야
합니다. EC2에 저장한 `.env`에는 다음 값을 설정합니다.

- `SERVER_DOMAIN`: 실제 도메인 이름. `https://` 없이 입력합니다.
- `DJANGO_SECRET_KEY`: `python3 -c 'import secrets; print(secrets.token_urlsafe(64))'`로 생성한 긴 임의 문자열.
- `ANALYZE_ACCESS_TOKEN`: 팀 테스트용 긴 임의 문자열. 별도로 생성하고 팀원에게만 전달합니다.
- `GOOGLE_API_KEY` 또는 `GEMINI_API_KEY`, `NAVER_CLIENT_ID`, `NAVER_CLIENT_SECRET`: 분석용 API 키.

비밀값과 `.env`는 Git이나 Android 앱 설정 파일에 넣지 마세요. 도메인과 키를
설정한 다음 EC2의 저장소 디렉터리에서 `docker compose up -d --build`를 실행합니다.
`https://도메인/api/health`의 `analysis_ready`가 `true`인지 확인하세요. 결과를
저장하지 않으므로 현재는 RDS 등 별도 DB가 필요하지 않습니다.

Android 앱은 기본적으로 팀의 Vercel 서버
(`https://frameless-team05-api.vercel.app`)를 사용합니다. 다른 서버를 쓰려면
Git이 무시하는 `android/local.properties`에 주소를 추가하세요. 기존 `sdk.dir`
줄은 유지합니다.

```properties
framelessBackendUrl=https://실제-도메인
```

앱을 빌드하면 기사 입력 화면에 `팀 테스트 코드` 입력칸이 보입니다. 서버에 설정한
`ANALYZE_ACCESS_TOKEN`을 입력해 테스트하세요. 테스트 코드는 앱에 저장되지
않아 앱을 다시 켜면 재입력해야 합니다. 로컬 Django 서버를 쓰려면
`framelessBackendUrl=http://10.0.2.2:8000`을 설정하고 다시 빌드하세요.

팀 테스트 코드는 임의 호출을 줄이기 위한 간단한 보호 장치입니다. 사람별
계정과 지속적인 호출량 제한은 없으므로 공개 출시 전에 별도로 구현해야 합니다.
EC2와 Gemini·NAVER API 사용량도 각각 확인하세요.

## Vercel 배포

`vercel.json`은 이 Django 프로젝트를 서울 리전의 Python 함수로 실행하고,
분석 요청의 최대 실행 시간을 300초로 설정합니다. DB는 사용하지 않습니다.
Vercel 프로젝트의 환경변수에 다음을 설정한 뒤 배포하세요.

| 변수 | 값 |
| --- | --- |
| `DJANGO_ENV` | `production` |
| `DJANGO_ALLOWED_HOSTS` | `.vercel.app` |
| `DJANGO_SECRET_KEY` | 길고 임의적인 새 문자열 |
| `ANALYZE_ACCESS_TOKEN` | 팀 테스트용 임의 문자열 |
| `GOOGLE_API_KEY` | Gemini API 키 |
| `NAVER_CLIENT_ID`, `NAVER_CLIENT_SECRET` | NAVER API HUB 키 |

비밀값을 `vercel.json`, Git, Android 앱에 넣지 마세요. `.vercelignore`가 로컬
`.env`와 Android 빌드 파일을 업로드 대상에서 제외합니다. 배포 후
`https://배포주소/api/health`에서 `analysis_ready: true`를 확인하고,
Android 앱의 기본 주소와 다른 프로젝트에 배포했다면
`android/local.properties`에 `framelessBackendUrl=https://배포주소`를
설정해 다시 빌드하세요.
Vercel 무료 Hobby의 함수 실행 한도는 300초입니다. 이 시간을 넘긴 기사 분석은
실패하므로 실제 기사로 끝까지 테스트해야 합니다.

## Android 에뮬레이터에서 확인

위 명령으로 Django 서버를 켠 상태에서 `android/local.properties`에
`framelessBackendUrl=http://10.0.2.2:8000`을 설정하세요. `android/` 프로젝트를
Android Studio로 열어 디버그 앱을 에뮬레이터에서 실행하면 호스트의 로컬 서버에
분석을 요청합니다. 인터넷 권한을 사용하며 로컬 HTTP 접속은 디버그 빌드에서만
허용합니다. Android 앱에는 API 키를 넣지 않습니다.
Android 빌드에는 JDK 21이 필요합니다.

기사 URL을 입력해 비교를 시작하면 관련 기사를 최대 세 개 분석합니다(입력 기사
포함 최대 네 개). 분석에는
몇 분이 걸릴 수 있습니다. 결과 화면은 백엔드가 실제로 확인한 대응 문장만
표시하며, 대응 문장이 없으면 빈 결과를 알려줍니다. 연결 오류는 입력 화면에
표시됩니다. 로컬 서버를 실제 기기에서 쓰려면 서버 주소를 기기에 맞게 바꿔야 합니다.

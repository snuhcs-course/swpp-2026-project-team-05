# EC2 배포 (선택 사항)

현재 팀 공용 백엔드는 Vercel에 있습니다. 이 디렉터리는 추후 EC2에서 Docker Compose와 Caddy로 Django API를 운영할 때 사용합니다.

1. EC2에 Docker Compose를 설치하고 도메인을 인스턴스의 공인 IP로 연결합니다.
2. 보안 그룹에서 HTTP 80·HTTPS 443을 열고, SSH 22는 관리자 IP에만 허용합니다.
3. 저장소 루트의 `.env.example`을 `.env`로 복사하고 `SERVER_DOMAIN`, `DJANGO_SECRET_KEY`, Gemini·NAVER API 키를 채웁니다. `SERVER_DOMAIN`에는 `https://`를 빼고 도메인만 적습니다.
4. 저장소 루트에서 실행합니다.

```sh
chmod 600 .env
docker compose --env-file .env -f deploy/ec2/compose.yaml up -d --build
curl https://도메인/api/health
```

`DJANGO_SECRET_KEY`는 `python3 -c 'import secrets; print(secrets.token_urlsafe(64))'`로 생성할 수 있습니다. `analysis_ready: true`면 분석 키가 설정된 상태입니다. 현재 분석 결과를 저장하지 않아 별도 DB는 필요하지 않습니다. EC2를 재시작해 공인 IP가 바뀌면 DNS도 갱신해야 합니다.

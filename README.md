# FrameLESS

News article comparison project for SNU Software Project. The current backend
analyzes a source article with Gemini, discovers related articles through Naver
News Search, and returns source-grounded sentence comparisons as JSON. The
Android client is being developed separately.

## Backend API

The Django API wraps the existing `analyze_related_articles()` function. It
does not store articles or results, so it does not need a database yet.

| Method | Path | Purpose |
| --- | --- | --- |
| `GET` | `/api/health` | Server health; no API credentials required |
| `POST` | `/api/analyze` | Analyze one article URL |

`POST /api/analyze` needs `Content-Type: application/json` and
`Authorization: Bearer <API_ACCESS_TOKEN>`. Its JSON body is:

```json
{"url": "https://n.news.naver.com/article/057/0001971678", "max_related": 3}
```

`max_related` is optional and must be an integer from 1 through 3. A successful
response is the existing pipeline result, with `source_analysis`,
`related_articles`, `candidates`, `screened`, and `comparison` fields. An error
has this shape:

```json
{"error": {"code": "invalid_url", "message": "..."}}
```

Analysis can take several minutes because it downloads articles and makes
multiple external API calls. The Android client should allow for a long request
timeout and show a progress state. One analysis runs at a time on the shared
server; another request gets HTTP 429 with a `Retry-After` header.

### Run locally

Python 3.13 is used on Render. The API also supports Python 3.14 locally.

```sh
python3 -m venv .venv
source .venv/bin/activate
pip install -r requirements.txt
export GEMINI_API_KEY='your-gemini-key'
export NAVER_CLIENT_ID='your-naver-id'
export NAVER_CLIENT_SECRET='your-naver-secret'
export API_ACCESS_TOKEN='a-long-random-team-test-token'
python manage.py runserver
```

In another terminal:

```sh
export API_ACCESS_TOKEN='the-same-token-as-the-server'
curl http://127.0.0.1:8000/api/health
curl -X POST http://127.0.0.1:8000/api/analyze \
  -H 'Content-Type: application/json' \
  -H "Authorization: Bearer $API_ACCESS_TOKEN" \
  -d '{"url":"https://n.news.naver.com/article/057/0001971678","max_related":3}'
```

The article downloader accepts only `n.news.naver.com`, `news.naver.com`, and
`m.news.naver.com` initially. Set `ALLOWED_ARTICLE_HOSTS` to a comma-separated
list of exact, trusted news hostnames if another outlet is needed. This rule
also applies to redirects and related articles returned by Naver Search.

Run the API tests without external credentials:

```sh
python manage.py test
python manage.py check
```

### Deploy on Render

The repository root contains `render.yaml` for one free Python web service in
Singapore. It uses Gunicorn, has a health check, and creates no database.

1. Merge this backend branch into the shared repository after review.
2. In Render, create a Blueprint from the GitHub repository and apply
   `render.yaml`. Connect the GitHub account with access to the team repository
   so later pushes can deploy automatically.
3. Enter `GEMINI_API_KEY`, `NAVER_CLIENT_ID`, `NAVER_CLIENT_SECRET`, and a long
   random `API_ACCESS_TOKEN` when prompted. Render generates
   `DJANGO_SECRET_KEY`. The Naver credentials must be for Naver Cloud's **NAVER
   API HUB**, which is what `article_search.py` calls. The regular Naver
   Developers Open API uses different credentials. Never commit these values.
4. Check `https://<service-name>.onrender.com/api/health`, then make an
   authenticated test request. Give teammates the URL and the test token
   through a private channel.

Render's free web service sleeps after inactivity, so the first request can be
slow. The shared bearer token is for team testing; do not embed it in a public
Android release. Add proper user authentication and request limits before
opening the analysis API to the public.

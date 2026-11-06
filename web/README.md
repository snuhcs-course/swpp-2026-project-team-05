# FrameLESS Web

Desktop web client for the FrameLESS pre-analyzed news experience. It uses the same authenticated Django API as the Android app.

## Run locally

```bash
npm install
npm run dev
```

Vite proxies `/api` to the deployed FrameLESS backend. To use another backend, copy `.env.example` to `.env.local` and set `VITE_API_BASE_URL`.

## Verify

```bash
npm run lint
npm test
npm run build
```

## Deploy

Deploy this directory as an independent Vercel project. `vercel.json` proxies API requests to the production backend and preserves client-side routes.

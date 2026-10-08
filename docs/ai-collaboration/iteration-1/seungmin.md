## AI Collaboration Contribution — Kim Seungmin

### 1. Summary of AI Usage

I used Codex (GPT-6 Sol, extra-high reasoning) as my primary development assistant for deployment and integration. I defined and reviewed the client–server architecture: the Android app submits article URLs to a Django API, while NAVER search and Gemini analysis run on the server. Codex helped implement the Android–API connection and prepare the backend for Vercel deployment.

### 2. Major Tasks Where AI Contributed

| Task | AI contribution | My role |
| --- | --- | --- |
| Connect teammates' Android UI and LLM analysis code | Implemented the Django API, Android request, response mapping, and error handling | Specified the request flow and reviewed the integration |
| Vercel deployment and diagnostics | Provided deployment guidance and implemented configuration and structured logs | Selected the deployment approach and verified the configuration and diagnostics |

### 3. Prompts and Results

These prompts summarize related instructions I gave Codex during development.

**Android UI and analysis integration.** Teammates had implemented the Android UI and LLM article-comparison functions separately. I directed Codex to connect them into one user flow.

> Use the team's existing Jetpack Compose screens and LLM analysis functions. Add a Django endpoint that accepts an article URL through `POST /api/analyze`, runs the analysis on the server, and returns `source_analysis` and `comparison` as JSON. In Android, map `same` matches to shared cards and `opposes` or `different_interpretation` matches to different cards. Show the cited sentences and original-article links, handle loading and API errors, and keep Gemini and NAVER credentials on the server.

Codex added the [Django API](https://github.com/snuhcs-course/swpp-2026-project-team-05/commit/f62b037) and [Android adapter](https://github.com/snuhcs-course/swpp-2026-project-team-05/commit/3ba9096) that connect those components. I reviewed the data flow and checked that the existing UI could display the analysis result.

**Shared deployment and diagnostics.** The team needed to test the app without running a local backend. An earlier AI-assisted Render configuration was abandoned because its sleep behavior did not meet that requirement.

> Deploy the Django API to Vercel's Seoul region (`icn1`) with Fluid Compute and a 300-second function limit. Keep Gemini and NAVER credentials in server environment variables, set the Android app's default backend URL to the deployed HTTPS endpoint, and verify `/api/health`. Add an `X-Analysis-ID` response header and the same ID to Android Logcat. Record timing and failures for article fetching, related-article search, model calls, and comparison without logging article text, prompts, or keys; return a distinct retryable error for temporary Gemini failures.

Codex produced the [Vercel configuration](https://github.com/snuhcs-course/swpp-2026-project-team-05/commit/47670a8), [long-running function setting](https://github.com/snuhcs-course/swpp-2026-project-team-05/commit/bb812a6), and [request diagnostics](https://github.com/snuhcs-course/swpp-2026-project-team-05/commit/fc0de10). I reviewed the configuration and logs. On 2026-10-08, `/api/health` returned HTTP 200 with `analysis_ready: true`; this confirms reachability and configuration, not comparison accuracy.

### 4. What Worked Well and What Required Human Verification

Codex accelerated deployment and integration, but I reviewed the architecture and confirmed that API credentials remained on the server. To make failures diagnosable, I directed Codex to add [request IDs and stage-level logs](https://github.com/snuhcs-course/swpp-2026-project-team-05/commit/fc0de10), allowing Android responses to be correlated with backend requests. On 2026-10-08, all 19 backend tests passed. Because they mock external services, they do not establish the quality of live LLM comparisons.

### 5. Reflection on the Development Process

Codex shortened the path from a local prototype to a shared backend. Its contribution was most effective when I supplied architectural constraints and verified the implementation against them. In the next iteration, I will continue using explicit integration checks and evaluate comparison quality with real articles separately.

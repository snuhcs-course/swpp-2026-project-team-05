## AI Collaboration Contribution — Kim Seungmin

### 1. Summary of AI Usage

I used Codex (GPT-6 Sol, extra-high reasoning) as my primary development assistant for deployment and integration. I defined and reviewed the client–server architecture: the Android app submits article URLs to a Django API, while NAVER search and Gemini analysis run on the server. Codex helped implement the Android–API connection and prepare the backend for Vercel deployment.

### 2. Major Tasks Where AI Contributed

| Task | AI contribution | My role |
| --- | --- | --- |
| Connect teammates' Android UI and LLM analysis code | Implemented the Django API, Android request, response mapping, and error handling | Specified the request flow and reviewed the integration |
| Vercel deployment and diagnostics | Provided deployment guidance and implemented configuration and structured logs | Selected the deployment approach and verified the configuration and diagnostics |

### 3. Representative Prompts and Outputs

The prompts below consolidate related instructions given during development and are edited for clarity.

**Example 1 — Integrating teammates' UI and analysis code.** Teammates had implemented the Android UI and LLM article-comparison functions separately. My task was to connect them into one user flow.

> Connect the existing Android UI and LLM analysis functions through a Django backend. Have the app send an article URL to `/api/analyze`, run the existing analysis on the server, and map the response to the UI's shared/different comparison cards and original-article links. Reuse the team's UI and analysis logic, and handle loading and errors in the app.

Codex added the [Django API](https://github.com/snuhcs-course/swpp-2026-project-team-05/commit/f62b037) and [Android adapter](https://github.com/snuhcs-course/swpp-2026-project-team-05/commit/3ba9096) that connect those components. I reviewed the data flow and checked that the existing UI could display the analysis result.

**Example 2 — Shared deployment and diagnostics.** The team needed to test the app without running a local backend. An earlier AI-assisted Render configuration was abandoned because its sleep behavior did not meet that requirement.

> Prepare the Django API for a shared Vercel deployment in the Seoul region. Configure a function duration suitable for long analyses, store Gemini and NAVER credentials only as server environment variables, and point the Android app at the deployed HTTPS API. Add request IDs and stage-level timing and error logs that can be correlated with Android Logcat, without logging article text, prompts, or API keys. Verify the deployment through `/api/health` and make transient model failures distinguishable from other errors.

Codex produced the [Vercel configuration](https://github.com/snuhcs-course/swpp-2026-project-team-05/commit/47670a8), [long-running function setting](https://github.com/snuhcs-course/swpp-2026-project-team-05/commit/bb812a6), and [request diagnostics](https://github.com/snuhcs-course/swpp-2026-project-team-05/commit/fc0de10). I reviewed the configuration and logs. On 2026-10-08, `/api/health` returned HTTP 200 with `analysis_ready: true`; this confirms reachability and configuration, not comparison accuracy.

### 4. What Worked Well and What Required Human Verification

Codex accelerated deployment and integration, but I reviewed the architecture and confirmed that API credentials remained on the server. To make failures diagnosable, I directed Codex to add [request IDs and stage-level logs](https://github.com/snuhcs-course/swpp-2026-project-team-05/commit/fc0de10), allowing Android responses to be correlated with backend requests. On 2026-10-08, all 19 backend tests passed. Because they mock external services, they do not establish the quality of live LLM comparisons.

### 5. Reflection on the Development Process

Codex shortened the path from a local prototype to a shared backend. Its contribution was most effective when I supplied architectural constraints and verified the implementation against them. In the next iteration, I will continue using explicit integration checks and evaluate comparison quality with real articles separately.

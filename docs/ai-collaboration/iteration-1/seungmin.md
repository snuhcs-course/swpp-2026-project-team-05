## AI Collaboration Contribution — Kim Seungmin

### 1. Summary of AI Usage

I used Codex (GPT-6 Sol, extra-high reasoning) as my primary development assistant for deployment and integration. I defined and reviewed the client–server architecture: the Android app submits article URLs to a Django API, while NAVER search and Gemini analysis run on the server. Codex helped implement the Android–API connection and prepare the backend for Vercel deployment.

### 2. Major Tasks Where AI Contributed

| Task | AI contribution | My role |
| --- | --- | --- |
| Connect teammates' Android UI and LLM analysis code | Implemented the Django API, Android request, response mapping, and error handling | Specified the request flow and reviewed the integration |
| Vercel deployment and diagnostics | Provided deployment guidance and implemented configuration and structured logs | Selected the deployment approach and verified the configuration and diagnostics |

### 3. Prompts and Results

I gave Codex high-level directions and reviewed the implementation as it progressed.

**Android UI and analysis integration.**

> The Android UI and LLM analysis functions are already implemented separately. Connect them through a backend so the app can send an article URL, the server can run the analysis, and the result can appear in the existing comparison screens. Keep the roles of the app and server clear, including how loading and failures are handled.

Codex added the [Django API](https://github.com/snuhcs-course/swpp-2026-project-team-05/commit/f62b037) and [Android adapter](https://github.com/snuhcs-course/swpp-2026-project-team-05/commit/3ba9096) that connect those components. I reviewed the data flow and checked that the existing UI could display the analysis result.

**Shared deployment and diagnostics.** An earlier AI-assisted Render configuration was abandoned because its sleep behavior did not meet the team's testing needs.

> Move the Django backend to Vercel so teammates can use the app without running a local server. Connect the Android app to the shared backend, keep API credentials on the server, and make it possible to trace an analysis failure from the app to the server logs.

Codex produced the [Vercel configuration](https://github.com/snuhcs-course/swpp-2026-project-team-05/commit/47670a8), [long-running function setting](https://github.com/snuhcs-course/swpp-2026-project-team-05/commit/bb812a6), and [request diagnostics](https://github.com/snuhcs-course/swpp-2026-project-team-05/commit/fc0de10). I reviewed the configuration and logs. On 2026-10-08, `/api/health` returned HTTP 200 with `analysis_ready: true`; this confirms reachability and configuration, not comparison accuracy.

### 4. What Worked Well and What Required Human Verification

Codex accelerated deployment and integration, but I reviewed the architecture and confirmed that API credentials remained on the server. To make failures diagnosable, I directed Codex to add [request IDs and stage-level logs](https://github.com/snuhcs-course/swpp-2026-project-team-05/commit/fc0de10), allowing Android responses to be correlated with backend requests. On 2026-10-08, all 19 backend tests passed. Because they mock external services, they do not establish the quality of live LLM comparisons.

### 5. Reflection on the Development Process

Codex shortened the path from a local prototype to a shared backend. Its contribution was most effective when I supplied architectural constraints and verified the implementation against them. In the next iteration, I will continue using explicit integration checks and evaluate comparison quality with real articles separately.

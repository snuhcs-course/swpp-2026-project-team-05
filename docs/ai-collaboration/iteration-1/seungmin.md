## AI Collaboration Contribution — Kim Seungmin

### 1. Summary of AI Usage

I used Codex (GPT-6 Sol, extra-high reasoning) as my primary development assistant for deployment and integration. I defined and reviewed the client–server architecture: the Android app submits article URLs to a Django API, while NAVER search and Gemini analysis run on the server. Codex helped implement the Android–API connection and prepare the backend for Vercel deployment.

### 2. Major Tasks Where AI Contributed

| Task | AI contribution | My role |
| --- | --- | --- |
| Backend–Android integration | Implemented API requests, response mapping, and error handling | Specified the request flow and reviewed the implementation |
| Vercel deployment and diagnostics | Provided deployment guidance and implemented configuration and structured logs | Selected the deployment approach and verified the configuration and diagnostics |

### 3. Representative Prompts and Outputs

**Example 1 — Connect the UI to the analysis API.** The goal was an end-to-end flow from article URL input to source-linked comparison results. A representative prompt (verbatim) was: “ui연결도 해줘 너가 이것저것 실험해보면서”. Codex connected the Android app to `POST /api/analyze` and mapped the JSON response into UI models. I reviewed the request flow and error handling before integrating the [Android connection code](https://github.com/snuhcs-course/swpp-2026-project-team-05/commit/3ba9096).

**Example 2 — Deploy a shared backend.** The backend needed to be accessible to teammates without a local server. An earlier AI-assisted Render setup was unsuitable because its sleep behavior conflicted with that requirement, so I redirected the work with this prompt (verbatim): “vercel쓰는거능 어떄/”. Codex prepared the [Vercel configuration](https://github.com/snuhcs-course/swpp-2026-project-team-05/commit/47670a8) and updated the [Android default backend URL](https://github.com/snuhcs-course/swpp-2026-project-team-05/commit/07d6a24). I reviewed the configuration; on 2026-10-08, the deployed `/api/health` endpoint returned HTTP 200 with `analysis_ready: true`. This verifies reachability and configuration, not comparison accuracy.

### 4. What Worked Well and What Required Human Verification

Codex accelerated deployment and integration, but I reviewed the architecture and confirmed that API credentials remained on the server. To make failures diagnosable, I directed Codex to add [request IDs and stage-level logs](https://github.com/snuhcs-course/swpp-2026-project-team-05/commit/fc0de10), allowing Android responses to be correlated with backend requests. On 2026-10-08, all 19 backend tests passed. Because they mock external services, they do not establish the quality of live LLM comparisons.

### 5. Reflection on the Development Process

Codex shortened the path from a local prototype to a shared backend. Its contribution was most effective when I supplied architectural constraints and verified the implementation against them. In the next iteration, I will continue using explicit integration checks and evaluate comparison quality with real articles separately.

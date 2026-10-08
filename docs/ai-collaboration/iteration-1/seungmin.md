## AI Collaboration Contribution — Kim Seungmin

### 1. Summary of AI Usage

I primarily used Codex (GPT-6 Sol, extra-high reasoning, based on my recollection) for deployment guidance and code integration. I specified and reviewed the client–server architecture: the Android app sends article URLs to a Django API, while NAVER search and Gemini analysis run on the server. Codex helped me connect the existing backend and UI and learn the Vercel deployment process step by step.

### 2. Major Tasks Where AI Contributed

| Task | AI contribution | My role |
| --- | --- | --- |
| Backend–Android integration | Generated and revised API connection and response-mapping code | Defined the request flow and reviewed the resulting implementation |
| Vercel deployment and diagnostics | Explained deployment steps and implemented configuration and request logs | Chose the architecture, checked deployment, and requested traceable logs |

### 3. Representative Prompts and Outputs

**Example 1 — Connect the UI to the analysis API.** I wanted users to move from entering an article URL to seeing source-linked comparison results. My prompt was: “ui연결도 해줘 너가 이것저것 실험해보면서”. Codex connected the Android app to `POST /api/analyze` and mapped the JSON response to the existing UI. I reviewed the request flow and error handling before integrating the [Android connection code](https://github.com/snuhcs-course/swpp-2026-project-team-05/commit/3ba9096).

**Example 2 — Deploy a shared backend.** I needed teammates to use the backend without running it locally. After rejecting an earlier Render free-tier setup because its sleep behavior did not meet our team-testing needs, I asked: “vercel쓰는거능 어떄/”. Codex guided me through the [Vercel configuration](https://github.com/snuhcs-course/swpp-2026-project-team-05/commit/47670a8) and changing the [Android default backend URL](https://github.com/snuhcs-course/swpp-2026-project-team-05/commit/07d6a24). I reviewed the deployment configuration. A check on 2026-10-08 showed that the deployed `/api/health` endpoint returned HTTP 200 with `analysis_ready: true`. This health check confirms reachability and configuration, not the accuracy of a full article comparison.

### 4. What Worked Well and What Required Human Verification

Codex made an unfamiliar deployment workflow easier to follow and sped up integration. I still reviewed whether the generated code respected the architecture and kept API credentials on the server. When failures were hard to locate, I directed Codex to add [request IDs and stage-level logs](https://github.com/snuhcs-course/swpp-2026-project-team-05/commit/fc0de10) so an Android response could be matched to a backend request. On 2026-10-08, 19 backend tests passed; they mock external services and therefore do not establish live LLM comparison quality.

### 5. Reflection on the Development Process

AI assistance helped me move more quickly from a local backend to a shared server. It was most useful when I gave Codex a specific design and then reviewed the implementation against that design. In the next iteration, I will keep using explicit checks for integration and deployment while evaluating real-article comparison quality separately.

# FrameLESS Iteration 2 통합 현황과 진행 순서

**기준일:** 2026-10-18 (KST)

**통합 상태:** [PR #10](https://github.com/snuhcs-course/swpp-2026-project-team-05/pull/10)이 `main`의 `6b75cb9`로 병합됨.

**상태:** 코드 통합·오프라인 검증 완료. 2026-10-18 기존 Vercel 운영 주소에 v2를 배포했고, 첫 실제 API 요청에서 부분 비교 결과를 받았다. 루브릭 분석은 검증 오류로 실패했고 Android 실서비스 요청은 로컬 에뮬레이터의 HTTPS 인증서 신뢰 오류로 아직 확인하지 못했다.

## 우리가 만들고 있는 것

사용자가 선택한 한국어 뉴스 기사 URL을 입력하면 같은 사건의 다른 기사에서 관련 구절을 찾아 **원문 링크와 함께** 비교한다. 정보 루브릭 I1–I5는 주장 대응, 배치, 배경, 전망, 인용 주체를 다루고, 표현 루브릭 A–G는 실제 다른 매체 표현을 기준으로 상대적 표지성을 제안한다. 대응 구절 미발견과 판단 불확실은 구별하며 기사 전체의 진위·정치 성향·편향 총점은 내지 않는다. 제품 목표와 세부 범위는 [Requirements and Specifications](https://github.com/snuhcs-course/swpp-2026-project-team-05/wiki/Requirements-and-Specifications), 구현상의 해석과 한계는 [루브릭 평가](rubric-evaluation-iter2.md)를 참고한다.

## 합쳐진 것과 아직 남은 것

진솔님의 Android UI v2 브랜치를 통합하고, 준서님의 standalone v2 분석 코드를 Django `POST /api/analyze`와 Android 응답 계약에 연결했다. 서버가 기사·주장 ID, 검증된 인용, 발언 주체, 부분 실패, 보류 상태를 유지하도록 어댑터를 두었다. 화면에 보이지 않는 기사까지 5단계 루브릭 분석을 호출하던 경로는 **최대 3개 표시 기사**로 제한하고, 서로 다른 입력 주장의 근거를 최대한 덮는 선택 규칙을 추가했다. 제외된 비교가 있으면 부분 결과와 경고를 남긴다. 초기·후속 관계 판정이 충돌할 때 비교 카드와 I1 카드가 모두 판정을 보류하도록 맞췄다. 서영님의 I1–I5/A–G 방향을 반영했지만, 질문별 묶음 P3는 아직 제안 단계이므로 구현·발표 완료 항목으로 세지 않는다. 실제 동작을 안정화한 뒤 시간과 품질이 허락하면 명세와 검증 기준을 정해 다룬다.

통합 코드 `5eb473e`에서 로컬 Django **64개**, CI 커버리지 검사 자체 **3개**, Android JVM **25개**가 통과했고, Android lint와 debug APK 빌드도 성공했다. Python API/어댑터 **87.7%**, v2 패키지 **66.5%**, Android data/state/ViewModel **84.4%/97.5%/98.5%**는 CI 하한을 넘는다. Compose UI는 **1.3%로 목표 30% 미달**이며 자동화 기기 테스트를 추가해야 한다. 합성 v2 HTTP 응답을 이용한 Android 에뮬레이터 화면 이동과 공개 기사 한 건의 입력·본문 추출 단계도 확인했다. 테스트 수치와 증거는 [Testing Documentation 초안](testing-documentation-iter2.md)에 기록한다. **첫 운영 API 요청은 HTTP 200, 46.3초였고, 입력 기사 주장 3개와 관련 기사 3개를 포함한 비교 결과를 반환했다. 그러나 `rubric_analysis`가 검증 단계에서 실패하여 `execution_status`는 `partial`이었다.** 원문 근거의 의미 정확도와 실제 서버→Android 전체 흐름은 검증 전이다. 합성 화면을 실제 LLM 분석 결과로 소개하지 않는다.

Vercel `frameless-team05-api`의 기존 운영 주소에 10월 18일 통합 v2를 배포했다. 운영 환경의 `GOOGLE_API_KEY`, `NAVER_CLIENT_ID`, `NAVER_CLIENT_SECRET` 변수명은 v2 코드의 필수 설정과 일치한다. 첫 요청에서 검색·비교는 결과를 반환했지만 루브릭 검증은 실패했다. **HTTP 200과 일부 카드가 있다고 전체 분석이나 결과 정확도가 확인된 것은 아니다.** [PR CI](https://github.com/snuhcs-course/swpp-2026-project-team-05/actions/runs/39119062346)의 Python·Android 두 작업은 통과했고 PR #10은 `main`에 병합됐다.

## 다음 작업 순서

1. **통합 검토:** 병합된 `main`에서 세 사람의 기능 연결, 근거 귀속, 오류·부분 결과 표시를 계속 검토한다. 준서님의 기존 PR #9는 원본 작업의 출처다. 루브릭 호출을 표시 기사로 제한했지만 검색·본문 검증 단계의 지연은 여전히 실제 서비스에서 측정해야 한다.
2. **루브릭 재검증:** 첫 운영 요청의 `rubric_analysis` 검증 오류를 수정하고 같은 기사로 다시 호출한다. `X-Analysis-ID`, 백엔드 리비전, 단계별 결과와 총 소요 시간을 기록한다. 비교·루브릭의 인용 구절, 링크, 발언 주체를 원문에 대조한다.
3. **Android 데모 확인:** 기본 APK의 대상 주소는 현재 v2를 배포한 Vercel 운영 주소다. 현재 로컬 에뮬레이터는 HTTPS 인증서 신뢰 오류로 요청이 막힌다. 적절한 네트워크 또는 기기에서 빌드된 APK의 대상 URL·리비전을 확인하고 입력→로딩→주장 목록→비교·루브릭 카드→원문 링크 순서로 검증한다. 실패와 재시도 화면도 확인한다. 이 단계가 통과해야 실제 v2 데모 영상으로 기록한다.
4. **루브릭 품질 검토:** 같은 사건/다른 사건, 부분 대응, 인용 반박, 다른 기사에만 등장하는 사실, 애매한 A–G 표현을 포함한 작은 사람 검토 세트를 만든다. 두 사람이 독립 판정한 뒤 차이를 조정하고 프롬프트·선별 규칙을 바꾼 경우 전후 사례와 지연을 함께 비교한다.
5. **발표·제출:** [11장 영어 중간발표 구성안](midterm-presentation-outline.md)을 실제 검증 결과로 갱신하고 10월 19일 23:59 KST 슬라이드 제출에 맞춘다. Iteration 2 제출(10월 23일 23:59 KST)에는 Wiki Requirements/Design/Daily Standup 갱신, 별도 Testing Documentation Wiki/PDF, `iteration-2-demo` 브랜치의 README·짧은 영상, eTL ZIP의 Requirements/Design/Testing/AI Collaboration PDF와 일정 XLSX가 필요하다. 실제 제출물·팀 담당자는 최종 공지와 팀 합의에 대조한다.

## 현재 결정이 필요한 입력

- 첫 운영 요청에서 실패한 루브릭 검증을 수정하고 실제 비교·루브릭의 원문 일치 여부를 확인한다. 키 값은 저장소·문서·대화에 넣지 않는다.
- 시험 책임자와 중간발표에서 보여 줄 팀원별 실제 작업·AI 사용 사례 확인.
- P3 질문별 묶음의 기대 출력과 사람 판정 기준. 현재는 핵심 데모가 안정적으로 동작한 뒤 가능한 범위에서 다룬다.

**관련 근거:** [중간발표 공지](https://swpp2026fall.slack.com/archives/C0BUL8N1864/p1791813453833289), [Iteration 2 제출 공지](https://swpp2026fall.slack.com/archives/C0BUL8N1864/p1791763218720289), [팀 역할·일정](https://swpp2026fall.slack.com/archives/C0C56TCMKU6/p1791896799209629), [루브릭 논의](https://swpp2026fall.slack.com/archives/C0C56TCMKU6/p1792113199423159).

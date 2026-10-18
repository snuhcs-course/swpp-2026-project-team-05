# FrameLESS Iteration 2 통합 현황과 진행 순서

**기준일:** 2026-10-18 (KST)

**작업 브랜치:** [`integration/iteration-2` · draft PR #10](https://github.com/snuhcs-course/swpp-2026-project-team-05/pull/10)

**상태:** 로컬 코드 통합 및 오프라인 검증 완료. 실제 NAVER/Gemini v2 요청과 배포된 Android 데모는 아직 검증 전.

## 우리가 만들고 있는 것

사용자가 선택한 한국어 뉴스 기사 URL을 입력하면 같은 사건의 다른 기사에서 관련 구절을 찾아 **원문 링크와 함께** 비교한다. 정보 루브릭 I1–I5는 주장 대응, 배치, 배경, 전망, 인용 주체를 다루고, 표현 루브릭 A–G는 실제 다른 매체 표현을 기준으로 상대적 표지성을 제안한다. 대응 구절 미발견과 판단 불확실은 구별하며 기사 전체의 진위·정치 성향·편향 총점은 내지 않는다. 제품 목표와 세부 범위는 [Requirements and Specifications](https://github.com/snuhcs-course/swpp-2026-project-team-05/wiki/Requirements-and-Specifications), 구현상의 해석과 한계는 [루브릭 평가](rubric-evaluation-iter2.md)를 참고한다.

## 합쳐진 것과 아직 남은 것

진솔님의 Android UI v2 브랜치를 통합하고, 준서님의 standalone v2 분석 코드를 Django `POST /api/analyze`와 Android 응답 계약에 연결했다. 서버가 기사·주장 ID, 검증된 인용, 발언 주체, 부분 실패, 보류 상태를 유지하도록 어댑터를 두었다. 서영님의 I1–I5/A–G 방향을 반영했지만, 질문별 묶음 P3는 아직 제안 단계이므로 구현·발표 완료 항목으로 세지 않는다. 실제 동작을 안정화한 뒤 시간과 품질이 허락하면 명세와 검증 기준을 정해 다룬다.

현재 통과한 것은 테스트 커밋 `91d7c29`의 Python 58개, Android JVM 25개, debug APK 빌드와 [두 작업이 성공한 PR CI](https://github.com/snuhcs-course/swpp-2026-project-team-05/actions/runs/39116785595)다. 합성 v2 HTTP 응답을 이용한 Android 에뮬레이터 화면 이동과 공개 기사 한 건의 입력·본문 추출 단계도 확인했다. 테스트 수치와 증거는 [Testing Documentation 초안](testing-documentation-iter2.md)에 기록한다. **NAVER 검색, Gemini 분석, 원문 근거의 의미 정확도, 실제 서버→Android 전체 흐름과 소요 시간은 아직 확인하지 않았다.** 합성 화면을 실제 LLM 분석 결과로 소개하지 않는다.

## 다음 작업 순서

1. **통합 검토:** 회귀 테스트를 보강하고 PR에서 세 사람의 기능 연결, 근거 귀속, 오류·부분 결과 표시를 검토한다. 준서님의 기존 PR #9는 원본 작업의 출처로 남겨 두고, 새 통합 PR의 적용 범위를 분명히 적는다.
2. **실제 v2 호출:** 서버에 허가된 NAVER API HUB/Gemini 키를 설정한 뒤 고정된 공개 기사 URL로 API를 호출한다. `X-Analysis-ID`, 백엔드·APK 리비전, 단계별 결과와 총 소요 시간을 기록하고 인용 구절·링크·발언 주체를 원문에 대조한다.
3. **Android 데모 확인:** 기본 APK는 이전 Vercel 백엔드를 가리키므로, 실제 v2가 배포된 URL을 `-PframelessBackendUrl=...`로 지정해 빌드하거나 기본 배포를 v2로 갱신한다. 빌드된 APK의 대상 URL을 확인한 뒤 같은 결과를 에뮬레이터 또는 기기에서 입력→로딩→주장 목록→비교·루브릭 카드→원문 링크 순서로 확인한다. 실패와 재시도 화면도 확인한다. 이 단계가 통과해야 실제 v2 데모 영상으로 기록한다.
4. **루브릭 품질 검토:** 같은 사건/다른 사건, 부분 대응, 인용 반박, 다른 기사에만 등장하는 사실, 애매한 A–G 표현을 포함한 작은 사람 검토 세트를 만든다. 두 사람이 독립 판정한 뒤 차이를 조정하고 프롬프트·선별 규칙을 바꾼 경우 전후 사례와 지연을 함께 비교한다.
5. **발표·제출:** [11장 영어 중간발표 구성안](midterm-presentation-outline.md)을 실제 검증 결과로 갱신하고 10월 19일 23:59 KST 슬라이드 제출에 맞춘다. Iteration 2 제출(10월 23일 23:59 KST)에는 Wiki Requirements/Design/Daily Standup 갱신, 별도 Testing Documentation Wiki/PDF, `iteration-2-demo` 브랜치의 README·짧은 영상, eTL ZIP의 Requirements/Design/Testing/AI Collaboration PDF와 일정 XLSX가 필요하다. 실제 제출물·팀 담당자는 최종 공지와 팀 합의에 대조한다.

## 현재 결정이 필요한 입력

- 실제 v2 서버 호출에 쓸 키 설정 경로. 키 값은 저장소·문서·대화에 넣지 않는다.
- 시험 책임자와 중간발표에서 보여 줄 팀원별 실제 작업·AI 사용 사례 확인.
- P3 질문별 묶음의 기대 출력과 사람 판정 기준. 현재는 핵심 데모가 안정적으로 동작한 뒤 가능한 범위에서 다룬다.

**관련 근거:** [중간발표 공지](https://swpp2026fall.slack.com/archives/C0BUL8N1864/p1791813453833289), [Iteration 2 제출 공지](https://swpp2026fall.slack.com/archives/C0BUL8N1864/p1791763218720289), [팀 역할·일정](https://swpp2026fall.slack.com/archives/C0C56TCMKU6/p1791896799209629), [루브릭 논의](https://swpp2026fall.slack.com/archives/C0C56TCMKU6/p1792113199423159).

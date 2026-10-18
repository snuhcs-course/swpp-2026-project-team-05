# FrameLESS 루브릭 구현 평가 — Iteration 2 통합 초안

**범위 (2026-10-18):** 이 문서는 현재 통합 작업 트리의 코드를 읽고 서영님이 공유한 루브릭 맥락과 대조한 평가다. 실제 Gemini·NAVER 응답의 정확도, 전체 데모 성공, 응답 시간은 아직 측정하지 않았다. 팀 명세 원문은 저장소에 없으므로 아래의 “채택” 표시는 코드가 명시한 적용 범위를 뜻한다. 근거: [`rubric_analysis.py`의 `analyze_selected_articles`](../server/analysis/v2/rubric_analysis.py), [`pipeline.py`의 `run_analysis_pipeline`](../server/analysis/v2/pipeline.py), [`v2_adapter.py`의 `_rubric_items`](../server/analysis/v2_adapter.py).

## 현재 적용 범위와 제품 의도

| 상태 | 기준 | 실제 구현과 해석 |
| --- | --- | --- |
| 채택 | **I1** 주장 포함 여부·관계 | 입력 기사의 핵심 주장별로 같은 내용, 반박, 다른 해석, 같은 쟁점의 다른 내용, 미발견·불확실을 구분한다. `not_found`는 **수집·분석한 본문에서 대응 구절을 못 찾음**이며 누락 의도나 거짓 판정이 아니다. |
| 채택 | **I2** 강조 위치·반복 | 모델이 식별한 대응 구간을 바탕으로 제목/리드/본문, 문장 수와 첫 위치를 Python이 측정한다. 기사 전체의 모든 대응 구간을 찾았다는 보장은 없다. |
| 채택 | **I3–I4** 배경·전망 | 배경/동기 해석과 미확인 전망을 원문 인용에 묶고, 전망의 추측 표지를 기록한다. 내용 분류 자체는 모델 판단이다. |
| 채택 | **I5** 인용·목소리 | 직접/간접 발언과 발언자·소속의 원문 근거를 대조한다. 직접 인용 내부 표현은 기자의 A–G 표지성에서 제외한다. |
| 채택 | **A–G** 표현 비교 | A 인용·보도 동사, B 발언 명명, C 행위·사건 서술어, D 정도·수량, E 평가·감정, F 일방적 용어, G 양태·확신·범주화다. 같은 명제의 **실제 다른 매체 표현**과 비교한 0–2 상대 표지성을 제안하며, 근거가 부족하면 `null`로 보류한다. **0은 절대 중립이 아니다.** |
| 제안, 미적용 | **P3** 질문별 묶음 | 현재는 입력 기사 핵심 주장 ID가 분석·화면의 축이다. 질문을 먼저 생성해 기사 간 답을 정렬하는 P3는 명세 제안이며 출력 `questions`도 빈 배열이다. 구현한다면 별도 명세·검증을 거쳐야 한다. |

코드는 기사 전체의 진위·정치 성향·편향 **총점**을 내지 않는다. 원문 URL과 대응 문장을 보여 주고 독자가 차이를 확인하게 하려는 FrameLESS의 목적에는 이 방향이 맞다. 다만 입력 기사 주장을 축으로 삼으므로 다른 기사에만 나온 핵심 사실이나 질문 구조는 충분히 드러나지 않는다. P3는 그 공백을 줄일 수 있는 후속 과제지만, 발표 전에는 검증된 I1–I5/A–G 경로가 우선이다. 근거: [`llm_analysis.py`의 `MATCH_RELATIONS`·`RELATION_INSTRUCTIONS`](../server/analysis/v2/llm_analysis.py), [`rubric_analysis.py`의 `PROPOSALS`·`analyze_selected_articles`](../server/analysis/v2/rubric_analysis.py), [`v2_adapter.py`의 `_rubric_items`](../server/analysis/v2_adapter.py), [제품 설명](../README.md).

## 코드에서 확인한 강점과 한계

- **근거 추적:** 주장·관계·표현의 인용을 기사 원문 구간과 문장 번호에 대조한다. 관계 주체가 기자인지 인용 발언자인지도 확인하고, I5 직접 인용을 기자 표현 채점에서 뺀다. 정보 분석의 검증 실패에는 한 번 수정 요청을 한다. 이는 허구 인용과 발언 주체 오인 위험을 줄이지만, 모델이 실제 구간을 *빠뜨린 경우*까지 검출하지는 못한다. 근거: [`llm_analysis.py`의 `_ground_span`·`_ground_relation_evidence`](../server/analysis/v2/llm_analysis.py), [`rubric_analysis.py`의 `analyze_article_information`·`_validate_information_response`·`compare_claim_expressions`](../server/analysis/v2/rubric_analysis.py).
- **보류 규칙:** 같은 사건만으로 `same`이나 반박을 만들지 않고, 기준 표현의 명제가 다르거나 근거가 없으면 A–G 표지성을 보류한다. F의 일방 사용 여부도 현재 기사 몇 개만으로 확정하지 않는다. 파이프라인 단계 불일치는 `uncertain`으로 낮추고 API는 부분 결과·오류를 구분한다. 근거: [`llm_analysis.py`의 `RELATION_INSTRUCTIONS`](../server/analysis/v2/llm_analysis.py), [`rubric_analysis.py`의 `retain_verified_comparison_context`·`compare_claim_expressions`](../server/analysis/v2/rubric_analysis.py), [`v2_adapter.py`의 `adapt_v2_result`](../server/analysis/v2_adapter.py).
- **아직 의미 정확도 미검증:** I1 관계, I3–I5 의미 분류, A–G 범주와 0–2 강도는 모델 판단이다. 문자 그대로의 인용 검증만으로 이 판단이 옳다고 증명되지 않는다. 표현 쌍의 후보는 문자열 유사도와 기사당 상한을 이용하므로 의미상 중요한 쌍을 놓칠 수 있다. 코드의 스냅샷 재호출은 스키마·동일 응답을 비교할 뿐 사람 기준의 의미 정확도를 채점하지 않는다. 근거: [`rubric_analysis.py`의 `measure_claim_placement`·`build_expression_pairs`·`compare_claim_expressions`](../server/analysis/v2/rubric_analysis.py), [`pipeline.py`의 `evaluate_analysis_snapshot`](../server/analysis/v2/pipeline.py).
- **재현성과 시간:** 기본 모델 설정은 `temperature=1.0`, `seed=42`다. seed와 스냅샷 기능은 재실행 비교에 유용하지만 응답의 재현성을 보장하지 않는다. 검색·후보 본문 검증 뒤 기사별 정보 분석과 주장별 표현 분석이 순차 실행되며, 잘못된 정보 응답에는 추가 호출이 있다. 운영 함수 제한은 **300초**이고 Django 경로는 결과 캐시를 쓰지 않는다. 실제 URL별 총 지연·호출 수를 측정하기 전에는 시간 내 완료를 주장할 수 없다. 근거: [`model_client.py`의 `get_generation_settings`·`GeminiJSONClient`](../server/analysis/v2/model_client.py), [`rubric_analysis.py`의 `analyze_selected_articles`](../server/analysis/v2/rubric_analysis.py), [`pipeline.py`의 `run_analysis_pipeline`](../server/analysis/v2/pipeline.py), [`views.py`의 `analyze_related_articles_v2`](../server/views.py), [Vercel 설정](../vercel.json).
- **표현 기준 미결:** 실제 다른 매체 표현을 baseline으로 쓰지만 무엇이 적절한 비교 기준인지 팀 명세상 아직 운영 결정이 필요하다. `provisional`/`needs_review`/`needs_comparison`과 `markedness=null`은 제품 화면에서도 유지되어야 한다. 현재 Android 어댑터는 유효한 표지 항목을 평탄화해 보여 주며, 제외·검증 실패의 상세 진단은 전달하지 않는다. 이는 데모 해석의 한계로 설명해야 한다. 근거: [`rubric_analysis.py`의 `compare_claim_expressions`·`analyze_selected_articles`](../server/analysis/v2/rubric_analysis.py), [`v2_adapter.py`의 `_rubric_items`](../server/analysis/v2_adapter.py).

## 중간 발표 전 우선 작업

1. **실제 연결 확인:** 서버 측 허용된 키로 고정한 공개 기사 URL을 분석하고, 입력→검색→본문 검증→루브릭→Android 근거 카드까지 확인한다. 단계별 시간, 모델 호출 수, 부분 실패와 300초 제한을 기록한다. 완료하지 못한 단계는 데모·슬라이드에서 명시한다.
2. **작은 사람 검토 세트:** 같은 사건·다른 사건, 부분 대응, 인용된 반박, 다른 기사에만 나온 사실, 직접 인용, F/G 애매 사례를 포함한 기사 쌍을 고정한다. 두 명이 원문을 보고 `같은 사건/주장 관계`, 근거 URL·구간, 발언 주체, I1–I5/A–G 및 보류 이유를 독립 기록한 뒤 불일치를 조정한다. 이 세트로 누락·오분류 사례를 보고하고 수치를 만들면 분모와 판정 규칙을 함께 공개한다.
3. **변동성·시간 개선:** 동일 입력 스냅샷으로 기본 설정과 낮은 temperature를 비교해 JSON 유효성, 근거 검증률, 사람 판정과의 일치, 지연을 확인한 뒤 설정을 결정한다. 단계별 호출 상한/시간 예산과 실패 시 부분 결과 표시를 점검한다. 낮은 temperature 자체를 정확도 향상으로 간주하지 않는다.
4. **P3는 안정화 이후:** 질문별 묶음의 기대 출력과 사람 판정 기준을 먼저 정한다. 현재 I1–I5/A–G를 축소하거나 0–2를 기사 총점으로 바꿔 구현하지 않는다.

오프라인 계약 테스트는 UI와 매핑의 오류를 잡는다. [Iteration 2 Testing Documentation](testing-documentation-iter2.md)의 결과와 별개로, 위의 실제 호출과 사람 검토가 끝나야 루브릭 품질을 발표 자료에서 실증할 수 있다.

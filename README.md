### 함수

| 파일 | 함수 | 입력 | 출력 | 기능 |
| --- | --- | --- | --- | --- |
| `llm_analysis.py` | `rank_comparison_candidates(...)` | 입력 사건·주장, 후보 제목·설명·URL | 기존 최초/예비 ID + `candidate_profiles`, `selection_notes` | 기존 LLM 1회에서 관련성·강조점·메타데이터 근거를 분류하고 Python으로 최대 3개 선택 |
| `llm_analysis.py` | `prioritize_diverse_candidates(metadata, candidate_profiles, selected_candidate_ids=None)` | 후보 메타데이터·강조점 프로필·이미 선택한 ID | 후보 우선순위·선택 과정 기록 | 직접 관련 기사로 시작한 뒤 같은 쟁점의 다른 강조점과 표현을 우선하고 중복 제목·설명을 억제 |
| `llm_analysis.py` | `_metadata_similarity(left, right)` | 후보 제목·설명 2개 | 0~1 표현 유사도 | 형식 표지·문장부호를 정리한 문자 3개 묶음으로 중복 표현 정도 계산. 사건·입장 판정과 구분 |
| `comparison_selection.py` | `select_verified_comparisons(...)` | 기존 검색·후보 순위·입력 주장 | 기존 본문·검증·최종 기사 + 회차별 `diversity_selection_notes` | 본문 검증된 기사와 아직 읽지 않은 예비 후보를 함께 비교해 빈자리 보충. 고유 기사 수집·검증 재사용 |
| `llm_analysis.py` | `verify_candidate_articles(...)` | 입력 기사·주장, 선택 후보 전체 본문 | 기존 판정 + 근거별 관계·귀속 | 기존 3개 배치 호출 안에서 발언 주체도 함께 판정하고 원문 검증 |
| `llm_analysis.py` | `_ground_relation_evidence(article, rows)` | 기사와 관계 근거 목록 | 위치·귀속·직접 발언 범위를 검증한 목록 | 원문·발언자·실제 인용을 검증하고 귀속 불확실성을 보존 |
| `rubric_analysis.py` | `analyze_article_information(..., verified_matches=None)` | 기사 전체 본문·주장·앞 단계의 검증 근거 | I1~I5 주석 + `verification_context`, `coverage_diagnostics` | 부분 대응을 재확인하고 단계 간 불일치를 미보도로 확정하지 않음 |
| `rubric_analysis.py` | `organize_expression_by_article(expression, article_ids)` | 기존 표현 분석·기사 ID | 기사 ID → A~G dictionary | 항목·제외·실제 기준 원문·값·보류 사유 정리 |
| `llm_analysis.py` | `_source_name_evidence(article, speaker, attribution)` | 기사·발언자 실명·검증된 귀속 구간 | 원문 실명 구간 또는 `None` | 성씨·직함 약칭을 원문 실명과 대조. `고 청장`의 청장 직함도 처리하며 원문에 없는 이름은 연결하지 않음 |
| `rubric_analysis.py` | `_ground_affiliation_evidence(article, evidence, attribution)` | 기사·출처 표지 근거·검증된 발언자 귀속 구간 | 실제 원문 위치를 가진 근거 | 동일 문장의 반복 표지는 해당 귀속 구간 안에서 정확히 한 번 나타날 때만 위치 확정. 잘못된 출처·모호한 구간은 거부 |
| `rubric_analysis.py` | `_same_grounded_speaker(relation, voice)` | 관계 근거와 I5 발언 주석 | `bool` | 실명과 직함이 붙은 이름의 동일 여부를 실제 귀속 범위와 대조 |
| `rubric_analysis.py` | `analyze_selected_articles(...)` | 기존 4단계 결과 | 기존 분석 + `information_by_article`, `expression_by_article` | 주장별 5가지 출력과 기존 필드를 유지하며 UI용 조회 구조 추가 |
| `model_client.py` | `get_generation_settings(model=None, *, temperature=None, seed=None)` | 선택적 모델·temperature·seed 및 환경변수 | 키를 포함하지 않는 설정 dictionary | 기본값·범위를 확인하고 생성 설정 반환 |
| `model_client.py` | `GeminiJSONClient(model=None, *, temperature=None, seed=None)` | 키 설정, 선택적 생성 설정 | 모델 클라이언트 | 명시한 temperature·seed로 기존 구조화 출력 호출 |
| `model_client.py` | `SnapshotJSONClient.generate_json(**request)` | 기존 모델 작업 인자 | 기존 응답 dictionary | 추가 호출 없이 정확한 작업 입력·응답·요청 해시 기록 |
| `pipeline.py` | `run_analysis_pipeline(..., use_cache=True, force_refresh=False, cache_dir=None)` | 기존 URL·옵션 + 캐시 설정 | 기존 결과 + `reproducibility`, `execution_status`, `errors`, `comparison_status` | 성공·부분 완료·실패 모두 dict 반환. 검색·수집 실패와 정상 결과 0건을 구분하고 가능한 결과 유지 |
| `pipeline.py` | `evaluate_analysis_snapshot(snapshot_path, *, client=None, temperature=None, seed=None, progress=None)` | 저장 스냅샷 경로·평가 설정 | 요청별 응답·일치 여부·스키마 검증·호출 수 | 후보·본문·요청 입력을 고정한 반복 평가 |
| `result_output.py` | `format_relation_with_owner(row)` | 관계와 귀속 정보 | 한국어 문자열 | 기자 서술·발언자 인용·귀속 미확인을 구분해 표시 |
| `related_example.py` | `main()` | 기존 옵션 + `--refresh`, `--no-cache`, `--cache-dir` | 기존 콘솔·JSON·파일 출력 | URL 전체 분석에 캐시 옵션 연결. `--input`·`--discovery-only`는 기존 방식 사용 |
| `article_fetch.py` | `fetch_article(url)` / `ArticleFetchError` | 공개 HTTP(S) 기사 URL | 제목·본문·매체·발행 시각 dict, 실패 시 단계·HTTP 상태를 가진 오류 | HTTP 실패와 본문 추출 실패를 구분. JavaScript 실행·로그인·유료벽 우회는 지원하지 않음 |
| `article_input.py` | `prepare_article(url)` | 기사 URL | 원문 + 문단·문장 번호·문자 위치 | 본문을 다시 쓰지 않고 이후 분석의 입력 구성 |
| `llm_analysis.py` | `analyze_source_article(...)` | 구조화한 입력 기사 | 사건·최대 5개 주장·원문 근거·검색어 | 독립된 술어로 주장 선택. 관객 수와 연속 순위처럼 따로 비교할 사실을 분리 |
| `rubric_analysis.py` | `retain_verified_comparison_context(annotation, context)` | 검증된 정보 주석·앞 단계 원문 근거 | 기존 주석 + 단계 간 불일치 기록 | 기존 대응 문장이 있는데 `not_found`로 바뀌면 `uncertain`으로 보류하고 근거 유지. 이전 판정을 정답으로 복사하지 않음 |
| `rubric_analysis.py` | `build_expression_pairs(claim, articles, annotations, limit_per_article=6)` | 주장·원문·검증된 I1~I5 주석 | 실제 문장 쌍·문구 차이·위치·인용 여부 | Python으로 비교 후보를 준비. 텍스트 유사도는 의미 일치나 편향 점수가 아님 |
| `rubric_analysis.py` | `find_reporting_verb_candidates(passage, annotation)` | 원문 구간·I5 주석 | 인용 밖 보도 동사의 원문·위치 목록 | `밝혔다/말했습니다` 등 제한된 패턴을 후보로 제시. A 분류·점수는 확정하지 않음 |
| `rubric_analysis.py` | `compare_claim_expressions(...)` | 주장·비교 기사 전체 본문·주석 | `findings`, `surface_differences`, `invalid_findings`, `diagnostics` | 같은 하위 명제의 A~G 비교. 평가어와 기준어를 원문 안에서 검증하고 직접 인용과 기자 서술 분리 |
| `result_output.py` | `format_analysis_result(result, show_details=False)` | 정상·부분 완료·실패 dict | 주장별 한국어 출력 | 실행 상태·오류·재시도 가능 여부와 기존 비교 내용을 함께 표시 |
| `article_search.py` | `search_naver_news(query, display=10)` | 검색어·개수 | 기사 메타데이터 목록, 실패 시 `OperationError` | HTTP·시간 초과·JSON 응답 파손을 구분. 파이프라인이 오류 dict로 변환 |
| `article_search.py` | `search_candidate_metadata(...)` | 입력 기사·사건·주장 검색어 | 기존 후보 + `errors`, `search_status` | 검색어별 실패를 기록하고 다른 검색어는 계속 실행. 전부 실패해도 입력 기사 분석을 유지할 수 있도록 반환 |
| `article_search.py` | `normalize_news_url(url)` | 기사 URL | 중복 판별용 문자열 | 네이버 뉴스·연예·스포츠 주소를 동일한 oid/aid 기준으로 합침 |
| `article_search.py` | `fetch_candidate_articles(metadata)` | 후보 메타데이터 | 본문 목록·실패 목록·오류 목록 | 후보별 수집 오류를 격리하고 대체 URL 시도. 복구된 경우 시도 기록만 남김 |
| `pipeline.py` | `run_discovery_pipeline(...)` | URL·선택 옵션 | 1~4단계 결과 + 실행 상태·오류 | 실패 단계와 이미 완료된 입력 기사 분석을 함께 반환 |
| `pipeline.py` | `complete_rubric_analysis(discovery, ...)` | 저장한 4단계 결과 | 5단계 결과 또는 기존 결과 + 오류 | 후속 처리 실패 시 앞 단계 결과 유지. 재시도 성공 시 해당 단계 오류를 복구 기록으로 이동 |
| `result_output.py` | `make_error_record(exc, stage=..., **context)` | 예외·단계·기사 ID/URL/검색어 | 오류 dict | `stage`, `code`, `message`, `retryable`, 선택적 HTTP 상태 등 생성. 원시 예외 문구·인증 헤더를 응답에 넣지 않음 |
| `result_output.py` | `make_failure_result(error, url='')` | 오류 dict·입력 URL | 실패 결과 dict | 분석 시작 실패에도 공통 최상위 필드 제공. 미생성 결과는 `None` |
| `result_output.py` | `add_result_error(result, error, fatal=False)` | 기존 결과·오류 | 수정된 결과 dict | 사용 가능한 데이터를 유지하며 실패·부분 완료 상태 설정 |
| `result_output.py` | `resolve_stage_errors(result, stage)` | 결과·복구된 단계 | 오류 상태가 갱신된 dict | 성공한 재시도의 기존 오류를 `resolved_errors`로 이동 |
| `result_output.py` | `load_discovery_result(path)` | 저장 JSON 경로 | 읽은 dict 또는 파일 오류 dict | UTF-8 BOM 지원. 파일 없음·인코딩·JSON 파손 구분 |
| `result_output.py` | `save_analysis_result(result, path)` | 결과 dict·저장 경로 | 기존 결과 또는 저장 오류가 추가된 dict | 폴더 생성·UTF-8 JSON 저장. 저장 실패해도 결과 반환 |
| `result_output.py` | `write_json_atomic(path, value, indent=None)` | JSON 경로·dict·들여쓰기 | 성공 시 `None`, 실패 시 예외 | 짧은 임시 파일명으로 저장 후 교체. 출력·캐시 저장에서 사용 |

### 실행 오류 output

기존 분석 필드와 LLM 판정은 유지하고, 실행 상태와 오류 정보만 추가합니다. UI는 분석 필드 접근 전에 `execution_status`를 확인해야 합니다. 실패 단계에서 아직 만들어지지 않은 `source_analysis`, `selection`, `rubric_analysis`는 `None`일 수 있습니다.

| 필드 | 값 | 의미 |
| --- | --- | --- |
| `execution_status` | `success` / `partial` / `failed` | 정상 완료 / 일부 실패했지만 결과 있음 / 분석 시작·진행 불가 |
| `errors` | 오류 dict 목록 | 현재 미해결 오류. 정상이면 `[]` |
| `comparison_status` | `available` / `unavailable` / `no_matches` / `not_requested` | 비교 기사 확보 / 실행 실패로 비교 불가 / 정상 처리했으나 대응 기사 없음 / 주장 없음으로 비교 미실행 |
| `resolved_errors` | 선택적 오류 dict 목록 | 재시도가 성공한 단계의 과거 오류 |


```json
{
  "execution_status": "partial",
  "comparison_status": "available",
  "errors": [
    {
      "stage": "candidate_fetch",
      "code": "ARTICLE_RESPONSE_INTERRUPTED",
      "candidate_id": "A1",
      "message": "기사 응답이 도중에 끊어졌습니다.",
      "retryable": true
    }
  ]
}
```

위 예는 오류 관련 필드만 발췌한 것입니다. 실제 dict에는 입력 기사·주장·가능한 비교 결과가 함께 있습니다.

| 상황 | 처리 | output |
| --- | --- | --- |
| 입력 URL/옵션 오류 또는 입력 기사 수집 실패 | 필요한 다음 단계 중단 | `failed`와 실패 코드. 수집 실패 시 분석 항목은 생성하지 않음 |
| 일부 또는 전체 검색 실패 | 실패 검색어 기록, 가능한 결과로 계속 진행 | 입력 기사 분석 유지, `partial`과 검색 오류. 정상 검색 결과 0건과 구분 |
| 후보 기사 수집 실패 | 대체 URL 시도, 실패 후보 제외·예비 후보 보충 | 나머지 기사 결과 유지, `partial`과 후보 ID·실패 사유 |
| 대체 URL 수집 성공 | 같은 후보의 본문을 정상 사용 | 복구되면 정상 처리. `fetch_attempt_errors`에 이전 시도 기록 |
| URL 중복 / UTF-8 BOM | 자동 중복 제거 / BOM을 처리하여 읽기 | 정상 처리되면 오류를 만들지 않음 |
| 입력 JSON 파일 없음·인코딩 오류·파손 | 파일 처리를 중단 | `INPUT_FILE_NOT_FOUND`, `INPUT_ENCODING_INVALID`, `INPUT_JSON_INVALID` 등 |
| 파일·캐시 저장 실패 | 메모리의 분석 결과를 그대로 반환 | `partial`과 `OUTPUT_WRITE_FAILED` / `CACHE_WRITE_FAILED` |



## 작동 파이프라인 과정

1. **기사 URL 입력 → `prepare_article()`**: 제목·매체·발행 시각·본문을 가져오고 문단·문장 번호와 원문 위치를 붙입니다.
2. **`analyze_source_article()` → LLM 호출 1**: 본문 전체를 번호가 붙은 구간으로 전달합니다. 핵심 사건 하나, 원문 근거·발언자·선택 기준이 붙은 핵심 주장 최대 5개, 사건 검색어와 주장별 보충 검색어를 받습니다. Python에서 필드와 원문 근거를 검증합니다.
3. **`search_candidate_metadata()` → 네이버 검색 API**: 사건·주장 검색어로 제목·설명·링크만 수집합니다. 중복 검색어·URL과 입력 기사를 제외하고 기본 20개의 메타데이터 후보 풀을 준비합니다. 사건 검색 후보 자리도 확보합니다.
4. **`rank_comparison_candidates()` → LLM 후보 선택**: 입력 기사 제목·발행 시각·핵심 주장과 후보 메타데이터로 주장별 최초 후보 최대 3개와 예비 순위를 정합니다. 후보 본문은 전달하지 않습니다.
5. **`fetch_candidate_articles()` → 선택 후보 본문 수집**: 최초 후보와 독립적인 사건 기준 후보의 URL만 읽습니다. 같은 후보가 여러 주장에 포함되면 한 번만 수집합니다. 실패하면 원문 링크를 시도하고 실패 기록을 남깁니다.
6. **`verify_candidate_articles()` → 본문 검증**: 기본 3개씩 전체 본문을 보내 같은 사건인지와 각 주장·쟁점의 대응 관계를 함께 확인합니다. Python은 모든 필수 ID와 원문 근거를 검증합니다. 같은 사건이면서 대응 근거가 있는 기사만 주장별 비교 목록에 확정합니다. 사건 기준 기사는 특정 주장 대응이 없어도 유지합니다.
7. **`select_verified_comparisons()` → 탈락 자리 보충**: 다른 사건·불확실·주장 미포함·수집 실패는 탈락으로 기록합니다. 이미 본문 검증된 대응 기사로 보충할 수 있으면 재사용하고, 부족하면 예비 순위의 다음 후보를 읽고 검증합니다. 최대 3개가 확인되거나 예비 후보가 소진되면 종료합니다. 읽지 않은 후보를 다른 사건·미보도로 처리하지 않습니다.
8. **5단계 분석 대상 구성**: 입력 기사 S0, 주장별 최종 비교 기사, `event_baseline_ids`의 사건 기준 기사를 합칩니다. 본문 수집·검증과 5단계 정보 분석은 각각 기사당 중복을 제거합니다. 사건 기준 자료가 없으면 경고합니다.
9. **`analyze_article_information()` → 기사별 LLM 정보 분석**: 고유 기사마다 전체 본문과 핵심 주장 목록을 한 번 전달합니다. 주장 대응·리드·배경·전망·인용을 받고 Python이 원문 위치를 검증합니다. 여러 주장에 선택된 기사의 주석은 재사용합니다.
10. **Python → I1~I5 구성**: 주장별 입력·비교·사건 기준 기사의 정보를 모읍니다. 제목·리드·본문 위치, 문장 구간 수·길이, 인용·발언자·명시된 소속별 횟수를 계산합니다. `not_found`를 누락 이유나 의도로 바꾸지 않습니다.
11. **`compare_claim_expressions()` → 주장별 LLM 표현 분석**: 입력 기사와 해당 주장에 선택한 기사들의 전체 본문·검증된 근거로 A~G를 비교합니다.
12. **`run_analysis_pipeline()` → JSON·콘솔 출력**: `rubric_analysis.claim_analyses`에 주장별 I1~I5·A~G를 반환합니다. `format_analysis_result()`는 입력 원문 아래 다른 기사 원문·정보·표현·링크를 보여줍니다. `--details`로 상세 근거를 보거나 `--output`으로 전체 JSON을 저장합니다.

주장이 없으면 검색·후보 선택·검증·루브릭 호출을 생략하고, 검색 후보가 없으면 후보 선택·검증 호출을 생략합니다. 비교 자료가 부족하면 표현 비교 호출을 생략합니다. 적절한 주장·비교 기사·차이 항목이 부족하면 개수를 억지로 채우지 않습니다. 기사 총점은 만들지 않습니다.

URL 입력
  → article_input / article_fetch: 본문 수집·구조화
  → llm_analysis: 사건·주장·검색어 추출
  → article_search: 관련 기사 검색
  → comparison_selection: 비교 기사 선택·검증·보충
  → rubric_analysis: 정보·표현 분석
  → 분석 결과 dict 반환

|LLM호출|
|입력기사에서 사건 주장 검색어 추출 1|
|제목·설명으로 주장별 후보 선택 1|
|선택 후보 본문에서 사건·주장 검증/후보본문들을 3개 단위로 묶어서 호출(ex 10개면 4번)|
|I1~I5 정보 분석 / 입력기사 + 후보기사 (ex 입력기사 1개,  후보 10개면 11번)|
|A~G 표현 비교 / 주장에 대해 입력기사, 비교기사 같이 전달 (주장이 3개면 3번)|

주장별 output, dictionary형태 
1. 입력 기사의 주장과 해당 원문 문장·발언자
2. 비교 기사 최대 3개의 제목·링크·대응 원문
3. 주장 관계: 같은 주장 / 반박 / 다른 해석 / 같은 쟁점의 다른 내용
4. 정보 구성 분석 I1~I5
5. 표현 비교 A~G와 비교 근거·보류 사유

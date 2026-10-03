# 뉴스 기사 비교 분석 모듈

기사 URL 하나를 입력하면 본문에서 핵심 사건과 주장을 찾고, 같은 사건을 다룬 다른 기사들이 원문의 각 문장을 어떻게 표현했는지 보여줍니다.

## 동작 흐름

1. `article_fetch.py`가 입력한 URL에서 기사 제목과 본문을 가져옵니다.
2. `llm_analysis.py`가 Gemini를 이용해 핵심 사건, 주장, 원문 근거 문장을 분석합니다.
3. `article_search.py`가 네이버 뉴스 검색 API로 후보를 찾고 URL 중복을 제거합니다. 후보의 본문을 읽어 실제로 같은 사건을 다룬 기사만 고릅니다.
4. `llm_analysis.py`가 원문 주장과 관련 기사의 대응 표현을 연결합니다. `related_example.py`는 이를 원문 문장별로 출력합니다.

`model_client.py`는 Gemini 호출을 담당하고, `requirements.txt`에는 필요한 패키지가 적혀 있습니다. 네이버 API는 **관련 기사 검색에만** 사용하며, 기사 본문은 URL에서 직접 가져옵니다.

## 실행 방법

Python 3.10 이상에서 다음 패키지를 설치합니다.

```bash
pip install -r requirements.txt
```

`GEMINI_API_KEY`, `NAVER_CLIENT_ID`, `NAVER_CLIENT_SECRET` 환경 변수를 설정합니다. 네이버 키는 **NAVER API HUB 뉴스 검색**용입니다. 키를 코드나 Git에 올리지 마세요.

`related_example.py`의 `link`에 분석할 기사 URL을 넣고 실행합니다.

```bash
python related_example.py
```

기본값은 관련 기사 최대 3개입니다. `show_details = True`로 바꾸면 명시된 이유와 표현 표지도 함께 볼 수 있습니다. 다른 코드에서는 `article_search.analyze_related_articles(url, max_related=3)`을 호출해 결과를 딕셔너리로 받을 수 있습니다.

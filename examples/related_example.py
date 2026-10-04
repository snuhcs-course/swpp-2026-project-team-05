"""Change the URL, then read how other articles phrase each source sentence."""

import re

from server.analysis.article_search import analyze_related_articles


link = "https://n.news.naver.com/article/057/0001971678?cds=news_media_pc&type=editn"
show_details = False  # True: also print explicit reasons and wording markers.

result = analyze_related_articles(link, max_related=3)
source = result["source_analysis"]
rows = result["comparison"]["sentence_comparisons"]
display_rows = rows if show_details else [row for row in rows if row["related"]]

print(f"원 기사: {source['title']}")
print(f"비교 기사: {len(result['related_articles'])}개 | 대응 표현이 있는 원문 문장: {len(display_rows)}개")
for index, article in enumerate(result["related_articles"], 1):
    print(f"  {index}. {article['title']}")

relation_names = {
    "same": "같은 취지",
    "opposes": "반대",
    "different_interpretation": "다른 해석",
}


def short_quote(text: str) -> str:
    spoken_parts = re.findall(r'[“"]([^”"]{8,})[”"]', text)
    return max(spoken_parts, key=len) if spoken_parts else text


for number, row in enumerate(display_rows, 1):
    print(f"\n{number}. 원문: {row['source_sentence']}")
    if not row["related"]:
        print("   관련 기사에서 대응 표현을 찾지 못함")
        continue
    seen = set()
    for member in row["related"]:
        key = (member["article_index"], member["evidence"]["quote"])
        if key in seen:
            continue
        seen.add(key)
        relation = relation_names.get(member["relation_to_source"], "관련 표현")
        voice = f"{member['speaker']} 발언" if member["speaker"] else "기사 서술"
        quote = short_quote(member["evidence"]["quote"])
        print(f"   [{member['article_index']}] {relation} / {voice}: \"{quote}\"")
        if show_details:
            if quote != member["evidence"]["quote"]:
                print(f"     발췌 전 문장: {member['evidence']['quote']}")
            reason = member["reason"]
            if reason and reason["quote"] not in member["evidence"]["quote"]:
                print(f"     이유: {reason['quote']}")
            if member["markers"]:
                markers = ", ".join(
                    f"{marker['text']}({marker['dimension']}/{marker['label']})"
                    for marker in member["markers"]
                )
                print(f"     표현 표지: {markers}")

if not show_details and len(rows) > len(display_rows):
    print(f"\n대응 표현을 찾지 못한 원문 문장 {len(rows) - len(display_rows)}개는 생략했습니다.")

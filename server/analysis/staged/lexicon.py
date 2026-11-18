"""Word lists the staged analysis decides with before (or instead of) the model.

Reporting verbs in three groups after 김병건 (2016ㄱ), modality modes after 김병건 (2016ㄱ) and 강계림 (2015), degree
words after 김병홍 (2018) and 이승선 (2008); the hide rules (same group, plain against function verb, same modality mode,
plain predicates on both sides) follow lay-reader judgments of earlier analyzer versions.

Entries are Kiwi content-word forms (verb and adjective stems, nouns before 하다).
"""

# A: reporting verbs by group. 0 neutral, 1 function or stance, 2 vivid or evocative.
A_GROUPS = {
    0: {"말", "밝히", "전하", "설명", "답", "응답", "덧붙이", "언급", "보도", "진술", "회신", "적", "쓰", "알리", "하",
        "판결", "판시", "선고", "공지", "확인", "열거"},
    1: {"강조", "지적", "주장", "해명", "반박", "재반박", "비판", "요구", "요청", "당부", "우려", "경고", "호소", "제안", "평가",
        "진단", "전망", "기대", "판단", "주문", "시사", "촉구", "반발", "항의", "문제", "제기", "인정", "시인", "부인", "반대",
        "거부", "짚", "내다보", "제언", "분석", "예고", "규정", "표명", "피력", "반문", "빗대", "겨냥", "역설", "설파",
        "되묻"},
    2: {"일축", "직격", "꼬집", "쏘아붙이", "강변", "둘러대", "실토", "털어놓", "자백", "몰아세우", "맞받", "받아치",
        "맞서", "못박", "질타", "성토", "비꼬", "압박", "위협", "맞불", "귀띔", "반색", "토로", "호통", "공세", "직격탄", "분통",
        "격앙", "발끈", "외치", "비난", "따지", "규탄", "개탄", "맹비난", "맹공", "질책", "일갈", "힐난", "혹평", "일침",
        "비아냥"},
}

# Predicates a report uses for the same fact without a judgment in them (the S4b judge called them similar in one
# event and different in another: 노출 / 담기 / 게시 / 올라오 shown 22 + 6 + 4 + 5 times, called similar 5 + 3 + 1 + 0;
# eval runs 2026-11-16). Two spans whose predicates are all on this list, with no evaluative word, are no difference.
# Left out on purpose: 검토 / 추진 (a certainty difference, G), 유출 (a leak, not an exposure), reporting verbs (A).
NEUTRAL_PRED = {
    # a fact made known or recorded
    "노출", "공개", "게시", "올라오", "올리", "담기", "담", "포함", "기재", "기록", "나오", "발표", "공표", "집계", "적히",
    # rise and fall
    "오르", "상승", "늘", "늘어나", "증가", "커지", "확대", "높아지", "높", "줄", "줄어들", "감소", "낮아지", "떨어지", "하락",
    "넘", "넘어서", "넘기", "달하", "이르", "웃돌", "밑돌",
    # meetings, procedure, office
    "열", "열리", "개최", "진행", "참석", "참여", "방문", "시찰", "출국", "도착", "만나", "회동", "의결", "처리", "통과", "가결",
    "상정", "제출", "지명", "임명", "선출", "당선", "발생", "일어나", "접수", "착수", "시행", "도입", "마련", "실시", "운영",
    "지원", "지급", "확정", "결정", "적용", "담당", "맡", "전담", "총괄", "근무", "지내", "거치", "이송", "옮기", "이관", "받",
    "주", "내", "가", "오", "보", "알",
}
# Words that put a judgment on a plain predicate: «무더기로 노출된» stays a candidate beside «노출됐다».
EVALUATIVE = ("무더기", "버젓이", "털리", "털렸", "고스란히", "대거", "마구", "줄줄이", "송두리째", "속수무책", "허술", "구멍",
              "뒤늦게", "끝내", "급기야", "아예", "기어이", "강행", "날치기", "일방", "속전속결", "기습", "꼼수", "폭주", "졸속",
              "무차별", "대놓고", "노골", "또다시", "대규모", "대대적", "총출동", "초토화", "빗발", "봇물", "들끓")

# G: sentence-final modality and evidential forms by mode. 3 assertion or endorsement, 2 the reporter's estimate,
# 1 hearsay or a voice with no source.
G_MODES = {
    3: ("확인됐", "확인된", "드러났", "드러난", "나타났", "밝혀졌", "조사됐", "분석됐", "파악됐", "파악된", "확실시",
        # categorization the reporter asserts
        "사실상", "셈이다", "셈이었", "분명하다", "분명해"),
    2: ("보인다", "보인", "보였", "보여진", "예상된", "예상되", "예상이다", "전망된", "전망되", "전망이다", "관측된", "관측되",
        "추정된", "추정되", "가능성", "수 있", "공산", "미지수", "풀이된", "풀이되", "듯", "해석된", "해석되", "분석된다",
        "추산된", "추산되", "점쳐진", "점쳐지", "예측된", "예측되", "평가된다"),
    1: ("알려졌", "알려진", "알려져", "전해졌", "전해진", "전해져", "라고 한다", "다고 한다", "관측이 나", "전망이 나", "지적이 나",
        "말이 나", "목소리가 나", "얘기가 나", "우려가 나", "라는 분석이다", "란 분석이다", "라는 관측이다", "란 관측이다",
        "라는 평가다", "란 평가다", "라는 지적이다", "란 지적이다", "라는 설명이다", "란 설명이다", "후문이다", "전언이다"),
}
# Hearsay first: '전망이 나온다' is a voice with no source (1), not the reporter's '전망이다' (2).
G_ORDER = (1, 3, 2)
# Vivid reporting phrases of more than one word.
A2_PHRASES = ("날 선", "날을 세", "목소리를 높", "목소리를 냈", "쓴소리", "선을 그", "포문을 열", "각을 세", "잘라 말",
              "못 박", "직격했", "직격한", "털어놨", "털어놓", "강하게 비판", "강력히 비판", "강도 높게", "비판을 쏟아",
              "저격", "엄포", "으름장", "칼을 빼", "쐐기를 박")
# Plain reporting phrases of more than one word.
A0_PHRASES = ("운을 뗐", "운을 떼")

# D: degree words on a quantity or a fact (the value itself is N, not D). Only words that tilt a number by themselves:
# '달했다 / 이른다', '넘게', '크게 달라지지 않', '잇따라 달렸다' were plain beside their counterparts (v2 replay).
D_WORDS = ("급증", "급감", "폭증", "폭락", "급등", "급락", "무려", "불과", "그쳤", "그친", "그치", "훌쩍", "대폭", "고작", "겨우",
           "눈덩이", "역대 최대", "역대 최고", "최고치", "최대 폭", "속출", "대규모", "대대적",
           # added after a count over 1,484 gold2 articles (2026-11-18): records, multiples, steep moves, playing down
           "사상 최대", "사상 최고", "최대치", "역대급", "큰 폭", "두 배", "치솟", "급격히", "가파르게", "껑충", "쑥 늘",
           "쑥 올", "뚝 떨어", "곤두박질", "반토막", "폭발적", "절반이나", "소폭", "미미", "턱없이")

# Words that make no difference a reader would see when they are all a candidate has: time and place adverbs, report
# frames, sentence links, titles, counters.
STOP = {"이날", "오늘", "전날", "지난", "지나", "오전", "오후", "새벽", "당시", "현재", "이번", "관련", "대하", "통하", "따르", "위하",
        "경우", "가운데", "이후", "이전", "앞서", "이어", "한편", "또", "또한", "등", "측", "씨", "의원", "대표", "위원", "최고위원",
        "장관", "대통령", "기자", "관계자", "전", "약", "두", "한", "세", "이", "그", "해당", "같", "이러", "그러", "하", "되", "있",
        "없", "때", "중", "께", "쯤", "가량", "뒤", "후", "전체", "모두", "각각", "다른", "이같이"}


def a_group(text: str, forms: list[str]) -> int | None:
    """The highest reporting-verb group among a span's words, or None when it has none. '하다' alone counts as a plain
    reporting verb ('…라고 했다'); after another noun it is that noun's verb ('처리했다') and says nothing."""
    if any(p in text for p in A2_PHRASES):
        return 2
    if any(p in text for p in A0_PHRASES):
        return 0
    found = [g for g, words in A_GROUPS.items() for f in forms if f != "하" and f in words]
    if found:
        return max(found)
    return 0 if forms and set(forms) <= {"하"} else None


def verb_only(forms: list[str]) -> bool:
    """A span that is a reporting verb and nothing else: every content word is a listed reporting verb, '하다' or a
    stop word ('이같이 말했다')."""
    listed = set().union(*A_GROUPS.values()) | STOP | {"하"}
    return bool(forms) and all(f in listed for f in forms)


def g_mode(text: str) -> int | None:
    for mode in G_ORDER:
        if any(m in text for m in G_MODES[mode]):
            return mode
    return None


def plain_predicates(pred_forms: list[str], text: str) -> bool:
    """A span whose predicates are all neutral (NEUTRAL_PRED) and that carries no evaluative word."""
    return bool(pred_forms) and all(f in NEUTRAL_PRED for f in pred_forms) and not any(w in text for w in EVALUATIVE)


# Plan nouns: two spans that differ only in these tell the same plan (방침이다 / 예정이다 / 계획이다); the S4b judge
# called all 11 such pairs similar in the v6 runs.
PLAN_WORDS = {"예정", "방침", "계획"}


def plans_only(this_forms: list[str], other_forms: list[str]) -> bool:
    """Two spans whose content words differ only in plan nouns, each holding one."""
    differ = set(this_forms) ^ set(other_forms)
    return (bool(differ) and differ <= PLAN_WORDS and bool(set(this_forms) & PLAN_WORDS)
            and bool(set(other_forms) & PLAN_WORDS))


def d_word(text: str) -> bool:
    return any(w in text for w in D_WORDS)

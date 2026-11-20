# Event issue view

| Field | Value |
| --- | --- |
| Status | Draft for review |
| Owner | Analysis |
| Clients | Web |
| Test specification | `specs/testing/event-issue-view.spec.md` |

## Purpose

Outlets covering one event take different sides on its issues and word the same facts differently. The reader sees,
for the article being read:

1. a frame box: the event in one line and this article's frame (one of the Media Frames Corpus frames, Card et al.,
   2015) with one sentence of its own text that shows it;
2. at the bottom, the event's issues: for each, the position this article takes (or that it only reports the facts,
   or does not cover the issue), the other positions with the outlets taking them, the outlets that only report
   facts, and the wording differences between two outlets inside the issue (A-G);
3. the facts the outlets report alike, with the wording differences inside them.

Every article of the event reads the same issue table from its own side, so a wording difference between two outlets
is one item that both articles show. The view does not judge whether an outlet is right or biased.

## Where it comes from

The issue table and the frames are produced offline and reviewed before import; the API never calls a model for
them. The offline analysis gathers about ten outlets on the event, extracts the facts and the issues from all of them
(Key Point Analysis, Bar-Haim et al., 2020), places each outlet on each issue from its own sentences, and picks the
wording differences in the reporters' own words (quotations are not compared).

## Stored data

`StoryCluster.issue_table` (nullable JSON):

```json
{
  "event": "국회, 조작기소 특검법 통과",
  "issues": [
    {"key": "K1", "question": "...가?", "kind": "contested",
     "positions": {"P1": "...", "P2": "..."},
     "outlets": [{"article": "<article uuid>", "position": "P1"}, {"article": "<article uuid>", "position": "F"}],
     "wordings": [{"left": {"article": "<article uuid>", "phrase": "단독 상정 강행"},
                   "right": {"article": "<article uuid>", "phrase": "與 주도로 본회의 상정"},
                   "code": "C", "explanation": "같은 행위를 서술하면서 ... 쪽이 더 센 말을 골랐습니다."}]}
  ],
  "facts": [{"key": "F1", "label": "...", "outlets": ["<article uuid>"], "wordings": []}]
}
```

| Field | Values |
| --- | --- |
| kind | `contested` (outlets take different positions), `one_side` (one position is taken, others only report facts), `common_fact` (all only report facts) |
| position | a key of `positions`, or `F` (reports the issue only as fact) |
| code | `A`-`G`, as in the expression findings |

`Article.frame_context` (nullable JSON): `{"frame": "합법성·사법", "sentence": {"scope": "BODY", "start": 120, "end": 168, "text": "..."}}`.

In the dataset the articles are named by their dataset keys; the importer stores their ids. It rejects an unknown
article, an article twice in one issue or fact, an unknown position or kind or code, a wording within one article or
whose phrase is not words of its article, a duplicate issue or fact key, an empty event, and a frame sentence that
does not match its offsets. A cluster revision without an issue table clears the old one. The import is atomic.

The A-G items are also stored as expression findings, one per marked phrase of each article, with every compared
article as a comparison target, so the existing highlights show them on both articles.

## Reader API

`GET /api/v1/articles/{id}/analysis` adds `frame`:

```json
{
  "event": "국회, 조작기소 특검법 통과",
  "frame": "합법성·사법",
  "sentence": {"scope": "BODY", "start": 120, "end": 168, "text": "..."},
  "others": [{"article_id": "uuid", "publisher": {...}, "title": "...", "frame": "정치", "sentence": "...",
              "positions": [{"issue": "...가?", "position": "..."}]}],
  "issues": [{"question": "...가?", "kind": "contested",
              "own": {"covered": true, "fact_only": false, "position": "..."},
              "positions": [{"label": "...", "is_own": true, "outlets": [{"article_id": "uuid", "publisher": {...}}]}],
              "fact_only_outlets": [{"article_id": "uuid", "publisher": {...}}],
              "wordings": [{"left": {"article_id": "uuid", "publisher": {...}, "phrase": "..."},
                            "right": {"article_id": "uuid", "publisher": {...}, "phrase": "..."},
                            "code": "C", "explanation": "..."}]}],
  "facts": [{"label": "...", "outlets": 7, "wordings": []}]
}
```

It is `null` when the article has no frame, its cluster has no issue table, or its frame sentence no longer matches
the stored article. The view is computed per request from the cluster's table:

- only visible articles of active publishers appear; positions, outlets and wordings naming any other are left out;
- the position groups list the other outlets (this article's group is marked `is_own`);
- issues come in the order the reader meets them: the ones this article takes a position on, then the ones it
  reports only as fact, then the ones it does not cover; within each, contested, one-sided, then common facts;
- `others` lists the other outlets with a frame, those taking positions first, with at most three positions each;
- `facts[].outlets` counts the visible outlets reporting the fact.

## Web display

- Header, under the publisher line: a Frame box with the event and this article's sentence and frame; the sentence is
  underlined in teal. An economy article with an economic lens shows the lens there instead.
- Below the article: "How other outlets see this event", one block per issue on which some outlet takes a position:
  the question, each position with the outlets taking it linked to their articles (this article's position tagged
  "This article"), and the outlets reporting the issue as fact only. The wording differences and the shared facts are
  not shown there (the A-G highlights in the article carry the wording differences). Under an economic lens the
  section is titled "Issue by issue across outlets".

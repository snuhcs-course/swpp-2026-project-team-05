#!/usr/bin/env python3
"""Read-only production API smoke for the six Iteration 3 demo stories."""

from __future__ import annotations

import json
import os
import statistics
import sys
import time
import urllib.parse
import urllib.request


BASE_URL = os.environ.get(
    "FRAMELESS_SMOKE_BASE_URL", "https://frameless-team05-api.vercel.app"
).rstrip("/")

# title: (case, expected finding count, expected public comparison-page count)
CASES = {
    "HD현대일렉트릭, 3분기 매출 1조1799억원 '역대 최대'": ("b08", 2, 3),
    "에버랜드 막내 판다 '슈바오' 30일 첫 나들이…쌍둥이 언니들은 작별 준비": (
        "b12", 1, 3
    ),
    "[한 손 뉴스] 변호사 업무 재개한 홍준표, 대선 경선 관계자 변호 맡아(종합)": (
        "b16", 3, 8
    ),
    "홍대 12만·이태원 2만 인파에도 '우측통행'…\"즐기는 것 좋지만 안전 최선\"": (
        "b33", 3, 3
    ),
    "한화, 美 해군 함정 건조 '첫' 수주…필리조선소 인수 2년만": ("b34", 3, 5),
    "'우주산업 거점' 고흥 우주발사체 국가산단 조성 속도": ("b11", 1, 1),
}


def request(method: str, path: str, token: str | None = None,
            body: dict | None = None) -> tuple[dict, float]:
    data = None if body is None else json.dumps(body).encode("utf-8")
    headers = {"Accept": "application/json", "User-Agent": "FrameLESS-release-smoke/1"}
    if token:
        headers["Authorization"] = f"Bearer {token}"
    if data:
        headers["Content-Type"] = "application/json; charset=utf-8"
    started = time.perf_counter()
    with urllib.request.urlopen(
        urllib.request.Request(BASE_URL + path, data=data, headers=headers, method=method),
        timeout=30,
    ) as response:
        payload = json.load(response)
    return payload, (time.perf_counter() - started) * 1000


def feed_articles(token: str) -> list[dict]:
    rows: list[dict] = []
    for category in ("POLITICS", "SOCIETY"):
        cursor = None
        while True:
            query = {"category": category, "scope": "ALL", "limit": "20"}
            if cursor:
                query["cursor"] = cursor
            page, _ = request(
                "GET", "/api/v1/articles?" + urllib.parse.urlencode(query), token
            )
            rows.extend(page["items"])
            cursor = page.get("next_cursor")
            if not cursor:
                break
    return rows


def main() -> int:
    identifier = os.environ.get("FRAMELESS_SMOKE_ID")
    password = os.environ.get("FRAMELESS_SMOKE_PASSWORD")
    if not identifier or not password:
        print("Set FRAMELESS_SMOKE_ID and FRAMELESS_SMOKE_PASSWORD.", file=sys.stderr)
        return 2

    login, _ = request(
        "POST", "/api/v1/auth/login",
        body={"identifier": identifier, "password": password},
    )
    token = login["token"]
    articles = feed_articles(token)
    by_title = {row["title"]: row for row in articles}
    elapsed_pairs: list[float] = []
    all_passed = True

    print(f"base_url={BASE_URL}")
    print(f"feed_visible_articles={len(by_title)}")
    print("case\tresult\tobserved\texpected\tdetail_ms\tanalysis_ms")
    for title, (case, expected_findings, expected_pages) in CASES.items():
        row = by_title.get(title)
        if row is None:
            print(f"{case}\tFAIL\tmissing\t{expected_findings}/{expected_pages}\t-\t-")
            all_passed = False
            continue
        detail, detail_ms = request("GET", f"/api/v1/articles/{row['id']}", token)
        analysis, analysis_ms = request(
            "GET", f"/api/v1/articles/{row['id']}/analysis", token
        )
        findings = analysis.get("findings", [])
        pages = sum(len(item.get("comparison_articles", [])) for item in findings)
        passed = (
            analysis.get("state") == "READY"
            and len(findings) == expected_findings
            and pages == expected_pages
            and bool(detail.get("body", "").strip())
        )
        all_passed = all_passed and passed
        elapsed_pairs.append(detail_ms + analysis_ms)
        print(
            f"{case}\t{'PASS' if passed else 'FAIL'}\t{len(findings)}/{pages}\t"
            f"{expected_findings}/{expected_pages}\t{detail_ms:.0f}\t{analysis_ms:.0f}"
        )

    if elapsed_pairs:
        print(f"median_detail_plus_analysis_ms={statistics.median(elapsed_pairs):.0f}")
        print(f"max_detail_plus_analysis_ms={max(elapsed_pairs):.0f}")
    print(f"result={'PASS' if all_passed else 'FAIL'}")
    return 0 if all_passed else 1


if __name__ == "__main__":
    raise SystemExit(main())

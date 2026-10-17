"""Change link or pass a URL to run discovery and FrameLESS rubric analysis."""

import argparse
import json
from pathlib import Path
import sys

from pipeline import complete_rubric_analysis, run_analysis_pipeline, run_discovery_pipeline
from result_output import (format_analysis_result, make_error_record, make_failure_result,
                           load_discovery_result, save_analysis_result, OperationError)


link = "https://www.ksw-news.com/news/articleView.html?idxno=3048396"


def main() -> None:
    for stream in (sys.stdout, sys.stderr):
        if hasattr(stream, "reconfigure"):
            stream.reconfigure(encoding="utf-8")
    parser = argparse.ArgumentParser(description="핵심 주장·관련 기사·FrameLESS I1~I5/A~G 분석")
    parser.add_argument("url", nargs="?", default=link)
    parser.add_argument("--max-claims", type=int, default=5)
    parser.add_argument("--max-candidates", type=int, default=20)
    parser.add_argument("--max-per-claim", type=int, default=3)
    parser.add_argument("--display", type=int, default=10, help="검색어별 검색 결과 수")
    parser.add_argument("--event-baseline-limit", type=int, default=5, help="포함·누락 비교용 사건 기사 수 0~20")
    parser.add_argument("--verification-batch-size", type=int, default=3, help="본문 검증 요청당 기사 수 1~5")
    parser.add_argument("--discovery-only", action="store_true", help="기존 1~4단계만 실행")
    parser.add_argument("--input", type=Path, help="저장한 4단계 결과 JSON으로 5단계만 실행")
    parser.add_argument("--details", action="store_true", help="배경·전망·발언자 근거도 출력")
    parser.add_argument("--json", action="store_true")
    parser.add_argument("--output", type=Path, help="전체 결과를 저장할 JSON 파일")
    parser.add_argument("--refresh", action="store_true", help="저장 결과를 재사용하지 않고 새로 분석")
    parser.add_argument("--no-cache", action="store_true", help="분석 캐시·스냅샷 저장 비활성화")
    parser.add_argument("--cache-dir", type=Path, help="분석 스냅샷 저장 폴더")
    args = parser.parse_args()
    progress = lambda message: print(message, file=sys.stderr)
    try:
        if args.input and args.discovery_only:
            raise OperationError("--input과 --discovery-only는 함께 사용할 수 없습니다.", code="INVALID_OPTIONS")
        if (args.input or args.discovery_only) and (args.refresh or args.no_cache or args.cache_dir):
            raise OperationError("캐시 옵션은 URL로 전체 실행할 때 사용합니다.", code="INVALID_OPTIONS")
        if args.input:
            discovery = load_discovery_result(args.input)
            result = complete_rubric_analysis(discovery, progress=progress)
        else:
            run = run_discovery_pipeline if args.discovery_only else run_analysis_pipeline
            cache_options = {} if args.discovery_only else {
                "use_cache": not args.no_cache, "force_refresh": args.refresh, "cache_dir": args.cache_dir}
            result = run(args.url, max_claims=args.max_claims, max_candidates=args.max_candidates,
                         max_per_claim=args.max_per_claim, display_per_query=args.display,
                         event_baseline_limit=args.event_baseline_limit,
                         verification_batch_size=args.verification_batch_size, progress=progress, **cache_options)
    except Exception as exc:
        result = make_failure_result(make_error_record(exc, stage="input_validation"), url=args.url)
    if args.output:
        result = save_analysis_result(result, args.output)
        if not any(e["stage"] == "output_save" for e in result["errors"]):
            print(f"전체 결과 저장: {args.output}", file=sys.stderr)
    if args.json:
        print(json.dumps(result, ensure_ascii=False, indent=2))
    else:
        print(format_analysis_result(result, show_details=args.details))
    if result.get("execution_status") == "failed":
        raise SystemExit(1)


if __name__ == "__main__":
    main()

# FrameLESS documentation

`docs/`에는 현재 구현을 뒷받침하는 운영 문서와 측정 결과를 둡니다. 제품이
어떻게 동작해야 하는지는 `specs/`가 정의하고, 실제 코드와 테스트는 그 계약을
구현하고 검증합니다.

## Current documents

- [`backend.md`](backend.md): 현재 뉴스 API, 로컬 실행, 배포 경계
- [`testing-documentation.md`](testing-documentation.md): 현재 테스트 전략과
  날짜·리비전이 명시된 결과
- 현재 기능·UI·API 계약은 [`specs/`](../specs/README.md), 실행 가능한 검증과
  남은 공백은 [`specs/testing/`](../specs/testing/README.md)에서 확인합니다.

`testing-documentation.md`의 측정 결과는 해당 행에 적힌 커밋과 환경에만
적용됩니다. 현재 `main`의 결과로 일반화해서 읽지 않습니다.

## Archived documents

파일 경로를 유지하는 다음 자료도 현재 구현 지시가 아닌 과거 기록입니다.

- [`iteration-3-demo-recording-plan.md`](iteration-3-demo-recording-plan.md):
  당시 APK·6기사 데이터셋에 맞춘 촬영 흐름; 현재 UI 촬영 지침으로 사용하지 않습니다.
- [`cluster-analysis-pipeline-lessons.md`](cluster-analysis-pipeline-lessons.md):
  2026-10-31 수동 파일럿의 설계 교훈; 현행 staged 호환 API와 정규화 import는
  각 feature 스펙과 backend 안내를 따릅니다.

`archive/`는 당시 상태를 보존하는 역사 자료입니다. 현재 요구사항이나 운영
상태의 근거로 사용하지 않습니다.

- `archive/iteration-2/`: Iteration 2 통합·제출·테스트와 기존 URL 분석 기록
- `archive/midterm/`: 중간발표 초안과 산출물
- `archive/research/`: 2026-10-31 전후의 수동 분석·UI 파일럿

과거 수치나 계획을 현재 문서로 고쳐 쓰지 않습니다. 현재 동작이 달라졌다면
새 결과를 현재 문서에 추가하고, 보관 자료는 당시 기록으로 유지합니다.

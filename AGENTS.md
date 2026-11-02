# FrameLESS repository guidance

## Sources of truth

Use this order when behavior is unclear:

1. reviewed product and UI specifications in `docs/`;
2. reviewed behavioral test specifications in `specs/testing/`;
3. executable tests and API contracts;
4. implementation details.

Do not infer intended behavior only from the current implementation. If these
sources disagree, stop and surface the conflict instead of silently choosing the
easiest assertion.

## Change workflow

- Add or update a test specification for a non-trivial feature, external
  contract, stateful flow, security rule, or regression-prone bug. Small internal
  refactors do not need their own specification.
- Draft intent, preconditions, postconditions, edge cases, and undefined behavior
  before generating test code. A person reviews the intent; automation then
  implements and runs the tests.
- Derive test oracles from the reviewed specification, not from the output of the
  code under test.
- Cover success, failure, boundary, and state-transition behavior. Add
  equivalence partitions, pairwise cases, properties, or metamorphic checks when
  they reduce risk more effectively than examples alone.
- Every postcondition in a test specification ends with either `Tested by:` and
  an exact test identifier, or `Tip:` describing the missing test.
- A fixed bug receives a regression test in the same pull request unless the
  behavior cannot be automated; document that exception explicitly.

## Test authority

Agents may draft specifications, add fixtures, generate tests, run CI-equivalent
commands, and diagnose failures. Do not weaken an acceptance criterion, delete or
skip a meaningful test, lower a quality threshold, or change a public contract
only to make a test pass. Ask for human confirmation before doing any of those.
Never hide a failing command or bypass a required CI job.

Generated tests are claims about the product, not evidence by themselves. Keep a
test only when its oracle is traceable to reviewed intent and the failure would
describe a real product defect.

## Pull requests and CI

- Keep the product/specification change and its tests in the same pull request.
- Complete the pull-request test checklist and link the relevant specification.
- Do not merge while a required CI job is failing.
- Keep deterministic CI offline: no production credentials, live model calls, or
  live publisher pages. Record live and AI quality evaluation separately.

Canonical local checks from the repository root are:

```sh
FRAMELESS_USE_SQLITE=1 python3.12 manage.py test
cd web && npm test && npm run lint && npm run build
cd android && ./gradlew :app:jacocoDebugUnitTestReport :app:lintDebug :app:assembleDebug
```

Run the smallest relevant command while iterating, then the affected full suite
before handoff. GitHub Actions remains the release gate.

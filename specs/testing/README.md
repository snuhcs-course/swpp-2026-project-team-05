# Behavioral test specifications

This directory turns reviewed product intent into stable test oracles. It sits
between the product specifications in the sibling `product/`, `ui/`, and
`features/` directories and executable tests.

## When a specification is required

Create or update one for behavior that is externally visible, stateful,
security-sensitive, ambiguous, expensive to regress, or shared across clients.
Do not create a document for every helper function or mechanical refactor.

## Lifecycle

1. **Draft** — an author or agent records intended behavior and proposed cases.
2. **Intent-reviewed** — a team member confirms the behavior, boundaries, and
   undefined cases. This is the point at which the text becomes a test oracle.
3. **Implemented** — every postcondition names an executable test or a visible
   `Tip` gap. CI runs the relevant automated checks.
4. **Superseded** — a new reviewed specification replaces the behavior. Keep the
   old file or Git history; do not rewrite prior evaluation results.

Use `TEMPLATE.spec.md` for new behavioral units. Put the feature name in the file
name, for example `article-search.spec.md`.

## Traceability rule

Every postcondition ends in one of these forms:

- `Tested by: package.Class.test_name`
- `Tip: add an integration test that ...`

`Tested by` means the named test asserts that exact oracle. A test that merely
executes the same file is not enough. `Tip` is an honest, reviewable gap and must
not be converted into a passing claim.

## Different documents, different jobs

- `specs/product/`, `specs/ui/`, and `specs/features/` define product and public
  contracts.
- `specs/testing/*.spec.md` defines executable oracles and coverage gaps.
- `docs/testing-documentation.md` records the team strategy and measured results.
- CI logs and coverage artifacts are evidence for one revision, not permanent
  product requirements.

Acceptance criteria and any team-approved editorial review set are versioned
separately from ordinary unit-test refactors. Once formally frozen for an
iteration, cases are added through a dated revision and are never silently
removed or edited.

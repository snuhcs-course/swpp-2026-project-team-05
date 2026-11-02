# <Behavioral unit> test specification

| Field | Value |
| --- | --- |
| Status | Draft / Intent-reviewed / Implemented / Superseded |
| Owner | <name or role> |
| Source requirements | <links to product/UI/API specifications> |
| Last reviewed | YYYY-MM-DD |

## Scope

State the user-visible behavior or contract covered by this specification.

### Non-goals

- State what this specification deliberately does not cover.

## Preconditions

- Describe valid initial state, identity, data, and dependencies.

## Postconditions

### Success

- [Unit / Integration / Acceptance] Observable outcome.\
  Tested by: `package.Class.test_name`

### Errors and boundaries

- [Unit / Integration / Acceptance] Observable error or boundary outcome.\
  Tip: add a test that exercises ...

### State transitions

- [Unit / Integration / Acceptance] State retained, reset, or restored.\
  Tested by: `package.Class.test_name`

## Undefined behavior

- List inputs or states for which the product intentionally makes no guarantee.

## Test design

| Risk or partition | Representative cases | Oracle source |
| --- | --- | --- |
| Happy path | <cases> | <requirement> |
| Boundary | <cases> | <requirement> |
| Error | <cases> | <requirement> |
| State interaction | <cases> | <requirement> |

## Human review checkpoint

- [ ] Product intent and non-goals were reviewed.
- [ ] Assertions come from the specification, not the implementation output.
- [ ] Missing coverage is recorded as `Tip`, not hidden.
- [ ] A contract or acceptance change received explicit team confirmation.

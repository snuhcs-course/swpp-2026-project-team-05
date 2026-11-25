# FrameLESS specifications

This directory contains product contracts and behavioral test oracles. Current operating
guides and measured results live in `docs/`; historical notes, pilots, and
presentations live under `docs/archive/`.

## Structure

```text
specs/
├── product/   Core product, data, and API contracts
├── ui/        Cross-client flows, states, and wireframes
├── features/  Incremental feature contracts
└── testing/   Behavioral test oracles and coverage gaps
```

Use this dependency direction:

```text
product / UI / feature specification
                ↓
       behavioral test specification
                ↓
 executable backend, Web, and Android tests
                ↓
      CI and documentation evidence
```

Product behavior belongs under `specs/`; consolidated implementation history and
results belong in `docs/`. Test specifications retain exact executable references,
coverage gaps, and brief dated verification notes. New substantial features should receive their own file under `features/`
rather than extending an unrelated contract. The existing news feature document
contains the three tightly related, already-implemented discovery slices and may
be split when one of them evolves independently.

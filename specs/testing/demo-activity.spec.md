# Demo activity seed

| Field | Value |
| --- | --- |
| Intent | Populate the existing reaction and recent-read features for a usable demo |
| Authorization | User requested 20 test accounts and synthetic activity in the shared dataset on 2026-11-25 |
| Scope | Explicit management command; no schema, endpoint, scheduler, or app change |

## Preconditions and postconditions

Use isolated fixtures with visible feed articles, readable comparisons, hidden
articles, inactive publishers, and an ordinary account with existing activity.
Freeze time when comparing repeated seed runs.

1. Dry run is read-only. Applying creates exactly 20 ordinary, non-login demo
   accounts and bounded views/reactions on up to 60 discoverable topics. Topics
   without generated activity remain available; article publication times,
   analysis, Saved, follows, and ordinary-account activity stay unchanged.
   Tested by: `tests.test_demo_activity.DemoActivityTests.test_preview_is_read_only_and_apply_preserves_ordinary_data`.
2. The seed has varied topic popularity, all five reaction types, recent and
   expired activity, and same-account participation on multiple articles of a
   topic. Current article counts and existing distinct-topic ranking remain the
   production oracles. Issue's leading demo topic differs from readership's.
   Tested by: `tests.test_demo_activity.DemoActivityTests.test_distribution_uses_existing_topic_oracles_and_invalidation`.
3. Reapplying replaces only this seed's activity, without account or row growth.
   Clear removes only its exact managed account identities and derived viewer
   keys. Clear works without an eligible corpus. Empty apply fails atomically.
   Tested by: `tests.test_demo_activity.DemoActivityTests.test_repeat_and_clear_touch_only_exact_owned_accounts_and_viewers`,
   `tests.test_demo_activity.DemoActivityTests.test_empty_apply_fails_but_clear_remains_available`.
4. Managed UUID, username, email, unusable-password marker, and ordinary role
   must match before an existing account can be reused/deleted. A collision or
   modified identity aborts the whole operation. SQL writes are atomic. Each
   non-login demo account has one deterministic `demo:` viewer key; ordinary
   API viewer keys are hex-only and cannot share that namespace. No bearer
   tokens, production-secret access, or login passwords are needed.
   Tested by: `tests.test_demo_activity.DemoActivityTests.test_reserved_identity_collisions_abort_without_modifying_data`,
   `tests.test_demo_activity.DemoActivityTests.test_failed_write_rolls_back_accounts_and_activity`,
   `tests.test_demo_activity.DemoActivityTests.test_repeat_and_clear_touch_only_exact_owned_accounts_and_viewers`.
5. Activity changes invalidate the derived trends refresh deadline; no ranking
   snapshots or artificial prior movements are fabricated. Ordinary reads use
   the normal ranking service. Time windows continue to expire naturally.
   Tested by: `tests.test_demo_activity.DemoActivityTests.test_distribution_uses_existing_topic_oracles_and_invalidation`.

## Operation

`python manage.py seed_demo_activity` previews the plan. `--apply` replaces the
seed's current data; `--apply --clear` removes it. Supply the target database's
`DATABASE_URL` through the environment.
Do not put credentials in command arguments or repository files.

This is synthetic demo activity, not evidence of actual audience or a performance
benchmark. It is not refreshed in the background. Re-run explicitly when a fresh
demo is needed; recent-read cleanup may remove expired generated views normally.

## Verification — 2026-11-25

All six seed regressions passed on SQLite and isolated PostgreSQL 16.2.
The affected full Django suite ran 474 cases
with seven PostgreSQL-only skips and no failures. The command's read-only
preview and apply also executed against the shared PostgreSQL database.
The applied seed contains 20 demo accounts, 385 view rows and 262 reaction rows
across 60 selected topics; ordinary account count stayed at five. Article,
analysis-record, Saved and follow counts remained 1,277 / 4 / 12 / 7.
Public deployed reads returned five ranked topics, an Issue with 18 distinct
participants, and five-type article reaction counts. Initial rankings use the
ordinary service and real prior snapshot; no movement history was fabricated.

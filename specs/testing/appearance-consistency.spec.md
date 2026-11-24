# Android appearance consistency

## Intent

Keep the portal, authentication, settings, tutorial, Reader and comparisons in
one visual system. Ordinary controls are neutral; analysis colors keep their
meaning and follow the app appearance override.

## Postconditions

1. Light and dark authentication screens use neutral primary actions with at
   least 4.5:1 text/background contrast. Both use the shared brand header.
   Tested by: `AuthAppearanceInstrumentedTest.lightLoginUsesReadableNeutralAction`,
   `AuthAppearanceInstrumentedTest.darkLoginUsesReadableNeutralAction`,
   `AuthAppearanceInstrumentedTest.registrationKeepsNeutralActionsAndInlineErrors`.
2. A submitting form blocks duplicate submits and field edits, shows progress,
   and keeps entered text and errors. Password visibility remains an explicit
   accessible action. Tested by:
   `AuthAppearanceInstrumentedTest.submittingLoginPreservesInputAndDisablesDuplicateActions`.
3. Explicit app dark mode gives readable analysis/paired-expression surfaces
   even while the device is in light mode. Tested by:
   `AuthAppearanceInstrumentedTest.analysisPaletteFollowsTheAppAppearanceOverride`.
4. Home, login, registration and onboarding show the same mark and wordmark;
   comparison cards use neutral surfaces and settings switches retain a visible
   thumb in both modes. Tip: inspect these surfaces on the connected phone;
   preserve keyboard, validation and back-navigation regression coverage.

## Scope

No account/API/schema behavior changes. Publisher logos and financial direction
colors retain their identity. Analysis offsets, selection, scrolling, and
comparison navigation keep their existing behavioral specifications.

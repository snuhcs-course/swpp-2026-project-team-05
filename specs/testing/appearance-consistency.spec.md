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
5. UI affordances use the shared vector assets, preserve their accessible
   names and state, and do not introduce inert info badges or invented trend
   movement. Home trend expansion still reveals five actual headline targets
   and collapse restores two. Tested by:
   `PortalNavigationInstrumentedTest.homeTrendExpansionKeepsHeadlineTargets`.
   Tip: inspect Home, search, Saved, Reader and comparison navigation on the
   connected phone in both appearances; icon geometry is a visual check.
6. The compact root bar preserves its four full-cell tab actions and selection;
   tap targets stay at least 48dp high. Tested by:
   `PortalNavigationInstrumentedTest.rootTabsExposeSelectionAndKeepTheirActions`.
   Tip: inspect the 60dp bar and curated publisher logos at actual
   phone density; verify sharp edges and full, uncropped brand artwork.
   Smaller publisher artwork preserves a publisher action at least 48dp high.
   Tested by: `PortalNavigationInstrumentedTest.publisherIdentityAndHeadlinesKeepTheirDestinations`.
7. Home omits ranking-window/calculation-time captions. Article list bylines
   retain publisher/category without publication times; Reader source dates
   remain available. Tested by:
   `FeedHomeChromeTest.rowMetadataIsCategoryThenPublisherWithoutPublicationTime`,
   `FeedHomeChromeTest.bylineIsPublisherWithoutCategoryOrTime`,
   `PortalNavigationInstrumentedTest.homeTrendExpansionKeepsHeadlineTargets`.
   Tip: inspect publisher-directory and Saved rows as part of phone verification.
8. Numeric market changes select the matching signed label and direction;
   positive is red/up-triangle, negative blue/down-triangle, and zero neutral
   without a triangle in either appearance. Tested by:
   `HomeBriefingTest.marketDirectionFollowsNumericChangeIncludingZero`,
   `HomeBriefingTest.changeLabelPreservesSignAndNormalizesNegativeZero`.
   Tip: inspect actual card colors and triangle SVGs in light/dark screenshots.
9. Clear, cloudy, rain and snow each render their matching SVG and condition
   label; changing supplied weather updates the existing card.
   Tested by: `PortalNavigationInstrumentedTest.briefingCardsFollowWeatherAndNumericMarketDirectionInBothThemes`.
   Tip: inspect its cropped weather/market captures for all four conditions in
   light and dark mode; the normal Home remains a mock clear-weather snapshot.

## Scope

No account/API/schema behavior changes. Publisher logos and financial direction
colors retain their identity. Analysis offsets, selection, scrolling, and
comparison navigation keep their existing behavioral specifications.

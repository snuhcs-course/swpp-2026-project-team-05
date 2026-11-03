# URL analysis mock

| Field | Value |
| --- | --- |
| Status | Mock-only client flow |
| Owner | Article discovery feature owner |
| Related behavior | [`news-app-features.md`](news-app-features.md) §§2–8 |
| Last reviewed | 2026-11-03 |

## 1. Purpose

When an authenticated search returns no articles, FrameLESS offers a natural
next step for a reader who already has a public article link. This iteration
validates that entry point and its interaction design before the ingestion and
analysis pipeline exists.

The mock must never imply that analysis is actually running. It sends no
request, stores no URL, creates no article, and produces no fabricated result.

## 2. Entry condition

Show `Analyze an article link` on Web and `기사 링크로 분석하기` on Android only
when all of the following are true:

- search is active with a valid query of at least two normalized characters;
- the completed search returned zero articles;
- the request is neither loading nor in an error state.

Ordinary empty feeds, Following empty states, short-query guidance, loading,
and errors retain their existing actions and do not expose this mock.

## 3. Interaction

The flow has two local-only states:

1. **URL entry** — explain that only public `http` or `https` article URLs are
   accepted, provide one labeled URL field, a primary continue action, and a
   secondary return action.
2. **Feature preview** — show a continuously animated progress indicator with
   explicit copy that the analysis feature is being prepared and that this URL
   was not uploaded or saved. Provide one clear action that returns to the
   search empty state.

Submitting trims surrounding whitespace and accepts only a syntactically valid
absolute `http` or `https` URL with a host. An invalid value stays in URL entry,
shows an inline error associated with the field, and makes no network request.

Closing or returning from either state clears the entered URL, validation
error, and preview state. Changing or clearing the search also resets the mock.

## 4. Client presentation

- Web keeps the flow inside the existing editorial empty-state panel so it does
  not introduce a fake destination or shareable route.
- Android opens a modal bottom sheet from the empty state so URL entry remains
  usable above the software keyboard.
- The progress indicator is decorative; status text carries the meaning for
  assistive technology.
- The primary and return actions remain visually distinct and use the clients'
  existing button vocabulary.

## 5. Boundaries and future replacement

This mock adds no backend endpoint, database table, queue, polling, background
work, notification, or analytics event. It does not reuse the legacy hidden URL
analysis endpoint.

The URL validation and UI state transition stay client-local. A future reviewed
pipeline may replace the mock transition with an API submission contract, but
must separately define authentication, ingestion safety, deduplication,
copyright handling, lifecycle states, cancellation, and failure recovery.

## 6. Acceptance

- A zero-result search can enter the URL flow on Web and Android.
- Invalid and non-HTTP(S) values are rejected inline.
- A valid URL reaches the clearly labeled feature-preview state.
- No API request or persistent mutation is triggered by the mock.
- Returning leaves the original zero-result search intact and resets the mock.


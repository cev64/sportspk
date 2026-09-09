# ARCHITECTURE.md

Companion to `README.md` (how to build) and `PRODUCT_SPEC.md` (what it does). This file explains
*why* the code is shaped the way it is, for whoever touches it next.

## The two boundaries that matter

### 1. The provider boundary

ESPN is the current data source. It is **unofficial and undocumented** — no SLA, no published rate
limits, and a schema that changes without notice. The architecture treats it as untrusted and
replaceable:

```
EspnApi ──> EspnDtos ──> EspnMapper ──> domain models ──> Room ──> ViewModel ──> Compose
            (nullable)   (total)
            └──────── data layer only ────────┘
```

- `ScoreboardRepository` is an **interface in `domain/`**. A second provider is a second
  implementation; no screen changes.
- Every DTO field is nullable or defaulted, `ignoreUnknownKeys = true`. An added or removed field
  cannot fail a parse.
- `EspnMapper` is **total**: an event it cannot make sense of is dropped, never propagated as a
  half-populated `Game`. One bad row cannot take down a scoreboard.
- Cached standings are stored in **our own JSON format**, built from domain models — so a provider
  change does not invalidate stored data.

This is not hypothetical. `broadcasts[].market` is a string on `/scoreboard` and an object on
`/summary`; typing it as a string broke every summary response. It is now decoded as a raw
`JsonElement` and normalised, with regression tests for both shapes and a third unexpected one.
Expect more of this. When ESPN changes something, the fix belongs in `data/`, and it should come
with a fixture-backed test.

### 2. The read/refresh split

Reads and writes are deliberately asymmetric:

- **Reads** (`observeScoreboard`, `observeGamesForTeams`, …) come from Room and **never fail**.
- **Refreshes** are explicit, take a `force` flag, and return a `RefreshResult`.

So a network failure never empties a screen. It sets a message; the last known scores stay on
screen with a dismissible banner. This is why the UI can be offline-first without any special
offline handling in the screens themselves.

Per-league mutexes in `EspnScoreboardRepository` stop the UI's refresh and the background worker's
refresh from stampeding the same endpoint.

## Adaptive layout

Every layout decision comes from **window size**, never a device model:

- `NavigationSuiteScaffold` picks bottom bar vs navigation rail.
- `ListDetailPaneScaffold` decides how many panes fit and renders the third (standings context)
  only when there is room — in practice, the Fold's inner display.

Note that Material 3 Adaptive's stock `calculatePaneScaffoldDirective` caps at **two** horizontal
partitions, so a `ListDetailPaneScaffold`'s extra pane is never shown with it. `ui/AdaptiveDirective.kt`
widens the directive to three partitions above 960dp; without that the standings-context pane would
be dead code. The threshold logic is unit tested (`AdaptiveDirectiveTest`) precisely so it cannot
silently regress back to invisible.

Fold continuity rests on three things, in order of importance:

1. `MainActivity` declares `configChanges` for size/layout/density, so a fold does **not** recreate
   the Activity. Compose re-lays-out against the new window size.
2. Selection and filters live in `SavedStateHandle` / `rememberSaveable`, so a *genuine* recreation
   (process death, theme change) still restores them.
3. The pane scaffold derives visibility from window size, so one selection renders stacked on the
   cover screen and side-by-side unfolded.

Relying on only (1) would break under process death; relying on only (2) would cause a visible
reset on every fold. Both are needed.

## State

`Repository -> ViewModel -> StateFlow -> Compose`, one direction. Composables never touch the
network or the database.

`ScoreboardViewModel` backs both the My Teams tab and the Scores tab — only its `Feed` differs.
That is deliberate: one code path means the two views cannot disagree about a score.

In-app polling runs **only while a screen is resumed** (`LifecycleResumeEffect`). Background
polling is WorkManager's job. Neither runs speculatively.

## Dependency injection

A hand-written `AppContainer` service locator, not Hilt. The graph is small and entirely
singleton-scoped; a DI framework plus its annotation processor was not worth the build cost for
this. Call sites would not change shape if that were revisited.

## Deliberate omissions

These were considered and rejected. Re-adding one should be a decision, not an accident:

| Not used | Why |
| --- | --- |
| Retrofit / OkHttp | Four GET endpoints. `HttpURLConnection` (OkHttp-backed on Android) plus kotlinx.serialization covers it. |
| An image loader (Coil) | Team logos are colour-coded monograms from ESPN's team colours. Avoids a dependency and a network fetch behind every row; works offline. The URLs are already in the data layer if this changes. |
| `material-icons-extended` | ~30 MB for two icons. Core icons cover everything used. |
| Hilt | See above. |
| Foreground service | WorkManager's 15-minute floor is a real limitation for kickoff alerts, but a persistent notification is a worse trade for a scoreboard. Escalate only if testing proves it necessary. |

## Things that must not be broken

- **`League.id` values** are a persistence contract — Room rows, DataStore, backups, deep links.
  Renaming one orphans stored favorites. `LeagueTest` pins the exact list.
- **`applicationId`** is permanent. Changing it makes Android treat the build as a different app.
- **Destructive Room migration** must never be enabled. `favorite_teams` is user data.
- **`app/schemas/`** must stay committed. Migrations cannot be validated without it.
- **The favorites seed marker** must persist, or an update would re-seed defaults over the user's
  edits.

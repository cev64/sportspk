# PRODUCT_SPEC.md — All-Games Scoreboard ("Sportspk")

This is the product specification for the concept recorded under **Current Application Idea** in
`AGENT_INSTRUCTIONS.md`. That file remains the authority on *how* to build; this file describes
*what* is being built. Where they overlap, `AGENT_INSTRUCTIONS.md` wins.

---

## 1. Purpose

A live scoreboard covering **every game** in NFL, NBA, MLB, NHL, Premier League, NCAA Football,
and NCAA Basketball, with a dedicated **My Teams** section for quick glanceable check-ins on a
user-editable favorites list.

Full league coverage is the core of the app. My Teams is a section *within* it, not the whole app.
Any change that makes favorites-only browsing possible at the expense of complete league boards is
a regression against this spec.

Primary device: **Samsung Galaxy Z Fold 8 Ultra**. Single user, single device, local-first.

---

## 2. Leagues

Complete scoreboards, every team — not filtered to favorites.

| League | `League` id | ESPN path |
| --- | --- | --- |
| NFL | `nfl` | `football/nfl` |
| NBA | `nba` | `basketball/nba` |
| MLB | `mlb` | `baseball/mlb` |
| NHL | `nhl` | `hockey/nhl` |
| Premier League | `epl` | `soccer/eng.1` |
| NCAA Football | `ncaaf` | `football/college-football` |
| NCAA Basketball | `ncaab` | `basketball/mens-college-basketball` |

**The `League` id column is a persistence contract.** Ids appear in Room rows, DataStore values,
JSON backups, and `sportspk://` deep links. Renaming one orphans every stored favorite that points
at it. `LeagueTest.league ids are a stable persisted contract` pins the exact list; if that test
fails, the correct response is a migration, not an edited assertion.

---

## 3. My Teams

Seeded on first run with the following, then **fully user-editable**:

| League | Team | ESPN team id |
| --- | --- | --- |
| NFL | Kansas City Chiefs | 12 |
| NFL | Carolina Panthers | 29 |
| NHL | St. Louis Blues | 19 |
| MLB | St. Louis Cardinals | 24 |
| NBA | Oklahoma City Thunder | 25 |
| NBA | Charlotte Hornets | 30 |
| Premier League | Tottenham Hotspur | 367 |
| NCAA Football | Michigan Wolverines | 130 |
| NCAA Football | Notre Dame Fighting Irish | 87 |
| NCAA Basketball | Creighton Bluejays | 156 |

These are **data, not logic** (`DefaultFavorites.SEEDS` → the `favorite_teams` table). Nothing in
the app branches on a specific team. Rules:

- Seeding runs **once ever**, guarded by a marker row in `refresh_state`, not by "is the table
  empty?". A user who deliberately empties their list stays at zero favorites.
- An APK update never re-seeds and never overwrites edits.
- Editing (add / remove / reorder) lives in Settings → My Teams.

---

## 4. Primary user workflow

1. The app opens to **My Teams** or **Scores** (configurable in Settings; My Teams is the default,
   as the fast cover-screen check-in).
2. **Scores** shows a league selector — `All` plus each of the seven leagues — over today's games
   for the selection, **live games sorted first**, then upcoming, then finals.
3. **My Teams** shows only games involving a favorite, drawn from the same cached data. No separate
   fetch path, so the two views can never disagree.
4. Tapping a game — from a league board or from My Teams — opens **the same detail screen**: score,
   period/inning/quarter, box score, and play-by-play where ESPN publishes it.
5. **Standings** shows a per-league table with favorites highlighted.

---

## 5. Major screens

| Screen | Compact (cover) | Expanded (inner) | Transition (fold/unfold) |
| --- | --- | --- | --- |
| **My Teams** | Single-column feed. Team, opponent, score, status. | List + game detail side by side, plus a standings-context pane. | Selection and scroll retained; detail moves from stacked to beside the list. |
| **Scores** | League chips + single-column feed. League boards are one tap away on cover, **not inner-only**. | Same, in a three-pane list/detail/extra scaffold. | Selected league and game retained. |
| **Game detail** | Full-width pane with a back affordance. | Pane beside the list; a third pane adds standings context when the window is at least 960dp wide. | Stays on the same game, at the same scroll position. |
| **Standings** | League chips + one scrolling table. | Same content with room for more columns; rail instead of bottom bar. | Selected league and scroll retained. |
| **Settings** | One scrolling column of grouped settings. | Same column, wider. Deliberately not multi-pane — a second pane here would be padding, not information. | No state to lose. |
| **About** | Full screen. | Full screen. | No state to lose. |

Navigation itself is adaptive: `NavigationSuiteScaffold` renders a **bottom bar** on the cover
screen and a **navigation rail** on the inner display from one declaration. No device-model checks
anywhere in the app.

---

## 6. Data sources

**Primary: ESPN's public site API.** Free, no key, no account.

| Purpose | Endpoint |
| --- | --- |
| Scoreboard | `site.api.espn.com/apis/site/v2/sports/{sport}/{league}/scoreboard` |
| Game detail | `.../summary?event={id}` |
| Team list | `.../teams?limit=1000` |
| Standings | `site.api.espn.com/apis/v2/sports/{sport}/{league}/standings` |

**These endpoints are unofficial and undocumented.** No SLA, no published rate limits, and the
schema changes without notice. The design assumes that:

- Every DTO field is nullable or defaulted; unknown JSON keys are ignored.
- A single malformed event is dropped, never propagated as a half-populated `Game` and never
  allowed to fail the whole scoreboard.
- ESPN's shape is mapped to our `Game` / `Team` / `League` / `Standings` models **in the data
  layer**. Nothing in `domain/` or `ui/` has ever seen an ESPN field name.
- Access is behind `ScoreboardRepository`. A second provider means a second implementation of that
  interface — no screen changes.

This is not theoretical: `broadcasts[].market` is a **string** on `/scoreboard` and an **object**
on `/summary`. Typing it as a string broke every summary response. It is now decoded as a raw
`JsonElement` and normalised (`EspnBroadcastDto.marketName`), with a regression test for both
shapes plus a third, unexpected one.

**Caching.** Scoreboards and standings are cached in Room. The UI *always* reads from cache and is
refreshed in the background — screens never block on a network call, and a failed refresh shows a
dismissible staleness banner over the last known scores rather than emptying the list.

**Polling** (`RefreshPolicy`, unit-tested):

| Condition | Interval |
| --- | --- |
| A favorite team is playing | 25s |
| A favorite game starts within 15 min | 1 min |
| Something is live, but nothing we follow | 2 min |
| Nothing happening | 15 min |

Cache TTL is 20s when a league has a live game and 5 min otherwise; standings 60 min. Fast polling
runs **only while a scoreboard is on screen and resumed**. Conservative request volume is preferred
over maximum freshness when nothing is happening.

---

## 7. Stored data

| What | Where | Kind | Survives update? |
| --- | --- | --- | --- |
| My Teams list | Room `favorite_teams` | **User data** | Yes — never cleared |
| Theme, dynamic color, alert toggles, start tab, last league | DataStore `settings` | **User data** | Yes — keys are permanent, missing keys fall back to defaults |
| Cached games | Room `games` | Cache | Rebuilt from network; safe to lose |
| Cached standings | Room `standings` (our own JSON, not ESPN's) | Cache | Rebuilt from network |
| Refresh timestamps + the "favorites seeded" marker | Room `refresh_state` | Mixed | Yes — the seed marker must persist or defaults would be re-seeded over the user's edits |
| Already-notified game events | Room `notified_events` | Bookkeeping | Yes; pruned after 14 days |

---

## 8. Android integrations

Used because they earn their place, not for completeness:

- **Local notifications**, two channels, independently controllable.
- **Deep links** — `sportspk://game/{leagueId}/{gameId}`.
- **Home-screen widget** (Glance).
- **WorkManager** background refresh.
- **Storage Access Framework** for backup export/import.
- **Adaptive window APIs** for every layout decision.
- **Dynamic color** where available, with a designed fallback palette.

Deliberately **not** used: location, camera, Bluetooth, NFC, biometrics, foreground services,
Quick Settings tiles, picture-in-picture. None of them make a scoreboard better.

---

## 9. Widgets

Glance widget, resizable:

- **Small** — the single most relevant favorite game (live first, then next up, then most recent
  final).
- **Larger** — the full favorite-teams feed.

The widget reads **the same Room cache the app reads**. It keeps no copy of anything and makes no
network calls of its own; it is refreshed after each background poll, so the widget and the app can
never disagree about a score. Tapping a row deep-links to that game's detail screen.

Widget configuration is not destroyed by app updates. If a future change ever genuinely requires
re-adding the widget, that must be documented in `CHANGELOG.md`.

---

## 10. Notifications

Exactly two events, for favorite teams only:

1. **Game start** — a favorite's game has gone live.
2. **Final score** — a favorite's game has ended, with the score.

No play-by-play notifications. Each event fires **once**, deduplicated through the
`notified_events` table, so repeated background polls cannot re-announce the same kickoff. Tapping
a notification deep-links straight to that game's detail screen.

`POST_NOTIFICATIONS` is requested **contextually**, when the user enables an alert in Settings —
never at launch. If it is denied, the app keeps working; only the alerts are silent.

---

## 11. Background tasks

`ScoreRefreshWorker` (WorkManager, periodic, network-constrained):

1. Refreshes only the leagues that actually contain a favorite team.
2. Compares game states before/after and raises kickoff/final notifications.
3. Updates the widget.
4. Reschedules itself using `RefreshPolicy`.

A failed poll returns success rather than retrying immediately — an immediate retry would hammer an
endpoint with no published rate limit, and cached scores stay readable regardless.

**Known limitation:** WorkManager's minimum periodic interval is 15 minutes, which is coarser than
the ~25s used in-app during a live game. So background alerts can lag a kickoff by up to that
interval. Per the concept, a foreground service is **not** used; it remains the escalation path
only if real-world testing shows the timing is unacceptable. That trade is deliberate and is
recorded here rather than silently accepted.

---

## 12. Fold-specific behavior

- **Cover screen** — single-column feed for the active tab, whether that is a league board or My
  Teams. Large touch targets, one-handed. Full league boards are reachable on cover, not
  inner-only. A tapped game opens as one compact pane.
- **Inner display** — multi-pane where it earns its keep: navigation rail, scoreboard list, game
  detail, and a standings-context pane, so moving between games never loses the list. The third
  (standings) pane requires at least 960dp of width; below that the layout stays at two panes so
  the scoreboard list does not get squeezed narrower than a game row reads comfortably. Material 3
  Adaptive's stock directive caps at two partitions, so this is a deliberately widened directive
  (`rememberScoreboardPaneDirective`) rather than default behaviour.
- **Fold continuity** — at My Teams/League → Game → box score, unfolding lands on **that same
  game's box score**, now with more surrounding panels rather than reset to the feed.

Mechanically this rests on three things:

1. `MainActivity` declares `configChanges` for size/layout/density, so folding does **not** recreate
   the Activity — Compose simply re-lays-out.
2. Selection, filters, and the current league live in `SavedStateHandle` and `rememberSaveable`, so
   even a genuine recreation (process death, theme change) restores them.
3. `ListDetailPaneScaffold`'s navigator decides pane visibility from the *window size*, so the same
   selection renders stacked on cover and side-by-side unfolded.

---

## 13. Backup and migration requirements

**Backup.** Settings → Data → Export / Import, JSON, via the system file picker. Covers the My
Teams list and settings — the things the user actually owns. Cached scores are excluded: they are
reconstructible and would be stale immediately.

Import **merges** rather than replacing. Restoring an old backup must never delete favorites added
since it was taken. `BackupPayload.version` exists so a future format change can be migrated on
import instead of rejected.

Android auto-backup is enabled as an extra layer (`backup_rules.xml`), scoped to the same user
data. Backups are a safety net, **not** a substitute for upgrade-in-place: a normal APK update
preserves everything automatically.

**Migration.** Room schema is exported to `app/schemas/` and committed. Destructive migration is
never enabled — `favorite_teams` is real user data, and a missing migration must fail loudly in
testing rather than silently delete it. Every schema change bumps `SportspkDatabase.VERSION` and
appends to `MIGRATIONS`, which is kept forever so a device on any older version can reach current.
`MigrationTest` asserts that the migration list covers every shipped version.

DataStore keys are permanent. Adding a setting means adding a key with a default, never clearing
the store.

---

## 14. Privacy

- **Everything stays on the device.** No account, no backend, no sync, no analytics, no crash
  reporting, no advertising identifiers.
- The only outbound network traffic is unauthenticated GETs to ESPN's public endpoints. They carry
  no user identifier — ESPN cannot tell which teams are favorites, because filtering happens
  locally after a whole-league scoreboard is fetched.
- No secrets ship in the APK; there are none to ship, because the data source needs no key.
- Permissions are limited to `INTERNET`, `ACCESS_NETWORK_STATE`, and `POST_NOTIFICATIONS` (the last
  requested contextually).
- Exported backups are plain JSON written wherever the user chooses through the system file picker.
  They contain team preferences only.

---

## 15. Explicitly out of scope for v1

- **No betting odds, lines, or expected-value features.** Scores, schedules, and standings only.
- **No fantasy sports integration.**
- **No account sync or backend.** Single-device, local-first.

---

## 16. Milestone 1 status

| # | Requirement | Status |
| --- | --- | --- |
| 1 | Favorites seeded with the listed defaults, editable in Settings | Done |
| 2 | Live My Teams feed from ESPN for NFL, NBA, NHL, MLB (+ EPL, NCAAF, NCAAB) | Done — all seven leagues, ahead of the milestone |
| 3 | Game detail with score and basic box score | Done — plus play-by-play where published |
| 4 | Cover/inner adaptive layouts with fold continuity on feed → detail | Done |
| 5 | GitHub Actions APK build installing over the previous build without data loss | Pipeline done and verified in CI terms; **the on-device half of the Mandatory Pre-Use Validation is still outstanding** — see README, "Before you trust this app with real data" |

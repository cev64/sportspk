# Changelog

All notable changes to Sportspk are recorded here.

## 1.0.0 — Initial project milestone

The first working slice of the All-Games Scoreboard concept, built on the foundation described in
`AGENT_INSTRUCTIONS.md` and specified in `PRODUCT_SPEC.md`.

### Added

**Scoreboards**
- Full league scoreboards for NFL, NBA, MLB, NHL, Premier League, NCAA Football, and NCAA
  Basketball, plus an `All` view aggregating today's games across every league. Live games sort
  first, then upcoming, then finals.
- **My Teams** section drawing from the same cached data as the league boards.
- Game detail screen — score, period/inning/quarter, box score, and play-by-play where the
  league's endpoint publishes it. Reached identically from a league board or from My Teams.
- Per-league standings with favorite teams highlighted.

**My Teams**
- Seeded on first run with Chiefs, Panthers, Blues, Cardinals, Thunder, Hornets, Tottenham,
  Michigan, Notre Dame, and Creighton — stored as ordinary user-editable data, not hardcoded logic.
- Add, remove, and reorder in Settings, with a searchable team picker per league.
- Seeding is guarded by a marker row so it runs once ever: emptying the list keeps it empty, and an
  app update never re-seeds over edits.

**Fold 8 Ultra**
- Adaptive navigation — bottom bar on the cover screen, navigation rail on the inner display, from
  one declaration and no device-model checks.
- Three-pane list/detail/extra scaffold on the inner display: scoreboard list, game detail, and
  standings context for the selected game's league.
- Fold continuity — folding or unfolding keeps the selected game, league, tab, and scroll position.
  The Activity is not recreated on a fold, and state is saved for the cases where recreation does
  happen.

**Data**
- ESPN public site API behind a `ScoreboardRepository` interface, with responses mapped into our
  own domain models in the data layer. No ESPN field name reaches `domain/` or `ui/`.
- Room cache for scoreboards and standings; the UI always reads from cache and never blocks on the
  network. A failed refresh shows a dismissible staleness banner over the last known scores.
- Adaptive polling (`RefreshPolicy`): ~25s while a favorite team is live, 1 min when a favorite
  starts soon, 2 min when something unrelated is live, 15 min when nothing is happening.

**Android integration**
- Glance home-screen widget — the most relevant favorite game at small sizes, the full favorite
  feed at larger ones. Reads the same Room cache as the app; taps deep-link to game detail.
- Game-start and final-score notifications for favorite teams, on independently controllable
  channels, deduplicated so a repeated poll cannot re-announce the same kickoff. No play-by-play
  spam. Tapping deep-links to the game.
- `sportspk://game/{leagueId}/{gameId}` deep links.
- WorkManager background refresh, restricted to leagues containing a favorite team.
- Light / dark / system themes, dynamic color with a designed fallback palette.
- JSON backup export and import through the system file picker; import merges rather than replaces.

**Build and release**
- GitHub Actions: `build.yml` (tests, lint, debug APK artifact) and `release.yml` (signed release
  APK on a `v*` tag, attached to the GitHub Release).
- Persistent release signing from GitHub secrets, reconstructed into the runner's temp directory
  and deleted afterwards. A missing secret fails the workflow rather than producing an APK that
  cannot update an existing install.
- Monotonic `versionCode` derived from the CI run number.
- Room schema export committed under `app/schemas/`, with a migration test harness.
- 34 unit tests, including ESPN mapping against real captured responses.

### Notes

- **Destructive Room migration is not enabled** and must not be. `favorite_teams` is user data.
- **The on-device upgrade test has not been performed** — see README, "Before you trust this app
  with real data". The signing and versionCode chain has been verified with `apksigner`, but
  installing build N+1 over build N on the actual device is still outstanding.
- Background alert timing is bounded by WorkManager's 15-minute periodic floor. A foreground
  service is deliberately not used.

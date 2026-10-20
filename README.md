# Sportspk

A single Android home-screen widget ("My Teams", sized for 3×3) showing today's games and live
scores plus the coming week's schedule for:

Thunder · Blues · Tottenham · Cardinals · Chiefs · Hornets · Notre Dame football · St. Louis CITY SC · Creighton basketball

Teams with no games coming up are hidden. Tap a game to open it on ESPN; tap the header to refresh.

## How it works

- Data comes from ESPN's public site API (`site.api.espn.com/apis/site/v2/sports/...`):
  each team's `/teams/{id}/schedule` (soccer uses `soccer/all/...` so cup and European games are
  included, plus `?fixture=true` for upcoming games), with the league `/scoreboard` overlaid for
  today's games because it has the freshest live scores.
- `RefreshWorker` refreshes every 15 minutes, and every ~2 minutes while a game is live or about
  to start. Results are cached on disk so the widget renders instantly after a reboot.
- To change teams, edit `FAVORITE_TEAMS` in `app/src/main/java/com/personal/sportspk/Teams.kt`.
  The week-long window and the 30-day "next game" fallback are in `GameSelector.kt`.

## Building

`./gradlew assembleDebug` (JDK 17+, Android SDK 35). CI builds a debug APK on every push; the
release workflow builds a signed APK from the permanent release key.

## Versioning

`versionName` is in `app/build.gradle.kts`. `versionCode` must only ever increase: CI passes
`-PversionCode=<1000 + run number>`; local builds use 1 so they never block a later CI build.

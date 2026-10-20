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

## Releases (how to get it on the phone)

`main` is the release branch. Every merge to `main` runs `.github/workflows/release.yml`, which
builds a signed APK and publishes it as the latest GitHub Release. This link always downloads the
newest build; open it on the phone (signed in to GitHub if the repo is private) and install:

https://github.com/cev64/sportspk/releases/latest/download/sportspk.apk

Each release is tagged `v<versionName>-<versionCode>`. `versionCode` is `1000 + run number`, so
every release installs over the previous one. Bump `versionNameValue` in `app/build.gradle.kts`
when you feel like it; it's only the label.

Pull requests run `.github/workflows/build.yml` (tests, lint, debug APK). The debug APK from a PR
installs as a separate app (`com.personal.sportspk.debug`), so it can be tried without touching
the real one.

### Release signing

The release key is the app's permanent update identity: an APK signed with a different key can't
install over the app. It lives only in repository secrets (and your backup), never in the repo:

| Secret | Value |
| --- | --- |
| `ANDROID_KEYSTORE_BASE64` | `base64 -w0 sportspk-release.jks` |
| `ANDROID_KEYSTORE_PASSWORD` | keystore password |
| `ANDROID_KEY_ALIAS` | `sportspk` |
| `ANDROID_KEY_PASSWORD` | key password (same as the keystore password) |

If any secret is missing the release workflow fails rather than publishing an APK that can't
update the app. For a local signed build, put `storeFile`, `storePassword`, `keyAlias` and
`keyPassword` in a git-ignored `keystore.properties` and run `./gradlew assembleRelease`.

## Building locally

`./gradlew assembleDebug` (JDK 17+, Android SDK 35).

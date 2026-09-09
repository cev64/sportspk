# Sportspk — All-Games Scoreboard

A native Android scoreboard covering **every game** in NFL, NBA, MLB, NHL, Premier League, NCAA
Football, and NCAA Basketball, with a **My Teams** section for quick glanceable check-ins.

Built for one person on one phone. See `AGENT_INSTRUCTIONS.md` for the build rules and
`PRODUCT_SPEC.md` for the full specification.

---

## What the app does

- **Scores** — a league selector over today's games for any of the seven leagues, or `All` to
  aggregate across every league. Live games sort first.
- **My Teams** — the same data filtered to a user-editable favorites list, for a fast check-in
  without browsing full league boards.
- **Game detail** — score, period/inning/quarter, box score, and play-by-play where the league's
  endpoint publishes it. Reached identically from a league board or from My Teams.
- **Standings** — a per-league table with favorite teams highlighted.
- **Home-screen widget** — the most relevant favorite game at small sizes, the full favorite feed
  at larger ones.
- **Alerts** — a notification when a favorite's game starts and when it ends. Nothing else.

## Current features

| Area | State |
| --- | --- |
| Seven league scoreboards | Working |
| My Teams feed, seeded and editable | Working |
| Game detail with box score + play-by-play | Working |
| Standings with favorites highlighted | Working |
| Adaptive cover/inner layouts, fold continuity | Working |
| Glance widget with deep links | Working |
| Game-start / final-score notifications | Working |
| JSON backup export/import | Working |
| Light / dark / system themes, dynamic color | Working |
| GitHub Actions debug + signed release pipeline | Working |
| **On-device upgrade validation** | **Not yet done — see below** |

Nothing above is mocked or stubbed. Where a capability depends on what ESPN publishes (play-by-play
is not available for every league), the UI says so rather than showing an empty box.

## Primary target device

**Samsung Galaxy Z Fold 8 Ultra.** Layout decisions are made from adaptive window APIs and window
size classes — the app never checks a device model. The Fold is the device the adaptive layouts are
tuned *for*, not a special case they are branched *on*.

---

## Before you trust this app with real data

The `AGENT_INSTRUCTIONS.md` **Mandatory Pre-Use Validation** has been completed for everything that
can be verified off-device. The build pipeline produces two APKs with the same `applicationId`, the
same signing certificate, and increasing `versionCode` — the three properties Android requires to
treat a new APK as an update. That was verified directly with `apksigner`.

**The on-device half is still outstanding and only you can do it:**

1. Create the permanent release keystore (below) and back it up.
2. Add the four GitHub secrets, tag `v1.0.0`, and install the release APK on the Fold.
3. Add teams to My Teams, change settings, add the widget.
4. Tag `v1.0.1` (or re-run the release workflow) to get a higher `versionCode`.
5. Install the new APK **directly over** the old one.
6. Confirm: no uninstall prompt, the app opens, your teams and settings are intact, the widget
   still works, and the Fold layouts still behave.

Do not start relying on this app for anything you care about until step 6 passes.

---

## Project architecture

```
app/src/main/java/com/personal/sportspk/
    data/
        local/        Room entities, DAOs, database, DataStore, favorites seed
        remote/       ESPN HTTP client, endpoints, DTOs
        mapper/       ESPN -> domain, Room <-> domain
        repository/   Repository implementations, backup
    domain/
        model/        Game, Team, League, Standings, FavoriteTeam
        repository/   Repository interfaces (the provider boundary)
        usecase/      RefreshPolicy
    ui/
        components/   GameRow, TeamBadge, shared states
        navigation/   Destinations, deep-link parsing
        screens/      scoreboard, detail, standings, settings, about
        theme/        Colors, typography, theme
    widget/           Glance widget
    workers/          WorkManager refresh + scheduler
    notifications/    Notification channels and posting
    di/               AppContainer (manual DI)
    util/             Formatting, error text
```

Data flows one way: `Repository -> ViewModel -> StateFlow -> Compose`. Composables never touch the
network or the database.

**Two boundaries matter most:**

1. **The provider boundary.** `ScoreboardRepository` is an interface in `domain/`. ESPN's shape is
   mapped into our own models immediately after decoding, so nothing in `domain/` or `ui/` has ever
   seen an ESPN field name. Swapping providers means writing one new implementation.
2. **The read/refresh split.** Reads always come from the Room cache and never fail. Refreshes are
   explicit and return a result, so a network failure produces a dismissible staleness banner over
   the last known scores instead of an empty screen.

**Dependency injection** is a hand-written `AppContainer` service locator rather than Hilt. The
graph is small and entirely singleton-scoped; a DI framework plus its annotation processor was not
worth the build cost. Call sites would not change shape if that were revisited.

**Team logos** are rendered as colour-coded abbreviation badges from ESPN's team colours, not as
downloaded images. That avoids an image-loading dependency and a network fetch behind every row of
every scoreboard, and it works offline. Adding real logos later means adding an image loader —
the data layer already carries the URLs.

---

## How to build locally

Requires JDK 17 and the Android SDK (compileSdk 35).

```bash
./gradlew assembleDebug        # debug APK
./gradlew testDebugUnitTest    # unit tests
./gradlew lintDebug            # lint
./gradlew connectedDebugAndroidTest   # instrumented tests (needs a device/emulator)
```

The debug build uses the applicationId suffix `.debug`, so it installs **alongside** the real app
instead of colliding with it under an incompatible signing identity.

## How to generate an APK

**Personal release build (the one you install):**

```bash
./gradlew assembleRelease -PversionCode=<n>
# -> app/build/outputs/apk/release/sportspk-v<version>-<n>.apk
```

This requires release signing material (below). Without it the APK is built **unsigned** rather
than silently falling back to the debug key — a debug-signed "release" would be a different signing
identity and could not update the installed app.

## How GitHub Actions works

| Workflow | Trigger | Does |
| --- | --- | --- |
| `build.yml` | every push / PR | unit tests, lint, `assembleDebug`, uploads the debug APK + reports |
| `release.yml` | tag `v*`, or manual | verifies secrets, reconstructs the keystore, tests, lint, `assembleRelease`, verifies the signature, attaches the APK to the GitHub Release |

The debug APK from `build.yml` is **not an upgrade build** — different signing key, different
applicationId. Use the release workflow for anything you keep data in.

## How release signing works

The release signing identity is **permanent**. It is never generated per build, and losing it means
never being able to update the installed app again without uninstalling (and losing its data).

**Create it once:**

```bash
keytool -genkeypair -v \
  -keystore sportspk-release.jks \
  -alias sportspk \
  -keyalg RSA -keysize 4096 -validity 10000 \
  -dname "CN=<your name>, O=Personal, C=US"
```

- **Keystore file:** `sportspk-release.jks`
- **Key alias:** `sportspk`
- **Store it outside this repository**, with at least one separate backup. It is git-ignored, but
  the real protection is not keeping it here at all.

**Required GitHub Actions secrets:**

| Secret | Value |
| --- | --- |
| `ANDROID_KEYSTORE_BASE64` | `base64 -w0 sportspk-release.jks` |
| `ANDROID_KEYSTORE_PASSWORD` | keystore password |
| `ANDROID_KEY_ALIAS` | `sportspk` |
| `ANDROID_KEY_PASSWORD` | key password |

The workflow decodes the keystore into `$RUNNER_TEMP` for the duration of the job and deletes it
afterwards. No secret value is ever echoed. If any secret is missing, the workflow fails loudly
rather than producing an APK that cannot update your install.

**Locally**, put the same values in a git-ignored `keystore.properties` in the repo root:

```properties
storeFile=/absolute/path/to/sportspk-release.jks
storePassword=...
keyAlias=sportspk
keyPassword=...
```

Gradle reads environment variables first, then `keystore.properties`.

**Restoring on a new machine:** copy the keystore back from your backup and recreate
`keystore.properties`. Nothing else is machine-specific. Never generate a new key to "fix" a build.

## How upgrade-in-place installation works

Android treats a new APK as an update to an existing app when three things hold:

1. **Same `applicationId`** — `com.personal.sportspk`, permanent. Not changed for a rename, an
   icon change, or a package reorganisation.
2. **Same signing certificate** — the persistent release keystore above.
3. **Higher `versionCode`** than the installed build.

When all three hold, installing the new APK preserves the Room database (My Teams), DataStore
preferences (theme, alerts, start tab), and widget configuration. No uninstall, no reconfiguration.

If an APK ever refuses to install over the existing app, **do not uninstall it** — that destroys
your data. Check, in order: applicationId mismatch, signing certificate mismatch, versionCode not
higher, debug-vs-release variant mismatch, manifest changes, a failed database migration, a corrupt
download.

## Versioning strategy

- **`versionName`** is edited by hand in `app/build.gradle.kts` for each meaningful release
  (`1.0.0`, `1.1.0`, `1.1.1`), and recorded in `CHANGELOG.md`.
- **`versionCode`** is derived in CI as `1000 + GITHUB_RUN_NUMBER`. The run number only ever
  increases, so version codes are monotonic by construction — there is no way to accidentally
  reuse one. The `1000` base leaves headroom below CI-produced codes.
- **Local builds** fall back to `versionCode = 1`, deliberately low so a hand-built local APK can
  never outrank a CI build and block the next real release from installing.

Release codes are never reduced and never reused.

## Data migration strategy

- Room schemas are exported to `app/schemas/` and **committed**. Do not delete them — they are what
  migration tests validate against.
- **Destructive migration is never enabled.** A missing migration fails loudly in testing rather
  than silently deleting the My Teams list.
- Every schema change: bump `SportspkDatabase.VERSION`, append a `Migration` to `MIGRATIONS`, and
  add a test that inserts representative rows at the old version and asserts they survive. Old
  migrations are kept forever, so `1 -> 2 -> 3 -> 4` all work from any starting point.
- DataStore keys are permanent. New settings get a new key and a default; the store is never
  cleared because one setting changed.

## Backup/export options

Settings → Data → **Export backup** / **Import backup**, as JSON through the system file picker.
Covers the My Teams list and settings. Import **merges** — restoring an old backup never deletes
teams added since it was taken. Cached scores are excluded, as they are reconstructible.

Android auto-backup is also enabled for the same user data. Neither replaces upgrade-in-place; a
normal APK update preserves everything automatically.

## Required permissions

| Permission | Why | If denied |
| --- | --- | --- |
| `INTERNET` | Fetch scores | App shows cached scores only |
| `ACCESS_NETWORK_STATE` | Constrain background work to when there is a connection | — |
| `POST_NOTIFICATIONS` | Game-start and final-score alerts. Requested **contextually**, when you enable an alert in Settings — never at launch. | Everything works; alerts stay silent |

No location, camera, Bluetooth, NFC, biometrics, or foreground services.

## External APIs

ESPN's public site API — `site.api.espn.com`. Free, no key, no account, so **no secrets ship in the
APK**.

These endpoints are **unofficial and undocumented**: no SLA, no published rate limits, and the
schema can change without notice. The app is written for that — every response field is optional, a
malformed game is dropped rather than crashing a scoreboard, and cached scores stay on screen when
a fetch fails. Polling is deliberately conservative (see `RefreshPolicy`): fast only while a
favorite team is actually playing, backing off to 15 minutes when nothing is happening.

## Known limitations

- **Background alert timing.** WorkManager's floor is a 15-minute periodic interval, so a
  background "game started" alert can lag the actual kickoff. A foreground service would fix this
  and is deliberately not used. Alerts are prompt while the app is open.
- **The data source can change or disappear without warning.** Nothing about it is guaranteed.
- **Play-by-play is not available for every league**; the detail screen says so rather than showing
  an empty section.
- **Team logos are colour-coded monograms**, not ESPN's logo images.
- **"Today's games" means what ESPN's scoreboard returns** — there is no date picker yet.
- **On-device upgrade validation has not been performed.** See the section near the top.

## Planned features

- Date navigation on league scoreboards (yesterday / tomorrow).
- Per-team alert overrides, so one team can be noisier than the rest.
- Drag-to-reorder for My Teams (currently arrow buttons).
- Real team logos, if an image loader earns its place.
- A conference/division filter for the NCAA boards, which are large.

# android-koreader-companion

An Android companion app for [KOReader](https://github.com/koreader/koreader), and optionally
[Mihon](https://mihon.app/) (manga reader). It runs on the same device as these apps and reads
their own on-disk data (settings, reading history, reading statistics) to show what you're reading
and how far along you are — in-app and via home-screen widgets — without touching either app.

Built for a Boox Palma 2 (e-ink, Android 13) and a Pixel 9 Pro. Distributed via F-Droid, so it's
fully open source and never asks for All Files Access.

## Status

**Implemented and passing `./gradlew test assembleDebug`.** On-device QA (real hardware, real
KOReader and Mihon data) has been run throughout development on a Boox Palma 2.

What works today:
- **Home** screen: the book you're currently reading in KOReader (cover, title, author, progress
  bar, percent read, estimated time remaining), and — if Mihon is granted — an independent card
  for the manga you're currently reading there too. The two are never merged; tapping either opens
  it in its own app.
- **Stats** screen: an All/KOReader/Mihon source filter, a condensed daily-stats row, a
  Week/Month/Year/All-Time table (reading time and pages broken into separate columns), a
  reading-activity heatmap that can shade by pages read or time read, and Day/Month/Year bar
  graphs for both reading time and pages read.
- **Config** screen: re-grant or revoke any of the three storage folders (KOReader, Books, Mihon)
  at any time, not just when access is broken.
- **Five home-screen widgets** — Currently Reading, Reading Stats, Reading Activity, "KOReader -
  Reading Overview" (KOReader only), and "All - Reading Overview" (KOReader + Mihon together) — all
  refreshed automatically in the background, with a tap-to-refresh option.
- First-launch onboarding to grant storage access, with a re-grant prompt if access is ever lost.

Not built yet: a theme picker (still one fixed black & white e-ink theme by design).

## How it works

No network access beyond one optional fallback (a Mihon manga's cover art, only when no local copy
is available), and no modification of KOReader or Mihon. Everything else is read directly from
their own data directories via the Storage Access Framework:

| Source | What it gives us |
| --- | --- |
| `settings.reader.lua` (`lastfile` key) | The absolute path of the last-opened KOReader book |
| `history.lua` | Fallback for the above — most-recently-opened entry |
| `settings/statistics.sqlite3` (`book` table) | Title, authors, page count, total read time/pages, ordered by `last_open` |
| `settings/statistics.sqlite3` (`page_stat_data` table) | Furthest page reached, plus per-day reading stats for the heatmap/graphs |
| The book's own `.epub` file | Cover image and title, extracted directly from the zip/OPF, used to confirm the stats row and epub actually match before showing a cover |
| `mihon/autobackup/*.tachibk` | Manga titles, chapters, and per-chapter read history — the most recently read manga+chapter, and daily reading stats |
| `mihon/downloads/**/*.cbz` | The first page image of the current (or latest downloaded) chapter, used as manga cover art |

`.lua` files are parsed with [LuaJ](https://sourceforge.net/projects/luaj/) (sandboxed — `io`/`os`/
`package`/`luajava` stripped — and timeout-guarded), executing the `return {...}` chunk directly
rather than hand-rolling a parser for KOReader's Lua serialization format. Mihon's `.tachibk`
backups are gzipped protobuf, decoded directly.

Because `SQLiteDatabase` can't open a `content://` SAF URI directly, `statistics.sqlite3` is copied
into the app's private cache before each query.

### Storage access

No `MANAGE_EXTERNAL_STORAGE` — that's off the table for F-Droid distribution. On first launch, the
app asks you to pick a folder via the system folder picker. **Pick your device's root "Internal
Storage" volume, not just the `koreader` folder** — your book library usually lives in a sibling
folder (e.g. `Books/`) that needs to be reachable under the same grant. The optional Mihon grant
works the same way: pick the `mihon/` folder itself, not `mihon/autobackup/` — it covers both
`autobackup/` (`.tachibk` reading data) and the sibling `downloads/` folder (`.cbz` chapters, used
for manga cover art). If access is ever lost (folder renamed/moved, grant revoked), the app shows
the same picker again inline — or you can proactively re-grant or revoke any of the three at any
time from the **Config** screen. Revoking is blocked if it would leave both KOReader and Mihon
ungranted at once, since the app needs at least one reading-data source.

## Architecture

Plain Android Clean Architecture, six Gradle modules:

```
app/            entry point, Hilt wiring, manifest, widget receivers + WorkManager worker
core/           Try<T>/AppError result type, DispatcherProvider — pure Kotlin, no Android deps
domain/         models, repository interfaces, UseCases — pure Kotlin, no Android deps
data/           repository implementations: SAF, Lua parsing, SQLite, EPUB extraction, Mihon backup
presentation/   ViewModels, Compose screens, onboarding, Glance widget content
design-system/  EinkTheme, colors, typography, shared Compose components
```

Dependency direction is enforced by module type, not just convention:
`app → presentation, domain, data, core` · `presentation → domain, design-system, core` ·
`data → domain, core` · `domain → core only` (`core` and `domain` are plain
`org.jetbrains.kotlin.jvm` modules — no Android Gradle plugin at all, so a stray `androidx.*`
import there is a build error, not a lint warning).

Patterns used throughout: `Try<T>`/`AppError` for error handling instead of exceptions crossing
layer boundaries, `operator fun invoke` UseCases, Hilt for DI (domain's UseCases are plain classes
with no Hilt annotations — `data/di/UseCaseModule.kt` bridges their construction, since `domain`
must stay framework-free).

<details>
<summary>Package layout inside each module</summary>

```
core/coroutines/          DispatcherProvider
core/error/                AppError
core/result/                Try<T>

domain/model/              Book, CurrentBook, ReadingProgress, ReadingStatsSummary, DailyReadingStat,
                            StatsBucket, StatsGranularity, StatsSourceFilter
domain/error/               KoreaderError
domain/repository/          CurrentBookRepository, StorageAccessRepository, ReadingStatsRepository,
                            StatsFilterRepository, StorageTarget (enum: KOREADER/BOOKS/MIHON)
domain/matching/            BookTitleMatcher (epub ↔ stats title matching)
domain/reading/              ReadingTimeEstimator
domain/usecase/              GetCurrentBookUseCase, GetMihonCurrentBookUseCase,
                            GetMergedDailyReadingStatsUseCase, BucketDailyReadingStatsUseCase,
                            Grant/Revoke/HasStorageAccessUseCase, Get/SetStatsSourceFilterUseCase

data/local/saf/             PathSegmentResolver, DocumentTreeResolver, KoreaderFileResolver,
                            StorageAccessLocalDataSource
data/local/lua/              LuaChunkLoader, ReaderSettingsDataSource, HistoryDataSource
data/local/db/                StatisticsDatabaseCopier, StatisticsSqliteDataSource
data/local/epub/              EpubOpfLocator, EpubCoverExtractor, EpubCoverDataSource
data/local/mihon/             MihonBackupDataSource, MihonCbzCoverDataSource, MihonCoverDataSource
data/repository/              CurrentBookRepositoryImpl, MihonCurrentBookRepositoryImpl,
                            StorageAccessRepositoryImpl, ReadingStatsRepositoryImpl
data/di/                       DataModule, DataSourceModule, UseCaseModule

design-system/theme/           EinkColors, EinkTypography, EinkTheme
design-system/components/       ReadingProgressBar, BookCoverImage, FilterChipRow

presentation/currentlyreading/  CurrentlyReadingViewModel, CurrentlyReadingScreen, StatsRowSection,
                                 ReadingActivitySection
presentation/stats/              StatsViewModel, StatsScreen, BarChart
presentation/config/             ConfigViewModel, ConfigScreen
presentation/onboarding/         OnboardingViewModel, OnboardingScreen, GrantAccessPrompt
presentation/navigation/          AppNavHost, BottomNav
presentation/widget/               Widget content composables, heatmap renderer + bucketing

app/                          KoreaderCompanionApplication, MainActivity, AppStartViewModel
app/widget/                    5 GlanceWidget/WidgetReceiver pairs (Currently Reading, Stats,
                                Reading Activity, Combined, All Sources), WidgetRefreshWorker,
                                WidgetUpdateScheduler
```
</details>

## Building

Requires JDK 17 and the Android SDK (platform 35, build-tools). On macOS via Homebrew:

```
brew install openjdk@17
brew install --cask android-commandlinetools
export JAVA_HOME=/usr/local/opt/openjdk@17
export ANDROID_HOME=/usr/local/share/android-commandlinetools
sdkmanager --licenses
sdkmanager --install "platform-tools" "platforms;android-35" "build-tools;35.0.0"
echo "sdk.dir=$ANDROID_HOME" > local.properties
```

Then:

```
./gradlew assembleDebug   # builds app/build/outputs/apk/debug/app-debug.apk
./gradlew test            # unit tests across all 6 modules
```

`core` and `domain` are pure-JVM modules and build/test without the Android SDK at all; everything
else needs it.

### Toolchain notes

- **AGP 9's built-in Kotlin support** means no `org.jetbrains.kotlin.android` plugin in any Android
  module's `build.gradle.kts` — applying it is now an error, not just redundant.
- **Robolectric on `compileSdk 36` needs Java 21.** This project targets `compileSdk 35` /
  `targetSdk 35` specifically to stay on Java 17 (still well above `minSdk 33`); bump only alongside
  a JDK upgrade.
- **Hilt 2.60.1** is required — earlier Hilt versions' Gradle plugin doesn't support AGP 9 at all
  (`Android BaseExtension not found`), and AGP 9 fully removed the `android.enableLegacyVariantApi`
  escape hatch some older guides mention.
- Module dependencies use type-safe project accessors (`projects.domain`, not `project(":domain")`)
  — the latter is deprecated under Gradle 9/10.

## Distribution

Package ID `io.github.woxakv.koreadercompanion`. F-Droid-friendly by construction: no
`MANAGE_EXTERNAL_STORAGE`, no analytics/telemetry/crash-reporting dependencies (verified via
`./gradlew :app:dependencies --configuration debugRuntimeClasspath | grep -iE "firebase|analytics|crashlytics"`
returning nothing), only FOSS dependencies (LuaJ is MIT-licensed).

## Known limitations

- **Time-remaining is an estimate for KOReader books**, not exact parity with KOReader's own
  display — it's derived from `total_read_time / total_read_pages`, which is close to but not
  identical to whatever averaging window KOReader's own UI uses internally. Mihon has no
  time-remaining estimate at all: it isn't computable without per-chapter page/duration totals.
- **KOReader cover pairing is fail-closed**: the epub's title and the stats DB's title must match
  exactly after normalization, or the book shows with no cover rather than risking a wrong pairing.
  Mihon cover art is best-effort instead — it matches the manga's on-disk download folder by exact
  name first, then a normalized-name fallback, and falls back to a remote thumbnail if no local
  `.cbz` is found.
- **Mihon progress is chapter-level, not page-level** (current chapter out of total chapters, not
  pages within a chapter), and Mihon books show no author — neither is present in Mihon's trimmed
  backup schema.
- **Primary storage only** — `PathSegmentResolver` handles the `primary:` SAF authority; SD cards /
  secondary volumes aren't resolved.

## Reference data

A real KOReader data folder used to ground this implementation lives at `~/Desktop/koreader/`
(outside this repo, and never committed — it's personal reading history). Test fixtures throughout
the codebase are small, hand-built synthetic files that match KOReader's and Mihon's file *formats*
only, never real book, manga, or reading data.

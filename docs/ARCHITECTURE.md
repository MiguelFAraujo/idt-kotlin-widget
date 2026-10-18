# LabPulse architecture

Baseline: `8a65b1083bd5261adc0ff03b558e2d6003f6e690` (2026-09-18). Reviewed 2026-10-17.

## Purpose and consumers

LabPulse is an Android service-monitoring application and home-screen widget. It checks the host configured by the user, displays current service results and historical statistics, and schedules background checks. This is a native Android application, not a server deployment. See `README.md` and `app/src/main/AndroidManifest.xml`.

The consumer is the Android UI/widget. JVM tests exercise rules and repositories with fakes; they do not prove device permissions, lifecycle, notification delivery, networking from the phone, or widget rendering. Those require an identified device/emulator. No production server, APK release or notification to a real user is part of this branch.

## Composition and boundaries

Paths below are relative to `app/src/main/java/com/idt/widget/`.

| Responsibility | Existing implementation |
|---|---|
| Application lifecycle | `IDTApplication.kt` |
| Activity/navigation | `MainActivity.kt`, `ui/` |
| Composition | `di/AppContainer.kt:10` creates configuration, history and service repositories using application context |
| Service contracts | `domain/repository/ServiceRepository.kt`, `domain/repository/HistoryRepository.kt` |
| Service configuration/persistence | `data/local/ConfigDataSource.kt`, `data/ServiceRepositoryImpl.kt:31` |
| HTTP checks and discovery | `data/remote/ServiceChecker.kt`, `DiagnosticsTool.kt`, `EndpointPolicy.kt`, `ServiceCatalog.kt` |
| History calculation/storage | `data/history/HistoryStore.kt`, `PersistentHistoryRepository.kt:16` |
| Dashboard presentation | `ui/dashboard/DashboardViewModel.kt:31` |
| Port-scan presentation | `ui/scan/ScanViewModel.kt:27` |
| Widget | `widget/StatusWidgetProvider.kt` and associated worker code |
| Update metadata | `update/` and `data/remote/UpdateChecker.kt`; release workflow is a separate writer |

`AppContainer` is the composition boundary. Android context and SharedPreferences belong to adapters. ViewModels depend on repository contracts, although several shared models still live under `data/model`; the domain is therefore not fully independent of the data package.

## Main flows

1. Application composition creates configuration and repository adapters.
2. The dashboard requests enabled endpoints from `ServiceRepository` (`DashboardViewModel.kt:57`).
3. Checks run concurrently inside a global timeout; the repository creates a configured HTTP checker (`ServiceRepositoryImpl.kt:66`). The caller's configured host is authoritative; no fallback host is invented (`EndpointPolicy.kt` and `ServiceRepositoryImpl.kt:42`).
4. Successful results are recorded through `HistoryRepository`; `HistoryStore` computes the rolling window and statistics. `PersistentHistoryRepository` stores the compact JSON in SharedPreferences.
5. The ViewModel publishes counts, cards, latency and errors through a StateFlow. Timeout and other failures stop the loading indicator (`DashboardViewModel.kt:80`).
6. A scan uses an injected `PortScanner` and selected ports, then adds nonduplicate endpoints (`ScanViewModel.kt:27,122`). Do not start full network scans during architecture smoke tests.

## Data and secrets

`PersistentHistoryRepository` uses `idt_history/history_json`; the history format is a map from endpoint IDs to `[timestamp, online-as-0-or-1, latency]` arrays. Keep this format unchanged in the current slice. The service adapter stores configured endpoints in `idt_endpoints/endpoints_json`. Endpoint credentials may be present in those private preferences; do not export them to logs, fixtures or reports.

Signing configuration reads local `keystore.properties` when present (`app/build.gradle.kts:9`). Signing files and SDK configuration are not copied into this worktree. Debug builds use the existing SDK through the environment. No release/signing task is authorized by this branch.

## Selected slice

At baseline, `domain/repository/HistoryRepository.kt:3` imports `data/history/EndpointStats`, so a domain contract names a persistence-package type. Move the two pure history value models to `domain/model`, make that contract refer to its domain type, and retain Kotlin source compatibility for existing callers through aliases at the old package. Keep the calculator, JSON, repository methods, UI behavior, dependencies and Gradle configuration unchanged.

This removes one dependency edge. `ServiceCheckResult` and other service models remain under `data/model`; moving those is a separate proposal, not an implied result of this slice. Kotlin aliases preserve source imports, not precompiled JVM binary class names; the app must be rebuilt as one unit. No independent Java or binary consumer was identified in the tracked project.

Validation: existing history tests before/after; a fixed legacy JSON fixture to protect persistence compatibility; full existing debug JVM tests; debug APK build; emulator smoke if the capacity gate permits. Device and release outcomes remain separately reported.

## Build, failure handling and rollback

Existing commands: `./gradlew testDebugUnitTest --no-daemon`, `./gradlew assembleDebug --no-daemon`; instrumentation uses the project's existing Android test tasks. Gates are measured before each load; builds are serialized with at most two workers and no persistent daemon. No dependency upgrade, release workflow, push to main, deployment or database migration is included.

Rollback is a revert of this branch's commits, followed by rebuilding the APK. No history migration is necessary because the JSON is unchanged. Preserve the original checkout and any user work. Baseline and validation receipts live outside the repository in the lab audit package with mission-specific IDs and timestamps.

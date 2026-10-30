# Fitness Companion (Android) - implementation plan

Source of requirements: `claude.md/CLAUDE.md`.

## Outcome

Native Android app (Kotlin, Jetpack Compose, Material 3) that tracks daily steps in a foreground
service, counts push-up / sit-up reps from hardware sensors, plays video workout guides with a
rest timer, and syncs steps and workouts to Google Health Connect.

## Constraints

- Stack fixed by the requirements: MVVM + Clean Architecture, Hilt, Coroutines/Flow, Room,
  DataStore, Media3, SensorManager, Health Connect.
- Toolchain taken from the installed Android Studio: AGP 9.3.3, Gradle 9.5.0, compileSdk 37,
  Kotlin 2.4.20 (built-in Kotlin support of AGP 9). minSdk 26 (Health Connect), targetSdk 36.
- Single `app` module; layers are packages (`domain`, `data`, `presentation`, `service`, `di`).

## Non-goals

- No backend, accounts or cloud storage.
- No bundled video files: guide clips are streamed.
- No boot receiver: tracking resumes when the app is opened after a reboot.

## Decisions made where the requirements were silent

| Topic | Decision |
|---|---|
| Charts | Compose Canvas (no MPAndroidChart / JitPack dependency) |
| Guide videos | Six freely licensed clips streamed from Wikimedia Commons, attribution shown in the app |
| Video previews | Muted looping player inside the list card, one at a time |
| `TYPE_ACCELEROMETER` | Second counting mode, also the fallback when there is no proximity sensor |
| Camera + ML Kit (optional) | Implemented as the "Camera AI" counting mode |
| Missing sensors | Unsupported modes are hidden, a notice explains why, manual tap counting always works |
| `WRITE_EXERCISE_SESSION` | Real permission name is `android.permission.health.WRITE_EXERCISE` |
| `BODY_SENSORS` | Declared up to API 35, requested as optional; Android 16 replaced it |
| Calories | MET x weight x hours (walking 3.5 MET at 4.8 km/h; stride = 0.415 x height) |

## Phases

| # | Phase | Key files |
|---|---|---|
| 1 | Gradle project, manifest, resources | `settings.gradle.kts`, `gradle/libs.versions.toml`, `app/build.gradle.kts`, `AndroidManifest.xml` |
| 2 | Domain: models, calculators, rep state machines, use cases | `domain/**` |
| 3 | Data: Room, DataStore, sensors, feedback, Health Connect | `data/**`, `di/AppModule.kt` |
| 4 | Step tracking foreground service | `service/StepCounterService.kt` |
| 5 | UI: Home, Counter, Guides, Player, History, Profile | `presentation/**` |
| 6 | Unit tests for the pure logic | `app/src/test/**` |
| 7 | Build, test, launch smoke test | - |

## Acceptance criteria

- `gradlew :app:assembleDebug` succeeds.
- `gradlew :app:testDebugUnitTest` passes.
- The app launches on the emulator and every screen opens without a crash.
- Required deliverables exist: `StepCounterService.kt`, `ProximityRepCounter.kt`,
  `HealthConnectManager.kt`, Compose screens for Home, Counter and Workout Player.

## Status: implemented, verified on 2026-10-04

| Check | Result |
|---|---|
| `:app:assembleDebug` | passes |
| `:app:testDebugUnitTest` | 26 tests pass |
| `:app:lintDebug` | 0 errors, 3 warnings (targetSdk 36 < 37, newer Gradle and AGP exist; all deliberate) |
| Launch on emulator `Medium_Phone` (API 37.2) | all six screens open, no crash |
| Proximity counter | 3 simulated near/far cycles counted as 3 reps, session saved, shown in History |
| Lifecycle | counting pauses when the app is minimised; state survives rotation |
| Video guide | list preview and player stream and play; rest timer counts down |
| Health Connect | permission screen opens; one workout session written; today's steps read back |
| Camera mode | preview and ML Kit pose detector start without a crash |

## Changes on 2026-10-05

- Interface text is Vietnamese only (user decision); numbers and dates use the `vi-VN` locale.
- Fixed tab navigation: after a home quick-start button, the Home tab reopened the Counter
  screen. The chosen exercise is now passed to the counter as state, not as a route argument.
- Video player no longer shows previous/next buttons (a guide is a single clip).
- Step sensor batching reduced from 5 s to 1 s so the count on screen follows more closely.
- Every button was driven on the emulator through `adb` + `uiautomator`; build, 26 unit tests
  and lint (0 errors) rerun on the final code.
- Submission context: demo on a real Android phone, no written report, graded against the
  brief in `claude.md/CLAUDE.md` only.

## Not verified

The emulator has no step-counter sensor, so these ran only as compiled code plus unit tests of
their pure logic:

- `StepCounterService` running in the foreground and storing real sensor readings.
- Writing step records to Health Connect (only the workout write was exercised).
- Accuracy of accelerometer and camera rep counting, and whether their thresholds suit a real
  body. These need a physical phone.

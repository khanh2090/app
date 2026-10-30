# TASK INSTRUCTION: BUILD AN ANDROID FITNESS & WORKOUT COMPANION APP

## 1. OVERVIEW
You are an expert Senior Android Engineer. Build a native Android application named **Fitness Companion** using **Kotlin** and **Jetpack Compose**.
The app helps users track daily steps, count workout repetitions (push-ups/sit-ups) automatically using hardware sensors, play video workout guides, and sync fitness data with Google Health Connect.

## 2. TECHNICAL STACK & ARCHITECTURE
- **Language:** Kotlin 1.9+
- **UI Framework:** Jetpack Compose (Material 3)
- **Architecture:** MVVM + Clean Architecture (Data, Domain, Presentation layers)
- **Dependency Injection:** Hilt
- **Async & Reactive:** Coroutines + Kotlin Flow
- **Local Storage:** Room DB (workout history, step logs) & DataStore (user specs/goals)
- **Media Player:** Jetpack Media3 (ExoPlayer)
- **Sensors:** `SensorManager` (`TYPE_STEP_COUNTER`, `TYPE_PROXIMITY`, `TYPE_ACCELEROMETER`)
- **Health Sync:** Health Connect API (`androidx.health.connect:connect-client`)
- **Camera & AI (Optional/Bonus):** CameraX + Google ML Kit Pose Detection

---

## 3. CORE FEATURES & IMPLEMENTATION REQUIREMENTS

### Feature 1: Daily Step Tracker (Foreground Service)
- Use `SensorManager.SENSOR_TYPE_STEP_COUNTER` for energy-efficient tracking.
- Run inside a **Foreground Service** with a persistent notification to ensure continuous step counting in the background.
- Calculate distance (meters) and burned calories dynamically based on user height, weight, and step count.
- Store daily historical data in Room DB.

### Feature 2: Sensor-Based Rep Counter (Push-ups & Sit-ups)
- **Proximity Sensor Mode:**
  - Place phone under chest for push-ups or near knees for sit-ups.
  - Implement a State Machine (`FAR` -> `NEAR` -> `FAR` = 1 valid rep).
  - Add debouncing logic (min 500ms delay between reps) to eliminate duplicate counts.
- **Audio/Haptic Feedback:** Play a click/beep sound and trigger vibration on each completed rep.

### Feature 3: Video Workout Guides
- List exercises with difficulty levels, muscle targets, and video previews.
- Integrate ExoPlayer in Compose (`AndroidView` / `PlayerView`) for smooth video playback.
- Include a rest timer between sets with audio countdown cues.

### Feature 4: Health Connect Integration
- Check and request Health Connect permissions at runtime (`READ_STEPS`, `WRITE_STEPS`, `WRITE_EXERCISE_SESSION`).
- Sync locally tracked steps and workout sessions to Google Health Connect.

---

## 4. UI/UX REQUIREMENTS
- **Theme:** Dynamic Material 3 (Dark/Light mode support).
- **Home Screen:** Daily progress rings (steps, calories, active time), quick-action workout buttons.
- **Workout/Counter Screen:** Large, bold rep count display, sensor signal status indicator, Start/Pause/Stop controls.
- **History Screen:** Weekly and monthly step/workout activity charts using MPAndroidChart or native Compose Canvas drawing.

---

## 5. PERMISSIONS & EDGE CASES
- Gracefully handle required permissions: `ACTIVITY_RECOGNITION`, `BODY_SENSORS`, `CAMERA`, `POST_NOTIFICATIONS`, Health Connect permissions.
- Fallback UI handling for devices lacking hardware sensors (e.g., proximity or step counter).
- Screen orientation changes and lifecycle awareness (pause timers/sensors when app is minimized).

---

## 6. DELIVERABLES
Please execute step-by-step:
1. Show the recommended Android project folder structure.
2. Provide `build.gradle.kts` dependencies configuration.
3. Write clean, production-ready Kotlin code for:
   - `StepCounterService.kt` (Foreground Service)
   - `ProximityRepCounter.kt` (Sensor State Machine logic)
   - Health Connect Manager module
   - Jetpack Compose UI screens (Home, Counter, Workout Player)
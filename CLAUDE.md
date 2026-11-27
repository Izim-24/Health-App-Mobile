# CLAUDE.md

This file provides guidance to Claude Code (claude.ai/code) when working with code in this repository.

## Project Overview

AI Health & Habit Tracker — an offline-first Android app (Vietnamese + English UI) for health tracking, habits,
nutrition with AI food-photo estimates, medication reminders and Health Connect import. A small Node
backend in `server/` holds the AI key and calls Gemini (free tier) or OpenAI.

## Build & Test Commands

```bash
./gradlew :app:assembleDebug            # debug APK
./gradlew :app:installDemo              # demo build: R8-optimised (~4x faster start) + local AI backend, for presenting
./gradlew :app:testDebugUnitTest        # JVM tests: domain, mappers, ViewModels
./gradlew :app:lintDebug                # Android Lint (0 errors expected)
./gradlew :app:connectedDebugAndroidTest  # on emulator: Room migration, DAO regression, Compose UI
cd server && npm test                   # backend tests (no real key needed)
cd server && npm start                  # backend with GEMINI_API_KEY / OPENAI_API_KEY from server/.env
cd server && npm run mock               # backend with clearly labelled sample responses (no key)
```

`gradle.properties` sets `android.injected.androidTest.leaveApksInstalledAfterRun=true` so instrumented
tests don't uninstall the app (which would wipe emulator data). Keep it.

## Architecture (Clean Architecture + MVVM)

```
app/src/main/java/com/healthapp/
├── domain/        Pure Kotlin: models, repository interfaces, use cases (HealthScore, ScoreHistory, FoodScan,
│                  HealthCalculations, MedicationSchedule, ReminderSchedule, WaterPace, FoodSearch, VitalAlerts, WatchSimulation). No Android imports.
├── data/          Room entities/DAOs, repository implementations, Retrofit APIs, WorkManager workers,
│                  Health Connect import (HealthConnectManager implements HealthSyncRepository)
├── presentation/  Compose screens + ViewModels. Depends on domain only — never import com.healthapp.data.
└── di/            Hilt modules binding domain interfaces to data implementations
```

Rules:
- ViewModels take domain interfaces (`AccountSession`, `ProfileRepository`, `MedicationRepository`, …),
  never DAOs or `*Impl` classes. Add a new interface in `domain/repository/` and bind it in `di/Module.kt`.
- Business rules live in `domain/usecase` as pure functions with JVM unit tests.
- Android-only APIs (permission contracts, Context) stay in presentation or data, not domain.

## Tech Stack (actually used)

Kotlin · Jetpack Compose + Material 3 · Hilt · Room (schemas exported to `app/schemas/`) · DataStore
(account id; app settings: onboarding done, theme) · Compose Navigation · WorkManager (reminders; medication
fallback; Health Connect background sync every 6 h) · AlarmManager exact alarms (medication doses, re-armed by `MedicationBootReceiver`) · Retrofit/OkHttp/Gson ·
Coil · Health Connect client · system camera via `ActivityResultContracts.TakePicture` (not CameraX) ·
charts drawn with Compose `Canvas` (no chart library).

## Database (Room, version 6)

accounts, user_profiles, habits, habit_logs, foods, meals, meal_items, water_logs, sleep_logs,
activity_logs, weight_logs, vital_logs, reminders, medications, medication_logs, mood_entries.
- Every schema change needs a `Migration` in `HealthDatabase` + `ALL_MIGRATIONS`, an exported schema
  JSON, and a `MigrationTestHelper` test in `app/src/androidTest`.
- v6 added `medications.pillsLeft` (null = supply not tracked) and `pillsPerDose`; marking a dose taken/untaken
  adjusts the supply (`MedicationSchedule.pillsAfter`) and posts one "running low" notice at ≤ 5 days.
- Meal lists observe only the `meals` table: insert child rows (meal_items) BEFORE the parent.
- Id prefixes mark origin: `hc_` = imported from Health Connect, `demo_` = sample data.

## Navigation

Bottom tabs: Trang chủ (dashboard) · Sức khỏe (vitals, Health Connect) · Ăn uống (nutrition) ·
Thói quen (habits) · Tinh thần (mood). Pushed screens: profile, reports, water, reminders, medications,
food_scanner, ai_chat, settings, health_connect. First launch starts at `onboarding` (skipped when a profile exists).

There is no login: the app is single-user and offline (one local account). Authentication was descoped
on purpose; do not add Firebase.

## API Keys & Secrets

AI keys live ONLY in `server/.env` (gitignored): `GEMINI_API_KEY` (+ optional `GEMINI_MODEL`,
`GEMINI_FALLBACK_MODEL`) or `OPENAI_API_KEY`. Never put keys in `gradle.properties`, `local.properties`
or `BuildConfig` — anything in the APK can be extracted. The app talks to the backend at
`http://10.0.2.2:8787` in debug and demo builds (emulator → host); the URL is `BuildConfig.AI_BACKEND_URL`
per build type. Release points at a placeholder until a real HTTPS backend exists.
- Lint skips test sources (`ignoreTestSources`): lint's K2 analysis crashes on them, app code is still checked.

## Testing Strategy

- JVM: domain use cases, Health Connect mapping, ViewModels with in-memory fakes of domain interfaces.
- Instrumented: Room migrations (`MigrationTestHelper`), DAO/repository regressions, Compose UI.
- Backend: `node --test` with a fake upstream (no real key).
- AI accuracy: `server/eval-food.mjs` on lab-weighed Nutrition5k photos; keep a separate holdout set. It reports
  table-based and AI-only calories from the same responses.

## Food AI

The model names each food, estimates grams and picks a `table_key`; the server replaces calories/macros
with `server/data/food-table.json` (per 100 g) when the key matches and labels each item `source: table|ai`.
Regenerate the table with `node eval/build-food-table.mjs` (Nutrition5k ingredients, eval dishes excluded, plus
`data/vn-foods.json` approximate Vietnamese dishes) — never edit `food-table.json` by hand.

## Language & accessibility

- All UI text lives in `res/values/strings.xml` (Vietnamese, default) and `res/values-en/strings.xml`; keep keys
  and placeholders identical (`StringResourcesTest` enforces it). Never hard-code UI text in Kotlin.
- Counts that read wrong at 1 in English use `<plurals>` (`pluralStringResource`, `UiText.Plural`,
  `getQuantityString`); Vietnamese has only the `other` form.
- Domain returns structured values (enums, `ScoreDetail`, `ScoreInsight`), never sentences; format them in
  `presentation/ui/text/DomainText.kt`. ViewModels expose `UiText` (resource id or already-final text).
- Data-layer text (notifications, errors) uses `AppLocale.localized(context).getString(...)`.
- Per-app language: `core/AppLocale` (LocaleManager on 13+, stored choice + `attachBaseContext` below).
  The backend gets `lang` and returns a stable `code` on errors; map codes to strings in `AIRepositoryImpl`.
- Stored values stay language-independent (e.g. mood ids are the original Vietnamese labels).
- Icon-only buttons need a content description naming the item; selections use `selectable`/`toggleable`
  with a `Role`; error text gets `liveRegion`; touch targets ≥ 48 dp.

## Design

Illustrations (onboarding, empty states, feature intros) are original vectors in
`presentation/ui/theme/Illustrations.kt`, generated by `tools/illustrations/gen.py` — edit the generator and
re-run it (`--gallery` writes a light/dark preview page). Colours are palette tokens, so they follow the theme.
Use the shared `EmptyState(illustration, title, message)` component for empty lists and `ConfirmDeleteDialog`
before any delete the user can't undo.

Use the components in `presentation/ui/components` (`AppCard`, `GradientCard`, `IconBadge`,
`ScreenHeader`, `StatTile`, `PrimaryButton`, `SoftButton`, `BackTopBar`) and `HealthPalette` accents
(Emerald=brand/overall, Coral=heart, Pink=mind/BP, Orange=activity/food, Sky=water, Violet=sleep/habits,
Indigo=meds/reports, Amber=reminders). Don't use green alone. `HealthPalette` values are `@Composable` (light/dark),
so read them in composition, not inside `Canvas` draw lambdas or top-level vals.

## Notes

- Coroutines everywhere; no blocking database work on the main thread.
- AI output is an estimate: always let the user review/edit before saving, and label mock data clearly.
- Health data is sensitive: backups are disabled, exports warn the user, no analytics.
- Export/restore: `DataManagementRepositoryImpl` writes `healthapp-export-v1` JSON; restore merges by id in one
  transaction (`DataManagementDao.importAll`), moves rows to the current account, re-arms reminders/medications.
- Food search folds accents in Kotlin (`FoodSearch`), not SQL LIKE. Built-in dishes: `data/food/VietnameseFoodLibrary`
  reads `assets/vn-foods.json`, an exact copy of `server/data/vn-foods.json` (a test enforces it — copy after editing).
- Health alerts: `VitalAlerts` (rules) → `HealthAlertRepository` (age, cooldown, `health_alerts` notification channel,
  recent list) fed by Health Connect sync (raw minute data, last 2 h), manual entry (no notification) and the watch
  simulator (`watch_simulator`, only when `BuildConfig.DEMO_TOOLS`; simulated data is never stored).
- Algorithms are documented for the report in `ALGORITHMS.md` — update it when a rule in `domain/usecase` changes.

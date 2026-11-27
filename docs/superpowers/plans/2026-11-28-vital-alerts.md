# Vital Alerts + Watch Simulator Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Warn users early (Android notifications) when heart-rate / blood-pressure readings are abnormal, telling exercise apart from rest, with an in-app watch simulator to demo it live.

**Architecture:** Pure rules in `domain/usecase/VitalAlerts.kt` decide alerts from heart-rate samples, per-minute steps and BP readings. A domain interface `HealthAlertRepository` (impl in `data/alerts/`) adds the user's age, cooldown, notification posting and a "recent alerts" store. Three inputs feed it: Health Connect sync (raw samples, last 2 h), manual entry (in-app only, no notification) and the simulator (demo/debug builds).

**Tech Stack:** Kotlin, Compose/Material 3, Hilt, Coroutines/Flow, NotificationCompat, SharedPreferences + Gson, JUnit4 + kotlinx-coroutines-test.

**Spec:** `docs/superpowers/specs/2026-11-28-vital-alerts-design.md`

## Global Constraints

- No diagnosis wording; every alert screen/notification detail says it does not replace medical diagnosis. Never auto-call: 115 only via `Intent.ACTION_DIAL` (`tel:115`).
- No DB schema change (Room stays v6); simulated data is never saved to the diary.
- No new dependencies / downloads.
- Thresholds (verbatim from spec): active = ≥ 500 steps in the 10 min before; max HR = 220 − age, age unknown → 40; rest tachy ≥ 120 for ≥ 10 min (warning); ≥ 150 for ≥ 5 min (urgent); rest brady < 40 for ≥ 10 min (warning); active > 90 % max for ≥ 5 min (warning, "reduce intensity"); active > 100 % max for ≥ 2 min (urgent, "stop"); BP ≥ 180 or ≥ 120 (urgent); BP 160–179 or 100–119 (warning, re-measure after 5 min rest); ≥ 5 BP readings in 7 days with mean ≥ 135 or ≥ 85 (trend, see a doctor). "Sustained" = every sample in the span meets the condition and samples are ≤ 2 min apart. Same alert kind not re-sent within 30 min; trend at most once per day. Health Connect: only data from the last 2 h.
- "Just finished exercise" (resting now, but ≥ 500 steps in the 10 min before that) never triggers rest alerts.
- UI text only in `res/values/strings.xml` + `res/values-en/strings.xml` (identical keys/placeholders); domain returns enums, `DomainText.kt`-style mapping in presentation; notifications use `AppLocale.localized(context)`.
- Simulator only when `BuildConfig.DEMO_TOOLS`; labelled "Dữ liệu mô phỏng / Simulated data". Time compression: 1 s real = 1 min data.
- Colours: `HealthPalette.Coral` (heart), `HealthPalette.Pink` (BP). Touch targets ≥ 48 dp, icon buttons have content descriptions.

## Review Focus

1. Running with HR 150–165 at age 28 → no alert (false alarm is the spec's anti-goal). Test in Task 1 and Task 2.
2. HR still 130 for 6 min right after a run → RECOVERING, no rest alert. Test in Task 1 (`recoveryAfterRun_noRestAlert`) and Task 2.
3. Watch off for 20 min inside a high-HR span → gap > 2 min breaks "sustained", no alert. Test in Task 1.
4. Every Health Connect sync re-reads the same 2 h → cooldown must prevent duplicate notifications; data older than 2 h never alerts. Tests in Task 1 (`dueAlerts_*`, `oldData_ignored`).
5. Notification permission denied / profile without birth date → no crash, age 40 used. Test `maxHeartRate(null) == 180` in Task 1; permission guard reviewed in Task 3.

---

### Task 1: VitalAlerts rules (domain)

**Files:**
- Create: `app/src/main/java/com/healthapp/domain/usecase/VitalAlerts.kt`
- Test: `app/src/test/java/com/healthapp/domain/usecase/VitalAlertsTest.kt`

**Interfaces:**
- Produces:
  - `data class HrSample(val time: LocalDateTime, val bpm: Int)`
  - `data class StepSample(val time: LocalDateTime, val steps: Int)` — steps counted in the minute ending at `time`
  - `data class BpReading(val time: LocalDateTime, val systolic: Int, val diastolic: Int)`
  - `enum class ActivityState { RESTING, ACTIVE, RECOVERING }`
  - `enum class AlertLevel { TREND, WARNING, URGENT }`
  - `enum class AlertKind(val level: AlertLevel) { REST_TACHY(WARNING), REST_TACHY_SEVERE(URGENT), REST_BRADY(WARNING), OVER_EXERTION(WARNING), ABOVE_MAX_HR(URGENT), BP_CRISIS(URGENT), BP_HIGH(WARNING), BP_SUSTAINED(TREND) }`
  - `data class VitalAlert(val kind: AlertKind, val at: LocalDateTime, val value: Int, val secondary: Int? = null, val state: ActivityState? = null, val simulated: Boolean = false)` with `val level get() = kind.level`
  - `object VitalAlerts` with constants `ACTIVE_STEPS = 500`, `DEFAULT_AGE = 40`, `MAX_GAP_MIN = 2L`, `COOLDOWN_MIN = 30L`, `RECENT_HOURS = 2L` and:
    - `fun maxHeartRate(age: Int?): Int`
    - `fun activityState(steps: List<StepSample>, at: LocalDateTime): ActivityState`
    - `fun heartAlerts(hr: List<HrSample>, steps: List<StepSample>, age: Int?, now: LocalDateTime): List<VitalAlert>`
    - `fun bpAlerts(readings: List<BpReading>, now: LocalDateTime): List<VitalAlert>`
    - `fun dueAlerts(candidates: List<VitalAlert>, lastSent: Map<AlertKind, LocalDateTime>, now: LocalDateTime): List<VitalAlert>`

- [ ] **Step 1: Write the failing tests** (helper builds per-minute HR and steps from a start time)

```kotlin
@Test fun maxHeartRate_usesAgeOr40() { assertEquals(192, VitalAlerts.maxHeartRate(28)); assertEquals(180, VitalAlerts.maxHeartRate(null)) }
@Test fun activityState_activeRestingRecovering() // 600 steps in last 10 min → ACTIVE; 0 → RESTING; 0 now but 600 in min 10–20 before → RECOVERING
@Test fun restTachy_after10min_warning()          // 12 min of 125 bpm, 0 steps → [REST_TACHY], value 125, state RESTING
@Test fun restTachy_9min_noAlert()
@Test fun restTachySevere_supersedesWarning()     // 6 min of 155 at rest → only REST_TACHY_SEVERE
@Test fun restBrady_after10min()                  // 11 min of 38 → REST_BRADY
@Test fun running_highHr_noAlert()                // age 28, 20 min of 155–165 bpm, 150 steps/min → empty
@Test fun overExertion_after5min()                // age 28, 6 min of 180 bpm (>172.8) while active → [OVER_EXERTION]
@Test fun aboveMax_supersedesOverExertion()       // age 28, 3 min of 195 active → only ABOVE_MAX_HR
@Test fun recoveryAfterRun_noRestAlert()          // 15 min run then 6 min of 130 bpm with 0 steps → empty
@Test fun dataGap_breaksSustained()               // 125 bpm at min 0–4 and 25–29, nothing between → empty
@Test fun oldData_ignored()                       // 15 min of 125 at rest ending 3 h before now → empty
@Test fun bpCrisis_and_high()                     // 185/100 → BP_CRISIS; 165/95 → BP_HIGH; 150/95 → none (acute); crisis wins over high
@Test fun bpSustained_needs5Readings()            // 5 readings in 7 days mean 138/82 → BP_SUSTAINED; 4 readings → none; readings 8 days old ignored
@Test fun dueAlerts_cooldown30min_trendOncePerDay() // same kind sent 20 min ago → filtered; 31 min → kept; BP_SUSTAINED sent earlier today → filtered, yesterday → kept
```

- [ ] **Step 2: Run tests, expect compile failure**

Run: `./gradlew.bat :app:testDebugUnitTest --tests "com.healthapp.domain.usecase.VitalAlertsTest"`
Expected: FAIL (unresolved `VitalAlerts`).

- [ ] **Step 3: Implement `VitalAlerts`**

`activityState`: sum steps with `time` in `(at−10min, at]` → ACTIVE if ≥ 500; else sum in `(at−20min, at−10min]` ≥ 500 → RECOVERING; else RESTING.
`heartAlerts`: keep samples with `time` in `[now − 2h, now]`, sorted. A rule fires when the run of samples ending at the latest sample all satisfy its predicate (bpm threshold AND the required `activityState` at that sample's time), consecutive gaps ≤ 2 min, and `latest.time − first.time ≥ duration`:

```kotlin
private fun sustained(s: List<HrSample>, minutes: Long, ok: (HrSample) -> Boolean): HrSample? {
    if (s.isEmpty() || !ok(s.last())) return null
    var first = s.last()
    for (i in s.size - 2 downTo 0) {
        if (!ok(s[i]) || Duration.between(s[i].time, first.time).toMinutes() > MAX_GAP_MIN) break
        first = s[i]
    }
    return if (Duration.between(first.time, s.last().time).toMinutes() >= minutes) s.last() else null
}
```
Rest rules require RESTING (not RECOVERING); exertion rules require ACTIVE; 90 % / 100 % compare `bpm > max * 0.9` / `bpm > max`. Emit severe/above-max instead of (not in addition to) the milder kind. `VitalAlert.value` = latest bpm, `state` = state at latest sample.
`bpAlerts`: acute rules on the latest reading within 2 h (crisis: `sys >= 180 || dia >= 120`; high: `sys >= 160 || dia >= 100`); trend over readings in `[now − 7d, now]`, ≥ 5, mean sys ≥ 135 or mean dia ≥ 85 (value/secondary = rounded means).
`dueAlerts`: keep a candidate if no `lastSent[kind]`, or (TREND) `lastSent` date < `now` date, or (others) `lastSent` ≤ `now − 30 min`.

- [ ] **Step 4: Run tests, expect PASS** (same command; 15 tests)

- [ ] **Step 5: Commit** — `git commit -m "feat: vital alert rules (rest vs exercise, BP crisis/trend)"`

---

### Task 2: Watch scenarios (domain)

**Files:**
- Create: `app/src/main/java/com/healthapp/domain/usecase/WatchScenario.kt`
- Test: `app/src/test/java/com/healthapp/domain/usecase/WatchScenarioTest.kt`

**Interfaces:**
- Consumes: Task 1 types and `VitalAlerts.heartAlerts/bpAlerts`.
- Produces:
  - `enum class WatchScenario { REST, RUN, OVER_EXERTION, REST_TACHY, BP_CRISIS }`
  - `data class WatchTarget(val bpm: Int, val stepsPerMin: Int, val bp: Pair<Int, Int>? = null)`
  - `object WatchSimulation { fun target(s: WatchScenario, minute: Int, age: Int?): WatchTarget; fun step(currentBpm: Double, target: Int): Double }`

Targets (minute = minutes since the scenario started): REST 70 bpm / 0 steps; RUN 155 / 150; OVER_EXERTION `round(max*0.95)` / 160 (age 28 → 182); REST_TACHY 0 steps, 128 bpm for minutes 0–11 then 158; BP_CRISIS 72 bpm / 0 steps with `bp = 186 to 118` at minute 2 only. `step` moves half of the way to the target each minute (`current + (target − current) * 0.5`), so switching RUN → REST decays like recovery (from 70 bpm: ≥ 120 by minute 3, ≥ 173 by minute 4).

- [ ] **Step 1: Write failing end-to-end tests** — feed 1 sample/min (bpm from `step`, steps from target) into `VitalAlerts` at each minute and collect kinds:

```kotlin
@Test fun run_20min_neverAlerts()                    // age 28 → no kinds
@Test fun runThenRest_recovery_neverAlerts()          // 15 min RUN then 10 min REST → no kinds
@Test fun restTachy_warnsBy14min_thenUrgent()         // REST_TACHY appears at minute ≤ 14; REST_TACHY_SEVERE appears by minute 21
@Test fun overExertion_warnsBy10min()                 // OVER_EXERTION appears at minute ≤ 10, ABOVE_MAX_HR never (age 28)
@Test fun bpCrisis_alertsAtMinute2()                  // BP_CRISIS at minute 2
```

- [ ] **Step 2: Run, expect FAIL** — `./gradlew.bat :app:testDebugUnitTest --tests "com.healthapp.domain.usecase.WatchScenarioTest"`
- [ ] **Step 3: Implement `WatchSimulation`** (adjust ramp/targets only if a test shows the spec timing is unreachable; record any change in the spec's "builder calls")
- [ ] **Step 4: Run, expect PASS**
- [ ] **Step 5: Commit** — `git commit -m "feat: watch simulation scenarios"`

---

### Task 3: HealthAlertRepository (notifications, cooldown, recent list)

**Files:**
- Modify: `app/src/main/java/com/healthapp/domain/repository/AppRepositories.kt` (add interface)
- Create: `app/src/main/java/com/healthapp/data/alerts/HealthAlertRepositoryImpl.kt`
- Modify: `app/src/main/java/com/healthapp/di/Module.kt` (bind)
- Modify: `app/src/main/java/com/healthapp/HealthApp.kt` (create channel at start, like `ReminderWorker.ensureChannel`)
- Modify: `app/proguard-rules.pro` (keep stored alert class for Gson)
- Modify: `res/values/strings.xml`, `res/values-en/strings.xml`

**Interfaces:**
- Consumes: Task 1; `AccountSession`, `ProfileRepository.getProfile`, `VitalRepository.observeAll`, `HealthCalculations.ageOn`.
- Produces:
```kotlin
interface HealthAlertRepository {
    /** Newest first, at most 20, includes simulated alerts (flagged). */
    val recent: Flow<List<VitalAlert>>
    /** Runs VitalAlerts (adds stored BP of the last 7 days and the profile age), applies cooldown,
     *  posts a notification for each due alert when [notify], records them; returns the due alerts. */
    suspend fun check(hr: List<HrSample>, steps: List<StepSample>, bp: List<BpReading>,
                      now: LocalDateTime = LocalDateTime.now(), notify: Boolean = true, simulated: Boolean = false): List<VitalAlert>
    /** Forgets the simulator's cooldown so a new scenario can alert again. */
    fun resetSimulation()
    suspend fun clearRecent()
}
```
- Notification: channel id `health_alerts`, `IMPORTANCE_HIGH`, names from strings `channel_health_alerts` / `channel_health_alerts_desc`; title per kind (`alert_title_<kind>`), body per kind with value(s) and one advice line (`alert_body_<kind>`); content intent opens `MainActivity` with extra `EXTRA_OPEN_ROUTE = "open_route"` = `"health_alerts"`; urgent → `PRIORITY_HIGH`, category `CATEGORY_ALARM` not used (no full-screen). Guard `POST_NOTIFICATIONS` exactly as `ReminderWorker.notify`.
- Store: SharedPreferences `health_alerts` (JSON list of last 20 alerts, map of real `lastSent`); simulator cooldown map kept in memory only.

- [ ] **Step 1: Implement interface + impl + DI + channel + strings** (no JVM test: behaviour is covered by Task 1 `dueAlerts` and Task 5's fake; impl is Android-bound)
- [ ] **Step 2: Build and lint** — `./gradlew.bat :app:compileDebugKotlin :app:lintDebug` → 0 errors; `StringResourcesTest` passes.
- [ ] **Step 3: Commit** — `git commit -m "feat: health alert notifications and recent alerts store"`

---

### Task 4: Alerts screen, Health-tab card, notification deep link

**Files:**
- Create: `app/src/main/java/com/healthapp/presentation/viewmodel/HealthAlertsViewModel.kt`
- Create: `app/src/main/java/com/healthapp/presentation/ui/alerts/HealthAlertsScreen.kt`
- Modify: `app/src/main/java/com/healthapp/presentation/ui/text/DomainText.kt` (`AlertKind.title()`, `AlertKind.advice()`, `ActivityState.label()`)
- Modify: `app/src/main/java/com/healthapp/presentation/ui/AppNavigation.kt` (route `health_alerts`; root takes `openRoute: String?` and navigates once)
- Modify: `app/src/main/java/com/healthapp/presentation/ui/MainActivity.kt` (read `open_route` in `onCreate`/`onNewIntent` into a state passed to the root)
- Modify: `app/src/main/java/com/healthapp/presentation/ui/health/HealthTrackingScreen.kt` (card under the Health Connect card: latest alert in last 24 h → tap opens `health_alerts`; nothing when empty)
- Modify: strings (vi/en)
- Test: `app/src/test/java/com/healthapp/presentation/viewmodel/ViewModelTests.kt` (+ `FakeHealthAlerts`)

**Interfaces:**
- Consumes: Task 3 `HealthAlertRepository.recent/clearRecent`.
- Produces: `HealthAlertsViewModel(repo: HealthAlertRepository)` with `val alerts: StateFlow<List<VitalAlert>>`, `fun clear()`.

Screen: newest alert expanded (title, level chip, value(s), "lúc đang nghỉ / đang vận động / vừa tập xong", advice). For URGENT: F.A.S.T card (Face–Arm–Speech–Time, 4 lines) + `PrimaryButton("Gọi 115")` → `ACTION_DIAL tel:115`. Older alerts as compact rows. Footer: not a medical diagnosis. Simulated alerts show a "Mô phỏng" chip. Empty state uses `EmptyState(Illustration.…, …)`.

- [ ] **Step 1: Failing test** — `healthAlerts_exposesRecent_andClears`: fake emits two alerts → `vm.alerts.value.size == 2`; `vm.clear()` → empty.
- [ ] **Step 2: Run, expect FAIL** — `./gradlew.bat :app:testDebugUnitTest --tests "com.healthapp.presentation.viewmodel.ViewModelTests"`
- [ ] **Step 3: Implement ViewModel, screen, card, routing, strings**
- [ ] **Step 4: Run unit tests + lint, expect PASS / 0 errors**
- [ ] **Step 5: Commit** — `git commit -m "feat: health alerts screen with F.A.S.T and call 115"`

---

### Task 5: Watch simulator screen (demo/debug)

**Files:**
- Create: `app/src/main/java/com/healthapp/presentation/viewmodel/WatchSimulatorViewModel.kt`
- Create: `app/src/main/java/com/healthapp/presentation/ui/alerts/WatchSimulatorScreen.kt`
- Modify: `AppNavigation.kt` (route `watch_simulator`), `HealthTrackingScreen.kt` (entry button only when `BuildConfig.DEMO_TOOLS`), strings
- Test: `ViewModelTests.kt`

**Interfaces:**
- Consumes: Task 2 `WatchSimulation`, Task 3 `HealthAlertRepository.check(..., simulated = true)` / `resetSimulation()`, `ProfileRepository` + `AccountSession` for age.
- Produces: `WatchSimulatorViewModel` with `state: StateFlow<WatchSimState>` (`scenario: WatchScenario?`, `bpm: Int`, `stepsPerMin: Int`, `activity: ActivityState`, `simMinute: Int`, `lastBp: Pair<Int,Int>?`, `lastAlert: VitalAlert?`, `running: Boolean`), `fun start(s: WatchScenario)`, `fun stop()`.

Loop (in `viewModelScope`): every `TICK_MS = 1000` real ms add one simulated minute (`simTime = startTime + simMinute min`), update bpm with `WatchSimulation.step`, append `HrSample`/`StepSample` (keep last 40 min), BP reading when the target has one, call `check(..., now = simTime, notify = true, simulated = true)`; `start` calls `resetSimulation()` and keeps the current bpm (so RUN → REST shows recovery). Screen: round "watch face" (Canvas circle, Coral ring) with bpm, steps/min, state label, sim clock; five scenario buttons (`selectable`, Role.RadioButton); last alert banner; label "Dữ liệu mô phỏng — không lưu vào nhật ký". Ask for `POST_NOTIFICATIONS` on first start (same pattern as `MedicationScreen`).

- [ ] **Step 1: Failing test** — `watchSimulator_restTachy_raisesAlert` with `StandardTestDispatcher`: start REST_TACHY, `advanceTimeBy(15_000)` → fake repo received a `check` whose result contains `REST_TACHY` and all calls had `simulated = true`; `watchSimulator_run_noAlert`: RUN for 20 s → no alerts returned.
- [ ] **Step 2: Run, expect FAIL**
- [ ] **Step 3: Implement ViewModel + screen + entry**
- [ ] **Step 4: Run tests + lint, expect PASS**
- [ ] **Step 5: Commit** — `git commit -m "feat: in-app watch simulator for alert demos"`

---

### Task 6: Real-data hooks (Health Connect sync, manual entry)

**Files:**
- Modify: `app/src/main/java/com/healthapp/data/healthconnect/HealthConnectMapper.kt` (pure helpers)
- Modify: `app/src/main/java/com/healthapp/data/healthconnect/HealthConnectManager.kt` (inject `HealthAlertRepository`; after `dao.upsertAll`, call `check` with last-2 h inputs, wrapped in `runCatching`)
- Modify: `app/src/main/java/com/healthapp/presentation/viewmodel/HealthTrackingViewModel.kt` (inject `HealthAlertRepository`; after a successful HEART_RATE/BLOOD_PRESSURE `record`, `check(..., notify = false)`; expose due alerts as `latestAlert` for an in-app banner linking to `health_alerts`)
- Test: `app/src/test/java/com/healthapp/data/healthconnect/HealthConnectMapperTest.kt`, `ViewModelTests.kt` (HealthTrackingViewModel constructor if a test builds it)

**Interfaces:**
- Produces (in `HealthConnectMapper`):
  - `fun alertHeart(samples: List<HcHeartSample>, now: LocalDateTime): List<HrSample>` — raw samples in last 2 h, one per minute (max bpm of that minute)
  - `fun alertSteps(raw: List<HcRawSteps>, rawEnd: (HcRawSteps) -> LocalDateTime, now: LocalDateTime): List<StepSample>` — spread each record's count evenly over its minutes, last 2 h only. (If `HcRawSteps` lacks an end time, add `end: LocalDateTime` to it and set it from `StepsRecord.endTime` in the manager.)

- [ ] **Step 1: Failing mapper tests** — `alertSteps_spreadsCountOverMinutes` (600 steps over 10 min → ten samples of 60); `alertHeart_keepsLast2hMaxPerMinute`.
- [ ] **Step 2: Run, expect FAIL** — `./gradlew.bat :app:testDebugUnitTest --tests "com.healthapp.data.healthconnect.HealthConnectMapperTest"`
- [ ] **Step 3: Implement helpers, manager hook, ViewModel hook + banner**
- [ ] **Step 4: Run all unit tests + lint + `:app:compileDebugAndroidTestKotlin`, expect PASS**
- [ ] **Step 5: Commit** — `git commit -m "feat: run vital alerts on Health Connect sync and manual entries"`

---

### Task 7: Docs and end-to-end check

**Files:**
- Modify: `ALGORITHMS.md` (section 12 "Cảnh báo sinh hiệu": thresholds, sources AHA 2017 BP / hypertensive crisis 180/120, target-HR zones, home BP 135/85, F.A.S.T; complexity; tests), table row 12
- Modify: `README.md` (feature bullet, test counts), `PROJECT_SUMMARY.md` (test table), `DEMO.md` (simulator demo step), `CLAUDE.md` (VitalAlerts in domain list; simulator only with DEMO_TOOLS)

- [ ] **Step 1: Update docs**
- [ ] **Step 2: Full verification** — `./gradlew.bat :app:testDebugUnitTest :app:lintDebug :app:assembleDemo` → all pass, 0 lint errors; `cd server && npm test` → 18 pass.
- [ ] **Step 3: Emulator check (debug)** — Health tab → Đồng hồ mô phỏng: RUN 20 s → no notification; REST_TACHY → notification within ~14 s, tap opens alert screen; BP_CRISIS → urgent notification with F.A.S.T + "Gọi 115" opens dialer (do not call).
- [ ] **Step 4: Commit** — `git commit -m "docs: vital alerts algorithm and demo steps"`

package com.healthapp.presentation.viewmodel

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.healthapp.domain.repository.AccountSession
import com.healthapp.domain.repository.HealthAlertRepository
import com.healthapp.domain.repository.ProfileRepository
import com.healthapp.domain.usecase.ActivityState
import com.healthapp.domain.usecase.BpReading
import com.healthapp.domain.usecase.HealthCalculations
import com.healthapp.domain.usecase.HrSample
import com.healthapp.domain.usecase.StepSample
import com.healthapp.domain.usecase.VitalAlert
import com.healthapp.domain.usecase.VitalAlerts
import com.healthapp.domain.usecase.WatchScenario
import com.healthapp.domain.usecase.WatchSimulation
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import java.time.LocalDate
import java.time.LocalDateTime
import javax.inject.Inject
import kotlin.math.roundToInt

data class WatchSimState(
    val scenario: WatchScenario? = null,
    val bpm: Int = 70,
    val stepsPerMin: Int = 0,
    val activity: ActivityState = ActivityState.RESTING,
    val simMinute: Int = 0,
    val lastBp: Pair<Int, Int>? = null,
    val lastAlert: VitalAlert? = null,
    val running: Boolean = false
)

/**
 * Demo watch: every second of real time adds one minute of simulated readings and runs them through
 * the real alert rules (notifications labelled "simulated"). Nothing is written to the diary.
 */
@HiltViewModel
class WatchSimulatorViewModel @Inject constructor(
    private val alerts: HealthAlertRepository,
    private val profiles: ProfileRepository,
    private val session: AccountSession
) : ViewModel() {
    private val mutable = MutableStateFlow(WatchSimState())
    val state: StateFlow<WatchSimState> = mutable.asStateFlow()

    private var job: Job? = null
    private var bpm = 70.0
    private var simTime: LocalDateTime = LocalDateTime.now()
    private val hr = ArrayDeque<HrSample>()
    private val steps = ArrayDeque<StepSample>()
    private val bp = mutableListOf<BpReading>()

    /** Switches scenario; the heart rate carries over, so RUN → REST shows a natural recovery. */
    fun start(scenario: WatchScenario) {
        job?.cancel()
        alerts.resetSimulation()
        bp.clear()
        mutable.update { it.copy(scenario = scenario, running = true, lastAlert = null, lastBp = null) }
        job = viewModelScope.launch {
            val age = runCatching {
                profiles.getProfile(session.getCurrentAccountId())?.dateOfBirth?.let { HealthCalculations.ageOn(LocalDate.parse(it)) }
            }.getOrNull()
            var minute = 0
            while (isActive) {
                delay(TICK_MS)
                simTime = simTime.plusMinutes(1)
                val target = WatchSimulation.target(scenario, minute, age)
                bpm = WatchSimulation.step(bpm, target.bpm)
                hr.addLast(HrSample(simTime, bpm.roundToInt()))
                steps.addLast(StepSample(simTime, target.stepsPerMin))
                while (hr.size > WINDOW_MIN) hr.removeFirst()
                while (steps.size > WINDOW_MIN) steps.removeFirst()
                target.bp?.let { (s, d) -> bp += BpReading(simTime, s, d) }
                val due = alerts.check(hr.toList(), steps.toList(), bp.toList(), now = simTime, notify = true, simulated = true)
                mutable.update { s ->
                    s.copy(
                        bpm = bpm.roundToInt(), stepsPerMin = target.stepsPerMin, simMinute = s.simMinute + 1,
                        activity = VitalAlerts.activityState(steps.toList(), simTime),
                        lastBp = target.bp ?: s.lastBp,
                        lastAlert = due.maxByOrNull { it.level } ?: s.lastAlert
                    )
                }
                minute++
            }
        }
    }

    fun stop() {
        job?.cancel()
        job = null
        mutable.update { it.copy(running = false) }
    }

    override fun onCleared() = stop()

    companion object {
        const val TICK_MS = 1000L
        /** Minutes of simulated history kept (rules look back at most 20 minutes). */
        private const val WINDOW_MIN = 40
    }
}

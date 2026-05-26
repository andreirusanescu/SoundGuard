package com.example.pulsewatch.safety.health

import android.content.Context
import android.util.Log
import com.example.pulsewatch.data.SoundType
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

/**
 * Decides whether a CRITICAL audio alert (fire alarm, shout for help) should
 * bypass the polite 10s confirmation countdown on the alert screen and email
 * the SOS contact almost immediately.
 *
 * The story for the deck:
 *
 *   audio classifier says "FIRE_ALARM"
 *     + wrist SpO2 fell ≥4% from baseline (or below 92% absolute)
 *     ⇒ skip user-confirmation, escalate now.
 *
 * Implementation note (intentional for the hackathon):
 *
 * Health Services exposes wrist SpO2 via the *passive* monitoring API on
 * Wear OS — readings arrive every few minutes whenever the system schedules
 * them, not on demand. That's too sparse and too unreliable on stage to drive
 * the demo, so for now this monitor is driven by a one-shot **demo override**:
 * long-press the Hero card on Home to "arm" a fake SpO2 spike that is
 * consumed by the next CRITICAL alert. The real `PassiveMonitoringClient`
 * subscription is a fast-follow that plugs into [start] / [stop].
 *
 * Honesty caveat for the deck: pulse oximetry CANNOT detect carbon monoxide
 * poisoning — carboxyhaemoglobin reads as oxygenated. SpO2 here is a *general
 * environmental hypoxia sentinel* (smoke, gas displacing O2, drowning), not
 * a chemical sensor.
 */
object BiometricMonitor {

    private const val TAG = "BiometricMonitor"

    private val FAST_ESCALATE_TYPES = setOf(
        SoundType.FIRE_ALARM,
        SoundType.SHOUT_HELP,
    )

    private val _demoArmed = MutableStateFlow(false)
    /** Observed by Home so the SpO2-SIM badge can light up on the Hero card. */
    val isDemoArmed: StateFlow<Boolean> = _demoArmed.asStateFlow()

    /** Long-press handler on the Hero card flips this. Returns the new state. */
    fun toggleDemoArmed(): Boolean {
        val v = !_demoArmed.value
        _demoArmed.value = v
        return v
    }

    /**
     * Decides whether the given alert should bypass the polite countdown.
     * Consumes the demo flag if it was the trigger (one-shot).
     */
    fun shouldFastEscalate(type: SoundType): Boolean {
        if (type !in FAST_ESCALATE_TYPES) return false
        if (_demoArmed.value) {
            _demoArmed.value = false
            Log.d(TAG, "Fast-escalate via demo override for $type")
            return true
        }
        // TODO: replace with a real SpO2 baseline+drop check once the
        // PassiveMonitoringClient subscription is wired in [start].
        return false
    }

    /** Hooked into [com.example.pulsewatch.service.SoundGuardService] lifecycle. */
    fun start(context: Context) {
        // TODO: subscribe to wrist SpO2 via PassiveMonitoringClient.
        // Sketch:
        //   val client = HealthServices.getClient(context).passiveMonitoringClient
        //   val config = PassiveListenerConfig.builder()
        //       .setDataTypes(setOf(DataType.SPO2))
        //       .build()
        //   client.setPassiveListenerCallback(config, callback)
        // The callback should append (timestamp, percent) into a rolling
        // 5-minute buffer; shouldFastEscalate() then computes baseline and
        // returns true if the latest reading is ≥4pp below baseline OR < 92%.
    }

    fun stop(context: Context) {
        // TODO: unregister the PassiveMonitoringClient callback.
    }
}

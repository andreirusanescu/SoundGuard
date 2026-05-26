package com.soundguard.app.ml

enum class Severity { CRITICAL, HIGH, MEDIUM }

/**
 * Canonical alert TYPEs (the enum `name` is what's wire-encoded in the
 * `/pulse/event/sound_alert` payload). Severity is implicit per the
 * watch–phone protocol; the watch derives its UI from TYPE alone.
 */
enum class SoundCategory(
    val yamnetLabels: Set<String>,
    val displayName: String,
    val severity: Severity,
    val threshold: Float
) {
    SIREN(
        setOf(
            "Siren",
            "Emergency vehicle",
            "Police car (siren)",
            "Ambulance (siren)",
            "Fire engine, fire truck (siren)",
            "Civil defense siren"
        ),
        "Siren",
        Severity.HIGH,
        0.40f
    ),
    FIRE_ALARM(
        setOf("Smoke detector, smoke alarm", "Fire alarm"),
        "Fire alarm",
        Severity.CRITICAL,
        0.40f
    ),
    SHOUT_HELP(
        setOf("Screaming", "Shout", "Yell", "Children shouting"),
        "Shout for help",
        Severity.CRITICAL,
        0.45f
    ),
    CAR_HORN(
        setOf("Vehicle horn, car horn, honking", "Air horn, truck horn", "Train horn"),
        "Car horn",
        Severity.HIGH,
        0.40f
    ),
    BABY_CRY(
        setOf("Baby cry, infant cry", "Crying, sobbing"),
        "Baby crying",
        Severity.MEDIUM,
        0.45f
    ),
    GLASS_BREAK(
        setOf("Glass", "Shatter"),
        "Glass breaking",
        Severity.HIGH,
        0.45f
    ),
    DOG_BARK(
        setOf("Bark", "Dog"),
        "Dog barking",
        Severity.MEDIUM,
        0.50f
    ),

    /**
     * Romania's national emergency cell-broadcast system (RO-ALERT).
     * Not a YAMNet class — surfaced via [com.soundguard.app.alert.RoAlertListener]
     * which intercepts Cell Broadcast notifications. Threshold = 1.0 (never via ML).
     */
    RO_ALERT(
        emptySet(),
        "RO-ALERT",
        Severity.CRITICAL,
        1.0f
    );

    companion object {
        private val labelToCategory: Map<String, SoundCategory> by lazy {
            buildMap {
                values().forEach { cat ->
                    cat.yamnetLabels.forEach { label -> put(label, cat) }
                }
            }
        }

        fun fromYamnetLabel(label: String): SoundCategory? = labelToCategory[label]
    }
}

data class Detection(
    val category: SoundCategory,
    val score: Float,
    val timestampMs: Long
)

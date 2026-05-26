package com.example.pulsewatch.data

// ─── DataLayer paths ────────────────────────────────────────────────────
const val SOUND_ALERT_PATH = "/pulse/event/sound_alert"        // phone → watch
const val AUDIO_CHUNK_PATH = "/pulse/audio/stream/chunk"       // watch → phone

// ─── SOS escalation timing ─────────────────────────────────────────────
// Default window — user has this long to tap Cancel before the watch
// auto-emails the SOS contact with location + alert type.
const val SOS_ESCALATION_SECONDS = 10
// Tightened window for CRITICAL alerts that coincide with a wrist SpO2 drop
// (smoke / suffocation / hypoxia signal). Keep enough time for a deliberate
// false-alarm cancel but skip most of the polite countdown.
const val SOS_FAST_ESCALATION_SECONDS = 2

// ─── Audio stream format (watch → phone) ───────────────────────────────
const val AUDIO_SAMPLE_RATE_HZ = 16_000
const val AUDIO_CHANNELS = 1                                    // mono
const val AUDIO_BYTES_PER_SAMPLE = 2                            // PCM_16BIT
const val AUDIO_CHUNK_MS = 500
const val AUDIO_CHUNK_SAMPLES = AUDIO_SAMPLE_RATE_HZ * AUDIO_CHUNK_MS / 1000   // 8000
const val AUDIO_CHUNK_BYTES = AUDIO_CHUNK_SAMPLES * AUDIO_BYTES_PER_SAMPLE     // 16000
// Byte order on the wire: little-endian (standard Android AudioRecord output)

enum class SoundSeverity { MEDIUM, HIGH, CRITICAL }

enum class SoundDirection(val displayName: String) {
    LEFT("LEFT"),
    CENTER("CENTER"),
    RIGHT("RIGHT")
}

enum class SoundType(val label: String, val emoji: String, val severity: SoundSeverity) {
    SIREN("Siren", "🚨", SoundSeverity.HIGH),
    FIRE_ALARM("Fire Alarm", "🔥", SoundSeverity.CRITICAL),
    SHOUT_HELP("Shout / Help", "🆘", SoundSeverity.CRITICAL),
    CAR_HORN("Car Horn", "📯", SoundSeverity.HIGH),
    BABY_CRY("Baby Crying", "👶", SoundSeverity.MEDIUM),
    GLASS_BREAK("Glass Break", "💥", SoundSeverity.HIGH),
    DOG_BARK("Dog Barking", "🐕", SoundSeverity.MEDIUM),
    // Romania's national emergency cell-broadcast — phone-side relay only,
    // never produced by the watch. Listed last so the wire enum-name match
    // succeeds when :app dispatches a RO-ALERT payload.
    RO_ALERT("RO-ALERT", "📢", SoundSeverity.CRITICAL)
}

data class SoundAlertPayload(
    val type: SoundType,
    val direction: SoundDirection,
    val confidence: Float,
    val timestamp: Long = System.currentTimeMillis()
) {
    val severity: SoundSeverity get() = type.severity

    fun toBytes(): ByteArray =
        "${type.name}|${direction.name}|$confidence|$timestamp"
            .toByteArray(Charsets.UTF_8)

    companion object {
        fun fromBytes(bytes: ByteArray): SoundAlertPayload? = runCatching {
            val parts = String(bytes, Charsets.UTF_8).split("|")
            SoundAlertPayload(
                type = SoundType.valueOf(parts[0]),
                direction = SoundDirection.valueOf(parts[1]),
                confidence = parts[2].toFloat(),
                timestamp = parts[3].toLong()
            )
        }.getOrNull()
    }
}

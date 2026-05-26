package com.soundguard.app.ui.theme

import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.BrokenImage
import androidx.compose.material.icons.filled.Campaign
import androidx.compose.material.icons.filled.ChildCare
import androidx.compose.material.icons.filled.DirectionsCar
import androidx.compose.material.icons.filled.LocalFireDepartment
import androidx.compose.material.icons.filled.LocalPolice
import androidx.compose.material.icons.filled.Pets
import androidx.compose.material.icons.filled.PriorityHigh
import androidx.compose.material.icons.filled.Warning
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import com.soundguard.app.ml.Severity
import com.soundguard.app.ml.SoundCategory

data class SeverityPalette(val accent: Color, val container: Color, val onContainer: Color)

fun paletteFor(severity: Severity): SeverityPalette = when (severity) {
    Severity.CRITICAL -> SeverityPalette(SeverityCritical, SeverityCriticalContainer, Color(0xFF7F1D1D))
    Severity.HIGH -> SeverityPalette(SeverityHigh, SeverityHighContainer, Color(0xFF7C2D12))
    Severity.MEDIUM -> SeverityPalette(SeverityMedium, SeverityMediumContainer, Color(0xFF713F12))
}

fun iconFor(category: SoundCategory): ImageVector = when (category) {
    SoundCategory.SIREN -> Icons.Filled.LocalPolice
    SoundCategory.FIRE_ALARM -> Icons.Filled.LocalFireDepartment
    SoundCategory.SHOUT_HELP -> Icons.Filled.PriorityHigh
    SoundCategory.CAR_HORN -> Icons.Filled.DirectionsCar
    SoundCategory.BABY_CRY -> Icons.Filled.ChildCare
    SoundCategory.GLASS_BREAK -> Icons.Filled.BrokenImage
    SoundCategory.DOG_BARK -> Icons.Filled.Pets
    SoundCategory.RO_ALERT -> Icons.Filled.Campaign
}

fun severityLabel(severity: Severity): String = when (severity) {
    Severity.CRITICAL -> "Critical"
    Severity.HIGH -> "High"
    Severity.MEDIUM -> "Medium"
}

val FallbackIcon: ImageVector = Icons.Filled.Warning

package com.soundguard.app.ui.settings

import androidx.compose.foundation.background
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Contrast
import androidx.compose.material.icons.filled.Email
import androidx.compose.material.icons.filled.FormatSize
import androidx.compose.material.icons.filled.Headphones
import androidx.compose.material.icons.filled.Logout
import androidx.compose.material.icons.filled.Person
import androidx.compose.material.icons.filled.RecordVoiceOver
import androidx.compose.material.icons.filled.VerifiedUser
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.FilterChip
import androidx.compose.material3.FilterChipDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.soundguard.app.auth.User
import com.soundguard.app.data.AgeBracket
import com.soundguard.app.data.Environment
import com.soundguard.app.data.HearingConcern
import com.soundguard.app.data.HearingProfile
import com.soundguard.app.ml.SoundCategory
import com.soundguard.app.ui.auth.AuthViewModel
import com.soundguard.app.ui.theme.iconFor
import com.soundguard.app.ui.theme.paletteFor

@Composable
fun SettingsScreen(
    modifier: Modifier = Modifier,
    onAuthorizeGmail: () -> Unit = {},
    authViewModel: AuthViewModel = hiltViewModel(),
    settingsViewModel: SettingsViewModel = hiltViewModel()
) {
    val user by authViewModel.user.collectAsStateWithLifecycle()
    val accessibility by settingsViewModel.accessibility.collectAsStateWithLifecycle()
    val profile by settingsViewModel.profile.collectAsStateWithLifecycle()
    val contacts by settingsViewModel.contacts.collectAsStateWithLifecycle()
    val gmailAuthorized by settingsViewModel.gmailAuthorized.collectAsStateWithLifecycle()

    Column(
        modifier = modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(20.dp)
    ) {
        Spacer(Modifier.height(8.dp))
        Text(
            text = "Settings",
            style = MaterialTheme.typography.headlineMedium
        )
        Spacer(Modifier.height(20.dp))
        user?.let {
            AccountCard(user = it, onSignOut = authViewModel::signOut)
            Spacer(Modifier.height(20.dp))
        }
        SectionHeader("Hearing profile")
        Text(
            text = "Helps the AI Coach personalize advice for you. Stays on-device.",
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.padding(horizontal = 4.dp, vertical = 4.dp)
        )
        Spacer(Modifier.height(8.dp))
        HearingProfileCard(
            profile = profile,
            onName = settingsViewModel::setDisplayName,
            onAge = settingsViewModel::setAgeBracket,
            onEnvironment = settingsViewModel::setEnvironment,
            onHeadphoneHours = settingsViewModel::setHeadphoneHours,
            onConcern = settingsViewModel::setConcern
        )
        Spacer(Modifier.height(20.dp))
        SectionHeader("Trusted contacts")
        Text(
            text = "Emails that receive your SOS when an alert escalates from the watch. " +
                "Synced to your watch automatically.",
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.padding(horizontal = 4.dp, vertical = 4.dp)
        )
        Spacer(Modifier.height(8.dp))
        TrustedContactsCard(
            contacts = contacts,
            onAdd = settingsViewModel::addContact,
            onRemove = settingsViewModel::removeContact,
        )
        Spacer(Modifier.height(12.dp))
        GmailAuthCard(
            authorized = gmailAuthorized,
            onAuthorize = onAuthorizeGmail,
        )
        Spacer(Modifier.height(20.dp))
        SectionHeader("Accessibility")
        Spacer(Modifier.height(8.dp))
        Card(
            modifier = Modifier.fillMaxWidth(),
            shape = RoundedCornerShape(20.dp),
            colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface)
        ) {
            Column {
                ToggleRow(
                    icon = Icons.Filled.Contrast,
                    title = "High contrast",
                    subtitle = "Pure black/white surfaces with boosted severity colours.",
                    checked = accessibility.highContrast,
                    onChange = settingsViewModel::setHighContrast
                )
                ToggleRow(
                    icon = Icons.Filled.FormatSize,
                    title = "Large text",
                    subtitle = "Bumps every text size by 30 %.",
                    checked = accessibility.largeText,
                    onChange = settingsViewModel::setLargeText
                )
                ToggleRow(
                    icon = Icons.Filled.RecordVoiceOver,
                    title = "Voice replies",
                    subtitle = "Read every Coach answer aloud automatically. Hands-free / low-vision friendly.",
                    checked = accessibility.voiceReplies,
                    onChange = settingsViewModel::setVoiceReplies
                )
            }
        }
        Spacer(Modifier.height(20.dp))
        SectionHeader("Detected categories")
        Spacer(Modifier.height(8.dp))
        Card(
            modifier = Modifier.fillMaxWidth(),
            shape = RoundedCornerShape(20.dp),
            colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface)
        ) {
            Column(modifier = Modifier.padding(vertical = 4.dp)) {
                SoundCategory.values().forEach { CategoryRow(it) }
            }
        }
        Spacer(Modifier.height(20.dp))
        SectionHeader("About")
        Spacer(Modifier.height(8.dp))
        Card(
            modifier = Modifier.fillMaxWidth(),
            shape = RoundedCornerShape(20.dp),
            colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface)
        ) {
            Column(modifier = Modifier.padding(20.dp)) {
                Text("SoundGuard", style = MaterialTheme.typography.titleMedium)
                Spacer(Modifier.height(4.dp))
                Text(
                    text = "Environmental audio awareness for headphone users. " +
                        "Watch streams audio, phone runs YAMNet on-device, " +
                        "and the watch surfaces alerts with class-specific haptics. " +
                        "Includes RO-ALERT integration and a Gemini coach that can speak its replies aloud.",
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        }
        Spacer(Modifier.height(40.dp))
    }
}

@Composable
private fun HearingProfileCard(
    profile: HearingProfile,
    onName: (String) -> Unit,
    onAge: (AgeBracket) -> Unit,
    onEnvironment: (Environment) -> Unit,
    onHeadphoneHours: (Int) -> Unit,
    onConcern: (HearingConcern) -> Unit
) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(20.dp),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface)
    ) {
        Column(modifier = Modifier.padding(16.dp)) {
            // Local mirror so the user can keep typing without being interrupted by recompositions.
            var name by remember(profile.displayName) { mutableStateOf(profile.displayName.orEmpty()) }
            LaunchedEffect(name) {
                kotlinx.coroutines.delay(400)
                if (name != profile.displayName.orEmpty()) onName(name)
            }
            OutlinedTextField(
                value = name,
                onValueChange = { name = it },
                label = { Text("Preferred name (optional)") },
                singleLine = true,
                modifier = Modifier.fillMaxWidth(),
                shape = RoundedCornerShape(16.dp)
            )
            Spacer(Modifier.height(14.dp))
            ProfileChipGroup(
                title = "Age range",
                options = AgeBracket.values().toList(),
                selected = profile.ageBracket,
                labelOf = { it.label },
                onSelect = onAge
            )
            Spacer(Modifier.height(12.dp))
            ProfileChipGroup(
                title = "Typical environment",
                options = Environment.values().toList(),
                selected = profile.environment,
                labelOf = { it.label },
                onSelect = onEnvironment
            )
            Spacer(Modifier.height(12.dp))
            HeadphoneHoursStepper(
                hours = profile.headphoneHoursPerDay,
                onChange = onHeadphoneHours
            )
            Spacer(Modifier.height(12.dp))
            ProfileChipGroup(
                title = "Hearing context",
                options = HearingConcern.values().toList(),
                selected = profile.concern,
                labelOf = { it.label },
                onSelect = onConcern
            )
        }
    }
}

@Composable
private fun <T> ProfileChipGroup(
    title: String,
    options: List<T>,
    selected: T,
    labelOf: (T) -> String,
    onSelect: (T) -> Unit
) {
    Column {
        Text(
            text = title,
            style = MaterialTheme.typography.labelLarge,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
        Spacer(Modifier.height(6.dp))
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .horizontalScroll(rememberScrollState()),
            horizontalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            options.forEach { option ->
                FilterChip(
                    selected = option == selected,
                    onClick = { onSelect(option) },
                    label = { Text(labelOf(option)) },
                    colors = FilterChipDefaults.filterChipColors(
                        selectedContainerColor = MaterialTheme.colorScheme.primary,
                        selectedLabelColor = Color.White
                    )
                )
            }
        }
    }
}

@Composable
private fun HeadphoneHoursStepper(hours: Int, onChange: (Int) -> Unit) {
    Row(verticalAlignment = Alignment.CenterVertically) {
        Box(
            modifier = Modifier
                .size(40.dp)
                .clip(CircleShape)
                .background(MaterialTheme.colorScheme.primaryContainer),
            contentAlignment = Alignment.Center
        ) {
            Icon(
                Icons.Filled.Headphones,
                contentDescription = null,
                tint = MaterialTheme.colorScheme.onPrimaryContainer,
                modifier = Modifier.size(20.dp)
            )
        }
        Spacer(Modifier.width(12.dp))
        Column(modifier = Modifier.weight(1f)) {
            Text("Headphone time", style = MaterialTheme.typography.titleMedium)
            Text(
                text = if (hours == 0) "Not set" else "$hours h / day",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
        OutlinedButton(
            onClick = { onChange((hours - 1).coerceAtLeast(0)) },
            enabled = hours > 0
        ) { Text("−") }
        Spacer(Modifier.width(6.dp))
        OutlinedButton(onClick = { onChange(hours + 1) }) { Text("+") }
    }
}

@Composable
private fun ToggleRow(
    icon: ImageVector,
    title: String,
    subtitle: String,
    checked: Boolean,
    onChange: (Boolean) -> Unit
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp, vertical = 12.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Box(
            modifier = Modifier
                .size(40.dp)
                .clip(CircleShape)
                .background(MaterialTheme.colorScheme.primaryContainer),
            contentAlignment = Alignment.Center
        ) {
            Icon(
                icon,
                contentDescription = null,
                tint = MaterialTheme.colorScheme.onPrimaryContainer,
                modifier = Modifier.size(22.dp)
            )
        }
        Spacer(Modifier.width(14.dp))
        Column(modifier = Modifier.weight(1f)) {
            Text(title, style = MaterialTheme.typography.titleMedium)
            Text(
                text = subtitle,
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
        Switch(checked = checked, onCheckedChange = onChange)
    }
}

@Composable
private fun AccountCard(user: User, onSignOut: () -> Unit) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(24.dp),
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.primaryContainer
        )
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(20.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Box(
                modifier = Modifier
                    .size(56.dp)
                    .clip(CircleShape)
                    .background(MaterialTheme.colorScheme.primary),
                contentAlignment = Alignment.Center
            ) {
                Icon(
                    Icons.Filled.Person,
                    contentDescription = null,
                    tint = Color.White,
                    modifier = Modifier.size(32.dp)
                )
            }
            Spacer(Modifier.width(16.dp))
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = user.displayName,
                    style = MaterialTheme.typography.titleLarge,
                    color = MaterialTheme.colorScheme.onPrimaryContainer
                )
                val subtitle = user.email ?: if (user.isGuest) "Guest mode" else "Signed in"
                Text(
                    text = subtitle,
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onPrimaryContainer.copy(alpha = 0.8f)
                )
            }
            OutlinedButton(onClick = onSignOut) {
                Icon(
                    Icons.Filled.Logout,
                    contentDescription = null,
                    modifier = Modifier.size(18.dp)
                )
                Spacer(Modifier.width(6.dp))
                Text("Sign out")
            }
        }
    }
}

@Composable
private fun SectionHeader(text: String) {
    Text(
        text = text.uppercase(),
        style = MaterialTheme.typography.labelMedium,
        color = MaterialTheme.colorScheme.onSurfaceVariant,
        fontWeight = FontWeight.SemiBold,
        modifier = Modifier.padding(horizontal = 4.dp)
    )
}

@Composable
private fun GmailAuthCard(
    authorized: Boolean,
    onAuthorize: () -> Unit,
) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(20.dp),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface)
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(16.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Box(
                modifier = Modifier
                    .size(40.dp)
                    .clip(CircleShape)
                    .background(
                        if (authorized) MaterialTheme.colorScheme.primary
                        else MaterialTheme.colorScheme.errorContainer
                    ),
                contentAlignment = Alignment.Center
            ) {
                Icon(
                    Icons.Filled.VerifiedUser,
                    contentDescription = null,
                    tint = if (authorized) Color.White
                    else MaterialTheme.colorScheme.onErrorContainer,
                    modifier = Modifier.size(22.dp)
                )
            }
            Spacer(Modifier.width(14.dp))
            Column(modifier = Modifier.weight(1f)) {
                Text("Gmail SOS authorization", style = MaterialTheme.typography.titleMedium)
                Text(
                    text = if (authorized)
                        "Granted — SOS will be sent from your Gmail."
                    else
                        "Required for SOS. No password is stored — Google issues a token at runtime.",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
            Spacer(Modifier.width(12.dp))
            Button(
                onClick = onAuthorize,
                shape = RoundedCornerShape(16.dp),
            ) {
                Text(if (authorized) "Re-auth" else "Authorize")
            }
        }
    }
}

@Composable
private fun TrustedContactsCard(
    contacts: List<String>,
    onAdd: (String) -> Boolean,
    onRemove: (String) -> Unit,
) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(20.dp),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface)
    ) {
        Column(modifier = Modifier.padding(16.dp)) {
            var draft by remember { mutableStateOf("") }
            var error by remember { mutableStateOf(false) }

            Row(verticalAlignment = Alignment.CenterVertically) {
                OutlinedTextField(
                    value = draft,
                    onValueChange = {
                        draft = it
                        if (error) error = false
                    },
                    label = { Text("Email address") },
                    placeholder = { Text("name@example.com") },
                    singleLine = true,
                    isError = error,
                    supportingText = if (error) {
                        { Text("Enter a valid email") }
                    } else null,
                    leadingIcon = {
                        Icon(Icons.Filled.Email, contentDescription = null)
                    },
                    modifier = Modifier.weight(1f),
                    shape = RoundedCornerShape(16.dp),
                )
                Spacer(Modifier.width(8.dp))
                Button(
                    onClick = {
                        if (onAdd(draft)) {
                            draft = ""
                            error = false
                        } else {
                            error = true
                        }
                    },
                    enabled = draft.isNotBlank(),
                    shape = RoundedCornerShape(16.dp),
                ) {
                    Icon(Icons.Filled.Add, contentDescription = "Add", modifier = Modifier.size(20.dp))
                }
            }

            if (contacts.isEmpty()) {
                Spacer(Modifier.height(12.dp))
                Text(
                    text = "No trusted contacts yet. Add at least one so SOS can reach someone.",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            } else {
                Spacer(Modifier.height(8.dp))
                contacts.forEach { email ->
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(vertical = 4.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Box(
                            modifier = Modifier
                                .size(36.dp)
                                .clip(CircleShape)
                                .background(MaterialTheme.colorScheme.primaryContainer),
                            contentAlignment = Alignment.Center
                        ) {
                            Icon(
                                Icons.Filled.Email,
                                contentDescription = null,
                                tint = MaterialTheme.colorScheme.onPrimaryContainer,
                                modifier = Modifier.size(18.dp)
                            )
                        }
                        Spacer(Modifier.width(12.dp))
                        Text(
                            text = email,
                            style = MaterialTheme.typography.bodyMedium,
                            modifier = Modifier.weight(1f)
                        )
                        IconButton(onClick = { onRemove(email) }) {
                            Icon(
                                Icons.Filled.Close,
                                contentDescription = "Remove $email",
                                tint = MaterialTheme.colorScheme.error
                            )
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun CategoryRow(category: SoundCategory) {
    val palette = paletteFor(category.severity)
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp, vertical = 12.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Box(
            modifier = Modifier
                .size(40.dp)
                .clip(CircleShape)
                .background(palette.container),
            contentAlignment = Alignment.Center
        ) {
            Icon(
                imageVector = iconFor(category),
                contentDescription = null,
                tint = palette.accent,
                modifier = Modifier.size(22.dp)
            )
        }
        Spacer(Modifier.width(14.dp))
        Column(modifier = Modifier.weight(1f)) {
            Text(category.displayName, style = MaterialTheme.typography.titleMedium)
            Text(
                text = "Severity: ${category.severity.name.lowercase().replaceFirstChar { it.uppercase() }}",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
    }
}

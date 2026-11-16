package com.example.ui.screens

import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.PickVisualMediaRequest
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.Logout
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.AddPhotoAlternate
import androidx.compose.material.icons.filled.DarkMode
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.DoneAll
import androidx.compose.material.icons.filled.Explore
import androidx.compose.material.icons.filled.Link
import androidx.compose.material.icons.filled.Menu
import androidx.compose.material.icons.filled.Notifications
import androidx.compose.material.icons.filled.Search
import androidx.compose.material.icons.filled.VolumeUp
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Surface
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.example.data.model.AvatarPreset
import com.example.data.model.OnlineStatus
import com.example.ui.components.BadgeChipsRow
import com.example.ui.components.NexusAvatar
import com.example.ui.theme.NexusBlue
import com.example.ui.theme.NexusCyan
import com.example.ui.theme.NexusDanger
import com.example.ui.theme.NexusPurple
import com.example.ui.theme.NexusPurpleGlow
import com.example.ui.theme.StatusOnline
import com.example.ui.viewmodel.NexusUiState
import com.example.ui.viewmodel.NexusViewModel

@Composable
fun ServerDiscoveryScreen(
    state: NexusUiState,
    viewModel: NexusViewModel,
    showDrawerButton: Boolean,
    onOpenNavDrawer: () -> Unit,
    onCreateServerClick: () -> Unit,
    onJoinInviteClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    var query by remember { mutableStateOf("") }
    val myUid = state.currentUid.orEmpty()
    val filteredServers = remember(state.publicServers, query) {
        val q = query.trim().lowercase()
        state.publicServers.filter { srv ->
            q.isEmpty() || srv.name.lowercase().contains(q) || srv.description.lowercase().contains(q)
        }
    }

    Column(
        modifier = modifier
            .fillMaxSize()
            .background(MaterialTheme.colorScheme.surface)
    ) {
        Surface(color = MaterialTheme.colorScheme.surface, shadowElevation = 2.dp) {
            Row(
                modifier = Modifier.fillMaxWidth().padding(horizontal = 14.dp, vertical = 12.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                if (showDrawerButton) {
                    IconButton(onClick = onOpenNavDrawer) { Icon(Icons.Default.Menu, "Menu") }
                }
                Icon(Icons.Default.Explore, null, tint = NexusPurpleGlow)
                Spacer(Modifier.width(8.dp))
                Text("Discover Communities", style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.Bold)
            }
        }

        Column(modifier = Modifier.padding(14.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
            OutlinedTextField(
                value = query,
                onValueChange = { query = it },
                leadingIcon = { Icon(Icons.Default.Search, null) },
                label = { Text("Search public servers...") },
                singleLine = true,
                modifier = Modifier.fillMaxWidth().testTag("discovery_search_input")
            )
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp), modifier = Modifier.fillMaxWidth()) {
                Button(
                    onClick = onCreateServerClick,
                    colors = ButtonDefaults.buttonColors(containerColor = NexusPurple),
                    modifier = Modifier.weight(1f)
                ) {
                    Icon(Icons.Default.Add, null, modifier = Modifier.size(18.dp))
                    Spacer(Modifier.width(6.dp))
                    Text("Create Server")
                }
                OutlinedButton(
                    onClick = onJoinInviteClick,
                    modifier = Modifier.weight(1f)
                ) {
                    Icon(Icons.Default.Link, null, modifier = Modifier.size(18.dp))
                    Spacer(Modifier.width(6.dp))
                    Text("Use Invite")
                }
            }
        }

        LazyColumn(
            modifier = Modifier.weight(1f),
            contentPadding = PaddingValues(horizontal = 14.dp, vertical = 6.dp),
            verticalArrangement = Arrangement.spacedBy(10.dp)
        ) {
            if (filteredServers.isEmpty()) {
                item {
                    Surface(
                        color = MaterialTheme.colorScheme.surfaceContainerHigh,
                        shape = RoundedCornerShape(16.dp),
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Text(
                            text = if (query.isBlank()) "No public servers yet — create the first Nexus community!"
                            else "No public servers matched \"$query\".",
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            modifier = Modifier.padding(18.dp)
                        )
                    }
                }
            }
            items(filteredServers, key = { it.id }) { srv ->
                val isJoined = myUid in srv.memberUids
                val isBanned = myUid in srv.bannedUids
                Surface(
                    color = MaterialTheme.colorScheme.surfaceContainerHigh,
                    shape = RoundedCornerShape(16.dp),
                    modifier = Modifier
                        .fillMaxWidth()
                        .border(1.dp, MaterialTheme.colorScheme.outlineVariant, RoundedCornerShape(16.dp))
                ) {
                    Row(
                        modifier = Modifier.padding(14.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Box(
                            modifier = Modifier
                                .size(48.dp)
                                .clip(RoundedCornerShape(14.dp))
                                .background(Brush.linearGradient(listOf(NexusPurple, NexusBlue))),
                            contentAlignment = Alignment.Center
                        ) { Text(srv.iconEmoji, fontSize = 22.sp) }
                        Spacer(Modifier.width(12.dp))
                        Column(modifier = Modifier.weight(1f)) {
                            Text(srv.name, style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
                            if (srv.description.isNotBlank()) {
                                Text(
                                    srv.description,
                                    style = MaterialTheme.typography.bodySmall,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                                    maxLines = 2,
                                    overflow = TextOverflow.Ellipsis
                                )
                            }
                            Text(
                                "${srv.memberCount} members • Invite ${srv.inviteCode}",
                                style = MaterialTheme.typography.labelSmall,
                                color = NexusCyan
                            )
                        }
                        Spacer(Modifier.width(8.dp))
                        Button(
                            onClick = { viewModel.joinPublicServer(srv) },
                            enabled = !isBanned,
                            colors = ButtonDefaults.buttonColors(
                                containerColor = if (isJoined) NexusBlue else StatusOnline,
                                contentColor = if (isJoined) Color.White else Color.Black
                            )
                        ) {
                            Text(
                                when {
                                    isBanned -> "Banned"
                                    isJoined -> "Open"
                                    else -> "Join"
                                }
                            )
                        }
                    }
                }
            }
        }
    }
}

@Composable
fun NotificationsScreen(
    state: NexusUiState,
    viewModel: NexusViewModel,
    showDrawerButton: Boolean,
    onOpenNavDrawer: () -> Unit,
    modifier: Modifier = Modifier
) {
    Column(
        modifier = modifier
            .fillMaxSize()
            .background(MaterialTheme.colorScheme.surface)
    ) {
        Surface(color = MaterialTheme.colorScheme.surface, shadowElevation = 2.dp) {
            Row(
                modifier = Modifier.fillMaxWidth().padding(horizontal = 14.dp, vertical = 12.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                if (showDrawerButton) {
                    IconButton(onClick = onOpenNavDrawer) { Icon(Icons.Default.Menu, "Menu") }
                }
                Icon(Icons.Default.Notifications, null, tint = NexusCyan)
                Spacer(Modifier.width(8.dp))
                Text("Notifications", style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.Bold)
            }
        }

        LazyColumn(
            modifier = Modifier.weight(1f),
            contentPadding = PaddingValues(14.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            if (state.notifications.isEmpty()) {
                item {
                    Text(
                        "You're all caught up! Friend requests, role promotions, and server updates appear here.",
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.padding(8.dp)
                    )
                }
            }
            items(state.notifications, key = { it.id }) { notif ->
                Surface(
                    color = if (notif.isRead) MaterialTheme.colorScheme.surfaceContainerLow
                    else MaterialTheme.colorScheme.surfaceContainerHigh,
                    shape = RoundedCornerShape(14.dp),
                    modifier = Modifier
                        .fillMaxWidth()
                        .border(
                            1.dp,
                            if (notif.isRead) MaterialTheme.colorScheme.outlineVariant else NexusPurpleGlow,
                            RoundedCornerShape(14.dp)
                        )
                ) {
                    Row(
                        modifier = Modifier.padding(12.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Column(modifier = Modifier.weight(1f)) {
                            Text(notif.title, style = MaterialTheme.typography.titleSmall, fontWeight = FontWeight.Bold)
                            Spacer(Modifier.height(2.dp))
                            Text(notif.body, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                        }
                        if (!notif.isRead) {
                            IconButton(onClick = { viewModel.markNotificationRead(notif.id) }) {
                                Icon(Icons.Default.DoneAll, "Mark read", tint = StatusOnline)
                            }
                        }
                        IconButton(onClick = { viewModel.deleteNotification(notif.id) }) {
                            Icon(Icons.Default.Delete, "Delete notification", tint = MaterialTheme.colorScheme.onSurfaceVariant)
                        }
                    }
                }
            }
        }
    }
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
fun ProfileAndSettingsScreen(
    state: NexusUiState,
    viewModel: NexusViewModel,
    showDrawerButton: Boolean,
    onOpenNavDrawer: () -> Unit,
    onSignOut: () -> Unit,
    modifier: Modifier = Modifier
) {
    val profile = state.currentProfile
    val isDark by viewModel.isDarkTheme.collectAsStateWithLifecycle()
    val isSound by viewModel.isSoundEnabled.collectAsStateWithLifecycle()
    val isHaptics by viewModel.isHapticsEnabled.collectAsStateWithLifecycle()
    val isNotifications by viewModel.isNotificationsEnabled.collectAsStateWithLifecycle()
    val isAutoPlayVoice by viewModel.isAutoPlayVoiceEnabled.collectAsStateWithLifecycle()

    var displayName by remember(profile?.displayName) { mutableStateOf(profile?.displayName ?: "") }
    var gamerTag by remember(profile?.gamerTag) { mutableStateOf(profile?.gamerTag ?: "") }
    var bio by remember(profile?.bio) { mutableStateOf(profile?.bio ?: "") }
    var statusText by remember(profile?.customStatusText) { mutableStateOf(profile?.customStatusText ?: "") }
    var avatarId by remember(profile?.avatarId) { mutableStateOf(profile?.avatarId ?: AvatarPreset.CYBER_KNIGHT.id) }
    var customAvatarData by remember(profile?.customAvatarData) { mutableStateOf(profile?.customAvatarData ?: "") }
    var onlineStatus by remember(profile?.onlineStatus) { mutableStateOf(profile?.statusEnum ?: OnlineStatus.ONLINE) }

    val avatarPicker = rememberLauncherForActivityResult(ActivityResultContracts.PickVisualMedia()) { uri ->
        if (uri != null) {
            viewModel.uploadCustomAvatar(uri) { b64 -> customAvatarData = b64 }
        }
    }

    Column(
        modifier = modifier
            .fillMaxSize()
            .background(MaterialTheme.colorScheme.surface)
    ) {
        Surface(color = MaterialTheme.colorScheme.surface, shadowElevation = 2.dp) {
            Row(
                modifier = Modifier.fillMaxWidth().padding(horizontal = 14.dp, vertical = 12.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                if (showDrawerButton) {
                    IconButton(onClick = onOpenNavDrawer) { Icon(Icons.Default.Menu, "Menu") }
                }
                Text("Profile & User Settings", style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.Bold)
            }
        }

        Column(
            modifier = Modifier
                .weight(1f)
                .verticalScroll(rememberScrollState())
                .padding(16.dp),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            Column(
                modifier = Modifier.widthIn(max = 640.dp).fillMaxWidth(),
                verticalArrangement = Arrangement.spacedBy(16.dp)
            ) {
                // Custom Profile Card
                Surface(
                    color = MaterialTheme.colorScheme.surfaceContainerHigh,
                    shape = RoundedCornerShape(20.dp),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Column(modifier = Modifier.padding(18.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
                        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(14.dp)) {
                            NexusAvatar(
                                avatarId = avatarId,
                                customAvatarData = customAvatarData,
                                status = onlineStatus,
                                size = 68.dp
                            )
                            Column(modifier = Modifier.weight(1f)) {
                                Text(displayName.ifBlank { "Nexus Player" }, style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.Bold)
                                Text("@${gamerTag.ifBlank { "player" }} • ${onlineStatus.label}", style = MaterialTheme.typography.bodySmall, color = NexusCyan)
                            }
                            OutlinedButton(onClick = {
                                avatarPicker.launch(PickVisualMediaRequest(ActivityResultContracts.PickVisualMedia.ImageOnly))
                            }) {
                                Icon(Icons.Default.AddPhotoAlternate, null, modifier = Modifier.size(18.dp))
                                Spacer(Modifier.width(6.dp))
                                Text("Photo")
                            }
                        }

                        if (profile != null) {
                            BadgeChipsRow(profile.badges)
                        }

                        Text("Online Presence", style = MaterialTheme.typography.labelMedium)
                        FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                            OnlineStatus.entries.forEach { st ->
                                FilterChip(
                                    selected = onlineStatus == st,
                                    onClick = {
                                        onlineStatus = st
                                        viewModel.updateOnlineStatus(st)
                                    },
                                    label = { Text(st.label) }
                                )
                            }
                        }

                        Text("Crest Icon", style = MaterialTheme.typography.labelMedium)
                        FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                            AvatarPreset.entries.forEach { preset ->
                                Box(
                                    modifier = Modifier
                                        .size(40.dp)
                                        .clip(CircleShape)
                                        .background(if (avatarId == preset.id) NexusPurple.copy(alpha = 0.3f) else MaterialTheme.colorScheme.surface)
                                        .clickable { avatarId = preset.id; customAvatarData = "" },
                                    contentAlignment = Alignment.Center
                                ) { Text(preset.glyph, fontSize = 18.sp) }
                            }
                        }

                        OutlinedTextField(
                            value = displayName,
                            onValueChange = { if (it.length <= 40) displayName = it },
                            label = { Text("Display Name") },
                            singleLine = true,
                            modifier = Modifier.fillMaxWidth()
                        )
                        OutlinedTextField(
                            value = gamerTag,
                            onValueChange = { if (it.length <= 30) gamerTag = it },
                            label = { Text("GamerTag") },
                            singleLine = true,
                            modifier = Modifier.fillMaxWidth()
                        )
                        OutlinedTextField(
                            value = statusText,
                            onValueChange = { if (it.length <= 80) statusText = it },
                            label = { Text("Custom Status") },
                            singleLine = true,
                            modifier = Modifier.fillMaxWidth()
                        )
                        OutlinedTextField(
                            value = bio,
                            onValueChange = { if (it.length <= 220) bio = it },
                            label = { Text("Bio") },
                            maxLines = 3,
                            modifier = Modifier.fillMaxWidth()
                        )
                        Button(
                            onClick = {
                                viewModel.updateProfile(displayName, gamerTag, bio, avatarId, customAvatarData, onlineStatus, statusText)
                            },
                            modifier = Modifier.fillMaxWidth().testTag("save_profile_button")
                        ) { Text("Save Profile Changes") }
                    }
                }

                // App Appearance & Voice/Audio Settings
                Surface(
                    color = MaterialTheme.colorScheme.surfaceContainerHigh,
                    shape = RoundedCornerShape(20.dp),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Column(modifier = Modifier.padding(18.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
                        Text("Appearance & Voice Settings", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)

                        SettingToggleRow(
                            icon = Icons.Default.DarkMode,
                            title = "Dark Theme (Obsidian Purple & Blue)",
                            subtitle = "Toggle between dark gaming mode and daylight mode",
                            checked = isDark,
                            onCheckedChange = viewModel::setDarkTheme,
                            testTag = "settings_dark_theme_switch"
                        )
                        SettingToggleRow(
                            icon = Icons.Default.VolumeUp,
                            title = "UI Sound Effects",
                            subtitle = "Play subtle audio chimes for interactions",
                            checked = isSound,
                            onCheckedChange = viewModel::setSoundEnabled
                        )
                        SettingToggleRow(
                            icon = Icons.Default.VolumeUp,
                            title = "Haptic Feedback",
                            subtitle = "Tactile pulse on voice and chat actions",
                            checked = isHaptics,
                            onCheckedChange = viewModel::setHapticsEnabled
                        )
                        SettingToggleRow(
                            icon = Icons.Default.Notifications,
                            title = "In-App Alerts",
                            subtitle = "Notify on friend requests and role updates",
                            checked = isNotifications,
                            onCheckedChange = viewModel::setNotificationsEnabled
                        )
                        SettingToggleRow(
                            icon = Icons.Default.VolumeUp,
                            title = "Auto-Ready Voice Stage",
                            subtitle = "Keep voice stage controls pinned in voice channels",
                            checked = isAutoPlayVoice,
                            onCheckedChange = viewModel::setAutoPlayVoiceEnabled
                        )
                    }
                }

                Button(
                    onClick = onSignOut,
                    colors = ButtonDefaults.buttonColors(containerColor = NexusDanger),
                    modifier = Modifier.fillMaxWidth().height(50.dp).testTag("logout_button")
                ) {
                    Icon(Icons.AutoMirrored.Filled.Logout, null)
                    Spacer(Modifier.width(8.dp))
                    Text("Log Out")
                }
            }
        }
    }
}

@Composable
private fun SettingToggleRow(
    icon: androidx.compose.ui.graphics.vector.ImageVector,
    title: String,
    subtitle: String,
    checked: Boolean,
    onCheckedChange: (Boolean) -> Unit,
    testTag: String? = null
) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Icon(icon, null, tint = NexusPurpleGlow, modifier = Modifier.size(22.dp))
        Spacer(Modifier.width(12.dp))
        Column(modifier = Modifier.weight(1f)) {
            Text(title, style = MaterialTheme.typography.labelLarge)
            Text(subtitle, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
        Switch(
            checked = checked,
            onCheckedChange = onCheckedChange,
            modifier = if (testTag != null) Modifier.testTag(testTag) else Modifier
        )
    }
}

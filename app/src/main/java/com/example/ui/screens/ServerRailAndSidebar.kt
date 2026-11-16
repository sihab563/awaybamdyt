package com.example.ui.screens

import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.animateDpAsState
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ExitToApp
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Campaign
import androidx.compose.material.icons.filled.CallEnd
import androidx.compose.material.icons.filled.ChatBubble
import androidx.compose.material.icons.filled.ContentCopy
import androidx.compose.material.icons.filled.Explore
import androidx.compose.material.icons.filled.GraphicEq
import androidx.compose.material.icons.filled.Link
import androidx.compose.material.icons.filled.Mic
import androidx.compose.material.icons.filled.MicOff
import androidx.compose.material.icons.filled.Notifications
import androidx.compose.material.icons.filled.Person
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material.icons.filled.Tag
import androidx.compose.material.icons.filled.VolumeUp
import androidx.compose.material3.Badge
import androidx.compose.material3.BadgedBox
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
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
import androidx.compose.ui.platform.LocalClipboardManager
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.data.model.ChannelType
import com.example.data.model.NexusServer
import com.example.data.model.OnlineStatus
import com.example.data.model.ServerChannel
import com.example.data.model.ServerRole
import com.example.data.model.VoiceMember
import com.example.ui.components.NexusAvatar
import com.example.ui.theme.NexusBlue
import com.example.ui.theme.NexusCyan
import com.example.ui.theme.NexusDanger
import com.example.ui.theme.NexusPurple
import com.example.ui.theme.NexusPurpleGlow
import com.example.ui.theme.StatusOnline
import com.example.ui.viewmodel.ActiveVoiceSession
import com.example.ui.viewmodel.NexusSection
import com.example.ui.viewmodel.NexusUiState

@Composable
fun ServerRail(
    state: NexusUiState,
    onHomeClick: () -> Unit,
    onServerClick: (String) -> Unit,
    onCreateServerClick: () -> Unit,
    onJoinInviteClick: () -> Unit,
    onDiscoveryClick: () -> Unit,
    onNotificationsClick: () -> Unit,
    onProfileClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    Column(
        modifier = modifier
            .width(74.dp)
            .fillMaxHeight()
            .background(MaterialTheme.colorScheme.surfaceContainerLowest)
            .padding(vertical = 10.dp),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        // NEXUS Home / Direct Messages Icon
        val homeActive = state.activeSection == NexusSection.HOME_DMS
        RailIconTile(
            selected = homeActive,
            badgeCount = 0,
            onClick = onHomeClick,
            modifier = Modifier.testTag("rail_home_button")
        ) {
            Icon(
                Icons.Default.ChatBubble,
                contentDescription = "Home & Direct Messages",
                tint = Color.White,
                modifier = Modifier.size(24.dp)
            )
        }

        HorizontalDivider(
            modifier = Modifier
                .padding(vertical = 10.dp)
                .width(34.dp),
            color = MaterialTheme.colorScheme.outlineVariant
        )

        // Joined Servers List
        LazyColumn(
            modifier = Modifier
                .weight(1f)
                .fillMaxWidth(),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(10.dp)
        ) {
            items(state.joinedServers, key = { it.id }) { server ->
                val isSelected = state.activeSection == NexusSection.SERVER &&
                    state.selectedServerId == server.id
                RailIconTile(
                    selected = isSelected,
                    badgeCount = 0,
                    onClick = { onServerClick(server.id) },
                    modifier = Modifier.testTag("rail_server_${server.id}")
                ) {
                    Text(text = server.iconEmoji, fontSize = 22.sp)
                }
            }

            item {
                Spacer(Modifier.height(4.dp))
                RailActionButton(
                    icon = Icons.Default.Add,
                    contentDescription = "Create Server",
                    tint = StatusOnline,
                    onClick = onCreateServerClick,
                    modifier = Modifier.testTag("rail_create_server")
                )
            }

            item {
                RailActionButton(
                    icon = Icons.Default.Link,
                    contentDescription = "Join via Invite",
                    tint = NexusBlue,
                    onClick = onJoinInviteClick,
                    modifier = Modifier.testTag("rail_join_invite")
                )
            }

            item {
                RailActionButton(
                    icon = Icons.Default.Explore,
                    contentDescription = "Discover Servers",
                    tint = if (state.activeSection == NexusSection.DISCOVERY) Color.White else NexusPurpleGlow,
                    active = state.activeSection == NexusSection.DISCOVERY,
                    onClick = onDiscoveryClick,
                    modifier = Modifier.testTag("rail_discovery")
                )
            }
        }

        HorizontalDivider(
            modifier = Modifier
                .padding(vertical = 8.dp)
                .width(34.dp),
            color = MaterialTheme.colorScheme.outlineVariant
        )

        // Notifications Bell + Profile Shortcuts
        BadgedBox(
            badge = {
                if (state.unreadNotificationsCount > 0) {
                    Badge(containerColor = NexusDanger) {
                        Text(state.unreadNotificationsCount.coerceAtMost(99).toString())
                    }
                }
            }
        ) {
            RailActionButton(
                icon = Icons.Default.Notifications,
                contentDescription = "Notifications",
                tint = if (state.activeSection == NexusSection.NOTIFICATIONS) Color.White else NexusCyan,
                active = state.activeSection == NexusSection.NOTIFICATIONS,
                onClick = onNotificationsClick,
                modifier = Modifier.testTag("rail_notifications")
            )
        }

        Spacer(Modifier.height(8.dp))

        RailActionButton(
            icon = Icons.Default.Person,
            contentDescription = "User Profile & Settings",
            tint = if (state.activeSection == NexusSection.PROFILE_SETTINGS) Color.White else MaterialTheme.colorScheme.onSurfaceVariant,
            active = state.activeSection == NexusSection.PROFILE_SETTINGS,
            onClick = onProfileClick,
            modifier = Modifier.testTag("rail_profile")
        )
    }
}

@Composable
private fun RailIconTile(
    selected: Boolean,
    badgeCount: Int,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    content: @Composable () -> Unit
) {
    val cornerRadius by animateDpAsState(
        targetValue = if (selected) 16.dp else 25.dp,
        label = "rail_corner"
    )
    val pillHeight by animateDpAsState(
        targetValue = if (selected) 36.dp else 8.dp,
        label = "rail_pill"
    )

    Row(
        modifier = modifier.fillMaxWidth(),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Box(
            modifier = Modifier
                .width(4.dp)
                .height(if (selected) pillHeight else 0.dp)
                .clip(RoundedCornerShape(topEnd = 4.dp, bottomEnd = 4.dp))
                .background(if (selected) NexusPurpleGlow else Color.Transparent)
        )
        Spacer(Modifier.width(7.dp))
        Box(
            modifier = Modifier
                .size(50.dp)
                .clip(RoundedCornerShape(cornerRadius))
                .background(
                    if (selected) Brush.linearGradient(listOf(NexusPurple, NexusBlue))
                    else Brush.linearGradient(
                        listOf(
                            MaterialTheme.colorScheme.surfaceContainerHigh,
                            MaterialTheme.colorScheme.surfaceContainerHigh
                        )
                    )
                )
                .clickable(onClick = onClick),
            contentAlignment = Alignment.Center
        ) {
            content()
        }
    }
}

@Composable
private fun RailActionButton(
    icon: androidx.compose.ui.graphics.vector.ImageVector,
    contentDescription: String,
    tint: Color,
    active: Boolean = false,
    onClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    val bg by animateColorAsState(
        targetValue = if (active) NexusPurple else MaterialTheme.colorScheme.surfaceContainerHigh,
        label = "rail_btn_bg"
    )
    Box(
        modifier = modifier
            .size(48.dp)
            .clip(CircleShape)
            .background(bg)
            .clickable(onClick = onClick),
        contentAlignment = Alignment.Center
    ) {
        Icon(icon, contentDescription = contentDescription, tint = tint, modifier = Modifier.size(22.dp))
    }
}

@Composable
fun ChannelSidebar(
    state: NexusUiState,
    server: NexusServer?,
    channels: List<ServerChannel>,
    voiceMembers: List<VoiceMember>,
    activeVoiceSession: ActiveVoiceSession?,
    onSelectChannel: (ServerChannel) -> Unit,
    onCreateChannelClick: () -> Unit,
    onEditChannelClick: (ServerChannel) -> Unit,
    onOpenServerSettings: () -> Unit,
    onLeaveServer: () -> Unit,
    onToggleMute: () -> Unit,
    onDisconnectVoice: () -> Unit,
    onOpenUserSettings: () -> Unit,
    onShowSnack: (String) -> Unit,
    modifier: Modifier = Modifier
) {
    val clipboard = LocalClipboardManager.current
    var headerMenuExpanded by remember { mutableStateOf(false) }

    Column(
        modifier = modifier
            .fillMaxHeight()
            .background(MaterialTheme.colorScheme.surfaceContainerLow)
    ) {
        if (server == null) {
            // Empty Server Selector Prompt
            Column(
                modifier = Modifier
                    .weight(1f)
                    .padding(16.dp),
                verticalArrangement = Arrangement.Center,
                horizontalAlignment = Alignment.CenterHorizontally
            ) {
                Text(
                    "NEXUS",
                    style = MaterialTheme.typography.titleLarge,
                    color = NexusPurpleGlow,
                    fontWeight = FontWeight.ExtraBold
                )
                Spacer(Modifier.height(6.dp))
                Text(
                    "Select a server from the rail, create your own community, or explore Public Discovery.",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        } else {
            val myUid = state.currentUid.orEmpty()
            val myRole = server.roleFor(myUid)
            val isAdminOrOwner = myRole == ServerRole.OWNER || myRole == ServerRole.ADMIN

            // Server Header
            Surface(
                color = MaterialTheme.colorScheme.surfaceContainerLow,
                shadowElevation = 2.dp,
                modifier = Modifier
                    .fillMaxWidth()
                    .clickable { headerMenuExpanded = true }
            ) {
                Row(
                    modifier = Modifier.padding(horizontal = 14.dp, vertical = 14.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(server.iconEmoji, fontSize = 20.sp)
                    Spacer(Modifier.width(8.dp))
                    Column(modifier = Modifier.weight(1f)) {
                        Text(
                            text = server.name,
                            style = MaterialTheme.typography.titleMedium,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis
                        )
                        Text(
                            text = "${server.memberCount} members • ${server.visibilityEnum.label}",
                            style = MaterialTheme.typography.labelSmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                    IconButton(
                        onClick = { headerMenuExpanded = true },
                        modifier = Modifier
                            .size(36.dp)
                            .testTag("server_header_menu_button")
                    ) {
                        Icon(Icons.Default.Settings, "Server Menu", modifier = Modifier.size(18.dp))
                    }

                    DropdownMenu(
                        expanded = headerMenuExpanded,
                        onDismissRequest = { headerMenuExpanded = false }
                    ) {
                        DropdownMenuItem(
                            text = { Text("Copy Invite (${server.inviteCode})") },
                            leadingIcon = { Icon(Icons.Default.ContentCopy, null) },
                            onClick = {
                                headerMenuExpanded = false
                                clipboard.setText(AnnotatedString(server.inviteCode))
                                onShowSnack("Copied invite code: ${server.inviteCode}")
                            }
                        )
                        if (isAdminOrOwner) {
                            DropdownMenuItem(
                                text = { Text("Create Channel") },
                                leadingIcon = { Icon(Icons.Default.Add, null) },
                                onClick = {
                                    headerMenuExpanded = false
                                    onCreateChannelClick()
                                }
                            )
                            DropdownMenuItem(
                                text = { Text("Server Settings & Moderation") },
                                leadingIcon = { Icon(Icons.Default.Settings, null) },
                                onClick = {
                                    headerMenuExpanded = false
                                    onOpenServerSettings()
                                }
                            )
                        }
                        if (myRole != ServerRole.OWNER) {
                            DropdownMenuItem(
                                text = { Text("Leave Server", color = MaterialTheme.colorScheme.error) },
                                leadingIcon = {
                                    Icon(Icons.AutoMirrored.Filled.ExitToApp, null, tint = MaterialTheme.colorScheme.error)
                                },
                                onClick = {
                                    headerMenuExpanded = false
                                    onLeaveServer()
                                }
                            )
                        }
                    }
                }
            }

            HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant)

            // Invite Pill
            Surface(
                color = MaterialTheme.colorScheme.surfaceContainerHigh,
                shape = RoundedCornerShape(10.dp),
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 10.dp, vertical = 8.dp)
                    .clickable {
                        clipboard.setText(AnnotatedString(server.inviteCode))
                        onShowSnack("Invite copied: ${server.inviteCode}")
                    }
            ) {
                Row(
                    modifier = Modifier.padding(horizontal = 10.dp, vertical = 7.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.SpaceBetween
                ) {
                    Text(
                        text = "Invite: ${server.inviteCode}",
                        style = MaterialTheme.typography.labelMedium,
                        color = NexusCyan
                    )
                    Icon(Icons.Default.ContentCopy, "Copy invite", modifier = Modifier.size(14.dp), tint = NexusCyan)
                }
            }

            // Grouped Categories & Channels
            val grouped = remember(channels) { channels.groupBy { it.categoryName } }
            LazyColumn(
                modifier = Modifier
                    .weight(1f)
                    .fillMaxWidth()
                    .padding(horizontal = 8.dp),
                verticalArrangement = Arrangement.spacedBy(2.dp)
            ) {
                grouped.forEach { (category, categoryChannels) ->
                    item(key = "cat_$category") {
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(start = 6.dp, end = 4.dp, top = 12.dp, bottom = 4.dp),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.SpaceBetween
                        ) {
                            Text(
                                text = category.uppercase(),
                                style = MaterialTheme.typography.labelSmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                                fontWeight = FontWeight.Bold
                            )
                            if (isAdminOrOwner) {
                                Icon(
                                    Icons.Default.Add,
                                    contentDescription = "Add channel",
                                    tint = MaterialTheme.colorScheme.onSurfaceVariant,
                                    modifier = Modifier
                                        .size(18.dp)
                                        .clickable(onClick = onCreateChannelClick)
                                )
                            }
                        }
                    }

                    items(categoryChannels, key = { it.id }) { channel ->
                        val isSelected = state.selectedChannelId == channel.id
                        val isConnectedHere = activeVoiceSession?.channelId == channel.id
                        val chIcon = when (channel.typeEnum) {
                            ChannelType.TEXT -> Icons.Default.Tag
                            ChannelType.VOICE -> Icons.Default.VolumeUp
                            ChannelType.ANNOUNCEMENT -> Icons.Default.Campaign
                        }

                        Column(modifier = Modifier.fillMaxWidth()) {
                            Row(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .clip(RoundedCornerShape(10.dp))
                                    .background(
                                        when {
                                            isSelected -> NexusPurple.copy(alpha = 0.22f)
                                            isConnectedHere -> StatusOnline.copy(alpha = 0.14f)
                                            else -> Color.Transparent
                                        }
                                    )
                                    .clickable { onSelectChannel(channel) }
                                    .padding(horizontal = 10.dp, vertical = 9.dp)
                                    .testTag("channel_row_${channel.id}"),
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Icon(
                                    chIcon,
                                    contentDescription = channel.typeEnum.label,
                                    modifier = Modifier.size(18.dp),
                                    tint = when {
                                        isConnectedHere -> StatusOnline
                                        isSelected -> NexusPurpleGlow
                                        else -> MaterialTheme.colorScheme.onSurfaceVariant
                                    }
                                )
                                Spacer(Modifier.width(8.dp))
                                Text(
                                    text = channel.name,
                                    style = MaterialTheme.typography.bodyMedium,
                                    fontWeight = if (isSelected) FontWeight.SemiBold else FontWeight.Normal,
                                    color = if (isSelected) MaterialTheme.colorScheme.onSurface
                                    else MaterialTheme.colorScheme.onSurfaceVariant,
                                    maxLines = 1,
                                    overflow = TextOverflow.Ellipsis,
                                    modifier = Modifier.weight(1f)
                                )
                                if (isAdminOrOwner && isSelected) {
                                    IconButton(
                                        onClick = { onEditChannelClick(channel) },
                                        modifier = Modifier.size(24.dp)
                                    ) {
                                        Icon(
                                            Icons.Default.Settings,
                                            "Edit channel",
                                            modifier = Modifier.size(14.dp),
                                            tint = MaterialTheme.colorScheme.onSurfaceVariant
                                        )
                                    }
                                }
                            }

                            // Active voice participants listed directly below voice channel
                            if (channel.typeEnum == ChannelType.VOICE) {
                                val roomMembers = voiceMembers.filter { it.channelId == channel.id }
                                roomMembers.forEach { vm ->
                                    Row(
                                        modifier = Modifier
                                            .fillMaxWidth()
                                            .padding(start = 30.dp, end = 8.dp, top = 3.dp, bottom = 3.dp),
                                        verticalAlignment = Alignment.CenterVertically
                                    ) {
                                        NexusAvatar(
                                            avatarId = vm.avatarId,
                                            size = 22.dp,
                                            isSpeaking = vm.isSpeaking
                                        )
                                        Spacer(Modifier.width(8.dp))
                                        Text(
                                            text = vm.displayName,
                                            style = MaterialTheme.typography.bodySmall,
                                            color = if (vm.isSpeaking) StatusOnline else MaterialTheme.colorScheme.onSurfaceVariant,
                                            maxLines = 1,
                                            overflow = TextOverflow.Ellipsis,
                                            modifier = Modifier.weight(1f)
                                        )
                                        if (vm.isMuted) {
                                            Icon(
                                                Icons.Default.MicOff,
                                                contentDescription = "Muted",
                                                modifier = Modifier.size(14.dp),
                                                tint = NexusDanger
                                            )
                                        }
                                    }
                                }
                            }
                        }
                    }
                }
            }
        }

        // Active Voice Connection Banner (Discord style)
        if (activeVoiceSession != null) {
            Surface(
                color = StatusOnline.copy(alpha = 0.14f),
                modifier = Modifier
                    .fillMaxWidth()
                    .border(1.dp, StatusOnline.copy(alpha = 0.35f))
            ) {
                Row(
                    modifier = Modifier.padding(horizontal = 10.dp, vertical = 8.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Icon(Icons.Default.GraphicEq, null, tint = StatusOnline, modifier = Modifier.size(18.dp))
                    Spacer(Modifier.width(8.dp))
                    Column(modifier = Modifier.weight(1f)) {
                        Text(
                            text = "Voice Connected",
                            style = MaterialTheme.typography.labelMedium,
                            color = StatusOnline,
                            fontWeight = FontWeight.Bold
                        )
                        Text(
                            text = "${activeVoiceSession.channelName} • ${activeVoiceSession.serverName}",
                            style = MaterialTheme.typography.labelSmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis
                        )
                    }
                    IconButton(onClick = onDisconnectVoice, modifier = Modifier.size(32.dp)) {
                        Icon(Icons.Default.CallEnd, "Disconnect voice", tint = NexusDanger, modifier = Modifier.size(18.dp))
                    }
                }
            }
        }

        // Bottom User Status Bar
        val profile = state.currentProfile
        Surface(
            color = MaterialTheme.colorScheme.surfaceContainerLowest,
            modifier = Modifier.fillMaxWidth()
        ) {
            Row(
                modifier = Modifier.padding(horizontal = 10.dp, vertical = 8.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                NexusAvatar(
                    avatarId = profile?.avatarId ?: "cyber_knight",
                    customAvatarData = profile?.customAvatarData.orEmpty(),
                    status = profile?.statusEnum ?: OnlineStatus.ONLINE,
                    size = 34.dp
                )
                Spacer(Modifier.width(8.dp))
                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        text = profile?.displayName ?: "Nexus User",
                        style = MaterialTheme.typography.labelLarge,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                    Text(
                        text = "@${profile?.gamerTag ?: "player"}",
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        maxLines = 1
                    )
                }
                if (activeVoiceSession != null) {
                    IconButton(onClick = onToggleMute, modifier = Modifier.size(34.dp)) {
                        Icon(
                            if (activeVoiceSession.isMuted) Icons.Default.MicOff else Icons.Default.Mic,
                            contentDescription = "Toggle mic",
                            tint = if (activeVoiceSession.isMuted) NexusDanger else StatusOnline,
                            modifier = Modifier.size(18.dp)
                        )
                    }
                }
                IconButton(onClick = onOpenUserSettings, modifier = Modifier.size(34.dp)) {
                    Icon(Icons.Default.Settings, "User settings", modifier = Modifier.size(18.dp))
                }
            }
        }
    }
}

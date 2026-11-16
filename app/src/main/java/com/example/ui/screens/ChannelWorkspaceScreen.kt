package com.example.ui.screens

import android.Manifest
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.PickVisualMediaRequest
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.AnimatedVisibility
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
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.Send
import androidx.compose.material.icons.filled.AddPhotoAlternate
import androidx.compose.material.icons.filled.AttachFile
import androidx.compose.material.icons.filled.CallEnd
import androidx.compose.material.icons.filled.Campaign
import androidx.compose.material.icons.filled.Group
import androidx.compose.material.icons.filled.Headset
import androidx.compose.material.icons.filled.HeadsetOff
import androidx.compose.material.icons.filled.Lock
import androidx.compose.material.icons.filled.Menu
import androidx.compose.material.icons.filled.Mic
import androidx.compose.material.icons.filled.MicOff
import androidx.compose.material.icons.filled.RadioButtonChecked
import androidx.compose.material.icons.filled.Tag
import androidx.compose.material.icons.filled.VolumeUp
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
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
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.example.data.model.ChannelMessage
import com.example.data.model.ChannelType
import com.example.data.model.GamerProfile
import com.example.data.model.NexusServer
import com.example.data.model.OnlineStatus
import com.example.data.model.ServerChannel
import com.example.data.model.ServerMember
import com.example.data.model.ServerRole
import com.example.data.model.VoiceMember
import com.example.ui.audio.VoiceEngine
import com.example.ui.components.BadgeChipsRow
import com.example.ui.components.DiscordMessageCard
import com.example.ui.components.MessageUiModel
import com.example.ui.components.NexusAvatar
import com.example.ui.components.ReplyAndAttachmentPreview
import com.example.ui.components.RoleBadgePill
import com.example.ui.theme.NexusBlue
import com.example.ui.theme.NexusCyan
import com.example.ui.theme.NexusDanger
import com.example.ui.theme.NexusPurple
import com.example.ui.theme.NexusPurpleGlow
import com.example.ui.theme.StatusOnline
import com.example.ui.viewmodel.NexusUiState
import com.example.ui.viewmodel.NexusViewModel

@Composable
fun ChannelWorkspaceScreen(
    state: NexusUiState,
    viewModel: NexusViewModel,
    showDrawerButton: Boolean,
    showMembersInline: Boolean,
    onOpenNavDrawer: () -> Unit,
    onToggleMembersSheet: () -> Unit,
    onInspectMember: (ServerMember) -> Unit,
    modifier: Modifier = Modifier
) {
    val server = state.activeServer
    val channel = state.activeChannel
    if (server == null || channel == null) {
        Box(
            modifier = modifier.fillMaxSize(),
            contentAlignment = Alignment.Center
        ) {
            Column(horizontalAlignment = Alignment.CenterHorizontally, modifier = Modifier.padding(24.dp)) {
                Text("Select or create a server to jump into channels", style = MaterialTheme.typography.titleMedium)
                if (showDrawerButton) {
                    Spacer(Modifier.height(12.dp))
                    Button(onClick = onOpenNavDrawer) { Text("Open Servers & Channels") }
                }
            }
        }
        return
    }

    val myUid = state.currentUid.orEmpty()
    val myRole = state.myRoleInActiveServer
    val canModerate = myRole == ServerRole.OWNER || myRole == ServerRole.ADMIN || myRole == ServerRole.MOD
    val canPost = !channel.adminOnlyPost || canModerate

    var messageInput by remember(channel.id) { mutableStateOf("") }
    var replyTarget by remember(channel.id) { mutableStateOf<ChannelMessage?>(null) }
    var editingTarget by remember(channel.id) { mutableStateOf<ChannelMessage?>(null) }

    val playingVoiceId by viewModel.voiceEngine.playingMessageId.collectAsStateWithLifecycle()

    val imagePicker = rememberLauncherForActivityResult(ActivityResultContracts.PickVisualMedia()) { uri ->
        if (uri != null) viewModel.attachImageFromUri(uri)
    }
    val filePicker = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocument()) { uri ->
        if (uri != null) viewModel.attachFileFromUri(uri)
    }

    val listState = rememberLazyListState()
    val visibleMessages = remember(state.activeChannelMessages, state.blockedUids) {
        state.activeChannelMessages.filter { it.senderId !in state.blockedUids }
    }
    LaunchedEffect(visibleMessages.size, channel.id) {
        if (visibleMessages.isNotEmpty()) {
            listState.animateScrollToItem(visibleMessages.lastIndex)
        }
    }

    Row(modifier = modifier.fillMaxSize()) {
        Column(
            modifier = Modifier
                .weight(1f)
                .fillMaxHeight()
                .background(MaterialTheme.colorScheme.surface)
        ) {
            // Channel Header
            Surface(
                color = MaterialTheme.colorScheme.surface,
                shadowElevation = 2.dp,
                modifier = Modifier.fillMaxWidth()
            ) {
                Row(
                    modifier = Modifier.padding(horizontal = 12.dp, vertical = 10.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    if (showDrawerButton) {
                        IconButton(onClick = onOpenNavDrawer, modifier = Modifier.testTag("open_channels_drawer")) {
                            Icon(Icons.Default.Menu, "Servers & Channels")
                        }
                    }
                    Icon(
                        when (channel.typeEnum) {
                            ChannelType.TEXT -> Icons.Default.Tag
                            ChannelType.VOICE -> Icons.Default.VolumeUp
                            ChannelType.ANNOUNCEMENT -> Icons.Default.Campaign
                        },
                        contentDescription = null,
                        tint = NexusPurpleGlow
                    )
                    Spacer(Modifier.width(8.dp))
                    Column(modifier = Modifier.weight(1f)) {
                        Text(
                            text = channel.name,
                            style = MaterialTheme.typography.titleMedium,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis
                        )
                        if (channel.topic.isNotBlank()) {
                            Text(
                                text = channel.topic,
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis
                            )
                        }
                    }
                    IconButton(
                        onClick = onToggleMembersSheet,
                        modifier = Modifier.testTag("toggle_members_button")
                    ) {
                        Icon(Icons.Default.Group, "Online members")
                    }
                }
            }

            HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant)

            // Voice Stage Panel (when in a VOICE channel)
            AnimatedVisibility(visible = channel.typeEnum == ChannelType.VOICE) {
                VoiceLoungeStage(
                    server = server,
                    channel = channel,
                    state = state,
                    voiceEngine = viewModel.voiceEngine,
                    voiceMembers = state.activeVoiceRoomMembers.filter { it.channelId == channel.id },
                    onJoinVoice = { hasMic -> viewModel.joinVoiceChannel(server, channel, hasMic) },
                    onToggleMute = { hasMic -> viewModel.toggleMute(hasMic) },
                    onToggleDeafen = { viewModel.toggleDeafen() },
                    onLeaveVoice = { viewModel.leaveVoiceRoom() },
                    onSendVoiceClip = { viewModel.sendPushToTalkVoiceMessage() },
                    onDeniedPermission = {
                        viewModel.showSnack("Microphone permission denied — joined in listen-only mode.")
                    }
                )
            }

            // Messages Feed
            LazyColumn(
                state = listState,
                modifier = Modifier
                    .weight(1f)
                    .fillMaxWidth(),
                contentPadding = PaddingValues(vertical = 10.dp)
            ) {
                item {
                    Column(modifier = Modifier.padding(horizontal = 16.dp, vertical = 14.dp)) {
                        Text(
                            text = "Welcome to #${channel.name}",
                            style = MaterialTheme.typography.headlineSmall,
                            fontWeight = FontWeight.Bold
                        )
                        Text(
                            text = channel.topic.ifBlank { "This is the start of #${channel.name} in ${server.name}." },
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                        HorizontalDivider(modifier = Modifier.padding(top = 12.dp), color = MaterialTheme.colorScheme.outlineVariant)
                    }
                }

                items(visibleMessages, key = { it.id }) { msg ->
                    val memberLookup = state.activeServerMembers.firstOrNull { it.userId == msg.senderId }
                    DiscordMessageCard(
                        msg = MessageUiModel(
                            id = msg.id,
                            senderId = msg.senderId,
                            senderName = msg.senderName,
                            senderTag = msg.senderTag,
                            senderAvatarId = msg.senderAvatarId,
                            senderRole = memberLookup?.roleEnum ?: msg.senderRoleEnum,
                            text = msg.text,
                            replyToAuthor = msg.replyToAuthor,
                            replyToSnippet = msg.replyToSnippet,
                            attachmentType = msg.attachmentEnum,
                            attachmentName = msg.attachmentName,
                            attachmentMime = msg.attachmentMime,
                            attachmentData = msg.attachmentData,
                            audioDurationSec = msg.audioDurationSec,
                            reactions = msg.reactions,
                            isEdited = msg.isEdited,
                            timestampMillis = msg.createdAt?.toDate()?.time
                        ),
                        currentUid = myUid,
                        canModerate = canModerate,
                        playingVoiceId = playingVoiceId,
                        onReply = {
                            editingTarget = null
                            replyTarget = msg
                        },
                        onReact = { emoji -> viewModel.toggleChannelReaction(msg, emoji) },
                        onEdit = {
                            replyTarget = null
                            editingTarget = msg
                            messageInput = msg.text
                        },
                        onDelete = { viewModel.deleteChannelMessage(msg) },
                        onPlayVoice = { id, b64 -> viewModel.voiceEngine.playVoiceMessage(id, b64) },
                        onAuthorClick = {
                            memberLookup?.let(onInspectMember)
                        },
                        onFileSavedSnack = viewModel::showSnack
                    )
                }
            }

            // Composer
            ReplyAndAttachmentPreview(
                replyAuthor = replyTarget?.senderName,
                replySnippet = replyTarget?.text?.ifBlank { "[Attachment]" },
                editingText = editingTarget?.text,
                pendingAttachment = state.pendingAttachment,
                onClearReply = { replyTarget = null },
                onClearEditing = { editingTarget = null; messageInput = "" },
                onClearAttachment = viewModel::clearPendingAttachment
            )

            if (!canPost) {
                Surface(
                    color = MaterialTheme.colorScheme.surfaceContainerHigh,
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Row(
                        modifier = Modifier.padding(14.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.Center
                    ) {
                        Icon(Icons.Default.Lock, null, modifier = Modifier.size(16.dp), tint = MaterialTheme.colorScheme.onSurfaceVariant)
                        Spacer(Modifier.width(8.dp))
                        Text(
                            "Announcement channel • Only Owner, Admins, and Moderators can post",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                }
            } else {
                Surface(
                    color = MaterialTheme.colorScheme.surfaceContainerLow,
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Row(
                        modifier = Modifier.padding(horizontal = 10.dp, vertical = 8.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        IconButton(
                            onClick = {
                                imagePicker.launch(PickVisualMediaRequest(ActivityResultContracts.PickVisualMedia.ImageOnly))
                            },
                            modifier = Modifier.testTag("attach_image_button")
                        ) {
                            Icon(Icons.Default.AddPhotoAlternate, "Attach image", tint = NexusPurpleGlow)
                        }
                        IconButton(
                            onClick = { filePicker.launch(arrayOf("*/*")) },
                            modifier = Modifier.testTag("attach_file_button")
                        ) {
                            Icon(Icons.Default.AttachFile, "Attach file", tint = NexusBlue)
                        }
                        OutlinedTextField(
                            value = messageInput,
                            onValueChange = { if (it.length <= 1500) messageInput = it },
                            placeholder = { Text("Message #${channel.name}") },
                            maxLines = 4,
                            shape = RoundedCornerShape(16.dp),
                            modifier = Modifier
                                .weight(1f)
                                .testTag("channel_message_input")
                        )
                        Spacer(Modifier.width(6.dp))
                        IconButton(
                            onClick = {
                                val editing = editingTarget
                                if (editing != null) {
                                    viewModel.editChannelMessage(editing, messageInput)
                                    editingTarget = null
                                    messageInput = ""
                                } else {
                                    viewModel.sendChannelMessage(messageInput, replyTarget)
                                    replyTarget = null
                                    messageInput = ""
                                }
                            },
                            enabled = messageInput.isNotBlank() || state.pendingAttachment != null,
                            modifier = Modifier
                                .size(48.dp)
                                .clip(CircleShape)
                                .background(NexusPurple)
                                .testTag("send_channel_message")
                        ) {
                            Icon(Icons.AutoMirrored.Filled.Send, "Send message", tint = Color.White)
                        }
                    }
                }
            }
        }

        if (showMembersInline) {
            OnlineMembersPane(
                members = state.activeServerMembers,
                onMemberClick = onInspectMember,
                modifier = Modifier
                    .width(230.dp)
                    .fillMaxHeight()
            )
        }
    }
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun VoiceLoungeStage(
    server: NexusServer,
    channel: ServerChannel,
    state: NexusUiState,
    voiceEngine: VoiceEngine,
    voiceMembers: List<VoiceMember>,
    onJoinVoice: (hasMicPermission: Boolean) -> Unit,
    onToggleMute: (hasMicPermission: Boolean) -> Unit,
    onToggleDeafen: () -> Unit,
    onLeaveVoice: () -> Unit,
    onSendVoiceClip: () -> Unit,
    onDeniedPermission: () -> Unit
) {
    val activeSession = state.activeVoiceSession
    val isConnectedHere = activeSession?.channelId == channel.id
    val micLevel by voiceEngine.micLevel.collectAsStateWithLifecycle()
    val isRecordingClip by voiceEngine.isRecordingClip.collectAsStateWithLifecycle()

    var pendingJoinAfterPerm by remember { mutableStateOf(false) }
    var pendingUnmuteAfterPerm by remember { mutableStateOf(false) }

    val micPermLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.RequestPermission()
    ) { granted ->
        if (!granted) onDeniedPermission()
        if (pendingJoinAfterPerm) {
            pendingJoinAfterPerm = false
            onJoinVoice(granted)
        } else if (pendingUnmuteAfterPerm) {
            pendingUnmuteAfterPerm = false
            onToggleMute(granted)
        }
    }

    Surface(
        color = MaterialTheme.colorScheme.surfaceContainerLow,
        shape = RoundedCornerShape(bottomStart = 18.dp, bottomEnd = 18.dp),
        modifier = Modifier
            .fillMaxWidth()
            .border(1.dp, NexusPurple.copy(alpha = 0.35f), RoundedCornerShape(bottomStart = 18.dp, bottomEnd = 18.dp))
    ) {
        Column(modifier = Modifier.padding(14.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.SpaceBetween, modifier = Modifier.fillMaxWidth()) {
                Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    Icon(Icons.Default.VolumeUp, null, tint = StatusOnline)
                    Text(
                        text = "${channel.name} • ${voiceMembers.size} in voice",
                        style = MaterialTheme.typography.titleSmall,
                        fontWeight = FontWeight.Bold
                    )
                }
                if (isConnectedHere) {
                    Button(
                        onClick = onLeaveVoice,
                        colors = ButtonDefaults.buttonColors(containerColor = NexusDanger),
                        contentPadding = PaddingValues(horizontal = 12.dp, vertical = 4.dp),
                        modifier = Modifier.testTag("leave_voice_button")
                    ) {
                        Icon(Icons.Default.CallEnd, null, modifier = Modifier.size(16.dp))
                        Spacer(Modifier.width(6.dp))
                        Text("Leave")
                    }
                } else {
                    Button(
                        onClick = {
                            if (voiceEngine.hasMicPermission()) {
                                onJoinVoice(true)
                            } else {
                                pendingJoinAfterPerm = true
                                micPermLauncher.launch(Manifest.permission.RECORD_AUDIO)
                            }
                        },
                        colors = ButtonDefaults.buttonColors(containerColor = StatusOnline, contentColor = Color.Black),
                        modifier = Modifier.testTag("join_voice_button")
                    ) {
                        Icon(Icons.Default.Mic, null, modifier = Modifier.size(16.dp))
                        Spacer(Modifier.width(6.dp))
                        Text("Join Voice")
                    }
                }
            }

            if (voiceMembers.isEmpty()) {
                Text(
                    "Voice lounge is open — tap Join Voice to start comms with your squad.",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            } else {
                FlowRow(
                    horizontalArrangement = Arrangement.spacedBy(10.dp),
                    verticalArrangement = Arrangement.spacedBy(10.dp)
                ) {
                    voiceMembers.forEach { vm ->
                        Surface(
                            color = MaterialTheme.colorScheme.surfaceContainerHigh,
                            shape = RoundedCornerShape(14.dp),
                            modifier = Modifier.border(
                                1.dp,
                                if (vm.isSpeaking) StatusOnline else MaterialTheme.colorScheme.outlineVariant,
                                RoundedCornerShape(14.dp)
                            )
                        ) {
                            Row(
                                modifier = Modifier.padding(horizontal = 10.dp, vertical = 8.dp),
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.spacedBy(8.dp)
                            ) {
                                NexusAvatar(avatarId = vm.avatarId, size = 32.dp, isSpeaking = vm.isSpeaking)
                                Column {
                                    Text(vm.displayName, style = MaterialTheme.typography.labelLarge)
                                    Text(
                                        text = when {
                                            vm.isDeafened -> "Deafened"
                                            vm.isMuted -> "Muted"
                                            vm.isSpeaking -> "Speaking"
                                            else -> "Connected"
                                        },
                                        style = MaterialTheme.typography.labelSmall,
                                        color = if (vm.isSpeaking) StatusOnline else MaterialTheme.colorScheme.onSurfaceVariant
                                    )
                                }
                                if (vm.isMuted) {
                                    Icon(Icons.Default.MicOff, "Muted", modifier = Modifier.size(16.dp), tint = NexusDanger)
                                }
                            }
                        }
                    }
                }
            }

            if (isConnectedHere && activeSession != null) {
                if (!activeSession.isMuted) {
                    LinearProgressIndicator(
                        progress = { micLevel },
                        color = StatusOnline,
                        trackColor = MaterialTheme.colorScheme.surfaceContainerHighest,
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(5.dp)
                            .clip(RoundedCornerShape(4.dp))
                    )
                }
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    OutlinedButton(
                        onClick = {
                            if (voiceEngine.hasMicPermission()) {
                                onToggleMute(true)
                            } else {
                                pendingUnmuteAfterPerm = true
                                micPermLauncher.launch(Manifest.permission.RECORD_AUDIO)
                            }
                        },
                        modifier = Modifier.weight(1f).testTag("voice_mute_button")
                    ) {
                        Icon(
                            if (activeSession.isMuted) Icons.Default.MicOff else Icons.Default.Mic,
                            null,
                            tint = if (activeSession.isMuted) NexusDanger else StatusOnline,
                            modifier = Modifier.size(18.dp)
                        )
                        Spacer(Modifier.width(6.dp))
                        Text(if (activeSession.isMuted) "Unmute" else "Mute")
                    }

                    OutlinedButton(
                        onClick = onToggleDeafen,
                        modifier = Modifier.weight(1f)
                    ) {
                        Icon(
                            if (activeSession.isDeafened) Icons.Default.HeadsetOff else Icons.Default.Headset,
                            null,
                            modifier = Modifier.size(18.dp)
                        )
                        Spacer(Modifier.width(6.dp))
                        Text(if (activeSession.isDeafened) "Undeafen" else "Deafen")
                    }

                    Button(
                        onClick = {
                            if (!voiceEngine.hasMicPermission()) {
                                micPermLauncher.launch(Manifest.permission.RECORD_AUDIO)
                            } else if (isRecordingClip) {
                                onSendVoiceClip()
                            } else {
                                voiceEngine.startPushToTalkClip()
                            }
                        },
                        colors = ButtonDefaults.buttonColors(
                            containerColor = if (isRecordingClip) NexusDanger else NexusPurple
                        ),
                        modifier = Modifier.testTag("ptt_record_button")
                    ) {
                        Icon(Icons.Default.RadioButtonChecked, null, modifier = Modifier.size(16.dp))
                        Spacer(Modifier.width(4.dp))
                        Text(if (isRecordingClip) "Send Clip" else "PTT")
                    }
                }
            }
        }
    }
}

@Composable
fun OnlineMembersPane(
    members: List<ServerMember>,
    onMemberClick: (ServerMember) -> Unit,
    modifier: Modifier = Modifier
) {
    val online = remember(members) { members.filter { it.statusEnum != OnlineStatus.OFFLINE } }
    val offline = remember(members) { members.filter { it.statusEnum == OnlineStatus.OFFLINE } }

    Surface(
        color = MaterialTheme.colorScheme.surfaceContainerLow,
        modifier = modifier.border(1.dp, MaterialTheme.colorScheme.outlineVariant)
    ) {
        LazyColumn(
            modifier = Modifier.fillMaxSize().padding(10.dp),
            verticalArrangement = Arrangement.spacedBy(6.dp)
        ) {
            item {
                Text(
                    text = "ONLINE — ${online.size}",
                    style = MaterialTheme.typography.labelSmall,
                    color = StatusOnline,
                    fontWeight = FontWeight.Bold,
                    modifier = Modifier.padding(vertical = 4.dp)
                )
            }
            items(online, key = { "on_${it.userId}" }) { m ->
                MemberRowTile(m, onClick = { onMemberClick(m) })
            }
            if (offline.isNotEmpty()) {
                item {
                    Spacer(Modifier.height(8.dp))
                    Text(
                        text = "OFFLINE — ${offline.size}",
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        fontWeight = FontWeight.Bold,
                        modifier = Modifier.padding(vertical = 4.dp)
                    )
                }
                items(offline, key = { "off_${it.userId}" }) { m ->
                    MemberRowTile(m, onClick = { onMemberClick(m) })
                }
            }
        }
    }
}

@Composable
private fun MemberRowTile(member: ServerMember, onClick: () -> Unit) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(10.dp))
            .clickable(onClick = onClick)
            .padding(horizontal = 8.dp, vertical = 6.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        NexusAvatar(
            avatarId = member.avatarId,
            customAvatarData = member.customAvatarData,
            status = member.statusEnum,
            size = 34.dp
        )
        Spacer(Modifier.width(10.dp))
        Column(modifier = Modifier.weight(1f)) {
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                Text(
                    text = member.displayName,
                    style = MaterialTheme.typography.labelLarge,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
                RoleBadgePill(member.roleEnum)
            }
            Text(
                text = member.statusEnum.label,
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
    }
}

@Composable
fun MemberInspectorDialog(
    member: ServerMember,
    profile: GamerProfile?,
    currentUid: String,
    isBlocked: Boolean,
    myRole: ServerRole,
    onDismiss: () -> Unit,
    onSendFriendRequest: () -> Unit,
    onDirectMessage: () -> Unit,
    onBlockToggle: () -> Unit,
    onKick: () -> Unit,
    onBan: () -> Unit
) {
    val isSelf = member.userId == currentUid
    val canModerateTarget = !isSelf && member.roleEnum != ServerRole.OWNER &&
        (myRole == ServerRole.OWNER || (myRole == ServerRole.ADMIN && member.roleEnum != ServerRole.ADMIN))

    AlertDialog(
        onDismissRequest = onDismiss,
        title = {
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                NexusAvatar(member.avatarId, member.customAvatarData, member.statusEnum, size = 48.dp)
                Column {
                    Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                        Text(member.displayName, style = MaterialTheme.typography.titleMedium)
                        RoleBadgePill(member.roleEnum)
                    }
                    Text("@${member.gamerTag} • ${member.statusEnum.label}", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
            }
        },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                val bio = profile?.bio.orEmpty()
                if (bio.isNotBlank()) {
                    Text(bio, style = MaterialTheme.typography.bodyMedium)
                }
                BadgeChipsRow(profile?.badges ?: member.badges)
                if (!isSelf) {
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp), modifier = Modifier.fillMaxWidth()) {
                        Button(onClick = { onDirectMessage(); onDismiss() }, modifier = Modifier.weight(1f)) {
                            Text("Message")
                        }
                        OutlinedButton(onClick = { onSendFriendRequest(); onDismiss() }, modifier = Modifier.weight(1f)) {
                            Text("Add Friend")
                        }
                    }
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp), modifier = Modifier.fillMaxWidth()) {
                        OutlinedButton(
                            onClick = { onBlockToggle(); onDismiss() },
                            colors = ButtonDefaults.outlinedButtonColors(contentColor = NexusDanger),
                            modifier = Modifier.weight(1f)
                        ) {
                            Text(if (isBlocked) "Unblock" else "Block User")
                        }
                        if (canModerateTarget) {
                            OutlinedButton(onClick = { onKick(); onDismiss() }) { Text("Kick") }
                            Button(
                                onClick = { onBan(); onDismiss() },
                                colors = ButtonDefaults.buttonColors(containerColor = NexusDanger)
                            ) { Text("Ban") }
                        }
                    }
                }
            }
        },
        confirmButton = {
            TextButton(onClick = onDismiss) { Text("Close") }
        }
    )
}

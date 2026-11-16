package com.example.ui.components

import android.content.Intent
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Image
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
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.Reply
import androidx.compose.material.icons.filled.AddReaction
import androidx.compose.material.icons.filled.AttachFile
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Download
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material.icons.filled.GraphicEq
import androidx.compose.material.icons.filled.Image
import androidx.compose.material.icons.filled.MoreVert
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.Shield
import androidx.compose.material.icons.filled.Stop
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.scale
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.data.model.AttachmentType
import com.example.data.model.AvatarPreset
import com.example.data.model.BadgePreset
import com.example.data.model.OnlineStatus
import com.example.data.model.ServerRole
import com.example.data.util.AttachmentHelper
import com.example.data.util.PendingAttachment
import com.example.ui.theme.NexusBlue
import com.example.ui.theme.NexusCyan
import com.example.ui.theme.NexusPurple
import com.example.ui.theme.NexusPurpleGlow
import com.example.ui.theme.RoleAdminPurple
import com.example.ui.theme.RoleModBlue
import com.example.ui.theme.RoleOwnerGold
import com.example.ui.theme.StatusAfk
import com.example.ui.theme.StatusInGame
import com.example.ui.theme.StatusOffline
import com.example.ui.theme.StatusOnline
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

val QUICK_EMOJIS = listOf("🔥", "⚡", "👑", "😂", "❤️", "🎮")

@Composable
fun statusColor(status: OnlineStatus): Color = when (status) {
    OnlineStatus.ONLINE -> StatusOnline
    OnlineStatus.IN_GAME -> StatusInGame
    OnlineStatus.AFK -> StatusAfk
    OnlineStatus.OFFLINE -> StatusOffline
}

@Composable
fun NexusAvatar(
    avatarId: String,
    customAvatarData: String = "",
    status: OnlineStatus? = null,
    size: Dp = 40.dp,
    isSpeaking: Boolean = false,
    modifier: Modifier = Modifier
) {
    val preset = AvatarPreset.fromId(avatarId)
    val decoded = remember(customAvatarData) { AttachmentHelper.decodeImageBitmap(customAvatarData) }
    val pulse = if (isSpeaking) {
        val transition = rememberInfiniteTransition(label = "voice_pulse")
        transition.animateFloat(
            initialValue = 1f,
            targetValue = 1.08f,
            animationSpec = infiniteRepeatable(tween(450), RepeatMode.Reverse),
            label = "ring_scale"
        ).value
    } else 1f

    val ringColor by animateColorAsState(
        targetValue = if (isSpeaking) StatusOnline else Color.Transparent,
        label = "ring_color"
    )

    Box(modifier = modifier.size(size)) {
        Box(
            modifier = Modifier
                .fillMaxSize()
                .scale(pulse)
                .clip(CircleShape)
                .background(
                    Brush.linearGradient(
                        listOf(NexusPurple, NexusBlue)
                    )
                )
                .then(
                    if (isSpeaking) Modifier.border(2.5.dp, ringColor, CircleShape)
                    else Modifier
                ),
            contentAlignment = Alignment.Center
        ) {
            if (decoded != null) {
                Image(
                    bitmap = decoded,
                    contentDescription = preset.label,
                    contentScale = ContentScale.Crop,
                    modifier = Modifier.fillMaxSize()
                )
            } else {
                Text(
                    text = preset.glyph,
                    fontSize = (size.value * 0.44f).sp
                )
            }
        }

        if (status != null) {
            val dotSize = (size * 0.30f).coerceIn(10.dp, 16.dp)
            Box(
                modifier = Modifier
                    .align(Alignment.BottomEnd)
                    .size(dotSize)
                    .clip(CircleShape)
                    .background(MaterialTheme.colorScheme.surface)
                    .padding(2.dp)
                    .clip(CircleShape)
                    .background(statusColor(status))
            )
        }
    }
}

@Composable
fun RoleBadgePill(role: ServerRole, modifier: Modifier = Modifier) {
    if (role == ServerRole.MEMBER) return
    val (bg, fg) = when (role) {
        ServerRole.OWNER -> RoleOwnerGold.copy(alpha = 0.18f) to RoleOwnerGold
        ServerRole.ADMIN -> RoleAdminPurple.copy(alpha = 0.2f) to RoleAdminPurple
        ServerRole.MOD -> RoleModBlue.copy(alpha = 0.2f) to RoleModBlue
        ServerRole.MEMBER -> Color.Transparent to MaterialTheme.colorScheme.onSurfaceVariant
    }
    Surface(
        color = bg,
        shape = RoundedCornerShape(6.dp),
        modifier = modifier
    ) {
        Text(
            text = role.label.uppercase(),
            style = MaterialTheme.typography.labelSmall,
            color = fg,
            fontWeight = FontWeight.Bold,
            modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp)
        )
    }
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
fun BadgeChipsRow(badgeIds: List<String>, modifier: Modifier = Modifier) {
    val badges = badgeIds.mapNotNull { BadgePreset.fromId(it) }
    if (badges.isEmpty()) return
    FlowRow(
        modifier = modifier,
        horizontalArrangement = Arrangement.spacedBy(6.dp),
        verticalArrangement = Arrangement.spacedBy(6.dp)
    ) {
        badges.forEach { badge ->
            Surface(
                color = MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.6f),
                shape = RoundedCornerShape(8.dp)
            ) {
                Row(
                    modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(4.dp)
                ) {
                    Text(badge.icon, fontSize = 12.sp)
                    Text(
                        badge.title,
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onPrimaryContainer
                    )
                }
            }
        }
    }
}

data class MessageUiModel(
    val id: String,
    val senderId: String,
    val senderName: String,
    val senderTag: String,
    val senderAvatarId: String,
    val senderRole: ServerRole,
    val text: String,
    val replyToAuthor: String,
    val replyToSnippet: String,
    val attachmentType: AttachmentType,
    val attachmentName: String,
    val attachmentMime: String,
    val attachmentData: String,
    val audioDurationSec: Int,
    val reactions: Map<String, List<String>>,
    val isEdited: Boolean,
    val timestampMillis: Long?
)

@OptIn(ExperimentalLayoutApi::class)
@Composable
fun DiscordMessageCard(
    msg: MessageUiModel,
    currentUid: String,
    canModerate: Boolean,
    playingVoiceId: String?,
    onReply: () -> Unit,
    onReact: (String) -> Unit,
    onEdit: () -> Unit,
    onDelete: () -> Unit,
    onPlayVoice: (String, String) -> Unit,
    onAuthorClick: () -> Unit,
    onFileSavedSnack: (String) -> Unit
) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    var menuExpanded by remember { mutableStateOf(false) }
    var emojiBarOpen by remember { mutableStateOf(false) }

    val saveFileLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.CreateDocument(msg.attachmentMime.ifBlank { "*/*" })
    ) { destUri ->
        if (destUri != null && msg.attachmentData.isNotBlank()) {
            scope.launch {
                val res = withContext(Dispatchers.IO) {
                    AttachmentHelper.saveAttachmentToUri(context, destUri, msg.attachmentData)
                }
                onFileSavedSnack(
                    if (res.isSuccess) "Saved ${msg.attachmentName}" else "Could not save attachment"
                )
            }
        }
    }

    val isOwn = msg.senderId == currentUid
    val timeLabel = remember(msg.timestampMillis) {
        msg.timestampMillis?.let {
            SimpleDateFormat("h:mm a", Locale.getDefault()).format(Date(it))
        } ?: "Sending..."
    }

    Column(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 12.dp, vertical = 4.dp)
            .clip(RoundedCornerShape(12.dp))
            .clickable { menuExpanded = true }
            .padding(horizontal = 6.dp, vertical = 6.dp)
    ) {
        if (msg.replyToAuthor.isNotBlank()) {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(start = 24.dp, bottom = 4.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(6.dp)
            ) {
                Icon(
                    Icons.AutoMirrored.Filled.Reply,
                    contentDescription = null,
                    modifier = Modifier.size(14.dp),
                    tint = NexusCyan
                )
                Text(
                    text = "@${msg.replyToAuthor}: ${msg.replyToSnippet}",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
            }
        }

        Row(
            modifier = Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.Top
        ) {
            NexusAvatar(
                avatarId = msg.senderAvatarId,
                size = 38.dp,
                modifier = Modifier.clickable(onClick = onAuthorClick)
            )
            Spacer(Modifier.width(10.dp))
            Column(modifier = Modifier.weight(1f)) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(6.dp)
                ) {
                    Text(
                        text = msg.senderName,
                        style = MaterialTheme.typography.titleSmall,
                        color = when (msg.senderRole) {
                            ServerRole.OWNER -> RoleOwnerGold
                            ServerRole.ADMIN -> RoleAdminPurple
                            ServerRole.MOD -> RoleModBlue
                            ServerRole.MEMBER -> MaterialTheme.colorScheme.onSurface
                        },
                        modifier = Modifier.clickable(onClick = onAuthorClick)
                    )
                    RoleBadgePill(msg.senderRole)
                    Text(
                        text = timeLabel + if (msg.isEdited) " (edited)" else "",
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }

                if (msg.text.isNotBlank()) {
                    Spacer(Modifier.height(2.dp))
                    Text(
                        text = msg.text,
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurface
                    )
                }

                // Inline Attachment Rendering
                when (msg.attachmentType) {
                    AttachmentType.IMAGE -> {
                        val img = remember(msg.attachmentData) {
                            AttachmentHelper.decodeImageBitmap(msg.attachmentData)
                        }
                        if (img != null) {
                            Spacer(Modifier.height(6.dp))
                            Image(
                                bitmap = img,
                                contentDescription = msg.attachmentName.ifBlank { "Shared image" },
                                contentScale = ContentScale.Crop,
                                modifier = Modifier
                                    .fillMaxWidth(0.85f)
                                    .heightIn(max = 220.dp)
                                    .clip(RoundedCornerShape(12.dp))
                                    .border(1.dp, MaterialTheme.colorScheme.outline, RoundedCornerShape(12.dp))
                            )
                        }
                    }

                    AttachmentType.FILE -> {
                        Spacer(Modifier.height(6.dp))
                        Surface(
                            color = MaterialTheme.colorScheme.surfaceContainerHigh,
                            shape = RoundedCornerShape(12.dp),
                            modifier = Modifier
                                .fillMaxWidth(0.9f)
                                .clickable {
                                    saveFileLauncher.launch(msg.attachmentName.ifBlank { "nexus_file" })
                                }
                        ) {
                            Row(
                                modifier = Modifier.padding(10.dp),
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Icon(
                                    Icons.Default.AttachFile,
                                    contentDescription = null,
                                    tint = NexusBlue
                                )
                                Spacer(Modifier.width(8.dp))
                                Column(modifier = Modifier.weight(1f)) {
                                    Text(
                                        text = msg.attachmentName.ifBlank { "Attachment" },
                                        style = MaterialTheme.typography.labelLarge,
                                        maxLines = 1,
                                        overflow = TextOverflow.Ellipsis
                                    )
                                    Text(
                                        text = "Tap to download",
                                        style = MaterialTheme.typography.bodySmall,
                                        color = NexusCyan
                                    )
                                }
                                Icon(
                                    Icons.Default.Download,
                                    contentDescription = "Download file",
                                    tint = MaterialTheme.colorScheme.onSurfaceVariant
                                )
                            }
                        }
                    }

                    AttachmentType.VOICE -> {
                        val isPlaying = playingVoiceId == msg.id
                        Spacer(Modifier.height(6.dp))
                        Surface(
                            color = NexusPurple.copy(alpha = 0.16f),
                            shape = RoundedCornerShape(14.dp),
                            modifier = Modifier
                                .border(1.dp, NexusPurpleGlow.copy(alpha = 0.45f), RoundedCornerShape(14.dp))
                                .clickable { onPlayVoice(msg.id, msg.attachmentData) }
                        ) {
                            Row(
                                modifier = Modifier.padding(horizontal = 12.dp, vertical = 8.dp),
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.spacedBy(8.dp)
                            ) {
                                Icon(
                                    if (isPlaying) Icons.Default.Stop else Icons.Default.PlayArrow,
                                    contentDescription = if (isPlaying) "Stop voice clip" else "Play voice clip",
                                    tint = NexusPurpleGlow
                                )
                                Icon(
                                    Icons.Default.GraphicEq,
                                    contentDescription = null,
                                    tint = NexusBlue
                                )
                                Text(
                                    text = if (isPlaying) "Playing comms..." else "Voice Comms • ${msg.audioDurationSec.coerceAtLeast(1)}s",
                                    style = MaterialTheme.typography.labelLarge,
                                    color = MaterialTheme.colorScheme.onSurface
                                )
                            }
                        }
                    }

                    AttachmentType.NONE -> Unit
                }

                // Reactions Row
                if (msg.reactions.isNotEmpty() || emojiBarOpen) {
                    Spacer(Modifier.height(6.dp))
                    FlowRow(
                        horizontalArrangement = Arrangement.spacedBy(6.dp),
                        verticalArrangement = Arrangement.spacedBy(4.dp)
                    ) {
                        msg.reactions.forEach { (emoji, uids) ->
                            val reactedByMe = currentUid in uids
                            Surface(
                                color = if (reactedByMe) NexusPurple.copy(alpha = 0.25f)
                                else MaterialTheme.colorScheme.surfaceContainerHigh,
                                shape = RoundedCornerShape(8.dp),
                                modifier = Modifier
                                    .border(
                                        1.dp,
                                        if (reactedByMe) NexusPurpleGlow else MaterialTheme.colorScheme.outlineVariant,
                                        RoundedCornerShape(8.dp)
                                    )
                                    .clickable { onReact(emoji) }
                            ) {
                                Text(
                                    text = "$emoji ${uids.size}",
                                    style = MaterialTheme.typography.labelMedium,
                                    color = MaterialTheme.colorScheme.onSurface,
                                    modifier = Modifier.padding(horizontal = 8.dp, vertical = 3.dp)
                                )
                            }
                        }
                    }
                }

                if (emojiBarOpen) {
                    Spacer(Modifier.height(6.dp))
                    Surface(
                        color = MaterialTheme.colorScheme.surfaceContainerHighest,
                        shape = RoundedCornerShape(10.dp)
                    ) {
                        Row(
                            modifier = Modifier.padding(horizontal = 6.dp, vertical = 4.dp),
                            horizontalArrangement = Arrangement.spacedBy(4.dp)
                        ) {
                            QUICK_EMOJIS.forEach { emoji ->
                                Text(
                                    text = emoji,
                                    fontSize = 18.sp,
                                    modifier = Modifier
                                        .clip(RoundedCornerShape(6.dp))
                                        .clickable {
                                            onReact(emoji)
                                            emojiBarOpen = false
                                        }
                                        .padding(horizontal = 6.dp, vertical = 2.dp)
                                )
                            }
                        }
                    }
                }
            }

            Box {
                IconButton(
                    onClick = { menuExpanded = true },
                    modifier = Modifier
                        .size(36.dp)
                        .testTag("message_more_${msg.id}")
                ) {
                    Icon(
                        Icons.Default.MoreVert,
                        contentDescription = "Message actions",
                        modifier = Modifier.size(18.dp),
                        tint = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }

                DropdownMenu(
                    expanded = menuExpanded,
                    onDismissRequest = { menuExpanded = false }
                ) {
                    DropdownMenuItem(
                        text = { Text("React") },
                        leadingIcon = { Icon(Icons.Default.AddReaction, null) },
                        onClick = {
                            menuExpanded = false
                            emojiBarOpen = !emojiBarOpen
                        }
                    )
                    DropdownMenuItem(
                        text = { Text("Reply") },
                        leadingIcon = { Icon(Icons.AutoMirrored.Filled.Reply, null) },
                        onClick = {
                            menuExpanded = false
                            onReply()
                        }
                    )
                    if (isOwn && msg.attachmentType != AttachmentType.VOICE) {
                        DropdownMenuItem(
                            text = { Text("Edit") },
                            leadingIcon = { Icon(Icons.Default.Edit, null) },
                            onClick = {
                                menuExpanded = false
                                onEdit()
                            }
                        )
                    }
                    if (isOwn || canModerate) {
                        DropdownMenuItem(
                            text = { Text(if (!isOwn && canModerate) "Delete (Moderate)" else "Delete") },
                            leadingIcon = {
                                Icon(
                                    if (!isOwn && canModerate) Icons.Default.Shield else Icons.Default.Delete,
                                    null,
                                    tint = MaterialTheme.colorScheme.error
                                )
                            },
                            onClick = {
                                menuExpanded = false
                                onDelete()
                            }
                        )
                    }
                }
            }
        }
    }
}

@Composable
fun ReplyAndAttachmentPreview(
    replyAuthor: String?,
    replySnippet: String?,
    editingText: String?,
    pendingAttachment: PendingAttachment?,
    onClearReply: () -> Unit,
    onClearEditing: () -> Unit,
    onClearAttachment: () -> Unit
) {
    Column(modifier = Modifier.fillMaxWidth()) {
        if (replyAuthor != null) {
            Surface(
                color = MaterialTheme.colorScheme.surfaceContainerHigh,
                modifier = Modifier.fillMaxWidth()
            ) {
                Row(
                    modifier = Modifier.padding(horizontal = 12.dp, vertical = 6.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Icon(Icons.AutoMirrored.Filled.Reply, null, tint = NexusCyan, modifier = Modifier.size(16.dp))
                    Spacer(Modifier.width(8.dp))
                    Text(
                        text = "Replying to @$replyAuthor: ${replySnippet.orEmpty()}",
                        style = MaterialTheme.typography.bodySmall,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                        modifier = Modifier.weight(1f)
                    )
                    IconButton(onClick = onClearReply, modifier = Modifier.size(28.dp)) {
                        Icon(Icons.Default.Close, "Cancel reply", modifier = Modifier.size(16.dp))
                    }
                }
            }
        }
        if (editingText != null) {
            Surface(
                color = NexusBlue.copy(alpha = 0.16f),
                modifier = Modifier.fillMaxWidth()
            ) {
                Row(
                    modifier = Modifier.padding(horizontal = 12.dp, vertical = 6.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Icon(Icons.Default.Edit, null, tint = NexusBlue, modifier = Modifier.size(16.dp))
                    Spacer(Modifier.width(8.dp))
                    Text(
                        text = "Editing message",
                        style = MaterialTheme.typography.bodySmall,
                        modifier = Modifier.weight(1f)
                    )
                    IconButton(onClick = onClearEditing, modifier = Modifier.size(28.dp)) {
                        Icon(Icons.Default.Close, "Cancel edit", modifier = Modifier.size(16.dp))
                    }
                }
            }
        }
        if (pendingAttachment != null) {
            Surface(
                color = MaterialTheme.colorScheme.surfaceContainerHighest,
                modifier = Modifier.fillMaxWidth()
            ) {
                Row(
                    modifier = Modifier.padding(horizontal = 12.dp, vertical = 6.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Icon(
                        if (pendingAttachment.type == "IMAGE") Icons.Default.Image else Icons.Default.AttachFile,
                        null,
                        tint = NexusPurpleGlow,
                        modifier = Modifier.size(18.dp)
                    )
                    Spacer(Modifier.width(8.dp))
                    Text(
                        text = pendingAttachment.fileName,
                        style = MaterialTheme.typography.labelMedium,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                        modifier = Modifier.weight(1f)
                    )
                    IconButton(onClick = onClearAttachment, modifier = Modifier.size(28.dp)) {
                        Icon(Icons.Default.Close, "Remove attachment", modifier = Modifier.size(16.dp))
                    }
                }
            }
        }
    }
}

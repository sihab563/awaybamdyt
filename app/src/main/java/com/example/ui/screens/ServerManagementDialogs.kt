package com.example.ui.screens

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Block
import androidx.compose.material.icons.filled.ContentCopy
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.PersonRemove
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.FilterChip
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Switch
import androidx.compose.material3.Tab
import androidx.compose.material3.TabRow
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalClipboardManager
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.data.model.ChannelType
import com.example.data.model.NexusServer
import com.example.data.model.ServerChannel
import com.example.data.model.ServerMember
import com.example.data.model.ServerRole
import com.example.data.model.ServerVisibility
import com.example.ui.components.NexusAvatar
import com.example.ui.components.RoleBadgePill
import com.example.ui.theme.NexusBlue
import com.example.ui.theme.NexusCyan
import com.example.ui.theme.NexusDanger
import com.example.ui.theme.NexusPurple

private val SERVER_EMOJIS = listOf("⚡", "🎮", "🛡️", "🔥", "🚀", "👾", "🏆", "🔮")

@OptIn(ExperimentalLayoutApi::class)
@Composable
fun CreateServerDialog(
    onDismiss: () -> Unit,
    onCreate: (name: String, desc: String, emoji: String, visibility: ServerVisibility) -> Unit
) {
    var name by remember { mutableStateOf("") }
    var description by remember { mutableStateOf("") }
    var emoji by remember { mutableStateOf("⚡") }
    var visibility by remember { mutableStateOf(ServerVisibility.PUBLIC) }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Create Nexus Server") },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                Text("Select Server Crest", style = MaterialTheme.typography.labelMedium)
                FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    SERVER_EMOJIS.forEach { e ->
                        Box(
                            modifier = Modifier
                                .size(40.dp)
                                .clip(CircleShape)
                                .background(
                                    if (emoji == e) NexusPurple.copy(alpha = 0.35f)
                                    else MaterialTheme.colorScheme.surfaceContainerHigh
                                )
                                .border(
                                    1.dp,
                                    if (emoji == e) NexusPurple else Color.Transparent,
                                    CircleShape
                                )
                                .clickable { emoji = e },
                            contentAlignment = Alignment.Center
                        ) {
                            Text(e, fontSize = 18.sp)
                        }
                    }
                }
                OutlinedTextField(
                    value = name,
                    onValueChange = { if (it.length <= 50) name = it },
                    label = { Text("Server Name") },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth().testTag("create_server_name")
                )
                OutlinedTextField(
                    value = description,
                    onValueChange = { if (it.length <= 280) description = it },
                    label = { Text("Server Description") },
                    maxLines = 3,
                    modifier = Modifier.fillMaxWidth()
                )
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    ServerVisibility.entries.forEach { vis ->
                        FilterChip(
                            selected = visibility == vis,
                            onClick = { visibility = vis },
                            label = { Text(vis.label) }
                        )
                    }
                }
            }
        },
        confirmButton = {
            Button(
                onClick = { onCreate(name, description, emoji, visibility); onDismiss() },
                enabled = name.trim().length >= 2,
                modifier = Modifier.testTag("confirm_create_server")
            ) { Text("Create") }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) { Text("Cancel") }
        }
    )
}

@Composable
fun JoinInviteDialog(
    onDismiss: () -> Unit,
    onJoin: (inviteCode: String) -> Unit
) {
    var code by remember { mutableStateOf("") }
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Join Server via Invite") },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Text(
                    "Paste an invite link or code (e.g., NX-7A4K9B) to join a public or private server.",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
                OutlinedTextField(
                    value = code,
                    onValueChange = { code = it },
                    label = { Text("Invite Code or Link") },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth().testTag("join_invite_input")
                )
            }
        },
        confirmButton = {
            Button(
                onClick = { onJoin(code); onDismiss() },
                enabled = code.trim().length >= 4,
                modifier = Modifier.testTag("confirm_join_invite")
            ) { Text("Join Server") }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) { Text("Cancel") }
        }
    )
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
fun ChannelEditorDialog(
    existing: ServerChannel?,
    onDismiss: () -> Unit,
    onSave: (name: String, topic: String, type: ChannelType, category: String, adminOnlyPost: Boolean) -> Unit,
    onDelete: (() -> Unit)? = null
) {
    var name by remember { mutableStateOf(existing?.name ?: "") }
    var topic by remember { mutableStateOf(existing?.topic ?: "") }
    var type by remember { mutableStateOf(existing?.typeEnum ?: ChannelType.TEXT) }
    var category by remember {
        mutableStateOf(
            existing?.categoryName ?: when (type) {
                ChannelType.TEXT -> "TEXT CHANNELS"
                ChannelType.VOICE -> "VOICE LOUNGES"
                ChannelType.ANNOUNCEMENT -> "INFO"
            }
        )
    }
    var adminOnlyPost by remember { mutableStateOf(existing?.adminOnlyPost ?: false) }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(if (existing == null) "Create Channel" else "Edit #${existing.name}") },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                Text("Channel Type", style = MaterialTheme.typography.labelMedium)
                FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    ChannelType.entries.forEach { t ->
                        FilterChip(
                            selected = type == t,
                            onClick = {
                                type = t
                                if (existing == null) {
                                    category = when (t) {
                                        ChannelType.TEXT -> "TEXT CHANNELS"
                                        ChannelType.VOICE -> "VOICE LOUNGES"
                                        ChannelType.ANNOUNCEMENT -> "INFO"
                                    }
                                    adminOnlyPost = (t == ChannelType.ANNOUNCEMENT)
                                }
                            },
                            label = { Text(t.label) }
                        )
                    }
                }
                OutlinedTextField(
                    value = name,
                    onValueChange = { if (it.length <= 40) name = it },
                    label = { Text("Channel Name") },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth().testTag("channel_name_input")
                )
                OutlinedTextField(
                    value = category,
                    onValueChange = { if (it.length <= 36) category = it },
                    label = { Text("Category Name") },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth()
                )
                OutlinedTextField(
                    value = topic,
                    onValueChange = { if (it.length <= 160) topic = it },
                    label = { Text("Channel Topic") },
                    maxLines = 2,
                    modifier = Modifier.fillMaxWidth()
                )
                if (type != ChannelType.VOICE) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.SpaceBetween
                    ) {
                        Column(modifier = Modifier.weight(1f)) {
                            Text("Restrict Posting to Staff", style = MaterialTheme.typography.labelLarge)
                            Text(
                                "Only Owner, Admins, and Moderators can send messages.",
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                        Switch(checked = adminOnlyPost, onCheckedChange = { adminOnlyPost = it })
                    }
                }
            }
        },
        confirmButton = {
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                if (onDelete != null) {
                    TextButton(
                        onClick = { onDelete(); onDismiss() },
                        colors = ButtonDefaults.textButtonColors(contentColor = NexusDanger)
                    ) { Text("Delete") }
                }
                Button(
                    onClick = { onSave(name, topic, type, category, adminOnlyPost); onDismiss() },
                    enabled = name.trim().isNotEmpty(),
                    modifier = Modifier.testTag("save_channel_button")
                ) { Text("Save") }
            }
        },
        dismissButton = { TextButton(onClick = onDismiss) { Text("Cancel") } }
    )
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
fun ServerSettingsAndModDialog(
    server: NexusServer,
    members: List<ServerMember>,
    currentUid: String,
    onDismiss: () -> Unit,
    onUpdateServer: (name: String, desc: String, emoji: String, vis: ServerVisibility, regenInvite: Boolean) -> Unit,
    onDeleteServer: () -> Unit,
    onAssignRole: (targetUid: String, role: ServerRole) -> Unit,
    onKickMember: (targetUid: String) -> Unit,
    onBanMember: (targetUid: String) -> Unit,
    onUnbanMember: (targetUid: String) -> Unit,
    onShowSnack: (String) -> Unit
) {
    val clipboard = LocalClipboardManager.current
    val myRole = server.roleFor(currentUid)
    val isOwner = myRole == ServerRole.OWNER
    var tab by remember { mutableIntStateOf(0) }
    var name by remember { mutableStateOf(server.name) }
    var desc by remember { mutableStateOf(server.description) }
    var emoji by remember { mutableStateOf(server.iconEmoji) }
    var visibility by remember { mutableStateOf(server.visibilityEnum) }
    var confirmDelete by remember { mutableStateOf(false) }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("${server.name} • Settings & Mod") },
        text = {
            Column(modifier = Modifier.fillMaxWidth()) {
                TabRow(selectedTabIndex = tab) {
                    Tab(selected = tab == 0, onClick = { tab = 0 }, text = { Text("Overview") })
                    Tab(selected = tab == 1, onClick = { tab = 1 }, text = { Text("Members (${members.size})") })
                    Tab(selected = tab == 2, onClick = { tab = 2 }, text = { Text("Bans (${server.bannedUids.size})") })
                }
                Spacer(Modifier.height(12.dp))

                when (tab) {
                    0 -> Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                        FlowRow(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                            SERVER_EMOJIS.forEach { e ->
                                Box(
                                    modifier = Modifier
                                        .size(34.dp)
                                        .clip(CircleShape)
                                        .background(if (emoji == e) NexusPurple.copy(alpha = 0.35f) else MaterialTheme.colorScheme.surfaceContainerHigh)
                                        .clickable { emoji = e },
                                    contentAlignment = Alignment.Center
                                ) { Text(e, fontSize = 16.sp) }
                            }
                        }
                        OutlinedTextField(
                            value = name,
                            onValueChange = { if (it.length <= 50) name = it },
                            label = { Text("Server Name") },
                            singleLine = true,
                            modifier = Modifier.fillMaxWidth()
                        )
                        OutlinedTextField(
                            value = desc,
                            onValueChange = { if (it.length <= 280) desc = it },
                            label = { Text("Description") },
                            maxLines = 2,
                            modifier = Modifier.fillMaxWidth()
                        )
                        if (isOwner) {
                            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                                ServerVisibility.entries.forEach { vis ->
                                    FilterChip(
                                        selected = visibility == vis,
                                        onClick = { visibility = vis },
                                        label = { Text(vis.label) }
                                    )
                                }
                            }
                        }
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.SpaceBetween
                        ) {
                            Text("Invite: ${server.inviteCode}", color = NexusCyan, style = MaterialTheme.typography.labelLarge)
                            Row {
                                IconButton(onClick = {
                                    clipboard.setText(AnnotatedString(server.inviteCode))
                                    onShowSnack("Copied invite ${server.inviteCode}")
                                }) { Icon(Icons.Default.ContentCopy, "Copy invite") }
                                IconButton(onClick = {
                                    onUpdateServer(name, desc, emoji, visibility, true)
                                }) { Icon(Icons.Default.Refresh, "Regenerate invite") }
                            }
                        }
                        if (isOwner) {
                            if (!confirmDelete) {
                                OutlinedButton(
                                    onClick = { confirmDelete = true },
                                    colors = ButtonDefaults.outlinedButtonColors(contentColor = NexusDanger),
                                    modifier = Modifier.fillMaxWidth()
                                ) {
                                    Icon(Icons.Default.Delete, null)
                                    Spacer(Modifier.width(8.dp))
                                    Text("Delete Server")
                                }
                            } else {
                                Button(
                                    onClick = { onDeleteServer(); onDismiss() },
                                    colors = ButtonDefaults.buttonColors(containerColor = NexusDanger),
                                    modifier = Modifier.fillMaxWidth()
                                ) { Text("Confirm Permanent Server Deletion") }
                            }
                        }
                    }

                    1 -> LazyColumn(
                        modifier = Modifier
                            .fillMaxWidth()
                            .heightIn(max = 320.dp),
                        verticalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        items(members, key = { it.userId }) { m ->
                            Column(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .clip(RoundedCornerShape(10.dp))
                                    .background(MaterialTheme.colorScheme.surfaceContainerHigh)
                                    .padding(10.dp)
                            ) {
                                Row(verticalAlignment = Alignment.CenterVertically) {
                                    NexusAvatar(m.avatarId, m.customAvatarData, m.statusEnum, size = 32.dp)
                                    Spacer(Modifier.width(8.dp))
                                    Column(modifier = Modifier.weight(1f)) {
                                        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                                            Text(m.displayName, style = MaterialTheme.typography.labelLarge)
                                            RoleBadgePill(m.roleEnum)
                                        }
                                        Text("@${m.gamerTag}", style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                                    }
                                }
                                if (m.userId != server.ownerId && m.userId != currentUid && (isOwner || m.roleEnum != ServerRole.ADMIN)) {
                                    Spacer(Modifier.height(8.dp))
                                    FlowRow(
                                        horizontalArrangement = Arrangement.spacedBy(6.dp),
                                        verticalArrangement = Arrangement.spacedBy(4.dp)
                                    ) {
                                        if (isOwner) {
                                            FilterChip(
                                                selected = m.roleEnum == ServerRole.ADMIN,
                                                onClick = { onAssignRole(m.userId, ServerRole.ADMIN) },
                                                label = { Text("Admin") }
                                            )
                                        }
                                        FilterChip(
                                            selected = m.roleEnum == ServerRole.MOD,
                                            onClick = { onAssignRole(m.userId, ServerRole.MOD) },
                                            label = { Text("Mod") }
                                        )
                                        FilterChip(
                                            selected = m.roleEnum == ServerRole.MEMBER,
                                            onClick = { onAssignRole(m.userId, ServerRole.MEMBER) },
                                            label = { Text("Member") }
                                        )
                                        IconButton(onClick = { onKickMember(m.userId) }) {
                                            Icon(Icons.Default.PersonRemove, "Kick", tint = NexusBlue)
                                        }
                                        IconButton(onClick = { onBanMember(m.userId) }) {
                                            Icon(Icons.Default.Block, "Ban", tint = NexusDanger)
                                        }
                                    }
                                }
                            }
                        }
                    }

                    2 -> {
                        if (server.bannedUids.isEmpty()) {
                            Text(
                                "No banned accounts in this server.",
                                style = MaterialTheme.typography.bodyMedium,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        } else {
                            LazyColumn(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .heightIn(max = 260.dp),
                                verticalArrangement = Arrangement.spacedBy(6.dp)
                            ) {
                                items(server.bannedUids) { bannedUid ->
                                    Row(
                                        modifier = Modifier
                                            .fillMaxWidth()
                                            .clip(RoundedCornerShape(10.dp))
                                            .background(MaterialTheme.colorScheme.surfaceContainerHigh)
                                            .padding(10.dp),
                                        verticalAlignment = Alignment.CenterVertically,
                                        horizontalArrangement = Arrangement.SpaceBetween
                                    ) {
                                        Text(
                                            "UID: ${bannedUid.take(12)}...",
                                            style = MaterialTheme.typography.labelMedium
                                        )
                                        TextButton(onClick = { onUnbanMember(bannedUid) }) {
                                            Text("Unban")
                                        }
                                    }
                                }
                            }
                        }
                    }
                }
            }
        },
        confirmButton = {
            if (tab == 0) {
                Button(
                    onClick = {
                        onUpdateServer(name, desc, emoji, visibility, false)
                        onDismiss()
                    },
                    enabled = name.trim().length >= 2
                ) { Text("Save Changes") }
            } else {
                Button(onClick = onDismiss) { Text("Done") }
            }
        },
        dismissButton = {
            if (tab == 0) TextButton(onClick = onDismiss) { Text("Close") }
        }
    )
}

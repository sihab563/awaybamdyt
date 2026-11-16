package com.example.ui.screens

import androidx.activity.compose.BackHandler
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.PickVisualMediaRequest
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
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
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.automirrored.filled.Send
import androidx.compose.material.icons.filled.AddPhotoAlternate
import androidx.compose.material.icons.filled.AttachFile
import androidx.compose.material.icons.filled.Block
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.ChatBubble
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Menu
import androidx.compose.material.icons.filled.PersonAdd
import androidx.compose.material.icons.filled.Search
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.ScrollableTabRow
import androidx.compose.material3.Surface
import androidx.compose.material3.Tab
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.example.data.model.DirectMessage
import com.example.data.model.FriendshipStatus
import com.example.data.model.OnlineStatus
import com.example.data.model.ServerRole
import com.example.ui.components.DiscordMessageCard
import com.example.ui.components.MessageUiModel
import com.example.ui.components.NexusAvatar
import com.example.ui.components.ReplyAndAttachmentPreview
import com.example.ui.theme.NexusBlue
import com.example.ui.theme.NexusDanger
import com.example.ui.theme.NexusPurple
import com.example.ui.theme.NexusPurpleGlow
import com.example.ui.theme.StatusOnline
import com.example.ui.viewmodel.NexusUiState
import com.example.ui.viewmodel.NexusViewModel

@Composable
fun FriendsAndDmScreen(
    state: NexusUiState,
    viewModel: NexusViewModel,
    showDrawerButton: Boolean,
    onOpenNavDrawer: () -> Unit,
    modifier: Modifier = Modifier
) {
    val activeThread = state.activeDmThread
    val myUid = state.currentUid.orEmpty()

    if (activeThread != null) {
        BackHandler { viewModel.openHomeDms(null) }
        val peerUid = activeThread.participantIds.firstOrNull { it != myUid }.orEmpty()
        val peerProfile = state.publicDirectory.firstOrNull { it.userId == peerUid }
        val peerName = peerProfile?.displayName
            ?: activeThread.participantNames[peerUid]
            ?: "Player"
        val isBlocked = peerUid in state.blockedUids

        var inputText by remember(activeThread.id) { mutableStateOf("") }
        var replyTarget by remember(activeThread.id) { mutableStateOf<DirectMessage?>(null) }
        var editingTarget by remember(activeThread.id) { mutableStateOf<DirectMessage?>(null) }

        val imagePicker = rememberLauncherForActivityResult(ActivityResultContracts.PickVisualMedia()) { uri ->
            if (uri != null) viewModel.attachImageFromUri(uri)
        }
        val filePicker = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocument()) { uri ->
            if (uri != null) viewModel.attachFileFromUri(uri)
        }

        val listState = rememberLazyListState()
        LaunchedEffect(state.activeDmMessages.size, activeThread.id) {
            if (state.activeDmMessages.isNotEmpty()) {
                listState.animateScrollToItem(state.activeDmMessages.lastIndex)
            }
        }

        Column(modifier = modifier.fillMaxSize().background(MaterialTheme.colorScheme.surface)) {
            Surface(color = MaterialTheme.colorScheme.surface, shadowElevation = 2.dp) {
                Row(
                    modifier = Modifier.fillMaxWidth().padding(horizontal = 10.dp, vertical = 10.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    IconButton(onClick = { viewModel.openHomeDms(null) }) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, "Back to Friends")
                    }
                    NexusAvatar(
                        avatarId = peerProfile?.avatarId ?: "cyber_knight",
                        customAvatarData = peerProfile?.customAvatarData.orEmpty(),
                        status = peerProfile?.statusEnum ?: OnlineStatus.OFFLINE,
                        size = 36.dp
                    )
                    Spacer(Modifier.width(10.dp))
                    Column(modifier = Modifier.weight(1f)) {
                        Text(peerName, style = MaterialTheme.typography.titleMedium)
                        Text(
                            text = if (isBlocked) "Blocked" else (peerProfile?.statusEnum?.label ?: "Offline"),
                            style = MaterialTheme.typography.labelSmall,
                            color = if (isBlocked) NexusDanger else MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                    IconButton(
                        onClick = {
                            if (isBlocked) viewModel.unblockUser(peerUid)
                            else viewModel.blockUser(peerUid, peerName, peerProfile?.gamerTag ?: "User")
                        }
                    ) {
                        Icon(
                            Icons.Default.Block,
                            contentDescription = if (isBlocked) "Unblock" else "Block user",
                            tint = if (isBlocked) NexusDanger else MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                }
            }
            HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant)

            LazyColumn(
                state = listState,
                modifier = Modifier.weight(1f).fillMaxWidth(),
                contentPadding = PaddingValues(vertical = 10.dp)
            ) {
                items(state.activeDmMessages, key = { it.id }) { msg ->
                    DiscordMessageCard(
                        msg = MessageUiModel(
                            id = msg.id,
                            senderId = msg.senderId,
                            senderName = msg.senderName,
                            senderTag = msg.senderTag,
                            senderAvatarId = msg.senderAvatarId,
                            senderRole = ServerRole.MEMBER,
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
                        canModerate = false,
                        playingVoiceId = null,
                        onReply = { editingTarget = null; replyTarget = msg },
                        onReact = { emoji -> viewModel.toggleDmReaction(msg, emoji) },
                        onEdit = { replyTarget = null; editingTarget = msg; inputText = msg.text },
                        onDelete = { viewModel.deleteDirectMessage(msg) },
                        onPlayVoice = { _, _ -> },
                        onAuthorClick = {},
                        onFileSavedSnack = viewModel::showSnack
                    )
                }
            }

            ReplyAndAttachmentPreview(
                replyAuthor = replyTarget?.senderName,
                replySnippet = replyTarget?.text,
                editingText = editingTarget?.text,
                pendingAttachment = state.pendingAttachment,
                onClearReply = { replyTarget = null },
                onClearEditing = { editingTarget = null; inputText = "" },
                onClearAttachment = viewModel::clearPendingAttachment
            )

            Surface(color = MaterialTheme.colorScheme.surfaceContainerLow, modifier = Modifier.fillMaxWidth()) {
                Row(
                    modifier = Modifier.padding(horizontal = 10.dp, vertical = 8.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    IconButton(onClick = {
                        imagePicker.launch(PickVisualMediaRequest(ActivityResultContracts.PickVisualMedia.ImageOnly))
                    }) { Icon(Icons.Default.AddPhotoAlternate, "Attach image", tint = NexusPurpleGlow) }
                    IconButton(onClick = { filePicker.launch(arrayOf("*/*")) }) {
                        Icon(Icons.Default.AttachFile, "Attach file", tint = NexusBlue)
                    }
                    OutlinedTextField(
                        value = inputText,
                        onValueChange = { if (it.length <= 1500) inputText = it },
                        placeholder = { Text("Message @$peerName") },
                        maxLines = 4,
                        shape = RoundedCornerShape(16.dp),
                        modifier = Modifier.weight(1f).testTag("dm_message_input")
                    )
                    Spacer(Modifier.width(6.dp))
                    IconButton(
                        onClick = {
                            val editing = editingTarget
                            if (editing != null) {
                                viewModel.editDirectMessage(editing, inputText)
                                editingTarget = null
                                inputText = ""
                            } else {
                                viewModel.sendDirectMessage(inputText, replyTarget)
                                replyTarget = null
                                inputText = ""
                            }
                        },
                        enabled = (inputText.isNotBlank() || state.pendingAttachment != null) && !isBlocked,
                        modifier = Modifier
                            .size(48.dp)
                            .clip(CircleShape)
                            .background(NexusPurple)
                            .testTag("send_dm_button")
                    ) {
                        Icon(Icons.AutoMirrored.Filled.Send, "Send DM", tint = Color.White)
                    }
                }
            }
        }
        return
    }

    // Friends & DM Hub View
    var selectedTab by remember { mutableIntStateOf(0) }
    var searchQuery by remember { mutableStateOf("") }

    val acceptedFriends = remember(state.friendships, myUid, state.publicDirectory, state.blockedUids) {
        state.friendships.filter { it.statusEnum == FriendshipStatus.ACCEPTED }
            .mapNotNull { fr ->
                val otherUid = fr.otherUserId(myUid)
                if (otherUid in state.blockedUids) null
                else fr to state.publicDirectory.firstOrNull { it.userId == otherUid }
            }
    }
    val pendingIncoming = remember(state.friendships, myUid) {
        state.friendships.filter { it.statusEnum == FriendshipStatus.PENDING && it.recipientId == myUid }
    }
    val pendingOutgoing = remember(state.friendships, myUid) {
        state.friendships.filter { it.statusEnum == FriendshipStatus.PENDING && it.requesterId == myUid }
    }

    Column(modifier = modifier.fillMaxSize().background(MaterialTheme.colorScheme.surface)) {
        Surface(color = MaterialTheme.colorScheme.surface, shadowElevation = 2.dp) {
            Row(
                modifier = Modifier.fillMaxWidth().padding(horizontal = 14.dp, vertical = 12.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                if (showDrawerButton) {
                    IconButton(onClick = onOpenNavDrawer) {
                        Icon(Icons.Default.Menu, "Open Servers")
                    }
                }
                Text("Friends & Direct Messages", style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.Bold)
            }
        }

        ScrollableTabRow(selectedTabIndex = selectedTab, edgePadding = 12.dp) {
            Tab(selected = selectedTab == 0, onClick = { selectedTab = 0 }, text = {
                Text("Online (${acceptedFriends.count { it.second?.statusEnum != OnlineStatus.OFFLINE }})")
            })
            Tab(selected = selectedTab == 1, onClick = { selectedTab = 1 }, text = {
                Text("All (${acceptedFriends.size})")
            })
            Tab(selected = selectedTab == 2, onClick = { selectedTab = 2 }, text = {
                Text("Pending (${pendingIncoming.size + pendingOutgoing.size})")
            })
            Tab(selected = selectedTab == 3, onClick = { selectedTab = 3 }, text = {
                Text("Add Friends")
            })
            Tab(selected = selectedTab == 4, onClick = { selectedTab = 4 }, text = {
                Text("Blocked (${state.blockedUsers.size})")
            })
        }

        if (state.dmThreads.isNotEmpty() && selectedTab <= 1) {
            Text(
                "RECENT DIRECT MESSAGES",
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.padding(start = 16.dp, top = 12.dp, bottom = 4.dp)
            )
            state.dmThreads.take(4).forEach { thread ->
                val otherUid = thread.participantIds.firstOrNull { it != myUid }.orEmpty()
                if (otherUid !in state.blockedUids) {
                    val peer = state.publicDirectory.firstOrNull { it.userId == otherUid }
                    val peerName = peer?.displayName ?: thread.participantNames[otherUid] ?: "Player"
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .clickable { viewModel.openHomeDms(thread.id) }
                            .padding(horizontal = 16.dp, vertical = 8.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        NexusAvatar(
                            avatarId = peer?.avatarId ?: "cyber_knight",
                            customAvatarData = peer?.customAvatarData.orEmpty(),
                            status = peer?.statusEnum ?: OnlineStatus.OFFLINE,
                            size = 38.dp
                        )
                        Spacer(Modifier.width(12.dp))
                        Column(modifier = Modifier.weight(1f)) {
                            Text(peerName, style = MaterialTheme.typography.titleSmall)
                            Text(
                                thread.lastMessage,
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis
                            )
                        }
                        Icon(Icons.Default.ChatBubble, "Open DM", tint = NexusBlue, modifier = Modifier.size(18.dp))
                    }
                }
            }
            HorizontalDivider(modifier = Modifier.padding(vertical = 6.dp), color = MaterialTheme.colorScheme.outlineVariant)
        }

        when (selectedTab) {
            0, 1 -> {
                val filtered = if (selectedTab == 0) {
                    acceptedFriends.filter { it.second?.statusEnum != OnlineStatus.OFFLINE }
                } else acceptedFriends

                LazyColumn(
                    modifier = Modifier.weight(1f),
                    contentPadding = PaddingValues(14.dp),
                    verticalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    if (filtered.isEmpty()) {
                        item {
                            Text(
                                text = if (selectedTab == 0) "No friends currently online. Switch to Add Friends to connect with squadmates!"
                                else "You haven't added any friends yet.",
                                style = MaterialTheme.typography.bodyMedium,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                    }
                    items(filtered, key = { it.first.id }) { (friendship, profile) ->
                        val otherUid = friendship.otherUserId(myUid)
                        val name = profile?.displayName ?: if (friendship.requesterId == myUid) friendship.recipientName else friendship.requesterName
                        val tag = profile?.gamerTag ?: if (friendship.requesterId == myUid) friendship.recipientTag else friendship.requesterTag
                        Surface(
                            color = MaterialTheme.colorScheme.surfaceContainerHigh,
                            shape = RoundedCornerShape(14.dp),
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            Row(
                                modifier = Modifier.padding(12.dp),
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                NexusAvatar(
                                    avatarId = profile?.avatarId ?: "cyber_knight",
                                    customAvatarData = profile?.customAvatarData.orEmpty(),
                                    status = profile?.statusEnum ?: OnlineStatus.OFFLINE,
                                    size = 42.dp
                                )
                                Spacer(Modifier.width(12.dp))
                                Column(modifier = Modifier.weight(1f)) {
                                    Text(name, style = MaterialTheme.typography.titleSmall)
                                    Text(
                                        "@$tag • ${profile?.statusEnum?.label ?: "Offline"}",
                                        style = MaterialTheme.typography.bodySmall,
                                        color = if (profile?.statusEnum == OnlineStatus.ONLINE) StatusOnline else MaterialTheme.colorScheme.onSurfaceVariant
                                    )
                                }
                                IconButton(onClick = {
                                    profile?.let { viewModel.openDmWithUser(it) }
                                }) {
                                    Icon(Icons.Default.ChatBubble, "Message", tint = NexusPurpleGlow)
                                }
                                IconButton(onClick = { viewModel.blockUser(otherUid, name, tag) }) {
                                    Icon(Icons.Default.Block, "Block", tint = NexusDanger)
                                }
                            }
                        }
                    }
                }
            }

            2 -> LazyColumn(
                modifier = Modifier.weight(1f),
                contentPadding = PaddingValues(14.dp),
                verticalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                items(pendingIncoming, key = { "in_${it.id}" }) { fr ->
                    Surface(
                        color = MaterialTheme.colorScheme.surfaceContainerHigh,
                        shape = RoundedCornerShape(14.dp),
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Row(modifier = Modifier.padding(12.dp), verticalAlignment = Alignment.CenterVertically) {
                            NexusAvatar(fr.requesterAvatarId, size = 40.dp)
                            Spacer(Modifier.width(10.dp))
                            Column(modifier = Modifier.weight(1f)) {
                                Text(fr.requesterName, style = MaterialTheme.typography.titleSmall)
                                Text("Incoming request • @${fr.requesterTag}", style = MaterialTheme.typography.bodySmall)
                            }
                            IconButton(onClick = { viewModel.respondToFriendRequest(fr, true) }) {
                                Icon(Icons.Default.Check, "Accept", tint = StatusOnline)
                            }
                            IconButton(onClick = { viewModel.respondToFriendRequest(fr, false) }) {
                                Icon(Icons.Default.Close, "Decline", tint = NexusDanger)
                            }
                        }
                    }
                }
                items(pendingOutgoing, key = { "out_${it.id}" }) { fr ->
                    Surface(
                        color = MaterialTheme.colorScheme.surfaceContainerLow,
                        shape = RoundedCornerShape(14.dp),
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Row(modifier = Modifier.padding(12.dp), verticalAlignment = Alignment.CenterVertically) {
                            NexusAvatar(fr.recipientAvatarId, size = 38.dp)
                            Spacer(Modifier.width(10.dp))
                            Column(modifier = Modifier.weight(1f)) {
                                Text(fr.recipientName, style = MaterialTheme.typography.titleSmall)
                                Text("Outgoing request pending", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                            }
                            TextButton(onClick = { viewModel.removeFriend(fr.id) }) { Text("Cancel") }
                        }
                    }
                }
            }

            3 -> Column(modifier = Modifier.weight(1f).padding(14.dp)) {
                OutlinedTextField(
                    value = searchQuery,
                    onValueChange = { searchQuery = it },
                    leadingIcon = { Icon(Icons.Default.Search, null) },
                    label = { Text("Search Nexus players by name or @tag") },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth().testTag("search_players_input")
                )
                Spacer(Modifier.height(10.dp))
                val players = remember(state.publicDirectory, searchQuery, myUid, state.blockedUids) {
                    val q = searchQuery.trim().lowercase()
                    state.publicDirectory.filter { p ->
                        p.userId != myUid &&
                            p.userId !in state.blockedUids &&
                            (q.isEmpty() || p.displayName.lowercase().contains(q) || p.gamerTag.lowercase().contains(q))
                    }
                }
                LazyColumn(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    items(players, key = { it.userId }) { p ->
                        val relation = state.friendships.firstOrNull { p.userId in it.userIds }
                        Surface(
                            color = MaterialTheme.colorScheme.surfaceContainerHigh,
                            shape = RoundedCornerShape(14.dp),
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            Row(modifier = Modifier.padding(12.dp), verticalAlignment = Alignment.CenterVertically) {
                                NexusAvatar(p.avatarId, p.customAvatarData, p.statusEnum, size = 40.dp)
                                Spacer(Modifier.width(10.dp))
                                Column(modifier = Modifier.weight(1f)) {
                                    Text(p.displayName, style = MaterialTheme.typography.titleSmall)
                                    Text("@${p.gamerTag} • ${p.statusEnum.label}", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                                }
                                IconButton(onClick = { viewModel.openDmWithUser(p) }) {
                                    Icon(Icons.Default.ChatBubble, "Direct Message", tint = NexusBlue)
                                }
                                if (relation == null || relation.statusEnum == FriendshipStatus.DECLINED) {
                                    IconButton(onClick = { viewModel.sendFriendRequest(p) }) {
                                        Icon(Icons.Default.PersonAdd, "Send Friend Request", tint = NexusPurpleGlow)
                                    }
                                } else {
                                    Text(
                                        text = if (relation.statusEnum == FriendshipStatus.ACCEPTED) "Friend" else "Pending",
                                        style = MaterialTheme.typography.labelSmall,
                                        color = StatusOnline
                                    )
                                }
                            }
                        }
                    }
                }
            }

            4 -> LazyColumn(
                modifier = Modifier.weight(1f),
                contentPadding = PaddingValues(14.dp),
                verticalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                if (state.blockedUsers.isEmpty()) {
                    item {
                        Text(
                            "No blocked users.",
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                }
                items(state.blockedUsers, key = { it.targetUid }) { blocked ->
                    Surface(
                        color = MaterialTheme.colorScheme.surfaceContainerHigh,
                        shape = RoundedCornerShape(14.dp),
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Row(modifier = Modifier.padding(12.dp), verticalAlignment = Alignment.CenterVertically) {
                            Icon(Icons.Default.Block, null, tint = NexusDanger)
                            Spacer(Modifier.width(10.dp))
                            Column(modifier = Modifier.weight(1f)) {
                                Text(blocked.targetName, style = MaterialTheme.typography.titleSmall)
                                Text("@${blocked.targetTag}", style = MaterialTheme.typography.bodySmall)
                            }
                            TextButton(onClick = { viewModel.unblockUser(blocked.targetUid) }) {
                                Text("Unblock")
                            }
                        }
                    }
                }
            }
        }
    }
}

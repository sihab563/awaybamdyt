package com.example.ui.viewmodel

import android.app.Application
import android.net.Uri
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.example.auth.authStateFlow
import com.example.data.model.BlockedUser
import com.example.data.model.ChannelMessage
import com.example.data.model.ChannelType
import com.example.data.model.DirectMessage
import com.example.data.model.DirectMessageThread
import com.example.data.model.Friendship
import com.example.data.model.GamerProfile
import com.example.data.model.NexusNotification
import com.example.data.model.NexusServer
import com.example.data.model.NotificationType
import com.example.data.model.OnlineStatus
import com.example.data.model.ServerChannel
import com.example.data.model.ServerMember
import com.example.data.model.ServerRole
import com.example.data.model.ServerVisibility
import com.example.data.model.VoiceMember
import com.example.data.preferences.ThemePreferences
import com.example.data.repository.NexusServerRepository
import com.example.data.repository.NexusSocialRepository
import com.example.data.unwrapFirestoreMessage
import com.example.data.util.AttachmentHelper
import com.example.data.util.PendingAttachment
import com.example.ui.audio.VoiceEngine
import com.google.firebase.Firebase
import com.google.firebase.auth.auth
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

enum class NexusSection {
    HOME_DMS,
    SERVER,
    DISCOVERY,
    NOTIFICATIONS,
    PROFILE_SETTINGS
}

data class ActiveVoiceSession(
    val serverId: String,
    val serverName: String,
    val channelId: String,
    val channelName: String,
    val isMuted: Boolean = true,
    val isDeafened: Boolean = false
)

data class NexusUiState(
    val authInitialized: Boolean = false,
    val currentUid: String? = null,
    val currentProfile: GamerProfile? = null,
    val activeSection: NexusSection = NexusSection.HOME_DMS,
    val selectedServerId: String? = null,
    val selectedChannelId: String? = null,
    val selectedDmThreadId: String? = null,
    val joinedServers: List<NexusServer> = emptyList(),
    val publicServers: List<NexusServer> = emptyList(),
    val activeServerMembers: List<ServerMember> = emptyList(),
    val activeServerChannels: List<ServerChannel> = emptyList(),
    val activeChannelMessages: List<ChannelMessage> = emptyList(),
    val activeVoiceRoomMembers: List<VoiceMember> = emptyList(),
    val activeVoiceSession: ActiveVoiceSession? = null,
    val publicDirectory: List<GamerProfile> = emptyList(),
    val friendships: List<Friendship> = emptyList(),
    val blockedUsers: List<BlockedUser> = emptyList(),
    val dmThreads: List<DirectMessageThread> = emptyList(),
    val activeDmMessages: List<DirectMessage> = emptyList(),
    val notifications: List<NexusNotification> = emptyList(),
    val pendingAttachment: PendingAttachment? = null,
    val isBusy: Boolean = false,
    val errorBanner: String? = null,
    val snackbarMessage: String? = null
) {
    val activeServer: NexusServer?
        get() = joinedServers.firstOrNull { it.id == selectedServerId }

    val activeChannel: ServerChannel?
        get() = activeServerChannels.firstOrNull { it.id == selectedChannelId }

    val activeDmThread: DirectMessageThread?
        get() = dmThreads.firstOrNull { it.id == selectedDmThreadId }

    val blockedUids: Set<String>
        get() = blockedUsers.map { it.targetUid }.toSet()

    val unreadNotificationsCount: Int
        get() = notifications.count { !it.isRead }

    val myRoleInActiveServer: ServerRole
        get() {
            val uid = currentUid ?: return ServerRole.MEMBER
            return activeServer?.roleFor(uid) ?: ServerRole.MEMBER
        }
}

@OptIn(ExperimentalCoroutinesApi::class)
class NexusViewModel(application: Application) : AndroidViewModel(application) {

    private val prefs = ThemePreferences(application)
    private val serverRepo = NexusServerRepository(application)
    private val socialRepo = NexusSocialRepository(application)
    val voiceEngine = VoiceEngine(application)

    val isDarkTheme: StateFlow<Boolean> = prefs.isDarkTheme
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), true)

    val isSoundEnabled: StateFlow<Boolean> = prefs.isSoundEnabled
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), true)

    val isHapticsEnabled: StateFlow<Boolean> = prefs.isHapticsEnabled
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), true)

    val isNotificationsEnabled: StateFlow<Boolean> = prefs.isNotificationsEnabled
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), true)

    val isAutoPlayVoiceEnabled: StateFlow<Boolean> = prefs.isAutoPlayVoiceEnabled
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), true)

    private val _localState = MutableStateFlow(NexusUiState())
    private val authUidFlow = Firebase.auth.authStateFlow().map { it?.uid }

    private val selectedServerIdFlow = MutableStateFlow<String?>(null)
    private val selectedChannelIdFlow = MutableStateFlow<String?>(null)
    private val selectedDmThreadIdFlow = MutableStateFlow<String?>(null)
    private val activeVoiceSessionFlow = MutableStateFlow<ActiveVoiceSession?>(null)

    private val coreStreams = authUidFlow.flatMapLatest { uid ->
        if (uid == null) {
            flowOf(CoreStreamBundle(uid = null))
        } else {
            combine(
                socialRepo.observeProfile(uid).catch { emit(null) },
                serverRepo.observeJoinedServers(uid).catch { emit(emptyList()) },
                serverRepo.observePublicServers().catch { emit(emptyList()) },
                socialRepo.observePublicDirectory().catch { emit(emptyList()) },
                socialRepo.observeFriendships(uid).catch { emit(emptyList()) }
            ) { profile, joined, pubServers, directory, friends ->
                CoreStreamBundle(uid, profile, joined, pubServers, directory, friends)
            }
        }
    }

    private val socialStreams = authUidFlow.flatMapLatest { uid ->
        if (uid == null) {
            flowOf(SocialStreamBundle())
        } else {
            combine(
                socialRepo.observeBlockedUsers(uid).catch { emit(emptyList()) },
                socialRepo.observeDmThreads(uid).catch { emit(emptyList()) },
                socialRepo.observeNotifications(uid).catch { emit(emptyList()) }
            ) { blocked, threads, notifs ->
                SocialStreamBundle(blocked, threads, notifs)
            }
        }
    }

    private val activeServerMembersFlow = combine(authUidFlow, selectedServerIdFlow) { uid, sId ->
        uid to sId
    }.flatMapLatest { (uid, sId) ->
        if (uid == null || sId.isNullOrBlank()) flowOf(emptyList())
        else serverRepo.observeServerMembers(sId).catch { emit(emptyList()) }
    }

    private val activeServerChannelsFlow = combine(authUidFlow, selectedServerIdFlow) { uid, sId ->
        uid to sId
    }.flatMapLatest { (uid, sId) ->
        if (uid == null || sId.isNullOrBlank()) flowOf(emptyList())
        else serverRepo.observeChannels(sId).catch { emit(emptyList()) }
    }

    private val activeChannelMessagesFlow = combine(
        authUidFlow,
        selectedServerIdFlow,
        selectedChannelIdFlow
    ) { uid, sId, cId -> Triple(uid, sId, cId) }.flatMapLatest { (uid, sId, cId) ->
        if (uid == null || sId.isNullOrBlank() || cId.isNullOrBlank()) flowOf(emptyList())
        else serverRepo.observeChannelMessages(sId, cId).catch { emit(emptyList()) }
    }

    private val activeVoiceMembersFlow = combine(
        authUidFlow,
        selectedServerIdFlow,
        selectedChannelIdFlow,
        activeVoiceSessionFlow
    ) { uid, sId, cId, session ->
        val targetServer = session?.serverId ?: sId
        val targetChannel = session?.channelId ?: cId
        Triple(uid, targetServer, targetChannel)
    }.flatMapLatest { (uid, sId, cId) ->
        if (uid == null || sId.isNullOrBlank() || cId.isNullOrBlank()) flowOf(emptyList())
        else serverRepo.observeVoiceMembers(sId, cId).catch { emit(emptyList()) }
    }

    private val activeDmMessagesFlow = combine(authUidFlow, selectedDmThreadIdFlow) { uid, tId ->
        uid to tId
    }.flatMapLatest { (uid, tId) ->
        if (uid == null || tId.isNullOrBlank()) flowOf(emptyList())
        else socialRepo.observeDmMessages(tId).catch { emit(emptyList()) }
    }

    val uiState: StateFlow<NexusUiState> = combine(
        _localState,
        coreStreams,
        socialStreams,
        combine(
            activeServerMembersFlow,
            activeServerChannelsFlow,
            activeChannelMessagesFlow,
            activeVoiceMembersFlow,
            activeDmMessagesFlow
        ) { members, channels, chMsgs, voiceMems, dmMsgs ->
            DetailStreamBundle(members, channels, chMsgs, voiceMems, dmMsgs)
        },
        activeVoiceSessionFlow
    ) { local, core, social, detail, voiceSession ->
        // Auto-select first channel if current selected channel isn't in server
        val validChannelId = if (detail.channels.any { it.id == local.selectedChannelId }) {
            local.selectedChannelId
        } else {
            detail.channels.firstOrNull { it.typeEnum == ChannelType.TEXT }?.id
                ?: detail.channels.firstOrNull()?.id
        }
        if (validChannelId != selectedChannelIdFlow.value && local.selectedServerId != null) {
            selectedChannelIdFlow.value = validChannelId
        }

        local.copy(
            authInitialized = true,
            currentUid = core.uid,
            currentProfile = core.profile,
            joinedServers = core.joinedServers,
            publicServers = core.publicServers,
            publicDirectory = core.directory,
            friendships = core.friendships,
            blockedUsers = social.blockedUsers,
            dmThreads = social.dmThreads,
            notifications = social.notifications,
            activeServerMembers = detail.members,
            activeServerChannels = detail.channels,
            selectedChannelId = validChannelId,
            activeChannelMessages = detail.channelMessages,
            activeVoiceRoomMembers = detail.voiceMembers,
            activeDmMessages = detail.dmMessages,
            activeVoiceSession = voiceSession
        )
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), NexusUiState())

    init {
        viewModelScope.launch {
            authUidFlow.collect { uid ->
                if (uid == null) {
                    voiceEngine.release()
                    activeVoiceSessionFlow.value = null
                    selectedServerIdFlow.value = null
                    selectedChannelIdFlow.value = null
                    selectedDmThreadIdFlow.value = null
                    _localState.update {
                        it.copy(
                            authInitialized = true,
                            currentUid = null,
                            selectedServerId = null,
                            selectedChannelId = null,
                            selectedDmThreadId = null,
                            activeSection = NexusSection.HOME_DMS,
                            pendingAttachment = null
                        )
                    }
                } else {
                    socialRepo.ensureSignedInProfile()
                }
            }
        }
    }

    fun onAuthCompleted(preferredTag: String? = null, preferredAvatarId: String? = null) {
        viewModelScope.launch {
            _localState.update { it.copy(isBusy = true) }
            socialRepo.ensureSignedInProfile(preferredTag, preferredAvatarId)
                .onFailure { err ->
                    _localState.update { it.copy(errorBanner = unwrapFirestoreMessage(err, "Profile setup failed")) }
                }
            _localState.update { it.copy(isBusy = false) }
        }
    }

    fun selectSection(section: NexusSection) {
        _localState.update { it.copy(activeSection = section) }
    }

    fun openHomeDms(threadId: String? = null) {
        selectedDmThreadIdFlow.value = threadId
        _localState.update {
            it.copy(
                activeSection = NexusSection.HOME_DMS,
                selectedDmThreadId = threadId
            )
        }
    }

    fun selectServer(serverId: String) {
        selectedServerIdFlow.value = serverId
        selectedChannelIdFlow.value = null
        _localState.update {
            it.copy(
                activeSection = NexusSection.SERVER,
                selectedServerId = serverId,
                selectedChannelId = null,
                pendingAttachment = null
            )
        }
    }

    fun selectChannel(channelId: String) {
        selectedChannelIdFlow.value = channelId
        _localState.update {
            it.copy(
                activeSection = NexusSection.SERVER,
                selectedChannelId = channelId,
                pendingAttachment = null
            )
        }
    }

    // --- Attachments ---

    fun attachImageFromUri(uri: Uri) {
        viewModelScope.launch {
            _localState.update { it.copy(isBusy = true) }
            val result = withContext(Dispatchers.IO) {
                AttachmentHelper.compressChatImageUri(getApplication(), uri)
            }
            result.onSuccess { pending ->
                _localState.update { it.copy(pendingAttachment = pending, isBusy = false) }
            }.onFailure { err ->
                _localState.update { it.copy(isBusy = false, snackbarMessage = err.message ?: "Failed to attach image") }
            }
        }
    }

    fun attachFileFromUri(uri: Uri) {
        viewModelScope.launch {
            _localState.update { it.copy(isBusy = true) }
            val result = withContext(Dispatchers.IO) {
                AttachmentHelper.readDocumentUri(getApplication(), uri)
            }
            result.onSuccess { pending ->
                _localState.update { it.copy(pendingAttachment = pending, isBusy = false) }
            }.onFailure { err ->
                _localState.update { it.copy(isBusy = false, snackbarMessage = err.message ?: "Failed to attach file") }
            }
        }
    }

    fun clearPendingAttachment() {
        _localState.update { it.copy(pendingAttachment = null) }
    }

    fun uploadCustomAvatar(uri: Uri, onReady: (String) -> Unit) {
        viewModelScope.launch {
            val result = withContext(Dispatchers.IO) {
                AttachmentHelper.compressAvatarUri(getApplication(), uri)
            }
            result.onSuccess(onReady)
                .onFailure { err -> showSnack(err.message ?: "Could not load photo") }
        }
    }

    // --- Server CRUD & Invites ---

    fun createServer(
        name: String,
        description: String,
        iconEmoji: String,
        visibility: ServerVisibility
    ) {
        val profile = uiState.value.currentProfile ?: return
        if (name.trim().length < 2) {
            showSnack("Server name must be at least 2 characters.")
            return
        }
        viewModelScope.launch {
            _localState.update { it.copy(isBusy = true) }
            serverRepo.createServer(name, description, iconEmoji, "purple_blue", visibility, profile)
                .onSuccess { newServerId ->
                    selectServer(newServerId)
                    showSnack("Created $name!")
                }
                .onFailure { showSnack(unwrapFirestoreMessage(it, "Could not create server")) }
            _localState.update { it.copy(isBusy = false) }
        }
    }

    fun updateServer(
        server: NexusServer,
        newName: String,
        newDescription: String,
        newEmoji: String,
        newVisibility: ServerVisibility,
        regenerateInvite: Boolean
    ) {
        viewModelScope.launch {
            serverRepo.updateServer(server, newName, newDescription, newEmoji, newVisibility, regenerateInvite)
                .onSuccess { showSnack("Server settings updated") }
                .onFailure { showSnack(unwrapFirestoreMessage(it, "Failed to update server")) }
        }
    }

    fun deleteServer(server: NexusServer) {
        viewModelScope.launch {
            if (activeVoiceSessionFlow.value?.serverId == server.id) {
                leaveVoiceRoom()
            }
            serverRepo.deleteServer(server)
                .onSuccess {
                    selectedServerIdFlow.value = null
                    selectedChannelIdFlow.value = null
                    _localState.update {
                        it.copy(
                            activeSection = NexusSection.HOME_DMS,
                            selectedServerId = null,
                            selectedChannelId = null,
                            snackbarMessage = "Deleted ${server.name}"
                        )
                    }
                }
                .onFailure { showSnack(unwrapFirestoreMessage(it, "Could not delete server")) }
        }
    }

    fun joinPublicServer(server: NexusServer) {
        val profile = uiState.value.currentProfile ?: return
        if (uiState.value.currentUid in server.memberUids) {
            selectServer(server.id)
            return
        }
        viewModelScope.launch {
            serverRepo.joinServerById(server.id, profile)
                .onSuccess { id ->
                    selectServer(id)
                    showSnack("Joined ${server.name}!")
                }
                .onFailure { showSnack(unwrapFirestoreMessage(it, "Could not join server")) }
        }
    }

    fun joinByInviteCode(code: String) {
        val profile = uiState.value.currentProfile ?: return
        val normalized = code.trim().removePrefix("https://nexus.gg/").removePrefix("nexus://invite/").trim().uppercase()
        val existing = uiState.value.joinedServers.firstOrNull { it.inviteCode.equals(normalized, ignoreCase = true) }
        if (existing != null) {
            selectServer(existing.id)
            showSnack("Opened ${existing.name}")
            return
        }
        viewModelScope.launch {
            _localState.update { it.copy(isBusy = true) }
            serverRepo.joinServerByInviteCode(code, profile)
                .onSuccess { srvId ->
                    selectServer(srvId)
                    showSnack("Joined server via invite!")
                }
                .onFailure { showSnack(unwrapFirestoreMessage(it, "Invalid or banned invite")) }
            _localState.update { it.copy(isBusy = false) }
        }
    }

    fun leaveServer(server: NexusServer) {
        viewModelScope.launch {
            if (activeVoiceSessionFlow.value?.serverId == server.id) {
                leaveVoiceRoom()
            }
            serverRepo.leaveServer(server)
                .onSuccess {
                    selectedServerIdFlow.value = null
                    selectedChannelIdFlow.value = null
                    _localState.update {
                        it.copy(
                            activeSection = NexusSection.HOME_DMS,
                            selectedServerId = null,
                            selectedChannelId = null,
                            snackbarMessage = "Left ${server.name}"
                        )
                    }
                }
                .onFailure { showSnack(unwrapFirestoreMessage(it, "Could not leave server")) }
        }
    }

    // --- Channels ---

    fun createChannel(
        serverId: String,
        name: String,
        topic: String,
        type: ChannelType,
        categoryName: String,
        adminOnlyPost: Boolean
    ) {
        if (name.isBlank()) {
            showSnack("Channel name required")
            return
        }
        val nextPos = (uiState.value.activeServerChannels.maxOfOrNull { it.position } ?: 0) + 1
        viewModelScope.launch {
            serverRepo.createChannel(serverId, name, topic, type, categoryName, nextPos, adminOnlyPost)
                .onSuccess { chId ->
                    selectChannel(chId)
                    showSnack("Created #$name")
                }
                .onFailure { showSnack(unwrapFirestoreMessage(it, "Could not create channel")) }
        }
    }

    fun updateChannel(
        serverId: String,
        channel: ServerChannel,
        name: String,
        topic: String,
        type: ChannelType,
        categoryName: String,
        adminOnlyPost: Boolean
    ) {
        viewModelScope.launch {
            serverRepo.updateChannel(serverId, channel.id, name, topic, type, categoryName, channel.position, adminOnlyPost)
                .onSuccess { showSnack("Updated #${name.lowercase()}") }
                .onFailure { showSnack(unwrapFirestoreMessage(it, "Could not update channel")) }
        }
    }

    fun deleteChannel(serverId: String, channelId: String) {
        viewModelScope.launch {
            if (activeVoiceSessionFlow.value?.channelId == channelId) {
                leaveVoiceRoom()
            }
            serverRepo.deleteChannel(serverId, channelId)
                .onSuccess {
                    if (selectedChannelIdFlow.value == channelId) selectedChannelIdFlow.value = null
                    showSnack("Channel deleted")
                }
                .onFailure { showSnack(unwrapFirestoreMessage(it, "Could not delete channel")) }
        }
    }

    // --- Channel Chat & Voice Room ---

    fun sendChannelMessage(
        text: String,
        replyTo: ChannelMessage? = null
    ) {
        val state = uiState.value
        val server = state.activeServer ?: return
        val channel = state.activeChannel ?: return
        val profile = state.currentProfile ?: return
        val attachment = state.pendingAttachment
        if (text.isBlank() && attachment == null) return

        viewModelScope.launch {
            _localState.update { it.copy(pendingAttachment = null) }
            serverRepo.sendChannelMessage(
                serverId = server.id,
                channelId = channel.id,
                senderProfile = profile,
                senderRole = state.myRoleInActiveServer,
                text = text,
                replyTo = replyTo,
                attachment = attachment
            ).onFailure { err ->
                showSnack(unwrapFirestoreMessage(err, "Message failed to send"))
            }
        }
    }

    fun editChannelMessage(message: ChannelMessage, newText: String) {
        if (newText.isBlank()) return
        viewModelScope.launch {
            serverRepo.editChannelMessage(message.serverId, message.channelId, message.id, newText)
                .onFailure { showSnack(unwrapFirestoreMessage(it, "Could not edit message")) }
        }
    }

    fun deleteChannelMessage(message: ChannelMessage) {
        viewModelScope.launch {
            serverRepo.deleteChannelMessage(message.serverId, message.channelId, message.id)
                .onSuccess { showSnack("Message deleted") }
                .onFailure { showSnack(unwrapFirestoreMessage(it, "Could not delete message")) }
        }
    }

    fun toggleChannelReaction(message: ChannelMessage, emoji: String) {
        viewModelScope.launch {
            serverRepo.toggleChannelReaction(message.serverId, message.channelId, message, emoji)
                .onFailure { showSnack(unwrapFirestoreMessage(it, "Reaction failed")) }
        }
    }

    fun joinVoiceChannel(server: NexusServer, channel: ServerChannel, hasMicPermission: Boolean) {
        val profile = uiState.value.currentProfile ?: return
        viewModelScope.launch {
            val prev = activeVoiceSessionFlow.value
            if (prev != null && prev.channelId != channel.id) {
                voiceEngine.stopMicMonitor()
                serverRepo.leaveVoiceRoom(prev.serverId, prev.channelId)
            }
            val startMuted = !hasMicPermission
            serverRepo.joinVoiceRoom(server.id, channel.id, profile, isMuted = startMuted)
                .onSuccess {
                    val nextSession = ActiveVoiceSession(
                        serverId = server.id,
                        serverName = server.name,
                        channelId = channel.id,
                        channelName = channel.name,
                        isMuted = startMuted,
                        isDeafened = false
                    )
                    activeVoiceSessionFlow.value = nextSession
                    if (!startMuted) {
                        startVoiceMonitorLoop(server.id, channel.id)
                    }
                    showSnack(if (startMuted) "Joined ${channel.name} (Muted)" else "Connected to ${channel.name}")
                }
                .onFailure { showSnack(unwrapFirestoreMessage(it, "Could not join voice room")) }
        }
    }

    fun toggleMute(hasMicPermission: Boolean) {
        val session = activeVoiceSessionFlow.value ?: return
        val nextMuted = if (!hasMicPermission) true else !session.isMuted
        activeVoiceSessionFlow.value = session.copy(isMuted = nextMuted)
        if (nextMuted) {
            voiceEngine.stopMicMonitor()
        } else {
            startVoiceMonitorLoop(session.serverId, session.channelId)
        }
        viewModelScope.launch {
            serverRepo.updateVoiceMemberState(
                session.serverId,
                session.channelId,
                isMuted = nextMuted,
                isDeafened = session.isDeafened,
                isSpeaking = false
            )
        }
    }

    fun toggleDeafen() {
        val session = activeVoiceSessionFlow.value ?: return
        val nextDeafened = !session.isDeafened
        val nextMuted = if (nextDeafened) true else session.isMuted
        if (nextMuted) voiceEngine.stopMicMonitor()
        activeVoiceSessionFlow.value = session.copy(isDeafened = nextDeafened, isMuted = nextMuted)
        viewModelScope.launch {
            serverRepo.updateVoiceMemberState(
                session.serverId,
                session.channelId,
                isMuted = nextMuted,
                isDeafened = nextDeafened,
                isSpeaking = false
            )
        }
    }

    fun leaveVoiceRoom() {
        val session = activeVoiceSessionFlow.value ?: return
        voiceEngine.stopMicMonitor()
        activeVoiceSessionFlow.value = null
        viewModelScope.launch {
            serverRepo.leaveVoiceRoom(session.serverId, session.channelId)
            showSnack("Disconnected from voice")
        }
    }

    fun sendPushToTalkVoiceMessage() {
        val state = uiState.value
        val server = state.activeServer ?: return
        val channel = state.activeChannel ?: return
        val profile = state.currentProfile ?: return
        val clip = voiceEngine.stopAndEncodePushToTalkClip() ?: return
        viewModelScope.launch {
            serverRepo.sendChannelMessage(
                serverId = server.id,
                channelId = channel.id,
                senderProfile = profile,
                senderRole = state.myRoleInActiveServer,
                text = "",
                voiceDataBase64 = clip.base64Wav,
                voiceDurationSec = clip.durationSec
            ).onFailure { showSnack(unwrapFirestoreMessage(it, "Failed to transmit voice clip")) }
        }
    }

    private fun startVoiceMonitorLoop(serverId: String, channelId: String) {
        voiceEngine.startMicMonitor { speaking ->
            val current = activeVoiceSessionFlow.value ?: return@startMicMonitor
            if (current.serverId == serverId && current.channelId == channelId && !current.isMuted) {
                viewModelScope.launch {
                    serverRepo.updateVoiceMemberState(
                        serverId,
                        channelId,
                        isMuted = false,
                        isDeafened = current.isDeafened,
                        isSpeaking = speaking
                    )
                }
            }
        }
    }

    // --- Moderation & Roles ---

    fun assignRole(server: NexusServer, targetUid: String, newRole: ServerRole) {
        val me = uiState.value.currentProfile ?: return
        viewModelScope.launch {
            serverRepo.assignMemberRole(server, targetUid, newRole)
                .onSuccess {
                    socialRepo.sendNotification(
                        recipientUid = targetUid,
                        senderName = me.displayName,
                        type = NotificationType.ROLE_ASSIGNED,
                        title = "Role Updated in ${server.name}",
                        body = "You are now ${newRole.label} in ${server.name}.",
                        targetServerId = server.id
                    )
                    showSnack("Updated role to ${newRole.label}")
                }
                .onFailure { showSnack(unwrapFirestoreMessage(it, "Role change failed")) }
        }
    }

    fun kickOrBanMember(server: NexusServer, targetUid: String, ban: Boolean) {
        val me = uiState.value.currentProfile ?: return
        viewModelScope.launch {
            serverRepo.kickOrBanMember(server, targetUid, ban)
                .onSuccess {
                    socialRepo.sendNotification(
                        recipientUid = targetUid,
                        senderName = me.displayName,
                        type = NotificationType.MOD_ACTION,
                        title = if (ban) "Banned from ${server.name}" else "Removed from ${server.name}",
                        body = if (ban) "A moderator banned your account from ${server.name}." else "You were kicked from ${server.name}."
                    )
                    showSnack(if (ban) "Member banned" else "Member kicked")
                }
                .onFailure { showSnack(unwrapFirestoreMessage(it, "Moderation action failed")) }
        }
    }

    fun unbanMember(server: NexusServer, targetUid: String) {
        viewModelScope.launch {
            serverRepo.unbanMember(server, targetUid)
                .onSuccess { showSnack("User unbanned") }
                .onFailure { showSnack(unwrapFirestoreMessage(it, "Could not unban user")) }
        }
    }

    // --- Friends, Blocks, DMs & Notifications ---

    fun sendFriendRequest(recipient: GamerProfile) {
        val me = uiState.value.currentProfile ?: return
        viewModelScope.launch {
            socialRepo.sendFriendRequest(me, recipient)
                .onSuccess { showSnack("Friend request sent to ${recipient.displayName}") }
                .onFailure { showSnack(unwrapFirestoreMessage(it, "Could not send friend request")) }
        }
    }

    fun respondToFriendRequest(friendship: Friendship, accept: Boolean) {
        val me = uiState.value.currentProfile ?: return
        viewModelScope.launch {
            socialRepo.respondToFriendRequest(friendship, accept, me)
                .onSuccess { showSnack(if (accept) "Friend added!" else "Request declined") }
                .onFailure { showSnack(unwrapFirestoreMessage(it, "Action failed")) }
        }
    }

    fun removeFriend(friendshipId: String) {
        viewModelScope.launch {
            socialRepo.removeFriendship(friendshipId)
                .onSuccess { showSnack("Friend removed") }
                .onFailure { showSnack(unwrapFirestoreMessage(it, "Could not remove friend")) }
        }
    }

    fun blockUser(targetUid: String, targetName: String, targetTag: String) {
        viewModelScope.launch {
            socialRepo.blockUser(targetUid, targetName, targetTag)
                .onSuccess {
                    if (selectedDmThreadIdFlow.value?.contains(targetUid) == true) {
                        selectedDmThreadIdFlow.value = null
                        _localState.update { it.copy(selectedDmThreadId = null) }
                    }
                    showSnack("Blocked $targetName")
                }
                .onFailure { showSnack(unwrapFirestoreMessage(it, "Could not block user")) }
        }
    }

    fun unblockUser(targetUid: String) {
        viewModelScope.launch {
            socialRepo.unblockUser(targetUid)
                .onSuccess { showSnack("User unblocked") }
                .onFailure { showSnack(unwrapFirestoreMessage(it, "Could not unblock user")) }
        }
    }

    fun openDmWithUser(peer: GamerProfile) {
        val me = uiState.value.currentProfile ?: return
        if (peer.userId in uiState.value.blockedUids) {
            showSnack("Unblock ${peer.displayName} before messaging.")
            return
        }
        viewModelScope.launch {
            socialRepo.openOrCreateDmThread(me, peer)
                .onSuccess { threadId -> openHomeDms(threadId) }
                .onFailure { showSnack(unwrapFirestoreMessage(it, "Could not open DM")) }
        }
    }

    fun sendDirectMessage(text: String, replyTo: DirectMessage? = null) {
        val state = uiState.value
        val threadId = state.selectedDmThreadId ?: return
        val me = state.currentProfile ?: return
        val attachment = state.pendingAttachment
        if (text.isBlank() && attachment == null) return

        viewModelScope.launch {
            _localState.update { it.copy(pendingAttachment = null) }
            socialRepo.sendDirectMessage(threadId, me, text, replyTo, attachment)
                .onFailure { showSnack(unwrapFirestoreMessage(it, "DM blocked or failed")) }
        }
    }

    fun editDirectMessage(msg: DirectMessage, newText: String) {
        if (newText.isBlank()) return
        viewModelScope.launch {
            socialRepo.editDirectMessage(msg.threadId, msg.id, newText)
                .onFailure { showSnack(unwrapFirestoreMessage(it, "Edit failed")) }
        }
    }

    fun deleteDirectMessage(msg: DirectMessage) {
        viewModelScope.launch {
            socialRepo.deleteDirectMessage(msg.threadId, msg.id)
                .onFailure { showSnack(unwrapFirestoreMessage(it, "Delete failed")) }
        }
    }

    fun toggleDmReaction(msg: DirectMessage, emoji: String) {
        viewModelScope.launch {
            socialRepo.toggleDmReaction(msg.threadId, msg, emoji)
        }
    }

    fun markNotificationRead(notifId: String) {
        viewModelScope.launch { socialRepo.markNotificationRead(notifId) }
    }

    fun deleteNotification(notifId: String) {
        viewModelScope.launch { socialRepo.deleteNotification(notifId) }
    }

    // --- Profile & Settings ---

    fun updateProfile(
        displayName: String,
        gamerTag: String,
        bio: String,
        avatarId: String,
        customAvatarData: String,
        onlineStatus: OnlineStatus,
        customStatusText: String
    ) {
        if (displayName.isBlank() || gamerTag.trim().length < 2) {
            showSnack("Display name and GamerTag (2+ chars) required.")
            return
        }
        val joinedIds = uiState.value.joinedServers.map { it.id }
        viewModelScope.launch {
            _localState.update { it.copy(isBusy = true) }
            socialRepo.updateProfile(
                displayName = displayName,
                gamerTag = gamerTag,
                bio = bio,
                avatarId = avatarId,
                customAvatarData = customAvatarData,
                onlineStatus = onlineStatus,
                customStatusText = customStatusText,
                joinedServerIds = joinedIds
            ).onSuccess { showSnack("Profile saved") }
                .onFailure { showSnack(unwrapFirestoreMessage(it, "Profile update failed")) }
            _localState.update { it.copy(isBusy = false) }
        }
    }

    fun updateOnlineStatus(status: OnlineStatus) {
        val joinedIds = uiState.value.joinedServers.map { it.id }
        viewModelScope.launch {
            socialRepo.setOnlineStatus(status, joinedIds)
                .onSuccess { showSnack("Status set to ${status.label}") }
        }
    }

    fun setDarkTheme(isDark: Boolean) {
        viewModelScope.launch { prefs.setDarkTheme(isDark) }
    }

    fun setSoundEnabled(enabled: Boolean) {
        viewModelScope.launch { prefs.setSoundEnabled(enabled) }
    }

    fun setHapticsEnabled(enabled: Boolean) {
        viewModelScope.launch { prefs.setHapticsEnabled(enabled) }
    }

    fun setNotificationsEnabled(enabled: Boolean) {
        viewModelScope.launch { prefs.setNotificationsEnabled(enabled) }
    }

    fun setAutoPlayVoiceEnabled(enabled: Boolean) {
        viewModelScope.launch { prefs.setAutoPlayVoiceEnabled(enabled) }
    }

    fun showSnack(msg: String) {
        _localState.update { it.copy(snackbarMessage = msg) }
    }

    fun clearSnackbar() {
        _localState.update { it.copy(snackbarMessage = null, errorBanner = null) }
    }

    override fun onCleared() {
        voiceEngine.release()
        super.onCleared()
    }

    private data class CoreStreamBundle(
        val uid: String? = null,
        val profile: GamerProfile? = null,
        val joinedServers: List<NexusServer> = emptyList(),
        val publicServers: List<NexusServer> = emptyList(),
        val directory: List<GamerProfile> = emptyList(),
        val friendships: List<Friendship> = emptyList()
    )

    private data class SocialStreamBundle(
        val blockedUsers: List<BlockedUser> = emptyList(),
        val dmThreads: List<DirectMessageThread> = emptyList(),
        val notifications: List<NexusNotification> = emptyList()
    )

    private data class DetailStreamBundle(
        val members: List<ServerMember> = emptyList(),
        val channels: List<ServerChannel> = emptyList(),
        val channelMessages: List<ChannelMessage> = emptyList(),
        val voiceMembers: List<VoiceMember> = emptyList(),
        val dmMessages: List<DirectMessage> = emptyList()
    )
}

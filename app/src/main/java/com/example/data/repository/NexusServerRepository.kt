package com.example.data.repository

import android.content.Context
import com.example.R
import com.example.data.OperationType
import com.example.data.handleFirestoreError
import com.example.data.model.AttachmentType
import com.example.data.model.ChannelMessage
import com.example.data.model.ChannelType
import com.example.data.model.GamerProfile
import com.example.data.model.NexusServer
import com.example.data.model.OnlineStatus
import com.example.data.model.ServerChannel
import com.example.data.model.ServerInvite
import com.example.data.model.ServerMember
import com.example.data.model.ServerRole
import com.example.data.model.ServerVisibility
import com.example.data.model.VoiceMember
import com.example.data.util.PendingAttachment
import com.google.firebase.Firebase
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.auth.auth
import com.google.firebase.firestore.FieldValue
import com.google.firebase.firestore.FirebaseFirestore
import com.google.firebase.firestore.Query
import kotlinx.coroutines.channels.awaitClose
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.callbackFlow
import kotlinx.coroutines.tasks.await
import java.util.UUID

class NexusServerRepository(
    private val db: FirebaseFirestore,
    private val auth: FirebaseAuth = Firebase.auth
) {
    constructor(context: Context) : this(
        db = FirebaseFirestore.getInstance(context.getString(R.string.firestore_database_id))
    )

    private fun uid(): String = auth.currentUser?.uid ?: throw IllegalStateException("Not signed in")

    fun observeJoinedServers(userId: String): Flow<List<NexusServer>> = callbackFlow {
        val reg = db.collection(COL_SERVERS)
            .whereArrayContains("memberUids", userId)
            .addSnapshotListener { snap, err ->
                if (err != null) {
                    handleFirestoreError(err, OperationType.LIST, COL_SERVERS)
                    close(err)
                    return@addSnapshotListener
                }
                val list = snap?.documents
                    ?.mapNotNull { it.toObject(NexusServer::class.java) }
                    ?.sortedBy { it.name.lowercase() }
                    ?: emptyList()
                trySend(list)
            }
        awaitClose { reg.remove() }
    }

    fun observePublicServers(): Flow<List<NexusServer>> = callbackFlow {
        val reg = db.collection(COL_SERVERS)
            .whereEqualTo("visibility", ServerVisibility.PUBLIC.name)
            .limit(80)
            .addSnapshotListener { snap, err ->
                if (err != null) {
                    handleFirestoreError(err, OperationType.LIST, COL_SERVERS)
                    close(err)
                    return@addSnapshotListener
                }
                val list = snap?.documents
                    ?.mapNotNull { it.toObject(NexusServer::class.java) }
                    ?.sortedByDescending { it.memberCount }
                    ?: emptyList()
                trySend(list)
            }
        awaitClose { reg.remove() }
    }

    fun observeServerMembers(serverId: String): Flow<List<ServerMember>> = callbackFlow {
        val path = "$COL_SERVERS/$serverId/$SUB_MEMBERS"
        val reg = db.collection(COL_SERVERS).document(serverId).collection(SUB_MEMBERS)
            .addSnapshotListener { snap, err ->
                if (err != null) {
                    handleFirestoreError(err, OperationType.LIST, path)
                    close(err)
                    return@addSnapshotListener
                }
                val members = snap?.documents
                    ?.mapNotNull { it.toObject(ServerMember::class.java) }
                    ?.sortedWith(
                        compareByDescending<ServerMember> { it.roleEnum.rank }
                            .thenBy { it.statusEnum == OnlineStatus.OFFLINE }
                            .thenBy { it.displayName.lowercase() }
                    )
                    ?: emptyList()
                trySend(members)
            }
        awaitClose { reg.remove() }
    }

    fun observeChannels(serverId: String): Flow<List<ServerChannel>> = callbackFlow {
        val path = "$COL_SERVERS/$serverId/$SUB_CHANNELS"
        val reg = db.collection(COL_SERVERS).document(serverId).collection(SUB_CHANNELS)
            .addSnapshotListener { snap, err ->
                if (err != null) {
                    handleFirestoreError(err, OperationType.LIST, path)
                    close(err)
                    return@addSnapshotListener
                }
                val list = snap?.documents
                    ?.mapNotNull { it.toObject(ServerChannel::class.java) }
                    ?.sortedWith(compareBy<ServerChannel> { it.position }.thenBy { it.name.lowercase() })
                    ?: emptyList()
                trySend(list)
            }
        awaitClose { reg.remove() }
    }

    fun observeChannelMessages(serverId: String, channelId: String): Flow<List<ChannelMessage>> = callbackFlow {
        val path = "$COL_SERVERS/$serverId/$SUB_CHANNELS/$channelId/$SUB_MESSAGES"
        val reg = db.collection(COL_SERVERS).document(serverId)
            .collection(SUB_CHANNELS).document(channelId)
            .collection(SUB_MESSAGES)
            .orderBy("createdAt", Query.Direction.ASCENDING)
            .limitToLast(150)
            .addSnapshotListener { snap, err ->
                if (err != null) {
                    handleFirestoreError(err, OperationType.LIST, path)
                    close(err)
                    return@addSnapshotListener
                }
                val msgs = snap?.documents?.mapNotNull { it.toObject(ChannelMessage::class.java) } ?: emptyList()
                trySend(msgs)
            }
        awaitClose { reg.remove() }
    }

    fun observeVoiceMembers(serverId: String, channelId: String): Flow<List<VoiceMember>> = callbackFlow {
        val path = "$COL_SERVERS/$serverId/$SUB_CHANNELS/$channelId/$SUB_VOICE_MEMBERS"
        val reg = db.collection(COL_SERVERS).document(serverId)
            .collection(SUB_CHANNELS).document(channelId)
            .collection(SUB_VOICE_MEMBERS)
            .addSnapshotListener { snap, err ->
                if (err != null) {
                    handleFirestoreError(err, OperationType.LIST, path)
                    close(err)
                    return@addSnapshotListener
                }
                val list = snap?.documents
                    ?.mapNotNull { it.toObject(VoiceMember::class.java) }
                    ?.sortedBy { it.displayName.lowercase() }
                    ?: emptyList()
                trySend(list)
            }
        awaitClose { reg.remove() }
    }

    suspend fun createServer(
        name: String,
        description: String,
        iconEmoji: String,
        iconColorKey: String,
        visibility: ServerVisibility,
        ownerProfile: GamerProfile,
        iconData: String = ""
    ): Result<String> = runCatching {
        val ownerUid = uid()
        val serverId = "srv_${UUID.randomUUID().toString().replace("-", "").take(14)}"
        val inviteCode = "NX-${UUID.randomUUID().toString().replace("-", "").take(6).uppercase()}"
        val serverRef = db.collection(COL_SERVERS).document(serverId)
        val inviteRef = db.collection(COL_INVITES).document(inviteCode)
        val memberRef = serverRef.collection(SUB_MEMBERS).document(ownerUid)

        val generalId = "ch_${UUID.randomUUID().toString().replace("-", "").take(10)}"
        val announceId = "ch_${UUID.randomUUID().toString().replace("-", "").take(10)}"
        val voiceId = "ch_${UUID.randomUUID().toString().replace("-", "").take(10)}"

        val batch = db.batch()
        batch.set(
            serverRef,
            buildMap {
                put("ownerId", ownerUid)
                put("name", name.trim())
                put("description", description.trim())
                put("iconEmoji", iconEmoji.ifBlank { "⚡" })
                put("iconColorKey", iconColorKey.ifBlank { "purple_blue" })
                if (iconData.isNotBlank()) put("iconData", iconData)
                put("visibility", visibility.name)
                put("inviteCode", inviteCode)
                put("memberUids", listOf(ownerUid))
                put("adminUids", emptyList<String>())
                put("modUids", emptyList<String>())
                put("bannedUids", emptyList<String>())
                put("memberCount", 1)
                put("createdAt", FieldValue.serverTimestamp())
                put("updatedAt", FieldValue.serverTimestamp())
            }
        )
        batch.set(
            inviteRef,
            mapOf(
                "inviteCode" to inviteCode,
                "serverId" to serverId,
                "serverName" to name.trim(),
                "serverDescription" to description.trim(),
                "iconEmoji" to iconEmoji.ifBlank { "⚡" },
                "iconColorKey" to iconColorKey.ifBlank { "purple_blue" },
                "visibility" to visibility.name,
                "ownerId" to ownerUid,
                "createdAt" to FieldValue.serverTimestamp(),
                "updatedAt" to FieldValue.serverTimestamp()
            )
        )
        batch.set(
            memberRef,
            buildMap {
                put("userId", ownerUid)
                put("displayName", ownerProfile.displayName.ifBlank { "Player" })
                put("gamerTag", ownerProfile.gamerTag.ifBlank { "NexusUser" })
                put("avatarId", ownerProfile.avatarId)
                if (ownerProfile.customAvatarData.isNotBlank()) put("customAvatarData", ownerProfile.customAvatarData)
                put("onlineStatus", ownerProfile.onlineStatus)
                put("role", ServerRole.OWNER.name)
                put("badges", ownerProfile.badges.take(8))
                put("joinedAt", FieldValue.serverTimestamp())
                put("updatedAt", FieldValue.serverTimestamp())
            }
        )
        batch.set(
            serverRef.collection(SUB_CHANNELS).document(announceId),
            mapOf(
                "serverId" to serverId,
                "name" to "announcements",
                "topic" to "Official news & patch updates",
                "type" to ChannelType.ANNOUNCEMENT.name,
                "categoryName" to "INFO",
                "position" to 0,
                "adminOnlyPost" to true,
                "createdBy" to ownerUid,
                "createdAt" to FieldValue.serverTimestamp(),
                "updatedAt" to FieldValue.serverTimestamp()
            )
        )
        batch.set(
            serverRef.collection(SUB_CHANNELS).document(generalId),
            mapOf(
                "serverId" to serverId,
                "name" to "general",
                "topic" to "Main squad discussion & LFG",
                "type" to ChannelType.TEXT.name,
                "categoryName" to "TEXT CHANNELS",
                "position" to 1,
                "adminOnlyPost" to false,
                "createdBy" to ownerUid,
                "createdAt" to FieldValue.serverTimestamp(),
                "updatedAt" to FieldValue.serverTimestamp()
            )
        )
        batch.set(
            serverRef.collection(SUB_CHANNELS).document(voiceId),
            mapOf(
                "serverId" to serverId,
                "name" to "Squad Voice",
                "topic" to "Live voice comms & callouts",
                "type" to ChannelType.VOICE.name,
                "categoryName" to "VOICE LOUNGES",
                "position" to 2,
                "adminOnlyPost" to false,
                "createdBy" to ownerUid,
                "createdAt" to FieldValue.serverTimestamp(),
                "updatedAt" to FieldValue.serverTimestamp()
            )
        )
        batch.commit().await()
        serverId
    }.onFailure { handleFirestoreError(it, OperationType.CREATE, COL_SERVERS) }

    suspend fun updateServer(
        server: NexusServer,
        newName: String,
        newDescription: String,
        newIconEmoji: String,
        newVisibility: ServerVisibility,
        regenerateInvite: Boolean = false
    ): Result<Unit> = runCatching {
        val nextInvite = if (regenerateInvite) {
            "NX-${UUID.randomUUID().toString().replace("-", "").take(6).uppercase()}"
        } else {
            server.inviteCode
        }
        val batch = db.batch()
        val srvRef = db.collection(COL_SERVERS).document(server.id)
        val updates = buildMap<String, Any> {
            put("name", newName.trim())
            put("description", newDescription.trim())
            put("iconEmoji", newIconEmoji.ifBlank { "⚡" })
            put("inviteCode", nextInvite)
            if (server.ownerId == uid()) {
                put("visibility", newVisibility.name)
            }
            put("updatedAt", FieldValue.serverTimestamp())
        }
        batch.update(srvRef, updates)
        if (regenerateInvite && nextInvite != server.inviteCode) {
            batch.delete(db.collection(COL_INVITES).document(server.inviteCode))
            batch.set(
                db.collection(COL_INVITES).document(nextInvite),
                mapOf(
                    "inviteCode" to nextInvite,
                    "serverId" to server.id,
                    "serverName" to newName.trim(),
                    "serverDescription" to newDescription.trim(),
                    "iconEmoji" to newIconEmoji.ifBlank { "⚡" },
                    "iconColorKey" to server.iconColorKey,
                    "visibility" to newVisibility.name,
                    "ownerId" to server.ownerId,
                    "createdAt" to FieldValue.serverTimestamp(),
                    "updatedAt" to FieldValue.serverTimestamp()
                )
            )
        } else {
            batch.update(
                db.collection(COL_INVITES).document(server.inviteCode),
                mapOf(
                    "serverName" to newName.trim(),
                    "serverDescription" to newDescription.trim(),
                    "iconEmoji" to newIconEmoji.ifBlank { "⚡" },
                    "visibility" to newVisibility.name,
                    "updatedAt" to FieldValue.serverTimestamp()
                )
            )
        }
        batch.commit().await()
        Unit
    }.onFailure { handleFirestoreError(it, OperationType.UPDATE, "$COL_SERVERS/${server.id}") }

    suspend fun deleteServer(server: NexusServer): Result<Unit> = runCatching {
        val srvRef = db.collection(COL_SERVERS).document(server.id)
        db.collection(COL_INVITES).document(server.inviteCode).delete().await()
        srvRef.delete().await()
        Unit
    }.onFailure { handleFirestoreError(it, OperationType.DELETE, "$COL_SERVERS/${server.id}") }

    suspend fun joinServerById(serverId: String, profile: GamerProfile): Result<String> = runCatching {
        val myUid = uid()
        val srvSnap = db.collection(COL_SERVERS).document(serverId).get().await()
        val server = srvSnap.toObject(NexusServer::class.java)
            ?: throw IllegalStateException("Server not found")
        joinServerInternal(server, myUid, profile)
        server.id
    }.onFailure { handleFirestoreError(it, OperationType.UPDATE, "$COL_SERVERS/$serverId") }

    suspend fun joinServerByInviteCode(rawCode: String, profile: GamerProfile): Result<String> = runCatching {
        val myUid = uid()
        val cleanCode = rawCode.trim()
            .removePrefix("https://nexus.gg/")
            .removePrefix("nexus://invite/")
            .trim()
            .uppercase()
        require(cleanCode.isNotBlank()) { "Enter a valid invite code" }
        val inviteSnap = db.collection(COL_INVITES).document(cleanCode).get().await()
        val invite = inviteSnap.toObject(ServerInvite::class.java)
            ?: throw IllegalStateException("Invite code '$cleanCode' is invalid or expired")

        val srvRef = db.collection(COL_SERVERS).document(invite.serverId)
        val batch = db.batch()
        batch.update(
            srvRef,
            mapOf(
                "memberUids" to FieldValue.arrayUnion(myUid),
                "memberCount" to FieldValue.increment(1),
                "updatedAt" to FieldValue.serverTimestamp()
            )
        )
        batch.set(
            srvRef.collection(SUB_MEMBERS).document(myUid),
            buildMemberCreateMap(myUid, profile, ServerRole.MEMBER)
        )
        batch.commit().await()
        invite.serverId
    }.onFailure { handleFirestoreError(it, OperationType.UPDATE, "$COL_INVITES/$rawCode") }

    private suspend fun joinServerInternal(server: NexusServer, myUid: String, profile: GamerProfile) {
        if (myUid in server.bannedUids) {
            throw IllegalStateException("You are banned from this server.")
        }
        if (myUid in server.memberUids) return
        val srvRef = db.collection(COL_SERVERS).document(server.id)
        val batch = db.batch()
        batch.update(
            srvRef,
            mapOf(
                "memberUids" to (server.memberUids + myUid).distinct(),
                "memberCount" to (server.memberUids + myUid).distinct().size,
                "updatedAt" to FieldValue.serverTimestamp()
            )
        )
        batch.set(
            srvRef.collection(SUB_MEMBERS).document(myUid),
            buildMemberCreateMap(myUid, profile, ServerRole.MEMBER)
        )
        batch.commit().await()
    }

    suspend fun leaveServer(server: NexusServer): Result<Unit> = runCatching {
        val myUid = uid()
        require(myUid != server.ownerId) { "Server owners must delete or manage the server." }
        val srvRef = db.collection(COL_SERVERS).document(server.id)
        val nextMembers = server.memberUids.filterNot { it == myUid }
        val batch = db.batch()
        batch.delete(srvRef.collection(SUB_MEMBERS).document(myUid))
        batch.update(
            srvRef,
            mapOf(
                "memberUids" to nextMembers,
                "adminUids" to server.adminUids.filterNot { it == myUid },
                "modUids" to server.modUids.filterNot { it == myUid },
                "memberCount" to nextMembers.size.coerceAtLeast(1),
                "updatedAt" to FieldValue.serverTimestamp()
            )
        )
        batch.commit().await()
        Unit
    }.onFailure { handleFirestoreError(it, OperationType.UPDATE, "$COL_SERVERS/${server.id}") }

    suspend fun createChannel(
        serverId: String,
        name: String,
        topic: String,
        type: ChannelType,
        categoryName: String,
        position: Int,
        adminOnlyPost: Boolean
    ): Result<String> = runCatching {
        val myUid = uid()
        val channelId = "ch_${UUID.randomUUID().toString().replace("-", "").take(12)}"
        db.collection(COL_SERVERS).document(serverId)
            .collection(SUB_CHANNELS).document(channelId)
            .set(
                buildMap {
                    put("serverId", serverId)
                    put("name", name.trim().lowercase().replace(" ", "-").take(40))
                    if (topic.isNotBlank()) put("topic", topic.trim().take(160))
                    put("type", type.name)
                    put("categoryName", categoryName.trim().uppercase().ifBlank { "GENERAL" }.take(36))
                    put("position", position.coerceIn(0, 500))
                    put("adminOnlyPost", adminOnlyPost)
                    put("createdBy", myUid)
                    put("createdAt", FieldValue.serverTimestamp())
                    put("updatedAt", FieldValue.serverTimestamp())
                }
            ).await()
        channelId
    }.onFailure { handleFirestoreError(it, OperationType.CREATE, "$COL_SERVERS/$serverId/$SUB_CHANNELS") }

    suspend fun updateChannel(
        serverId: String,
        channelId: String,
        name: String,
        topic: String,
        type: ChannelType,
        categoryName: String,
        position: Int,
        adminOnlyPost: Boolean
    ): Result<Unit> = runCatching {
        db.collection(COL_SERVERS).document(serverId)
            .collection(SUB_CHANNELS).document(channelId)
            .update(
                mapOf(
                    "name" to name.trim().lowercase().replace(" ", "-").take(40),
                    "topic" to topic.trim().take(160),
                    "type" to type.name,
                    "categoryName" to categoryName.trim().uppercase().ifBlank { "GENERAL" }.take(36),
                    "position" to position.coerceIn(0, 500),
                    "adminOnlyPost" to adminOnlyPost,
                    "updatedAt" to FieldValue.serverTimestamp()
                )
            ).await()
        Unit
    }.onFailure { handleFirestoreError(it, OperationType.UPDATE, "$COL_SERVERS/$serverId/$SUB_CHANNELS/$channelId") }

    suspend fun deleteChannel(serverId: String, channelId: String): Result<Unit> = runCatching {
        db.collection(COL_SERVERS).document(serverId)
            .collection(SUB_CHANNELS).document(channelId)
            .delete().await()
        Unit
    }.onFailure { handleFirestoreError(it, OperationType.DELETE, "$COL_SERVERS/$serverId/$SUB_CHANNELS/$channelId") }

    suspend fun sendChannelMessage(
        serverId: String,
        channelId: String,
        senderProfile: GamerProfile,
        senderRole: ServerRole,
        text: String,
        replyTo: ChannelMessage? = null,
        attachment: PendingAttachment? = null,
        voiceDataBase64: String? = null,
        voiceDurationSec: Int = 0
    ): Result<String> = runCatching {
        val myUid = uid()
        val msgId = "msg_${UUID.randomUUID().toString().replace("-", "").take(16)}"
        val attachType = when {
            voiceDataBase64 != null -> AttachmentType.VOICE.name
            attachment != null -> attachment.type
            else -> AttachmentType.NONE.name
        }
        val payload = buildMap<String, Any> {
            put("serverId", serverId)
            put("channelId", channelId)
            put("senderId", myUid)
            put("senderName", senderProfile.displayName.ifBlank { "Player" })
            put("senderTag", senderProfile.gamerTag.ifBlank { "NexusUser" })
            put("senderAvatarId", senderProfile.avatarId)
            put("senderRole", senderRole.name)
            put("text", text.trim())
            if (replyTo != null) {
                put("replyToId", replyTo.id)
                put("replyToAuthor", replyTo.senderName)
                put("replyToSnippet", replyTo.text.ifBlank { "[${replyTo.attachmentType}]" }.take(120))
            }
            put("attachmentType", attachType)
            when {
                voiceDataBase64 != null -> {
                    put("attachmentName", "Voice Comms (${voiceDurationSec}s)")
                    put("attachmentMime", "audio/wav")
                    put("attachmentData", voiceDataBase64)
                    put("audioDurationSec", voiceDurationSec.coerceIn(1, 120))
                }
                attachment != null -> {
                    put("attachmentName", attachment.fileName.take(120))
                    put("attachmentMime", attachment.mimeType.take(80))
                    put("attachmentData", attachment.base64Data)
                }
            }
            put("reactions", emptyMap<String, List<String>>())
            put("isEdited", false)
            put("createdAt", FieldValue.serverTimestamp())
            put("updatedAt", FieldValue.serverTimestamp())
        }
        db.collection(COL_SERVERS).document(serverId)
            .collection(SUB_CHANNELS).document(channelId)
            .collection(SUB_MESSAGES).document(msgId)
            .set(payload).await()
        msgId
    }.onFailure { handleFirestoreError(it, OperationType.CREATE, "$COL_SERVERS/$serverId/$SUB_CHANNELS/$channelId/$SUB_MESSAGES") }

    suspend fun editChannelMessage(
        serverId: String,
        channelId: String,
        messageId: String,
        newText: String
    ): Result<Unit> = runCatching {
        db.collection(COL_SERVERS).document(serverId)
            .collection(SUB_CHANNELS).document(channelId)
            .collection(SUB_MESSAGES).document(messageId)
            .update(
                mapOf(
                    "text" to newText.trim(),
                    "isEdited" to true,
                    "updatedAt" to FieldValue.serverTimestamp()
                )
            ).await()
        Unit
    }.onFailure { handleFirestoreError(it, OperationType.UPDATE, "$COL_SERVERS/$serverId/$SUB_CHANNELS/$channelId/$SUB_MESSAGES/$messageId") }

    suspend fun deleteChannelMessage(
        serverId: String,
        channelId: String,
        messageId: String
    ): Result<Unit> = runCatching {
        db.collection(COL_SERVERS).document(serverId)
            .collection(SUB_CHANNELS).document(channelId)
            .collection(SUB_MESSAGES).document(messageId)
            .delete().await()
        Unit
    }.onFailure { handleFirestoreError(it, OperationType.DELETE, "$COL_SERVERS/$serverId/$SUB_CHANNELS/$channelId/$SUB_MESSAGES/$messageId") }

    suspend fun toggleChannelReaction(
        serverId: String,
        channelId: String,
        message: ChannelMessage,
        emoji: String
    ): Result<Unit> = runCatching {
        val myUid = uid()
        val updated = message.reactions.toMutableMap()
        val currentList = updated[emoji]?.toMutableList() ?: mutableListOf()
        if (myUid in currentList) {
            currentList.remove(myUid)
        } else {
            currentList.add(myUid)
        }
        if (currentList.isEmpty()) updated.remove(emoji) else updated[emoji] = currentList
        db.collection(COL_SERVERS).document(serverId)
            .collection(SUB_CHANNELS).document(channelId)
            .collection(SUB_MESSAGES).document(message.id)
            .update(
                mapOf(
                    "reactions" to updated,
                    "updatedAt" to FieldValue.serverTimestamp()
                )
            ).await()
        Unit
    }.onFailure { handleFirestoreError(it, OperationType.UPDATE, "$COL_SERVERS/$serverId/$SUB_CHANNELS/$channelId/$SUB_MESSAGES/${message.id}") }

    suspend fun joinVoiceRoom(
        serverId: String,
        channelId: String,
        profile: GamerProfile,
        isMuted: Boolean
    ): Result<Unit> = runCatching {
        val myUid = uid()
        db.collection(COL_SERVERS).document(serverId)
            .collection(SUB_CHANNELS).document(channelId)
            .collection(SUB_VOICE_MEMBERS).document(myUid)
            .set(
                mapOf(
                    "serverId" to serverId,
                    "channelId" to channelId,
                    "userId" to myUid,
                    "displayName" to profile.displayName.ifBlank { "Player" },
                    "gamerTag" to profile.gamerTag.ifBlank { "NexusUser" },
                    "avatarId" to profile.avatarId,
                    "isMuted" to isMuted,
                    "isDeafened" to false,
                    "isSpeaking" to false,
                    "joinedAt" to FieldValue.serverTimestamp(),
                    "updatedAt" to FieldValue.serverTimestamp()
                )
            ).await()
        Unit
    }.onFailure { handleFirestoreError(it, OperationType.CREATE, "$COL_SERVERS/$serverId/$SUB_CHANNELS/$channelId/$SUB_VOICE_MEMBERS") }

    suspend fun updateVoiceMemberState(
        serverId: String,
        channelId: String,
        isMuted: Boolean,
        isDeafened: Boolean,
        isSpeaking: Boolean
    ): Result<Unit> = runCatching {
        val myUid = uid()
        db.collection(COL_SERVERS).document(serverId)
            .collection(SUB_CHANNELS).document(channelId)
            .collection(SUB_VOICE_MEMBERS).document(myUid)
            .update(
                mapOf(
                    "isMuted" to isMuted,
                    "isDeafened" to isDeafened,
                    "isSpeaking" to isSpeaking,
                    "updatedAt" to FieldValue.serverTimestamp()
                )
            ).await()
        Unit
    }.onFailure { handleFirestoreError(it, OperationType.UPDATE, "$COL_SERVERS/$serverId/$SUB_CHANNELS/$channelId/$SUB_VOICE_MEMBERS") }

    suspend fun leaveVoiceRoom(serverId: String, channelId: String): Result<Unit> = runCatching {
        val myUid = uid()
        db.collection(COL_SERVERS).document(serverId)
            .collection(SUB_CHANNELS).document(channelId)
            .collection(SUB_VOICE_MEMBERS).document(myUid)
            .delete().await()
        Unit
    }.onFailure { handleFirestoreError(it, OperationType.DELETE, "$COL_SERVERS/$serverId/$SUB_CHANNELS/$channelId/$SUB_VOICE_MEMBERS") }

    suspend fun assignMemberRole(
        server: NexusServer,
        targetUid: String,
        newRole: ServerRole
    ): Result<Unit> = runCatching {
        require(targetUid != server.ownerId) { "Owner role cannot be reassigned." }
        val srvRef = db.collection(COL_SERVERS).document(server.id)
        val nextAdmins = server.adminUids.toMutableSet().apply {
            if (newRole == ServerRole.ADMIN) add(targetUid) else remove(targetUid)
        }.toList()
        val nextMods = server.modUids.toMutableSet().apply {
            if (newRole == ServerRole.MOD) add(targetUid) else remove(targetUid)
        }.toList()
        val batch = db.batch()
        val serverUpdate = buildMap<String, Any> {
            if (uid() == server.ownerId) put("adminUids", nextAdmins)
            put("modUids", nextMods)
            put("updatedAt", FieldValue.serverTimestamp())
        }
        batch.update(srvRef, serverUpdate)
        batch.update(
            srvRef.collection(SUB_MEMBERS).document(targetUid),
            mapOf(
                "role" to newRole.name,
                "updatedAt" to FieldValue.serverTimestamp()
            )
        )
        batch.commit().await()
        Unit
    }.onFailure { handleFirestoreError(it, OperationType.UPDATE, "$COL_SERVERS/${server.id}/$SUB_MEMBERS/$targetUid") }

    suspend fun kickOrBanMember(
        server: NexusServer,
        targetUid: String,
        ban: Boolean
    ): Result<Unit> = runCatching {
        require(targetUid != server.ownerId) { "Cannot kick or ban server owner." }
        val srvRef = db.collection(COL_SERVERS).document(server.id)
        val nextMembers = server.memberUids.filterNot { it == targetUid }
        val nextBans = if (ban) (server.bannedUids + targetUid).distinct() else server.bannedUids
        val batch = db.batch()
        batch.delete(srvRef.collection(SUB_MEMBERS).document(targetUid))
        val updates = buildMap<String, Any> {
            put("memberUids", nextMembers)
            if (uid() == server.ownerId) put("adminUids", server.adminUids.filterNot { it == targetUid })
            put("modUids", server.modUids.filterNot { it == targetUid })
            if (ban) put("bannedUids", nextBans)
            put("memberCount", nextMembers.size.coerceAtLeast(1))
            put("updatedAt", FieldValue.serverTimestamp())
        }
        batch.update(srvRef, updates)
        batch.commit().await()
        Unit
    }.onFailure { handleFirestoreError(it, OperationType.UPDATE, "$COL_SERVERS/${server.id}") }

    suspend fun unbanMember(server: NexusServer, targetUid: String): Result<Unit> = runCatching {
        db.collection(COL_SERVERS).document(server.id)
            .update(
                mapOf(
                    "bannedUids" to server.bannedUids.filterNot { it == targetUid },
                    "updatedAt" to FieldValue.serverTimestamp()
                )
            ).await()
        Unit
    }.onFailure { handleFirestoreError(it, OperationType.UPDATE, "$COL_SERVERS/${server.id}") }

    private fun buildMemberCreateMap(
        memberUid: String,
        profile: GamerProfile,
        role: ServerRole
    ): Map<String, Any> = buildMap {
        put("userId", memberUid)
        put("displayName", profile.displayName.ifBlank { "Player" })
        put("gamerTag", profile.gamerTag.ifBlank { "NexusUser" })
        put("avatarId", profile.avatarId)
        if (profile.customAvatarData.isNotBlank()) put("customAvatarData", profile.customAvatarData)
        put("onlineStatus", profile.onlineStatus)
        put("role", role.name)
        put("badges", profile.badges.take(8))
        put("joinedAt", FieldValue.serverTimestamp())
        put("updatedAt", FieldValue.serverTimestamp())
    }

    companion object {
        const val COL_SERVERS = "nexus_servers"
        const val COL_INVITES = "server_invites"
        const val SUB_MEMBERS = "members"
        const val SUB_CHANNELS = "channels"
        const val SUB_MESSAGES = "messages"
        const val SUB_VOICE_MEMBERS = "voice_members"
    }
}

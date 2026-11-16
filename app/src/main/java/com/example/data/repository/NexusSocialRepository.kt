package com.example.data.repository

import android.content.Context
import com.example.R
import com.example.data.OperationType
import com.example.data.handleFirestoreError
import com.example.data.model.AttachmentType
import com.example.data.model.AvatarPreset
import com.example.data.model.BadgePreset
import com.example.data.model.BlockedUser
import com.example.data.model.DirectMessage
import com.example.data.model.DirectMessageThread
import com.example.data.model.Friendship
import com.example.data.model.FriendshipStatus
import com.example.data.model.GamerProfile
import com.example.data.model.NexusNotification
import com.example.data.model.NotificationType
import com.example.data.model.OnlineStatus
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

class NexusSocialRepository(
    private val db: FirebaseFirestore,
    private val auth: FirebaseAuth = Firebase.auth
) {
    constructor(context: Context) : this(
        db = FirebaseFirestore.getInstance(context.getString(R.string.firestore_database_id))
    )

    private fun uid(): String = auth.currentUser?.uid ?: throw IllegalStateException("Not signed in")

    suspend fun ensureSignedInProfile(
        preferredTag: String? = null,
        preferredAvatarId: String? = null
    ): Result<GamerProfile> = runCatching {
        val user = auth.currentUser ?: throw IllegalStateException("Not signed in")
        val myUid = user.uid
        val displayName = user.displayName?.takeIf { it.isNotBlank() } ?: "Nexus Player"
        val cleanTag = preferredTag?.trim()?.take(24)?.takeIf { it.length >= 2 }
            ?: displayName.replace(" ", "").take(16).ifBlank { "Player" } + myUid.takeLast(4).uppercase()
        val avatar = preferredAvatarId ?: AvatarPreset.CYBER_KNIGHT.id

        val userRef = db.collection(COL_USERS).document(myUid)
        val profRef = db.collection(COL_PROFILES).document(myUid)
        val existingProf = profRef.get().await()

        if (!existingProf.exists()) {
            val profile = GamerProfile(
                id = myUid,
                userId = myUid,
                displayName = displayName,
                gamerTag = cleanTag,
                bio = "Active on Nexus",
                avatarId = avatar,
                onlineStatus = OnlineStatus.ONLINE.name,
                customStatusText = "Online in Nexus",
                badges = listOf(BadgePreset.EARLY_ADOPTER.id)
            )
            val batch = db.batch()
            batch.set(
                userRef,
                buildMap {
                    put("userId", myUid)
                    user.email?.let { put("email", it) }
                    put("displayName", displayName)
                    put("gamerTag", cleanTag)
                    put("avatarId", avatar)
                    put("onlineStatus", OnlineStatus.ONLINE.name)
                    put("createdAt", FieldValue.serverTimestamp())
                    put("updatedAt", FieldValue.serverTimestamp())
                }
            )
            batch.set(profRef, profile.toCreateMap())
            batch.commit().await()
            profile
        } else {
            profRef.update(
                mapOf(
                    "onlineStatus" to OnlineStatus.ONLINE.name,
                    "updatedAt" to FieldValue.serverTimestamp()
                )
            ).await()
            existingProf.toObject(GamerProfile::class.java) ?: GamerProfile(userId = myUid, displayName = displayName, gamerTag = cleanTag)
        }
    }.onFailure { handleFirestoreError(it, OperationType.WRITE, COL_PROFILES) }

    fun observeProfile(userId: String): Flow<GamerProfile?> = callbackFlow {
        val reg = db.collection(COL_PROFILES).document(userId)
            .addSnapshotListener { snap, err ->
                if (err != null) {
                    handleFirestoreError(err, OperationType.GET, "$COL_PROFILES/$userId")
                    close(err)
                    return@addSnapshotListener
                }
                trySend(snap?.toObject(GamerProfile::class.java))
            }
        awaitClose { reg.remove() }
    }

    fun observePublicDirectory(): Flow<List<GamerProfile>> = callbackFlow {
        val reg = db.collection(COL_PROFILES)
            .whereEqualTo("visibility", "PUBLIC")
            .limit(100)
            .addSnapshotListener { snap, err ->
                if (err != null) {
                    handleFirestoreError(err, OperationType.LIST, COL_PROFILES)
                    close(err)
                    return@addSnapshotListener
                }
                val list = snap?.documents
                    ?.mapNotNull { it.toObject(GamerProfile::class.java) }
                    ?.sortedBy { it.displayName.lowercase() }
                    ?: emptyList()
                trySend(list)
            }
        awaitClose { reg.remove() }
    }

    suspend fun updateProfile(
        displayName: String,
        gamerTag: String,
        bio: String,
        avatarId: String,
        customAvatarData: String,
        onlineStatus: OnlineStatus,
        customStatusText: String,
        joinedServerIds: List<String> = emptyList()
    ): Result<Unit> = runCatching {
        val myUid = uid()
        val profUpdates = buildMap<String, Any> {
            put("displayName", displayName.trim().take(40))
            put("gamerTag", gamerTag.trim().take(30))
            put("bio", bio.trim().take(220))
            put("avatarId", avatarId)
            if (customAvatarData.isNotBlank()) put("customAvatarData", customAvatarData)
            put("onlineStatus", onlineStatus.name)
            put("customStatusText", customStatusText.trim().take(80))
            put("updatedAt", FieldValue.serverTimestamp())
        }
        val batch = db.batch()
        batch.update(db.collection(COL_PROFILES).document(myUid), profUpdates)
        batch.update(
            db.collection(COL_USERS).document(myUid),
            mapOf(
                "displayName" to displayName.trim().take(40),
                "gamerTag" to gamerTag.trim().take(30),
                "avatarId" to avatarId,
                "onlineStatus" to onlineStatus.name,
                "updatedAt" to FieldValue.serverTimestamp()
            )
        )
        joinedServerIds.take(15).forEach { srvId ->
            val memRef = db.collection(NexusServerRepository.COL_SERVERS)
                .document(srvId)
                .collection(NexusServerRepository.SUB_MEMBERS)
                .document(myUid)
            val memUpdates = buildMap<String, Any> {
                put("displayName", displayName.trim().take(40))
                put("gamerTag", gamerTag.trim().take(30))
                put("avatarId", avatarId)
                if (customAvatarData.isNotBlank()) put("customAvatarData", customAvatarData)
                put("onlineStatus", onlineStatus.name)
                put("updatedAt", FieldValue.serverTimestamp())
            }
            batch.update(memRef, memUpdates)
        }
        batch.commit().await()
        Unit
    }.onFailure { handleFirestoreError(it, OperationType.UPDATE, COL_PROFILES) }

    suspend fun setOnlineStatus(status: OnlineStatus, joinedServerIds: List<String> = emptyList()): Result<Unit> = runCatching {
        val myUid = uid()
        db.collection(COL_PROFILES).document(myUid)
            .update(mapOf("onlineStatus" to status.name, "updatedAt" to FieldValue.serverTimestamp()))
            .await()
        runCatching {
            db.collection(COL_USERS).document(myUid)
                .update(mapOf("onlineStatus" to status.name, "updatedAt" to FieldValue.serverTimestamp()))
                .await()
        }
        joinedServerIds.take(15).forEach { srvId ->
            runCatching {
                db.collection(NexusServerRepository.COL_SERVERS)
                    .document(srvId)
                    .collection(NexusServerRepository.SUB_MEMBERS)
                    .document(myUid)
                    .update(mapOf("onlineStatus" to status.name, "updatedAt" to FieldValue.serverTimestamp()))
                    .await()
            }
        }
        Unit
    }.onFailure { handleFirestoreError(it, OperationType.UPDATE, COL_PROFILES) }

    // --- Friends, Blocked Users & Notifications ---

    fun observeFriendships(userId: String): Flow<List<Friendship>> = callbackFlow {
        val reg = db.collection(COL_FRIENDSHIPS)
            .whereArrayContains("userIds", userId)
            .addSnapshotListener { snap, err ->
                if (err != null) {
                    handleFirestoreError(err, OperationType.LIST, COL_FRIENDSHIPS)
                    close(err)
                    return@addSnapshotListener
                }
                val list = snap?.documents?.mapNotNull { it.toObject(Friendship::class.java) } ?: emptyList()
                trySend(list)
            }
        awaitClose { reg.remove() }
    }

    suspend fun sendFriendRequest(sender: GamerProfile, recipient: GamerProfile): Result<Unit> = runCatching {
        val myUid = uid()
        require(myUid != recipient.userId)
        val sortedIds = listOf(myUid, recipient.userId).sorted()
        val fId = sortedIds.joinToString("_")
        db.collection(COL_FRIENDSHIPS).document(fId).set(
            mapOf(
                "userIds" to sortedIds,
                "requesterId" to myUid,
                "requesterName" to sender.displayName.ifBlank { "Player" },
                "requesterTag" to sender.gamerTag.ifBlank { "NexusUser" },
                "requesterAvatarId" to sender.avatarId,
                "recipientId" to recipient.userId,
                "recipientName" to recipient.displayName.ifBlank { "Player" },
                "recipientTag" to recipient.gamerTag.ifBlank { "NexusUser" },
                "recipientAvatarId" to recipient.avatarId,
                "status" to FriendshipStatus.PENDING.name,
                "createdAt" to FieldValue.serverTimestamp(),
                "updatedAt" to FieldValue.serverTimestamp()
            )
        ).await()
        sendNotification(
            recipientUid = recipient.userId,
            senderName = sender.displayName.ifBlank { "Player" },
            type = NotificationType.FRIEND_REQUEST,
            title = "New Friend Request",
            body = "${sender.displayName} (@${sender.gamerTag}) sent you a friend request."
        )
        Unit
    }.onFailure { handleFirestoreError(it, OperationType.CREATE, COL_FRIENDSHIPS) }

    suspend fun respondToFriendRequest(
        friendship: Friendship,
        accept: Boolean,
        responderProfile: GamerProfile
    ): Result<Unit> = runCatching {
        val nextStatus = if (accept) FriendshipStatus.ACCEPTED.name else FriendshipStatus.DECLINED.name
        db.collection(COL_FRIENDSHIPS).document(friendship.id).update(
            mapOf(
                "status" to nextStatus,
                "updatedAt" to FieldValue.serverTimestamp()
            )
        ).await()
        if (accept) {
            sendNotification(
                recipientUid = friendship.requesterId,
                senderName = responderProfile.displayName.ifBlank { "Player" },
                type = NotificationType.FRIEND_ACCEPTED,
                title = "Friend Request Accepted",
                body = "${responderProfile.displayName} accepted your friend request!"
            )
        }
        Unit
    }.onFailure { handleFirestoreError(it, OperationType.UPDATE, "$COL_FRIENDSHIPS/${friendship.id}") }

    suspend fun removeFriendship(friendshipId: String): Result<Unit> = runCatching {
        db.collection(COL_FRIENDSHIPS).document(friendshipId).delete().await()
        Unit
    }.onFailure { handleFirestoreError(it, OperationType.DELETE, "$COL_FRIENDSHIPS/$friendshipId") }

    fun observeBlockedUsers(userId: String): Flow<List<BlockedUser>> = callbackFlow {
        val path = "$COL_USERS/$userId/$SUB_BLOCKED"
        val reg = db.collection(COL_USERS).document(userId).collection(SUB_BLOCKED)
            .addSnapshotListener { snap, err ->
                if (err != null) {
                    handleFirestoreError(err, OperationType.LIST, path)
                    close(err)
                    return@addSnapshotListener
                }
                val list = snap?.documents?.mapNotNull { it.toObject(BlockedUser::class.java) } ?: emptyList()
                trySend(list)
            }
        awaitClose { reg.remove() }
    }

    suspend fun blockUser(targetUid: String, targetName: String, targetTag: String): Result<Unit> = runCatching {
        val myUid = uid()
        require(myUid != targetUid)
        db.collection(COL_USERS).document(myUid)
            .collection(SUB_BLOCKED).document(targetUid)
            .set(
                mapOf(
                    "targetUid" to targetUid,
                    "targetName" to targetName.take(40),
                    "targetTag" to targetTag.take(30),
                    "createdAt" to FieldValue.serverTimestamp()
                )
            ).await()
        // Also remove active friendship if one exists
        val fId = listOf(myUid, targetUid).sorted().joinToString("_")
        runCatching { db.collection(COL_FRIENDSHIPS).document(fId).delete().await() }
        Unit
    }.onFailure { handleFirestoreError(it, OperationType.CREATE, "$COL_USERS/blocked_users/$targetUid") }

    suspend fun unblockUser(targetUid: String): Result<Unit> = runCatching {
        val myUid = uid()
        db.collection(COL_USERS).document(myUid)
            .collection(SUB_BLOCKED).document(targetUid)
            .delete().await()
        Unit
    }.onFailure { handleFirestoreError(it, OperationType.DELETE, "$COL_USERS/${auth.currentUser?.uid}/$SUB_BLOCKED/$targetUid") }

    fun observeNotifications(userId: String): Flow<List<NexusNotification>> = callbackFlow {
        val path = "$COL_USERS/$userId/$SUB_NOTIFICATIONS"
        val reg = db.collection(COL_USERS).document(userId).collection(SUB_NOTIFICATIONS)
            .orderBy("createdAt", Query.Direction.DESCENDING)
            .limit(60)
            .addSnapshotListener { snap, err ->
                if (err != null) {
                    handleFirestoreError(err, OperationType.LIST, path)
                    close(err)
                    return@addSnapshotListener
                }
                val list = snap?.documents?.mapNotNull { it.toObject(NexusNotification::class.java) } ?: emptyList()
                trySend(list)
            }
        awaitClose { reg.remove() }
    }

    suspend fun sendNotification(
        recipientUid: String,
        senderName: String,
        type: NotificationType,
        title: String,
        body: String,
        targetServerId: String = ""
    ) {
        val myUid = auth.currentUser?.uid ?: return
        val notifId = "ntf_${UUID.randomUUID().toString().replace("-", "").take(14)}"
        runCatching {
            db.collection(COL_USERS).document(recipientUid)
                .collection(SUB_NOTIFICATIONS).document(notifId)
                .set(
                    buildMap {
                        put("userId", recipientUid)
                        put("senderId", myUid)
                        put("senderName", senderName.take(40))
                        put("type", type.name)
                        put("title", title.take(80))
                        put("body", body.take(240))
                        if (targetServerId.isNotBlank()) put("targetServerId", targetServerId)
                        put("isRead", false)
                        put("createdAt", FieldValue.serverTimestamp())
                    }
                ).await()
        }
    }

    suspend fun markNotificationRead(notifId: String): Result<Unit> = runCatching {
        val myUid = uid()
        db.collection(COL_USERS).document(myUid)
            .collection(SUB_NOTIFICATIONS).document(notifId)
            .update("isRead", true).await()
        Unit
    }

    suspend fun deleteNotification(notifId: String): Result<Unit> = runCatching {
        val myUid = uid()
        db.collection(COL_USERS).document(myUid)
            .collection(SUB_NOTIFICATIONS).document(notifId)
            .delete().await()
        Unit
    }

    // --- Direct Messages ---

    fun observeDmThreads(userId: String): Flow<List<DirectMessageThread>> = callbackFlow {
        val reg = db.collection(COL_DM_THREADS)
            .whereArrayContains("participantIds", userId)
            .addSnapshotListener { snap, err ->
                if (err != null) {
                    handleFirestoreError(err, OperationType.LIST, COL_DM_THREADS)
                    close(err)
                    return@addSnapshotListener
                }
                val list = snap?.documents
                    ?.mapNotNull { it.toObject(DirectMessageThread::class.java) }
                    ?.sortedByDescending { it.updatedAt?.seconds ?: 0L }
                    ?: emptyList()
                trySend(list)
            }
        awaitClose { reg.remove() }
    }

    fun observeDmMessages(threadId: String): Flow<List<DirectMessage>> = callbackFlow {
        val path = "$COL_DM_THREADS/$threadId/$SUB_MESSAGES"
        val reg = db.collection(COL_DM_THREADS).document(threadId).collection(SUB_MESSAGES)
            .orderBy("createdAt", Query.Direction.ASCENDING)
            .limitToLast(150)
            .addSnapshotListener { snap, err ->
                if (err != null) {
                    handleFirestoreError(err, OperationType.LIST, path)
                    close(err)
                    return@addSnapshotListener
                }
                val list = snap?.documents?.mapNotNull { it.toObject(DirectMessage::class.java) } ?: emptyList()
                trySend(list)
            }
        awaitClose { reg.remove() }
    }

    suspend fun openOrCreateDmThread(me: GamerProfile, peer: GamerProfile): Result<String> = runCatching {
        val myUid = uid()
        val sortedIds = listOf(myUid, peer.userId).sorted()
        val threadId = sortedIds.joinToString("_")
        val ref = db.collection(COL_DM_THREADS).document(threadId)
        val snap = ref.get().await()
        if (!snap.exists()) {
            ref.set(
                mapOf(
                    "participantIds" to sortedIds,
                    "participantNames" to mapOf(myUid to me.displayName, peer.userId to peer.displayName),
                    "participantTags" to mapOf(myUid to me.gamerTag, peer.userId to peer.gamerTag),
                    "lastMessage" to "Started a direct conversation",
                    "lastSenderId" to myUid,
                    "createdAt" to FieldValue.serverTimestamp(),
                    "updatedAt" to FieldValue.serverTimestamp()
                )
            ).await()
        }
        threadId
    }.onFailure { handleFirestoreError(it, OperationType.WRITE, COL_DM_THREADS) }

    suspend fun sendDirectMessage(
        threadId: String,
        sender: GamerProfile,
        text: String,
        replyTo: DirectMessage? = null,
        attachment: PendingAttachment? = null
    ): Result<String> = runCatching {
        val myUid = uid()
        val msgId = "dm_${UUID.randomUUID().toString().replace("-", "").take(16)}"
        val msgRef = db.collection(COL_DM_THREADS).document(threadId)
            .collection(SUB_MESSAGES).document(msgId)
        val threadRef = db.collection(COL_DM_THREADS).document(threadId)

        val previewText = text.trim().ifBlank { attachment?.fileName ?: "Attachment" }.take(200)
        val batch = db.batch()
        batch.set(
            msgRef,
            buildMap {
                put("threadId", threadId)
                put("senderId", myUid)
                put("senderName", sender.displayName.ifBlank { "Player" })
                put("senderTag", sender.gamerTag.ifBlank { "NexusUser" })
                put("senderAvatarId", sender.avatarId)
                put("text", text.trim())
                if (replyTo != null) {
                    put("replyToId", replyTo.id)
                    put("replyToAuthor", replyTo.senderName)
                    put("replyToSnippet", replyTo.text.ifBlank { "[${replyTo.attachmentType}]" }.take(120))
                }
                put("attachmentType", attachment?.type ?: AttachmentType.NONE.name)
                if (attachment != null) {
                    put("attachmentName", attachment.fileName.take(120))
                    put("attachmentMime", attachment.mimeType.take(80))
                    put("attachmentData", attachment.base64Data)
                }
                put("reactions", emptyMap<String, List<String>>())
                put("isEdited", false)
                put("createdAt", FieldValue.serverTimestamp())
                put("updatedAt", FieldValue.serverTimestamp())
            }
        )
        batch.update(
            threadRef,
            mapOf(
                "lastMessage" to previewText,
                "lastSenderId" to myUid,
                "updatedAt" to FieldValue.serverTimestamp()
            )
        )
        batch.commit().await()
        msgId
    }.onFailure { handleFirestoreError(it, OperationType.CREATE, "$COL_DM_THREADS/$threadId/$SUB_MESSAGES") }

    suspend fun editDirectMessage(threadId: String, messageId: String, newText: String): Result<Unit> = runCatching {
        db.collection(COL_DM_THREADS).document(threadId)
            .collection(SUB_MESSAGES).document(messageId)
            .update(
                mapOf(
                    "text" to newText.trim(),
                    "isEdited" to true,
                    "updatedAt" to FieldValue.serverTimestamp()
                )
            ).await()
        Unit
    }

    suspend fun deleteDirectMessage(threadId: String, messageId: String): Result<Unit> = runCatching {
        db.collection(COL_DM_THREADS).document(threadId)
            .collection(SUB_MESSAGES).document(messageId)
            .delete().await()
        Unit
    }

    suspend fun toggleDmReaction(threadId: String, message: DirectMessage, emoji: String): Result<Unit> = runCatching {
        val myUid = uid()
        val updated = message.reactions.toMutableMap()
        val list = updated[emoji]?.toMutableList() ?: mutableListOf()
        if (myUid in list) list.remove(myUid) else list.add(myUid)
        if (list.isEmpty()) updated.remove(emoji) else updated[emoji] = list
        db.collection(COL_DM_THREADS).document(threadId)
            .collection(SUB_MESSAGES).document(message.id)
            .update(
                mapOf(
                    "reactions" to updated,
                    "updatedAt" to FieldValue.serverTimestamp()
                )
            ).await()
        Unit
    }

    companion object {
        const val COL_USERS = "users"
        const val SUB_BLOCKED = "blocked_users"
        const val SUB_NOTIFICATIONS = "notifications"
        const val COL_PROFILES = "gamer_profiles"
        const val COL_FRIENDSHIPS = "friendships"
        const val COL_DM_THREADS = "dm_threads"
        const val SUB_MESSAGES = "messages"
    }
}

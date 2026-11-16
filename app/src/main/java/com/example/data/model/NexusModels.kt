package com.example.data.model

import com.google.firebase.Timestamp
import com.google.firebase.firestore.DocumentId
import com.google.firebase.firestore.FieldValue
import com.google.firebase.firestore.IgnoreExtraProperties
import com.google.firebase.firestore.ServerTimestamp

enum class OnlineStatus(val label: String) {
    ONLINE("Online"),
    IN_GAME("In Game"),
    AFK("Idle"),
    OFFLINE("Offline");

    companion object {
        fun fromRaw(raw: String?): OnlineStatus =
            entries.firstOrNull { it.name.equals(raw, ignoreCase = true) } ?: ONLINE
    }
}

enum class ServerVisibility(val label: String) {
    PUBLIC("Public"),
    PRIVATE("Private");

    companion object {
        fun fromRaw(raw: String?): ServerVisibility =
            entries.firstOrNull { it.name.equals(raw, ignoreCase = true) } ?: PUBLIC
    }
}

enum class ServerRole(val label: String, val rank: Int) {
    OWNER("Owner", 4),
    ADMIN("Admin", 3),
    MOD("Moderator", 2),
    MEMBER("Member", 1);

    companion object {
        fun fromRaw(raw: String?): ServerRole =
            entries.firstOrNull { it.name.equals(raw, ignoreCase = true) } ?: MEMBER
    }
}

enum class ChannelType(val label: String) {
    TEXT("Text"),
    VOICE("Voice"),
    ANNOUNCEMENT("Announcements");

    companion object {
        fun fromRaw(raw: String?): ChannelType =
            entries.firstOrNull { it.name.equals(raw, ignoreCase = true) } ?: TEXT
    }
}

enum class AttachmentType {
    NONE,
    IMAGE,
    FILE,
    VOICE;

    companion object {
        fun fromRaw(raw: String?): AttachmentType =
            entries.firstOrNull { it.name.equals(raw, ignoreCase = true) } ?: NONE
    }
}

enum class FriendshipStatus {
    PENDING,
    ACCEPTED,
    DECLINED;

    companion object {
        fun fromRaw(raw: String?): FriendshipStatus =
            entries.firstOrNull { it.name.equals(raw, ignoreCase = true) } ?: PENDING
    }
}

enum class NotificationType {
    FRIEND_REQUEST,
    FRIEND_ACCEPTED,
    SERVER_INVITE,
    ROLE_ASSIGNED,
    MENTION,
    MOD_ACTION;

    companion object {
        fun fromRaw(raw: String?): NotificationType =
            entries.firstOrNull { it.name.equals(raw, ignoreCase = true) } ?: MENTION
    }
}

enum class AvatarPreset(val id: String, val label: String, val glyph: String) {
    CYBER_KNIGHT("cyber_knight", "Cyber Knight", "⚡"),
    NEON_ROGUE("neon_rogue", "Neon Rogue", "🦊"),
    VOID_MAGE("void_mage", "Void Mage", "🔮"),
    APEX_PILOT("apex_pilot", "Apex Pilot", "🚀"),
    STAR_STRIKER("star_striker", "Star Striker", "⚔️"),
    PIXEL_PHANTOM("pixel_phantom", "Pixel Phantom", "👾");

    companion object {
        fun fromId(id: String?): AvatarPreset =
            entries.firstOrNull { it.id == id } ?: CYBER_KNIGHT
    }
}

enum class BadgePreset(val id: String, val title: String, val icon: String) {
    EARLY_ADOPTER("EARLY_ADOPTER", "Nexus Pioneer", "⚡"),
    FOUNDER("FOUNDER", "Server Founder", "👑"),
    VOICE_HOST("VOICE_HOST", "Voice Commander", "🎙️"),
    COMMUNITY_STAR("COMMUNITY_STAR", "Squad Builder", "🌟"),
    MODERATOR("MODERATOR", "Guardian Mod", "🛡️");

    companion object {
        fun fromId(id: String?): BadgePreset? = entries.firstOrNull { it.id == id }
    }
}

@IgnoreExtraProperties
data class GamerProfile(
    @DocumentId val id: String = "",
    val userId: String = "",
    val displayName: String = "",
    val gamerTag: String = "",
    val bio: String = "",
    val avatarId: String = AvatarPreset.CYBER_KNIGHT.id,
    val customAvatarData: String = "",
    val bannerColorKey: String = "purple_blue",
    val onlineStatus: String = OnlineStatus.ONLINE.name,
    val customStatusText: String = "",
    val badges: List<String> = listOf(BadgePreset.EARLY_ADOPTER.id),
    val visibility: String = "PUBLIC",
    @ServerTimestamp val createdAt: Timestamp? = null,
    @ServerTimestamp val updatedAt: Timestamp? = null
) {
    val statusEnum: OnlineStatus get() = OnlineStatus.fromRaw(onlineStatus)
    val avatarPreset: AvatarPreset get() = AvatarPreset.fromId(avatarId)

    fun toCreateMap(): Map<String, Any> = buildMap {
        put("userId", userId)
        put("displayName", displayName)
        put("gamerTag", gamerTag)
        if (bio.isNotBlank()) put("bio", bio)
        put("avatarId", avatarId)
        if (customAvatarData.isNotBlank()) put("customAvatarData", customAvatarData)
        put("bannerColorKey", bannerColorKey)
        put("onlineStatus", onlineStatus)
        if (customStatusText.isNotBlank()) put("customStatusText", customStatusText)
        put("badges", badges.take(8))
        put("visibility", "PUBLIC")
        put("createdAt", FieldValue.serverTimestamp())
        put("updatedAt", FieldValue.serverTimestamp())
    }
}

@IgnoreExtraProperties
data class NexusServer(
    @DocumentId val id: String = "",
    val ownerId: String = "",
    val name: String = "",
    val description: String = "",
    val iconEmoji: String = "⚡",
    val iconColorKey: String = "purple_blue",
    val iconData: String = "",
    val visibility: String = ServerVisibility.PUBLIC.name,
    val inviteCode: String = "",
    val memberUids: List<String> = emptyList(),
    val adminUids: List<String> = emptyList(),
    val modUids: List<String> = emptyList(),
    val bannedUids: List<String> = emptyList(),
    val memberCount: Int = 1,
    @ServerTimestamp val createdAt: Timestamp? = null,
    @ServerTimestamp val updatedAt: Timestamp? = null
) {
    val visibilityEnum: ServerVisibility get() = ServerVisibility.fromRaw(visibility)

    fun roleFor(uid: String): ServerRole = when {
        uid == ownerId -> ServerRole.OWNER
        uid in adminUids -> ServerRole.ADMIN
        uid in modUids -> ServerRole.MOD
        else -> ServerRole.MEMBER
    }
}

@IgnoreExtraProperties
data class ServerInvite(
    @DocumentId val id: String = "",
    val inviteCode: String = "",
    val serverId: String = "",
    val serverName: String = "",
    val serverDescription: String = "",
    val iconEmoji: String = "⚡",
    val iconColorKey: String = "purple_blue",
    val visibility: String = ServerVisibility.PUBLIC.name,
    val ownerId: String = "",
    @ServerTimestamp val createdAt: Timestamp? = null,
    @ServerTimestamp val updatedAt: Timestamp? = null
)

@IgnoreExtraProperties
data class ServerMember(
    @DocumentId val id: String = "",
    val userId: String = "",
    val displayName: String = "",
    val gamerTag: String = "",
    val avatarId: String = AvatarPreset.CYBER_KNIGHT.id,
    val customAvatarData: String = "",
    val onlineStatus: String = OnlineStatus.ONLINE.name,
    val role: String = ServerRole.MEMBER.name,
    val badges: List<String> = emptyList(),
    @ServerTimestamp val joinedAt: Timestamp? = null,
    @ServerTimestamp val updatedAt: Timestamp? = null
) {
    val statusEnum: OnlineStatus get() = OnlineStatus.fromRaw(onlineStatus)
    val roleEnum: ServerRole get() = ServerRole.fromRaw(role)
}

@IgnoreExtraProperties
data class ServerChannel(
    @DocumentId val id: String = "",
    val serverId: String = "",
    val name: String = "",
    val topic: String = "",
    val type: String = ChannelType.TEXT.name,
    val categoryName: String = "TEXT CHANNELS",
    val position: Int = 0,
    val adminOnlyPost: Boolean = false,
    val createdBy: String = "",
    @ServerTimestamp val createdAt: Timestamp? = null,
    @ServerTimestamp val updatedAt: Timestamp? = null
) {
    val typeEnum: ChannelType get() = ChannelType.fromRaw(type)
}

@IgnoreExtraProperties
data class ChannelMessage(
    @DocumentId val id: String = "",
    val serverId: String = "",
    val channelId: String = "",
    val senderId: String = "",
    val senderName: String = "",
    val senderTag: String = "",
    val senderAvatarId: String = AvatarPreset.CYBER_KNIGHT.id,
    val senderRole: String = ServerRole.MEMBER.name,
    val text: String = "",
    val replyToId: String = "",
    val replyToAuthor: String = "",
    val replyToSnippet: String = "",
    val attachmentType: String = AttachmentType.NONE.name,
    val attachmentName: String = "",
    val attachmentMime: String = "",
    val attachmentData: String = "",
    val audioDurationSec: Int = 0,
    val reactions: Map<String, List<String>> = emptyMap(),
    val isEdited: Boolean = false,
    @ServerTimestamp val createdAt: Timestamp? = null,
    @ServerTimestamp val updatedAt: Timestamp? = null
) {
    val attachmentEnum: AttachmentType get() = AttachmentType.fromRaw(attachmentType)
    val senderRoleEnum: ServerRole get() = ServerRole.fromRaw(senderRole)
}

@IgnoreExtraProperties
data class VoiceMember(
    @DocumentId val id: String = "",
    val serverId: String = "",
    val channelId: String = "",
    val userId: String = "",
    val displayName: String = "",
    val gamerTag: String = "",
    val avatarId: String = AvatarPreset.CYBER_KNIGHT.id,
    val isMuted: Boolean = false,
    val isDeafened: Boolean = false,
    val isSpeaking: Boolean = false,
    @ServerTimestamp val joinedAt: Timestamp? = null,
    @ServerTimestamp val updatedAt: Timestamp? = null
)

@IgnoreExtraProperties
data class Friendship(
    @DocumentId val id: String = "",
    val userIds: List<String> = emptyList(),
    val requesterId: String = "",
    val requesterName: String = "",
    val requesterTag: String = "",
    val requesterAvatarId: String = AvatarPreset.CYBER_KNIGHT.id,
    val recipientId: String = "",
    val recipientName: String = "",
    val recipientTag: String = "",
    val recipientAvatarId: String = AvatarPreset.CYBER_KNIGHT.id,
    val status: String = FriendshipStatus.PENDING.name,
    @ServerTimestamp val createdAt: Timestamp? = null,
    @ServerTimestamp val updatedAt: Timestamp? = null
) {
    val statusEnum: FriendshipStatus get() = FriendshipStatus.fromRaw(status)

    fun otherUserId(myUid: String): String =
        if (requesterId == myUid) recipientId else requesterId
}

@IgnoreExtraProperties
data class BlockedUser(
    @DocumentId val id: String = "",
    val targetUid: String = "",
    val targetName: String = "",
    val targetTag: String = "",
    @ServerTimestamp val createdAt: Timestamp? = null
)

@IgnoreExtraProperties
data class DirectMessageThread(
    @DocumentId val id: String = "",
    val participantIds: List<String> = emptyList(),
    val participantNames: Map<String, String> = emptyMap(),
    val participantTags: Map<String, String> = emptyMap(),
    val lastMessage: String = "",
    val lastSenderId: String = "",
    @ServerTimestamp val createdAt: Timestamp? = null,
    @ServerTimestamp val updatedAt: Timestamp? = null
)

@IgnoreExtraProperties
data class DirectMessage(
    @DocumentId val id: String = "",
    val threadId: String = "",
    val senderId: String = "",
    val senderName: String = "",
    val senderTag: String = "",
    val senderAvatarId: String = AvatarPreset.CYBER_KNIGHT.id,
    val text: String = "",
    val replyToId: String = "",
    val replyToAuthor: String = "",
    val replyToSnippet: String = "",
    val attachmentType: String = AttachmentType.NONE.name,
    val attachmentName: String = "",
    val attachmentMime: String = "",
    val attachmentData: String = "",
    val audioDurationSec: Int = 0,
    val reactions: Map<String, List<String>> = emptyMap(),
    val isEdited: Boolean = false,
    @ServerTimestamp val createdAt: Timestamp? = null,
    @ServerTimestamp val updatedAt: Timestamp? = null
) {
    val attachmentEnum: AttachmentType get() = AttachmentType.fromRaw(attachmentType)
}

@IgnoreExtraProperties
data class NexusNotification(
    @DocumentId val id: String = "",
    val userId: String = "",
    val senderId: String = "",
    val senderName: String = "",
    val type: String = NotificationType.MENTION.name,
    val title: String = "",
    val body: String = "",
    val targetServerId: String = "",
    val isRead: Boolean = false,
    @ServerTimestamp val createdAt: Timestamp? = null
) {
    val typeEnum: NotificationType get() = NotificationType.fromRaw(type)
}

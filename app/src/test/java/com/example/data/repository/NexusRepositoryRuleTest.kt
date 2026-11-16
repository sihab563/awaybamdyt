package com.example.data.repository

import com.example.base.FirestoreEmulatorTestBase
import com.example.data.model.ChannelType
import com.example.data.model.FriendshipStatus
import com.example.data.model.OnlineStatus
import com.example.data.model.ServerRole
import com.example.data.model.ServerVisibility
import com.google.firebase.firestore.FirebaseFirestoreException
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withTimeout
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Test
import java.util.UUID

class NexusRepositoryRuleTest : FirestoreEmulatorTestBase() {

    private fun randomEmail(prefix: String): String =
        "${prefix}_${UUID.randomUUID().toString().take(8)}@nexus.test"

    @Test
    fun unauthenticatedUser_observeJoinedServers_failsWithPermissionDenied() = runBlocking {
        auth.signOut()
        val repo = NexusServerRepository(firestore, auth)

        val err = runCatching {
            withTimeout(FLOW_TIMEOUT_MS) {
                repo.observeJoinedServers("unauth_uid").first()
            }
        }.exceptionOrNull()

        assertNotNull(err)
        val firestoreErr = err!!.unwrapFirestoreException()
        assertNotNull("Expected FirebaseFirestoreException, got $err", firestoreErr)
        assertEquals(FirebaseFirestoreException.Code.PERMISSION_DENIED, firestoreErr!!.code)
    }

    @Test
    fun serverChannelsChatVoiceAndModeration_endToEndFlow() = runBlocking {
        val aliceEmail = randomEmail("alice")
        val bobEmail = randomEmail("bob")

        val aliceUid = signInTestUser(aliceEmail)
        val socialAlice = NexusSocialRepository(firestore, auth)
        val serverAlice = NexusServerRepository(firestore, auth)
        val aliceProfile = withTimeout(TIMEOUT_MS) {
            socialAlice.ensureSignedInProfile("AlicePrime").getOrThrow()
        }

        // Alice creates a PRIVATE server + invite + default channels
        val serverId = withTimeout(TIMEOUT_MS) {
            serverAlice.createServer(
                name = "Vanguard",
                description = "Private squad server",
                iconEmoji = "⚡",
                iconColorKey = "purple_blue",
                visibility = ServerVisibility.PRIVATE,
                ownerProfile = aliceProfile
            ).getOrThrow()
        }

        val aliceServers = withTimeout(FLOW_TIMEOUT_MS) {
            serverAlice.observeJoinedServers(aliceUid).first { it.any { s -> s.id == serverId } }
        }
        val createdServer = aliceServers.first { it.id == serverId }

        // Bob joins via Invite Code
        val bobUid = signInTestUser(bobEmail)
        val socialBob = NexusSocialRepository(firestore, auth)
        val serverBob = NexusServerRepository(firestore, auth)
        val bobProfile = withTimeout(TIMEOUT_MS) {
            socialBob.ensureSignedInProfile("BobStrike").getOrThrow()
        }

        val joinedId = withTimeout(TIMEOUT_MS) {
            serverBob.joinServerByInviteCode(createdServer.inviteCode, bobProfile).getOrThrow()
        }
        assertEquals(serverId, joinedId)

        val channels = withTimeout(FLOW_TIMEOUT_MS) {
            serverBob.observeChannels(serverId).first { it.size >= 3 }
        }
        val generalCh = channels.first { it.typeEnum == ChannelType.TEXT }
        val announceCh = channels.first { it.typeEnum == ChannelType.ANNOUNCEMENT }
        val voiceCh = channels.first { it.typeEnum == ChannelType.VOICE }

        // Bob cannot post in adminOnlyPost Announcement channel
        val annFail = withTimeout(TIMEOUT_MS) {
            serverBob.sendChannelMessage(
                serverId = serverId,
                channelId = announceCh.id,
                senderProfile = bobProfile,
                senderRole = ServerRole.MEMBER,
                text = "Unauthorized"
            )
        }
        assertTrue(annFail.isFailure)
        assertEquals(
            FirebaseFirestoreException.Code.PERMISSION_DENIED,
            annFail.exceptionOrNull()!!.unwrapFirestoreException()!!.code
        )

        // Bob sends chat message in general, edits it, joins/updates/leaves voice room
        val msgId = withTimeout(TIMEOUT_MS) {
            serverBob.sendChannelMessage(
                serverId = serverId,
                channelId = generalCh.id,
                senderProfile = bobProfile,
                senderRole = ServerRole.MEMBER,
                text = "GGs ready!"
            ).getOrThrow()
        }
        val bobMsgs = withTimeout(FLOW_TIMEOUT_MS) {
            serverBob.observeChannelMessages(serverId, generalCh.id).first { it.any { m -> m.id == msgId } }
        }
        val bobMsg = bobMsgs.first { it.id == msgId }
        withTimeout(TIMEOUT_MS) {
            serverBob.editChannelMessage(serverId, generalCh.id, msgId, "GGs ready at 8!").getOrThrow()
        }

        withTimeout(TIMEOUT_MS) {
            serverBob.joinVoiceRoom(serverId, voiceCh.id, bobProfile, isMuted = false).getOrThrow()
        }
        val voiceList = withTimeout(FLOW_TIMEOUT_MS) {
            serverBob.observeVoiceMembers(serverId, voiceCh.id).first { it.any { v -> v.userId == bobUid } }
        }
        assertEquals(1, voiceList.size)
        withTimeout(TIMEOUT_MS) {
            serverBob.updateVoiceMemberState(serverId, voiceCh.id, isMuted = true, isDeafened = false, isSpeaking = false).getOrThrow()
            serverBob.leaveVoiceRoom(serverId, voiceCh.id).getOrThrow()
        }

        // Alice reacts, assigns MOD role, then bans Bob -> Bob fails to re-join
        signInTestUser(aliceEmail)
        val refreshedServer = withTimeout(FLOW_TIMEOUT_MS) {
            serverAlice.observeJoinedServers(aliceUid).first { list ->
                list.firstOrNull { it.id == serverId }?.memberUids?.contains(bobUid) == true
            }
        }.first { it.id == serverId }

        withTimeout(TIMEOUT_MS) {
            serverAlice.toggleChannelReaction(serverId, generalCh.id, bobMsg, "🔥").getOrThrow()
            serverAlice.assignMemberRole(refreshedServer, bobUid, ServerRole.MOD).getOrThrow()
            serverAlice.deleteChannelMessage(serverId, generalCh.id, msgId).getOrThrow()
            serverAlice.kickOrBanMember(refreshedServer, bobUid, ban = true).getOrThrow()
        }

        signInTestUser(bobEmail)
        val rejoinFail = withTimeout(TIMEOUT_MS) {
            serverBob.joinServerByInviteCode(createdServer.inviteCode, bobProfile)
        }
        assertTrue(rejoinFail.isFailure)
        assertEquals(
            FirebaseFirestoreException.Code.PERMISSION_DENIED,
            rejoinFail.exceptionOrNull()!!.unwrapFirestoreException()!!.code
        )
    }

    @Test
    fun friendsDirectMessagesAndBlocking_enforcedByRules() = runBlocking {
        val aliceEmail = randomEmail("dm_alice")
        val bobEmail = randomEmail("dm_bob")

        val aliceUid = signInTestUser(aliceEmail)
        val socialAlice = NexusSocialRepository(firestore, auth)
        val aliceProfile = withTimeout(TIMEOUT_MS) {
            socialAlice.ensureSignedInProfile("AliceDM").getOrThrow()
        }

        val bobUid = signInTestUser(bobEmail)
        val socialBob = NexusSocialRepository(firestore, auth)
        val bobProfile = withTimeout(TIMEOUT_MS) {
            socialBob.ensureSignedInProfile("BobDM").getOrThrow()
        }

        // Bob sends friend request + DM to Alice
        withTimeout(TIMEOUT_MS) {
            socialBob.sendFriendRequest(bobProfile, aliceProfile).getOrThrow()
        }

        signInTestUser(aliceEmail)
        val pending = withTimeout(FLOW_TIMEOUT_MS) {
            socialAlice.observeFriendships(aliceUid).first { it.isNotEmpty() }
        }.first()
        assertEquals(FriendshipStatus.PENDING, pending.statusEnum)

        withTimeout(TIMEOUT_MS) {
            socialAlice.respondToFriendRequest(pending, accept = true, responderProfile = aliceProfile).getOrThrow()
            socialAlice.setOnlineStatus(OnlineStatus.IN_GAME).getOrThrow()
        }

        val threadId = withTimeout(TIMEOUT_MS) {
            socialAlice.openOrCreateDmThread(aliceProfile, bobProfile).getOrThrow()
        }
        withTimeout(TIMEOUT_MS) {
            socialAlice.sendDirectMessage(threadId, aliceProfile, "Ready for ranked?").getOrThrow()
        }

        // Alice blocks Bob -> Bob is denied sending further DMs
        withTimeout(TIMEOUT_MS) {
            socialAlice.blockUser(bobUid, bobProfile.displayName, bobProfile.gamerTag).getOrThrow()
        }

        signInTestUser(bobEmail)
        val blockedDmAttempt = withTimeout(TIMEOUT_MS) {
            socialBob.sendDirectMessage(threadId, bobProfile, "Should fail when blocked")
        }
        assertTrue(blockedDmAttempt.isFailure)
        assertEquals(
            FirebaseFirestoreException.Code.PERMISSION_DENIED,
            blockedDmAttempt.exceptionOrNull()!!.unwrapFirestoreException()!!.code
        )
    }

    private companion object {
        const val TIMEOUT_MS = 5000L
        const val FLOW_TIMEOUT_MS = 3500L
    }
}

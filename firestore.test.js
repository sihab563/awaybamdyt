const {
  initializeTestEnvironment,
  assertFails,
  assertSucceeds,
} = require("@firebase/rules-unit-testing");
const { test, before, after, beforeEach } = require("node:test");
const fs = require("node:fs");
let testEnv;
const PROJECT_ID = process.env.GCP_PROJECT || "demo-no-project";
const ALICE_UID = "alice_nexus_01";
const BOB_UID = "bob_nexus_02";
const CHARLIE_UID = "charlie_nexus_03";

const [emulatorHost, emulatorPortStr] = (process.env.FIRESTORE_EMULATOR_HOST || "127.0.0.1:8085").split(":");
const emulatorPort = parseInt(emulatorPortStr, 10);

function ts() {
  return new Date(Date.now() - 1000);
}

before(async () => {
  const rules = fs.readFileSync("./firestore.rules", "utf8");
  testEnv = await initializeTestEnvironment({
    projectId: PROJECT_ID,
    firestore: {
      rules,
      host: emulatorHost,
      port: emulatorPort,
    },
  });
});

after(async () => {
  if (testEnv) await testEnv.cleanup();
});

beforeEach(async () => {
  if (testEnv) await testEnv.clearFirestore();
});

async function seedProfile(uid, name, tag) {
  await testEnv.withSecurityRulesDisabled(async (ctx) => {
    const db = ctx.firestore();
    await db.collection("gamer_profiles").doc(uid).set({
      userId: uid,
      displayName: name,
      gamerTag: tag,
      bio: "Competitive gamer",
      avatarId: "cyber_knight",
      bannerColorKey: "purple_blue",
      onlineStatus: "ONLINE",
      customStatusText: "In Nexus",
      badges: ["EARLY_ADOPTER"],
      visibility: "PUBLIC",
      createdAt: ts(),
      updatedAt: ts(),
    });
  });
}

async function seedServer(serverId, ownerUid, visibility = "PUBLIC", inviteCode = "NX-TEST01") {
  await testEnv.withSecurityRulesDisabled(async (ctx) => {
    const db = ctx.firestore();
    await db.collection("nexus_servers").doc(serverId).set({
      ownerId: ownerUid,
      name: "Apex Vanguard",
      description: "Competitive gaming & voice hub",
      iconEmoji: "⚡",
      iconColorKey: "purple_blue",
      visibility,
      inviteCode,
      memberUids: [ownerUid],
      adminUids: [],
      modUids: [],
      bannedUids: [],
      memberCount: 1,
      createdAt: ts(),
      updatedAt: ts(),
    });
    await db.collection("server_invites").doc(inviteCode).set({
      inviteCode,
      serverId,
      serverName: "Apex Vanguard",
      serverDescription: "Competitive gaming & voice hub",
      iconEmoji: "⚡",
      iconColorKey: "purple_blue",
      visibility,
      ownerId: ownerUid,
      createdAt: ts(),
      updatedAt: ts(),
    });
    await db.collection("nexus_servers").doc(serverId).collection("members").doc(ownerUid).set({
      userId: ownerUid,
      displayName: "Alice",
      gamerTag: "AlicePrime",
      avatarId: "cyber_knight",
      onlineStatus: "ONLINE",
      role: "OWNER",
      badges: ["FOUNDER"],
      joinedAt: ts(),
      updatedAt: ts(),
    });
    await db.collection("nexus_servers").doc(serverId).collection("channels").doc("ch_general").set({
      serverId,
      name: "general",
      topic: "Main squad chat",
      type: "TEXT",
      categoryName: "TEXT CHANNELS",
      position: 0,
      adminOnlyPost: false,
      createdBy: ownerUid,
      createdAt: ts(),
      updatedAt: ts(),
    });
    await db.collection("nexus_servers").doc(serverId).collection("channels").doc("ch_ann").set({
      serverId,
      name: "announcements",
      topic: "Official server news",
      type: "ANNOUNCEMENT",
      categoryName: "INFO",
      position: 1,
      adminOnlyPost: true,
      createdBy: ownerUid,
      createdAt: ts(),
      updatedAt: ts(),
    });
    await db.collection("nexus_servers").doc(serverId).collection("channels").doc("ch_voice").set({
      serverId,
      name: "Squad Voice",
      topic: "Drop in & comms",
      type: "VOICE",
      categoryName: "VOICE LOUNGES",
      position: 2,
      adminOnlyPost: false,
      createdBy: ownerUid,
      createdAt: ts(),
      updatedAt: ts(),
    });
  });
}

// --- SECURITY BOUNDS & UNATHENTICATED REJECTION ---

test("Unauthenticated user: cannot read profiles, servers, or friendships", async () => {
  const unauth = testEnv.unauthenticatedContext().firestore();
  await assertFails(unauth.collection("gamer_profiles").where("visibility", "==", "PUBLIC").get());
  await assertFails(unauth.collection("nexus_servers").where("visibility", "==", "PUBLIC").get());
  await assertFails(unauth.collection("friendships").get());
});

test("Private PII users/{uid}: strictly isolated to owner", async () => {
  const alice = testEnv.authenticatedContext(ALICE_UID).firestore();
  const bob = testEnv.authenticatedContext(BOB_UID).firestore();

  await assertSucceeds(
    alice.collection("users").doc(ALICE_UID).set({
      userId: ALICE_UID,
      email: "alice@example.com",
      displayName: "Alice",
      gamerTag: "AlicePrime",
      avatarId: "cyber_knight",
      onlineStatus: "ONLINE",
      createdAt: ts(),
      updatedAt: ts(),
    })
  );

  await assertFails(bob.collection("users").doc(ALICE_UID).get());
  await assertFails(
    bob.collection("users").doc(ALICE_UID).set({
      userId: ALICE_UID,
      displayName: "Spoof",
      gamerTag: "SpoofTag",
      avatarId: "cyber_knight",
      onlineStatus: "ONLINE",
      createdAt: ts(),
      updatedAt: ts(),
    })
  );
});

// --- SERVER CREATION, INVITE LOOKUP & PRIVATE ACCESS ---

test("Atomic batch: owner creates server + invite + member + default channels", async () => {
  const alice = testEnv.authenticatedContext(ALICE_UID).firestore();
  const batch = alice.batch();
  const srvRef = alice.collection("nexus_servers").doc("srv_atomic");
  batch.set(srvRef, {
    ownerId: ALICE_UID,
    name: "Neon Citadel",
    description: "Midnight raids",
    iconEmoji: "🛡️",
    iconColorKey: "purple_blue",
    visibility: "PRIVATE",
    inviteCode: "NX-CITADEL",
    memberUids: [ALICE_UID],
    adminUids: [],
    modUids: [],
    bannedUids: [],
    memberCount: 1,
    createdAt: ts(),
    updatedAt: ts(),
  });
  batch.set(alice.collection("server_invites").doc("NX-CITADEL"), {
    inviteCode: "NX-CITADEL",
    serverId: "srv_atomic",
    serverName: "Neon Citadel",
    serverDescription: "Midnight raids",
    iconEmoji: "🛡️",
    iconColorKey: "purple_blue",
    visibility: "PRIVATE",
    ownerId: ALICE_UID,
    createdAt: ts(),
    updatedAt: ts(),
  });
  batch.set(srvRef.collection("members").doc(ALICE_UID), {
    userId: ALICE_UID,
    displayName: "Alice",
    gamerTag: "AlicePrime",
    avatarId: "cyber_knight",
    onlineStatus: "ONLINE",
    role: "OWNER",
    badges: ["FOUNDER"],
    joinedAt: ts(),
    updatedAt: ts(),
  });
  batch.set(srvRef.collection("channels").doc("ch_1"), {
    serverId: "srv_atomic",
    name: "general",
    topic: "Welcome",
    type: "TEXT",
    categoryName: "TEXT CHANNELS",
    position: 0,
    adminOnlyPost: false,
    createdBy: ALICE_UID,
    createdAt: ts(),
    updatedAt: ts(),
  });
  await assertSucceeds(batch.commit());

  // Non-member Bob cannot list or get private server directly
  const bob = testEnv.authenticatedContext(BOB_UID).firestore();
  await assertFails(bob.collection("nexus_servers").doc("srv_atomic").get());
  await assertFails(bob.collection("server_invites").get());

  // Bob CAN resolve invite code if he knows the exact code
  await assertSucceeds(bob.collection("server_invites").doc("NX-CITADEL").get());
});

test("Invite Join + Self-Escalation Guard + Banned User Rejection", async () => {
  await seedServer("srv_priv", ALICE_UID, "PRIVATE", "NX-JOIN99");
  const bob = testEnv.authenticatedContext(BOB_UID).firestore();

  // Bob cannot join directly as ADMIN (self-role escalation blocked)
  const badBatch = bob.batch();
  badBatch.update(bob.collection("nexus_servers").doc("srv_priv"), {
    memberUids: [ALICE_UID, BOB_UID],
    memberCount: 2,
    updatedAt: ts(),
  });
  badBatch.set(bob.collection("nexus_servers").doc("srv_priv").collection("members").doc(BOB_UID), {
    userId: BOB_UID,
    displayName: "Bob",
    gamerTag: "Bob99",
    avatarId: "neon_rogue",
    onlineStatus: "ONLINE",
    role: "ADMIN",
    badges: [],
    joinedAt: ts(),
    updatedAt: ts(),
  });
  await assertFails(badBatch.commit());

  // Bob validly joins as MEMBER
  const goodBatch = bob.batch();
  goodBatch.update(bob.collection("nexus_servers").doc("srv_priv"), {
    memberUids: [ALICE_UID, BOB_UID],
    memberCount: 2,
    updatedAt: ts(),
  });
  goodBatch.set(bob.collection("nexus_servers").doc("srv_priv").collection("members").doc(BOB_UID), {
    userId: BOB_UID,
    displayName: "Bob",
    gamerTag: "Bob99",
    avatarId: "neon_rogue",
    onlineStatus: "ONLINE",
    role: "MEMBER",
    badges: [],
    joinedAt: ts(),
    updatedAt: ts(),
  });
  await assertSucceeds(goodBatch.commit());

  // Alice kicks and bans Charlie -> Charlie cannot join
  const alice = testEnv.authenticatedContext(ALICE_UID).firestore();
  await assertSucceeds(
    alice.collection("nexus_servers").doc("srv_priv").update({
      bannedUids: [CHARLIE_UID],
      updatedAt: ts(),
    })
  );
  const charlie = testEnv.authenticatedContext(CHARLIE_UID).firestore();
  await assertFails(
    charlie.collection("nexus_servers").doc("srv_priv").update({
      memberUids: [ALICE_UID, BOB_UID, CHARLIE_UID],
      memberCount: 3,
      updatedAt: ts(),
    })
  );
});

// --- CHANNEL PERMISSIONS, CHAT MESSAGES, REACTIONS, EDITS & MODERATION ---

test("Channels: adminOnlyPost enforces role permissions, reactions & mod message delete work", async () => {
  await seedServer("srv_main", ALICE_UID, "PUBLIC", "NX-MAIN01");
  const alice = testEnv.authenticatedContext(ALICE_UID).firestore();
  const bob = testEnv.authenticatedContext(BOB_UID).firestore();

  // Bob joins
  const joinBatch = bob.batch();
  joinBatch.update(bob.collection("nexus_servers").doc("srv_main"), {
    memberUids: [ALICE_UID, BOB_UID],
    memberCount: 2,
    updatedAt: ts(),
  });
  joinBatch.set(bob.collection("nexus_servers").doc("srv_main").collection("members").doc(BOB_UID), {
    userId: BOB_UID,
    displayName: "Bob",
    gamerTag: "Bob99",
    avatarId: "neon_rogue",
    onlineStatus: "ONLINE",
    role: "MEMBER",
    badges: [],
    joinedAt: ts(),
    updatedAt: ts(),
  });
  await assertSucceeds(joinBatch.commit());

  // Bob cannot create channels or post in adminOnlyPost announcement channel
  await assertFails(
    bob.collection("nexus_servers").doc("srv_main").collection("channels").doc("ch_hack").set({
      serverId: "srv_main",
      name: "hacked",
      type: "TEXT",
      categoryName: "TEXT",
      position: 5,
      adminOnlyPost: false,
      createdBy: BOB_UID,
      createdAt: ts(),
      updatedAt: ts(),
    })
  );
  await assertFails(
    bob.collection("nexus_servers").doc("srv_main").collection("channels").doc("ch_ann").collection("messages").doc("m_ann_fail").set({
      serverId: "srv_main",
      channelId: "ch_ann",
      senderId: BOB_UID,
      senderName: "Bob",
      senderTag: "Bob99",
      senderAvatarId: "neon_rogue",
      text: "Unauthorized announcement",
      attachmentType: "NONE",
      reactions: {},
      isEdited: false,
      createdAt: ts(),
      updatedAt: ts(),
    })
  );

  // Bob posts in general chat
  const bobMsgRef = bob.collection("nexus_servers").doc("srv_main").collection("channels").doc("ch_general").collection("messages").doc("msg_1");
  await assertSucceeds(
    bobMsgRef.set({
      serverId: "srv_main",
      channelId: "ch_general",
      senderId: BOB_UID,
      senderName: "Bob",
      senderTag: "Bob99",
      senderAvatarId: "neon_rogue",
      senderRole: "MEMBER",
      text: "Squad up!",
      attachmentType: "NONE",
      reactions: {},
      isEdited: false,
      createdAt: ts(),
      updatedAt: ts(),
    })
  );

  // Alice adds emoji reaction to Bob's message, cannot edit Bob's text
  const aliceMsgRef = alice.collection("nexus_servers").doc("srv_main").collection("channels").doc("ch_general").collection("messages").doc("msg_1");
  await assertSucceeds(
    aliceMsgRef.update({
      reactions: { "🔥": [ALICE_UID] },
      updatedAt: ts(),
    })
  );
  await assertFails(
    aliceMsgRef.update({
      text: "Hijacked text",
      isEdited: true,
      updatedAt: ts(),
    })
  );

  // Bob edits own text
  await assertSucceeds(
    bobMsgRef.update({
      text: "Squad up at 9pm!",
      isEdited: true,
      updatedAt: ts(),
    })
  );

  // Server Owner Alice moderates & deletes Bob's message
  await assertSucceeds(aliceMsgRef.delete());
});

// --- VOICE ROOM PRESENCE ---

test("Voice room: members join/mute/leave; non-members denied; owner can mute/kick", async () => {
  await seedServer("srv_voice", ALICE_UID, "PUBLIC", "NX-VOX01");
  const alice = testEnv.authenticatedContext(ALICE_UID).firestore();
  const bob = testEnv.authenticatedContext(BOB_UID).firestore();
  const charlie = testEnv.authenticatedContext(CHARLIE_UID).firestore();

  // Bob joins server
  const joinBatch = bob.batch();
  joinBatch.update(bob.collection("nexus_servers").doc("srv_voice"), {
    memberUids: [ALICE_UID, BOB_UID],
    memberCount: 2,
    updatedAt: ts(),
  });
  joinBatch.set(bob.collection("nexus_servers").doc("srv_voice").collection("members").doc(BOB_UID), {
    userId: BOB_UID,
    displayName: "Bob",
    gamerTag: "Bob99",
    avatarId: "neon_rogue",
    onlineStatus: "ONLINE",
    role: "MEMBER",
    badges: [],
    joinedAt: ts(),
    updatedAt: ts(),
  });
  await assertSucceeds(joinBatch.commit());

  const bobVoiceRef = bob.collection("nexus_servers").doc("srv_voice").collection("channels").doc("ch_voice").collection("voice_members").doc(BOB_UID);
  await assertSucceeds(
    bobVoiceRef.set({
      serverId: "srv_voice",
      channelId: "ch_voice",
      userId: BOB_UID,
      displayName: "Bob",
      gamerTag: "Bob99",
      avatarId: "neon_rogue",
      isMuted: false,
      isDeafened: false,
      isSpeaking: true,
      joinedAt: ts(),
      updatedAt: ts(),
    })
  );

  // Charlie (non-member) cannot read or join voice room
  await assertFails(
    charlie.collection("nexus_servers").doc("srv_voice").collection("channels").doc("ch_voice").collection("voice_members").get()
  );

  // Server owner Alice server-mutes Bob
  const aliceVoiceBobRef = alice.collection("nexus_servers").doc("srv_voice").collection("channels").doc("ch_voice").collection("voice_members").doc(BOB_UID);
  await assertSucceeds(
    aliceVoiceBobRef.update({
      isMuted: true,
      isSpeaking: false,
      updatedAt: ts(),
    })
  );

  // Bob leaves voice room
  await assertSucceeds(bobVoiceRef.delete());
});

// --- FRIENDSHIPS, USER BLOCKING, DMS & NOTIFICATIONS ---

test("Blocking stops friend requests & DMs; notifications respect recipient/sender integrity", async () => {
  await seedProfile(ALICE_UID, "Alice", "AlicePrime");
  await seedProfile(BOB_UID, "Bob", "Bob99");

  const alice = testEnv.authenticatedContext(ALICE_UID).firestore();
  const bob = testEnv.authenticatedContext(BOB_UID).firestore();

  // Alice blocks Bob
  await assertSucceeds(
    alice.collection("users").doc(ALICE_UID).collection("blocked_users").doc(BOB_UID).set({
      targetUid: BOB_UID,
      targetName: "Bob",
      targetTag: "Bob99",
      createdAt: ts(),
    })
  );

  // Bob cannot send friend request to Alice while blocked
  const sortedIds = [ALICE_UID, BOB_UID].sort();
  await assertFails(
    bob.collection("friendships").doc(sortedIds.join("_")).set({
      userIds: sortedIds,
      requesterId: BOB_UID,
      requesterName: "Bob",
      requesterTag: "Bob99",
      requesterAvatarId: "neon_rogue",
      recipientId: ALICE_UID,
      recipientName: "Alice",
      recipientTag: "AlicePrime",
      recipientAvatarId: "cyber_knight",
      status: "PENDING",
      createdAt: ts(),
      updatedAt: ts(),
    })
  );

  // Bob cannot create a DM thread or send DM to Alice while blocked
  await assertFails(
    bob.collection("dm_threads").doc(sortedIds.join("_")).set({
      participantIds: sortedIds,
      participantNames: { [ALICE_UID]: "Alice", [BOB_UID]: "Bob" },
      participantTags: { [ALICE_UID]: "AlicePrime", [BOB_UID]: "Bob99" },
      lastMessage: "Hey!",
      lastSenderId: BOB_UID,
      createdAt: ts(),
      updatedAt: ts(),
    })
  );

  // Alice unblocks Bob -> Bob can send friend request + notification
  await assertSucceeds(
    alice.collection("users").doc(ALICE_UID).collection("blocked_users").doc(BOB_UID).delete()
  );
  await assertSucceeds(
    bob.collection("friendships").doc(sortedIds.join("_")).set({
      userIds: sortedIds,
      requesterId: BOB_UID,
      requesterName: "Bob",
      requesterTag: "Bob99",
      requesterAvatarId: "neon_rogue",
      recipientId: ALICE_UID,
      recipientName: "Alice",
      recipientTag: "AlicePrime",
      recipientAvatarId: "cyber_knight",
      status: "PENDING",
      createdAt: ts(),
      updatedAt: ts(),
    })
  );
  await assertSucceeds(
    bob.collection("users").doc(ALICE_UID).collection("notifications").doc("notif_fr_1").set({
      userId: ALICE_UID,
      senderId: BOB_UID,
      senderName: "Bob",
      type: "FRIEND_REQUEST",
      title: "New Friend Request",
      body: "Bob wants to connect on Nexus",
      isRead: false,
      createdAt: ts(),
    })
  );

  // Alice reads and marks notification read; Bob cannot read Alice's notifications
  await assertFails(bob.collection("users").doc(ALICE_UID).collection("notifications").get());
  await assertSucceeds(alice.collection("users").doc(ALICE_UID).collection("notifications").doc("notif_fr_1").update({ isRead: true }));
});

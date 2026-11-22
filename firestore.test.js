const {
  initializeTestEnvironment,
  assertFails,
  assertSucceeds,
} = require("@firebase/rules-unit-testing");
const { test, before, after, beforeEach } = require("node:test");
const fs = require("node:fs");

let testEnv;
const PROJECT_ID = process.env.GCP_PROJECT || "demo-no-project";
const ALICE_UID = "alice_123";
const BOB_UID = "bob_456";
const CHARLIE_UID = "charlie_789";

const [emulatorHost, emulatorPortStr] = (
  process.env.FIRESTORE_EMULATOR_HOST || "127.0.0.1:8085"
).split(":");
const emulatorPort = parseInt(emulatorPortStr, 10);

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
  if (testEnv) {
    await testEnv.cleanup();
  }
});

beforeEach(async () => {
  if (testEnv) {
    await testEnv.clearFirestore();
  }
});

test("Unauthenticated user cannot read or write any collection", async () => {
  const unauthDb = testEnv.unauthenticatedContext().firestore();
  const past = new Date(Date.now() - 1000);

  await assertFails(unauthDb.collection("users").doc(ALICE_UID).get());
  await assertFails(unauthDb.collection("friend_codes").doc("ALICE1").get());
  await assertFails(unauthDb.collection("circles").get());
  await assertFails(unauthDb.collection("circle_invites").get());
  await assertFails(unauthDb.collection("circle_posts").get());
  await assertFails(
    unauthDb.collection("circles").doc("BOOTH1").set({
      name: "Test",
      inviteCode: "BOOTH1",
      ownerId: ALICE_UID,
      memberUids: [ALICE_UID],
      createdAt: past,
      updatedAt: past,
    })
  );
});

test("Users and friend_codes isolate profiles and forbid directory listing", async () => {
  const aliceDb = testEnv.authenticatedContext(ALICE_UID).firestore();
  const bobDb = testEnv.authenticatedContext(BOB_UID).firestore();
  const past = new Date(Date.now() - 2000);

  await assertSucceeds(
    aliceDb.collection("users").doc(ALICE_UID).set({
      userId: ALICE_UID,
      displayName: "Alice",
      friendCode: "ALICE1",
      createdAt: past,
      updatedAt: past,
    })
  );

  await assertSucceeds(
    aliceDb.collection("friend_codes").doc("ALICE1").set({
      friendCode: "ALICE1",
      userId: ALICE_UID,
      displayName: "Alice",
      createdAt: past,
      updatedAt: past,
    })
  );

  // Alice can update her bio and uploaded avatarBase64 within bounds
  await assertSucceeds(
    aliceDb.collection("users").doc(ALICE_UID).update({
      bio: "Thích chụp ảnh lấy liền cùng hội bạn thân ✨",
      avatarBase64: "B".repeat(1200),
      updatedAt: new Date(Date.now() - 800),
    })
  );

  // Oversized bio (>160 chars) or oversized avatarBase64 (>250000 chars) must fail
  await assertFails(
    aliceDb.collection("users").doc(ALICE_UID).update({
      bio: "X".repeat(161),
      updatedAt: new Date(Date.now() - 700),
    })
  );
  await assertFails(
    aliceDb.collection("users").doc(ALICE_UID).update({
      avatarBase64: "B".repeat(250001),
      updatedAt: new Date(Date.now() - 600),
    })
  );

  // Bob can look up Alice's exact friend code, but CANNOT list/scrape friend_codes
  await assertSucceeds(bobDb.collection("friend_codes").doc("ALICE1").get());
  await assertFails(bobDb.collection("friend_codes").get());

  // Cross-user profile read/write & code hijack must fail
  await assertFails(bobDb.collection("users").doc(ALICE_UID).get());
  await assertFails(
    bobDb.collection("users").doc(ALICE_UID).update({
      bio: "Hacked bio",
      updatedAt: new Date(Date.now() - 500),
    })
  );
  await assertFails(
    bobDb.collection("friend_codes").doc("ALICE1").update({
      displayName: "Hijacked",
      updatedAt: new Date(Date.now() - 500),
    })
  );
});

test("Circles & circle_invites: member invites friend via code, friend accepts and joins", async () => {
  const aliceDb = testEnv.authenticatedContext(ALICE_UID).firestore();
  const bobDb = testEnv.authenticatedContext(BOB_UID).firestore();
  const charlieDb = testEnv.authenticatedContext(CHARLIE_UID).firestore();
  const past = new Date(Date.now() - 5000);

  await assertSucceeds(
    aliceDb.collection("circles").doc("CHILL88").set({
      name: "Hội bạn thân",
      inviteCode: "CHILL88",
      ownerId: ALICE_UID,
      memberUids: [ALICE_UID],
      memberNames: { [ALICE_UID]: "Alice" },
      createdAt: past,
      updatedAt: past,
    })
  );

  // Alice invites Bob to CHILL88
  await assertSucceeds(
    aliceDb.collection("circle_invites").doc("CHILL88_bob_456").set({
      circleId: "CHILL88",
      circleName: "Hội bạn thân",
      fromUserId: ALICE_UID,
      fromName: "Alice",
      toUserId: BOB_UID,
      toName: "Bob",
      status: "PENDING",
      createdAt: past,
      updatedAt: past,
    })
  );

  // Charlie cannot read Bob's invite or query unfiltered invites
  await assertFails(charlieDb.collection("circle_invites").doc("CHILL88_bob_456").get());
  await assertFails(charlieDb.collection("circle_invites").get());

  // Bob queries his incoming invites and accepts
  await assertSucceeds(
    bobDb.collection("circle_invites").where("toUserId", "==", BOB_UID).get()
  );
  await assertSucceeds(
    bobDb.collection("circle_invites").doc("CHILL88_bob_456").update({
      status: "ACCEPTED",
      updatedAt: new Date(Date.now() - 1000),
    })
  );

  // Terminal state locking: Bob cannot flip ACCEPTED back to PENDING
  await assertFails(
    bobDb.collection("circle_invites").doc("CHILL88_bob_456").update({
      status: "PENDING",
      updatedAt: new Date(Date.now() - 500),
    })
  );

  // Bob joins circle adding only self and display name
  await assertSucceeds(
    bobDb.collection("circles").doc("CHILL88").update({
      memberUids: [ALICE_UID, BOB_UID],
      memberNames: { [ALICE_UID]: "Alice", [BOB_UID]: "Bob" },
      updatedAt: new Date(Date.now() - 500),
    })
  );
});

test("CirclePosts (SINGLE_PHOTO + overlayText) & PostComments: members message replies on photos, outsiders denied", async () => {
  const aliceDb = testEnv.authenticatedContext(ALICE_UID).firestore();
  const bobDb = testEnv.authenticatedContext(BOB_UID).firestore();
  const charlieDb = testEnv.authenticatedContext(CHARLIE_UID).firestore();
  const created = new Date(Date.now() - 5000);
  const sampleImage = "A".repeat(120);

  await testEnv.withSecurityRulesDisabled(async (ctx) => {
    await ctx.firestore().collection("circles").doc("CHILL88").set({
      name: "Hội bạn thân",
      inviteCode: "CHILL88",
      ownerId: ALICE_UID,
      memberUids: [ALICE_UID, BOB_UID],
      createdAt: created,
      updatedAt: created,
    });
  });

  // Alice creates a SINGLE_PHOTO post with overlayText
  await assertSucceeds(
    aliceDb.collection("circle_posts").doc("post_1").set({
      circleId: "CHILL88",
      circleName: "Hội bạn thân",
      authorId: ALICE_UID,
      authorName: "Alice",
      caption: "Cuối tuần chill ♡",
      overlayText: "Saigon 2026",
      layout: "SINGLE_PHOTO",
      frameStyle: "PASTEL_PINK",
      filter: "KOREAN_PINK",
      dateLabel: "21/11/2026",
      imageBase64: sampleImage,
      memberUids: [ALICE_UID, BOB_UID],
      likedBy: [],
      createdAt: created,
      updatedAt: created,
    })
  );

  // Bob replies with a comment message on post_1
  await assertSucceeds(
    bobDb.collection("circle_posts").doc("post_1").collection("comments").doc("c_1").set({
      postId: "post_1",
      circleId: "CHILL88",
      authorId: BOB_UID,
      authorName: "Bob",
      text: "Ảnh này xinh quá!",
      memberUids: [ALICE_UID, BOB_UID],
      createdAt: new Date(Date.now() - 2000),
      updatedAt: new Date(Date.now() - 2000),
    })
  );

  // Bob queries comments using memberUids filter; unfiltered fails
  await assertSucceeds(
    bobDb
      .collection("circle_posts")
      .doc("post_1")
      .collection("comments")
      .where("memberUids", "array-contains", BOB_UID)
      .get()
  );
  await assertFails(
    bobDb.collection("circle_posts").doc("post_1").collection("comments").get()
  );

  // Charlie (non-member) cannot read or post comments
  await assertFails(
    charlieDb.collection("circle_posts").doc("post_1").collection("comments").doc("c_1").get()
  );
  await assertFails(
    charlieDb.collection("circle_posts").doc("post_1").collection("comments").doc("c_2").set({
      postId: "post_1",
      circleId: "CHILL88",
      authorId: CHARLIE_UID,
      authorName: "Charlie",
      text: "Intruder",
      memberUids: [ALICE_UID, BOB_UID, CHARLIE_UID],
      createdAt: new Date(Date.now() - 1000),
      updatedAt: new Date(Date.now() - 1000),
    })
  );

  // Bob can delete his own comment; Alice (post author) can delete post
  await assertSucceeds(
    bobDb.collection("circle_posts").doc("post_1").collection("comments").doc("c_1").delete()
  );
  await assertSucceeds(aliceDb.collection("circle_posts").doc("post_1").delete());
});

test("CirclePosts accept expanded frame styles", async () => {
  const aliceDb = testEnv.authenticatedContext(ALICE_UID).firestore();
  const created = new Date(Date.now() - 3000);
  const sampleImage = "A".repeat(120);

  await testEnv.withSecurityRulesDisabled(async (ctx) => {
    await ctx.firestore().collection("circles").doc("CHILL88").set({
      name: "Hội bạn thân",
      inviteCode: "CHILL88",
      ownerId: ALICE_UID,
      memberUids: [ALICE_UID, BOB_UID],
      createdAt: created,
      updatedAt: created,
    });
  });

  await assertSucceeds(
    aliceDb.collection("circle_posts").doc("post_yellow").set({
      circleId: "CHILL88",
      circleName: "Hội bạn thân",
      authorId: ALICE_UID,
      authorName: "Alice",
      caption: "Khung vàng rực rỡ",
      layout: "PHOTO_STRIP",
      frameStyle: "SUN_YELLOW",
      filter: "ORIGINAL",
      dateLabel: "21/11/2026",
      imageBase64: sampleImage,
      memberUids: [ALICE_UID, BOB_UID],
      likedBy: [],
      createdAt: created,
      updatedAt: created,
    })
  );

  await assertSucceeds(
    aliceDb.collection("circle_posts").doc("post_film").set({
      circleId: "CHILL88",
      circleName: "Hội bạn thân",
      authorId: ALICE_UID,
      authorName: "Alice",
      caption: "Cuộn phim 35mm",
      layout: "GRID_2X2",
      frameStyle: "FILM_STRIP",
      filter: "VINTAGE_FILM",
      dateLabel: "21/11/2026",
      imageBase64: sampleImage,
      memberUids: [ALICE_UID, BOB_UID],
      likedBy: [],
      createdAt: created,
      updatedAt: created,
    })
  );
});

test("DirectChats and DirectMessages isolate 1-on-1 conversations between friends", async () => {
  const aliceDb = testEnv.authenticatedContext(ALICE_UID).firestore();
  const bobDb = testEnv.authenticatedContext(BOB_UID).firestore();
  const charlieDb = testEnv.authenticatedContext(CHARLIE_UID).firestore();
  const chatId = `${ALICE_UID}_${BOB_UID}`;
  const created = new Date(Date.now() - 4000);

  await assertSucceeds(
    aliceDb.collection("direct_chats").doc(chatId).set({
      participantUids: [ALICE_UID, BOB_UID],
      participantNames: { [ALICE_UID]: "Alice", [BOB_UID]: "Bob" },
      lastMessageText: "",
      createdAt: created,
      updatedAt: created,
    })
  );

  // Alice and Bob can send messages; Charlie cannot read or write
  await assertSucceeds(
    aliceDb.collection("direct_chats").doc(chatId).collection("messages").doc("m_1").set({
      chatId,
      senderId: ALICE_UID,
      senderName: "Alice",
      text: "Tối nay đi chụp photobooth nha!",
      participantUids: [ALICE_UID, BOB_UID],
      createdAt: new Date(Date.now() - 2000),
      updatedAt: new Date(Date.now() - 2000),
    })
  );

  await assertSucceeds(
    aliceDb.collection("direct_chats").doc(chatId).update({
      lastMessageText: "Tối nay đi chụp photobooth nha!",
      lastMessageSenderId: ALICE_UID,
      lastMessageAt: new Date(Date.now() - 2000),
      updatedAt: new Date(Date.now() - 2000),
    })
  );

  // Bob queries his direct chats & messages using array-contains
  await assertSucceeds(
    bobDb.collection("direct_chats").where("participantUids", "array-contains", BOB_UID).get()
  );
  await assertSucceeds(
    bobDb.collection("direct_chats").doc(chatId).collection("messages")
      .where("participantUids", "array-contains", BOB_UID).get()
  );

  // Unfiltered queries & outsider access are denied
  await assertFails(bobDb.collection("direct_chats").get());
  await assertFails(charlieDb.collection("direct_chats").doc(chatId).get());
  await assertFails(
    charlieDb.collection("direct_chats").doc(chatId).collection("messages").doc("m_1").get()
  );
  await assertFails(
    charlieDb.collection("direct_chats").doc(chatId).collection("messages").doc("m_2").set({
      chatId,
      senderId: CHARLIE_UID,
      senderName: "Charlie",
      text: "Intruder",
      participantUids: [ALICE_UID, BOB_UID],
      createdAt: new Date(Date.now() - 1000),
      updatedAt: new Date(Date.now() - 1000),
    })
  );

  // Alice can delete her own message; Bob cannot delete Alice's message
  await assertFails(
    bobDb.collection("direct_chats").doc(chatId).collection("messages").doc("m_1").delete()
  );
  await assertSucceeds(
    aliceDb.collection("direct_chats").doc(chatId).collection("messages").doc("m_1").delete()
  );
});

test("FriendRequests, Friendships, and shared FRIENDS_ALL posts allow mutual friends to view photos and isolate strangers", async () => {
  const aliceDb = testEnv.authenticatedContext(ALICE_UID).firestore();
  const bobDb = testEnv.authenticatedContext(BOB_UID).firestore();
  const charlieDb = testEnv.authenticatedContext(CHARLIE_UID).firestore();
  const created = new Date(Date.now() - 4000);
  const pairId = `${ALICE_UID}_${BOB_UID}`;
  const reqId = `${ALICE_UID}_${BOB_UID}`;

  // Alice sends friend request to Bob
  await assertSucceeds(
    aliceDb.collection("friend_requests").doc(reqId).set({
      fromUserId: ALICE_UID,
      fromName: "Alice",
      toUserId: BOB_UID,
      toName: "Bob",
      status: "PENDING",
      createdAt: created,
      updatedAt: created,
    })
  );

  // Charlie cannot read Alice->Bob friend request
  await assertFails(charlieDb.collection("friend_requests").doc(reqId).get());

  // Bob accepts friend request and creates mutual friendship
  await assertSucceeds(
    bobDb.collection("friend_requests").doc(reqId).update({
      status: "ACCEPTED",
      updatedAt: new Date(Date.now() - 2000),
    })
  );
  await assertSucceeds(
    bobDb.collection("friendships").doc(pairId).set({
      participantUids: [ALICE_UID, BOB_UID],
      participantNames: { [ALICE_UID]: "Alice", [BOB_UID]: "Bob" },
      createdAt: new Date(Date.now() - 2000),
      updatedAt: new Date(Date.now() - 2000),
    })
  );

  // Alice publishes a shared Friends photo (FRIENDS_ALL) without a separate circle doc
  await assertSucceeds(
    aliceDb.collection("circle_posts").doc("friends_post_1").set({
      circleId: "FRIENDS_ALL",
      circleName: "Friends",
      authorId: ALICE_UID,
      authorName: "Alice",
      caption: "Đi cafe nha",
      layout: "SINGLE_PHOTO",
      frameStyle: "SUN_YELLOW",
      filter: "ORIGINAL",
      dateLabel: "22/11/2026",
      imageBase64: "A".repeat(120),
      memberUids: [ALICE_UID, BOB_UID],
      likedBy: [],
      createdAt: new Date(Date.now() - 1000),
      updatedAt: new Date(Date.now() - 1000),
    })
  );

  // Bob can read and like; Charlie (non-friend) is denied
  await assertSucceeds(bobDb.collection("circle_posts").doc("friends_post_1").get());
  await assertFails(charlieDb.collection("circle_posts").doc("friends_post_1").get());

  // Alice can sync memberUids on her shared FRIENDS_ALL post; Bob cannot change memberUids
  await assertSucceeds(
    aliceDb.collection("circle_posts").doc("friends_post_1").update({
      memberUids: [ALICE_UID, BOB_UID, CHARLIE_UID],
      updatedAt: new Date(Date.now() - 500),
    })
  );
  await assertFails(
    bobDb.collection("circle_posts").doc("friends_post_1").update({
      memberUids: [ALICE_UID],
      updatedAt: new Date(Date.now() - 400),
    })
  );
});

test("CirclePosts emoji reactions and DirectMessages quoted photo replies enforce per-user isolation", async () => {
  const aliceDb = testEnv.authenticatedContext(ALICE_UID).firestore();
  const bobDb = testEnv.authenticatedContext(BOB_UID).firestore();
  const charlieDb = testEnv.authenticatedContext(CHARLIE_UID).firestore();
  const created = new Date(Date.now() - 4000);
  const chatId = `${ALICE_UID}_${BOB_UID}`;

  await aliceDb.collection("circle_posts").doc("react_post_1").set({
    circleId: "FRIENDS_ALL",
    circleName: "Friends",
    authorId: ALICE_UID,
    authorName: "Alice",
    authorAvatarBase64: "C".repeat(200),
    caption: "Bất ngờ cho hai bạn ấy",
    layout: "SINGLE_PHOTO",
    frameStyle: "SUN_YELLOW",
    filter: "ORIGINAL",
    dateLabel: "22/11/2026",
    imageBase64: "A".repeat(120),
    memberUids: [ALICE_UID, BOB_UID],
    likedBy: [],
    reactions: {},
    reactorNames: {},
    createdAt: created,
    updatedAt: created,
  });

  // Bob can react with an emoji on Alice's post using only his own UID key
  await assertSucceeds(
    bobDb.collection("circle_posts").doc("react_post_1").update({
      [`reactions.${BOB_UID}`]: "😍",
      [`reactorNames.${BOB_UID}`]: "Bob",
      updatedAt: new Date(Date.now() - 2000),
    })
  );

  // Alice can also react without clobbering Bob's reaction
  await assertSucceeds(
    aliceDb.collection("circle_posts").doc("react_post_1").update({
      [`reactions.${ALICE_UID}`]: "🔥",
      [`reactorNames.${ALICE_UID}`]: "Alice",
      updatedAt: new Date(Date.now() - 1500),
    })
  );

  // Bob CANNOT overwrite or spoof Alice's reaction key
  await assertFails(
    bobDb.collection("circle_posts").doc("react_post_1").update({
      [`reactions.${ALICE_UID}`]: "😂",
      [`reactorNames.${ALICE_UID}`]: "Fake",
      updatedAt: new Date(Date.now() - 1000),
    })
  );

  // Charlie (non-member) cannot react
  await assertFails(
    charlieDb.collection("circle_posts").doc("react_post_1").update({
      [`reactions.${CHARLIE_UID}`]: "❤️",
      [`reactorNames.${CHARLIE_UID}`]: "Charlie",
      updatedAt: new Date(Date.now() - 900),
    })
  );

  // Quoted photo reply inside 1-on-1 DirectMessages
  await assertSucceeds(
    bobDb.collection("direct_chats").doc(chatId).set({
      participantUids: [ALICE_UID, BOB_UID],
      participantNames: { [ALICE_UID]: "Alice", [BOB_UID]: "Bob" },
      participantAvatars: { [ALICE_UID]: "C".repeat(200) },
      lastMessageText: "",
      createdAt: created,
      updatedAt: created,
    })
  );

  await assertSucceeds(
    bobDb.collection("direct_chats").doc(chatId).collection("messages").doc("msg_quote_1").set({
      chatId,
      senderId: BOB_UID,
      senderName: "Bob",
      text: "Lớp có 2 đực thôi à :)))",
      participantUids: [ALICE_UID, BOB_UID],
      replyPostId: "react_post_1",
      replyPostImageBase64: "D".repeat(300),
      replyPostAuthorId: ALICE_UID,
      replyPostAuthorName: "Alice",
      replyPostAuthorAvatarBase64: "C".repeat(200),
      replyPostCaption: "Bất ngờ cho hai bạn ấy",
      replyPostTimeLabel: "22/11",
      createdAt: new Date(Date.now() - 500),
      updatedAt: new Date(Date.now() - 500),
    })
  );
});



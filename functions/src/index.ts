import {onSchedule} from "firebase-functions/v2/scheduler";
import {onRequest} from "firebase-functions/v2/https";
import {onDocumentUpdated, onDocumentDeleted, onDocumentCreated} from "firebase-functions/v2/firestore";
import * as admin from "firebase-admin";
import * as functionsV1 from "firebase-functions/v1";

admin.initializeApp();

/** Test function: Sends a notification to all users every 24 hours. */
export const testNotificationPing = onSchedule("every 24 hours", async () => {
  await sendTestPings();
});

/** Manual HTTP trigger to test notifications immediately without waiting for the schedule. */
export const manualTestPing = onRequest(async (req, res) => {
  await sendTestPings();
  res.status(200).send("Test pings sent successfully.");
});

/**
 * Sends test notifications to all users and a topic.
 * @return {Promise<void>}
 */
async function sendTestPings() {
  const db = admin.firestore();
  const messaging = admin.messaging();
  const usersSnap = await db.collection("users").get();
  const now = admin.firestore.Timestamp.now();
  const title = "Test Ping";
  const body = `Periodic test notification at ${new Date().toLocaleTimeString()}`;
  const promises: Promise<unknown>[] = [];

  for (const userDoc of usersSnap.docs) {
    const uid = userDoc.id;
    promises.push(
      db.collection("users").doc(uid).collection("notifications").add({
        title: title,
        body: body,
        timestamp: now,
        type: "test_ping",
      })
    );
  }

  const message = {
    data: {title: title, body: body, type: "test_ping"},
    topic: "test_notifications",
  };
  promises.push(messaging.send(message));
  await Promise.all(promises);
  console.log(`Sent test ping to ${usersSnap.size} users and topic.`);
}

/** Transitions circles from 'open' to 'closed' once closeAt timestamp is reached. */
export const closeExpiredCircles = onSchedule("every 15 minutes", async () => {
  const db = admin.firestore();
  const now = admin.firestore.Timestamp.now();

  // Query only by status to avoid composite index requirement.
  const circlesSnap = await db
    .collection("circles")
    .where("status", "==", "open")
    .get();

  if (circlesSnap.empty) return;

  const batch = db.batch();
  let count = 0;
  for (const doc of circlesSnap.docs) {
    const closeAt = doc.data().closeAt as admin.firestore.Timestamp | undefined;
    if (closeAt && closeAt.toMillis() <= now.toMillis()) {
      batch.update(doc.ref, {status: "closed"});
      const inviteCode = doc.data().inviteCode;
      if (inviteCode) {
        batch.update(db.collection("circle_codes").doc(inviteCode), {status: "closed"});
      }
      count++;
      if (count >= 400) break;
    }
  }

  if (count > 0) {
    await batch.commit();
    console.log(`Closed ${count} expired circles.`);
  }
});

/** Periodically cleans up circles that have passed their deleteAt timestamp. */
export const cleanupExpiredCircles = onSchedule("every 60 minutes", async () => {
  await performCleanup();
});

/** Manual HTTP trigger to force run cleanup and close logic for testing. */
export const manualCleanupPing = onRequest(async (req, res) => {
  await performCleanup();
  res.status(200).send("Manual cleanup executed. Check Firebase Function logs.");
});

/**
 * Performs the actual deletion logic for expired circles.
 * @return {Promise<void>}
 */
async function performCleanup() {
  const db = admin.firestore();
  const now = admin.firestore.Timestamp.now();
  const circlesSnap = await db
    .collection("circles")
    .where("deleteAt", "<=", now)
    .limit(25)
    .get();

  if (circlesSnap.empty) return;

  const batch = db.batch();
  for (const circleDoc of circlesSnap.docs) {
    batch.delete(circleDoc.ref);
  }
  await batch.commit();
  console.log(`Deleted ${circlesSnap.size} expired circles.`);
}

/** Periodically cleans up "dead" friend requests and circle invites. */
export const cleanupDeadLinks = onSchedule("every 24 hours", async () => {
  await performLinksCleanup();
});

/** Manual HTTP trigger to force run cleanup for friend requests and invites. */
export const manualLinksCleanupPing = onRequest(async (req, res) => {
  await performLinksCleanup();
  res.status(200).send("Links cleanup executed. Check Firebase Function logs.");
});

/**
 * Deletes friend requests and circle invites that are no longer pending.
 * @return {Promise<void>}
 */
async function performLinksCleanup() {
  const db = admin.firestore();
  // Cleanup circle invites that are not pending
  await deleteByQuery(db.collection("circle_invites").where("status", "!=", "pending"));
  // Cleanup friend requests that are not pending (including accepted ones)
  await deleteByQuery(db.collection("friend_requests").where("status", "!=", "pending"));
  console.log("Dead links cleanup completed.");
}

/** Triggered when a friend request is created to send notifications. */
export const onFriendRequestCreated = onDocumentCreated("friend_requests/{requestId}", async (event) => {
  const data = event.data?.data();
  if (!data) return;
  const receiverUid = data.receiverUid;
  const senderUid = data.senderUid;
  const db = admin.firestore();
  const senderSnap = await db.doc(`user_public/${senderUid}`).get();
  const senderName = senderSnap.data()?.username || "Someone";
  const title = "New Friend Request";
  const body = `${senderName} sent you a friend request.`;

  await db.collection(`users/${receiverUid}/notifications`).add({
    title: title, body: body, timestamp: admin.firestore.FieldValue.serverTimestamp(),
    type: "friend_request", senderUid: senderUid,
  });

  const userSnap = await db.doc(`users/${receiverUid}`).get();
  const token = userSnap.data()?.fcmToken;
  if (token) {
    await admin.messaging().send({token: token, data: {title: title, body: body, type: "friend_request"}});
  }
});

/** Triggered when a friend request is accepted to update friends lists. */
export const onFriendRequestAccepted = onDocumentUpdated("friend_requests/{requestId}", async (event) => {
  const before = event.data?.before.data();
  const after = event.data?.after.data();
  if (!before || !after) return;
  if (before.status === after.status) return;
  if (after.status !== "accepted") return;

  const senderUid = after.senderUid;
  const receiverUid = after.receiverUid;
  const db = admin.firestore();
  const batch = db.batch();

  batch.update(db.doc(`users/${senderUid}`), {friends: admin.firestore.FieldValue.arrayUnion(receiverUid)});
  batch.update(db.doc(`users/${receiverUid}`), {friends: admin.firestore.FieldValue.arrayUnion(senderUid)});

  const receiverSnap = await db.doc(`user_public/${receiverUid}`).get();
  const receiverName = receiverSnap.data()?.username || "Someone";
  const title = "Friend Request Accepted";
  const body = `${receiverName} accepted your friend request.`;

  const notifRef = db.collection(`users/${senderUid}/notifications`).doc();
  batch.set(notifRef, {
    title: title, body: body, timestamp: admin.firestore.FieldValue.serverTimestamp(),
    type: "friend_request_accepted", senderUid: receiverUid,
  });

  await batch.commit();

  const senderDoc = await db.doc(`users/${senderUid}`).get();
  const token = senderDoc.data()?.fcmToken;
  if (token) {
    await admin.messaging().send({token: token, data: {title: title, body: body, type: "friend_request_accepted"}});
  }
});

/** Triggered when a user removes a friend from their list. */
export const onFriendRemoved = onDocumentUpdated("users/{uid}", async (event) => {
  const before = event.data?.before.data();
  const after = event.data?.after.data();
  if (!before || !after) return;

  const beforeFriends = (before.friends || []) as string[];
  const afterFriends = (after.friends || []) as string[];

  // Find users who were removed
  const removedUids = beforeFriends.filter((f) => !afterFriends.includes(f));
  if (removedUids.length === 0) return;

  const initiatorUid = event.params.uid;
  const db = admin.firestore();

  for (const targetUid of removedUids) {
    console.log(`Unfriending process: ${initiatorUid} removed ${targetUid}`);
    const batch = db.batch();

    // 1) Remove initiator from target's friends list (bilateral removal)
    batch.update(db.doc(`users/${targetUid}`), {
      friends: admin.firestore.FieldValue.arrayRemove(initiatorUid),
    });

    // 2) Cleanup any associated friend requests to allow re-adding later.
    // We use safe single-field queries to avoid composite index requirements.
    const [snap1, snap2] = await Promise.all([
      db.collection("friend_requests").where("senderUid", "==", initiatorUid).get(),
      db.collection("friend_requests").where("senderUid", "==", targetUid).get(),
    ]);

    snap1.docs.forEach((doc) => {
      if (doc.data().receiverUid === targetUid) batch.delete(doc.ref);
    });
    snap2.docs.forEach((doc) => {
      if (doc.data().receiverUid === initiatorUid) batch.delete(doc.ref);
    });

    await batch.commit();
    console.log(`Bilateral unfriend complete for ${initiatorUid} and ${targetUid}`);
  }
});

/** Triggered when a circle invite is created to notify the invitee. */
export const onCircleInviteCreated = onDocumentCreated("circle_invites/{inviteId}", async (event) => {
  const data = event.data?.data();
  if (!data) return;
  const inviteeUid = data.inviteeUid;
  const db = admin.firestore();
  const title = "New Circle Invite";
  const body = `${data.inviterName} invited you to join "${data.circleName}".`;

  await db.collection(`users/${inviteeUid}/notifications`).add({
    title: title, body: body, timestamp: admin.firestore.FieldValue.serverTimestamp(),
    type: "circle_invite", senderUid: data.inviterUid, circleId: data.circleId,
  });

  const userSnap = await db.doc(`users/${inviteeUid}`).get();
  const token = userSnap.data()?.fcmToken;
  if (token) {
    await admin.messaging().send({token: token, data: {title: title, body: body, type: "circle_invite"}});
  }
});

/** Triggered when a photo is added to a circle to notify members. */
export const onPhotoAdded = onDocumentCreated("circles/{circleId}/photos/{photoId}", async (event) => {
  const data = event.data?.data();
  if (!data) return;
  const circleId = event.params.circleId;
  const db = admin.firestore();
  const circleSnap = await db.doc(`circles/${circleId}`).get();
  const circleData = circleSnap.data();
  if (!circleData) return;

  const uploaderUid = data.uploaderUid;
  const members = circleData.members as string[];
  const uploaderSnap = await db.doc(`user_public/${uploaderUid}`).get();
  const uploaderName = uploaderSnap.data()?.username || "Someone";
  const title = "New Photo";
  const body = `${uploaderName} posted a new photo in "${circleData.name}".`;

  const batch = db.batch();
  for (const memberUid of members) {
    if (memberUid === uploaderUid) continue;
    const notifRef = db.collection(`users/${memberUid}/notifications`).doc();
    batch.set(notifRef, {
      title: title, body: body, timestamp: admin.firestore.FieldValue.serverTimestamp(),
      type: "new_photo", senderUid: uploaderUid, circleId: circleId,
    });
  }
  await batch.commit();

  for (const memberUid of members) {
    if (memberUid === uploaderUid) continue;
    const mDoc = await db.doc(`users/${memberUid}`).get();
    const token = mDoc.data()?.fcmToken;
    if (token) {
      await admin.messaging().send({token: token, data: {title: title, body: body, type: "new_photo"}});
    }
  }
});

/** Firestore trigger: when a circle doc is deleted, clean up its Storage files and related Firestore docs. */
export const onCircleDeleted = onDocumentDeleted("circles/{circleId}", async (event) => {
  const circleId = event.params.circleId;
  const db = admin.firestore();
  const bucket = admin.storage().bucket();
  const [files] = await bucket.getFiles({prefix: `circles/${circleId}/`});
  await Promise.all(files.map((f) => f.delete().catch(() => null)));
  await deleteCollection(db.collection("circles").doc(circleId).collection("photos"));
  await deleteByQuery(db.collection("circle_codes").where("circleId", "==", circleId));
  await deleteByQuery(db.collection("circle_invites").where("circleId", "==", circleId));
  await deleteByQuery(db.collection("reports").where("circleId", "==", circleId));
});

/**
 * 1st-gen Auth trigger: when a user account is deleted, clean up owned circles and user data.
 * Note: 2nd-gen Cloud Functions do not support Auth create/delete triggers.
 */
export const onAuthUserDeleted = functionsV1.auth.user().onDelete(async (user) => {
  const deletedUid = user.uid;

  const db = admin.firestore();
  const bucket = admin.storage().bucket();

  // 1) Delete circles owned by this user (your onCircleDeleted will handle deep cleanup)
  const ownedCirclesSnap = await db
    .collection("circles")
    .where("ownerUid", "==", deletedUid)
    .get();

  await Promise.all(
    ownedCirclesSnap.docs.map((d) => d.ref.delete().catch(() => null))
  );

  // 2) Remove user from circles where they are a member but NOT the owner
  const memberCirclesSnap = await db
    .collection("circles")
    .where("members", "array-contains", deletedUid)
    .get();

  await Promise.all(
    memberCirclesSnap.docs.map(async (d) => {
      const data = d.data();
      if (data.ownerUid === deletedUid) return; // already deleted above
      await d.ref.update({
        members: admin.firestore.FieldValue.arrayRemove(deletedUid),
      }).catch(() => null);
    })
  );

  // 3) Remove deletedUid from other users' friends lists (prevents ghost friends)
  const friendsSnap = await db
    .collection("users")
    .where("friends", "array-contains", deletedUid)
    .get();

  await Promise.all(
    friendsSnap.docs.map((d) =>
      d.ref.update({
        friends: admin.firestore.FieldValue.arrayRemove(deletedUid),
      }).catch(() => null)
    )
  );

  // 4) Delete user docs
  await db.collection("users").doc(deletedUid).delete().catch(() => null);
  await db.collection("user_public").doc(deletedUid).delete().catch(() => null);

  // 5) Delete profile pictures (support both profile_pictures/{uid}/... and profile_pictures/{uid}.jpg)
  const [folderFiles] = await bucket.getFiles({prefix: `profile_pictures/${deletedUid}/`});
  await Promise.all(folderFiles.map((f) => f.delete().catch(() => null)));

  await bucket.file(`profile_pictures/${deletedUid}.jpg`).delete().catch(() => null);
});

// -------- helpers --------

/**
 * Deletes documents returned by a query in batches.
 * @param {FirebaseFirestore.Query} query The query to delete.
 * @return {Promise<void>}
 */
async function deleteByQuery(query: FirebaseFirestore.Query) {
  const snap = await query.get();
  if (snap.empty) return;
  const db = admin.firestore();
  let batch = db.batch();
  let opCount = 0;
  for (const doc of snap.docs) {
    batch.delete(doc.ref);
    opCount++;
    if (opCount >= 450) {
      await batch.commit();
      batch = db.batch();
      opCount = 0;
    }
  }
  if (opCount > 0) {
    await batch.commit();
  }
}

/**
 * Deletes an entire collection in batches.
 * @param {FirebaseFirestore.CollectionReference} col The collection reference to delete.
 * @param {number} batchSize The number of documents to delete in each batch.
 * @return {Promise<void>}
 */
async function deleteCollection(col: FirebaseFirestore.CollectionReference, batchSize = 450) {
  const db = admin.firestore();
  let snap = await col.limit(batchSize).get();
  while (!snap.empty) {
    const batch = db.batch();
    snap.docs.forEach((d) => batch.delete(d.ref));
    await batch.commit();
    snap = await col.limit(batchSize).get();
  }
}

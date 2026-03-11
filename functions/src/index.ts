import {onSchedule} from "firebase-functions/v2/scheduler";
import {onRequest} from "firebase-functions/v2/https";
import {onDocumentUpdated, onDocumentDeleted, onDocumentCreated} from "firebase-functions/v2/firestore";
import * as admin from "firebase-admin";

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
export const closeExpiredCircles = onSchedule("every 30 minutes", async () => {
  const db = admin.firestore();
  const now = admin.firestore.Timestamp.now();

  // We query ONLY by status to avoid missing composite indexes.
  const circlesSnap = await db
    .collection("circles")
    .where("status", "==", "open")
    .get();

  if (circlesSnap.empty) return;

  const batch = db.batch();
  let count = 0;
  for (const doc of circlesSnap.docs) {
    const closeAt = doc.data().closeAt as admin.firestore.Timestamp | undefined;

    // Check in memory if the close date has passed
    if (closeAt && closeAt.toMillis() <= now.toMillis()) {
      batch.update(doc.ref, {status: "closed"});

      const inviteCode = doc.data().inviteCode;
      if (inviteCode) {
        batch.update(db.collection("circle_codes").doc(inviteCode), {status: "closed"});
      }

      count++;
      if (count >= 400) break; // Limit batch size
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

  // Find circles that should be deleted.
  // Query only by deleteAt to avoid needing a composite index.
  const circlesSnap = await db
    .collection("circles")
    .where("deleteAt", "<=", now)
    .limit(25)
    .get();

  if (circlesSnap.empty) return;

  const batch = db.batch();
  for (const circleDoc of circlesSnap.docs) {
    // Simply delete the circle document.
    // The 'onCircleDeleted' trigger below will handle cleaning up storage, photos, circle_codes, etc.
    batch.delete(circleDoc.ref);
  }

  await batch.commit();
  console.log(`Deleted ${circlesSnap.size} expired circles.`);
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

/** Triggered when a friend request is deleted to clean up friends lists if necessary. */
export const onFriendRequestDeleted = onDocumentDeleted("friend_requests/{requestId}", async (event) => {
  const data = event.data?.data();
  if (!data) return;
  if (data.status === "accepted") {
    const senderUid = data.senderUid;
    const receiverUid = data.receiverUid;
    const db = admin.firestore();
    const batch = db.batch();
    batch.update(db.doc(`users/${senderUid}`), {friends: admin.firestore.FieldValue.arrayRemove(receiverUid)});
    batch.update(db.doc(`users/${receiverUid}`), {friends: admin.firestore.FieldValue.arrayRemove(senderUid)});
    await batch.commit();
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

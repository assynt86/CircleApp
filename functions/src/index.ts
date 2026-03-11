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
 * Sends a test notification log to all users and a push message to a test topic.
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
    // 1) Add to Notification Log in Firestore
    promises.push(
      db.collection("users").doc(uid).collection("notifications").add({
        title: title,
        body: body,
        timestamp: now,
        type: "test_ping",
      })
    );
  }

  // 2) Send FCM Push Notification to the "test_notifications" topic
  const message = {
    data: {
      title: title,
      body: body,
      type: "test_ping",
    },
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

  // Find circles that are still 'open' but have passed their closeAt time
  const circlesSnap = await db
    .collection("circles")
    .where("status", "==", "open")
    .where("closeAt", "<=", now)
    .limit(50)
    .get();

  if (circlesSnap.empty) return;

  const batch = db.batch();
  for (const doc of circlesSnap.docs) {
    // Update main circle doc
    batch.update(doc.ref, {status: "closed"});

    // Update corresponding circle_code doc if it exists
    const inviteCode = doc.data().inviteCode;
    if (inviteCode) {
      batch.update(db.collection("circle_codes").doc(inviteCode), {status: "closed"});
    }
  }

  await batch.commit();
  console.log(`Closed ${circlesSnap.size} expired circles.`);
});

export const cleanupExpiredCircles = onSchedule("every 60 minutes", async () => {
  const db = admin.firestore();
  const bucket = admin.storage().bucket();

  const now = admin.firestore.Timestamp.now();

  // Find circles that should be deleted and not already cleaned
  const circlesSnap = await db
    .collection("circles")
    .where("deleteAt", "<=", now)
    .where("cleanedUp", "==", false)
    .limit(25)
    .get();

  if (circlesSnap.empty) return;

  for (const circleDoc of circlesSnap.docs) {
    const circleId = circleDoc.id;
    const inviteCode = circleDoc.data().inviteCode;

    // 1) Delete all files in Storage under circles/{circleId}/
    const [files] = await bucket.getFiles({prefix: `circles/${circleId}/`});
    await Promise.all(files.map((f) => f.delete().catch(() => null)));

    // 2) Delete photo metadata docs
    const photosSnap = await db
      .collection("circles")
      .doc(circleId)
      .collection("photos")
      .get();

    const batch = db.batch();
    photosSnap.docs.forEach((d) => batch.delete(d.ref));

    // 3) Mark circle cleanedUp and update status to expired
    batch.update(circleDoc.ref, {
      cleanedUp: true,
      status: "expired",
    });

    // 4) Update circle_codes to expired
    if (inviteCode) {
      batch.update(db.collection("circle_codes").doc(inviteCode), {
        status: "expired",
      });
    }

    await batch.commit();
  }
});

export const onFriendRequestCreated = onDocumentCreated(
  "friend_requests/{requestId}",
  async (event) => {
    const data = event.data?.data();
    if (!data) return;

    const receiverUid = data.receiverUid;
    const senderUid = data.senderUid;
    console.log(`Friend request created: ${senderUid} -> ${receiverUid}`);

    const db = admin.firestore();
    const senderSnap = await db.doc(`user_public/${senderUid}`).get();
    const senderName = senderSnap.data()?.username || "Someone";

    const title = "New Friend Request";
    const body = `${senderName} sent you a friend request.`;

    // 1) Log to Firestore
    await db.collection(`users/${receiverUid}/notifications`).add({
      title: title,
      body: body,
      timestamp: admin.firestore.FieldValue.serverTimestamp(),
      type: "friend_request",
      senderUid: senderUid,
    });

    // 2) Send Push Notification (if token exists)
    const userSnap = await db.doc(`users/${receiverUid}`).get();
    const token = userSnap.data()?.fcmToken;
    if (token) {
      console.log(`Sending push notification to ${receiverUid}`);
      await admin.messaging().send({
        token: token,
        data: {title: title, body: body, type: "friend_request"},
      });
    } else {
      console.log(`No FCM token found for ${receiverUid}`);
    }
  }
);

export const onFriendRequestAccepted = onDocumentUpdated(
  "friend_requests/{requestId}",
  async (event) => {
    const before = event.data?.before.data();
    const after = event.data?.after.data();
    if (!before || !after) return;

    // Only run when status changes to "accepted"
    if (before.status === after.status) return;
    if (after.status !== "accepted") return;

    const senderUid = after.senderUid;
    const receiverUid = after.receiverUid;
    console.log(`Friend request accepted: ${senderUid} <-> ${receiverUid}`);

    const db = admin.firestore();
    const batch = db.batch();

    batch.update(db.doc(`users/${senderUid}`), {
      friends: admin.firestore.FieldValue.arrayUnion(receiverUid),
    });
    batch.update(db.doc(`users/${receiverUid}`), {
      friends: admin.firestore.FieldValue.arrayUnion(senderUid),
    });

    // Notify sender that request was accepted
    const receiverSnap = await db.doc(`user_public/${receiverUid}`).get();
    const receiverName = receiverSnap.data()?.username || "Someone";

    const title = "Friend Request Accepted";
    const body = `${receiverName} accepted your friend request.`;

    const notifRef = db.collection(`users/${senderUid}/notifications`).doc();
    batch.set(notifRef, {
      title: title,
      body: body,
      timestamp: admin.firestore.FieldValue.serverTimestamp(),
      type: "friend_request_accepted",
      senderUid: receiverUid,
    });

    await batch.commit();
    console.log("Successfully updated friends lists and created notification document.");

    // Send Push
    const senderDoc = await db.doc(`users/${senderUid}`).get();
    const token = senderDoc.data()?.fcmToken;
    if (token) {
      console.log(`Sending acceptance push notification to ${senderUid}`);
      await admin.messaging().send({
        token: token,
        data: {title: title, body: body, type: "friend_request_accepted"},
      });
    } else {
      console.log(`No FCM token found for ${senderUid}`);
    }
  }
);

export const onCircleInviteCreated = onDocumentCreated(
  "circle_invites/{inviteId}",
  async (event) => {
    const data = event.data?.data();
    if (!data) return;

    const inviteeUid = data.inviteeUid;
    const inviterUid = data.inviterUid;
    const inviterName = data.inviterName;
    const circleName = data.circleName;
    const circleId = data.circleId;

    const db = admin.firestore();
    const title = "New Circle Invite";
    const body = `${inviterName} invited you to join "${circleName}".`;

    await db.collection(`users/${inviteeUid}/notifications`).add({
      title: title,
      body: body,
      timestamp: admin.firestore.FieldValue.serverTimestamp(),
      type: "circle_invite",
      senderUid: inviterUid,
      circleId: circleId,
    });

    const userSnap = await db.doc(`users/${inviteeUid}`).get();
    const token = userSnap.data()?.fcmToken;
    if (token) {
      await admin.messaging().send({
        token: token,
        data: {title: title, body: body, type: "circle_invite"},
      });
    }
  }
);

export const onPhotoAdded = onDocumentCreated(
  "circles/{circleId}/photos/{photoId}",
  async (event) => {
    const data = event.data?.data();
    if (!data) return;

    const circleId = event.params.circleId;
    const uploaderUid = data.uploaderUid;

    const db = admin.firestore();
    const circleSnap = await db.doc(`circles/${circleId}`).get();
    const circleData = circleSnap.data();
    if (!circleData) return;

    const circleName = circleData.name;
    const members = circleData.members as string[];
    const uploaderSnap = await db.doc(`user_public/${uploaderUid}`).get();
    const uploaderName = uploaderSnap.data()?.username || "Someone";

    const title = "New Photo";
    const body = `${uploaderName} posted a new photo in "${circleName}".`;

    const batch = db.batch();
    for (const memberUid of members) {
      if (memberUid === uploaderUid) continue;

      const notifRef = db.collection(`users/${memberUid}/notifications`).doc();
      batch.set(notifRef, {
        title: title,
        body: body,
        timestamp: admin.firestore.FieldValue.serverTimestamp(),
        type: "new_photo",
        senderUid: uploaderUid,
        circleId: circleId,
      });
    }

    await batch.commit();

    // Send Push to members
    for (const memberUid of members) {
      if (memberUid === uploaderUid) continue;
      const mDoc = await db.doc(`users/${memberUid}`).get();
      const token = mDoc.data()?.fcmToken;
      if (token) {
        await admin.messaging().send({
          token: token,
          data: {title: title, body: body, type: "new_photo"},
        });
      }
    }
  }
);

/** Firestore trigger: when a circle doc is deleted, clean up its Storage files and related Firestore docs. */
export const onCircleDeleted = onDocumentDeleted(
  "circles/{circleId}",
  async (event) => {
    const circleId = event.params.circleId;

    const db = admin.firestore();
    const bucket = admin.storage().bucket();

    // 1) Delete all files in Storage under circles/{circleId}/
    const [files] = await bucket.getFiles({prefix: `circles/${circleId}/`});
    await Promise.all(files.map((f) => f.delete().catch(() => null)));

    // 2) Delete photos subcollection docs (subcollections don't delete automatically)
    await deleteCollection(db.collection("circles").doc(circleId).collection("photos"));

    // 3) Delete circle_codes docs that point to this circle
    await deleteByQuery(db.collection("circle_codes").where("circleId", "==", circleId));

    // 4) Delete invites for this circle (optional but good cleanup)
    await deleteByQuery(db.collection("circle_invites").where("circleId", "==", circleId));

    // 5) Delete reports for this circle (optional)
    await deleteByQuery(db.collection("reports").where("circleId", "==", circleId));
  }
);

// -------- helpers --------

/**
 * Deletes all documents returned by a query, committing in batches (Firestore batch limit is 500).
 * @param {FirebaseFirestore.Query} query Query whose result docs will be deleted.
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

    // keep well below 500
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
 * Deletes an entire subcollection in batches.
 * @param {FirebaseFirestore.CollectionReference} col The collection reference to delete from.
 * @param {number} batchSize Max docs per batch commit (keep < 500).
 * @return {Promise<void>}
 */
async function deleteCollection(
  col: FirebaseFirestore.CollectionReference,
  batchSize = 450
) {
  const db = admin.firestore();

  // Loop until the collection is empty (no constant condition lint issue)
  let snap = await col.limit(batchSize).get();
  while (!snap.empty) {
    const batch = db.batch();
    snap.docs.forEach((d) => batch.delete(d.ref));
    await batch.commit();

    snap = await col.limit(batchSize).get();
  }
}

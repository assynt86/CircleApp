import { onDocumentCreated, onDocumentUpdated } from "firebase-functions/v2/firestore";
import { onSchedule } from "firebase-functions/v2/scheduler";
import * as admin from "firebase-admin";

admin.initializeApp();

const db = admin.firestore();
const fcm = admin.messaging();

/**
 * Send notification to a specific UID
 */
async function sendPushNotification(uid: string, title: string, body: string, data?: any) {
  const userDoc = await db.collection("users").doc(uid).get();
  const fcmToken = userDoc.data()?.fcmToken;

  if (fcmToken) {
    try {
      await fcm.send({
        token: fcmToken,
        notification: {
          title,
          body,
        },
        data: data || {},
      });
    } catch (error) {
      console.error(`Error sending notification to ${uid}:`, error);
    }
  }
}

/**
 * TEST FUNCTION: Sends a notification every 1 minute to ALL users with a token.
 * Remove this after testing!
 */
export const testNotificationTimer = onSchedule("every 1 minutes", async () => {
  const usersWithTokens = await db.collection("users").where("fcmToken", "!=", "").get();

  const promises = usersWithTokens.docs.map(doc => {
    const token = doc.data().fcmToken;
    return fcm.send({
      token: token,
      notification: {
        title: "Test Notification",
        body: `Test signal sent at ${new Date().toLocaleTimeString()}`,
      },
    }).catch(e => console.log("Failed to send test to", doc.id, e));
  });

  await Promise.all(promises);
});

/**
 * Notify owner when someone joins via invite code
 */
export const onCircleJoin = onDocumentUpdated("circles/{circleId}", async (event) => {
  const newData = event.data?.after.data();
  const oldData = event.data?.before.data();

  if (!newData || !oldData) return;

  const newMembers = newData.members as string[];
  const oldMembers = oldData.members as string[];

  if (newMembers.length > oldMembers.length) {
    const joinedUid = newMembers.find((m) => !oldMembers.includes(m));
    if (joinedUid && joinedUid !== newData.ownerUid) {
      const userDoc = await db.collection("users").doc(joinedUid).get();
      const username = userDoc.data()?.username || "Someone";
      const circleName = newData.name || "a circle";

      await sendPushNotification(
        newData.ownerUid,
        "New Member Joined!",
        `${username} has joined ${circleName}`
      );
    }
  }
});

/**
 * Notify members when someone uploads 20+ photos
 */
export const onPhotoUpload = onDocumentCreated("circles/{circleId}/photos/{photoId}", async (event) => {
  const photoData = event.data?.data();
  const circleId = event.params.circleId;

  if (!photoData) return;

  const uploaderUid = photoData.uploaderUid;

  const photosSnap = await db.collection("circles").doc(circleId).collection("photos")
    .where("uploaderUid", "==", uploaderUid)
    .get();

  if (photosSnap.size === 20) {
    const circleDoc = await db.collection("circles").doc(circleId).get();
    const circleData = circleDoc.data();
    if (!circleData) return;

    const userDoc = await db.collection("users").doc(uploaderUid).get();
    const username = userDoc.data()?.username || "Someone";

    const members = circleData.members as string[];
    const others = members.filter((m) => m !== uploaderUid);

    for (const uid of others) {
      await sendPushNotification(
        uid,
        "Photo Dump!",
        `${username} just dropped their photo dump in ${circleData.name}`
      );
    }
  }
});

/**
 * Friend Request Received
 */
export const onFriendRequestCreated = onDocumentCreated("friend_requests/{requestId}", async (event) => {
  const requestData = event.data?.data();
  if (!requestData) return;

  const senderDoc = await db.collection("users").doc(requestData.senderUid).get();
  const senderName = senderDoc.data()?.username || "Someone";

  await sendPushNotification(
    requestData.receiverUid,
    "New Friend Request",
    `${senderName} sent you a friend request.`
  );
});

/**
 * Friend Request Accepted
 */
export const onUserUpdate = onDocumentUpdated("users/{userId}", async (event) => {
  const newData = event.data?.after.data();
  const oldData = event.data?.before.data();

  if (!newData || !oldData) return;

  const newFriends = newData.friends as string[];
  const oldFriends = oldData.friends as string[];

  if (newFriends.length > oldFriends.length) {
    const addedFriendUid = newFriends.find((f) => !oldFriends.includes(f));
    if (addedFriendUid) {
      const userDoc = await db.collection("users").doc(event.params.userId).get();
      const username = userDoc.data()?.username || "Someone";

      await sendPushNotification(
        addedFriendUid,
        "Friend Request Accepted",
        `${username} accepted your friend request!`
      );
    }
  }
});

/**
 * Scheduled checks for closing/expiring circles
 */
export const notifyExpiringCircles = onSchedule("every 1 hours", async () => {
  const now = admin.firestore.Timestamp.now();
  const in24Hours = new admin.firestore.Timestamp(now.seconds + 24 * 3600, 0);
  const in25Hours = new admin.firestore.Timestamp(now.seconds + 25 * 3600, 0);

  const closingSnap = await db.collection("circles")
    .where("closeAt", ">=", in24Hours)
    .where("closeAt", "<", in25Hours)
    .get();

  for (const doc of closingSnap.docs) {
    const data = doc.data();
    for (const uid of data.members) {
      await sendPushNotification(
        uid,
        "Circle Closing Soon!",
        `${data.name} will close in 24 hours. Get your photos in!`
      );
    }
  }

  const expiringSnap = await db.collection("circles")
    .where("deleteAt", ">=", in24Hours)
    .where("deleteAt", "<", in25Hours)
    .get();

  for (const doc of expiringSnap.docs) {
    const data = doc.data();
    for (const uid of data.members) {
      await sendPushNotification(
        uid,
        "Circle Expiring Soon!",
        `${data.name} will be deleted in 24 hours. Save your memories!`
      );
    }
  }
});

/**
 * Original cleanup function
 */
export const cleanupExpiredCircles = onSchedule("every 60 minutes", async () => {
  const bucket = admin.storage().bucket();
  const now = admin.firestore.Timestamp.now();

  const circlesSnap = await db
    .collection("circles")
    .where("deleteAt", "<=", now)
    .where("cleanedUp", "==", false)
    .limit(25)
    .get();

  if (circlesSnap.empty) return;

  for (const circleDoc of circlesSnap.docs) {
    const circleId = circleDoc.id;
    const [files] = await bucket.getFiles({ prefix: `circles/${circleId}/` });
    await Promise.all(files.map((f) => f.delete().catch(() => null)));

    const photosSnap = await db.collection("circles").doc(circleId).collection("photos").get();
    const batch = db.batch();
    photosSnap.docs.forEach((d) => batch.delete(d.ref));
    batch.update(circleDoc.ref, { cleanedUp: true });
    await Promise.all([batch.commit()]);
  }
});

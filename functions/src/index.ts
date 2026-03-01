import {onSchedule} from "firebase-functions/v2/scheduler";
import {onDocumentUpdated} from "firebase-functions/v2/firestore";
import * as admin from "firebase-admin";

admin.initializeApp();

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

    // 3) Mark circle cleanedUp so it won’t run again
    batch.update(circleDoc.ref, {cleanedUp: true});

    await batch.commit();
  }
});

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

    const db = admin.firestore();
    const batch = db.batch();

    batch.update(db.doc(`users/${senderUid}`), {
      friends: admin.firestore.FieldValue.arrayUnion(receiverUid),
    });
    batch.update(db.doc(`users/${receiverUid}`), {
      friends: admin.firestore.FieldValue.arrayUnion(senderUid),
    });

    await batch.commit();
  }
);

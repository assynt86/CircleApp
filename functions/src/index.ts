import {onSchedule} from "firebase-functions/v2/scheduler";
import {onDocumentUpdated, onDocumentDeleted} from "firebase-functions/v2/firestore";
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

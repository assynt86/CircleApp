import * as admin from "firebase-admin";
import { onSchedule } from "firebase-functions/v2/scheduler";
import { logger } from "firebase-functions";

admin.initializeApp();

const db = admin.firestore();
const bucket = admin.storage().bucket();

export const cleanupExpiredCircles = onSchedule("every 5 minutes", async () => {
  logger.info("cleanupExpiredCircles triggered");

  const now = admin.firestore.Timestamp.now();

  const circlesSnap = await db
    .collection("circles")
    .where("cleanedUp", "==", false)
    .where("deleteAt", "<=", now)
    .limit(25)
    .get();

  logger.info(`cleanupExpiredCircles: found ${circlesSnap.size} expired circle(s).`);

  if (circlesSnap.empty) return;

  for (const circleDoc of circlesSnap.docs) {
    const circleId = circleDoc.id;

    try {
      await circleDoc.ref.update({
        cleanupInProgress: true,
        cleanupStartedAt: admin.firestore.FieldValue.serverTimestamp(),
      });

      const photosRef = db.collection("circles").doc(circleId).collection("photos");
      const photosSnap = await photosRef.get();

      logger.info(`Circle ${circleId}: found ${photosSnap.size} photo doc(s).`);

      await Promise.all(
        photosSnap.docs.map(async (photoDoc) => {
          const data = photoDoc.data() as { storagePath?: string };
          const storagePath = data.storagePath;

          if (!storagePath) return;

          await bucket.file(storagePath).delete({ ignoreNotFound: true });
        })
      );

      const photoDocs = photosSnap.docs;
      const chunkSize = 400;

      for (let i = 0; i < photoDocs.length; i += chunkSize) {
        const batch = db.batch();
        const chunk = photoDocs.slice(i, i + chunkSize);
        chunk.forEach((d) => batch.delete(d.ref));
        await batch.commit();
      }

      await circleDoc.ref.delete();

      logger.info(`Circle ${circleId}: deleted circle + all photos.`);
    } catch (e: unknown) {
      const msg =
        e instanceof Error ? e.message : typeof e === "string" ? e : "Unknown error";

      logger.error(`Circle ${circleId}: cleanup failed: ${msg}`);

      await circleDoc.ref.set(
        {
          cleanedUp: true,
          cleanupError: msg,
          cleanedAt: admin.firestore.FieldValue.serverTimestamp(),
        },
        { merge: true }
      );
    }
  }
});

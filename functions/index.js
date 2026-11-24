/**
 * KG Taxi server: push notifications.
 *
 * - New order  -> push to drivers on the line in the same city (only users with role "driver").
 * - Status change (driver assigned / arrived / finished / cancelled) -> push to the passenger.
 * - New chat message -> push to the other side of the ride.
 *
 * Needs the Firebase Blaze plan. Deploy:  firebase deploy --only functions
 */
const { onDocumentCreated, onDocumentUpdated } = require("firebase-functions/v2/firestore");
const { setGlobalOptions } = require("firebase-functions/v2");
const { initializeApp } = require("firebase-admin/app");
const { getFirestore } = require("firebase-admin/firestore");
const { getMessaging } = require("firebase-admin/messaging");

// The KG Taxi data lives in a named Firestore database (not "(default)").
const DATABASE = "ai-studio-android-kgtaxi-68b2dabe-46af-41b4-a972-626508d00f13";

setGlobalOptions({ region: "asia-southeast1", maxInstances: 5 });
const app = initializeApp();
const db = getFirestore(app, DATABASE);

async function tokenOf(uid) {
  if (!uid) return null;
  const snap = await db.collection("device_tokens").doc(uid).get();
  return snap.exists ? snap.get("token") : null;
}

async function send(tokens, title, body, data = {}) {
  const list = tokens.filter(Boolean);
  if (list.length === 0) return;
  const res = await getMessaging().sendEachForMulticast({
    tokens: list,
    notification: { title, body },
    data,
    android: { priority: "high", notification: { channelId: "kg_taxi_trips" } },
  });
  // Forget tokens of uninstalled apps.
  const dead = [];
  res.responses.forEach((r, i) => {
    const code = r.error && r.error.code;
    if (code === "messaging/registration-token-not-registered" || code === "messaging/invalid-registration-token") {
      dead.push(list[i]);
    }
  });
  for (const t of dead) {
    const q = await db.collection("device_tokens").where("token", "==", t).get();
    await Promise.all(q.docs.map((d) => d.ref.delete()));
  }
}

// New order -> drivers on the line in that city.
exports.notifyDriversNewRide = onDocumentCreated(
  { document: "rides/{rideId}", database: DATABASE },
  async (event) => {
    const ride = event.data && event.data.data();
    if (!ride || ride.status !== "SEARCHING") return;
    const online = await db.collection("device_tokens")
      .where("city", "==", ride.city)
      .where("driverOnline", "==", true)
      .limit(200)
      .get();
    const tokens = [];
    for (const d of online.docs) {
      if (d.id === ride.passengerId) continue;
      const role = await db.collection("roles").doc(d.id).get();
      if (role.exists && role.get("role") === "driver") tokens.push(d.get("token"));
    }
    await send(
      tokens,
      `Новый заказ • ${ride.priceSom} сом`,
      `${ride.pickupAddress} → ${ride.dropoffAddress}`,
      { rideId: event.params.rideId, type: "new_ride" }
    );
  }
);

// Status change -> passenger.
const STATUS_TEXT = {
  ASSIGNED: ["Водитель едет", (r) => [r.driverName, r.driverCar, r.driverPlate].filter(Boolean).join(" • ")],
  ARRIVED: ["Машина подъехала!", () => "Назовите водителю PIN из приложения"],
  COMPLETED: ["Поездка завершена", (r) => `К оплате ${r.priceSom} сом`],
  CANCELLED: ["Заказ отменён", () => "Водитель отменил заказ. Попробуйте заказать снова."],
};

exports.notifyPassengerStatus = onDocumentUpdated(
  { document: "rides/{rideId}", database: DATABASE },
  async (event) => {
    const before = event.data.before.data();
    const after = event.data.after.data();
    if (!before || !after || before.status === after.status) return;
    const t = STATUS_TEXT[after.status];
    if (!t) return;
    // A passenger who cancels does not need a push about it.
    if (after.status === "CANCELLED" && !after.driverId) return;
    await send([await tokenOf(after.passengerId)], t[0], t[1](after), {
      rideId: event.params.rideId,
      type: "status",
      status: after.status,
    });
  }
);

// Chat message -> the other side.
exports.notifyChatMessage = onDocumentCreated(
  { document: "rides/{rideId}/messages/{msgId}", database: DATABASE },
  async (event) => {
    const msg = event.data && event.data.data();
    if (!msg) return;
    const ride = (await db.collection("rides").doc(event.params.rideId).get()).data();
    if (!ride) return;
    const toPassenger = msg.senderId !== ride.passengerId;
    const target = toPassenger ? ride.passengerId : ride.driverId;
    await send(
      [await tokenOf(target)],
      toPassenger ? "Сообщение от водителя" : "Сообщение от пассажира",
      String(msg.text).slice(0, 200),
      { rideId: event.params.rideId, type: "chat" }
    );
  }
);

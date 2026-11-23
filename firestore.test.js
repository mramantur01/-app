const {
  initializeTestEnvironment,
  assertFails,
  assertSucceeds,
} = require("@firebase/rules-unit-testing");
const { test, before, after, beforeEach } = require("node:test");
const fs = require("node:fs");

let testEnv;
const PROJECT_ID = process.env.GCP_PROJECT || "demo-no-project";
const ALICE_UID = "alice_kg_123";
const BOB_UID = "bob_kg_456";

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

function makeProfile(uid, ts) {
  return {
    userId: uid,
    displayName: "Амантур",
    phone: "+996 555 123 456",
    email: "amantur@example.kg",
    homeAddress: "пр. Чуй 155, Бишкек",
    workAddress: "ул. Киевская 95, Бишкек",
    paymentMethod: "MBANK",
    kgOneActive: true,
    businessProfileActive: false,
    companyName: "Ala-Too Tech",
    companyEmail: "finance@alatoo.kg",
    bonusPoints: 250,
    preferredCity: "Бишкек",
    language: "RU",
    darkTheme: false,
    soundEnabled: true,
    createdAt: ts,
    updatedAt: ts,
  };
}

function makeTrip(uid, ts) {
  return {
    userId: uid,
    serviceType: "TAXI",
    tariffName: "Комфорт",
    city: "Бишкек",
    pickupAddress: "Площадь Ала-Тоо",
    dropoffAddress: "Аэропорт Манас (FRU)",
    distanceKm: 28.4,
    durationMin: 35,
    totalPriceSom: 890,
    paymentMethod: "MBANK",
    status: "IN_PROGRESS",
    driverName: "Нурлан Т.",
    driverCar: "Toyota Camry • 01KG 777 AAA",
    safetyPin: "4829",
    rating: 0,
    tipSom: 0,
    businessTrip: false,
    createdAt: ts,
    updatedAt: ts,
  };
}

test("Unauthenticated users cannot read or write any KG Taxi collection", async () => {
  const unauthDb = testEnv.unauthenticatedContext().firestore();
  const ts = new Date(Date.now() - 2000);
  await assertFails(unauthDb.collection("users").doc(ALICE_UID).get());
  await assertFails(unauthDb.collection("users").doc(ALICE_UID).set(makeProfile(ALICE_UID, ts)));
  await assertFails(unauthDb.collection("users").doc(ALICE_UID).collection("trips").get());
  await assertFails(unauthDb.collection("users").doc(ALICE_UID).collection("saved_places").get());
});

test("UserProfile: owner create, partial update, shadow field & cross-user rejection", async () => {
  const aliceDb = testEnv.authenticatedContext(ALICE_UID).firestore();
  const bobDb = testEnv.authenticatedContext(BOB_UID).firestore();
  const ts = new Date(Date.now() - 5000);

  await assertSucceeds(aliceDb.collection("users").doc(ALICE_UID).set(makeProfile(ALICE_UID, ts)));
  await assertSucceeds(aliceDb.collection("users").doc(ALICE_UID).get());

  // Partial update succeeds
  await assertSucceeds(
    aliceDb.collection("users").doc(ALICE_UID).update({
      kgOneActive: false,
      darkTheme: true,
      updatedAt: new Date(Date.now() - 1000),
    })
  );

  // Immutable createdAt cannot be mutated
  await assertFails(
    aliceDb.collection("users").doc(ALICE_UID).update({
      createdAt: new Date(Date.now() - 500),
      updatedAt: new Date(Date.now() - 500),
    })
  );

  // Ghost / shadow field rejected
  await assertFails(
    aliceDb.collection("users").doc(ALICE_UID).update({
      isAdmin: true,
      updatedAt: new Date(Date.now() - 500),
    })
  );

  // Future timestamp rejected
  await assertFails(
    aliceDb.collection("users").doc(ALICE_UID).update({
      bonusPoints: 300,
      updatedAt: new Date(Date.now() + 60000),
    })
  );

  // Cross-user read & write rejected
  await assertFails(bobDb.collection("users").doc(ALICE_UID).get());
  await assertFails(bobDb.collection("users").doc(ALICE_UID).update({ bonusPoints: 0 }));
});

test("TripRecord: owner create, in-trip edit, completion lock & post-trip rating", async () => {
  const aliceDb = testEnv.authenticatedContext(ALICE_UID).firestore();
  const bobDb = testEnv.authenticatedContext(BOB_UID).firestore();
  const ts = new Date(Date.now() - 10000);
  const tripRef = aliceDb.collection("users").doc(ALICE_UID).collection("trips").doc("trip_001");

  await assertSucceeds(tripRef.set(makeTrip(ALICE_UID, ts)));
  await assertSucceeds(aliceDb.collection("users").doc(ALICE_UID).collection("trips").get());

  // In-trip destination edit succeeds while IN_PROGRESS
  await assertSucceeds(
    tripRef.update({
      dropoffAddress: "ТЦ Asia Mall",
      distanceKm: 6.2,
      durationMin: 14,
      totalPriceSom: 260,
      updatedAt: new Date(Date.now() - 4000),
    })
  );

  // Complete trip
  await assertSucceeds(
    tripRef.update({
      status: "COMPLETED",
      updatedAt: new Date(Date.now() - 3000),
    })
  );

  // Terminal state locking: cannot modify totalPriceSom or status after COMPLETED
  await assertFails(
    tripRef.update({
      status: "IN_PROGRESS",
      updatedAt: new Date(Date.now() - 2000),
    })
  );
  await assertFails(
    tripRef.update({
      totalPriceSom: 0,
      updatedAt: new Date(Date.now() - 2000),
    })
  );

  // Post-trip rating, tip, and lost item note succeed on COMPLETED trip
  await assertSucceeds(
    tripRef.update({
      rating: 5,
      tipSom: 100,
      lostItemNote: "Забыл наушники на заднем сиденье",
      updatedAt: new Date(Date.now() - 1000),
    })
  );

  // Cross-user access denied
  await assertFails(
    bobDb.collection("users").doc(ALICE_UID).collection("trips").doc("trip_001").get()
  );
  await assertFails(
    bobDb.collection("users").doc(ALICE_UID).collection("trips").get()
  );
});

test("SavedPlace, FamilyMember, and VehicleMaintenance CRUD and isolation", async () => {
  const aliceDb = testEnv.authenticatedContext(ALICE_UID).firestore();
  const bobDb = testEnv.authenticatedContext(BOB_UID).firestore();
  const ts = new Date(Date.now() - 5000);

  // Saved place
  await assertSucceeds(
    aliceDb.collection("users").doc(ALICE_UID).collection("saved_places").doc("place_1").set({
      userId: ALICE_UID,
      label: "Дом",
      address: "пр. Чуй 155",
      city: "Бишкек",
      lat: 42.8746,
      lng: 74.5698,
      category: "HOME",
      rating: 5.0,
      createdAt: ts,
      updatedAt: ts,
    })
  );
  await assertSucceeds(aliceDb.collection("users").doc(ALICE_UID).collection("saved_places").get());

  // Family member
  await assertSucceeds(
    aliceDb.collection("users").doc(ALICE_UID).collection("family_members").doc("fam_1").set({
      userId: ALICE_UID,
      name: "Айдана",
      relation: "Дочь (Teen)",
      phone: "+996 700 555 222",
      monthlyLimitSom: 15000,
      teenControlEnabled: true,
      createdAt: ts,
      updatedAt: ts,
    })
  );
  await assertSucceeds(aliceDb.collection("users").doc(ALICE_UID).collection("family_members").get());

  // Maintenance log
  await assertSucceeds(
    aliceDb.collection("users").doc(ALICE_UID).collection("maintenance_logs").doc("maint_1").set({
      userId: ALICE_UID,
      vehicleName: "Toyota Camry 70",
      serviceTitle: "Замена моторного масла 5W-30",
      dueMileageKm: 95000,
      currentMileageKm: 92400,
      fuelLitersPer100Km: 8.5,
      completed: false,
      createdAt: ts,
      updatedAt: ts,
    })
  );
  await assertSucceeds(aliceDb.collection("users").doc(ALICE_UID).collection("maintenance_logs").get());

  // Cross-user isolation
  await assertFails(bobDb.collection("users").doc(ALICE_UID).collection("saved_places").get());
  await assertFails(bobDb.collection("users").doc(ALICE_UID).collection("family_members").get());
  await assertFails(bobDb.collection("users").doc(ALICE_UID).collection("maintenance_logs").get());
});

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

function nowDate() {
  return new Date(Date.now() - 1000);
}

test("Unauthenticated user: cannot read or write customSources or playlists", async () => {
  const unauthDb = testEnv.unauthenticatedContext().firestore();
  await assertFails(
    unauthDb.collection("users").doc(ALICE_UID).collection("customSources").get()
  );
  await assertFails(
    unauthDb.collection("users").doc(ALICE_UID).collection("playlists").get()
  );
});

test("Authenticated owner: can create and query custom music API source and playlist", async () => {
  const aliceDb = testEnv.authenticatedContext(ALICE_UID).firestore();
  const ts = nowDate();
  await assertSucceeds(
    aliceDb
      .collection("users")
      .doc(ALICE_UID)
      .collection("customSources")
      .doc("src_global_01")
      .set({
        userId: ALICE_UID,
        name: "Global Open Lossless API",
        baseUrl: "https://de1.api.radio-browser.info",
        searchPath: "/json/stations/search",
        protocolType: "RADIO_BROWSER_GLOBAL",
        regionCode: "GLOBAL",
        customHeaderName: "",
        customHeaderValue: "",
        preferredCodec: "FLAC Hi-Res",
        isEnabled: true,
        createdAt: ts,
        updatedAt: ts,
      })
  );

  await assertSucceeds(
    aliceDb
      .collection("users")
      .doc(ALICE_UID)
      .collection("customSources")
      .where("userId", "==", ALICE_UID)
      .get()
  );

  await assertSucceeds(
    aliceDb
      .collection("users")
      .doc(ALICE_UID)
      .collection("playlists")
      .doc("pl_01")
      .set({
        userId: ALICE_UID,
        title: "Global Audiophile Reference",
        description: "24-bit/192kHz custom API masters",
        accentHex: "#00F5D4",
        sourceService: "CUSTOM_API",
        isOfflinePinned: true,
        trackIds: ["trk_1", "trk_2"],
        createdAt: ts,
        updatedAt: ts,
      })
  );
});

test("Cross-user access: Bob cannot read or modify Alice's custom music source", async () => {
  const ts = nowDate();
  await testEnv.withSecurityRulesDisabled(async (context) => {
    await context
      .firestore()
      .collection("users")
      .doc(ALICE_UID)
      .collection("customSources")
      .doc("src_alice")
      .set({
        userId: ALICE_UID,
        name: "Alice Private Subsonic Node",
        baseUrl: "https://music.example.org",
        searchPath: "/rest/search3.view",
        protocolType: "SUBSONIC_NAVIDROME",
        regionCode: "JP",
        customHeaderName: "X-Api-Key",
        customHeaderValue: "secret",
        preferredCodec: "FLAC 24-Bit",
        isEnabled: true,
        createdAt: ts,
        updatedAt: ts,
      });
  });

  const bobDb = testEnv.authenticatedContext(BOB_UID).firestore();
  await assertFails(
    bobDb
      .collection("users")
      .doc(ALICE_UID)
      .collection("customSources")
      .doc("src_alice")
      .get()
  );
});

test("Shadow update & value poisoning: rejects ghost fields and invalid protocolType", async () => {
  const aliceDb = testEnv.authenticatedContext(ALICE_UID).firestore();
  const ts = nowDate();
  await assertFails(
    aliceDb
      .collection("users")
      .doc(ALICE_UID)
      .collection("customSources")
      .doc("src_bad")
      .set({
        userId: ALICE_UID,
        name: "Bad Source",
        baseUrl: "https://api.example.com",
        searchPath: "/v1/tracks",
        protocolType: "INVALID_PROTOCOL",
        regionCode: "GLOBAL",
        customHeaderName: "",
        customHeaderValue: "",
        preferredCodec: "FLAC",
        isEnabled: true,
        createdAt: ts,
        updatedAt: ts,
      })
  );
});

test("SoundProfile and SyncState: owner can create and update valid documents", async () => {
  const aliceDb = testEnv.authenticatedContext(ALICE_UID).firestore();
  const ts = nowDate();
  await assertSucceeds(
    aliceDb
      .collection("users")
      .doc(ALICE_UID)
      .collection("soundProfiles")
      .doc("eq_harman")
      .set({
        userId: ALICE_UID,
        name: "Harman Reference 2026",
        presetCategory: "HARMAN_TARGET",
        bandsDb: [35, 25, 10, -5, 0, 15, 25, 30, 20, 10],
        preampGainTenths: -20,
        bassBoostPercent: 25,
        spatializerStrength: 40,
        dacBitPerfectMode: true,
        isActive: true,
        createdAt: ts,
        updatedAt: ts,
      })
  );

  await assertSucceeds(
    aliceDb
      .collection("users")
      .doc(ALICE_UID)
      .collection("syncState")
      .doc("android_primary")
      .set({
        userId: ALICE_UID,
        deviceName: "Pixel 10 Pro (USB DAC)",
        activeTrackId: "trk_neon_01",
        positionMs: 42500,
        isPlaying: true,
        activeService: "CUSTOM_API",
        audioQualityBadge: "24-bit/192kHz FLAC",
        activeSourceName: "Global Open Lossless API",
        regionCode: "GLOBAL",
        createdAt: ts,
        updatedAt: ts,
      })
  );
});

test("library backup: only the authenticated owner can read or write a bounded snapshot", async () => {
  const alice = testEnv.authenticatedContext(ALICE_UID).firestore();
  const bob = testEnv.authenticatedContext(BOB_UID).firestore();
  const guest = testEnv.unauthenticatedContext().firestore();
  const aliceBackup = alice.collection("users").doc(ALICE_UID).collection("backups").doc("library");
  const path = (db) => db.collection("users").doc(ALICE_UID).collection("backups").doc("library");
  // The compat SDK used elsewhere in this suite accepts its own FieldValue sentinel.
  const firebase = require("firebase/compat/app");
  require("firebase/compat/firestore");
  const payload = { userId: ALICE_UID, schemaVersion: 1, data: '{"version":1,"tracks":[],"playlists":[]}', updatedAt: firebase.firestore.FieldValue.serverTimestamp() };
  await assertSucceeds(aliceBackup.set(payload));
  await assertSucceeds(aliceBackup.get());
  await assertFails(path(bob).get());
  await assertFails(path(guest).get());
  await assertFails(path(bob).set({ ...payload, userId: BOB_UID }));
  await assertFails(aliceBackup.set({ ...payload, extra: true }));
  await assertFails(aliceBackup.set({ ...payload, data: "x".repeat(700001) }));
  await assertFails(alice.collection("users").doc(ALICE_UID).collection("backups").doc("other").set(payload));
  await assertFails(alice.collection("users").doc(ALICE_UID).collection("backups").get());
});

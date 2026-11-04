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

const [emulatorHost, emulatorPortStr] = (process.env.FIRESTORE_EMULATOR_HOST || "127.0.0.1:8085").split(":");
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

test("Unauthenticated user: cannot read files", async () => {
  const unauthDb = testEnv.unauthenticatedContext().firestore();
  await assertFails(unauthDb.collection("users").doc(ALICE_UID).collection("files").get());
});

test("Cross-user access: Bob cannot read Alice's files", async () => {
  await testEnv.withSecurityRulesDisabled(async (context) => {
    await context.firestore().collection("users").doc(ALICE_UID).collection("files").doc("file_1").set({
      userId: ALICE_UID,
      name: "sample.jpg",
      mimeType: "image/jpeg",
      fileType: "PHOTO",
      sizeBytes: 1024,
      contentBase64: "aGVsbG8=",
      category: "Photos",
      isStarred: false,
      createdAt: new Date(),
      updatedAt: new Date()
    });
  });

  const bobDb = testEnv.authenticatedContext(BOB_UID).firestore();
  await assertFails(bobDb.collection("users").doc(ALICE_UID).collection("files").doc("file_1").get());
  await assertFails(bobDb.collection("users").doc(ALICE_UID).collection("files").get());
});

test("Authenticated owner: can create valid photo and read it", async () => {
  const aliceDb = testEnv.authenticatedContext(ALICE_UID).firestore();
  const fileRef = aliceDb.collection("users").doc(ALICE_UID).collection("files").doc("photo_1");

  await assertSucceeds(
    fileRef.set({
      userId: ALICE_UID,
      name: "vacation.png",
      mimeType: "image/png",
      fileType: "PHOTO",
      sizeBytes: 2048,
      contentBase64: "iVBORw0KGgoAAAANSUhEUgAAAAEAAAABCAYAAAAfFcSJAAAADUlEQVR42mNk+M9QDwADhgGAWjR9awAAAABJRU5ErkJggg==",
      thumbnailBase64: "iVBORw0KGgo=",
      description: "Beach photo",
      category: "Photos",
      isStarred: true,
      createdAt: new Date(),
      updatedAt: new Date()
    })
  );

  await assertSucceeds(fileRef.get());
  await assertSucceeds(aliceDb.collection("users").doc(ALICE_UID).collection("files").get());
});

test("Authenticated owner: can create valid PDF document", async () => {
  const aliceDb = testEnv.authenticatedContext(ALICE_UID).firestore();
  const pdfRef = aliceDb.collection("users").doc(ALICE_UID).collection("files").doc("pdf_1");

  await assertSucceeds(
    pdfRef.set({
      userId: ALICE_UID,
      name: "document.pdf",
      mimeType: "application/pdf",
      fileType: "PDF",
      sizeBytes: 5000,
      contentBase64: "JVBERi0xLjQK",
      category: "PDFs",
      isStarred: false,
      createdAt: new Date(),
      updatedAt: new Date()
    })
  );
});

test("Validation: rejects file with missing required fields or spoofed userId", async () => {
  const aliceDb = testEnv.authenticatedContext(ALICE_UID).firestore();
  const fileRef = aliceDb.collection("users").doc(ALICE_UID).collection("files").doc("bad_file");

  // Spoofed userId
  await assertFails(
    fileRef.set({
      userId: BOB_UID,
      name: "bad.pdf",
      mimeType: "application/pdf",
      fileType: "PDF",
      sizeBytes: 100,
      contentBase64: "AAA=",
      category: "PDFs",
      isStarred: false,
      createdAt: new Date(),
      updatedAt: new Date()
    })
  );

  // Missing required sizeBytes
  await assertFails(
    fileRef.set({
      userId: ALICE_UID,
      name: "bad.pdf",
      mimeType: "application/pdf",
      fileType: "PDF",
      contentBase64: "AAA=",
      category: "PDFs",
      isStarred: false,
      createdAt: new Date(),
      updatedAt: new Date()
    })
  );
});

test("Update & Delete: Alice can update and delete her own file; Bob cannot", async () => {
  await testEnv.withSecurityRulesDisabled(async (context) => {
    await context.firestore().collection("users").doc(ALICE_UID).collection("files").doc("file_to_update").set({
      userId: ALICE_UID,
      name: "notes.pdf",
      mimeType: "application/pdf",
      fileType: "PDF",
      sizeBytes: 1024,
      contentBase64: "AAA=",
      category: "Work",
      isStarred: false,
      createdAt: new Date(),
      updatedAt: new Date()
    });
  });

  const bobDb = testEnv.authenticatedContext(BOB_UID).firestore();
  await assertFails(bobDb.collection("users").doc(ALICE_UID).collection("files").doc("file_to_update").update({
    name: "hacked.pdf"
  }));
  await assertFails(bobDb.collection("users").doc(ALICE_UID).collection("files").doc("file_to_update").delete());

  const aliceDb = testEnv.authenticatedContext(ALICE_UID).firestore();
  await assertSucceeds(aliceDb.collection("users").doc(ALICE_UID).collection("files").doc("file_to_update").update({
    name: "renamed_notes.pdf",
    isStarred: true,
    updatedAt: new Date()
  }));
  await assertSucceeds(aliceDb.collection("users").doc(ALICE_UID).collection("files").doc("file_to_update").delete());
});

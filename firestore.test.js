const {
  initializeTestEnvironment,
  assertFails,
  assertSucceeds,
} = require("@firebase/rules-unit-testing");
const { test, before, after, beforeEach } = require("node:test");
const fs = require("node:fs");

let testEnv;
const PROJECT_ID = process.env.GCP_PROJECT || "demo-no-project";
const ALICE_UID = "alice_uid_123";
const BOB_UID = "bob_uid_456";

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

test("Unauthenticated user cannot read or write tasks, categories, or history", async () => {
  const unauthDb = testEnv.unauthenticatedContext().firestore();
  await assertFails(unauthDb.collection("users").doc(ALICE_UID).collection("tasks").get());
  await assertFails(unauthDb.collection("users").doc(ALICE_UID).collection("categories").get());
  await assertFails(unauthDb.collection("users").doc(ALICE_UID).collection("history").get());
  await assertFails(
    unauthDb.collection("users").doc(ALICE_UID).collection("tasks").doc("task_1").set({
      userId: ALICE_UID,
      title: "Rogue task",
      description: "",
      priority: "MEDIUM",
      completed: false,
      recurring: false,
      recurrencePattern: "NONE",
      createdAt: new Date(Date.now() - 1000),
      updatedAt: new Date(Date.now() - 1000),
    })
  );
});

test("Authenticated owner can create, update, list, and delete tasks matching Kotlin payloads", async () => {
  const aliceDb = testEnv.authenticatedContext(ALICE_UID).firestore();
  const created = new Date(Date.now() - 5000);

  // List on empty user-scoped subcollection must succeed
  await assertSucceeds(aliceDb.collection("users").doc(ALICE_UID).collection("tasks").get());

  // Valid create (matches CloudTask.toCreateMap() without @DocumentId `id`)
  await assertSucceeds(
    aliceDb.collection("users").doc(ALICE_UID).collection("tasks").doc("task_1").set({
      userId: ALICE_UID,
      title: "Ship Firebase sync",
      description: "Verify rules and Credential Manager",
      categoryId: "cat_work",
      priority: "HIGH",
      dueDate: Date.now(),
      dueTime: "14:30",
      completed: false,
      recurring: true,
      recurrencePattern: "DAILY",
      subtasks: [{ id: "sub_1", title: "Write rules", completed: true, sortOrder: 0 }],
      createdAt: created,
      updatedAt: created,
    })
  );

  // Valid partial update (preserves userId & createdAt)
  await assertSucceeds(
    aliceDb.collection("users").doc(ALICE_UID).collection("tasks").doc("task_1").update({
      completed: true,
      completedAt: Date.now(),
      updatedAt: new Date(Date.now() - 1000),
    })
  );

  // Reject Shadow Update with ghost field
  await assertFails(
    aliceDb.collection("users").doc(ALICE_UID).collection("tasks").doc("task_1").update({
      title: "Shadow",
      isAdminOverride: true,
      updatedAt: new Date(Date.now() - 1000),
    })
  );

  // Reject mutating immutable createdAt
  await assertFails(
    aliceDb.collection("users").doc(ALICE_UID).collection("tasks").doc("task_1").update({
      createdAt: new Date(Date.now() - 500),
      updatedAt: new Date(Date.now() - 500),
    })
  );

  // Reject extra @DocumentId `id` field on create
  await assertFails(
    aliceDb.collection("users").doc(ALICE_UID).collection("tasks").doc("task_2").set({
      id: "task_2",
      userId: ALICE_UID,
      title: "Invalid ID field",
      description: "",
      priority: "LOW",
      completed: false,
      recurring: false,
      recurrencePattern: "NONE",
      createdAt: created,
      updatedAt: created,
    })
  );

  // Reject future timestamp skew
  await assertFails(
    aliceDb.collection("users").doc(ALICE_UID).collection("tasks").doc("task_future").set({
      userId: ALICE_UID,
      title: "Future timestamp",
      description: "",
      priority: "LOW",
      completed: false,
      recurring: false,
      recurrencePattern: "NONE",
      createdAt: new Date(Date.now() + 60000),
      updatedAt: new Date(Date.now() + 60000),
    })
  );

  // Owner delete succeeds
  await assertSucceeds(aliceDb.collection("users").doc(ALICE_UID).collection("tasks").doc("task_1").delete());
});

test("Cross-user isolation blocks Bob from reading or modifying Alice's tasks, categories, and history", async () => {
  const past = new Date(Date.now() - 10000);
  await testEnv.withSecurityRulesDisabled(async (ctx) => {
    const db = ctx.firestore();
    await db.collection("users").doc(ALICE_UID).collection("tasks").doc("t1").set({
      userId: ALICE_UID,
      title: "Private Alice Task",
      description: "",
      priority: "URGENT",
      completed: false,
      recurring: false,
      recurrencePattern: "NONE",
      createdAt: past,
      updatedAt: past,
    });
    await db.collection("users").doc(ALICE_UID).collection("categories").doc("c1").set({
      userId: ALICE_UID,
      name: "Work",
      color: 0xFF6366F1,
      createdAt: past,
      updatedAt: past,
    });
    await db.collection("users").doc(ALICE_UID).collection("history").doc("h1").set({
      userId: ALICE_UID,
      taskId: "t1",
      taskTitle: "Private Alice Task",
      priority: "URGENT",
      completedAtMillis: Date.now(),
      createdAt: past,
    });
  });

  const bobDb = testEnv.authenticatedContext(BOB_UID).firestore();
  await assertFails(bobDb.collection("users").doc(ALICE_UID).collection("tasks").get());
  await assertFails(bobDb.collection("users").doc(ALICE_UID).collection("tasks").doc("t1").get());
  await assertFails(bobDb.collection("users").doc(ALICE_UID).collection("tasks").doc("t1").update({ title: "Hijack", updatedAt: past }));
  await assertFails(bobDb.collection("users").doc(ALICE_UID).collection("tasks").doc("t1").delete());
  await assertFails(bobDb.collection("users").doc(ALICE_UID).collection("categories").get());
  await assertFails(bobDb.collection("users").doc(ALICE_UID).collection("history").get());
});

test("Categories and CompletionHistory enforce schema bounds and immutability", async () => {
  const aliceDb = testEnv.authenticatedContext(ALICE_UID).firestore();
  const past = new Date(Date.now() - 2000);

  await assertSucceeds(
    aliceDb.collection("users").doc(ALICE_UID).collection("categories").doc("cat_1").set({
      userId: ALICE_UID,
      name: "Deep Focus",
      color: 0xFF0EA5E9,
      createdAt: past,
      updatedAt: past,
    })
  );

  // Empty category name rejected
  await assertFails(
    aliceDb.collection("users").doc(ALICE_UID).collection("categories").doc("cat_bad").set({
      userId: ALICE_UID,
      name: "",
      color: 0xFF0EA5E9,
      createdAt: past,
      updatedAt: past,
    })
  );

  await assertSucceeds(
    aliceDb.collection("users").doc(ALICE_UID).collection("history").doc("hist_1").set({
      userId: ALICE_UID,
      taskId: "t1",
      taskTitle: "Completed task",
      categoryName: "Deep Focus",
      categoryColor: 0xFF0EA5E9,
      priority: "MEDIUM",
      completedAtMillis: Date.now(),
      createdAt: past,
    })
  );

  // History entries are append-only (update forbidden)
  await assertFails(
    aliceDb.collection("users").doc(ALICE_UID).collection("history").doc("hist_1").update({
      taskTitle: "Tampered",
    })
  );
});

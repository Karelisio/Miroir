// Test de bout en bout du Worker (deux membres simulés). Usage : node scripts/smoke.mjs [URL]
// Lancez d'abord : npm run db:migrate:local && npx wrangler dev --local --port 8787
import assert from "node:assert/strict";

const BASE = process.argv[2] ?? "http://localhost:8787";
const rnd = (n) => Buffer.from(crypto.getRandomValues(new Uint8Array(n))).toString("base64url");
const pairId = rnd(16);
const secret = rnd(32);
const day = new Date().toISOString().slice(0, 10);

async function call(method, path, member, body, auth = secret) {
  const res = await fetch(BASE + path, {
    method,
    headers: { "content-type": "application/json", authorization: `Bearer ${auth}`, "x-pair-id": pairId, "x-member": member },
    body: body ? JSON.stringify(body) : undefined,
  });
  return { status: res.status, body: await res.json() };
}

let r = await call("POST", "/pair", "A", { action: "create", profile: "cHJvZkE" });
assert.equal(r.status, 200);
assert.equal(r.body.partnerJoined, false);

r = await call("POST", "/pair", "B", { action: "join", profile: "cHJvZkI" });
assert.equal(r.body.partnerJoined, true);

// Verrou : la première proposition l'emporte.
r = await call("POST", "/state", "A", { since: 0, today: { day, tag: "tag1", ref: "cmVmMQ" } });
assert.equal(r.body.days[0].tag, "tag1");
r = await call("POST", "/state", "B", { since: 0, today: { day, tag: "tag2", ref: "cmVmMg" } });
assert.equal(r.body.days[0].tag, "tag1");

// Joker : une seule fois par jour.
r = await call("POST", "/skip", "B", { day, expectedSkips: 0, tag: "tag3", ref: "cmVmMw" });
assert.equal(r.body.day.tag, "tag3");
assert.equal((await call("POST", "/skip", "A", { day, expectedSkips: 0, tag: "tag4", ref: "cmVmNA" })).status, 409);

// Réponse sur l'ancienne question refusée, puis réponse de A cachée à B.
assert.equal((await call("POST", "/answer", "A", { day, tag: "tag1", ref: "cmVmMQ", payload: "YQ" })).body.error, "question_changed");
r = await call("POST", "/answer", "A", { day, tag: "tag3", ref: "cmVmMw", payload: "YW5zQQ" });
assert.equal(r.status, 200);
r = await call("POST", "/state", "B", { since: 0 });
assert.equal(r.body.days[0].answers.A.payload, null, "la réponse de A ne doit pas fuiter avant celle de B");
assert.equal((await call("POST", "/react", "A", { day, payload: "cmVh" })).status, 409);

// Les deux ont répondu : révélation.
r = await call("POST", "/answer", "B", { day, tag: "tag3", ref: "cmVmMw", payload: "YW5zQg" });
assert.equal(r.body.day.answers.A.payload, "YW5zQQ");
assert.equal((await call("POST", "/answer", "B", { day, tag: "tag3", ref: "cmVmMw", payload: "YW5zQg" })).status, 200, "renvoi idempotent");
r = await call("POST", "/react", "A", { day, payload: "cmVh" });
assert.equal(r.body.day.reactions.A.payload, "cmVh");

// Passer un jour non verrouillé : le client fournit sa question courante.
const day2 = new Date(Date.now() - 86_400_000).toISOString().slice(0, 10);
r = await call("POST", "/skip", "A", { day: day2, expectedSkips: 0, tag: "n2", ref: "bjI", currentTag: "n1", currentRef: "bjE" });
assert.equal(r.body.day.tag, "n2");
assert.equal(r.body.day.skippedRef, "bjE");

// Synchro incrémentale.
const rev = (await call("POST", "/state", "B", { since: 0 })).body.rev;
await call("POST", "/custom-question", "A", { id: rnd(16), payload: "Y3E" });
r = await call("POST", "/state", "B", { since: rev });
assert.equal(r.body.days.length, 0);
r = await call("POST", "/state", "B", { since: 0 });
assert.equal(r.body.days.length, 2);
assert.equal(r.body.days.find((d) => d.day === day).answers.B.payload, "YW5zQg");
assert.equal(r.body.customQuestions.length, 1);

// Réglages : version optimiste.
assert.equal((await call("POST", "/settings", "A", { payload: "c2V0", baseVersion: 0 })).body.settings.version, 1);
assert.equal((await call("POST", "/settings", "B", { payload: "c2V0Mg", baseVersion: 0 })).status, 409);

// Authentification et dissociation.
assert.equal((await call("POST", "/state", "A", { since: 0 }, rnd(32))).status, 401);
assert.equal((await call("POST", "/token/update", "B", { fcmToken: "abc" })).status, 200);
assert.equal((await call("DELETE", "/pair", "A")).status, 200);
assert.equal((await call("POST", "/state", "A", { since: 0 })).status, 401);

console.log("✓ Worker : tous les scénarios passent");

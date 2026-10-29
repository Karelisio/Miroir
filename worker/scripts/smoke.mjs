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

// Coucou : un seul toutes les 30 minutes.
assert.equal((await call("POST", "/nudge", "A")).status, 200);
assert.equal((await call("POST", "/nudge", "A")).status, 429);
assert.equal((await call("POST", "/nudge", "B")).status, 200);

// ---------- De l'autre côté (jeux) ----------
const game = rnd(16);
const done = "$done";
r = await call("POST", "/games/session", "A", { id: game, kind: "duel", reveal: "all", payload: "cGFydGll", notify: true });
assert.equal(r.status, 200);
assert.equal(r.body.created, true);
r = await call("POST", "/games/session", "B", { id: game, kind: "duel", reveal: "all", payload: "YXV0cmU" });
assert.equal(r.body.created, false, "création idempotente : la première partie l'emporte");
assert.equal(r.body.session.payload, "cGFydGll");
assert.equal((await call("POST", "/games/session", "A", { id: rnd(16), kind: "poker", reveal: "all", payload: "eA" })).status, 400);

// Duel (révélation « all ») : rien ne fuite tant que les deux n'ont pas fini.
await call("POST", "/games/answer", "A", { session: game, item: "q0", payload: "QTA" });
await call("POST", "/games/answer", "A", { session: game, item: done, payload: "ZA", notify: true });
await call("POST", "/games/answer", "B", { session: game, item: "q0", payload: "QjA" });
r = await call("POST", "/games/state", "B", { since: 0 });
let s = r.body.sessions.find((x) => x.id === game);
assert.equal(s.answers.find((a) => a.member === "A" && a.item === "q0").payload, null, "réponse de A cachée avant la fin de B");
assert.equal(s.answers.find((a) => a.member === "A" && a.item === done).payload, "ZA", "l'état « terminé » est visible");
assert.equal((await call("POST", "/games/answer", "A", { session: game, item: "q0", payload: "Q09SUklHRQ" })).body.session.answers.find((a) => a.member === "A" && a.item === "q0").payload, "QTA", "une réponse donnée ne change plus");
r = await call("POST", "/games/answer", "B", { session: game, item: done, payload: "ZA", notify: true });
s = r.body.session;
assert.equal(s.answers.find((a) => a.member === "A" && a.item === "q0").payload, "QTA", "révélation une fois les deux terminés");

// Devine-moi (révélation « item ») : la réponse de l'autre n'apparaît qu'après avoir répondu au même élément.
const devine = rnd(16);
await call("POST", "/games/session", "A", { id: devine, kind: "devine", reveal: "item", payload: "ZGV2" });
await call("POST", "/games/answer", "A", { session: devine, item: "q0", payload: "UkVQ" });
r = await call("POST", "/games/state", "B", { since: 0 });
assert.equal(r.body.sessions.find((x) => x.id === devine).answers[0].payload, null);
r = await call("POST", "/games/answer", "B", { session: devine, item: "q0", payload: "R1VFU1M" });
assert.equal(r.body.session.answers.find((a) => a.member === "A").payload, "UkVQ");
await call("POST", "/games/answer", "A", { session: devine, item: "~judge0", payload: "b2s" });

// Synchro incrémentale et statut.
const grev = (await call("POST", "/games/state", "A", { since: 0 })).body.rev;
assert.equal((await call("POST", "/games/state", "A", { since: grev })).body.sessions.length, 0);
assert.equal((await call("POST", "/games/session/status", "B", { id: devine, status: "done" })).body.session.status, "done");
assert.equal((await call("POST", "/games/state", "A", { since: grev })).body.sessions.length, 1);

// Réglages de jeu, questions perso.
assert.equal((await call("POST", "/games/settings", "A", { payload: "aW50ZW5zaXRl" })).status, 200);
assert.equal((await call("POST", "/games/settings", "B", { payload: "aW50Qg" })).status, 200);
r = await call("POST", "/games/state", "A", { since: 0 });
assert.equal(r.body.settings.length, 2);
const gq = rnd(16);
assert.equal((await call("POST", "/games/custom", "A", { id: gq, payload: "cXVlc3Rpb24" })).status, 200);
assert.equal((await call("POST", "/games/custom", "B", { id: gq, payload: "aGFjaw" })).status, 403);

// Envies : seules les étiquettes communes sont renvoyées, jamais celles de l'autre.
const tag = () => rnd(24);
const [oo, ayBm, byAm, x1, x2, x3] = [tag(), tag(), tag(), tag(), tag(), tag()];
r = await call("POST", "/games/wishes", "A", { entries: [
  { slot: tag(), t1: oo, t2: ayBm, mine: "b3Vp" },       // oui
  { slot: tag(), t1: x1, t2: x2, mine: "bm9u" },        // non (étiquettes aléatoires)
] });
assert.deepEqual(r.body.matches, []);
r = await call("POST", "/games/wishes", "B", { entries: [
  { slot: tag(), t1: oo, t2: byAm, mine: "b3Vp" },       // oui → match avec le oui de A
  { slot: tag(), t1: x3, t2: tag(), mine: "bm9u" },
] });
assert.deepEqual(r.body.matches, [oo]);
r = await call("POST", "/games/state", "A", { since: 0, wishesSince: 0 });
assert.deepEqual(r.body.wishes.matches, [oo]);
assert.equal(r.body.wishes.mine.length, 2, "chacun ne relit que ses propres envies");
assert.ok(!JSON.stringify(r.body).includes(x3), "les étiquettes de l'autre ne fuient jamais");

// QR de secours : un nouveau téléphone reprend l'emplacement de B ; un emplacement inconnu est refusé.
r = await call("POST", "/pair", "B", { action: "rejoin", fcmToken: "nouveau" });
assert.equal(r.status, 200);
assert.equal(r.body.partnerJoined, true);
assert.equal((await call("POST", "/pair", "B", { action: "rejoin" }, rnd(32))).status, 404);

// Authentification et dissociation.
assert.equal((await call("POST", "/state", "A", { since: 0 }, rnd(32))).status, 401);
assert.equal((await call("POST", "/token/update", "B", { fcmToken: "abc" })).status, 200);
assert.equal((await call("DELETE", "/pair", "A")).status, 200);
assert.equal((await call("POST", "/state", "A", { since: 0 })).status, 401);

console.log("✓ Worker : tous les scénarios passent");

// Miroir — Worker de synchronisation.
// Le Worker ne voit jamais la clé AES du couple : il stocke uniquement des identifiants
// opaques et des charges chiffrées, et ne révèle la réponse de l'autre qu'une fois que
// les deux membres ont répondu.

import { sendPush, type PushType } from "./fcm";

export interface Env {
  DB: D1Database;
  FCM_SERVICE_ACCOUNT?: string;
}

type Member = "A" | "B";

interface Auth {
  pairId: string;
  member: Member;
}

class HttpError extends Error {
  constructor(
    readonly status: number,
    readonly code: string,
    readonly extra: Record<string, unknown> = {},
  ) {
    super(code);
  }
}

const MAX_PAYLOAD = 16 * 1024;
const ID_RE = /^[A-Za-z0-9_-]{16,64}$/;
const DAY_RE = /^\d{4}-\d{2}-\d{2}$/;
const B64_RE = /^[A-Za-z0-9_-]+$/;

// ---------- utilitaires ----------

function json(body: unknown, status = 200): Response {
  return new Response(JSON.stringify(body), {
    status,
    headers: { "content-type": "application/json; charset=utf-8", "cache-control": "no-store" },
  });
}

async function sha256Hex(text: string): Promise<string> {
  const digest = await crypto.subtle.digest("SHA-256", new TextEncoder().encode(text));
  return [...new Uint8Array(digest)].map((b) => b.toString(16).padStart(2, "0")).join("");
}

function safeEqual(a: string, b: string): boolean {
  const ea = new TextEncoder().encode(a);
  const eb = new TextEncoder().encode(b);
  if (ea.byteLength !== eb.byteLength) return false;
  return crypto.subtle.timingSafeEqual(ea, eb);
}

function other(member: Member): Member {
  return member === "A" ? "B" : "A";
}

function cipher(value: unknown, field: string, optional = false): string | null {
  if (value === undefined || value === null) {
    if (optional) return null;
    throw new HttpError(400, "missing_field", { field });
  }
  if (typeof value !== "string" || value.length === 0 || value.length > MAX_PAYLOAD || !B64_RE.test(value)) {
    throw new HttpError(400, "invalid_field", { field });
  }
  return value;
}

function str(value: unknown, field: string, re: RegExp): string {
  if (typeof value !== "string" || !re.test(value)) throw new HttpError(400, "invalid_field", { field });
  return value;
}

/** Les jours acceptés : de J-7 à J+1 (UTC), pour tolérer fuseaux et files hors ligne. */
function checkDay(value: unknown): string {
  const day = str(value, "day", DAY_RE);
  const t = Date.parse(`${day}T12:00:00Z`);
  const now = Date.now();
  if (Number.isNaN(t) || t < now - 8 * 86_400_000 || t > now + 2 * 86_400_000) {
    throw new HttpError(422, "day_out_of_range");
  }
  return day;
}

async function readBody(req: Request): Promise<Record<string, unknown>> {
  const len = Number(req.headers.get("content-length") ?? "0");
  if (len > MAX_PAYLOAD * 4) throw new HttpError(413, "too_large");
  try {
    const body = await req.json();
    if (typeof body !== "object" || body === null || Array.isArray(body)) throw new Error();
    return body as Record<string, unknown>;
  } catch {
    throw new HttpError(400, "invalid_json");
  }
}

function readAuthHeaders(req: Request): { pairId: string; member: Member; secret: string } {
  const pairId = req.headers.get("x-pair-id") ?? "";
  const member = req.headers.get("x-member") ?? "";
  const authz = req.headers.get("authorization") ?? "";
  const secret = authz.startsWith("Bearer ") ? authz.slice(7) : "";
  if (!ID_RE.test(pairId) || (member !== "A" && member !== "B") || secret.length < 32 || !B64_RE.test(secret)) {
    throw new HttpError(401, "unauthorized");
  }
  return { pairId, member, secret };
}

async function authenticate(req: Request, env: Env): Promise<Auth> {
  const { pairId, member, secret } = readAuthHeaders(req);
  const row = await env.DB.prepare("SELECT secret_hash FROM pairs WHERE id = ?").bind(pairId).first<{ secret_hash: string }>();
  if (!row || !safeEqual(row.secret_hash, await sha256Hex(secret))) throw new HttpError(401, "unauthorized");
  const m = await env.DB.prepare("SELECT 1 AS ok FROM members WHERE pair_id = ? AND member = ?").bind(pairId, member).first();
  if (!m) throw new HttpError(401, "unauthorized");
  return { pairId, member };
}

/** Incrément de révision et écriture dans la même transaction D1. */
function bumpRev(env: Env, pairId: string): D1PreparedStatement {
  return env.DB.prepare("UPDATE pairs SET rev = rev + 1 WHERE id = ?").bind(pairId);
}
const REV = "(SELECT rev FROM pairs WHERE id = ?1)";

async function currentRev(env: Env, pairId: string): Promise<number> {
  const r = await env.DB.prepare("SELECT rev FROM pairs WHERE id = ?").bind(pairId).first<{ rev: number }>();
  return r?.rev ?? 0;
}

// ---------- notifications ----------

async function notify(env: Env, pairId: string, to: Member, type: PushType, data: Record<string, string> = {}) {
  const row = await env.DB.prepare("SELECT fcm_token FROM members WHERE pair_id = ? AND member = ?")
    .bind(pairId, to)
    .first<{ fcm_token: string | null }>();
  if (!row?.fcm_token) return;
  try {
    const res = await sendPush(env.FCM_SERVICE_ACCOUNT, row.fcm_token, type, { pairId, ...data });
    if (res.unregistered) {
      await env.DB.prepare("UPDATE members SET fcm_token = NULL WHERE pair_id = ? AND member = ? AND fcm_token = ?")
        .bind(pairId, to, row.fcm_token)
        .run();
    }
  } catch (e) {
    console.error("push", e);
  }
}

// ---------- lecture de l'état ----------

interface DayRow {
  day: string;
  tag: string;
  ref: string;
  skips: number;
  skipped_ref: string | null;
  rev: number;
}

async function loadDays(env: Env, auth: Auth, rows: DayRow[]) {
  if (rows.length === 0) return [];
  const days = rows.map((r) => r.day);
  const placeholders = days.map((_, i) => `?${i + 2}`).join(",");
  const [answers, reactions] = await env.DB.batch([
    env.DB.prepare(`SELECT day, member, tag, payload, created_at FROM answers WHERE pair_id = ?1 AND day IN (${placeholders})`).bind(auth.pairId, ...days),
    env.DB.prepare(`SELECT day, member, payload, updated_at FROM reactions WHERE pair_id = ?1 AND day IN (${placeholders})`).bind(auth.pairId, ...days),
  ]);
  type A = { day: string; member: Member; tag: string; payload: string; created_at: number };
  type R = { day: string; member: Member; payload: string; updated_at: number };
  const ans = (answers?.results ?? []) as A[];
  const rea = (reactions?.results ?? []) as R[];

  return rows.map((r) => {
    const dayAnswers = ans.filter((a) => a.day === r.day && a.tag === r.tag);
    const both = dayAnswers.length === 2;
    const out: Record<string, unknown> = {};
    for (const a of dayAnswers) {
      // La réponse de l'autre n'est transmise qu'après la réponse des deux.
      const visible = both || a.member === auth.member;
      out[a.member] = { answered: true, payload: visible ? a.payload : null, at: a.created_at };
    }
    const reactionsOut: Record<string, unknown> = {};
    if (both) {
      for (const x of rea.filter((x) => x.day === r.day)) reactionsOut[x.member] = { payload: x.payload, at: x.updated_at };
    }
    return {
      day: r.day,
      tag: r.tag,
      ref: r.ref,
      skips: r.skips,
      skippedRef: r.skipped_ref,
      rev: r.rev,
      answers: out,
      reactions: reactionsOut,
    };
  });
}

async function dayDto(env: Env, auth: Auth, day: string) {
  const row = await env.DB.prepare("SELECT day, tag, ref, skips, skipped_ref, rev FROM days WHERE pair_id = ? AND day = ?")
    .bind(auth.pairId, day)
    .first<DayRow>();
  if (!row) return null;
  return (await loadDays(env, auth, [row]))[0] ?? null;
}

async function ensureDay(env: Env, auth: Auth, day: string, tag: string, ref: string) {
  const exists = await env.DB.prepare("SELECT 1 FROM days WHERE pair_id = ? AND day = ?").bind(auth.pairId, day).first();
  if (exists) return;
  await env.DB.batch([
    bumpRev(env, auth.pairId),
    env.DB.prepare(`INSERT OR IGNORE INTO days (pair_id, day, tag, ref, skips, rev) VALUES (?1, ?2, ?3, ?4, 0, ${REV})`).bind(
      auth.pairId,
      day,
      tag,
      ref,
    ),
  ]);
}

// ---------- endpoints ----------

async function handlePair(req: Request, env: Env, ctx: ExecutionContext): Promise<Response> {
  const { pairId, member, secret } = readAuthHeaders(req);
  const body = await readBody(req);
  const action = body.action;
  const profile = cipher(body.profile, "profile", true);
  const fcmToken = typeof body.fcmToken === "string" && body.fcmToken.length < 4096 ? body.fcmToken : null;
  const hash = await sha256Hex(secret);
  const now = Date.now();
  const existing = await env.DB.prepare("SELECT secret_hash FROM pairs WHERE id = ?").bind(pairId).first<{ secret_hash: string }>();

  if (action === "create") {
    if (member !== "A") throw new HttpError(400, "creator_must_be_A");
    if (existing && !safeEqual(existing.secret_hash, hash)) throw new HttpError(409, "pair_exists");
    await env.DB.batch([
      env.DB.prepare("INSERT OR IGNORE INTO pairs (id, secret_hash, rev, created_at) VALUES (?, ?, 1, ?)").bind(pairId, hash, now),
      env.DB.prepare(
        `INSERT INTO members (pair_id, member, profile, fcm_token, joined_at, updated_at) VALUES (?, 'A', ?, ?, ?, ?)
         ON CONFLICT (pair_id, member) DO UPDATE SET profile = COALESCE(excluded.profile, profile),
           fcm_token = COALESCE(excluded.fcm_token, fcm_token), updated_at = excluded.updated_at`,
      ).bind(pairId, profile, fcmToken, now, now),
    ]);
  } else if (action === "join") {
    if (member !== "B") throw new HttpError(400, "joiner_must_be_B");
    if (!existing || !safeEqual(existing.secret_hash, hash)) throw new HttpError(404, "pair_not_found");
    await env.DB.batch([
      bumpRev(env, pairId),
      env.DB.prepare(
        `INSERT INTO members (pair_id, member, profile, fcm_token, joined_at, updated_at) VALUES (?, 'B', ?, ?, ?, ?)
         ON CONFLICT (pair_id, member) DO UPDATE SET profile = COALESCE(excluded.profile, profile),
           fcm_token = COALESCE(excluded.fcm_token, fcm_token), updated_at = excluded.updated_at`,
      ).bind(pairId, profile, fcmToken, now, now),
    ]);
    ctx.waitUntil(notify(env, pairId, "A", "paired"));
  } else {
    throw new HttpError(400, "invalid_action");
  }

  const members = await env.DB.prepare("SELECT member FROM members WHERE pair_id = ?").bind(pairId).all<{ member: Member }>();
  return json({
    pairId,
    member,
    partnerJoined: members.results.some((m) => m.member === other(member)),
    rev: await currentRev(env, pairId),
  });
}

async function handleUnpair(req: Request, env: Env, ctx: ExecutionContext): Promise<Response> {
  const auth = await authenticate(req, env);
  // Notifier avant suppression (le jeton disparaît avec la paire).
  await notify(env, auth.pairId, other(auth.member), "unpaired");
  await env.DB.batch(
    ["answers", "reactions", "days", "custom_questions", "members"].map((t) =>
      env.DB.prepare(`DELETE FROM ${t} WHERE pair_id = ?`).bind(auth.pairId),
    ).concat(env.DB.prepare("DELETE FROM pairs WHERE id = ?").bind(auth.pairId)),
  );
  void ctx;
  return json({ ok: true });
}

async function handleState(req: Request, env: Env): Promise<Response> {
  const auth = await authenticate(req, env);
  const body = await readBody(req);
  const since = typeof body.since === "number" && body.since >= 0 ? Math.floor(body.since) : 0;

  const today = body.today as Record<string, unknown> | undefined | null;
  if (today && typeof today === "object") {
    const day = checkDay(today.day);
    await ensureDay(env, auth, day, str(today.tag, "tag", B64_RE), cipher(today.ref, "ref")!);
  }

  const [pair, members, days, customs] = await env.DB.batch([
    env.DB.prepare("SELECT rev, settings, settings_version FROM pairs WHERE id = ?").bind(auth.pairId),
    env.DB.prepare("SELECT member, profile, joined_at FROM members WHERE pair_id = ?").bind(auth.pairId),
    env.DB.prepare("SELECT day, tag, ref, skips, skipped_ref, rev FROM days WHERE pair_id = ? AND rev > ? ORDER BY day").bind(auth.pairId, since),
    env.DB.prepare("SELECT id, author, payload, deleted, created_at, rev FROM custom_questions WHERE pair_id = ? AND rev > ?").bind(auth.pairId, since),
  ]);
  const p = (pair?.results?.[0] ?? { rev: 0, settings: null, settings_version: 0 }) as {
    rev: number;
    settings: string | null;
    settings_version: number;
  };
  type M = { member: Member; profile: string | null; joined_at: number };
  type C = { id: string; author: Member; payload: string; deleted: number; created_at: number; rev: number };

  return json({
    rev: p.rev,
    serverTime: Date.now(),
    me: auth.member,
    members: ((members?.results ?? []) as M[]).map((m) => ({ member: m.member, profile: m.profile, joinedAt: m.joined_at })),
    settings: { payload: p.settings, version: p.settings_version },
    days: await loadDays(env, auth, (days?.results ?? []) as DayRow[]),
    customQuestions: ((customs?.results ?? []) as C[]).map((c) => ({
      id: c.id,
      author: c.author,
      payload: c.payload,
      deleted: c.deleted === 1,
      createdAt: c.created_at,
      rev: c.rev,
    })),
  });
}

async function handleAnswer(req: Request, env: Env, ctx: ExecutionContext): Promise<Response> {
  const auth = await authenticate(req, env);
  const body = await readBody(req);
  const day = checkDay(body.day);
  const tag = str(body.tag, "tag", B64_RE);
  const ref = cipher(body.ref, "ref")!;
  const payload = cipher(body.payload, "payload")!;

  await ensureDay(env, auth, day, tag, ref);
  const row = await env.DB.prepare("SELECT tag FROM days WHERE pair_id = ? AND day = ?").bind(auth.pairId, day).first<{ tag: string }>();
  if (!row || row.tag !== tag) {
    throw new HttpError(409, "question_changed", { day: await dayDto(env, auth, day) });
  }

  const [, insert] = await env.DB.batch([
    bumpRev(env, auth.pairId),
    env.DB.prepare(
      "INSERT OR IGNORE INTO answers (pair_id, day, member, tag, payload, created_at) VALUES (?, ?, ?, ?, ?, ?)",
    ).bind(auth.pairId, day, auth.member, tag, payload, Date.now()),
    env.DB.prepare(`UPDATE days SET rev = ${REV} WHERE pair_id = ?1 AND day = ?2`).bind(auth.pairId, day),
  ]);
  const inserted = (insert?.meta.changes ?? 0) > 0;
  const count = await env.DB.prepare("SELECT COUNT(*) AS n FROM answers WHERE pair_id = ? AND day = ? AND tag = ?")
    .bind(auth.pairId, day, tag)
    .first<{ n: number }>();

  if (inserted) {
    const partner = other(auth.member);
    ctx.waitUntil(notify(env, auth.pairId, partner, (count?.n ?? 0) >= 2 ? "revealed" : "partner_answered", { day }));
  }
  return json({ rev: await currentRev(env, auth.pairId), day: await dayDto(env, auth, day) });
}

async function handleSkip(req: Request, env: Env, ctx: ExecutionContext): Promise<Response> {
  const auth = await authenticate(req, env);
  const body = await readBody(req);
  const day = checkDay(body.day);
  const tag = str(body.tag, "tag", B64_RE);
  const ref = cipher(body.ref, "ref")!;
  const expected = typeof body.expectedSkips === "number" ? body.expectedSkips : -1;

  const row = await env.DB.prepare("SELECT skips FROM days WHERE pair_id = ? AND day = ?").bind(auth.pairId, day).first<{ skips: number }>();
  if (!row) throw new HttpError(404, "day_not_found");
  const answered = await env.DB.prepare("SELECT COUNT(*) AS n FROM answers WHERE pair_id = ? AND day = ?").bind(auth.pairId, day).first<{ n: number }>();
  if ((answered?.n ?? 0) > 0) throw new HttpError(409, "already_answered", { day: await dayDto(env, auth, day) });
  if (row.skips >= 1 || row.skips !== expected) throw new HttpError(409, "skip_used", { day: await dayDto(env, auth, day) });

  const [, update] = await env.DB.batch([
    bumpRev(env, auth.pairId),
    env.DB.prepare(
      `UPDATE days SET skipped_ref = ref, tag = ?3, ref = ?4, skips = skips + 1, rev = ${REV}
       WHERE pair_id = ?1 AND day = ?2 AND skips = ?5
         AND NOT EXISTS (SELECT 1 FROM answers WHERE pair_id = ?1 AND day = ?2)`,
    ).bind(auth.pairId, day, tag, ref, expected),
  ]);
  if ((update?.meta.changes ?? 0) === 0) throw new HttpError(409, "skip_used", { day: await dayDto(env, auth, day) });
  ctx.waitUntil(notify(env, auth.pairId, other(auth.member), "sync", { day }));
  return json({ rev: await currentRev(env, auth.pairId), day: await dayDto(env, auth, day) });
}

async function handleReact(req: Request, env: Env, ctx: ExecutionContext): Promise<Response> {
  const auth = await authenticate(req, env);
  const body = await readBody(req);
  const day = str(body.day, "day", DAY_RE);
  const payload = cipher(body.payload, "payload")!;

  const n = await env.DB.prepare(
    "SELECT COUNT(*) AS n FROM answers a JOIN days d ON d.pair_id = a.pair_id AND d.day = a.day AND d.tag = a.tag WHERE a.pair_id = ? AND a.day = ?",
  )
    .bind(auth.pairId, day)
    .first<{ n: number }>();
  if ((n?.n ?? 0) < 2) throw new HttpError(409, "not_revealed");

  await env.DB.batch([
    bumpRev(env, auth.pairId),
    env.DB.prepare(
      `INSERT INTO reactions (pair_id, day, member, payload, updated_at) VALUES (?, ?, ?, ?, ?)
       ON CONFLICT (pair_id, day, member) DO UPDATE SET payload = excluded.payload, updated_at = excluded.updated_at`,
    ).bind(auth.pairId, day, auth.member, payload, Date.now()),
    env.DB.prepare(`UPDATE days SET rev = ${REV} WHERE pair_id = ?1 AND day = ?2`).bind(auth.pairId, day),
  ]);
  ctx.waitUntil(notify(env, auth.pairId, other(auth.member), "reaction", { day }));
  return json({ rev: await currentRev(env, auth.pairId), day: await dayDto(env, auth, day) });
}

async function handleCustomQuestion(req: Request, env: Env, ctx: ExecutionContext): Promise<Response> {
  const auth = await authenticate(req, env);
  const body = await readBody(req);
  const id = str(body.id, "id", ID_RE);
  const payload = cipher(body.payload, "payload")!;
  const deleted = body.deleted === true ? 1 : 0;

  const existing = await env.DB.prepare("SELECT author FROM custom_questions WHERE pair_id = ? AND id = ?")
    .bind(auth.pairId, id)
    .first<{ author: Member }>();
  if (existing && existing.author !== auth.member) throw new HttpError(403, "not_author");

  await env.DB.batch([
    bumpRev(env, auth.pairId),
    env.DB.prepare(
      `INSERT INTO custom_questions (pair_id, id, author, payload, deleted, created_at, rev) VALUES (?1, ?2, ?3, ?4, ?5, ?6, ${REV})
       ON CONFLICT (pair_id, id) DO UPDATE SET payload = excluded.payload, deleted = excluded.deleted, rev = excluded.rev`,
    ).bind(auth.pairId, id, auth.member, payload, deleted, Date.now()),
  ]);
  if (!existing && !deleted) ctx.waitUntil(notify(env, auth.pairId, other(auth.member), "custom_question"));
  else ctx.waitUntil(notify(env, auth.pairId, other(auth.member), "sync"));
  return json({ rev: await currentRev(env, auth.pairId) });
}

async function handleSettings(req: Request, env: Env, ctx: ExecutionContext): Promise<Response> {
  const auth = await authenticate(req, env);
  const body = await readBody(req);
  const payload = cipher(body.payload, "payload")!;
  const base = typeof body.baseVersion === "number" ? body.baseVersion : -1;

  const [res] = await env.DB.batch([
    env.DB.prepare(
      "UPDATE pairs SET settings = ?, settings_version = settings_version + 1, rev = rev + 1 WHERE id = ? AND settings_version = ?",
    ).bind(payload, auth.pairId, base),
  ]);
  const row = await env.DB.prepare("SELECT settings, settings_version FROM pairs WHERE id = ?")
    .bind(auth.pairId)
    .first<{ settings: string | null; settings_version: number }>();
  if ((res?.meta.changes ?? 0) === 0) {
    throw new HttpError(409, "settings_conflict", { settings: { payload: row?.settings ?? null, version: row?.settings_version ?? 0 } });
  }
  ctx.waitUntil(notify(env, auth.pairId, other(auth.member), "sync"));
  return json({ rev: await currentRev(env, auth.pairId), settings: { payload: row?.settings, version: row?.settings_version } });
}

async function handleProfile(req: Request, env: Env, ctx: ExecutionContext): Promise<Response> {
  const auth = await authenticate(req, env);
  const body = await readBody(req);
  const profile = cipher(body.profile, "profile")!;
  await env.DB.batch([
    bumpRev(env, auth.pairId),
    env.DB.prepare("UPDATE members SET profile = ?, updated_at = ? WHERE pair_id = ? AND member = ?").bind(
      profile,
      Date.now(),
      auth.pairId,
      auth.member,
    ),
  ]);
  ctx.waitUntil(notify(env, auth.pairId, other(auth.member), "sync"));
  return json({ rev: await currentRev(env, auth.pairId) });
}

async function handleToken(req: Request, env: Env): Promise<Response> {
  const auth = await authenticate(req, env);
  const body = await readBody(req);
  const token = body.fcmToken;
  if (token !== null && (typeof token !== "string" || token.length === 0 || token.length > 4096)) {
    throw new HttpError(400, "invalid_field", { field: "fcmToken" });
  }
  await env.DB.prepare("UPDATE members SET fcm_token = ?, updated_at = ? WHERE pair_id = ? AND member = ?")
    .bind(token, Date.now(), auth.pairId, auth.member)
    .run();
  return json({ ok: true });
}

// ---------- routage ----------

type Handler = (req: Request, env: Env, ctx: ExecutionContext) => Promise<Response>;

const routes: Record<string, Handler> = {
  "POST /pair": handlePair,
  "DELETE /pair": handleUnpair,
  "POST /state": handleState,
  "POST /answer": handleAnswer,
  "POST /skip": handleSkip,
  "POST /react": handleReact,
  "POST /custom-question": handleCustomQuestion,
  "POST /settings": handleSettings,
  "POST /profile": handleProfile,
  "POST /token/update": handleToken,
};

export default {
  async fetch(req: Request, env: Env, ctx: ExecutionContext): Promise<Response> {
    const url = new URL(req.url);
    if (req.method === "GET" && (url.pathname === "/" || url.pathname === "/health")) {
      return json({ ok: true, service: "miroir" });
    }
    const handler = routes[`${req.method} ${url.pathname}`];
    if (!handler) return json({ error: "not_found" }, 404);
    try {
      return await handler(req, env, ctx);
    } catch (e) {
      if (e instanceof HttpError) return json({ error: e.code, ...e.extra }, e.status);
      console.error(e);
      return json({ error: "internal" }, 500);
    }
  },
} satisfies ExportedHandler<Env>;

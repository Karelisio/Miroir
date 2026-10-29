// De l'autre côté — routes des jeux à deux. Même principe que Miroir : le Worker ne stocke que du
// chiffré et des identifiants opaques, et décide seulement de ce que chacun a le droit de voir.

import {
  authenticate, bumpRev, cipher, currentRev, HttpError, ID_RE, json, notify, other, readBody, REV, str,
  type Auth, type Env, type Member,
} from "./common";

/** Tables à vider à la dissociation du couple. */
export const GAME_TABLES = ["game_answers", "game_sessions", "game_members", "game_wishes", "game_custom"];

const KINDS = new Set(["devine", "duel", "preferes", "av", "surprise", "defi", "canape"]);
const REVEALS = new Set(["item", "all", "open"]);
const STATUSES = new Set(["open", "done", "cancelled"]);
const ITEM_RE = /^[A-Za-z0-9_$~.-]{1,40}$/;
const TAG_RE = /^[A-Za-z0-9_-]{16,64}$/;
const DONE = "$done";
const MAX_WISHES = 100;

/** Événement neutre envoyé à l'autre : l'appli affiche « Nouveau défi », « C'est ton tour »… jamais le contenu. */
type GameEvent = "challenge" | "turn" | "reveal" | "match" | "surprise";

const pushGame = (env: Env, auth: Auth, event: GameEvent, session?: string) =>
  notify(env, auth.pairId, other(auth.member), "game", session ? { event, session } : { event });

// ---------- lecture ----------

interface SessionRow {
  id: string;
  kind: string;
  reveal: string;
  created_by: Member;
  payload: string;
  status: string;
  created_at: number;
  updated_at: number;
  rev: number;
}
interface AnswerRow {
  session_id: string;
  member: Member;
  item: string;
  payload: string;
  created_at: number;
}

/** Règles de révélation : la réponse de l'autre n'est transmise que lorsqu'elle peut l'être. */
function visibleAnswers(auth: Auth, session: SessionRow, rows: AnswerRow[]) {
  const mine = new Set(rows.filter((r) => r.member === auth.member).map((r) => r.item));
  const theirs = new Set(rows.filter((r) => r.member !== auth.member).map((r) => r.item));
  const bothDone = mine.has(DONE) && theirs.has(DONE);
  return rows.map((r) => {
    const own = r.member === auth.member;
    const public_ = r.item === DONE || r.item.startsWith("~") || session.reveal === "open";
    const visible = own || public_ || (session.reveal === "item" ? mine.has(r.item) : bothDone);
    return { member: r.member, item: r.item, payload: visible ? r.payload : null, at: r.created_at };
  });
}

function toDto(auth: Auth, rows: SessionRow[], answers: AnswerRow[]) {
  return rows.map((s) => ({
    id: s.id,
    kind: s.kind,
    reveal: s.reveal,
    createdBy: s.created_by,
    payload: s.payload,
    status: s.status,
    createdAt: s.created_at,
    updatedAt: s.updated_at,
    rev: s.rev,
    answers: visibleAnswers(auth, s, answers.filter((a) => a.session_id === s.id)),
  }));
}

/** Parties modifiées depuis [since], avec leurs réponses visibles. Sous-requête plutôt qu'une liste
 * d'identifiants (limite de paramètres liés de D1). */
async function sessionsSince(env: Env, auth: Auth, since: number) {
  const [sessions, answers] = await env.DB.batch([
    env.DB.prepare("SELECT * FROM game_sessions WHERE pair_id = ? AND rev > ? ORDER BY created_at").bind(auth.pairId, since),
    env.DB.prepare(
      `SELECT session_id, member, item, payload, created_at FROM game_answers WHERE pair_id = ?1
       AND session_id IN (SELECT id FROM game_sessions WHERE pair_id = ?1 AND rev > ?2)`,
    ).bind(auth.pairId, since),
  ]);
  return toDto(auth, (sessions?.results ?? []) as SessionRow[], (answers?.results ?? []) as AnswerRow[]);
}

async function sessionDto(env: Env, auth: Auth, id: string) {
  const [session, answers] = await env.DB.batch([
    env.DB.prepare("SELECT * FROM game_sessions WHERE pair_id = ? AND id = ?").bind(auth.pairId, id),
    env.DB.prepare("SELECT session_id, member, item, payload, created_at FROM game_answers WHERE pair_id = ? AND session_id = ?").bind(auth.pairId, id),
  ]);
  return toDto(auth, (session?.results ?? []) as SessionRow[], (answers?.results ?? []) as AnswerRow[])[0] ?? null;
}

/** Étiquettes d'envies communes aux deux membres (le reste n'est jamais renvoyé). */
async function wishMatches(env: Env, auth: Auth): Promise<string[]> {
  const rows = await env.DB.prepare(
    `SELECT DISTINCT m.t FROM (
       SELECT t1 AS t FROM game_wishes WHERE pair_id = ?1 AND member = ?2
       UNION ALL SELECT t2 FROM game_wishes WHERE pair_id = ?1 AND member = ?2) m
     WHERE m.t IN (SELECT t1 FROM game_wishes WHERE pair_id = ?1 AND member = ?3
                   UNION SELECT t2 FROM game_wishes WHERE pair_id = ?1 AND member = ?3)`,
  )
    .bind(auth.pairId, auth.member, other(auth.member))
    .all<{ t: string }>();
  return rows.results.map((r) => r.t);
}

async function handleGameState(req: Request, env: Env): Promise<Response> {
  const auth = await authenticate(req, env);
  const body = await readBody(req);
  const since = typeof body.since === "number" && body.since >= 0 ? Math.floor(body.since) : 0;
  const wishesSince = typeof body.wishesSince === "number" && body.wishesSince >= 0 ? Math.floor(body.wishesSince) : 0;

  const [members, customs, wishes] = await env.DB.batch([
    env.DB.prepare("SELECT member, settings, updated_at FROM game_members WHERE pair_id = ? AND rev > ?").bind(auth.pairId, since),
    env.DB.prepare("SELECT id, author, payload, deleted, created_at, rev FROM game_custom WHERE pair_id = ? AND rev > ?").bind(auth.pairId, since),
    env.DB.prepare("SELECT slot, mine, rev FROM game_wishes WHERE pair_id = ? AND member = ? AND rev > ?").bind(auth.pairId, auth.member, wishesSince),
  ]);
  type C = { id: string; author: Member; payload: string; deleted: number; created_at: number; rev: number };
  type W = { slot: string; mine: string; rev: number };
  type S = { member: Member; settings: string; updated_at: number };
  const rev = await currentRev(env, auth.pairId);
  return json({
    rev,
    sessions: await sessionsSince(env, auth, since),
    settings: ((members?.results ?? []) as S[]).map((m) => ({ member: m.member, payload: m.settings, at: m.updated_at })),
    custom: ((customs?.results ?? []) as C[]).map((c) => ({
      id: c.id, author: c.author, payload: c.payload, deleted: c.deleted === 1, createdAt: c.created_at, rev: c.rev,
    })),
    wishes: {
      mine: ((wishes?.results ?? []) as W[]).map((w) => ({ slot: w.slot, payload: w.mine })),
      matches: await wishMatches(env, auth),
      rev,
    },
  });
}

// ---------- écriture ----------

/** Création idempotente : si la partie existe déjà (ex. défi de la semaine), on renvoie l'existante. */
async function handleCreateSession(req: Request, env: Env, ctx: ExecutionContext): Promise<Response> {
  const auth = await authenticate(req, env);
  const body = await readBody(req);
  const id = str(body.id, "id", ID_RE);
  const kind = str(body.kind, "kind", /^[a-z]{2,12}$/);
  const reveal = str(body.reveal, "reveal", /^[a-z]{3,4}$/);
  if (!KINDS.has(kind)) throw new HttpError(400, "invalid_field", { field: "kind" });
  if (!REVEALS.has(reveal)) throw new HttpError(400, "invalid_field", { field: "reveal" });
  const payload = cipher(body.payload, "payload")!;
  const status = body.status === "done" ? "done" : "open";
  const now = Date.now();
  const [, insert] = await env.DB.batch([
    bumpRev(env, auth.pairId),
    env.DB.prepare(
      `INSERT OR IGNORE INTO game_sessions (pair_id, id, kind, reveal, created_by, payload, status, created_at, updated_at, rev)
       VALUES (?1, ?2, ?3, ?4, ?5, ?6, ?7, ?8, ?8, ${REV})`,
    ).bind(auth.pairId, id, kind, reveal, auth.member, payload, status, now),
  ]);
  const created = (insert?.meta.changes ?? 0) > 0;
  if (created && body.notify === true) ctx.waitUntil(pushGame(env, auth, kind === "surprise" ? "surprise" : "challenge", id));
  return json({ rev: await currentRev(env, auth.pairId), created, session: await sessionDto(env, auth, id) });
}

async function handleGameAnswer(req: Request, env: Env, ctx: ExecutionContext): Promise<Response> {
  const auth = await authenticate(req, env);
  const body = await readBody(req);
  const id = str(body.session, "session", ID_RE);
  const item = str(body.item, "item", ITEM_RE);
  const payload = cipher(body.payload, "payload")!;
  const session = await env.DB.prepare("SELECT reveal, status FROM game_sessions WHERE pair_id = ? AND id = ?")
    .bind(auth.pairId, id)
    .first<{ reveal: string; status: string }>();
  if (!session) throw new HttpError(404, "session_not_found");
  if (session.status === "cancelled") throw new HttpError(409, "session_cancelled");

  // Une réponse donnée ne change plus (sinon on pourrait « corriger » après avoir vu l'autre),
  // sauf dans les parties ouvertes (action ou vérité) et pour les éléments publics « ~ ».
  const editable = session.reveal === "open" || item.startsWith("~");
  const [, insert] = await env.DB.batch([
    bumpRev(env, auth.pairId),
    env.DB.prepare(
      `INSERT INTO game_answers (pair_id, session_id, member, item, payload, created_at) VALUES (?1, ?2, ?3, ?4, ?5, ?6)
       ON CONFLICT (pair_id, session_id, member, item) DO UPDATE SET payload = excluded.payload, created_at = excluded.created_at
       WHERE ?7 = 1 AND game_answers.payload <> excluded.payload`,
    ).bind(auth.pairId, id, auth.member, item, payload, Date.now(), editable ? 1 : 0),
    env.DB.prepare(`UPDATE game_sessions SET rev = ${REV}, updated_at = ?3 WHERE pair_id = ?1 AND id = ?2`).bind(auth.pairId, id, Date.now()),
  ]);
  const changed = (insert?.meta.changes ?? 0) > 0;
  if (changed && body.notify === true) {
    let event: GameEvent = "turn";
    if (item === DONE) {
      const partnerDone = await env.DB.prepare("SELECT 1 AS ok FROM game_answers WHERE pair_id = ? AND session_id = ? AND member = ? AND item = ?")
        .bind(auth.pairId, id, other(auth.member), DONE)
        .first();
      if (partnerDone) event = "reveal";
    }
    ctx.waitUntil(pushGame(env, auth, event, id));
  }
  return json({ rev: await currentRev(env, auth.pairId), session: await sessionDto(env, auth, id) });
}

async function handleSessionStatus(req: Request, env: Env, ctx: ExecutionContext): Promise<Response> {
  const auth = await authenticate(req, env);
  const body = await readBody(req);
  const id = str(body.id, "id", ID_RE);
  const status = str(body.status, "status", /^[a-z]{4,9}$/);
  if (!STATUSES.has(status)) throw new HttpError(400, "invalid_field", { field: "status" });
  const [, update] = await env.DB.batch([
    bumpRev(env, auth.pairId),
    env.DB.prepare(`UPDATE game_sessions SET status = ?3, updated_at = ?4, rev = ${REV} WHERE pair_id = ?1 AND id = ?2`).bind(
      auth.pairId, id, status, Date.now(),
    ),
  ]);
  if ((update?.meta.changes ?? 0) === 0) throw new HttpError(404, "session_not_found");
  ctx.waitUntil(notify(env, auth.pairId, other(auth.member), "sync"));
  return json({ rev: await currentRev(env, auth.pairId), session: await sessionDto(env, auth, id) });
}

async function handleGameSettings(req: Request, env: Env, ctx: ExecutionContext): Promise<Response> {
  const auth = await authenticate(req, env);
  const body = await readBody(req);
  const payload = cipher(body.payload, "payload")!;
  await env.DB.batch([
    bumpRev(env, auth.pairId),
    env.DB.prepare(
      `INSERT INTO game_members (pair_id, member, settings, updated_at, rev) VALUES (?1, ?2, ?3, ?4, ${REV})
       ON CONFLICT (pair_id, member) DO UPDATE SET settings = excluded.settings, updated_at = excluded.updated_at, rev = excluded.rev`,
    ).bind(auth.pairId, auth.member, payload, Date.now()),
  ]);
  ctx.waitUntil(notify(env, auth.pairId, other(auth.member), "sync"));
  return json({ rev: await currentRev(env, auth.pairId) });
}

async function handleGameCustom(req: Request, env: Env, ctx: ExecutionContext): Promise<Response> {
  const auth = await authenticate(req, env);
  const body = await readBody(req);
  const id = str(body.id, "id", ID_RE);
  const payload = cipher(body.payload, "payload")!;
  const deleted = body.deleted === true ? 1 : 0;
  const existing = await env.DB.prepare("SELECT author FROM game_custom WHERE pair_id = ? AND id = ?").bind(auth.pairId, id).first<{ author: Member }>();
  if (existing && existing.author !== auth.member) throw new HttpError(403, "not_author");
  await env.DB.batch([
    bumpRev(env, auth.pairId),
    env.DB.prepare(
      `INSERT INTO game_custom (pair_id, id, author, payload, deleted, created_at, rev) VALUES (?1, ?2, ?3, ?4, ?5, ?6, ${REV})
       ON CONFLICT (pair_id, id) DO UPDATE SET payload = excluded.payload, deleted = excluded.deleted, rev = excluded.rev`,
    ).bind(auth.pairId, id, auth.member, payload, deleted, Date.now()),
  ]);
  ctx.waitUntil(notify(env, auth.pairId, other(auth.member), "sync"));
  return json({ rev: await currentRev(env, auth.pairId) });
}

/**
 * Envies : chaque entrée porte deux étiquettes aveugles. Le client les calcule de sorte que seules
 * les combinaisons oui+oui et oui+peut-être coïncident ; un « non » envoie des étiquettes aléatoires.
 */
async function handleWishes(req: Request, env: Env, ctx: ExecutionContext): Promise<Response> {
  const auth = await authenticate(req, env);
  const body = await readBody(req);
  const entries = body.entries;
  if (!Array.isArray(entries) || entries.length === 0 || entries.length > MAX_WISHES) throw new HttpError(400, "invalid_field", { field: "entries" });
  const now = Date.now();
  const rows = entries.map((e, i) => {
    if (typeof e !== "object" || e === null) throw new HttpError(400, "invalid_field", { field: `entries[${i}]` });
    const x = e as Record<string, unknown>;
    return {
      slot: str(x.slot, "slot", TAG_RE),
      t1: str(x.t1, "t1", TAG_RE),
      t2: str(x.t2, "t2", TAG_RE),
      mine: cipher(x.mine, "mine")!,
    };
  });
  const before = new Set(await wishMatches(env, auth));
  await env.DB.batch([
    bumpRev(env, auth.pairId),
    ...rows.map((r) =>
      env.DB.prepare(
        `INSERT INTO game_wishes (pair_id, member, slot, t1, t2, mine, updated_at, rev) VALUES (?1, ?2, ?3, ?4, ?5, ?6, ?7, ${REV})
         ON CONFLICT (pair_id, member, slot) DO UPDATE SET t1 = excluded.t1, t2 = excluded.t2, mine = excluded.mine,
           updated_at = excluded.updated_at, rev = excluded.rev`,
      ).bind(auth.pairId, auth.member, r.slot, r.t1, r.t2, r.mine, now),
    ),
  ]);
  const matches = await wishMatches(env, auth);
  if (matches.some((t) => !before.has(t))) ctx.waitUntil(pushGame(env, auth, "match"));
  return json({ rev: await currentRev(env, auth.pairId), matches });
}

export const gameRoutes: Record<string, (req: Request, env: Env, ctx: ExecutionContext) => Promise<Response>> = {
  "POST /games/state": handleGameState,
  "POST /games/session": handleCreateSession,
  "POST /games/session/status": handleSessionStatus,
  "POST /games/answer": handleGameAnswer,
  "POST /games/settings": handleGameSettings,
  "POST /games/custom": handleGameCustom,
  "POST /games/wishes": handleWishes,
};


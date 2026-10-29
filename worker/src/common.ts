// Utilitaires partagés par les routes du Worker (authentification, validation, révisions, push).

import { sendPush, type PushType } from "./fcm";

export interface Env {
  DB: D1Database;
  FCM_SERVICE_ACCOUNT?: string;
}

export type Member = "A" | "B";

export interface Auth {
  pairId: string;
  member: Member;
}

export class HttpError extends Error {
  constructor(
    readonly status: number,
    readonly code: string,
    readonly extra: Record<string, unknown> = {},
  ) {
    super(code);
  }
}

export const MAX_PAYLOAD = 16 * 1024;
export const ID_RE = /^[A-Za-z0-9_-]{16,64}$/;
export const DAY_RE = /^\d{4}-\d{2}-\d{2}$/;
export const B64_RE = /^[A-Za-z0-9_-]+$/;

// ---------- utilitaires ----------

export function json(body: unknown, status = 200): Response {
  return new Response(JSON.stringify(body), {
    status,
    headers: { "content-type": "application/json; charset=utf-8", "cache-control": "no-store" },
  });
}

export async function sha256Hex(text: string): Promise<string> {
  const digest = await crypto.subtle.digest("SHA-256", new TextEncoder().encode(text));
  return [...new Uint8Array(digest)].map((b) => b.toString(16).padStart(2, "0")).join("");
}

export function safeEqual(a: string, b: string): boolean {
  const ea = new TextEncoder().encode(a);
  const eb = new TextEncoder().encode(b);
  if (ea.byteLength !== eb.byteLength) return false;
  return crypto.subtle.timingSafeEqual(ea, eb);
}

export function other(member: Member): Member {
  return member === "A" ? "B" : "A";
}

export function cipher(value: unknown, field: string, optional = false): string | null {
  if (value === undefined || value === null) {
    if (optional) return null;
    throw new HttpError(400, "missing_field", { field });
  }
  if (typeof value !== "string" || value.length === 0 || value.length > MAX_PAYLOAD || !B64_RE.test(value)) {
    throw new HttpError(400, "invalid_field", { field });
  }
  return value;
}

export function str(value: unknown, field: string, re: RegExp): string {
  if (typeof value !== "string" || !re.test(value)) throw new HttpError(400, "invalid_field", { field });
  return value;
}

/** Les jours acceptés : de J-7 à J+1 (UTC), pour tolérer fuseaux et files hors ligne. */
export function checkDay(value: unknown): string {
  const day = str(value, "day", DAY_RE);
  const t = Date.parse(`${day}T12:00:00Z`);
  const now = Date.now();
  if (Number.isNaN(t) || t < now - 8 * 86_400_000 || t > now + 2 * 86_400_000) {
    throw new HttpError(422, "day_out_of_range");
  }
  return day;
}

export async function readBody(req: Request): Promise<Record<string, unknown>> {
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

export function readAuthHeaders(req: Request): { pairId: string; member: Member; secret: string } {
  const pairId = req.headers.get("x-pair-id") ?? "";
  const member = req.headers.get("x-member") ?? "";
  const authz = req.headers.get("authorization") ?? "";
  const secret = authz.startsWith("Bearer ") ? authz.slice(7) : "";
  if (!ID_RE.test(pairId) || (member !== "A" && member !== "B") || secret.length < 32 || !B64_RE.test(secret)) {
    throw new HttpError(401, "unauthorized");
  }
  return { pairId, member, secret };
}

export async function authenticate(req: Request, env: Env): Promise<Auth> {
  const { pairId, member, secret } = readAuthHeaders(req);
  const row = await env.DB.prepare("SELECT secret_hash FROM pairs WHERE id = ?").bind(pairId).first<{ secret_hash: string }>();
  if (!row || !safeEqual(row.secret_hash, await sha256Hex(secret))) throw new HttpError(401, "unauthorized");
  const m = await env.DB.prepare("SELECT 1 AS ok FROM members WHERE pair_id = ? AND member = ?").bind(pairId, member).first();
  if (!m) throw new HttpError(401, "unauthorized");
  return { pairId, member };
}

/** Incrément de révision et écriture dans la même transaction D1. */
export function bumpRev(env: Env, pairId: string): D1PreparedStatement {
  return env.DB.prepare("UPDATE pairs SET rev = rev + 1 WHERE id = ?").bind(pairId);
}
export const REV = "(SELECT rev FROM pairs WHERE id = ?1)";

export async function currentRev(env: Env, pairId: string): Promise<number> {
  const r = await env.DB.prepare("SELECT rev FROM pairs WHERE id = ?").bind(pairId).first<{ rev: number }>();
  return r?.rev ?? 0;
}

// ---------- notifications ----------

export async function notify(env: Env, pairId: string, to: Member, type: PushType, data: Record<string, string> = {}) {
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

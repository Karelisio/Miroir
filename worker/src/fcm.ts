// Envoi de messages FCM via l'API HTTP v1, authentifiée par le compte de service Firebase
// (secret Worker FCM_SERVICE_ACCOUNT). Le jeton OAuth2 est mis en cache dans l'isolat.

interface ServiceAccount {
  project_id: string;
  client_email: string;
  private_key: string;
  token_uri?: string;
}

export type PushType =
  | "paired"
  | "unpaired"
  | "partner_answered"
  | "revealed"
  | "reaction"
  | "custom_question"
  | "sync";

export interface PushResult {
  ok: boolean;
  unregistered: boolean;
}

const SCOPE = "https://www.googleapis.com/auth/firebase.messaging";
const DEFAULT_TOKEN_URI = "https://oauth2.googleapis.com/token";

let cachedToken: { value: string; expiresAt: number; email: string } | null = null;

function base64Url(bytes: ArrayBuffer | Uint8Array): string {
  const arr = bytes instanceof Uint8Array ? bytes : new Uint8Array(bytes);
  let bin = "";
  for (const b of arr) bin += String.fromCharCode(b);
  return btoa(bin).replace(/\+/g, "-").replace(/\//g, "_").replace(/=+$/, "");
}

function pemToDer(pem: string): ArrayBuffer {
  const b64 = pem
    .replace(/-----BEGIN [^-]+-----/, "")
    .replace(/-----END [^-]+-----/, "")
    .replace(/\\n/g, "")
    .replace(/\s+/g, "");
  const bin = atob(b64);
  const out = new Uint8Array(bin.length);
  for (let i = 0; i < bin.length; i++) out[i] = bin.charCodeAt(i);
  return out.buffer;
}

function parseServiceAccount(raw: string | undefined): ServiceAccount | null {
  if (!raw) return null;
  try {
    const sa = JSON.parse(raw) as ServiceAccount;
    if (!sa.project_id || !sa.client_email || !sa.private_key) return null;
    return sa;
  } catch {
    return null;
  }
}

async function accessToken(sa: ServiceAccount): Promise<string> {
  const now = Math.floor(Date.now() / 1000);
  if (cachedToken && cachedToken.email === sa.client_email && cachedToken.expiresAt - 60 > now) {
    return cachedToken.value;
  }
  const tokenUri = sa.token_uri ?? DEFAULT_TOKEN_URI;
  const enc = new TextEncoder();
  const header = base64Url(enc.encode(JSON.stringify({ alg: "RS256", typ: "JWT" })));
  const claims = base64Url(
    enc.encode(
      JSON.stringify({ iss: sa.client_email, scope: SCOPE, aud: tokenUri, iat: now, exp: now + 3600 }),
    ),
  );
  const key = await crypto.subtle.importKey(
    "pkcs8",
    pemToDer(sa.private_key),
    { name: "RSASSA-PKCS1-v1_5", hash: "SHA-256" },
    false,
    ["sign"],
  );
  const signature = await crypto.subtle.sign("RSASSA-PKCS1-v1_5", key, enc.encode(`${header}.${claims}`));
  const jwt = `${header}.${claims}.${base64Url(signature)}`;

  const res = await fetch(tokenUri, {
    method: "POST",
    headers: { "content-type": "application/x-www-form-urlencoded" },
    body: new URLSearchParams({ grant_type: "urn:ietf:params:oauth:grant-type:jwt-bearer", assertion: jwt }),
  });
  if (!res.ok) throw new Error(`OAuth2 ${res.status}: ${await res.text()}`);
  const body = (await res.json()) as { access_token: string; expires_in: number };
  cachedToken = { value: body.access_token, expiresAt: now + body.expires_in, email: sa.client_email };
  return body.access_token;
}

/** Envoie un message "data" (l'appli construit elle-même la notification). */
export async function sendPush(
  rawServiceAccount: string | undefined,
  fcmToken: string,
  type: PushType,
  data: Record<string, string>,
): Promise<PushResult> {
  const sa = parseServiceAccount(rawServiceAccount);
  if (!sa) {
    console.warn("FCM_SERVICE_ACCOUNT absent ou invalide : notification ignorée");
    return { ok: false, unregistered: false };
  }
  const token = await accessToken(sa);
  const urgent = type !== "sync" && type !== "custom_question";
  const res = await fetch(`https://fcm.googleapis.com/v1/projects/${sa.project_id}/messages:send`, {
    method: "POST",
    headers: { authorization: `Bearer ${token}`, "content-type": "application/json" },
    body: JSON.stringify({
      message: {
        token: fcmToken,
        data: { ...data, type },
        android: { priority: urgent ? "high" : "normal", ttl: "86400s" },
      },
    }),
  });
  if (res.ok) return { ok: true, unregistered: false };
  const text = await res.text();
  const unregistered = res.status === 404 || text.includes("UNREGISTERED");
  console.warn(`FCM ${res.status}: ${text.slice(0, 300)}`);
  return { ok: false, unregistered };
}

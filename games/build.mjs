// Assemble la banque « De l'autre côté » (games/src/*.jsonl) dans l'appli.
// Usage : node games/build.mjs   →   app/src/main/assets/games.json
import { readdirSync, writeFileSync, mkdirSync } from "node:fs";
import { join, dirname } from "node:path";
import { fileURLToPath } from "node:url";
import { createHash } from "node:crypto";
import { validateFile } from "./validate.mjs";

const here = dirname(fileURLToPath(import.meta.url));
const src = join(here, "src");
const out = join(here, "..", "app", "src", "main", "assets", "games.json");

// Typographie française : apostrophe courbe et espace fine insécable avant ? ! ; :
const NNBSP = " ";
const typo = (s) => s.trim().replace(/'/g, "’").replace(/\s+/g, " ").replace(/([^\s\d])\s*([?!;:])(?=\s|$|[?!»)])/g, `$1${NNBSP}$2`);
const norm = (s) => s.toLowerCase().normalize("NFD").replace(/[̀-ͯ]/g, "").replace(/[^a-z0-9]/g, "");

const files = readdirSync(src).filter((f) => f.endsWith(".jsonl")).sort().map((f) => join(src, f));
const seen = new Map();
const items = [];
let errors = 0;
for (const file of files) {
  const { items: entries, errors: errs } = validateFile(file);
  errs.forEach((e) => console.error("✗ " + e));
  errors += errs.length;
  for (const q of entries) {
    const n = `${q.mode}:${norm(q.text)}:${(q.options ?? []).map(norm).join("|")}`;
    if (seen.has(n)) { console.warn(`doublon ignoré : ${q.id} ≈ ${seen.get(n)}`); continue; }
    seen.set(n, q.id);
    const item = { id: q.id, c: q.category, m: q.mode, i: q.intensity, t: typo(q.text) };
    if (q.format) item.f = q.format;
    if (q.options) item.o = q.options.map(typo);
    if (q.min) item.lo = typo(q.min);
    if (q.max) item.hi = typo(q.max);
    if (q.remote) item.r = true;
    items.push(item);
  }
}
if (errors) { console.error(`${errors} erreur(s) : banque non générée.`); process.exit(1); }
items.sort((a, b) => a.id.localeCompare(b.id));
const body = JSON.stringify(items);
const version = createHash("sha256").update(body).digest("hex").slice(0, 12);
mkdirSync(dirname(out), { recursive: true });
writeFileSync(out, `{"version":"${version}","items":${body}}\n`);
const stats = {};
for (const q of items) { stats[q.c] ??= {}; stats[q.c][q.m] = (stats[q.c][q.m] ?? 0) + 1; }
console.log(`${items.length} entrées → ${out} (version ${version})`);
for (const [c, s] of Object.entries(stats)) console.log(`  ${c}: ${Object.values(s).reduce((a, b) => a + b, 0)} ${JSON.stringify(s)}`);

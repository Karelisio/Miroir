// Validates question source files (JSONL). Usage: node questions/validate.mjs <file.jsonl>...
import { readFileSync } from "node:fs";
import { basename } from "node:path";

const TYPES = new Set(["text", "choice", "scale", "who"]);
const CATEGORIES = new Set([
  "fun", "souvenirs", "profond", "futur", "nous_deux", "tu_preferes", "qui_de_nous",
  "hypothetique", "valeurs", "gouts", "enfance", "voyages", "quotidien",
]);
const PACKS = new Set(["noel", "nouvel_an", "saint_valentin", "ete", "anniversaire_couple", "anniversaire"]);

export function validateFile(file) {
  const key = basename(file, ".jsonl");
  const isPack = PACKS.has(key);
  if (!isPack && !CATEGORIES.has(key)) throw new Error(`${file}: nom de fichier inconnu`);
  const errors = [];
  const items = [];
  const ids = new Set();
  const texts = new Set();
  readFileSync(file, "utf8").split("\n").forEach((line, i) => {
    if (!line.trim()) return;
    const where = `${file}:${i + 1}`;
    let q;
    try { q = JSON.parse(line); } catch (e) { errors.push(`${where}: JSON invalide (${e.message})`); return; }
    if (!new RegExp(`^${key}-\\d{3}$`).test(q.id ?? "")) errors.push(`${where}: id doit être ${key}-NNN`);
    if (ids.has(q.id)) errors.push(`${where}: id dupliqué ${q.id}`);
    ids.add(q.id);
    if (!TYPES.has(q.type)) errors.push(`${where}: type invalide`);
    if (typeof q.text !== "string" || q.text.length < 8 || q.text.length > 160) errors.push(`${where}: text 8..160 caractères`);
    const norm = (q.text ?? "").toLowerCase().replace(/[^a-zà-ÿ0-9]/g, "");
    if (texts.has(norm)) errors.push(`${where}: texte dupliqué`);
    texts.add(norm);
    if (!Array.isArray(q.tags) || q.tags.length < 1 || q.tags.length > 4 || q.tags.some(t => typeof t !== "string" || !t)) errors.push(`${where}: tags 1..4`);
    if (q.type === "choice") {
      if (!Array.isArray(q.options) || q.options.length < 2 || q.options.length > 4) errors.push(`${where}: options 2..4`);
      else if (q.options.some(o => typeof o !== "string" || !o || o.length > 48)) errors.push(`${where}: option vide ou > 48 caractères`);
    } else if (q.options !== undefined) errors.push(`${where}: options seulement pour choice`);
    if (q.type === "scale") {
      for (const k of ["minLabel", "maxLabel"]) if (typeof q[k] !== "string" || !q[k] || q[k].length > 28) errors.push(`${where}: ${k} requis (≤ 28 car.)`);
    } else if (q.minLabel !== undefined || q.maxLabel !== undefined) errors.push(`${where}: minLabel/maxLabel seulement pour scale`);
    if (isPack) {
      if (!CATEGORIES.has(q.category)) errors.push(`${where}: category requise pour un pack`);
    } else if (q.category !== undefined) errors.push(`${where}: pas de category hors pack`);
    if (key === "tu_preferes" && q.type !== "choice") errors.push(`${where}: tu_preferes => choice`);
    if (key === "qui_de_nous" && q.type !== "who") errors.push(`${where}: qui_de_nous => who`);
    items.push(q);
  });
  return { key, isPack, items, errors };
}

if (process.argv[1] && process.argv[1].endsWith("validate.mjs")) {
  let failed = false;
  for (const f of process.argv.slice(2)) {
    const { items, errors } = validateFile(f);
    const byType = items.reduce((m, q) => ((m[q.type] = (m[q.type] ?? 0) + 1), m), {});
    console.log(`${f}: ${items.length} questions ${JSON.stringify(byType)}`);
    errors.slice(0, 30).forEach(e => console.log("  ✗ " + e));
    if (errors.length) failed = true;
  }
  process.exit(failed ? 1 : 0);
}

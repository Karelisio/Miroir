// Assemble la banque de questions (questions/src/**.jsonl) en un seul JSON embarqué dans l'appli.
// Usage : node questions/build.mjs   →   app/src/main/assets/questions.json
import { readdirSync, writeFileSync, mkdirSync } from "node:fs";
import { join, dirname } from "node:path";
import { fileURLToPath } from "node:url";
import { createHash } from "node:crypto";
import { validateFile } from "./validate.mjs";

const here = dirname(fileURLToPath(import.meta.url));
const src = join(here, "src");
const out = join(here, "..", "app", "src", "main", "assets", "questions.json");

// Typographie française : apostrophe courbe et espace fine insécable avant ? ! ; :
const NNBSP = " ";
function typo(s) {
  return s
    .trim()
    .replace(/'/g, "’")
    .replace(/\s+/g, " ")
    .replace(/([^\s\d])\s*([?!;:])(?=\s|$|[?!»)])/g, `$1${NNBSP}$2`)
    .replace(/«\s*/g, `«${NNBSP}`)
    .replace(/\s*»/g, `${NNBSP}»`);
}
const norm = (s) => s.toLowerCase().normalize("NFD").replace(/[̀-ͯ]/g, "").replace(/[^a-z0-9]/g, "");

const files = [
  ...readdirSync(src).filter((f) => f.endsWith(".jsonl")).sort().map((f) => join(src, f)),
  ...readdirSync(join(src, "packs")).filter((f) => f.endsWith(".jsonl")).sort().map((f) => join(src, "packs", f)),
];

const seen = new Map();
const questions = [];
let errors = 0;
let dropped = 0;
for (const file of files) {
  const { key, isPack, items, errors: errs } = validateFile(file);
  errs.forEach((e) => console.error("✗ " + e));
  errors += errs.length;
  for (const q of items) {
    const n = norm(q.text);
    if (seen.has(n)) {
      console.warn(`doublon ignoré : ${q.id} ≈ ${seen.get(n)}`);
      dropped++;
      continue;
    }
    seen.set(n, q.id);
    const item = {
      id: q.id,
      category: isPack ? q.category : key,
      type: q.type,
      text: typo(q.text),
      tags: [...new Set(q.tags.map((t) => t.trim().toLowerCase()))],
    };
    if (q.options) item.options = q.options.map(typo);
    if (q.minLabel) item.minLabel = typo(q.minLabel);
    if (q.maxLabel) item.maxLabel = typo(q.maxLabel);
    if (isPack) item.pack = key;
    questions.push(item);
  }
}
if (errors) {
  console.error(`${errors} erreur(s) : banque non générée.`);
  process.exit(1);
}

const body = JSON.stringify(questions);
const version = createHash("sha256").update(body).digest("hex").slice(0, 12);
mkdirSync(dirname(out), { recursive: true });
writeFileSync(out, `{"version":"${version}","count":${questions.length},"questions":[\n${questions.map((q) => JSON.stringify(q)).join(",\n")}\n]}\n`);

const by = (f) => questions.reduce((m, q) => ((m[f(q)] = (m[f(q)] ?? 0) + 1), m), {});
console.log(`${questions.length} questions (${dropped} doublon(s) ignoré(s)) → ${out}`);
console.log("types :", JSON.stringify(by((q) => q.type)));
console.log("catégories :", JSON.stringify(by((q) => (q.pack ? "pack:" + q.pack : q.category))));

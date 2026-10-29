// Valide les fichiers sources de la banque « De l'autre côté » (JSONL).
// Usage : node games/validate.mjs games/src/<categorie>-<a|b>.jsonl ...
import { readFileSync } from "node:fs";
import { basename } from "node:path";

export const CATEGORIES = {
  souvenirs: { prefix: "sou", intensities: [1, 2] },
  nous_deux: { prefix: "ndx", intensities: [1, 2] },
  futur: { prefix: "fut", intensities: [1, 2] },
  valeurs: { prefix: "val", intensities: [1, 2] },
  fun: { prefix: "fun", intensities: [1, 2] },
  romantique: { prefix: "rom", intensities: [1, 2, 3] },
  ose: { prefix: "ose", intensities: [1, 2, 3] },
  intime: { prefix: "int", intensities: [2, 3] },
};
// Partie a : Devine-moi (et Duel) + Tu préfères ; partie b : Vérité, Action, Envies, Défis.
export const PARTS = { a: ["devine", "preferes"], b: ["verite", "action", "envie", "defi"] };
const FORMATS = new Set(["choice", "scale", "text"]);
const KEYS = new Set(["id", "category", "mode", "intensity", "text", "format", "options", "min", "max", "remote"]);

// Marques de genre interdites : la banque doit convenir à tous les couples.
const GENDERED = [
  [/\((e|es|s)\)/i, "« (e) »"],
  [/[·•]/, "point médian"],
  // Les pronoms (il, elle, -le, -la) désignent souvent des choses : ils sont relus à la main, pas bloqués.
  [/\b(pour|de|à|sans)\s+(le|la)\s+(rejoindre|embrasser|regarder|caresser|serrer|surprendre|toucher|câliner|enlacer|déshabiller|masser)\b/i, "pronom le/la"],
  [/\b(es|suis|être|sois|serais|serait|êtes|sommes)\s+(prêt|prête)s?\b/i, "mot genré"],
  [/\b(jaloux|jalouse|amoureux|amoureuse|heureux|heureuse|(?<!(?:te|se|me|nous|vous) )fier|fière|gêné|gênée|excité|excitée|chéri|chérie|copain|copine|mari|femme|homme|garçon|fille|petit ami|petite amie)\b/i, "mot genré"],
];

export function validateFile(file) {
  const key = basename(file, ".jsonl");
  const m = key.match(/^([a-z_]+)-([ab])$/);
  const errors = [];
  const items = [];
  if (!m || !CATEGORIES[m[1]]) return { key, items, errors: [`${file}: nom attendu <categorie>-<a|b>.jsonl`] };
  const [, category, part] = m;
  const { prefix, intensities } = CATEGORIES[category];
  const idRe = new RegExp(`^${prefix}-${part}-\\d{4}$`);
  const ids = new Set();
  const texts = new Set();
  readFileSync(file, "utf8").split("\n").forEach((line, i) => {
    if (!line.trim()) return;
    const where = `${file}:${i + 1}`;
    let q;
    try { q = JSON.parse(line); } catch (e) { errors.push(`${where}: JSON invalide (${e.message})`); return; }
    for (const k of Object.keys(q)) if (!KEYS.has(k)) errors.push(`${where}: clé inconnue « ${k} »`);
    if (!idRe.test(q.id ?? "")) errors.push(`${where}: id doit être ${prefix}-${part}-NNNN`);
    if (ids.has(q.id)) errors.push(`${where}: id dupliqué ${q.id}`);
    ids.add(q.id);
    if (q.category !== category) errors.push(`${where}: category doit être « ${category} »`);
    if (!PARTS[part].includes(q.mode)) errors.push(`${where}: mode doit être ${PARTS[part].join("/")}`);
    if (!intensities.includes(q.intensity)) errors.push(`${where}: intensity ∈ ${intensities.join(",")} pour ${category}`);
    if (typeof q.text !== "string" || q.text.length < 8 || q.text.length > 170) errors.push(`${where}: text 8..170 caractères`);
    const text = q.text ?? "";
    const placeholders = text.match(/\{[^}]*\}/g) ?? [];
    if (placeholders.some((p) => p !== "{autre}")) errors.push(`${where}: seul le repère {autre} est permis`);
    if (placeholders.length && (q.mode === "devine" || q.mode === "preferes")) errors.push(`${where}: pas de {autre} en ${q.mode}`);
    const all = [text, ...(Array.isArray(q.options) ? q.options : []), q.min ?? "", q.max ?? ""].join(" ");
    for (const [re, label] of GENDERED) if (re.test(all)) errors.push(`${where}: ${label} (${all.match(re)[0]}) : reformule de façon neutre`);
    const n = text.toLowerCase().normalize("NFD").replace(/[̀-ͯ]/g, "").replace(/[^a-z0-9]/g, "");
    if (texts.has(n)) errors.push(`${where}: texte dupliqué`);
    texts.add(n);
    if (q.mode === "devine") {
      if (!FORMATS.has(q.format)) errors.push(`${where}: format choice/scale/text requis en devine`);
      if (q.format === "choice") {
        if (!Array.isArray(q.options) || q.options.length < 2 || q.options.length > 4) errors.push(`${where}: options 2..4`);
        else if (q.options.some((o) => typeof o !== "string" || !o.trim() || o.length > 50)) errors.push(`${where}: option vide ou > 50 caractères`);
      } else if (q.options !== undefined) errors.push(`${where}: options seulement pour format choice`);
      if (q.format === "scale") {
        for (const k of ["min", "max"]) if (typeof q[k] !== "string" || !q[k].trim() || q[k].length > 28) errors.push(`${where}: ${k} requis (≤ 28 car.)`);
      } else if (q.min !== undefined || q.max !== undefined) errors.push(`${where}: min/max seulement pour format scale`);
    } else {
      if (q.format !== undefined) errors.push(`${where}: format seulement en devine`);
      if (q.mode === "preferes") {
        if (!Array.isArray(q.options) || q.options.length !== 2 || q.options.some((o) => typeof o !== "string" || !o.trim() || o.length > 70)) errors.push(`${where}: exactement 2 options ≤ 70 car.`);
      } else if (q.options !== undefined) errors.push(`${where}: options seulement en devine/preferes`);
      if (q.min !== undefined || q.max !== undefined) errors.push(`${where}: min/max seulement en devine`);
    }
    if (q.remote !== undefined && (q.mode !== "action" || typeof q.remote !== "boolean")) errors.push(`${where}: remote (booléen) seulement en action`);
    items.push(q);
  });
  return { key, category, part, items, errors };
}

if (process.argv[1] && process.argv[1].endsWith("validate.mjs")) {
  let failed = false;
  for (const f of process.argv.slice(2)) {
    const { items, errors } = validateFile(f);
    const count = (k) => items.reduce((acc, q) => ((acc[q[k]] = (acc[q[k]] ?? 0) + 1), acc), {});
    console.log(`${f}: ${items.length} entrées · modes ${JSON.stringify(count("mode"))} · intensités ${JSON.stringify(count("intensity"))} · formats ${JSON.stringify(count("format"))}`);
    errors.slice(0, 40).forEach((e) => console.log("  ✗ " + e));
    if (errors.length > 40) console.log(`  … et ${errors.length - 40} autres erreurs`);
    if (errors.length) failed = true;
  }
  process.exit(failed ? 1 : 0);
}

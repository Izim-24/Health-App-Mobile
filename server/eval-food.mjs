// Accuracy check for food-photo estimates.
//
//   node --env-file=.env eval-food.mjs [cases.json]        (default: eval/cases.json)
//   node eval-food.mjs --check [cases.json]                 (validate the case file, no API calls)
//
// cases.json: [{ "file": "eval/photos/pho.jpg", "label": "Phở bò tô lớn",
//                "calories": 520, "protein": 30, "carbs": 60, "fat": 15, "grams": 600 }]
// Only "file" and "calories" are required. Reference values must come from something
// measured — weighed food + a nutrition table or package label — not from another estimate.
import { readFile, writeFile } from 'node:fs/promises';
import { existsSync, statSync } from 'node:fs';
import path from 'node:path';
import { estimateFood, resolveProvider } from './index.mjs';

const args = process.argv.slice(2);
const checkOnly = args.includes('--check');
const modelArg = args.includes('--model') ? args[args.indexOf('--model') + 1] : null;
const promptArg = args.includes('--prompt') ? args[args.indexOf('--prompt') + 1] : undefined;
const casesPath = args.find((a, i) => !a.startsWith('--') && !['--model', '--prompt'].includes(args[i - 1])) || 'eval/cases.json';
const MAX_BYTES = 2_000_000; // same limit the server enforces (~2.8 MB base64)
const mimeFor = (f) => ({ '.jpg': 'image/jpeg', '.jpeg': 'image/jpeg', '.png': 'image/png', '.webp': 'image/webp' })[path.extname(f).toLowerCase()];

const cases = JSON.parse(await readFile(casesPath, 'utf8'));
const problems = [];
for (const [i, c] of cases.entries()) {
  if (!c.file || typeof c.calories !== 'number') problems.push(`#${i + 1}: needs "file" and numeric "calories"`);
  else if (!existsSync(c.file)) problems.push(`#${i + 1}: file not found: ${c.file}`);
  else if (!mimeFor(c.file)) problems.push(`#${i + 1}: unsupported type (use .jpg/.png/.webp): ${c.file}`);
  else if (statSync(c.file).size > MAX_BYTES) problems.push(`#${i + 1}: ${c.file} is ${(statSync(c.file).size / 1e6).toFixed(1)} MB — resize to ≤1024 px like the app does`);
}
if (problems.length) { console.error('Case file problems:\n  ' + problems.join('\n  ')); process.exit(1); }
console.log(`${cases.length} cases OK.`);
if (checkOnly) process.exit(0);

const provider = resolveProvider();
if (!provider) { console.error('No GEMINI_API_KEY/OPENAI_API_KEY — run with: node --env-file=.env eval-food.mjs'); process.exit(1); }
if (modelArg) provider.model = modelArg;
const model = `${provider.name}/${provider.model}`;
console.log(`Model: ${model} · prompt: ${promptArg || 'default'}`);
const sleep = (ms) => new Promise((r) => setTimeout(r, ms));
// Free tiers allow only a few requests per minute: space calls out and back off on 429.
async function estimateWithRetry(args) {
  for (let attempt = 0; ; attempt++) {
    let r;
    try { r = await estimateFood(args); } catch (e) { r = { error: e.name === 'TimeoutError' ? 'timeout (45 s)' : e.message }; }
    if (r.status !== 429 || attempt >= 3) return r;
    console.log(`  rate-limited, waiting ${20 * (attempt + 1)} s…`);
    await sleep(20_000 * (attempt + 1));
  }
}

const pct = (pred, ref) => (ref > 0 ? ((pred - ref) / ref) * 100 : null);
const rows = [];
for (const [n, c] of cases.entries()) {
  if (n > 0) await sleep(Number(process.env.EVAL_DELAY_MS || 4000));
  const image = (await readFile(c.file)).toString('base64');
  const started = Date.now();
  const r = await estimateWithRetry({ provider, image, mimeType: mimeFor(c.file), prompt: promptArg });
  const ms = Date.now() - started;
  if (r.error) {
    rows.push({ ...c, error: r.status ? `HTTP ${r.status}` : r.error, ms });
    console.log(`✖ ${c.label || c.file}: ${rows.at(-1).error}`);
    continue;
  }
  const t = r.food.foods.reduce((a, f) => ({ kcal: a.kcal + f.calories, p: a.p + f.protein_g, c: a.c + f.carbs_g, f: a.f + f.fat_g, g: a.g + f.estimated_grams }), { kcal: 0, p: 0, c: 0, f: 0, g: 0 });
  const row = {
    ...c, ms, predicted: { calories: t.kcal, protein: +t.p.toFixed(1), carbs: +t.c.toFixed(1), fat: +t.f.toFixed(1), grams: t.g },
    items: r.food.foods.map((f) => `${f.name} ${f.estimated_grams}g ${f.calories}kcal (${Math.round(f.confidence * 100)}%)`),
    calErrPct: pct(t.kcal, c.calories), gramsErrPct: c.grams ? pct(t.g, c.grams) : null, notes: r.food.notes
  };
  rows.push(row);
  console.log(`${Math.abs(row.calErrPct) <= 20 ? '✔' : Math.abs(row.calErrPct) <= 35 ? '~' : '✖'} ${c.label || c.file}: ref ${c.calories} kcal, AI ${t.kcal} kcal (${row.calErrPct >= 0 ? '+' : ''}${row.calErrPct.toFixed(0)}%) · ${row.items.join('; ')}`);
}

const ok = rows.filter((r) => r.calErrPct != null);
const abs = ok.map((r) => Math.abs(r.calErrPct)).sort((a, b) => a - b);
const mean = (xs) => xs.reduce((a, b) => a + b, 0) / (xs.length || 1);
const summary = {
  model, cases: rows.length, failed: rows.length - ok.length,
  calories: ok.length ? {
    meanAbsErrorPct: +mean(abs).toFixed(1),
    medianAbsErrorPct: +abs[Math.floor(abs.length / 2)].toFixed(1),
    meanBiasPct: +mean(ok.map((r) => r.calErrPct)).toFixed(1), // + = AI overestimates on average
    within20Pct: ok.filter((r) => Math.abs(r.calErrPct) <= 20).length,
    within35Pct: ok.filter((r) => Math.abs(r.calErrPct) <= 35).length,
    worst: ok.slice().sort((a, b) => Math.abs(b.calErrPct) - Math.abs(a.calErrPct)).slice(0, 3).map((r) => `${r.label || r.file} ${r.calErrPct.toFixed(0)}%`)
  } : null,
  avgSeconds: +(mean(rows.map((r) => r.ms)) / 1000).toFixed(1)
};
console.log('\nSummary:', JSON.stringify(summary, null, 2));
const out = path.join(path.dirname(casesPath), `results-${path.basename(casesPath, '.json')}-${provider.model}-${promptArg || 'default'}.json`);
await writeFile(out, JSON.stringify({ summary, rows }, null, 2));
console.log(`Details written to ${out}`);

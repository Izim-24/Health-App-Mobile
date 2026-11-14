// Picks a varied set of Nutrition5k test dishes (lab-weighed ground truth), downloads their
// overhead photos and writes eval/cases.json for eval-food.mjs.
//
//   node eval/prepare-nutrition5k.mjs [count=15]
//
// Expects eval/nutrition5k/dish_metadata_cafe1.csv and rgb_test_ids.txt (public dataset:
// https://github.com/google-research-datasets/Nutrition5k, CC BY 4.0).
import { readFile, writeFile, mkdir } from 'node:fs/promises';
import { existsSync } from 'node:fs';

const args = process.argv.slice(2);
const count = Number(args.find((a) => /^\d+$/.test(a)) || 15);
// --offset N picks the N-th photo-available dish in each calorie band, so offset 0 (dev set)
// and offset 1 (holdout set) never share a dish.
const offset = args.includes('--offset') ? Number(args[args.indexOf('--offset') + 1]) : 0;
const outFile = args.includes('--out') ? args[args.indexOf('--out') + 1] : 'eval/cases.json';
const BASE = 'https://storage.googleapis.com/nutrition5k_dataset/nutrition5k_dataset';
const dir = 'eval/nutrition5k';
const testIds = new Set((await readFile(`${dir}/rgb_test_ids.txt`, 'utf8')).split(/\s+/).filter(Boolean));

const dishes = (await readFile(`${dir}/dish_metadata_cafe1.csv`, 'utf8')).split('\n').filter(Boolean).map((line) => {
  const f = line.split(',');
  const ingredients = [];
  for (let i = 6; i + 6 < f.length + 1; i += 7) ingredients.push({ name: f[i + 1], grams: +f[i + 2] });
  return { id: f[0], calories: +f[1], grams: +f[2], fat: +f[3], carbs: +f[4], protein: +f[5], ingredients };
}).filter((d) => testIds.has(d.id) && d.grams >= 120 && d.grams <= 800 && d.calories >= 100 && d.calories <= 1200 && d.ingredients.length >= 2);

// Stratify by calories into `count` bands so the set covers light to heavy plates; deterministic pick.
dishes.sort((a, b) => a.calories - b.calories);
const picks = [];
for (let b = 0; b < count; b++) {
  const band = dishes.slice(Math.floor((b * dishes.length) / count), Math.floor(((b + 1) * dishes.length) / count));
  // first dish in the band that actually has an overhead photo
  let seen = 0;
  for (const d of band.sort((x, y) => x.id.localeCompare(y.id))) {
    const url = `${BASE}/imagery/realsense_overhead/${d.id}/rgb.png`;
    const head = await fetch(url, { method: 'HEAD' });
    if (!head.ok) continue;
    if (seen++ === offset) { picks.push({ ...d, url, bytes: Number(head.headers.get('content-length')) }); break; }
  }
}
const totalMB = picks.reduce((a, p) => a + p.bytes, 0) / 1e6;
console.log(`${dishes.length} eligible dishes; picked ${picks.length}; photos total ${totalMB.toFixed(1)} MB`);
if (args.includes('--plan')) { picks.forEach((p) => console.log(`  ${p.id}  ${Math.round(p.calories)} kcal  ${Math.round(p.grams)} g  ${(p.bytes / 1e3).toFixed(0)} KB  ${p.ingredients.map((i) => i.name).slice(0, 4).join(', ')}`)); process.exit(0); }

await mkdir('eval/photos', { recursive: true });
const cases = [];
for (const p of picks) {
  const file = `eval/photos/${p.id}.png`;
  if (!existsSync(file)) await writeFile(file, Buffer.from(await (await fetch(p.url)).arrayBuffer()));
  cases.push({
    file, label: p.ingredients.map((i) => i.name).slice(0, 4).join(', '),
    calories: Math.round(p.calories), grams: Math.round(p.grams),
    protein: +p.protein.toFixed(1), carbs: +p.carbs.toFixed(1), fat: +p.fat.toFixed(1), source: `Nutrition5k ${p.id}`
  });
}
await writeFile(outFile, JSON.stringify(cases, null, 2));
console.log(`Wrote ${outFile} with ${cases.length} cases.`);

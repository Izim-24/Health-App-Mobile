import assert from 'node:assert/strict';
import { after, before, test } from 'node:test';
import { createServer, sanitizeFood, MOCK_FOOD, resolveProvider, toGeminiSchema, applyFoodTable, FOOD_TABLE } from './index.mjs';

const IMAGE = 'A'.repeat(400); // valid base64 shape; content is irrelevant with a fake upstream
let server;
let base;
let forwarded;
let upstreamText;

before(async () => {
  server = createServer({ apiKey: 'test-key', mock: false, fetchImpl: async (_url, options) => {
    forwarded = options;
    return new Response(JSON.stringify({ output: [{ content: [{ type: 'output_text', text: upstreamText }] }] }), { status: 200 });
  } });
  await new Promise((resolve) => server.listen(0, '127.0.0.1', resolve));
  base = `http://127.0.0.1:${server.address().port}`;
});
after(() => server.close());

const post = (path, body) => fetch(`${base}${path}`, { method: 'POST', headers: { 'content-type': 'application/json' }, body: JSON.stringify(body) });

test('health and chat forward messages without exposing key to client', async () => {
  upstreamText = 'Xin chào.';
  const health = await (await fetch(`${base}/health`)).json();
  assert.deepEqual(health, { status: 'ok', configured: true, provider: 'openai', model: 'gpt-4.1-mini', mock: false });
  const response = await post('/api/chat', { messages: [{ role: 'user', content: 'Xin chào' }] });
  assert.equal(response.status, 200);
  assert.deepEqual(await response.json(), { answer: 'Xin chào.', model: 'gpt-4.1-mini' });
  assert.equal(forwarded.headers.authorization, 'Bearer test-key');
  assert.equal(JSON.parse(forwarded.body).store, false);
});

test('rejects malformed health messages before upstream', async () => {
  const response = await post('/api/chat', { messages: [{ role: 'user', content: '' }] });
  assert.equal(response.status, 400);
});

test('food image: sends image with strict JSON schema and returns sanitized items', async () => {
  upstreamText = JSON.stringify({ is_food: true, notes: 'Khó ước lượng dầu.', foods: [
    { name: ' Phở bò ', estimated_grams: 550.4, calories: 452.6, protein_g: 28, carbs_g: 55, fat_g: 12, confidence: 0.85 }
  ] });
  const response = await post('/api/food-image', { image: IMAGE, mimeType: 'image/jpeg' });
  assert.equal(response.status, 200);
  const body = await response.json();
  // no table_key → the model's own numbers are kept and labelled as such
  assert.deepEqual(body.foods, [{ name: 'Phở bò', estimated_grams: 550, calories: 453, protein_g: 28, carbs_g: 55, fat_g: 12, confidence: 0.85, ai_calories: 453, source: 'ai' }]);
  assert.equal(body.is_food, true);
  assert.equal(body.mock, false);

  const sent = JSON.parse(forwarded.body);
  assert.equal(sent.store, false);
  assert.equal(sent.text.format.type, 'json_schema');
  assert.equal(sent.text.format.strict, true);
  const image = sent.input[0].content.find((c) => c.type === 'input_image');
  assert.equal(image.image_url, `data:image/jpeg;base64,${IMAGE}`);
});

test('food image: rejects bad mime type, non-base64 and oversized images before upstream', async () => {
  forwarded = null;
  assert.equal((await post('/api/food-image', { image: IMAGE, mimeType: 'image/gif' })).status, 400);
  assert.equal((await post('/api/food-image', { image: '<script>' + IMAGE, mimeType: 'image/png' })).status, 400);
  assert.equal((await post('/api/food-image', { image: 'A'.repeat(3_000_000), mimeType: 'image/png' })).status, 413);
  assert.equal(forwarded, null);
});

test('food image: malformed model output becomes a 502, not bad data', async () => {
  upstreamText = 'Sorry, I cannot help.';
  const response = await post('/api/food-image', { image: IMAGE, mimeType: 'image/jpeg' });
  assert.equal(response.status, 502);
});

test('sanitizeFood drops unusable items and clamps ranges', () => {
  const out = sanitizeFood({ is_food: true, notes: 'x', foods: [
    { name: '', estimated_grams: 100, calories: 100 },
    { name: 'Bánh mì', estimated_grams: -5, calories: 250 },
    { name: 'Nước ngọt', estimated_grams: 330, calories: 140, protein_g: -1, carbs_g: 35, fat_g: 0, confidence: 7 }
  ] });
  assert.equal(out.foods.length, 2);
  assert.equal(out.foods[0].estimated_grams, 1); // clamped, not dropped
  assert.equal(out.foods[1].protein_g, 0);
  assert.equal(out.foods[1].confidence, 1);
  assert.equal(sanitizeFood({ is_food: true, foods: [], notes: '' }).is_food, false);
  assert.equal(sanitizeFood('nope'), null);
});

test('mock mode without a key returns clearly flagged sample data', async () => {
  const mockServer = createServer({ apiKey: '', mock: true, fetchImpl: async () => { throw new Error('must not call upstream'); } });
  await new Promise((resolve) => mockServer.listen(0, '127.0.0.1', resolve));
  const url = `http://127.0.0.1:${mockServer.address().port}/api/food-image`;
  const body = await (await fetch(url, { method: 'POST', headers: { 'content-type': 'application/json' }, body: JSON.stringify({ image: IMAGE, mimeType: 'image/jpeg' }) })).json();
  mockServer.close();
  assert.equal(body.mock, true);
  assert.equal(body.foods.length, MOCK_FOOD.foods.length);
});

test('resolveProvider prefers Gemini, trims keys, falls back to OpenAI', () => {
  assert.deepEqual(resolveProvider({ GEMINI_API_KEY: 'g-key\r', OPENAI_API_KEY: 'sk-x' }), { name: 'gemini', apiKey: 'g-key', model: 'gemini-3.5-flash', fallbackModel: 'gemini-3.5-flash-lite' });
  assert.equal(resolveProvider({ GEMINI_API_KEY: 'g', GEMINI_MODEL: 'gemini-3.5-flash-lite' }).model, 'gemini-3.5-flash-lite');
  assert.equal(resolveProvider({ OPENAI_API_KEY: 'sk-x' }).name, 'openai');
  assert.equal(resolveProvider({ GEMINI_API_KEY: '  ' }), null);
  assert.equal(resolveProvider({ GEMINI_API_KEY: 'g', GEMINI_FALLBACK_MODEL: '' }).fallbackModel, null); // can be turned off
});

test('toGeminiSchema uppercases types and drops additionalProperties', () => {
  const g = toGeminiSchema({ type: 'object', additionalProperties: false, required: ['a'], properties: { a: { type: 'array', items: { type: 'number' } } } });
  assert.deepEqual(g, { type: 'OBJECT', required: ['a'], properties: { a: { type: 'ARRAY', items: { type: 'NUMBER' } } } });
});

test('Gemini: food image sends inlineData + JSON schema, ignores thought parts', async () => {
  let sent;
  const gemini = createServer({ provider: { name: 'gemini', apiKey: 'g-key', model: 'gemini-test' }, mock: false, fetchImpl: async (url, options) => {
    sent = { url, options };
    const answer = { is_food: true, notes: 'n', foods: [{ name: 'Cơm tấm', estimated_grams: 350, calories: 620, protein_g: 30, carbs_g: 80, fat_g: 20, confidence: 0.7 }] };
    return new Response(JSON.stringify({ candidates: [{ content: { parts: [{ text: 'thinking…', thought: true }, { text: JSON.stringify(answer) }] } }] }), { status: 200 });
  } });
  await new Promise((resolve) => gemini.listen(0, '127.0.0.1', resolve));
  const res = await fetch(`http://127.0.0.1:${gemini.address().port}/api/food-image`, { method: 'POST', headers: { 'content-type': 'application/json' }, body: JSON.stringify({ image: IMAGE, mimeType: 'image/png' }) });
  gemini.close();
  assert.equal(res.status, 200);
  assert.equal((await res.json()).foods[0].name, 'Cơm tấm');
  assert.match(sent.url, /models\/gemini-test:generateContent$/);
  assert.equal(sent.options.headers['x-goog-api-key'], 'g-key');
  const body = JSON.parse(sent.options.body);
  assert.deepEqual(body.contents[0].parts[1], { inlineData: { mimeType: 'image/png', data: IMAGE } });
  assert.equal(body.generationConfig.responseMimeType, 'application/json');
  assert.equal(body.generationConfig.responseSchema.type, 'OBJECT');
});

test('Gemini: chat maps assistant→model and free-tier 429 gets a clear message', async () => {
  let sent;
  let status = 200;
  const gemini = createServer({ provider: { name: 'gemini', apiKey: 'g', model: 'm' }, mock: false, fetchImpl: async (_url, options) => {
    sent = JSON.parse(options.body);
    return new Response(JSON.stringify(status === 200 ? { candidates: [{ content: { parts: [{ text: 'Chào bạn.' }] } }] } : { error: {} }), { status });
  } });
  await new Promise((resolve) => gemini.listen(0, '127.0.0.1', resolve));
  const url = `http://127.0.0.1:${gemini.address().port}/api/chat`;
  const messages = [{ role: 'user', content: 'Hi' }, { role: 'assistant', content: 'Hello' }, { role: 'user', content: 'Xin chào' }];
  const ok = await (await fetch(url, { method: 'POST', headers: { 'content-type': 'application/json' }, body: JSON.stringify({ messages }) })).json();
  status = 429;
  const limited = await fetch(url, { method: 'POST', headers: { 'content-type': 'application/json' }, body: JSON.stringify({ messages }) });
  gemini.close();
  assert.equal(ok.answer, 'Chào bạn.');
  assert.deepEqual(sent.contents.map((c) => c.role), ['user', 'model', 'user']);
  assert.equal(limited.status, 502);
  assert.match((await limited.json()).error, /miễn phí/);
});

test('Gemini: falls back to the lite model once when the main model is rate-limited', async () => {
  const calls = [];
  const gemini = createServer({ provider: { name: 'gemini', apiKey: 'g', model: 'main', fallbackModel: 'lite' }, mock: false, fetchImpl: async (url) => {
    calls.push(url.match(/models\/([^:]+):/)[1]);
    if (url.includes('/main:')) return new Response('{}', { status: 429 });
    const answer = { is_food: true, notes: '', foods: [{ name: 'Phở', estimated_grams: 500, calories: 450, protein_g: 28, carbs_g: 55, fat_g: 12, confidence: 0.8 }] };
    return new Response(JSON.stringify({ candidates: [{ content: { parts: [{ text: JSON.stringify(answer) }] } }] }), { status: 200 });
  } });
  await new Promise((resolve) => gemini.listen(0, '127.0.0.1', resolve));
  const res = await fetch(`http://127.0.0.1:${gemini.address().port}/api/food-image`, { method: 'POST', headers: { 'content-type': 'application/json' }, body: JSON.stringify({ image: IMAGE, mimeType: 'image/jpeg' }) });
  gemini.close();
  const body = await res.json();
  assert.equal(res.status, 200);
  assert.deepEqual(calls, ['main', 'lite']);
  assert.equal(body.model, 'lite');
  assert.equal(body.foods[0].name, 'Phở');
});

test('fallback is not used for non-retryable errors', async () => {
  const calls = [];
  const gemini = createServer({ provider: { name: 'gemini', apiKey: 'g', model: 'main', fallbackModel: 'lite' }, mock: false, fetchImpl: async (url) => {
    calls.push(url); return new Response('{}', { status: 403 });
  } });
  await new Promise((resolve) => gemini.listen(0, '127.0.0.1', resolve));
  const res = await fetch(`http://127.0.0.1:${gemini.address().port}/api/food-image`, { method: 'POST', headers: { 'content-type': 'application/json' }, body: JSON.stringify({ image: IMAGE, mimeType: 'image/jpeg' }) });
  gemini.close();
  assert.equal(res.status, 502);
  assert.equal(calls.length, 1);
});

test('food table: matched items get table calories × grams, unmatched keep AI values', async () => {
  let sent;
  const answer = { is_food: true, notes: '', foods: [
    { name: 'Cơm trắng', table_key: 'White Rice ', estimated_grams: 200, calories: 300, protein_g: 6, carbs_g: 60, fat_g: 1, confidence: 0.9 },
    { name: 'Món lạ', table_key: 'not in table', estimated_grams: 100, calories: 222, protein_g: 5, carbs_g: 20, fat_g: 10, confidence: 0.4 }
  ] };
  // Own server instance: the shared one has used up its per-minute request budget by now.
  const srv = createServer({ apiKey: 'k', mock: false, fetchImpl: async (_url, options) => {
    sent = JSON.parse(options.body);
    return new Response(JSON.stringify({ output: [{ content: [{ type: 'output_text', text: JSON.stringify(answer) }] }] }), { status: 200 });
  } });
  await new Promise((resolve) => srv.listen(0, '127.0.0.1', resolve));
  const res = await fetch(`http://127.0.0.1:${srv.address().port}/api/food-image`, { method: 'POST', headers: { 'content-type': 'application/json' }, body: JSON.stringify({ image: IMAGE, mimeType: 'image/jpeg' }) });
  srv.close();
  const body = await res.json();
  const rice = FOOD_TABLE.get('white rice');
  const r1 = (x) => Math.round(x * 10) / 10;
  assert.deepEqual(body.foods[0], {
    name: 'Cơm trắng', estimated_grams: 200, calories: Math.round(rice.kcal * 2), protein_g: r1(rice.protein * 2), carbs_g: r1(rice.carbs * 2),
    fat_g: r1(rice.fat * 2), confidence: 0.9, ai_calories: 300, source: 'table', table_key: 'white rice'
  });
  assert.deepEqual(body.foods[1], { name: 'Món lạ', estimated_grams: 100, calories: 222, protein_g: 5, carbs_g: 20, fat_g: 10, confidence: 0.4, ai_calories: 222, source: 'ai' });
  // the prompt lists the table keys and the schema requires table_key
  assert.match(sent.instructions, /Reference list: .*white rice/);
  assert.ok(sent.text.format.schema.properties.foods.items.required.includes('table_key'));
});

test('food table: applyFoodTable scales per-100 g values and rounds', () => {
  const table = new Map([['almonds', { key: 'almonds', kcal: 578, protein: 21.2, carbs: 21.6, fat: 49.9 }]]);
  const out = applyFoodTable({ is_food: true, notes: '', foods: [{ name: 'Hạnh nhân', table_key: 'almonds', estimated_grams: 28, calories: 100, protein_g: 1, carbs_g: 1, fat_g: 1, confidence: 0.8 }] }, table);
  assert.deepEqual(out.foods[0], { name: 'Hạnh nhân', estimated_grams: 28, calories: 162, protein_g: 5.9, carbs_g: 6, fat_g: 14, confidence: 0.8, ai_calories: 100, source: 'table', table_key: 'almonds' });
});

test('food table: every entry has plausible per-100 g values', () => {
  assert.ok(FOOD_TABLE.size > 100);
  // Copied unchanged from the source; their macros disagree with their kcal in Nutrition5k itself.
  const sourceQuirks = new Set(['fish', 'country rice']);
  for (const f of FOOD_TABLE.values()) {
    if (sourceQuirks.has(f.key)) continue;
    assert.ok(f.kcal >= 0 && f.kcal <= 900, f.key);
    const fromMacros = 4 * f.protein + 4 * f.carbs + 9 * f.fat;
    // macros must roughly explain the calories (fibre, alcohol and rounding allow some slack)
    if (f.kcal > 40) assert.ok(Math.abs(fromMacros - f.kcal) / f.kcal < 0.3, `${f.key}: ${f.kcal} vs ${fromMacros.toFixed(0)}`);
  }
});

test('language: lang "en" switches chat instructions and food names; default stays Vietnamese', async () => {
  const seen = [];
  const answer = { is_food: true, notes: 'n', foods: [{ name: 'Rice', table_key: '', estimated_grams: 100, calories: 130, protein_g: 3, carbs_g: 28, fat_g: 0, confidence: 0.9 }] };
  const gemini = createServer({ provider: { name: 'gemini', apiKey: 'g', model: 'm' }, mock: false, fetchImpl: async (_url, options) => {
    seen.push(JSON.parse(options.body).systemInstruction.parts[0].text);
    const text = seen.length <= 2 ? 'ok' : JSON.stringify(answer);
    return new Response(JSON.stringify({ candidates: [{ content: { parts: [{ text }] } }] }), { status: 200 });
  } });
  await new Promise((resolve) => gemini.listen(0, '127.0.0.1', resolve));
  const at = (path, body) => fetch(`http://127.0.0.1:${gemini.address().port}${path}`, { method: 'POST', headers: { 'content-type': 'application/json' }, body: JSON.stringify(body) });
  const messages = [{ role: 'user', content: 'Hi' }];
  await at('/api/chat', { messages });
  await at('/api/chat', { messages, lang: 'en' });
  await at('/api/food-image', { image: IMAGE, mimeType: 'image/jpeg' });
  await at('/api/food-image', { image: IMAGE, mimeType: 'image/jpeg', lang: 'en' });
  gemini.close();
  assert.match(seen[0], /tiếng Việt/);
  assert.match(seen[1], /in English/);
  assert.doesNotMatch(seen[2], /Language override/);
  assert.match(seen[3], /Language override: write every `name`/);
});

test('errors carry a stable code the app can translate', async () => {
  const srv = createServer({ provider: { name: 'gemini', apiKey: 'g', model: 'm' }, mock: false, fetchImpl: async () => new Response('{}', { status: 403 }) });
  await new Promise((resolve) => srv.listen(0, '127.0.0.1', resolve));
  const at = (path, body) => fetch(`http://127.0.0.1:${srv.address().port}${path}`, { method: 'POST', headers: { 'content-type': 'application/json' }, body: JSON.stringify(body) });
  const auth = await at('/api/chat', { messages: [{ role: 'user', content: 'Hi' }] });
  const badImage = await at('/api/food-image', { image: IMAGE, mimeType: 'image/gif' });
  const unknown = await at('/api/nope', {});
  srv.close();
  assert.equal(auth.status, 502);
  assert.equal((await auth.json()).code, 'upstream_auth');
  assert.equal((await badImage.json()).code, 'bad_image');
  assert.equal((await unknown.json()).code, 'not_found');
  const none = createServer({ provider: null, mock: false });
  await new Promise((resolve) => none.listen(0, '127.0.0.1', resolve));
  const r = await fetch(`http://127.0.0.1:${none.address().port}/api/chat`, { method: 'POST', headers: { 'content-type': 'application/json' }, body: '{}' });
  none.close();
  assert.equal(r.status, 503);
  assert.equal((await r.json()).code, 'not_configured');
});

import http from 'node:http';
import { fileURLToPath } from 'node:url';
import { readFileSync } from 'node:fs';

/**
 * Food-composition table (per 100 g as served), built by eval/build-food-table.mjs. The model names the
 * food and estimates grams; calories and macros then come from this table instead of the model's memory.
 */
export const FOOD_TABLE = new Map(
  JSON.parse(readFileSync(new URL('./data/food-table.json', import.meta.url), 'utf8')).foods.map((f) => [f.key, f])
);
const tableKeyList = [...FOOD_TABLE.keys()].join('; ');
const tableInstructions = `
- table_key: the entry from the reference list below that best matches the item as served (same food AND preparation, e.g. "scrambled eggs", not "eggs" raw). Copy it exactly. Use "" if nothing fits well — never force a poor match.
Reference list: ${tableKeyList}`;

const instructions = `Bạn là trợ lý cung cấp thông tin sức khỏe bằng tiếng Việt. Trả lời ngắn gọn, rõ ràng và thừa nhận khi không chắc chắn. Không tự chẩn đoán, kê đơn hoặc thay đổi liều thuốc. Không khẳng định đã xem hồ sơ, chỉ số hay kết quả xét nghiệm nếu người dùng chưa gửi trong cuộc trò chuyện. Với dấu hiệu có thể khẩn cấp, hướng dẫn người dùng tìm trợ giúp y tế tại địa phương ngay. Nhắc rằng thông tin chỉ mang tính tham khảo khi phù hợp.`;

const foodInstructionsV1 = `You estimate the nutrition of food in a photo for a Vietnamese health diary app.
- List each distinct food or drink that is visible (max 8). Use the common Vietnamese name (e.g. "Cơm trắng", "Phở bò", "Trứng chiên").
- estimated_grams: the edible portion actually visible on the plate/bowl (not per 100 g). Judge size from plates, bowls, cutlery and hands.
- calories, protein_g, carbs_g, fat_g: for THAT estimated portion, from standard food-composition values. Include visible cooking oil, sauce and broth.
- Keep calories consistent with macros (about 4 kcal/g protein, 4 kcal/g carbs, 9 kcal/g fat).
- confidence: 0..1 for how sure you are about identity AND portion. Use low values for blurry photos, mixed dishes or hidden ingredients.
- If the photo shows no food, set is_food=false and foods=[].
- notes: one short Vietnamese sentence about the main uncertainty (e.g. lượng dầu, kích thước khẩu phần).
Never invent foods that are not visible.`;

// v2: explicit scale references, counting for discrete items, volume→grams for piles,
// and extra care with calorie-dense foods (the largest source of error in evaluation).
const foodInstructionsV2 = `You estimate the nutrition of food in a photo for a Vietnamese health diary app. Work step by step:
1. Identify each distinct food or drink that is actually visible (max 8). Use the common Vietnamese name (e.g. "Cơm trắng", "Phở bò", "Hạnh nhân"). Never invent hidden foods.
2. Establish scale first: a dinner plate is about 26 cm across, a side plate about 20 cm, a rice bowl 11–12 cm, a fork about 19 cm, a spoon about 15 cm. Judge every item's footprint and height against these.
3. Estimate estimated_grams (edible portion visible) per item:
   - Countable items: COUNT the pieces, then multiply by a typical piece weight (almond ≈1.2 g, cashew/peanut ≈1–1.5 g, grape ≈5 g, cherry tomato ≈15 g, strawberry ≈12 g, raspberry/blackberry ≈4 g, whole egg ≈50 g, cooked bacon strip ≈8 g, sausage slice ≈8–15 g by diameter and thickness, meatball ≈25 g, dumpling/spring roll ≈25–40 g).
   - Piles and mounds (rice, noodles, mashed or chopped food): estimate volume from footprint × height, then convert (cooked rice ≈0.8 g/ml, cut fruit and cooked vegetables ≈0.5–0.7 g/ml, raw leafy greens ≈0.1–0.2 g/ml).
   - Meat or fish pieces: area × thickness, about 1.05 g per cm³.
4. calories, protein_g, carbs_g, fat_g are for THAT portion, from standard food-composition values for the food as served (cooked weights). Calorie-dense foods (nuts, seeds, cheese, sausage, bacon, fried food, pastry, oil, creamy sauces) dominate the total: be most careful with their count and size and do not underestimate them. Light foods (leafy greens, melon, cucumber, berries) contribute little.
5. Include visible cooking oil, glaze, dressing or sauce in the item it coats.
6. Keep calories consistent with macros (4 kcal/g protein, 4 kcal/g carbs, 9 kcal/g fat).
7. confidence: 0..1 for identity AND portion together; lower it for blurry photos, stacked or hidden food, and mixed dishes.
8. If the photo shows no food, set is_food=false and foods=[].
9. notes: one short Vietnamese sentence about the main uncertainty.`;

export const FOOD_PROMPTS = { v1: foodInstructionsV1, v2: foodInstructionsV2 };
const DEFAULT_FOOD_PROMPT = 'v1';

const foodSchema = {
  type: 'object',
  additionalProperties: false,
  required: ['is_food', 'foods', 'notes'],
  properties: {
    is_food: { type: 'boolean' },
    notes: { type: 'string' },
    foods: {
      type: 'array',
      maxItems: 8,
      items: {
        type: 'object',
        additionalProperties: false,
        required: ['name', 'table_key', 'estimated_grams', 'calories', 'protein_g', 'carbs_g', 'fat_g', 'confidence'],
        properties: {
          name: { type: 'string' },
          table_key: { type: 'string' },
          estimated_grams: { type: 'number' },
          calories: { type: 'number' },
          protein_g: { type: 'number' },
          carbs_g: { type: 'number' },
          fat_g: { type: 'number' },
          confidence: { type: 'number' }
        }
      }
    }
  }
};

/** Canned answer for `npm run mock` / MOCK_AI=1 — lets the app be tested without a key. Always flagged mock:true. */
export const MOCK_FOOD = {
  is_food: true,
  notes: 'DỮ LIỆU GIẢ LẬP — không phân tích ảnh thật.',
  foods: [
    { name: 'Cơm trắng', estimated_grams: 180, calories: 234, protein_g: 4.3, carbs_g: 50.8, fat_g: 0.5, confidence: 0.8 },
    { name: 'Thịt kho trứng', estimated_grams: 150, calories: 320, protein_g: 21, carbs_g: 6, fat_g: 24, confidence: 0.6 },
    { name: 'Rau muống xào tỏi', estimated_grams: 100, calories: 95, protein_g: 3, carbs_g: 5, fat_g: 7, confidence: 0.7 }
  ]
};

const MAX_IMAGE_BASE64 = 2_800_000; // ~2 MB image; the app sends ≤1024 px JPEGs (~150–300 KB)
const IMAGE_TYPES = new Set(['image/jpeg', 'image/png', 'image/webp']);

function send(res, status, body) {
  res.writeHead(status, { 'content-type': 'application/json; charset=utf-8', 'cache-control': 'no-store', 'x-content-type-options': 'nosniff' });
  res.end(JSON.stringify(body));
}

async function readBody(req, limit) {
  let size = 0;
  const chunks = [];
  for await (const chunk of req) {
    size += chunk.length;
    if (size > limit) throw Object.assign(new Error('Yêu cầu quá dài.'), { status: 413 });
    chunks.push(chunk);
  }
  try { return JSON.parse(Buffer.concat(chunks).toString('utf8')); }
  catch { throw Object.assign(new Error('JSON không hợp lệ.'), { status: 400 }); }
}

function validMessages(messages) {
  return Array.isArray(messages) && messages.length >= 1 && messages.length <= 12 &&
    messages.every((m) => m && (m.role === 'user' || m.role === 'assistant') &&
      typeof m.content === 'string' && m.content.trim().length >= 1 && m.content.length <= 2000) &&
    messages.at(-1).role === 'user';
}

function outputText(result) {
  return result.output?.flatMap((item) => item.content || []).filter((item) => item.type === 'output_text').map((item) => item.text).join('\n').trim();
}

const num = (v, min, max) => (typeof v === 'number' && Number.isFinite(v) ? Math.min(Math.max(v, min), max) : null);

/** Server-side guard on model output: drop malformed items and clamp to physically plausible ranges. */
export function sanitizeFood(parsed) {
  if (!parsed || typeof parsed !== 'object' || !Array.isArray(parsed.foods)) return null;
  const foods = parsed.foods.slice(0, 8).flatMap((f) => {
    const name = typeof f?.name === 'string' ? f.name.trim().slice(0, 80) : '';
    const grams = num(f?.estimated_grams, 1, 3000);
    const calories = num(f?.calories, 0, 5000);
    if (!name || grams === null || calories === null) return [];
    return [{
      name,
      table_key: typeof f.table_key === 'string' ? f.table_key.trim().toLowerCase().slice(0, 80) : '',
      estimated_grams: Math.round(grams),
      calories: Math.round(calories),
      protein_g: num(f.protein_g, 0, 500) ?? 0,
      carbs_g: num(f.carbs_g, 0, 1000) ?? 0,
      fat_g: num(f.fat_g, 0, 500) ?? 0,
      confidence: num(f.confidence, 0, 1) ?? 0
    }];
  });
  return { is_food: Boolean(parsed.is_food) && foods.length > 0, foods, notes: typeof parsed.notes === 'string' ? parsed.notes.slice(0, 300) : '' };
}

const r1 = (x) => Math.round(x * 10) / 10;

/**
 * Replaces the model's calories/macros with table values (per 100 g × estimated grams) for items whose
 * table_key is in [FOOD_TABLE]. Items without a match keep the model's numbers. Every item says which
 * it is (`source`), and `ai_calories` keeps the model's own figure for transparency and evaluation.
 */
export function applyFoodTable(food, table = FOOD_TABLE) {
  return {
    ...food,
    foods: food.foods.map(({ table_key: key, ...f }) => {
      const row = key ? table.get(key) : undefined;
      if (!row) return { ...f, ai_calories: f.calories, source: 'ai' };
      const k = f.estimated_grams / 100;
      return {
        ...f,
        calories: Math.round(row.kcal * k), protein_g: r1(row.protein * k), carbs_g: r1(row.carbs * k), fat_g: r1(row.fat * k),
        ai_calories: f.calories, source: 'table', table_key: key
      };
    })
  };
}

/**
 * AI provider chosen from the environment. GEMINI_API_KEY wins when present (free tier works),
 * otherwise OPENAI_API_KEY. Keys are trimmed (Windows editors leave \r in .env files).
 */
export function resolveProvider(env = process.env) {
  const gemini = (env.GEMINI_API_KEY || '').trim();
  const openai = (env.OPENAI_API_KEY || '').trim();
  if (gemini) return {
    name: 'gemini', apiKey: gemini, model: (env.GEMINI_MODEL || 'gemini-3.5-flash').trim(),
    // Separate free-tier quota: used once if the main model is rate-limited or overloaded.
    fallbackModel: (env.GEMINI_FALLBACK_MODEL ?? 'gemini-3.5-flash-lite').trim() || null
  };
  if (openai) return { name: 'openai', apiKey: openai, model: (env.OPENAI_MODEL || 'gpt-4.1-mini').trim() };
  return null;
}

/** Converts the JSON Schema above to Gemini's OpenAPI-subset schema (UPPERCASE types, no additionalProperties). */
export function toGeminiSchema(s) {
  if (Array.isArray(s)) return s.map(toGeminiSchema);
  if (!s || typeof s !== 'object') return s;
  const out = {};
  for (const [k, v] of Object.entries(s)) {
    if (k === 'additionalProperties') continue;
    if (k === 'type') out.type = String(v).toUpperCase();
    else if (k === 'properties') out.properties = Object.fromEntries(Object.entries(v).map(([pk, pv]) => [pk, toGeminiSchema(pv)]));
    else if (k === 'items') out.items = toGeminiSchema(v);
    else out[k] = v;
  }
  return out;
}

const TIMEOUT_MS = 45_000;
const geminiUrl = (model) => `https://generativelanguage.googleapis.com/v1beta/models/${encodeURIComponent(model)}:generateContent`;
const geminiText = (json) => (json.candidates?.[0]?.content?.parts || []).filter((p) => !p.thought && typeof p.text === 'string').map((p) => p.text).join('').trim();

async function postJson(fetchImpl, url, headers, body) {
  return fetchImpl(url, { method: 'POST', headers: { 'content-type': 'application/json', ...headers }, body: JSON.stringify(body), signal: AbortSignal.timeout(TIMEOUT_MS) });
}

/** Chat completion. Returns { answer } or { error, status? }. */
export async function chatWithProvider({ provider, messages, fetchImpl = fetch }) {
  if (provider.name === 'gemini') {
    const r = await postJson(fetchImpl, geminiUrl(provider.model), { 'x-goog-api-key': provider.apiKey }, {
      systemInstruction: { parts: [{ text: instructions }] },
      contents: messages.map(({ role, content }) => ({ role: role === 'assistant' ? 'model' : 'user', parts: [{ text: content.trim() }] })),
      // Thinking tokens count against the limit on Gemini, so leave headroom.
      generationConfig: { maxOutputTokens: 4096, temperature: 0.4 }
    });
    if (!r.ok) return { error: 'upstream', status: r.status };
    const answer = geminiText(await r.json());
    return answer ? { answer } : { error: 'Dịch vụ AI không trả về nội dung.' };
  }
  const r = await postJson(fetchImpl, 'https://api.openai.com/v1/responses', { authorization: `Bearer ${provider.apiKey}` }, {
    model: provider.model, store: false, instructions, max_output_tokens: 500,
    input: messages.map(({ role, content }) => ({ role, content: content.trim() }))
  });
  if (!r.ok) return { error: 'upstream', status: r.status };
  const answer = outputText(await r.json());
  return answer ? { answer } : { error: 'Dịch vụ AI không trả về nội dung.' };
}

/**
 * The single model call behind /api/food-image. Exported so the accuracy harness
 * (eval-food.mjs) measures exactly what the app gets. Returns { food } or { error, status? }.
 */
export async function estimateFood({ provider, fetchImpl = fetch, image, mimeType, prompt = DEFAULT_FOOD_PROMPT, useTable = true }) {
  const foodInstructions = (FOOD_PROMPTS[prompt] ?? FOOD_PROMPTS[DEFAULT_FOOD_PROMPT]) + tableInstructions;
  let text;
  if (provider.name === 'gemini') {
    const r = await postJson(fetchImpl, geminiUrl(provider.model), { 'x-goog-api-key': provider.apiKey }, {
      systemInstruction: { parts: [{ text: foodInstructions }] },
      contents: [{ role: 'user', parts: [
        { text: 'Ước tính món ăn và dinh dưỡng trong ảnh này.' },
        { inlineData: { mimeType, data: image } }
      ] }],
      generationConfig: { responseMimeType: 'application/json', responseSchema: toGeminiSchema(foodSchema), temperature: 0.2, maxOutputTokens: 8192 }
    });
    if (!r.ok) return { error: 'upstream', status: r.status };
    text = geminiText(await r.json());
  } else {
    const r = await postJson(fetchImpl, 'https://api.openai.com/v1/responses', { authorization: `Bearer ${provider.apiKey}` }, {
      model: provider.model, store: false, instructions: foodInstructions,
      input: [{ role: 'user', content: [
        { type: 'input_text', text: 'Ước tính món ăn và dinh dưỡng trong ảnh này.' },
        { type: 'input_image', image_url: `data:${mimeType};base64,${image}`, detail: 'high' }
      ] }],
      text: { format: { type: 'json_schema', name: 'food_estimate', schema: foodSchema, strict: true } },
      max_output_tokens: 1200, temperature: 0.2
    });
    if (!r.ok) return { error: 'upstream', status: r.status };
    text = outputText(await r.json());
  }
  let parsed;
  try { parsed = JSON.parse(text || 'null'); } catch { parsed = null; }
  const food = sanitizeFood(parsed);
  if (!food) return { error: 'AI trả về dữ liệu không đúng định dạng.' };
  return { food: useTable ? applyFoodTable(food) : { ...food, foods: food.foods.map(({ table_key, ...f }) => ({ ...f, ai_calories: f.calories, source: 'ai' })) } };
}

const RETRYABLE = new Set([429, 500, 503]);

/**
 * Runs `call(provider)`; if the provider answers 429/500/503 and has a fallbackModel, retries once with it.
 * Returns the result plus the model that produced it.
 */
export async function withFallback(provider, call) {
  const first = await call(provider);
  if (!first.status || !RETRYABLE.has(first.status) || !provider.fallbackModel || provider.fallbackModel === provider.model) {
    return { ...first, model: provider.model };
  }
  const second = await call({ ...provider, model: provider.fallbackModel });
  return { ...second, model: provider.fallbackModel, fellBack: true };
}

const upstreamError = (status) => (
  status === 401 || status === 403 ? 'Khóa API backend không hợp lệ hoặc không có quyền.'
    : status === 429 ? 'Dịch vụ AI đang giới hạn lượt dùng (gói miễn phí) — thử lại sau ít phút.'
      : 'Dịch vụ AI đang lỗi hoặc chưa có quyền sử dụng model.');

export function createServer({
  provider: providerOption,
  apiKey, // legacy/test shortcut: an OpenAI key
  model,
  fetchImpl = fetch,
  mock = process.env.MOCK_AI === '1'
} = {}) {
  const provider = providerOption !== undefined ? providerOption
    : apiKey !== undefined ? (apiKey ? { name: 'openai', apiKey, model: model || 'gpt-4.1-mini' } : null)
      : resolveProvider();
  const hits = new Map();
  const limited = (req) => {
    const now = Date.now();
    const address = req.socket.remoteAddress || 'local';
    const recent = (hits.get(address) || []).filter((time) => now - time < 60_000);
    if (recent.length >= 10) return true;
    recent.push(now);
    hits.set(address, recent);
    return false;
  };

  return http.createServer(async (req, res) => {
    if (req.method === 'GET' && req.url === '/health') {
      return send(res, 200, { status: 'ok', configured: Boolean(provider), provider: provider?.name ?? null, model: provider?.model ?? null, mock: mock && !provider });
    }
    const route = req.method === 'POST' && (req.url === '/api/chat' || req.url === '/api/food-image') ? req.url : null;
    if (!route) return send(res, 404, { error: 'Không tìm thấy.' });
    if (!provider && !mock) return send(res, 503, { error: 'Backend chưa có GEMINI_API_KEY hoặc OPENAI_API_KEY.' });
    if (limited(req)) return send(res, 429, { error: 'Vui lòng thử lại sau một phút.' });

    try {
      if (!req.headers['content-type']?.startsWith('application/json')) return send(res, 415, { error: 'Chỉ nhận JSON.' });

      if (route === '/api/chat') {
        const body = await readBody(req, 16_384);
        if (!validMessages(body?.messages)) return send(res, 400, { error: 'Nội dung hội thoại không hợp lệ.' });
        if (!provider) return send(res, 200, { answer: '[GIẢ LẬP] Đây là câu trả lời mẫu, không phải từ AI.' });
        const result = await withFallback(provider, (p) => chatWithProvider({ provider: p, messages: body.messages, fetchImpl }));
        if (result.error) return send(res, 502, { error: result.status ? upstreamError(result.status) : result.error });
        return send(res, 200, { answer: result.answer, model: result.model });
      }

      // /api/food-image
      const body = await readBody(req, MAX_IMAGE_BASE64 + 1024);
      const image = typeof body?.image === 'string' ? body.image : '';
      const mimeType = body?.mimeType;
      if (!IMAGE_TYPES.has(mimeType)) return send(res, 400, { error: 'Định dạng ảnh không hỗ trợ (JPEG, PNG, WebP).' });
      if (image.length < 100 || image.length > MAX_IMAGE_BASE64 || !/^[A-Za-z0-9+/]+=*$/.test(image)) return send(res, 400, { error: 'Ảnh không hợp lệ hoặc quá lớn.' });
      if (!provider) return send(res, 200, { ...MOCK_FOOD, mock: true });

      const result = await withFallback(provider, (p) => estimateFood({ provider: p, fetchImpl, image, mimeType }));
      if (result.error) return send(res, 502, { error: result.status ? upstreamError(result.status) : result.error });
      return send(res, 200, { ...result.food, mock: false, model: result.model });
    } catch (error) {
      return send(res, error.status || 502, { error: error.status ? error.message : 'Không kết nối được dịch vụ AI.' });
    }
  });
}

if (process.argv[1] && fileURLToPath(import.meta.url) === process.argv[1]) {
  const port = Number(process.env.PORT || 8787);
  const provider = resolveProvider();
  createServer({ provider }).listen(port, '127.0.0.1', () => {
    process.stdout.write(`Health AI backend listening on 127.0.0.1:${port} — ${provider ? `${provider.name} / ${provider.model}` : 'NO KEY (requests return 503)'}\n`);
  });
}

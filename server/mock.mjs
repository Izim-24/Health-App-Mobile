// Development only: runs the backend with MOCK data when no OPENAI_API_KEY is set.
// Every mock response is flagged ("mock": true / "[GIẢ LẬP]") and the app shows a warning banner.
import { createServer } from './index.mjs';

const port = Number(process.env.PORT || 8787);
const hasKey = Boolean(process.env.GEMINI_API_KEY || process.env.OPENAI_API_KEY);
createServer({ mock: true }).listen(port, '127.0.0.1', () => {
  process.stdout.write(hasKey
    ? `Health AI backend on 127.0.0.1:${port} — key found, using the REAL AI (mock only applies without a key)\n`
    : `Health AI backend on 127.0.0.1:${port} — MOCK MODE: responses are fake sample data\n`);
});

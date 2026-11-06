import test from "node:test";
import assert from "node:assert/strict";
import worker from "../src/index.js";

const realFetch = globalThis.fetch;
const calls = [];
function mockFetch(handler) {
  globalThis.fetch = async (url, init) => {
    calls.push({ url: String(url), init });
    return handler(String(url), init);
  };
}
test.afterEach(() => { globalThis.fetch = realFetch; calls.length = 0; });

const kv = () => {
  const m = new Map();
  return {
    m,
    get: async (k) => m.get(k)?.v ?? null,
    getWithMetadata: async (k) => ({ value: m.get(k)?.v ?? null, metadata: m.get(k)?.meta ?? null }),
    put: async (k, v, o) => void m.set(k, { v, meta: o && o.metadata }),
  };
};
const req = (path, { method = "GET", headers = {}, body } = {}) =>
  new Request("https://x" + path, { method, headers, body: body ? JSON.stringify(body) : undefined });
const job = { kind: "TEXT_TO_IMAGE", prompt: "a cat", style: "anime", aspect: "16:9", durationSec: 5 };
const j = async (r) => ({ status: r.status, body: await r.json() });

test("health works without auth", async () => {
  const r = await j(await worker.fetch(req("/health"), {}));
  assert.equal(r.status, 200);
  assert.equal(r.body.ok, true);
});

test("APP_TOKEN is enforced when set", async () => {
  const env = { APP_TOKEN: "t" };
  assert.equal((await worker.fetch(req("/v1/status"), env)).status, 401);
  assert.equal((await worker.fetch(req("/v1/status", { headers: { "x-app-token": "t" } }), env)).status, 200);
});

test("status reports owner, quota and providers", async () => {
  const env = { OWNER_KEY: "me", FAL_KEY: "k", QUOTA: kv() };
  const owner = await j(await worker.fetch(req("/v1/status", { headers: { "x-owner-key": "me" } }), env));
  assert.deepEqual(owner.body, { ok: true, owner: true, quota: true, ai: false, providers: ["fal"] });
  const pub = await j(await worker.fetch(req("/v1/status"), env));
  assert.equal(pub.body.owner, false);
});

test("validation errors", async () => {
  const env = { FAL_KEY: "k" };
  const post = (body) => worker.fetch(req("/v1/jobs", { method: "POST", body }), env);
  assert.equal((await post({ ...job, prompt: "  " })).status, 400);
  assert.equal((await post({ ...job, kind: "NOPE" })).status, 400);
  assert.equal((await post({ ...job, kind: "IMAGE_EDIT" })).status, 400);
  assert.equal((await post({ ...job, prompt: "x".repeat(2001) })).status, 400);
});

test("503 when no provider is configured", async () => {
  const r = await j(await worker.fetch(req("/v1/jobs", { method: "POST", body: job }), {}));
  assert.equal(r.status, 503);
});

test("fal text-to-image maps aspect to image_size and round-trips", async () => {
  mockFetch((url, init) => {
    if (url === "https://queue.fal.run/fal-ai/flux/schnell")
      return new Response(JSON.stringify({ status_url: "https://queue.fal.run/s", response_url: "https://queue.fal.run/r" }));
    if (url === "https://queue.fal.run/s") return new Response(JSON.stringify({ status: "COMPLETED" }));
    if (url === "https://queue.fal.run/r") return new Response(JSON.stringify({ images: [{ url: "https://cdn/x.png" }] }));
    return new Response("{}", { status: 404 });
  });
  const env = { FAL_KEY: "k" };
  const created = await j(await worker.fetch(req("/v1/jobs", { method: "POST", body: job }), env));
  assert.equal(created.status, 200);
  const sent = JSON.parse(calls[0].init.body);
  assert.equal(sent.image_size, "landscape_16_9");
  assert.equal(calls[0].init.headers.Authorization, "Key k");
  const done = await j(await worker.fetch(req("/v1/jobs/" + encodeURIComponent(created.body.id)), env));
  assert.equal(done.body.status, "DONE");
  assert.equal(done.body.resultUrl, "https://cdn/x.png");
});

test("provider errors surface readable messages", async () => {
  mockFetch(() => new Response(JSON.stringify({ detail: "Exhausted balance" }), { status: 403 }));
  const r = await j(await worker.fetch(req("/v1/jobs", { method: "POST", body: job }), { FAL_KEY: "k" }));
  assert.equal(r.status, 502);
  assert.match(r.body.error, /Exhausted balance/);
});

test("replicate flow and failure states", async () => {
  mockFetch((url, init) => {
    if (init && init.method === "POST") return new Response(JSON.stringify({ id: "p1" }), { status: 201 });
    return new Response(JSON.stringify({ status: "failed", error: "boom" }));
  });
  const env = { REPLICATE_TOKEN: "t", PROVIDER_ORDER: "replicate" };
  const created = await j(await worker.fetch(req("/v1/jobs", { method: "POST", body: job }), env));
  const st = await j(await worker.fetch(req("/v1/jobs/" + encodeURIComponent(created.body.id)), env));
  assert.equal(st.body.status, "FAILED");
  assert.equal(st.body.error, "boom");
});

test("daily quota for public users, owner is unlimited", async () => {
  mockFetch(() => new Response(JSON.stringify({ status_url: "s", response_url: "r" })));
  const env = { FAL_KEY: "k", OWNER_KEY: "me", QUOTA: kv(), FREE_DAILY_IMAGES: "2" };
  const post = (h) => worker.fetch(req("/v1/jobs", { method: "POST", headers: h, body: job }), env);
  assert.equal((await post({ "x-device-id": "d1" })).status, 200);
  assert.equal((await post({ "x-device-id": "d1" })).status, 200);
  assert.equal((await post({ "x-device-id": "d1" })).status, 429);
  assert.equal((await post({ "x-device-id": "d2" })).status, 200);
  assert.equal((await post({})).status, 400);
  for (let i = 0; i < 5; i++) assert.equal((await post({ "x-owner-key": "me" })).status, 200);
});

test("only the owner can choose a provider", async () => {
  mockFetch(() => new Response(JSON.stringify({ id: "x" }), { status: 201 }));
  const env = { FAL_KEY: "k", REPLICATE_TOKEN: "t", OWNER_KEY: "me", PROVIDER_ORDER: "fal,replicate" };
  const asPublic = await j(await worker.fetch(req("/v1/jobs", { method: "POST", body: { ...job, provider: "replicate" } }), env));
  assert.ok(asPublic.body.id.startsWith("fal."));
  mockFetch(() => new Response(JSON.stringify({ id: "x" }), { status: 201 }));
  const asOwner = await j(await worker.fetch(req("/v1/jobs", { method: "POST", headers: { "x-owner-key": "me" }, body: { ...job, provider: "replicate" } }), env));
  assert.ok(asOwner.body.id.startsWith("replicate."));
});

test("falls back to the next provider when the first one fails", async () => {
  mockFetch(async (url) => {
    if (url.includes("fal.run") || url.includes("queue.fal.run")) {
      return new Response(JSON.stringify({ detail: "User is locked. Reason: Exhausted balance." }), { status: 403 });
    }
    return new Response(JSON.stringify({ id: "pred1", status: "starting" }), { status: 201 });
  });
  const env = { FAL_KEY: "k", REPLICATE_TOKEN: "r" };
  const r = await j(await worker.fetch(req("/v1/jobs", { method: "POST", body: job }), env));
  assert.equal(r.status, 200);
  assert.ok(r.body.id.startsWith("replicate."));
});

test("reports every provider error when all fail", async () => {
  mockFetch(async () => new Response(JSON.stringify({ detail: "Exhausted balance" }), { status: 403 }));
  const env = { FAL_KEY: "k", REPLICATE_TOKEN: "r" };
  const r = await j(await worker.fetch(req("/v1/jobs", { method: "POST", body: job }), env));
  assert.equal(r.status, 502);
  assert.match(r.body.error, /Exhausted balance.*\|/);
});

test("failed submissions do not use up the daily quota", async () => {
  mockFetch(() => new Response(JSON.stringify({ detail: "Exhausted balance" }), { status: 403 }));
  const env = { FAL_KEY: "k", QUOTA: kv(), FREE_DAILY_IMAGES: "1" };
  const post = () => worker.fetch(req("/v1/jobs", { method: "POST", headers: { "x-device-id": "d" }, body: job }), env);
  for (let i = 0; i < 3; i++) {
    const r = await j(await post());
    assert.equal(r.status, 502);
    assert.equal(r.body.code, "BALANCE");
  }
});

test("fal video: LTX gets no aspect/duration, other models do; negative prompt is sent", async () => {
  mockFetch(() => new Response(JSON.stringify({ status_url: "s", response_url: "r" })));
  const v = { ...job, kind: "TEXT_TO_VIDEO", durationSec: 10 };
  await worker.fetch(req("/v1/jobs", { method: "POST", body: v }), { FAL_KEY: "k" });
  let sent = JSON.parse(calls[0].init.body);
  assert.equal(sent.aspect_ratio, undefined);
  assert.ok(sent.negative_prompt);
  calls.length = 0;
  mockFetch(() => new Response(JSON.stringify({ status_url: "s", response_url: "r" })));
  await worker.fetch(req("/v1/jobs", { method: "POST", body: v }), { FAL_KEY: "k", FAL_MODEL_T2V: "fal-ai/kling-video/v2/master/text-to-video" });
  sent = JSON.parse(calls[0].init.body);
  assert.equal(sent.aspect_ratio, "16:9");
  assert.equal(sent.duration, "10");
});

test("fal job that completes with an error is reported as FAILED", async () => {
  mockFetch((url) => {
    if (url === "https://queue.fal.run/fal-ai/flux/schnell") return new Response(JSON.stringify({ status_url: "https://q/s", response_url: "https://q/r" }));
    if (url === "https://q/s") return new Response(JSON.stringify({ status: "COMPLETED" }));
    return new Response(JSON.stringify({ detail: [{ msg: "bad input" }] }), { status: 422 });
  });
  const env = { FAL_KEY: "k" };
  const created = await j(await worker.fetch(req("/v1/jobs", { method: "POST", body: job }), env));
  const st = await j(await worker.fetch(req("/v1/jobs/" + encodeURIComponent(created.body.id)), env));
  assert.equal(st.body.status, "FAILED");
  assert.match(st.body.error, /bad input/);
});

const fakeAI = (calls) => ({
  run: async (model, input) => {
    calls.push({ model, input });
    if (model.includes("flux")) return { image: btoa("JPEGDATA") };
    return { response: "\"A lone astronaut walks across a violet desert at dusk, cinematic.\"" };
  },
});

test("Arabic prompts are turned into English smart prompts before generation", async () => {
  mockFetch(() => new Response(JSON.stringify({ status_url: "s", response_url: "r" })));
  const ai = [];
  const env = { FAL_KEY: "k", AI: fakeAI(ai) };
  const r = await j(await worker.fetch(req("/v1/jobs", { method: "POST", body: { ...job, prompt: "رائد فضاء في صحراء بنفسجية" } }), env));
  assert.equal(r.status, 200);
  assert.equal(r.body.finalPrompt, "A lone astronaut walks across a violet desert at dusk, cinematic.");
  assert.equal(JSON.parse(calls[0].init.body).prompt, r.body.finalPrompt);
  // English prompts without smart mode are sent as they are
  ai.length = 0;
  const plain = await j(await worker.fetch(req("/v1/jobs", { method: "POST", body: job }), env));
  assert.equal(plain.body.finalPrompt, null);
  assert.equal(ai.length, 0);
});

test("assist enhance and surprise", async () => {
  const ai = [];
  const env = { AI: fakeAI(ai) };
  const e = await j(await worker.fetch(req("/v1/assist", { method: "POST", body: { mode: "enhance", prompt: "cat", kind: "TEXT_TO_IMAGE", style: "anime" } }), env));
  assert.equal(e.status, 200);
  assert.match(e.body.prompt, /astronaut/);
  const s = await j(await worker.fetch(req("/v1/assist", { method: "POST", body: { mode: "surprise", lang: "ar" } }), env));
  assert.equal(s.status, 200);
  assert.match(ai[1].input.messages[0].content, /Arabic/);
  const none = await j(await worker.fetch(req("/v1/assist", { method: "POST", body: { mode: "enhance", prompt: "x" } }), {}));
  assert.equal(none.status, 503);
});

test("free Cloudflare image provider stores the image and serves it", async () => {
  const ai = [];
  const store = kv();
  const env = { AI: fakeAI(ai), QUOTA: store };
  const r = await j(await worker.fetch(req("/v1/jobs", { method: "POST", headers: { "x-device-id": "d" }, body: job }), env));
  assert.equal(r.status, 200);
  assert.equal(r.body.provider, "cf");
  const st = await j(await worker.fetch(req("/v1/jobs/" + encodeURIComponent(r.body.id)), env));
  assert.equal(st.body.status, "DONE");
  assert.match(st.body.resultUrl, /^https:\/\/x\/v1\/files\/[0-9a-f]{32}$/);
  const file = await worker.fetch(new Request(st.body.resultUrl), { ...env, APP_TOKEN: "secret" });
  assert.equal(file.status, 200);
  assert.equal(await file.text(), "JPEGDATA");
  // video is not offered by cf
  const v = await j(await worker.fetch(req("/v1/jobs", { method: "POST", headers: { "x-device-id": "d" }, body: { ...job, kind: "TEXT_TO_VIDEO" } }), env));
  assert.equal(v.status, 503);
  assert.equal(v.body.code, "NO_PROVIDER");
});

test("cleanLlm strips labels and quotes", async () => {
  const { cleanLlm } = await import("../src/index.js");
  assert.equal(cleanLlm('Prompt: "hello world"'), "hello world");
  assert.equal(cleanLlm("```\nhi\n```"), "hi");
});

test("bad job ids are 404, not 500", async () => {
  assert.equal((await worker.fetch(req("/v1/jobs/fal.%%%"), {})).status, 404);
  assert.equal((await worker.fetch(req("/v1/jobs/nope"), {})).status, 404);
});

test("parseStory reads title and scenes in many shapes", async () => {
  const { parseStory } = await import("../src/index.js");
  const s = parseStory("TITLE: The Lantern\nSCENE: A boy in a red coat walks into an old Damascus street at dusk.\n**SCENE 2:** The boy in the red coat lights a lantern, warm glow fills the alley.\n3. The boy in the red coat smiles as lanterns rise into the night sky.", 3);
  assert.equal(s.title, "The Lantern");
  assert.equal(s.scenes.length, 3);
  assert.match(s.scenes[1], /lights a lantern/);
  const ar = parseStory("العنوان: الفانوس\nالمشهد 1: صبي بمعطف أحمر يدخل شارعًا دمشقيًا قديمًا عند الغروب\nالمشهد 2: الصبي بالمعطف الأحمر يشعل فانوسًا ويملأ الضوء الزقاق", 4);
  assert.equal(ar.title, "الفانوس");
  assert.equal(ar.scenes.length, 2);
});

test("story assist returns scenes", async () => {
  const env = { AI: { run: async () => ({ response: "TITLE: Waves\nSCENE: A surfer in a yellow suit paddles out at sunrise over calm water.\nSCENE: The surfer in the yellow suit rides a huge glassy wave, camera tracking." }) } };
  const r = await j(await worker.fetch(req("/v1/assist", { method: "POST", body: { mode: "story", prompt: "surfer", scenes: 2 } }), env));
  assert.equal(r.status, 200);
  assert.equal(r.body.title, "Waves");
  assert.equal(r.body.scenes.length, 2);
});

test("pro quality: owner only, uses premium fal model with seed", async () => {
  mockFetch(() => new Response(JSON.stringify({ status_url: "s", response_url: "r" })));
  const env = { FAL_KEY: "k", OWNER_KEY: "me", REPLICATE_TOKEN: "t" };
  const v = { ...job, kind: "TEXT_TO_VIDEO", quality: "pro", seed: 42 };
  const pub = await j(await worker.fetch(req("/v1/jobs", { method: "POST", body: v }), env));
  assert.equal(pub.status, 403);
  assert.equal(pub.body.code, "PRO_LOCKED");
  const own = await j(await worker.fetch(req("/v1/jobs", { method: "POST", headers: { "x-owner-key": "me" }, body: v }), env));
  assert.equal(own.status, 200);
  assert.match(calls[0].url, /kling-video/);
  const sent = JSON.parse(calls[0].init.body);
  assert.equal(sent.seed, 42);
  assert.equal(sent.duration, "5");
  // with fal failing, pro video does not silently fall back to replicate's basic model
  mockFetch(() => new Response(JSON.stringify({ detail: "Exhausted balance" }), { status: 403 }));
  const fail = await j(await worker.fetch(req("/v1/jobs", { method: "POST", headers: { "x-owner-key": "me" }, body: v }), env));
  assert.equal(fail.body.code, "BALANCE");
});

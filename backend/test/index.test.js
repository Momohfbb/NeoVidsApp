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

const kv = () => { const m = new Map(); return { get: async (k) => m.get(k) ?? null, put: async (k, v) => void m.set(k, v) }; };
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
  assert.deepEqual(owner.body, { ok: true, owner: true, quota: true, providers: ["fal"] });
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

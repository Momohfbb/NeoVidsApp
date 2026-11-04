// NeoVids AI backend: one small, stateless API in front of many generation providers.
// API keys stay here (Cloudflare secrets). The app only ever talks to this server.
//
//   POST /v1/jobs        { kind, prompt, style, aspect, durationSec, sourceImage?, provider? } -> { id, status }
//   GET  /v1/jobs/:id    -> { id, status: QUEUED|RUNNING|DONE|FAILED, progress, resultUrl?, error? }
//   GET  /v1/providers   -> providers that are configured and what they support
//
// Job ids are "<provider>.<base64url(json state)>", so no database is needed.

const b64 = {
  enc: (o) => btoa(JSON.stringify(o)).replace(/\+/g, "-").replace(/\//g, "_").replace(/=+$/, ""),
  dec: (s) => JSON.parse(atob(s.replace(/-/g, "+").replace(/_/g, "/"))),
};

const STYLE_HINT = {
  cinematic: "cinematic lighting, film look, shallow depth of field",
  anime: "anime style, vibrant colors, clean line art",
  realistic: "photorealistic, natural light, high detail",
  "3d": "3D render, octane, soft studio lighting",
  watercolor: "watercolor painting, soft washes, paper texture",
};

const fullPrompt = (r) =>
  r.kind === "IMAGE_EDIT" ? r.prompt : `${r.prompt}. ${STYLE_HINT[r.style] || ""}`.trim();

class HttpError extends Error {
  constructor(status, message) { super(message); this.status = status; }
}

async function call(url, init, what) {
  const res = await fetch(url, init);
  const text = await res.text();
  let json = null;
  try { json = JSON.parse(text); } catch { /* not json */ }
  if (!res.ok) {
    const d = json && (json.detail ?? json.error ?? json.message);
    const msg = d == null ? text.slice(0, 200) : typeof d === "string" ? d : JSON.stringify(d).slice(0, 300);
    throw new HttpError(502, `${what}: ${res.status} ${msg}`);
  }
  return json;
}

const firstUrl = (out) => {
  if (!out) return null;
  if (typeof out === "string") return out;
  if (Array.isArray(out)) return firstUrl(out[0]);
  if (out.video && out.video.url) return out.video.url;
  if (out.url) return out.url;
  if (out.images && out.images[0]) return firstUrl(out.images[0]);
  return null;
};

// ---------------------------------------------------------------- providers
// Model names change often; every default can be overridden with an env var.

const replicate = {
  name: "replicate",
  kinds: ["TEXT_TO_VIDEO", "IMAGE_TO_VIDEO", "TEXT_TO_IMAGE", "IMAGE_EDIT"],
  configured: (env) => !!env.REPLICATE_TOKEN,
  model(env, kind) {
    return {
      TEXT_TO_VIDEO: env.REPLICATE_MODEL_T2V || "minimax/video-01",
      IMAGE_TO_VIDEO: env.REPLICATE_MODEL_I2V || "minimax/video-01",
      TEXT_TO_IMAGE: env.REPLICATE_MODEL_T2I || "black-forest-labs/flux-schnell",
      IMAGE_EDIT: env.REPLICATE_MODEL_EDIT || "black-forest-labs/flux-kontext-pro",
    }[kind];
  },
  async submit(env, r) {
    const input = { prompt: fullPrompt(r) };
    if (r.kind === "IMAGE_TO_VIDEO") input.first_frame_image = r.sourceImage;
    if (r.kind === "IMAGE_EDIT") input.input_image = r.sourceImage;
    if (r.kind === "TEXT_TO_IMAGE") input.aspect_ratio = r.aspect;
    const p = await call(
      `https://api.replicate.com/v1/models/${replicate.model(env, r.kind)}/predictions`,
      {
        method: "POST",
        headers: { Authorization: `Bearer ${env.REPLICATE_TOKEN}`, "Content-Type": "application/json" },
        body: JSON.stringify({ input }),
      },
      "Replicate",
    );
    return { id: p.id };
  },
  async poll(env, s) {
    const p = await call(`https://api.replicate.com/v1/predictions/${s.id}`, {
      headers: { Authorization: `Bearer ${env.REPLICATE_TOKEN}` },
    }, "Replicate");
    if (p.status === "succeeded") return { status: "DONE", progress: 1, resultUrl: firstUrl(p.output) };
    if (p.status === "failed" || p.status === "canceled") return { status: "FAILED", error: String(p.error || p.status) };
    return p.status === "starting" ? { status: "QUEUED", progress: 0.1 } : { status: "RUNNING", progress: 0.5 };
  },
};

const fal = {
  name: "fal",
  kinds: ["TEXT_TO_VIDEO", "IMAGE_TO_VIDEO", "TEXT_TO_IMAGE", "IMAGE_EDIT"],
  configured: (env) => !!env.FAL_KEY,
  model(env, kind) {
    return {
      TEXT_TO_VIDEO: env.FAL_MODEL_T2V || "fal-ai/ltx-video",
      IMAGE_TO_VIDEO: env.FAL_MODEL_I2V || "fal-ai/ltx-video/image-to-video",
      TEXT_TO_IMAGE: env.FAL_MODEL_T2I || "fal-ai/flux/schnell",
      IMAGE_EDIT: env.FAL_MODEL_EDIT || "fal-ai/flux-pro/kontext",
    }[kind];
  },
  async submit(env, r) {
    const input = { prompt: fullPrompt(r) };
    if (r.kind === "TEXT_TO_IMAGE") {
      // fal image models take image_size presets rather than aspect_ratio
      input.image_size = { "16:9": "landscape_16_9", "9:16": "portrait_16_9", "1:1": "square_hd" }[r.aspect] || "square_hd";
    } else if (r.kind !== "IMAGE_EDIT") {
      input.aspect_ratio = r.aspect;
    }
    if (r.sourceImage) input.image_url = r.sourceImage;
    const q = await call(`https://queue.fal.run/${fal.model(env, r.kind)}`, {
      method: "POST",
      headers: { Authorization: `Key ${env.FAL_KEY}`, "Content-Type": "application/json" },
      body: JSON.stringify(input),
    }, "fal");
    return { status_url: q.status_url, response_url: q.response_url };
  },
  async poll(env, s) {
    const h = { Authorization: `Key ${env.FAL_KEY}` };
    const st = await call(s.status_url, { headers: h }, "fal");
    if (st.status === "COMPLETED") {
      const out = await call(s.response_url, { headers: h }, "fal");
      return { status: "DONE", progress: 1, resultUrl: firstUrl(out) };
    }
    return st.status === "IN_QUEUE" ? { status: "QUEUED", progress: 0.1 } : { status: "RUNNING", progress: 0.5 };
  },
};

const luma = {
  name: "luma",
  kinds: ["TEXT_TO_VIDEO"],
  configured: (env) => !!env.LUMA_KEY,
  async submit(env, r) {
    const g = await call("https://api.lumalabs.ai/dream-machine/v1/generations", {
      method: "POST",
      headers: { Authorization: `Bearer ${env.LUMA_KEY}`, "Content-Type": "application/json" },
      body: JSON.stringify({ prompt: fullPrompt(r), aspect_ratio: r.aspect, model: env.LUMA_MODEL || "ray-2" }),
    }, "Luma");
    return { id: g.id };
  },
  async poll(env, s) {
    const g = await call(`https://api.lumalabs.ai/dream-machine/v1/generations/${s.id}`, {
      headers: { Authorization: `Bearer ${env.LUMA_KEY}` },
    }, "Luma");
    if (g.state === "completed") return { status: "DONE", progress: 1, resultUrl: g.assets && g.assets.video };
    if (g.state === "failed") return { status: "FAILED", error: g.failure_reason || "failed" };
    return g.state === "queued" ? { status: "QUEUED", progress: 0.1 } : { status: "RUNNING", progress: 0.5 };
  },
};

// Add a new provider = write one object like the ones above and list it here.
const PROVIDERS = { replicate, fal, luma };

// ---------------------------------------------------------------- http
const CORS = {
  "Access-Control-Allow-Origin": "*",
  "Access-Control-Allow-Headers": "content-type,x-app-token,x-owner-key,x-device-id",
  "Access-Control-Allow-Methods": "GET,POST,OPTIONS",
};
const json = (obj, status = 200) =>
  new Response(JSON.stringify(obj), { status, headers: { "content-type": "application/json", ...CORS } });

function candidatesFor(env, req) {
  const order = (env.PROVIDER_ORDER || "fal,replicate,luma").split(",").map((s) => s.trim());
  const names = req.provider ? [req.provider] : order;
  const list = names.map((n) => PROVIDERS[n]).filter((p) => p && p.configured(env) && p.kinds.includes(req.kind));
  if (!list.length) throw new HttpError(503, "No configured provider supports this request. Set a provider key (see backend/README.md).");
  return list;
}

// Tries each configured provider in order; if one fails (empty balance, outage), the next one is used.
async function submitWithFallback(env, req) {
  const errors = [];
  for (const p of candidatesFor(env, req)) {
    try {
      return { provider: p, state: await p.submit(env, req) };
    } catch (e) {
      errors.push(e.message || String(e));
    }
  }
  throw new HttpError(502, errors.join(" | "));
}

// ---------------------------------------------------------------- access control
// Owner (x-owner-key == OWNER_KEY): no limits from this server and may pick any provider.
// Everyone else: daily quotas per device, set by you with FREE_DAILY_VIDEOS / FREE_DAILY_IMAGES.
// Quotas need a KV namespace bound as QUOTA; without it they are not enforced.
const isOwner = (request, env) => !!env.OWNER_KEY && request.headers.get("x-owner-key") === env.OWNER_KEY;

async function checkQuota(request, env, kind) {
  if (!env.QUOTA) return;
  const device = (request.headers.get("x-device-id") || "").slice(0, 64);
  if (!device) throw new HttpError(400, "x-device-id header is required");
  const video = kind === "TEXT_TO_VIDEO" || kind === "IMAGE_TO_VIDEO";
  const limit = parseInt((video ? env.FREE_DAILY_VIDEOS : env.FREE_DAILY_IMAGES) ?? (video ? "3" : "10"), 10);
  const key = `q:${device}:${video ? "v" : "i"}:${new Date().toISOString().slice(0, 10)}`;
  const used = parseInt((await env.QUOTA.get(key)) || "0", 10);
  if (used >= limit) throw new HttpError(429, `Daily limit reached (${limit} ${video ? "videos" : "images"}). Try again tomorrow.`);
  await env.QUOTA.put(key, String(used + 1), { expirationTtl: 172800 });
}

export default {
  async fetch(request, env) {
    if (request.method === "OPTIONS") return new Response(null, { headers: CORS });
    try {
      if (env.APP_TOKEN && request.headers.get("x-app-token") !== env.APP_TOKEN) throw new HttpError(401, "Unauthorized");
      const url = new URL(request.url);
      const path = url.pathname;

      if (path === "/" || path === "/health") return json({ ok: true, service: "neovids-backend" });

      if (path === "/v1/status" && request.method === "GET") {
        return json({
          ok: true,
          owner: isOwner(request, env),
          quota: !!env.QUOTA,
          providers: Object.values(PROVIDERS).filter((p) => p.configured(env)).map((p) => p.name),
        });
      }

      if (path === "/v1/providers" && request.method === "GET") {
        return json(Object.values(PROVIDERS).filter((p) => p.configured(env)).map((p) => ({ name: p.name, kinds: p.kinds })));
      }

      if (path === "/v1/jobs" && request.method === "POST") {
        const body = await request.json();
        if (!body.prompt || !String(body.prompt).trim()) throw new HttpError(400, "prompt is required");
        if (String(body.prompt).length > 2000) throw new HttpError(400, "prompt too long");
        if (!["TEXT_TO_VIDEO", "IMAGE_TO_VIDEO", "TEXT_TO_IMAGE", "IMAGE_EDIT"].includes(body.kind)) throw new HttpError(400, "invalid kind");
        if ((body.kind === "IMAGE_TO_VIDEO" || body.kind === "IMAGE_EDIT") && !body.sourceImage) throw new HttpError(400, "sourceImage is required");
        const owner = isOwner(request, env);
        if (!owner) {
          delete body.provider; // only the owner can choose a provider
          await checkQuota(request, env, body.kind);
        }
        const { provider, state } = await submitWithFallback(env, body);
        return json({ id: `${provider.name}.${b64.enc(state)}`, status: "QUEUED", progress: 0 });
      }

      const m = path.match(/^\/v1\/jobs\/(.+)$/);
      if (m && request.method === "GET") {
        const id = decodeURIComponent(m[1]);
        const dot = id.indexOf(".");
        const provider = PROVIDERS[id.slice(0, dot)];
        if (!provider) throw new HttpError(404, "unknown job");
        const r = await provider.poll(env, b64.dec(id.slice(dot + 1)));
        if (r.status === "DONE" && !r.resultUrl) return json({ id, status: "FAILED", error: "provider returned no result" });
        return json({ id, progress: 0, ...r });
      }

      throw new HttpError(404, "not found");
    } catch (e) {
      const status = e instanceof HttpError ? e.status : 500;
      return json({ error: e.message || "error" }, status);
    }
  },
};

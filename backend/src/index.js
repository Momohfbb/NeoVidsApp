// NeoVids AI backend: one small, stateless API in front of many generation providers.
// API keys stay here (Cloudflare secrets). The app only ever talks to this server.
//
//   POST /v1/jobs        { kind, prompt, style, aspect, durationSec, sourceImage?, provider?, smart? }
//                        -> { id, status, finalPrompt }
//   GET  /v1/jobs/:id    -> { id, status: QUEUED|RUNNING|DONE|FAILED, progress, resultUrl?, error?, code? }
//   POST /v1/assist      { mode: "enhance"|"surprise", prompt?, kind, style, lang } -> { prompt }
//                        { mode: "story", prompt, scenes: 3..6, style, lang } -> { title, scenes: [string] }
//   Jobs also accept quality: "fast"|"pro" (pro = premium licensed video models, owner only unless PRO_PUBLIC=1)
//   and seed (int) so that several variations of one prompt differ.
//   GET  /v1/status      -> { ok, owner, quota, ai, providers }
//   GET  /v1/providers   -> providers that are configured and what they support
//   GET  /v1/files/:key  -> images made by the built-in Cloudflare AI provider (public, unguessable keys)
//
// Job ids are "<provider>.<base64url(json state)>", so no database is needed.
// Errors are { error, code } where code is one of:
//   BAD_REQUEST, UNAUTHORIZED, QUOTA, BALANCE, NO_PROVIDER, PROVIDER, NOT_FOUND, INTERNAL

const b64 = {
  enc: (o) => {
    const bytes = new TextEncoder().encode(JSON.stringify(o));
    let s = "";
    for (const b of bytes) s += String.fromCharCode(b);
    return btoa(s).replace(/\+/g, "-").replace(/\//g, "_").replace(/=+$/, "");
  },
  dec: (s) => {
    const bin = atob(s.replace(/-/g, "+").replace(/_/g, "/"));
    return JSON.parse(new TextDecoder().decode(Uint8Array.from(bin, (c) => c.charCodeAt(0))));
  },
};

const STYLE_HINT = {
  cinematic: "cinematic lighting, anamorphic film look, shallow depth of field, rich color grading",
  anime: "anime style, vibrant colors, clean line art, studio-quality animation",
  realistic: "photorealistic, natural light, high detail, 8k, sharp focus",
  "3d": "3D render, octane render, soft studio lighting, high detail",
  watercolor: "watercolor painting, soft washes, paper texture, delicate brush strokes",
  cyberpunk: "cyberpunk, neon lights, rain-soaked streets, futuristic, high contrast",
  fantasy: "epic fantasy art, magical atmosphere, volumetric light, highly detailed",
  vintage: "vintage 35mm film, grain, warm faded colors, nostalgic",
};

const NEGATIVE =
  "low quality, worst quality, blurry, deformed, distorted, disfigured, watermark, text, motion smear, motion artifacts, bad anatomy, extra fingers, ugly";

const VIDEO_KINDS = ["TEXT_TO_VIDEO", "IMAGE_TO_VIDEO"];
const KINDS = ["TEXT_TO_VIDEO", "IMAGE_TO_VIDEO", "TEXT_TO_IMAGE", "IMAGE_EDIT"];

const styledPrompt = (r) => {
  const p = (r.finalPrompt || r.prompt).trim();
  if (r.kind === "IMAGE_EDIT" || r.finalPrompt) return p; // smart prompts already include the style
  return `${p}. ${STYLE_HINT[r.style] || ""}`.trim();
};

class HttpError extends Error {
  constructor(status, message, code) {
    super(message);
    this.status = status;
    this.code = code || (status === 400 ? "BAD_REQUEST" : status === 401 ? "UNAUTHORIZED" : status === 404 ? "NOT_FOUND" : status === 429 ? "QUOTA" : "PROVIDER");
  }
}

const BALANCE_RE = /exhausted balance|insufficient (credit|balance|funds)|out of credit|billing|payment required|top up/i;

async function call(url, init, what) {
  const res = await fetch(url, init);
  const text = await res.text();
  let json = null;
  try { json = JSON.parse(text); } catch { /* not json */ }
  if (!res.ok) {
    const d = json && (json.detail ?? json.error ?? json.message ?? json.title);
    const msg = d == null ? text.slice(0, 200) : typeof d === "string" ? d : JSON.stringify(d).slice(0, 300);
    const balance = res.status === 402 || BALANCE_RE.test(msg);
    throw new HttpError(502, `${what}: ${res.status} ${msg}`, balance ? "BALANCE" : "PROVIDER");
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
  if (out.image) return firstUrl(out.image);
  return null;
};

const randomKey = () => {
  const a = new Uint8Array(16);
  crypto.getRandomValues(a);
  return [...a].map((b) => b.toString(16).padStart(2, "0")).join("");
};

// ---------------------------------------------------------------- providers
// Model names change often; every default can be overridden with an env var.

const replicate = {
  name: "replicate",
  kinds: KINDS,
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
    const input = { prompt: styledPrompt(r) };
    if (r.kind === "IMAGE_TO_VIDEO") input.first_frame_image = r.sourceImage;
    if (r.kind === "IMAGE_EDIT") input.input_image = r.sourceImage;
    if (r.kind === "TEXT_TO_IMAGE") input.aspect_ratio = r.aspect || "1:1";
    if (VIDEO_KINDS.includes(r.kind)) input.prompt_optimizer = !r.finalPrompt;
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
  kinds: KINDS,
  configured: (env) => !!env.FAL_KEY,
  model(env, kind, quality) {
    if (quality === "pro") {
      const pro = {
        TEXT_TO_VIDEO: env.FAL_MODEL_T2V_PRO || "fal-ai/kling-video/v2.1/standard/text-to-video",
        IMAGE_TO_VIDEO: env.FAL_MODEL_I2V_PRO || "fal-ai/kling-video/v2.1/standard/image-to-video",
        TEXT_TO_IMAGE: env.FAL_MODEL_T2I_PRO || "fal-ai/flux/dev",
      }[kind];
      if (pro) return pro;
    }
    return {
      TEXT_TO_VIDEO: env.FAL_MODEL_T2V || "fal-ai/ltx-video",
      IMAGE_TO_VIDEO: env.FAL_MODEL_I2V || "fal-ai/ltx-video/image-to-video",
      TEXT_TO_IMAGE: env.FAL_MODEL_T2I || "fal-ai/flux/schnell",
      IMAGE_EDIT: env.FAL_MODEL_EDIT || "fal-ai/flux-pro/kontext",
    }[kind];
  },
  input(env, r) {
    const model = fal.model(env, r.kind, r.quality);
    const input = { prompt: styledPrompt(r) };
    if (Number.isInteger(r.seed)) input.seed = r.seed;
    if (r.kind === "TEXT_TO_IMAGE") {
      // fal image models take image_size presets rather than aspect_ratio
      input.image_size = { "16:9": "landscape_16_9", "9:16": "portrait_16_9", "1:1": "square_hd" }[r.aspect] || "square_hd";
    } else if (VIDEO_KINDS.includes(r.kind)) {
      input.negative_prompt = NEGATIVE;
      // LTX takes no aspect/duration; Kling, MiniMax, Wan, Veo etc. do.
      if (!/ltx/.test(model)) {
        input.aspect_ratio = r.aspect || "16:9";
        input.duration = String(r.durationSec >= 10 ? 10 : 5);
      }
    }
    if (r.sourceImage) input.image_url = r.sourceImage;
    return { model, input };
  },
  async submit(env, r) {
    const { model, input } = fal.input(env, r);
    const q = await call(`https://queue.fal.run/${model}`, {
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
      // A finished request can still have failed (validation, content policy): report it instead of retrying forever.
      try {
        const out = await call(s.response_url, { headers: h }, "fal");
        return { status: "DONE", progress: 1, resultUrl: firstUrl(out) };
      } catch (e) {
        return { status: "FAILED", error: e.message, code: e.code };
      }
    }
    if (st.status === "IN_QUEUE") return { status: "QUEUED", progress: 0.1 };
    return { status: "RUNNING", progress: 0.5 };
  },
};

const luma = {
  name: "luma",
  kinds: ["TEXT_TO_VIDEO", "IMAGE_TO_VIDEO"],
  configured: (env) => !!env.LUMA_KEY,
  async submit(env, r) {
    const body = {
      prompt: styledPrompt(r),
      aspect_ratio: r.aspect || "16:9",
      model: env.LUMA_MODEL || "ray-2",
      duration: r.durationSec >= 9 ? "9s" : "5s",
      resolution: "720p",
    };
    if (r.kind === "IMAGE_TO_VIDEO") body.keyframes = { frame0: { type: "image", url: r.sourceImage } };
    const g = await call("https://api.lumalabs.ai/dream-machine/v1/generations", {
      method: "POST",
      headers: { Authorization: `Bearer ${env.LUMA_KEY}`, "Content-Type": "application/json" },
      body: JSON.stringify(body),
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

// Free, built-in image generation with Cloudflare Workers AI (no extra account needed).
// Needs the AI binding and the QUOTA KV namespace (used to keep the images for a week).
const cf = {
  name: "cf",
  kinds: ["TEXT_TO_IMAGE"],
  configured: (env) => !!env.AI && !!env.QUOTA,
  async submit(env, r) {
    const model = env.CF_MODEL_T2I || "@cf/black-forest-labs/flux-1-schnell";
    let out;
    try {
      const input = { prompt: styledPrompt(r).slice(0, 2000), steps: 8 };
      if (Number.isInteger(r.seed)) input.seed = r.seed;
      out = await env.AI.run(model, input);
    } catch (e) {
      const msg = String(e && e.message || e);
      throw new HttpError(502, `Cloudflare AI: ${msg}`, /neuron|quota|limit|allocation/i.test(msg) ? "BALANCE" : "PROVIDER");
    }
    let bytes;
    if (out && typeof out.image === "string") bytes = Uint8Array.from(atob(out.image), (c) => c.charCodeAt(0));
    else if (out instanceof ReadableStream) bytes = await new Response(out).arrayBuffer();
    else throw new HttpError(502, "Cloudflare AI returned no image", "PROVIDER");
    const key = randomKey();
    await env.QUOTA.put(`img:${key}`, bytes, { expirationTtl: 7 * 86400, metadata: { type: "image/jpeg" } });
    return { url: `${r._origin}/v1/files/${key}` };
  },
  async poll(_env, s) {
    return { status: "DONE", progress: 1, resultUrl: s.url };
  },
};

// Add a new provider = write one object like the ones above and list it here.
const PROVIDERS = { replicate, fal, luma, cf };

// ---------------------------------------------------------------- smart prompts (Workers AI LLM)
const LLM_MODELS = (env) => [env.LLM_MODEL || "@cf/meta/llama-3.3-70b-instruct-fp8-fast", "@cf/meta/llama-3.1-8b-instruct"];

async function llm(env, system, user, { temperature = 0.6, maxTokens = 400 } = {}) {
  if (!env.AI) throw new HttpError(503, "AI assistant is not enabled on the server", "NO_PROVIDER");
  let last;
  for (const model of LLM_MODELS(env)) {
    try {
      const r = await env.AI.run(model, {
        messages: [{ role: "system", content: system }, { role: "user", content: user }],
        temperature,
        max_tokens: maxTokens,
      });
      const text = String((r && (r.response ?? r.result?.response)) || "").trim();
      if (text) return cleanLlm(text);
    } catch (e) {
      last = e;
    }
  }
  throw new HttpError(502, `AI assistant failed: ${(last && last.message) || "empty answer"}`, "PROVIDER");
}

// Models sometimes wrap the answer in quotes, labels or code fences.
export function cleanLlm(text) {
  let t = text.replace(/^```[a-z]*\s*|\s*```$/gi, "").trim();
  t = t.replace(/^(enhanced prompt|prompt|final prompt|here is[^:]*):\s*/i, "").trim();
  if (/^["“«].*["”»]$/s.test(t)) t = t.slice(1, -1).trim();
  return t.slice(0, 1500);
}

const KIND_WORDS = {
  TEXT_TO_VIDEO: "a short AI video clip (5-10 seconds)",
  IMAGE_TO_VIDEO: "animating a still photo into a short video (describe the motion and camera move)",
  TEXT_TO_IMAGE: "a single still image",
  IMAGE_EDIT: "an edit instruction applied to an existing photo (keep it a clear instruction)",
};

const PROMPT_ENGINEER = (r) =>
  `You are a world-class prompt engineer for generative ${VIDEO_KINDS.includes(r.kind) ? "video" : "image"} models.
Rewrite the user's idea into ONE production-ready English prompt for ${KIND_WORDS[r.kind] || "an image"}.
Rules:
- Always write in English, even if the user wrote in Arabic or another language. Translate faithfully, never change the subject.
- Keep every element the user asked for. Add concrete visual detail: subject, setting, lighting, color palette, lens/camera${VIDEO_KINDS.includes(r.kind) ? ", camera movement and the action over time" : ", composition"}.
- Visual style to apply: ${STYLE_HINT[r.style] || r.style || "best fitting style"}.
- 40 to 90 words, a single paragraph, no lists, no quotes, no preamble, no hashtags.
- Never add text, logos or watermarks to the scene. Keep it safe for work.
Answer with the prompt only.`;

const nonLatin = (s) => /[^\u0000-ɏ\s\d.,!?'"()\-:;]/.test(s);

/** Turns any prompt (Arabic too) into a rich English prompt. Falls back to the raw prompt on failure. */
async function smartPrompt(env, r) {
  if (!env.AI) return null;
  if (!(r.smart || nonLatin(r.prompt))) return null;
  try {
    return await llm(env, PROMPT_ENGINEER(r), r.prompt, { temperature: 0.5 });
  } catch {
    return null;
  }
}

/** Splits one idea into a short film: a title and N visually consistent scene prompts. */
async function story(env, body, lang) {
  const n = Math.min(6, Math.max(2, parseInt(body.scenes, 10) || 4));
  const text = await llm(
    env,
    `You are an award-winning film director and storyboard artist.
Turn the user's idea into a short film of exactly ${n} consecutive shots, each 5 seconds long, with a clear beginning, middle and end.
Write in ${lang}. Output format, nothing else:
TITLE: <a short catchy title>
SCENE: <shot 1>
SCENE: <shot 2>
...
Rules for every SCENE line: one vivid sentence of 20 to 45 words describing exactly what the camera sees and how it moves.
Repeat the main character's full look (age, clothes, colors) in every scene so the shots stay consistent. Same location style and lighting across shots.
Visual style: ${STYLE_HINT[body.style] || body.style || "cinematic"}. Safe for work, no text on screen.`,
    String(body.prompt),
    { temperature: 0.8, maxTokens: 900 },
  );
  return parseStory(text, n);
}

export function parseStory(text, n) {
  const lines = text.split(/\r?\n/).map((l) => l.trim()).filter(Boolean);
  let title = "";
  const scenes = [];
  for (const l of lines) {
    const t = l.match(/^\**\s*(TITLE|العنوان)\s*\**\s*[:：]\s*(.+)$/i);
    if (t && !title) { title = t[2].replace(/^["“«]|["”»]$/g, "").trim(); continue; }
    const m = l.match(/^\**\s*(SCENE|SHOT|المشهد|مشهد|لقطة)?\s*\d*\s*\**\s*[:：.)-]\s*(.+)$/i) || l.match(/^\d+[.)-]\s*(.+)$/);
    if (m) {
      const body = (m[2] ?? m[1]).trim();
      if (body.length > 12) scenes.push(body);
    }
  }
  if (!scenes.length) throw new HttpError(502, "AI assistant returned no scenes", "PROVIDER");
  return { title: title || scenes[0].split(/[,.،]/)[0].slice(0, 40), scenes: scenes.slice(0, n) };
}

async function assist(env, body) {
  const kind = KINDS.includes(body.kind) ? body.kind : "TEXT_TO_VIDEO";
  const lang = body.lang === "ar" ? "Arabic" : "English";
  if (body.mode === "surprise") {
    const seed = Math.floor(Math.random() * 1e6);
    const text = await llm(
      env,
      `You invent fresh, surprising and visually stunning ideas for ${KIND_WORDS[kind]}.
Answer with ONE idea in ${lang}, 12 to 25 words, vivid and specific, no quotes, no preamble, no numbering. Safe for work.`,
      `Give me a new idea (variation #${seed}).${body.style ? ` Preferred style: ${body.style}.` : ""}`,
      { temperature: 1.0, maxTokens: 120 },
    );
    return { prompt: text };
  }
  if (!body.prompt || !String(body.prompt).trim()) throw new HttpError(400, "prompt is required");
  if (String(body.prompt).length > 2000) throw new HttpError(400, "prompt too long");
  if (body.mode === "story") return story(env, body, lang);
  const text = await llm(env, PROMPT_ENGINEER({ kind, style: body.style }), String(body.prompt));
  return { prompt: text };
}

// ---------------------------------------------------------------- http
const CORS = {
  "Access-Control-Allow-Origin": "*",
  "Access-Control-Allow-Headers": "content-type,x-app-token,x-owner-key,x-device-id",
  "Access-Control-Allow-Methods": "GET,POST,OPTIONS",
};
const json = (obj, status = 200) =>
  new Response(JSON.stringify(obj), { status, headers: { "content-type": "application/json", ...CORS } });

function candidatesFor(env, req) {
  const order = (env.PROVIDER_ORDER || "fal,replicate,luma,cf").split(",").map((s) => s.trim());
  // cf is always a last-resort fallback, even if PROVIDER_ORDER forgets it.
  if (!order.includes("cf")) order.push("cf");
  const names = req.provider ? [req.provider] : order;
  const proVideo = req.quality === "pro" && VIDEO_KINDS.includes(req.kind);
  const list = names
    .map((n) => PROVIDERS[n])
    .filter((p) => p && p.configured(env) && p.kinds.includes(req.kind))
    .filter((p) => !proVideo || p.name === "fal" || p.name === "luma");
  if (!list.length) {
    throw new HttpError(503, "No configured provider supports this request. Set a provider key (see backend/README.md).", "NO_PROVIDER");
  }
  return list;
}

// Tries each configured provider in order; if one fails (empty balance, outage), the next one is used.
async function submitWithFallback(env, req) {
  const errors = [];
  let allBalance = true;
  for (const p of candidatesFor(env, req)) {
    try {
      return { provider: p, state: await p.submit(env, req) };
    } catch (e) {
      errors.push(e.message || String(e));
      if (e.code !== "BALANCE") allBalance = false;
    }
  }
  throw new HttpError(502, errors.join(" | "), allBalance ? "BALANCE" : "PROVIDER");
}

// ---------------------------------------------------------------- access control
// Owner (x-owner-key == OWNER_KEY): no limits from this server and may pick any provider.
// Everyone else: daily quotas per device, set by you with FREE_DAILY_VIDEOS / FREE_DAILY_IMAGES / FREE_DAILY_ASSISTS.
// Quotas need a KV namespace bound as QUOTA; without it they are not enforced.
const isOwner = (request, env) => !!env.OWNER_KEY && request.headers.get("x-owner-key") === env.OWNER_KEY;

const LIMITS = {
  v: (env) => env.FREE_DAILY_VIDEOS ?? "3",
  i: (env) => env.FREE_DAILY_IMAGES ?? "10",
  a: (env) => env.FREE_DAILY_ASSISTS ?? "40",
};
const WHAT = { v: "videos", i: "images", a: "AI assists" };

/** Checks the daily quota and returns a function that records one use (call it only after success). */
async function quota(request, env, type) {
  if (!env.QUOTA || isOwner(request, env)) return async () => {};
  const device = (request.headers.get("x-device-id") || "").slice(0, 64);
  if (!device) throw new HttpError(400, "x-device-id header is required");
  const limit = parseInt(LIMITS[type](env), 10);
  const key = `q:${device}:${type}:${new Date().toISOString().slice(0, 10)}`;
  const used = parseInt((await env.QUOTA.get(key)) || "0", 10);
  if (used >= limit) throw new HttpError(429, `Daily limit reached (${limit} ${WHAT[type]}). Try again tomorrow.`, "QUOTA");
  return async () => env.QUOTA.put(key, String(used + 1), { expirationTtl: 172800 });
}

async function readBody(request) {
  try {
    return await request.json();
  } catch {
    throw new HttpError(400, "invalid JSON body");
  }
}

export default {
  async fetch(request, env) {
    if (request.method === "OPTIONS") return new Response(null, { headers: CORS });
    try {
      const url = new URL(request.url);
      const path = url.pathname;

      if (path === "/" || path === "/health") return json({ ok: true, service: "neovids-backend" });

      // Generated files are public (random 128-bit keys) so the image viewer and players can load them directly.
      const file = path.match(/^\/v1\/files\/([0-9a-f]{32})$/);
      if (file && request.method === "GET") {
        if (!env.QUOTA) throw new HttpError(404, "not found");
        const { value, metadata } = await env.QUOTA.getWithMetadata(`img:${file[1]}`, { type: "arrayBuffer" });
        if (!value) throw new HttpError(404, "not found");
        return new Response(value, {
          headers: { "content-type": (metadata && metadata.type) || "image/jpeg", "cache-control": "public, max-age=604800", ...CORS },
        });
      }

      if (env.APP_TOKEN && request.headers.get("x-app-token") !== env.APP_TOKEN) throw new HttpError(401, "Unauthorized");

      if (path === "/v1/status" && request.method === "GET") {
        return json({
          ok: true,
          owner: isOwner(request, env),
          quota: !!env.QUOTA,
          ai: !!env.AI,
          providers: Object.values(PROVIDERS).filter((p) => p.configured(env)).map((p) => p.name),
        });
      }

      if (path === "/v1/providers" && request.method === "GET") {
        return json(Object.values(PROVIDERS).filter((p) => p.configured(env)).map((p) => ({ name: p.name, kinds: p.kinds })));
      }

      if (path === "/v1/assist" && request.method === "POST") {
        const body = await readBody(request);
        const record = await quota(request, env, "a");
        const out = await assist(env, body);
        await record();
        return json(out);
      }

      if (path === "/v1/jobs" && request.method === "POST") {
        const body = await readBody(request);
        if (!body.prompt || !String(body.prompt).trim()) throw new HttpError(400, "prompt is required");
        if (String(body.prompt).length > 2000) throw new HttpError(400, "prompt too long");
        if (!KINDS.includes(body.kind)) throw new HttpError(400, "invalid kind");
        if ((body.kind === "IMAGE_TO_VIDEO" || body.kind === "IMAGE_EDIT") && !body.sourceImage) throw new HttpError(400, "sourceImage is required");
        if (!isOwner(request, env)) delete body.provider; // only the owner can choose a provider
        if (body.quality === "pro" && !isOwner(request, env) && env.PRO_PUBLIC !== "1") {
          throw new HttpError(403, "Pro quality is reserved for the app owner", "PRO_LOCKED");
        }
        const record = await quota(request, env, VIDEO_KINDS.includes(body.kind) ? "v" : "i");
        body._origin = url.origin;
        body.finalPrompt = await smartPrompt(env, body);
        const { provider, state } = await submitWithFallback(env, body);
        await record(); // failed submissions do not use up the daily quota
        return json({
          id: `${provider.name}.${b64.enc(state)}`,
          status: "QUEUED",
          progress: 0,
          finalPrompt: body.finalPrompt || null,
          provider: provider.name,
        });
      }

      const m = path.match(/^\/v1\/jobs\/(.+)$/);
      if (m && request.method === "GET") {
        let id;
        try { id = decodeURIComponent(m[1]); } catch { throw new HttpError(404, "unknown job"); }
        const dot = id.indexOf(".");
        const provider = dot > 0 ? PROVIDERS[id.slice(0, dot)] : null;
        if (!provider) throw new HttpError(404, "unknown job");
        let state;
        try { state = b64.dec(id.slice(dot + 1)); } catch { throw new HttpError(404, "unknown job"); }
        const r = await provider.poll(env, state);
        if (r.status === "DONE" && !r.resultUrl) return json({ id, status: "FAILED", progress: 0, error: "provider returned no result", code: "PROVIDER" });
        return json({ id, progress: 0, ...r });
      }

      throw new HttpError(404, "not found");
    } catch (e) {
      const status = e instanceof HttpError ? e.status : 500;
      return json({ error: e.message || "error", code: e instanceof HttpError ? e.code : "INTERNAL" }, status);
    }
  },
};

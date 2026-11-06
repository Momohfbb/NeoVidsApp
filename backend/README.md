# NeoVids backend

خادم وسيط صغير (Cloudflare Worker) بين التطبيق ومزوّدي التوليد. مفاتيح الخدمات تبقى هنا فقط ولا توضع داخل التطبيق.

المزوّدون المدعومون حاليًا: **fal.ai** و**Replicate** (فيديو، صورة إلى فيديو، صورة، تعديل صور) و**Luma** (نص إلى فيديو).
لإضافة مزوّد جديد اكتب كائنًا واحدًا في `src/index.js` يشبه الموجودين ثم سجّله في `PROVIDERS`.

> أسماء النماذج الافتراضية قد تتغير عند المزوّدين. يمكن تغيير أي منها بمتغيرات مثل `FAL_MODEL_T2V` و`REPLICATE_MODEL_T2I`.

## النشر التلقائي من GitHub (الأسهل)
في GitHub ← Settings ← Secrets and variables ← Actions أضف:
`CLOUDFLARE_API_TOKEN` (قالب «Edit Cloudflare Workers») و`CLOUDFLARE_ACCOUNT_ID`، ومفتاح مزوّد واحد على الأقل (`FAL_KEY` أو `REPLICATE_TOKEN` أو `LUMA_KEY`)، و`APP_TOKEN` اختياريًا.
ثم شغّل Actions ← **Deploy backend** ← Run workflow. عنوان الخادم يظهر في سجل التشغيل.

## النشر اليدوي (مرة واحدة)
```bash
cd backend
npm install
npx wrangler login
npx wrangler secret put FAL_KEY          # أو REPLICATE_TOKEN أو LUMA_KEY (واحد على الأقل)
npx wrangler secret put APP_TOKEN        # اختياري لكن موصى به: كلمة سر يرسلها التطبيق
npx wrangler deploy
```
سيظهر عنوان مثل `https://neovids-backend.<حسابك>.workers.dev`.

## ربط التطبيق
- من داخل التطبيق: الإعدادات ← عنوان الخادم.
- أو عند البناء: في GitHub ← Settings ← Secrets and variables ← Actions: أضف متغيرًا `BACKEND_URL` وسرًّا `APP_TOKEN`، ثم أعد تشغيل Build APK.

## الواجهة
- `POST /v1/jobs` ← `{kind, prompt, style, aspect, durationSec, sourceImage?, provider?}`
- `GET /v1/jobs/:id` ← الحالة والنتيجة
- `GET /v1/providers` ← المزوّدون المفعّلون

## تنبيهات
- بدون `APP_TOKEN` يستطيع أي شخص يعرف العنوان استهلاك رصيدك. الرمز داخل الـ APK يمكن استخراجه، فهو يمنع الإساءة العابرة فقط؛ للحماية الجدية أضف تسجيل دخول وحدودًا لكل مستخدم.
- حدود الاستخدام وفلاتر المحتوى تحددها خدمات المزوّدين.

## وضع المالك والحدود (أنت تتحكم)
- **أنت (المالك):** عيّن سرّ `OWNER_KEY` واكتب القيمة نفسها في التطبيق (الإعدادات ← مفتاح المالك). عندها لا يطبّق الخادم عليك أي حدود، وتستطيع اختيار المزوّد.
- **الجمهور:** حدود يومية لكل جهاز تحددها في `wrangler.toml`: `FREE_DAILY_VIDEOS` و`FREE_DAILY_IMAGES`.
- تحتاج الحدود مساحة KV باسم `QUOTA` (النشر التلقائي ينشئها). بدونها لا تُطبَّق الحدود.
- «بلا قيود» تعني بلا قيود من تطبيقك وخادمك. رصيد المزوّدين وسياسات المحتوى لديهم تبقى سارية، ولا يتجاوزها الخادم.

## الذكاء المدمج (Cloudflare Workers AI)
الخادم مربوط بـ Workers AI (`[ai]` في `wrangler.toml`)، ويوفّر بلا أي مفتاح إضافي:
- **الوصف الذكي:** أي وصف غير إنجليزي (أو عند `smart: true`) يُعاد كتابته بالإنجليزية بتفاصيل سينمائية قبل إرساله للمزوّد. يُعاد للتطبيق في `finalPrompt`.
- **`POST /v1/assist`**: `{mode: "enhance"|"surprise", prompt, kind, style, lang}` ← `{prompt}`.
- **المزوّد `cf`**: توليد صور مجاني (FLUX.1 schnell) كخيار أخير بعد المزوّدين المدفوعين. الصور تُحفظ أسبوعًا في KV وتُقدَّم من `/v1/files/<key>`.
- الحصة المجانية لـ Workers AI: نحو 10,000 «neuron» يوميًا على حساب Cloudflare.

## رموز الأخطاء
كل خطأ يعود بالشكل `{error, code}`، و`code` واحد من: `BALANCE` (نفد رصيد المزوّد)، `QUOTA`، `NO_PROVIDER`، `UNAUTHORIZED`، `BAD_REQUEST`، `PROVIDER`، `NOT_FOUND`. الطلبات الفاشلة لا تُحتسب من الحصة اليومية.

# NeoVids backend

خادم وسيط صغير (Cloudflare Worker) بين التطبيق ومزوّدي التوليد. مفاتيح الخدمات تبقى هنا فقط ولا توضع داخل التطبيق.

المزوّدون المدعومون حاليًا: **fal.ai** و**Replicate** (فيديو، صورة إلى فيديو، صورة، تعديل صور) و**Luma** (نص إلى فيديو).
لإضافة مزوّد جديد اكتب كائنًا واحدًا في `src/index.js` يشبه الموجودين ثم سجّله في `PROVIDERS`.

> أسماء النماذج الافتراضية قد تتغير عند المزوّدين. يمكن تغيير أي منها بمتغيرات مثل `FAL_MODEL_T2V` و`REPLICATE_MODEL_T2I`.

## النشر (مرة واحدة)
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

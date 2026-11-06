# نشر NeoVids على Google Play

كل شيء جاهز في الكود. ما يلي خطوات لا يستطيع غيرك القيام بها لأنها تحتاج حساباتك.

## 1) مفتاح التوقيع (مرة واحدة، 5 دقائق)
مفتاح التوقيع هو «هوية» تطبيقك على Play. احفظه في مكان آمن: إن ضاع لا تستطيع تحديث التطبيق.

على أي حاسوب فيه Java (أو في Google Colab بخلية `!`):
```
keytool -genkeypair -v -keystore release.keystore -alias neovids -keyalg RSA -keysize 2048 -validity 10000
base64 -w0 release.keystore > keystore.txt
```
ثم في GitHub ← المستودع ← Settings ← Secrets and variables ← Actions ← **New repository secret** أضف:
- `KEYSTORE_BASE64` = محتوى `keystore.txt`
- `KEYSTORE_PASSWORD` = كلمة سر المخزن
- `KEY_ALIAS` = `neovids`
- `KEY_PASSWORD` = كلمة سر المفتاح

بعدها كل دفع إلى `main` ينشر بجانب النسخة التجريبية:
- `NeoVids-release.apk` (نسخة موقّعة للتوزيع المباشر)
- `NeoVids-release.aab` (الملف الذي يُرفع إلى Google Play)

## 2) حساب Google Play Console
- سجّل في https://play.google.com/console (رسوم لمرة واحدة 25$).
- أنشئ تطبيقًا باسم **NeoVids AI**، وارفع `NeoVids-release.aab` في «الاختبار الداخلي» أولًا.
- سياسة الخصوصية: استخدم رابط `docs/privacy.md` على GitHub.
- الأيقونة: `docs/logo.png` (1024×1024، صغّرها إلى 512×512)، وصورة الواجهة: `docs/brand.png`.

## 3) قبل النشر العام
- انقل الفيديو إلى الجودة الاحترافية بترخيص تجاري (Kling)، لأن ترخيص نموذج LTX الافتراضي للأبحاث فقط: أضف متغير `FAL_MODEL_T2V` بقيمة `fal-ai/kling-video/v2.1/standard/text-to-video` في `backend/wrangler.toml` إن أردت Kling للجميع.
- راجع الحدود اليومية في `backend/wrangler.toml` حتى لا يُستنزف رصيدك.

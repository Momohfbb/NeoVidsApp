# NeoVids AI

تطبيق Android (Jetpack Compose) يدعم العربية والإنجليزية.

## الحصول على ملف APK
أسهل طريقة من الهاتف: https://github.com/Momohfbb/NeoVidsApp/releases/latest/download/app-debug.apk

كل دفع (push) إلى الفرع `main` يشغّل GitHub Actions الذي يبني `app-debug.apk`.
افتح تبويب **Actions** ← آخر تشغيل لـ **Build APK** ← قسم **Artifacts** ← `NeoVids-AI-debug-apk`.

## البناء محليًا
```
./gradlew assembleDebug
```
يتطلب Java 17 و Android SDK 35.

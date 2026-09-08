# Build Diagnostic

این نسخه فقط Workflow ساخت APK را اصلاح می‌کند.

- پروژه Gradle Wrapper ندارد.
- Gradle 8.6 مستقیماً از توزیع رسمی Gradle دریافت می‌شود.
- `setup-gradle` عمداً حذف شده تا خطای مبهم Action حذف شود.
- خروجی کامل `gradle assembleDebug` با `--stacktrace --console=plain` در Artifact با نام `build-log` ذخیره می‌شود.
- هیچ فایل Kotlin یا منطق برنامه در این اصلاح تغییر نکرده است.

# TenderCheck для Android

Нативная Android-оболочка WebView для существующего Kotlin/JS-приложения. Сам тренажёр и его данные собираются из корневого проекта и включаются в APK как локальные assets, поэтому приложение работает офлайн.

## Сборка

Из корня репозитория:

```bash
./gradlew build jsBrowserDistribution
mkdir -p android/app/src/main/assets/www
cp -R build/dist/js/productionExecutable/. android/app/src/main/assets/www/
./gradlew -p android :app:assembleDebug
```

APK: `android/app/build/outputs/apk/debug/app-debug.apk`.

В GitHub Actions сборка запускается автоматически при изменениях в ветке `android-apk`. Готовый APK публикуется как артефакт workflow.

# Release signing

1. Создайте keystore (один раз, сохраните в надёжном месте):

```bash
keytool -genkeypair -v \
  -keystore querydoc-release.jks \
  -alias querydoc \
  -keyalg RSA -keysize 4096 -validity 10000
```

2. Создайте `keystore.properties` в корне проекта:

```properties
storeFile=/absolute/path/querydoc-release.jks
storePassword=...
keyAlias=querydoc
keyPassword=...
```

3. Соберите:

```bash
./gradlew :app:assembleRelease
```

Файл `keystore.properties` и keystore не коммитятся. Без них release APK
собирается unsigned; debug-ключ для release не используется.

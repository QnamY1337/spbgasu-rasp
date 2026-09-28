# Правила работы с проектом

Android-приложение «Расписание СПбГАСУ» (Kotlin, Compose, Room, Hilt, WorkManager). Описание — в README.md.

## Git
- **Коммит в локальный репозиторий после каждого изменения** — небольшие осмысленные коммиты, а не один
  большой в конце фазы (требование пользователя от 28.09.2026).
- Пуш в https://github.com/QnamY1337/spbgasu-rasp (приватный, ветка `main`) — по завершении шага/фазы.
- Сообщения коммитов — на русском, первая строка кратко о сути изменения.

## Проверка
- Изменения проверяются на эмуляторе `Pixel_8_API_37` (часовой пояс эмулятора — Europe/Moscow).
- **На телефон пользователя (vivo, adb `10AF3Y0NH300538`) сборку ставить только по его запросу.**
- Перед коммитом: `./gradlew :app:testDebugUnitTest` и `:app:assembleDebug`.

## Сборка на этой машине
- `java` нет в PATH: `JAVA_HOME="Z:\android studio\jbr"`.
- Android SDK: `%LOCALAPPDATA%\Android\Sdk` (compileSdk 37); `local.properties` не в git.
- Путь проекта содержит пробел — Robolectric берёт образ Android из `~/.gradle/robolectric-jars`.
- Ключ релиза: `%USERPROFILE%.androidspbgasu-release.jks`, пароли — в `keystore.properties` (не в git).

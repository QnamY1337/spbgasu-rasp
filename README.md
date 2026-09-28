# Расписание СПбГАСУ

Android-приложение (Kotlin, Jetpack Compose) с расписанием пар СПбГАСУ, напоминаниями и отслеживанием замен.
Разработка идёт по фазам. Готово:

- **Фаза 1** — разведка источника и парсер расписания.
- **Фаза 2** — Room (пары, недели, замены, задания, экзамены), репозитории, DataStore, Hilt, use case'ы
  и поиск замен сравнением снепшотов.

## Сборка и тесты

Нужен JDK 17+ (подойдёт JBR из Android Studio) и Android SDK (compileSdk 37).

```bash
./gradlew :app:testDebugUnitTest            # тесты на сохранённых ответах сайта
./gradlew :app:testDebugUnitTest -Plive     # + живой запрос к rasp.spbgasu.ru
```

## Как устроен источник данных (разведка, 28.09.2026)

Эндпоинт `POST https://rasp.spbgasu.ru/bitrix/services/main/ajax.php?mode=class&c=gasu:raspisanie.csv&action=getRasp`.

Отличия от первоначальной спецификации, найденные на практике:

- **Нужен CSRF.** Без заголовка `X-Bitrix-Csrf-Token` и сессионной cookie `PHPSESSID` приходит `invalid_csrf`.
  Токен берётся с главной страницы (`'bitrix_sessid':'…'`). Если он протух, Bitrix присылает свежий в
  `errors[0].customData.csrf` — клиент повторяет запрос один раз.
- **Тело только form-urlencoded**: `search_params[SEARCH]=3-ТТП-26&search_params[FILTER]=GROUPS&…`.
  JSON-тело Bitrix не разбирает («Could not find value for parameter {search_params}»).
- **Частичный поиск не работает**: `SEARCH` должен точно совпадать с названием группы.
  Для автокомплита на главной странице есть полный список групп — `window.GROUPS = [...]` (614 групп).
- **Несуществующая группа** — это `status: success` с 8 пустыми неделями, а не ошибка.
- В одной паре может быть несколько аудиторий/преподавателей (подгруппы) через `<br>` или запятую:
  `421(1)/Г<br>421(2)/Г`. Аудитории бывают вида `406*/К`, `Актовый зал/Г`.
- Резервный `GET /getExcel.php?TYPE=GROUPS&FIND=…` отвечает `.xlsx` (application/vnd.ms-excel).

## Как ищутся замены

Сайт не отдаёт список замен, поэтому `SyncScheduleUseCase` при каждой синхронизации сравнивает свежее
расписание с сохранённым (`ScheduleDiffer`) по слотам «дата + номер пары»:

- первая синхронизация замен не даёт, прошедшие дни не сравниваются;
- сравниваются только недели, которые есть в обоих снепшотах: публикация новой недели — не «добавленные пары»;
- подгруппы в одном слоте сопоставляются сначала целиком, потом по предмету, потом по порядку —
  перестановка подгрупп на сайте замен не даёт;
- если сайт вдруг вернул пустое расписание, а раньше пары были, снепшот не перезаписывается.

Типы замен: `SUBJECT` (включая смену лекции на практику), `ROOM`, `TEACHER`, `CANCELLED`, `ADDED`.

## Структура

```
app/src/main/java/com/example/gasuschedule/
├── data/
│   ├── remote/      — BitrixScheduleSource (сессия, CSRF), ScheduleHtmlParser, MainPageParser
│   ├── local/       — Room: AppDatabase, сущности, dao/, prefs/ (DataStore)
│   └── repository/  — реализации репозиториев
├── domain/
│   ├── model/       — Lesson, ScheduleWeek, ScheduleChange, HomeworkItem, ExamEntry, ошибки
│   ├── repository/  — интерфейсы
│   └── usecase/     — SyncSchedule, ScheduleDiffer, GetTodaySchedule, GetWeekSchedule
└── di/              — Hilt: NetworkModule, DatabaseModule, RepositoryModule
```

Схема БД экспортируется в `app/schemas/` — при изменении сущностей нужна миграция и новая версия БД.

Тесты Room идут на Robolectric: образ Android скачивает Gradle в `~/.gradle/robolectric-jars`
(встроенный загрузчик Robolectric здесь падал на SSL, а из пути с пробелами не грузится нативная библиотека).

# Расписание СПбГАСУ

Android-приложение (Kotlin, Jetpack Compose) с расписанием пар СПбГАСУ, напоминаниями и отслеживанием замен.
Разработка идёт по фазам; сейчас готова **фаза 1 — разведка источника и прототип парсера**.

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

## Структура

```
app/src/main/java/com/example/gasuschedule/
├── data/remote/
│   ├── ScheduleRemoteSource.kt   — интерфейс источника (можно подменить на Excel)
│   ├── BitrixScheduleSource.kt   — OkHttp-клиент: сессия, CSRF, повтор
│   ├── ScheduleHtmlParser.kt     — Jsoup: HTML -> SemesterSchedule(weeks, lessons)
│   ├── MainPageParser.kt         — токен и список групп с главной
│   └── dto/BitrixAjaxResponse.kt — success / invalid_csrf / error
└── domain/model/Lesson.kt
```

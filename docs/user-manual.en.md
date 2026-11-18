# Nutrition Assistant user manual

## Installation and launch

The app icon is the green “Food calendar” with a plate, fork, and spoon. It adapts to circular and other Android icon shapes. On Android 13+, a monochrome version is available for launchers with themed icons enabled.

The app supports Android 8.0 and later. Open the project in Android Studio with JDK 17 or 21, Android SDK Platform 37, and SDK Build Tools 36.0.0, or build the APK:

```powershell
./gradlew.bat :app:assembleDebug
```

Gradle Wrapper 9.6.0 is included. The build uses Android Gradle Plugin 9.5.1, Kotlin 2.4.21 through AGP built-in Kotlin support, KSP 2.3.14, and Room 2.8.5 with KSP2 support. Configure the SDK using ANDROID_HOME or local.properties; the first build needs internet to download dependencies. The APK is app/build/outputs/apk/debug/app-debug.apk. Open it on a test device and allow installation from the selected source in Android settings. On macOS/Linux use ./gradlew :app:assembleDebug.

### Selecting Java for Gradle

In Android Studio, open **Settings → Build, Execution, Deployment → Build Tools → Gradle → Gradle JDK** and select an installed JDK 21 (JDK 17 also works with this project). When `GRADLE_LOCAL_JAVA_HOME` is selected, the `java.home` property in the local `.gradle/config.properties` file supplies the path. This machine-specific file is excluded from Git. After changing it, run **Sync Project with Gradle Files**.

Build verification uses JDK 21. The Java runtime used to launch Android Studio itself is configured separately. For terminal builds, set `JAVA_HOME`; the wrapper does not automatically read `.gradle/config.properties`. `JAVA_HOME is not set and no 'java' command could be found in your PATH` means Java has not been configured for the terminal. Example for the JDK installed on this computer:

```powershell
$env:JAVA_HOME = 'C:\Program Files\Android\openjdk\jdk-21.0.8'
./gradlew.bat --version
./gradlew.bat :app:assembleDebug
```

Use your own installation path on another computer. `--version` should report a compatible JVM. [Configuring the Gradle JDK in Android Studio](https://developer.android.com/build/jdks).

The app opens «Календарь» (Calendar) with today's diary. The bottom tabs are «Расписание» (Schedule) for reminder times, «Календарь» (Calendar) for daily records and statistics, «Меню» (Menu) for dish, product, and medication catalogs, and «Данные» (Data) for JSON backups. Light and dark themes follow Android settings; the palette, cards, and calendar are based on sports_activity_assistant. The interface is in Russian. No internet, registration, or server is required. One local user's data is stored in Room. Automatic cloud backup and synchronization are unavailable. Save a JSON copy in Data before uninstalling: uninstalling deletes the local database.

## Consistent interface and forms

In version 0.10.0, screens, editors, date/time input, confirmations, errors, and import preview share fonts, fields, spacing, and buttons. Light and dark themes follow Android settings. Text follows the system font size; long forms and lists scroll, and actions wrap when space is limited. In a short window with the keyboard open, scroll the entire form if needed to reach its actions.

The open editor, entered text, selected food items, and doses survive screen recreation, such as device rotation. They become diary records only after saving. When record values have changed, Cancel, Back, or tapping outside the window offers «Отбросить» (Discard); canceling that confirmation returns to the draft. Products and other catalog entries already saved through a nested editor remain in the catalog when the parent form is canceled.

Deleting a diary record or removing a catalog entry first asks for confirmation. Diary or catalog action failures appear in «Не удалось выполнить действие» (Action failed); «Понятно» (Understood) returns to the entered values. Invalid fields are highlighted and saving is disabled until corrected. While saving, repeated actions and closing the editor are blocked.

Enter eight digits for a date, for example `17112026` → `17.11.2026`, or four digits including a leading zero for a time, for example `0935` → `09:35`. Separators are inserted automatically. «Выбрать» (Select) applies the value; invalid dates and times outside 00:00–23:59 are rejected. Android's file picker and permission dialogs retain the system's own appearance.

## Calendar and diary

The calendar displays six Monday-first weeks, including adjacent months. Arrows and horizontal swipes browse months; switching selects the first day of the new month, as in sports_activity_assistant. Tap a day to open its records; tapping an adjacent month's day also changes the visible month. «Сегодня» (Today) returns to the current date. «Выбрать дату» (Select date) below the calendar opens a date-input form using the app theme. Today has an outline and the selected day is highlighted. The selected date and visible month survive screen recreation and tab changes.

Food, medication, and toilet icons mark days with saved events. The diary heading shows counts of meals, medication intakes, and toilet visits for the selected day. Multiple dishes in one meal or medicines in one intake count as one event. Edits and deletions update the calendar automatically; an empty day is never assumed symptom-free.

Scrolling the diary up collapses the calendar; returning to the top expands it. Vertical gestures over the calendar also scroll the diary. On short screens or with large system fonts, the calendar scrolls with the diary. «+» opens a choice of breakfast, lunch, dinner, snack, medication intake, or toilet entry. Choosing a type always creates a new event; use «Изменить» (Edit) on the required record to change it. Other sections remain available further down the diary. Each section supports adding, editing, and deleting records. Merely viewing a date does not create records.

Multiple breakfasts, lunches, dinners, snacks, and medication intakes can be recorded per day. A meal can contain one dish, one product, or a mixture. Multiple stool and wellbeing entries are also supported. Sleep and water count each have one record per day.

### Symptom marks and monthly overview

Since version 0.10.0, a red “!” means “Loose stool, type 6–7”: one recorded episode is sufficient. This is a diary mark, not a diagnosis. A mixed day with types 4 and 7 stays marked; type 5 alone never turns the mark on. The selected day's heading separately counts type-6–7 episodes. Cell accessibility descriptions include this count, type 5, and food/symptom completeness.

“✓” means an explicitly confirmed outcome without loose stool: No stool and No loose stool have different cell descriptions and day-list labels. “?” means an unconfirmed symptom outcome; empty and future days retain it. Food confirmation is independent. Today has a thicker outline and the selected day has a background; the red mark remains visible with both. Editing or deleting the last type-6–7 record immediately removes “!” without automatically confirming absence of symptoms.

Below the selected-day heading, the monthly overview counts days with types 6–7, their episodes, type-5 episodes, confirmed days without loose stool, and separate food/symptom confirmations. It covers the entire displayed month, including future dates that are still unconfirmed; adjacent grid dates are excluded. A loading label appears until data arrives.

«Дни месяца и фильтр» (Month days and filter) opens all dates in that month. Enable «Только дни с типом 6–7» (Only days with types 6–7) to keep dates with recorded episodes. Tapping a row selects the day and opens its diary and timeline. Filtering applies to this list; the grid retains the month's context. An empty result means no recorded episodes, not established absence of symptoms. The filter survives screen recreation.

### Selected-day timeline

The timeline shows food, medication, stool, health observations, and sleep with recorded names, quantities, doses, and comments. Precise events sort by actual time including UTC offset while retaining the original local diary date. Medication at 13:00 therefore appears after a stool event at 12:30. Equal precise times establish no relative order; the app infers no cause.

Entries without precise times appear under Time unspecified — order unknown. Daily ratings and sleep have a separate Whole day or night — no order relative to events group. Creation time never supplies an event time; sleep is not converted into a wake-up event. Water remains in its diary card. Each group initially shows up to six entries; larger groups offer a full timeline. Use the record cards below to edit or delete. With enlarged text, the calendar becomes taller and scrolls with the diary.

### Food

1. Tap «Добавить» (Add) for a meal or «Изменить» (Edit) for an existing record.
2. Choose «Блюда» (Dishes) and homemade («Домашняя») or restaurant («Ресторанная») food, or «Продукты» (Products) for an individual product.
3. Type part of a name. Matches update as you type; case, extra whitespace, and Russian е/ё differences are normalized.
4. Tap dishes or products to include them. Select multiple items across sections and food types. «Убрать из события» removes a selection.
5. To create a new entry or variant, choose «Добавить … в справочник и выбрать» (Add to catalog and select). For a dish, enter its name and type and check its products; at least one product is required. Choose a product category (Other by default); optionally specify a concrete product, brand, and composition. The new entry is immediately selected in the event.
6. Tap «Сохранить событие» (Save event).

Event search shows up to 40 matches; refine the search as needed. Search text alone is never saved as a dish or product. Empty events cannot be saved. A created catalog entry remains even if the event is subsequently canceled.

### Medication

Tap «Добавить прием» (Add intake) and search by part of a medicine name. Create a missing entry from the same dialog to select it immediately. An intake can contain multiple medicines, each with an optional dose or quantity entered manually. The medication catalog starts empty; the app does not recommend medicines or doses.

### Stool

In «Туалет» (Toilet), select Bristol stool type 1–7. Types 1–2 are labeled constipation signs, 6–7 loose stool/diarrhea, 3–4 formed stool, and 5 soft stool. These are diary categories rather than diagnoses. Urgency 0–2, pain 0–3, actual time, and a comment are optional; auxiliary scales have a Missing choice. Existing type-0 entries remain unassessed; new type-0 entries are not allowed.

After saving type 1–2 or 6–7, «Аналитика стула» (Stool analysis) updates automatically. It shows food from the previous calendar day relative to the event date, including snacks. For an October 18 record, this means October 17, rather than a rolling 24-hour window.

The algorithm compares dishes, products, food types, and known ingredients. An individually logged product also counts as an ingredient when compared with dish compositions. Each factor shows days with the selected condition / all observed days following exposure, and the corresponding counts without exposure. Multiple stools or repeated ingredients on one day do not multiply day counts. Constipation and diarrhea are analyzed separately. Only history through the selected date is used.

Comparisons include only days with assessed stool records and food recorded on the preceding day. Missing records are never assumed healthy or symptom-free. A “repeated association” requires at least two affected days after exposure, at least two observed days without exposure, and a higher condition rate with exposure. Up to 12 factors are displayed, prioritizing repeated associations and then rate differences. Insufficient data is stated explicitly.

An association does not establish a cause. “Without exposure” means the factor was not recorded on the preceding day; results depend on diary completeness. Medication, infections, water, sleep, and wellbeing effects are not modeled. The algorithm gives no diagnoses, treatment advice, or recommendations to eliminate foods.

### Daily state and separate observations

Tap «+» → «Состояние за день» (Daily state) or «Заполнить день» (Fill day). Choose up to five measures and tap «Сохранить день» (Save day). No text is required; optional comments expand separately. Every measure initially says Not filled; saving an empty form does not create healthy scores.

| Measure | Choices |
| --- | --- |
| Sleep quality | 1 — very poor … 5 — very good |
| Stress | 0 — absent … 4 — very high |
| Activity | None / light / moderate / vigorous |
| Wellbeing | 1 — very poor … 5 — very good |
| Illness signs | None / present / unknown |

These are the diary's own version-1 self-report scales with no claimed medical validation. Missing differs from zero, no activity/signs, and Unknown. Clearing a daily stress, activity, wellbeing, or illness rating removes its comment. The sleep comment is cleared separately. Activity can include kind and minutes (1–1440); present illness can include impact on usual activities and symptom categories. No diagnosis is required. Choosing no activity or absent/unknown illness clears hidden dependent details.

«+» → «Отдельное наблюдение» (Separate observation), or Add observation, lets you choose a measure and Whole day or Separate moment. There is one daily rating per measure and any number of moments. Open an existing entry to edit or delete it. Activity events show maximum reported intensity, sum of known minutes, and count of events with unknown duration; these never replace the daily rating. Different scales are not combined into an index.

### Actual time and day completeness

Food, medication, stool, and moment observations offer «Указать фактическое время» (Specify actual time). It remains unknown until explicitly selected and is never inferred from creation time. Check the UTC offset and optional IANA timezone, such as Europe/Moscow. Repeated daylight-saving times need an explicit offset choice; nonexistent local times cannot be saved. Time unknown clears the time and zone. Whole-day ratings have no precise clock time.

Check day completeness independently confirms all food and all observations/symptoms. Symptoms require an explicit outcome: no loose stool; type 6–7 occurred (record the episode first); or no stool. It is checked against recorded stools. No loose stool is the user's own report, not a diagnosis. An empty day is never automatically confirmed. Food changes clear food confirmation; stool, health, sleep, or medication changes clear symptom confirmation. Confirm again after editing. Current analysis does not yet use these new scales, times, or confirmations; that extension is scheduled separately.

### Sleep and water

In Sleep, tap Edit. Quality 1–5 can be saved without times; awakenings (0–1000) and a comment are optional. Zero awakenings differs from an unknown count. Record time in bed reveals both boundaries: check bedtime date, bedtime, and wake-up time; wake-up date is the selected day. Enabling a new night suggests the previous date and 23:00–07:00, applied only after saving. Wake-up must follow bedtime. The interval uses local wall clocks and is not measured sleep duration, especially across clock changes. Legacy times without dates retain unknown dates until clarified. The quick day form changes only the rating/comment, preserving the existing interval and awakenings. Clearing all sleep details in the sleep editor deletes the record.

Legacy wellbeing text appears as an unrated moment; old sleep descriptions remain comments. No scores are guessed; text and UUIDs survive updates and JSON. New comments are limited to 1000 characters; an unchanged longer legacy comment can be retained. Water supports −1, +1, and an exact glass count; negative values are disallowed.

## Menu: catalogs and composition

Version 0.8.0 includes 100 basic products across all 15 categories, including buckwheat, rice, eggs, meats, and mince types. Generic bread, dumplings, and mince retain unknown compositions. Built-in dishes on a new installation are marked “Recipe template” in their comments: check them against your recipe. Names alone never establish lactose, garlic, or the absence of additives.

In Menu, choose Dishes, Products, or Medication. Dishes have homemade/restaurant filters; products have a Category filter. Search covers name, brand, and variant label; «Искать в составе» (Search composition) also finds nested ingredients, such as garlic in a cutlet through mince. All, Favorites, and Recent filters speed up selection. Recent contains up to 30 catalog entries from recorded history, ordered by local date/time; archived entries are hidden from ordinary selection.

«В избранное» (Favorite) saves a preference. «Архив» (Archive) shows hidden entries; «Вернуть в справочник» (Restore to catalog) restores the same identity and details. Archiving requires confirmation and retains history and recipe links. Creating an entry assigns a new UUID even if its name matches another entry; edit or restore the existing record when appropriate.

A product needs a name and category. Basic product represents a generic entry; Concrete product reveals brand and variant label. This keeps two breads with different compositions separate and distinguishable in lists and history. «Уточнить состав продукта» (Specify product composition) selects product components, including composite products. A dish requires a homemade/restaurant type and at least one product.

Product search filters components as you type without losing selections. Create a missing product in the same editor, specify its composition, and include it in the recipe immediately; it remains in the catalog if the dish is canceled. Optional component quantities can be expanded. Cycles and excessively large compositions are rejected without partial changes.

«Сведения о составе и комментарий» (Composition details and comment) reveals Unknown, Partial, or Confirmed by source, the source, check date, and optional comment. Confirmation requires a source and date. Changing components or quantities removes confirmed status until reviewed again. Sources are user-entered text; the app does not verify packaging. Names, brands, variant labels, and comments are limited to 1000 characters.

«Состав и источники» (Composition and sources) shows paths such as “cutlet → packaged mince → garlic,” revisions, known quantities, and sources for each node. Unknown or partial composition does not establish absence. The screen displays the first 200 nodes; backups retain the full tree.

Built-in products have stable keys. Updates add missing entries while preserving user edits, favorites, and archive state. On the first update, a single legacy basic entry with the same normalized name is linked to its key; a missing category is filled only for revision 1. Multiple legacy matches produce a Menu prompt: select an existing entry or create the built-in entry separately. Ambiguous identities are never automatically merged.

### Portions, history, and repeating meals

Intake forms provide category, favorite, recent, and composition-search filters too. Composition on a search result shows current details; Composition of item on selected or logged food shows its own snapshot. One dish, one product such as dumplings, or “cutlet + mash + cucumber” can form a single event.

«Порция и комментарий» (Portion and comment) expands an item quantity and comment; Intake comment adds an overall comment. Quantities are positive numbers with a selected unit: g, ml, or pieces. Blank means unknown; zero never substitutes for a missing value, and units are not automatically converted. Medication keeps its separate free-text dose.

Ordinary catalog or event-comment edits preserve logged food composition. Update composition in an existing event editor compares the old and current trees. Use current composition changes only that item when Save event is pressed; portion and comment remain. If composition changed after selection, saving stops and requests another review.

«+» → Repeat recent meal lists the last 10 meals. Review composition, portions, and the selected date, remove items, or explicitly refresh their compositions. Historical compositions and portions are retained by default; the event and its items receive new UUIDs, leaving the source unchanged. The old actual time is not copied. Archived items are marked for review.

Actual-time, scale, and completeness forms are available as described above.

Export creates JSON 4; import reads JSON 1–4. Composition is limited to 16 levels and 10,000 expanded nodes per meal. Legacy flat ingredients and unknown values receive no invented sources or quantities.

## Reminders and permission

«Расписание» (Schedule) provides four independent daily reminders: breakfast (08:00), lunch (13:00), dinner (19:00), and toilet (09:00). They default to enabled, but delivery needs notification permission. Use the switch to disable one reminder; «Изменить время» opens the shared HH:mm input form. Settings are saved locally after «Выбрать» (Select).

The app's only permission is POST_NOTIFICATIONS. On Android 13+, «Разрешить уведомления» requests it. If Android no longer displays the prompt, use «Открыть настройки Android» to enable notifications in system settings. On Android 8–12 the button opens system settings. The diary remains usable when permission is denied. The notification channel can also be disabled in Android settings.

Sleep, wellbeing, snacks, medication, and water are entered manually without reminders. Tapping a notification opens the diary. Each reminder type is sent at most once per local calendar day.

Android may delay inexact reminders to save battery. Reopen the app after rebooting or force-stopping it to restore reminders. Automatic boot restoration and exact alarms would need additional permissions, so they are not used. Time and timezone changes recalculate the schedule.

## Data: JSON backup

Open the bottom «Данные» (Data) tab. A backup includes the entire diary, catalogs, archived entries, historical meal compositions, doses, wellbeing and sleep-quality text, and reminder settings. The current date filter does not limit the backup. The file is not encrypted and contains health information; you choose where to store it and whom to share it with.

### Export

1. Tap «Экспорт резервной копии» (Export backup) and wait for the snapshot to be prepared.
2. Choose a location in the system dialog and save `nutrition-backup-YYYYMMDD-HHmmss.json`.
3. Wait for the saved message. Records added after snapshot preparation belong to the next backup.

No additional storage permission is needed. A copy outside the local database can be transferred to another device. Save a new verified backup before uninstalling.

### Import

1. Tap «Импорт из JSON» (Import JSON) and select a Nutrition Assistant JSON 1, 2, 3, or 4 backup in UTF-8, up to 16 MiB and 100,000 objects and components. Other apps' backups are not accepted.
2. Review «Проверка импорта» (Import preview): object counts, additions, matches, and conflicts. History merges into the current personal diary; the database is not cleared.
3. Explicitly approve matching same-name catalog entries with different UUIDs. Current local metadata, composition, favorites, and archive state remain by default. «Восстановить состав и архив справочника из файла» (Restore catalog composition and archive from file) replaces current matched product, dish, and medication details with the backup's values; historical meal and medication snapshots are unchanged. Enable this option for full restoration on a new installation. Transfer matching also includes basic product/variant, brand, and variant label. If this combination is ambiguous in either catalog, entries remain separate without name-based merging.
4. For differing data, confirm keeping local records. A conflicting intake keeps all its local items; the file does not overwrite it. Existing water, sleep, and day-completeness records on the same date also remain; importing new observations invalidates the corresponding previous confirmation. Long lists show the first 100 details of each kind and the total count.
5. Optionally select «Восстановить настройки напоминаний из файла» (Restore reminder settings from file). Current settings remain by default. Enable this option for full restoration on a new device.
6. Tap «Применить импорт» (Apply import) and wait for the result. «Отмена» (Cancel) closes the preview without changing the diary.

Matching UUIDs and previously approved matches are recognized on repeated imports, preventing duplicates. Validation and application cover the entire operation: corruption, unknown fields/versions, or a write failure cannot cause partial restoration. If the diary, catalog, or settings change after preview, select the file again. File operations and validation run in the background; another operation is blocked while one is running.

The preview and prepared backup survive screen recreation within the current process. After process termination, select the file again. Interrupted export may leave an empty or incomplete file: save a new backup and verify it by opening the import preview. Android notification permission is not transferred; check Schedule after restoration. The sent-notification log is not restored. Full database replacement, synchronization, and importing multiple profiles are unavailable.

[Format description and synthetic example](backup-format.md) are provided for transfer checks on a separate test installation.

## Updating an existing installation

Install an update over the previous app using the same signing key. Room migrations 1 → 2 → 3 → 4 → 5 → 6 → 7 preserve events and reminder settings. Schema 7 moves old state text into comments without guessing scores, makes sleep times optional, and adds scales with versions. Schema 6 adds favorites while preserving identity and history. Schema 5 adds nested composition, extra metadata, and snapshots; legacy values remain unknown and no day confirmations are fabricated. Schema 4 adds stable UUIDs and persistent import matches while retaining local numeric IDs and snapshots. Schema 3 moves former store-bought catalog entries into Products while retaining IDs and diary links. Logged store-bought items are relabeled as products; their names and known compositions are preserved. The store-bought category value is removed from current database records.

Former free-text dish ingredients become linked product entries; equal normalized names are deduplicated. Historical diary compositions stay unchanged. Schema-1 free-text food descriptions remain whole dishes marked “Type unspecified (legacy record),” without guessed ingredients. Old medication doses and sleep entries are preserved.

## Developer verification

```powershell
./gradlew.bat clean :app:assembleDebug :app:assembleRelease :app:testDebugUnitTest :app:lintDebug :app:assembleDebugAndroidTest --warning-mode=all
./gradlew.bat :app:connectedDebugAndroidTest --warning-mode=all
```

The second command needs a dedicated test emulator or device: the test runner may reinstall the app. Tests cover analysis and search, multi-item events and individual doses, historical snapshots, migration, creating a dish through the interface, the calendar grid, event counts, navigation, and selected-date restoration.

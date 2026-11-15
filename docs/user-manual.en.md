# Nutrition Assistant user manual

## Installation and launch

The app supports Android 8.0 and later. Open the project in Android Studio with JDK 17 or 21 and Android SDK Platform 35, or build the APK:

```powershell
./gradlew.bat :app:assembleDebug
```

Gradle Wrapper 8.13 is included. Configure the SDK using ANDROID_HOME or local.properties; the first build needs internet to download dependencies. The APK is app/build/outputs/apk/debug/app-debug.apk. Open it on a test device and allow installation from the selected source in Android settings. On macOS/Linux use ./gradlew :app:assembleDebug.

### Selecting Java for Gradle

In Android Studio, open **Settings → Build, Execution, Deployment → Build Tools → Gradle → Gradle JDK** and select an installed JDK 21 (JDK 17 also works with this project). When `GRADLE_LOCAL_JAVA_HOME` is selected, the `java.home` property in the local `.gradle/config.properties` file supplies the path. This machine-specific file is excluded from Git. After changing it, run **Sync Project with Gradle Files**.

Java 25 bundled with a newer Android Studio is incompatible with Gradle 8.13. `Incompatible Gradle JVM version` and `Unsupported class file major version 69` mean that a compatible build JDK must be selected. The Java runtime used to launch Android Studio itself is configured separately. For terminal builds, set `JAVA_HOME`; the wrapper does not automatically read `.gradle/config.properties`. Example for the JDK installed on this computer:

```powershell
$env:JAVA_HOME = 'C:\Program Files\Android\openjdk\jdk-21.0.8'
./gradlew.bat --version
./gradlew.bat :app:assembleDebug
```

Use your own installation path on another computer. `--version` should report a compatible JVM. [Configuring the Gradle JDK in Android Studio](https://developer.android.com/build/jdks).

The app opens «Календарь» (Calendar) with today's diary. The bottom tabs are «Расписание» (Schedule) for reminder times, «Календарь» (Calendar) for daily records and statistics, and «Меню» (Menu), the former food and medication catalogs. Light and dark themes follow Android settings; the palette, cards, and calendar are based on sports_activity_assistant. The interface is in Russian. No internet, registration, or server is required. One local user's data is stored in Room. Cloud backup is disabled. Import, export, and synchronization are unavailable; uninstalling the app deletes its data.

## Calendar and diary

The calendar displays six Monday-first weeks, including adjacent months. Arrows and horizontal swipes browse months; switching selects the first day of the new month, as in sports_activity_assistant. Tap a day to open its records; tapping an adjacent month's day also changes the visible month. «Сегодня» (Today) returns to the current date. «Выбрать дату» (Select date) below the calendar opens the system date picker. Today has an outline and the selected day is highlighted. The selected date and visible month survive screen recreation and tab changes.

Food, medication, and toilet icons mark days with saved events. The diary heading shows counts of meals, medication intakes, and toilet visits for the selected day. Multiple dishes in one meal or medicines in one intake count as one event. Edits and deletions update the calendar automatically; an empty day is never assumed symptom-free.

Scrolling the diary up collapses the calendar; returning to the top expands it. Vertical gestures over the calendar also scroll the diary. On short screens or with large system fonts, the calendar scrolls with the diary. «+» opens a choice of breakfast, lunch, dinner, snack, medication intake, or toilet entry. If a primary meal already exists, it opens for editing. Other sections remain available further down the diary. Each section supports adding, editing, and deleting records. Merely viewing a date does not create records.

Breakfast, lunch, and dinner each have one daily record containing multiple dishes. Multiple snacks and medication intakes are supported, as are multiple stool and wellbeing entries. Sleep and water count each have one record per day.

### Food

1. Tap «Добавить» (Add) for a meal or «Изменить» (Edit) for an existing record.
2. Choose homemade («Домашняя»), restaurant («Ресторанная»), or store-bought («Магазинная») food.
3. Type part of a name. Matches update as you type; case, extra whitespace, and Russian е/ё differences are normalized.
4. Tap dishes to include them. Select multiple dishes across food types. «Убрать из события» removes a selection.
5. If there is no exact name in the current catalog, choose «Добавить … в справочник и выбрать» (Add to catalog and select). Enter the name, type, and ingredients. Homemade dishes require at least one ingredient. The created dish is immediately selected in the event being edited.
6. Tap «Сохранить событие» (Save event).

Up to 40 matches are shown; refine the search as needed. Search text alone is never saved as a dish. Empty events cannot be saved. A created catalog entry remains even if the event is subsequently canceled.

### Medication

Tap «Добавить прием» (Add intake) and search by part of a medicine name. Create a missing entry from the same dialog to select it immediately. An intake can contain multiple medicines, each with an optional dose or quantity entered manually. The medication catalog starts empty; the app does not recommend medicines or doses.

### Stool

In «Туалет» (Toilet), select Bristol stool type 1–7. Types 1–2 are labeled constipation signs, 6–7 loose stool/diarrhea, 3–4 formed stool, and 5 soft stool. These are diary categories rather than diagnoses. Existing type-0 entries remain unassessed; new type-0 entries are not allowed.

After saving type 1–2 or 6–7, «Аналитика стула» (Stool analysis) updates automatically. It shows food from the previous calendar day relative to the event date, including snacks. For an October 18 record, this means October 17, rather than a rolling 24-hour window.

The algorithm compares dishes, food types, and known ingredients. Each factor shows days with the selected condition / all observed days following exposure, and the corresponding counts without exposure. Multiple stools or repeated ingredients on one day do not multiply day counts. Constipation and diarrhea are analyzed separately. Only history through the selected date is used.

Comparisons include only days with assessed stool records and food recorded on the preceding day. Missing records are never assumed healthy or symptom-free. A “repeated association” requires at least two affected days after exposure, at least two observed days without exposure, and a higher condition rate with exposure. Up to 12 factors are displayed, prioritizing repeated associations and then rate differences. Insufficient data is stated explicitly.

An association does not establish a cause. “Without exposure” means the factor was not recorded on the preceding day; results depend on diary completeness. Medication, infections, water, sleep, and wellbeing effects are not modeled. The algorithm gives no diagnoses, treatment advice, or recommendations to eliminate foods.

### Sleep, wellbeing, and water

Enter sleep manually: bedtime on the preceding evening, wake time on the selected day, and a free-text sleep-quality description. The initial 23:00 and 07:00 values are form defaults only; nothing is recorded until saved. Wellbeing uses free text. Water supports −1, +1, and an exact glass count; negative values are disallowed.

## Menu: catalogs

Open «Меню» (Menu), marked with a fork and knife icon, formerly «Справочники» (Catalogs). Food has three sections by source, prefilled with typical examples. Homemade examples include a basic ingredient list: adjust it to your recipe before logging food. Enter one ingredient per line; commas and semicolons are also accepted. Known ingredients can optionally be provided for restaurant and store-bought dishes.

Search, add, rename, change food type and ingredients, or remove entries from the catalog. Removal hides an entry from new events while preserving history. Creating the same name in the same type restores a hidden entry. Identical names are allowed across different food types; duplicates within a type are prevented. Creating an exact match selects the existing entry with its existing ingredients. Names and individual ingredients are limited to 1000 characters.

Catalog edits affect new events. Saved events retain their original names, food types, and ingredients. Editing an event also retains the historical composition of its existing selections; create a new event to record a new recipe.

## Reminders and permission

«Расписание» (Schedule) provides four independent daily reminders: breakfast (08:00), lunch (13:00), dinner (19:00), and toilet (09:00). They default to enabled, but delivery needs notification permission. Use the switch to disable one reminder and «Изменить время» to change its time. Settings are saved locally.

The app's only permission is POST_NOTIFICATIONS. On Android 13+, «Разрешить уведомления» requests it. If Android no longer displays the prompt, use «Открыть настройки Android» to enable notifications in system settings. On Android 8–12 the button opens system settings. The diary remains usable when permission is denied. The notification channel can also be disabled in Android settings.

Sleep, wellbeing, snacks, medication, and water are entered manually without reminders. Tapping a notification opens the diary. Each reminder type is sent at most once per local calendar day.

Android may delay inexact reminders to save battery. Reopen the app after rebooting or force-stopping it to restore reminders. Automatic boot restoration and exact alarms would need additional permissions, so they are not used. Time and timezone changes recalculate the schedule.

## Updating an existing installation

Install an update over the previous app using the same signing key. Room migration 1 → 2 preserves records and schedule settings. Each old free-text food description becomes a single catalog dish marked “Type unspecified (legacy record),” without guessing ingredients. Legacy entries appear in the catalog and can be edited; past events retain the original snapshot. Old medication entries are linked to the catalog while preserving doses. Existing sleep records are retained.

## Developer verification

```powershell
./gradlew.bat :app:testDebugUnitTest :app:lintDebug
./gradlew.bat :app:connectedDebugAndroidTest
```

The second command needs a dedicated test emulator or device: the test runner may reinstall the app. Tests cover analysis and search, multi-item events and individual doses, historical snapshots, migration, creating a dish through the interface, the calendar grid, event counts, navigation, and selected-date restoration.

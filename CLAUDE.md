# CLAUDE.md — Lorenz Flow

## What the app does

Lorenz Flow is an offline-first Android productivity suite: task/deadline manager
(categories, sorting, swipe-delete with undo, archive, A4 PDF export), color-coded
notes in a draggable staggered grid, a calendar day-view of due tasks, a Pomodoro
timer (Focus/Short/Long modes, custom durations, alarm + vibration), and a
hardware-encrypted password vault (AndroidKeyStore AES/CBC/PKCS7, KeyguardManager
or fallback SHA-256 master PIN, SecureRandom password generator), plus Profile and
Settings/About. Fully offline — no networking, no Firebase, no remote API.

## Stack

- Native Android, **100% Java** (no Kotlin, no Compose), Material Design 3 widgets
- Single-activity: `MainActivity` hosts fragments via `replace(R.id.fragment_container, ...).commit()`
  (sub-screens use `addToBackStack(null).commit()`); no Navigation component
- ViewBinding everywhere (bindings like `FragmentTasksBinding`); null out bindings in `onDestroyView()`
- Room DB `lorenz_flow_db`, **version 4** (tables `tasks`, `notes`, `passwords`).
  EVERY schema change needs a `Migration` in `AppDatabase`. NEVER call
  `fallbackToDestructiveMigration()`; pre-release v1/v2 may use `fallbackToDestructiveMigrationFrom(1, 2)`.
- JDK 17, Gradle 9.5.0 (wrapper), AGP 9.3.3, minSdk 26, targetSdk/compileSdk 34

## Build & test

- `./gradlew assembleDebug` → `app/build/outputs/apk/debug/app-debug.apk`; `./gradlew installDebug` to install
- `./gradlew testDebugUnitTest` — JVM tests (`src/test`), e.g. `EntitiesTest`
- `DaoTest` and `SecurityUtilTest` are **instrumented** (`src/androidTest`): run via
  `./gradlew connectedDebugAndroidTest` with a device/emulator attached

## Folder structure (app/src/main/java/com/lorenzflow/app)

- `MainActivity.java` — bottom-nav host, theme init before `super.onCreate()`, notification permission
- `data/` — `AppDatabase` (singleton + migrations), `Task`/`Note`/`PasswordEntry` entities + DAOs
- `notification/` — `AlarmReceiver`, `BootReceiver` (re-registers alarms on boot), `NotificationHelper`
- `ui/` — `TasksFragment`, `NotesFragment`, `CalendarFragment`, `PomodoroFragment`, `SettingsFragment`,
  `ProfileFragment`, `PasswordManagerFragment`, `AboutFragment`, `ArchiveTasksFragment`,
  `ArchiveNotesFragment` and adapters (`TaskAdapter`, `NoteAdapter`, `PasswordAdapter;` archive
  screens use their own inner adapters)
- `util/` — `SecurityUtil` (KeyStore crypto; encrypt returns ciphertext + base64 IV per entry),
  `PdfExportUtil` (PdfDocument → MediaStore/Documents dir), `AnimUtil` (list fade-in polish)
- `res/` — layouts, menus, drawables, `values/` + `values-night/` (dark mode uses parallel
  colors.xml/themes.xml)

## Conventions

- **All DB/heavy work off the main thread:** `Executors.newSingleThreadExecutor().execute(() -> { ... dao ... })`,
  then `getActivity() != null` check → `requireActivity().runOnUiThread(...)` and manually re-query
  (`loadTasks()`, `loadNotes()`, `loadAllTasks()`). No LiveData/ViewModel layer.
- **Strings go in `strings.xml`** — no hardcoded UI literals in layouts or Java. Category names
  ("Work" etc.) are DB data values kept as plain strings, not resources.
- `SharedPreferences` name is always `"lorenz_flow_prefs"`. Keys: `pref_theme` (0/1/2),
  `pref_notifications`, `pref_tasks_sort`, `pomodoro_focus_mins`/`pomodoro_short_mins`/`pomodoro_long_mins`,
  `profile_name`/`profile_bio`/`profile_image_uri`, `pm_master_pin_hash` (SHA-256 hex).
- **Design tokens:** use only `lf_*` colors/dimens (`lf_*` in values + values-night) from the soft &
  airy redesign. NEVER introduce new `ios_*` or `md_theme_*` colors in layouts/adapters.
  Cards: 24dp corners (`lf_corner_card`), 0 elevation, 16dp screen padding (`lf_screen_padding`).
- Alarms: `setExactAndAllowWhileIdle(RTC_WAKEUP, ...)` with `canScheduleExactAlarms()` +
  SecurityException fallback; PendingIntent codes `(int) task.getId()`,
  `FLAG_UPDATE_CURRENT | FLAG_IMMUTABLE`. Always cancel alarms on complete/edit/delete/archive;
  re-schedule on undo/restore when a future deadline exists.
- Room is the only annotation processor + ViewBinding only — don't add libraries without good reason.

## Known issues

- `BootReceiver` re-registers alarms on a plain executor **without `goAsync()`** — alarms may not
  survive reboot if the process is killed mid-work.
- `ArchiveTasksFragment.restoreTask()` doesn't re-schedule the task's deadline alarm.
- `PomodoroFragment` state is held only in the fragment — switching tabs resets the running timer.
- `TasksFragment` and `CalendarFragment` duplicate ~200 lines: add/edit task dialog, date/time
  picker, category mapping, and scheduleAlarm/cancelAlarm helpers.
- Profile, About, and the Archive screens, plus the add/edit dialogs, still use the older iOS
  styling instead of the `lf_*` soft-and-airy design tokens.

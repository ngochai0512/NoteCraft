# NoteCraft

A native Android note-taking app built with **Kotlin** and **Jetpack Compose**. NoteCraft brings a GoodNotes-style handwriting surface to Android: draw with pens and pencils, highlight text, erase, insert shapes and typed text, select with a lasso, and keep everything organized in a searchable note library.

The app is fully offline. Notes are stored as readable JSON files in the app's internal storage.

```
Package: com.notecraft
Min SDK: 26 (Android 8.0)
Target SDK: 36
UI: Jetpack Compose + Material 3
```

## Features

### Drawing tools

- **Pen** — smooth, rounded ink strokes
- **Pencil** — graphite-like texture with per-stroke seeded grain
- **Highlighter** — wide translucent strokes with multiply blending
- **Eraser** — adjustable stroke eraser that removes ink, shapes, and text
- **Shapes** — line, arrow, rectangle, and ellipse
- **Text** — tap to add text, tap existing text to edit it
- **Lasso** — draw a loop to select items, drag to move, then duplicate or delete

### Canvas

- Pinch to zoom up to 8×
- Two-finger pan
- Zoom controls with a fit-page button
- Undo and redo for every canvas, text, selection, and page operation
- A4-proportioned pages with a subtle paper shadow

### Notes and pages

- Create, rename, duplicate, and delete notes
- Add, duplicate, delete, and reorder pages
- Page backgrounds: blank, lined, grid, and dotted
- Page navigator with page dots and previous/next controls
- Live page thumbnails in the library
- Search notes by title
- Sort by recently updated, recently created, or title
- Adaptive grid that shows more columns on larger screens

### Colors and preferences

- 13 preset ink colors
- Custom HSV color picker
- Per-tool color and size settings
- Tool settings persist across app launches

### Appearance

- Material 3 light and dark themes
- Edge-to-edge layout
- Adaptive launcher icon

## Requirements

- **Android Studio** (project created with Android Studio 2026.2.2)
- **Android SDK 36**
- **JDK 17 or newer** — Android Studio's bundled JBR (JDK 25) works
- An Android device or emulator running **Android 8.0 (API 26)** or newer

## Open and run

1. Open Android Studio.
2. Choose **Open** and select this folder:
   ```
   /Users/ngochai/Desktop/NoteCraft
   ```
3. Wait for **Gradle sync** to finish.
4. Create or select an emulator in **Tools → Device Manager**:
   - API 36 system image
   - `arm64-v8a` on Apple Silicon
   - Any phone profile works; the app is portrait-first
5. Select the `app` run configuration and click **Run ▶**.

### Build from the terminal

```bash
cd ~/Desktop/NoteCraft
export JAVA_HOME="/Applications/Android Studio.app/Contents/jbr/Contents/Home"

./gradlew assembleDebug
```

The debug APK is written to:

```
app/build/outputs/apk/debug/app-debug.apk
```

Install it on a connected device or running emulator:

```bash
./gradlew installDebug
```

Or build a release APK (unsigned by default):

```bash
./gradlew assembleRelease
```

`local.properties` points Gradle at the Android SDK:

```properties
sdk.dir=/Users/ngochai/Library/Android/sdk
```

Update that path if the SDK is installed elsewhere.

## Drawing gestures

| Action | Gesture |
| --- | --- |
| Draw | One finger with a drawing tool |
| Erase | Drag the eraser over ink, shapes, or text |
| Select | Lasso tool → draw a loop around items |
| Move selection | Drag inside the selection bounds with the lasso tool |
| Duplicate / delete selection | Use the selection toolbar above the tool row |
| Pan | Drag with two fingers |
| Zoom | Pinch with two fingers, or use the canvas zoom buttons |
| Add text | Text tool → tap an empty spot on the page |
| Edit text | Text tool → tap existing text |
| Change page | Use the arrows or page dots below the top bar |
| Add a page | Use the `+` button in the page navigator |

## Project structure

```
app/src/main/
├── AndroidManifest.xml
├── java/com/notecraft/
│   ├── MainActivity.kt               # Edge-to-edge Compose host
│   ├── model/
│   │   └── Models.kt                 # Note, Page, Stroke, TextItem, tools, defaults
│   ├── data/
│   │   └── NoteRepository.kt         # JSON file persistence
│   ├── util/
│   │   ├── Geometry.kt               # Hit tests, lasso selection, bounds
│   │   └── Format.kt                 # Relative note dates
│   └── ui/
│       ├── NoteCraftApp.kt           # Library ↔ editor navigation
│       ├── NoteCraftViewModel.kt     # State, undo/redo, autosave, page operations
│       ├── theme/Theme.kt            # Material 3 colors, typography, palette
│       ├── components/
│       │   ├── Dialogs.kt            # Rename, confirm, and text-entry dialogs
│       │   └── NoteThumbnail.kt      # Library page previews
│       ├── library/
│       │   └── LibraryScreen.kt      # Note grid, search, sort, note actions
│       └── editor/
│           ├── EditorScreen.kt       # Top bar, page navigator, dialogs
│           ├── DrawingCanvas.kt      # Gestures, drawing input, zoom/pan
│           ├── PageRenderer.kt       # Paper, ink, pencil, shape, and text rendering
│           ├── EditorToolbar.kt      # Tool picker, colors, widths, color picker
│           └── EditorDialogs.kt      # Page background picker
└── res/
    ├── drawable/                     # Launcher icon artwork
    ├── mipmap-anydpi-v26/            # Adaptive launcher icons
    └── values/                       # Strings, colors, themes
```

## Data format

Each note is a JSON file at:

```
filesDir/notes/<note-id>.json
```

Notes contain pages, and each page contains strokes and text items. Coordinates are normalized to a **1000 × 1414** logical page, so drawings look the same at any zoom level or screen size.

A stroke saves:

- Tool type
- ARGB color
- Width
- Touch points with pressure values
- Optional shape type
- A seed for deterministic pencil texture

Saving is debounced while editing, then flushed when the editor closes or the app goes to the background.

## Architecture notes

- **State** lives in `NoteCraftViewModel`, exposed to Compose through `mutableStateOf`.
- **Persistence** runs on `Dispatchers.IO`; a `Mutex` serializes file writes.
- **Undo/redo** stores immutable page-list snapshots, capped at 80 entries.
- **Drawing** is rendered directly on a Compose `Canvas`; no bitmap layers are created.
- **Gestures** use `awaitEachGesture` with a custom state machine so one-finger drawing and two-finger zoom/pan work together.
- **Tool preferences** are stored in `SharedPreferences` under `notecraft_tool_prefs`.

## Known limitations

This is intentionally a compact version of a full note-taking app:

- The eraser removes entire strokes rather than erasing pixels.
- Text does not auto-wrap; use line breaks for multi-line notes.
- Undo history lasts for the current app session only.
- Notes are local to the device; there is no sync, export, or cloud backup.
- Pages scroll horizontally through the page navigator rather than a continuous scrolling canvas.

## Troubleshooting

### “Module not defined” in Android Studio

The IDE has not imported the Gradle module yet:

1. **File → Sync Project with Gradle Files**
2. If the module still does not appear, right-click the root `build.gradle.kts` → **Link Gradle Project**.
3. Confirm **Run → Edit Configurations → app → Module** is set to `NoteCraft.app`.

### “Unable to locate a Java Runtime”

Set Gradle's JDK:

```bash
export JAVA_HOME="/Applications/Android Studio.app/Contents/jbr/Contents/Home"
```

Or set **Settings → Build, Execution, Deployment → Build Tools → Gradle → Gradle JDK** to Android Studio's bundled JBR.

### Emulator does not start

- In **Device Manager**, use **Cold Boot Now**.
- Switch the AVD graphics setting to **Software**.
- On Apple Silicon, use an `arm64-v8a` system image.

## License

This project is provided as a sample application. Add a license file if you plan to distribute it.

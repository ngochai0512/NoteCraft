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

- **Fountain pen** — pressure-sensitive nib that opens up as you press
- **Ball pen** — uniform, crisp ink line
- **Brush pen** — wide, soft strokes with strong pressure response
- **Marker** — broad translucent highlighter with multiply blending
- **Eraser** — adjustable eraser with **Partial** (split the touched stroke) and **Object** (remove the whole stroke) modes; shapes and text are removed whole
- **Shapes** — line, arrow, rectangle, and ellipse
- **Tape** — translucent washi-style strip for covering and decorating
- **Laser pointer** — a red trail that fades when you lift your finger
- **Pencil** — textured graphite line
- **Text** — resizable typed text boxes with wrapping, left/center/right alignment, and edit-in-place
- **Lasso** — select strokes and text to move, duplicate or delete
- **Ruler** — toggle a draggable straight-edge guide; strokes started near it snap to a straight line

**Jetpack Ink 1.0.0** powers low-latency authoring for the fountain pen, ball pen, brush pen, and highlighter. Finished Ink strokes are normalized into NoteCraft's page model so persistence, undo/redo, erasing, and lasso selection continue to work with the rest of the app. Pencil, shapes, tape, laser, text, and lasso remain custom. Ruler and stylus-only modes use the custom renderer so snapped points and pointer types stay under app control.

Tapping a tool selects it and opens that tool's color, size, and shape options in a compact dropdown. Tap the same tool again or touch outside to dismiss it; the ribbon otherwise stays collapsed so the canvas keeps more space.

### Canvas

- Edge-to-edge paper that fills the editor; no desk backdrop, page border, or drop shadow
- Ruled, squared graph, dotted and blank paper drawn continuously across the viewport; Cornell, planner, weekly, music, storyboard and checklist templates stay page-bounded
- Draw anywhere on the visible paper, including outside the logical page bounds
- Pinch to zoom up to 8× with unbounded two-finger pan
- Floating zoom controls with fit-page, page-thumbnail, page-background, and ruler buttons
- Straight-edge ruler guide: drag its handle to move it and slide sideways to rotate, then draw along it for perfectly straight lines
- Undo and redo for every canvas, text, selection, and page operation

### Notes and pages

- Create, rename, duplicate, and delete notes
- Add, duplicate, delete, and reorder pages by long-pressing a page thumbnail and dragging it
- Star a page as a bookmark; the star is shown on its page-drawer thumbnail
- Page backgrounds: blank, ruled, squared graph, dotted, Cornell, daily planner, weekly planner, music staff, storyboard and checklist
- Compact page navigator with previous/next controls
- Two-page spread on tablets in landscape, with the left page as the current page and both pages editable
- Page thumbnail drawer accessible from the zoom controls, with a quick add-page tile
- Live notebook-cover previews in the library
- Notebook covers with spine crease, ribbon bookmark and label badge
- Cover-tone chooser in the library menu and editor, with an automatic default
- Export a notebook to PDF through the system document picker
- Search notes by title
- Sort by recently updated, recently created, or title
- Adaptive grid that shows more columns on larger screens

### Colors and preferences

- 12 preset ink colors
- Recently used inks shown in each tool's dropdown
- Custom HSV color picker
- Per-tool color, size, and eraser-mode settings
- Dedicated Settings screen (not a dialog) with a back action
- Theme override: system, light, or dark
- Default drawing tool and default paper for new pages
- Toggle for the editor’s autosave indicator
- Stylus-only mode that ignores finger drawing and palm touches while keeping two-finger pan and zoom
- Cloud backup, handwriting search and audio notes are visible as **Coming soon** placeholders
- Tool and app preferences persist across app launches

### Appearance

- Design system defined in [DESIGN.md](DESIGN.md)
- Ink Blue primary, tactile paper surfaces and notebook cover tones
- Responsive phone and tablet layouts in portrait and landscape
- Compact icon-only ribbon in landscape; horizontal zoom dock in short landscape
- Adaptive notebook grid, page drawer columns, and scrollable dialogs
- Material 3 light and dark themes
- Adaptive launcher icon with a dedicated round icon
- Display cutout-aware chrome: toolbars, overlays, sheets, and grids stay inside safe insets while the paper remains edge-to-edge
- Edge-to-edge layout and press-scale tool feedback

## Requirements

- **Android Studio** (project created with Android Studio 2026.2.2)
- **Android SDK 36**
- **JDK 17 or newer** — Android Studio's bundled JBR (JDK 25) works
- An Android device or emulator running **Android 8.0 (API 26)** or newer

## Open and run

1. Open Android Studio.
2. Choose **Open** and select this folder:
   ```
   /Users/ngochai/Desktop/NoteCraft-Group-Project
   ```
3. Wait for **Gradle sync** to finish.
4. Create or select an emulator in **Tools → Device Manager**:
   - API 36 system image
   - `arm64-v8a` on Apple Silicon
   - Any phone profile works; the app supports portrait and landscape. Enable **Auto-rotate** in the emulator/device quick settings to rotate with the sensor.
5. Select the `app` run configuration and click **Run ▶**.

### Build from the terminal

```bash
cd ~/Desktop/NoteCraft-Group-Project
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

Run the JVM unit tests:

```bash
./gradlew :app:testDebugUnitTest
```

### Testing display cutouts

Simulate a cutout on an emulator or device with Developer options:

```bash
adb shell cmd overlay enable com.android.internal.display.cutout.emulation.tall
```

Rotate to landscape and check that the top bar, tool ribbon, zoom controls, page navigator, sheets, and library grid stay clear of the cutout while the editor paper remains edge-to-edge. Disable it with:

```bash
adb shell cmd overlay disable com.android.internal.display.cutout.emulation.tall
```

`local.properties` points Gradle at the Android SDK:

```properties
sdk.dir=/Users/ngochai/Library/Android/sdk
```

Update that path if the SDK is installed elsewhere.

## Drawing gestures

| Action | Gesture |
| --- | --- |
| Draw | One finger or stylus with a drawing tool |
| Erase | Drag the eraser over ink; **Partial** splits the stroke, **Object** removes the whole stroke; shapes and text are removed whole |
| Straight line | Enable the ruler, drag its handle to position and rotate it, then draw near the edge |
| Select | Lasso tool → draw a loop around items |
| Move selection | Drag inside the selection bounds with the lasso tool |
| Duplicate / delete selection | Use the selection toolbar above the tool row |
| Pan | Drag with two fingers |
| Zoom | Pinch with two fingers, or use the canvas zoom buttons |
| Add text | Text tool → tap an empty spot on the page |
| Edit text | Text tool → tap existing text |
| Resize text box | Text tool → drag the box's bottom-right handle |
| Bookmark a page | Open the page drawer and tap its star, or use the editor menu |
| Reorder pages | Open the page drawer, long-press a thumbnail, and drag it |
| Change page | Use the previous/next arrows, or open the page-thumbnail drawer from the zoom controls |
| Add a page | Open the page-thumbnail drawer from the zoom controls and tap **Add page** |

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
│   │   ├── Geometry.kt               # Hit tests, partial eraser, lasso, bounds
│   │   ├── PdfExporter.kt            # Notebook-to-PDF rendering
│   │   └── Format.kt                 # Relative note dates
│   └── ui/
│       ├── NoteCraftApp.kt           # Library ↔ editor navigation
│       ├── NoteCraftViewModel.kt     # State, undo/redo, autosave, page operations
│       ├── theme/Theme.kt            # Material 3 colors, typography, palette
│       ├── components/
│       │   ├── Dialogs.kt            # Rename, confirm, and text-entry dialogs
│       │   ├── CoverToneDialog.kt    # Notebook cover-tone chooser
│       │   └── NoteThumbnail.kt      # Library page previews
│       ├── settings/
│       │   └── SettingsScreen.kt     # Dedicated preferences screen
│       ├── library/
│       │   └── LibraryScreen.kt      # Note grid, search, sort, note actions
│       └── editor/
│           ├── EditorScreen.kt       # Top bar, page drawer, dialogs
│           ├── DrawingCanvas.kt      # Gestures, drawing input, zoom/pan
│           ├── PageRenderer.kt       # Paper, ink, pencil, shape, and text rendering
│           ├── EditorToolbar.kt      # Tool picker, colors, widths, color picker
│           └── EditorDialogs.kt      # Page background picker
└── res/
    └── values/                       # Strings, colors, themes
```

## Data format

Each note is a JSON file at:

```
filesDir/notes/<note-id>.json
```

Notes contain pages, and each page contains strokes and text items. Coordinates are normalized to a **1000 × 1414** logical page, so drawings look the same at any zoom level or screen size.

A note also stores an optional `coverTone` name; when absent, the library derives a stable cover tone from the note id. A page may also store `bookmarked`. A stroke saves:

- Tool type
- ARGB color
- Width
- Touch points with pressure values
- Optional shape type
- A seed for deterministic pencil texture

A text item stores its position, text, color, font size, optional `width` in page units, and an optional `alignment` (`LEFT`, `CENTER`, `RIGHT`). Missing width means a legacy auto-width box that only breaks on explicit newlines; new text boxes wrap inside their stored width.

Saving is debounced while editing, then flushed when the editor closes or the app goes to the background.

## Architecture notes

- **State** lives in `NoteCraftViewModel`, exposed to Compose through `mutableStateOf`.
- **Persistence** runs on `Dispatchers.IO`; a `Mutex` serializes file writes.
- **Undo/redo** stores immutable page-list snapshots, capped at 80 entries.
- **Drawing** is rendered directly on a Compose `Canvas` in the editor; PDF export rasterizes pages separately.
- **Gestures** use `awaitEachGesture` with a custom state machine so one-finger drawing and two-finger zoom/pan work together.
- **Tool preferences** are stored in `SharedPreferences` under `notecraft_tool_prefs`, including recently used colors and the eraser mode.
- **App preferences** (theme, default tool, default paper, autosave indicator, stylus-only mode) are stored under `notecraft_settings`.
- **PDF export** rasterizes each page through the same renderer at 150 dpi and writes an A4 PDF selected with the system document picker.
- **Ink authoring** uses `androidx.ink:ink-authoring-compose` for fountain, ball, brush, and highlighter tools. Wet strokes are rendered by `InProgressStrokes`; finished strokes are converted to immutable NoteCraft strokes and handed to the existing page state, so the downstream canvas, persistence, history, eraser, and lasso remain the source of truth.
- **Native Ink** requires the Ink native loader libraries, which are packaged for all supported ABIs in the APK.

## Known limitations

This is intentionally a compact version of a full note-taking app:

- The eraser removes the touched portion of a stroke (Partial) or the whole stroke (Object) rather than individual pixels; shapes and text are removed whole.
- Text boxes wrap at their stored width, but height is automatic; there is no vertical box constraint.
- Undo history lasts for the current app session only.
- Notes are local to the device; there is no sync, cloud backup, or import yet. PDF export is available.
- Pages are navigated with the page controls and thumbnail drawer rather than a continuous swipe canvas; tablets in landscape show a two-page spread.
- Media insertion and collaboration are not implemented yet.
- Ink is used for authoring and wet rendering; dry strokes still use NoteCraft's renderer, so wet and dry Ink strokes can differ slightly.
- Pencil, shape, tape, laser, text, and lasso interactions remain custom.
- The two-page spread is tablet-landscape only; stylus-only mode uses the custom renderer instead of the Ink overlay.

## Design system

The interface follows [DESIGN.md](DESIGN.md):

- **Brand**: Ink Blue `#2563EB`, with Focus Azure containers
- **Paper**: `#FCFCFA` / `#FBF8F1` fill that covers the entire editor
- **Ink and text**: `#0F172A`, `#475569`, `#94A3B8`
- **Accents**: highlighter yellow `#FEF08A`, mint `#10B981`, tag purple `#8B5CF6`
- **Covers**: Navy Leather, Terracotta, Forest Sage, Pastel Lavender and Charcoal
- **Type**: 24 sp display, 18 sp titles, 15/14 sp body, 12 sp captions and 10 sp micro labels
- **Shapes**: 8–12 dp tool pills, 16–20 dp floating ribbons, 10 dp notebook covers and 24 dp modals

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

NoteCraft is released under the [MIT License](LICENSE).

Third-party libraries retain their own licenses, including AndroidX, Jetpack Compose, Jetpack Ink, and Material Icons (Apache License 2.0).


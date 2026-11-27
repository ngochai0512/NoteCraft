# NoteCraft

A native Android note-taking app built with **Kotlin** and **Jetpack Compose**. NoteCraft brings a completely new handwriting surface and experience to Android: draw with pens and pencils, highlight text, erase, insert shapes and typed text, select with a lasso, and keep everything organized in a searchable note library.

The app is fully offline. Notes are stored as readable JSON files in the app's internal storage.

This app's name is insprired by a collection of free and open-source apps made by ArtCraft (not made and distributed by ArtCraft). Also, this app is built for the group project presentation in January 2027.

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
- **Text** — resizable typed text boxes with wrapping, left/center/right alignment, bold/italic/underline, bullet/numbered lists, line spacing, highlight, and tap-to-edit
- **Sticky notes** — lightweight colored callouts with wrapped text and adjustable text size; tap to add, drag to move, tap to edit
- **Lasso** — select strokes and text to move, resize, rotate, copy/cut/paste, duplicate or delete
- **Ruler** — toggle a draggable straight-edge guide; strokes started near it snap to a straight line

**Jetpack Ink 1.0.0** powers low-latency authoring for the fountain pen, ball pen, brush pen, and highlighter. Finished Ink strokes are normalized into NoteCraft's page model so persistence, undo/redo, erasing, and lasso selection continue to work with the rest of the app. Pencil, shapes, tape, laser, text, and lasso remain custom. Ruler and stylus-only modes use the custom renderer so snapped points and pointer types stay under app control.

Tapping a tool selects it and opens that tool's color, size, and shape options in a compact dropdown. Tap the same tool again or touch outside to dismiss it; the ribbon otherwise stays collapsed so the canvas keeps more space.

### Canvas

- Edge-to-edge paper that fills the editor; no desk backdrop, page border, or drop shadow
- Paper tint fills the viewport edge-to-edge; ruled, squared graph, dotted, and template guidelines are drawn inside each page's bounds and pan and zoom with the content
- Draw on any visible page, including horizontally outside the logical page bounds
- Pinch to zoom up to 8× with two-finger pan; vertical panning stays within the page stack
- Dark paper follows the app theme; dark ink and text are automatically lifted for contrast, while the stored colors stay untouched
- Floating zoom controls with fit-page, page-thumbnail, page-background, and ruler buttons
- Straight-edge ruler guide: drag its handle to move it and slide sideways to rotate, then draw along it for perfectly straight lines
- Undo and redo for every canvas, text, selection, and page operation

### Notes and pages

- Create, rename, duplicate, and delete notes
- **New notebook** starts from the default template; **New from template** opens a preview chooser for blank, ruled, grid, dotted, Cornell, planner, weekly, music, storyboard and checklist notebooks
- Pin notebooks to keep them at the top of the library, regardless of the selected sort
- Organize notebooks with flat folders and comma-separated tags, then filter the library by folder or tag
- Add, duplicate, delete, and reorder pages by long-pressing a page thumbnail and dragging it
- Star a page as a bookmark; the star is shown on its page-drawer thumbnail
- Page backgrounds: blank, ruled, squared graph, dotted, Cornell, daily planner, weekly planner, music staff, storyboard and checklist
- Continuous vertical page scrolling with a passive page indicator; no previous/next arrows
- Two-finger drag scrolls through pages with inertia; the page drawer jumps directly to a page
- Page thumbnail drawer accessible from the zoom controls, with a quick add-page tile
- Live notebook-cover previews in the library
- Notebook covers with spine crease, ribbon bookmark and label badge
- Cover-tone chooser in the library menu and editor, with an automatic default
- Export a notebook to PDF through the system document picker
- Export the current page or a page range as PDF, or share the current page as a PNG through the Android share sheet
- Search notes by title, folder, and tag
- Sort by recently updated, recently created, or title
- Adaptive grid that shows more columns on larger screens

### Colors and preferences

- 12 preset ink colors
- Recently used inks shown in drawing-tool dropdowns
- Custom HSV color picker
- Per-tool color and size settings, plus a shared eraser mode
- Dedicated Settings screen (not a dialog) with a back action
- Theme override: system, light, or dark
- Default drawing tool, default notebook template, and default paper for new pages
- Toggle for the editor’s autosave indicator
- Stylus-only mode that ignores finger drawing and palm touches while keeping two-finger pan and zoom
- Tool and app preferences persist across app launches

### Appearance

- Design system defined in [DESIGN.md](DESIGN.md)
- Ink Blue primary, tactile paper surfaces and notebook cover tones
- Responsive phone and tablet layouts in portrait and landscape
- Compact icon-only ribbon in landscape; horizontal zoom dock in short landscape
- Adaptive notebook grid, page drawer columns, and scrollable dialogs
- Material 3 light and dark themes, including a dark edge-to-edge paper surface
- Adaptive launcher icon with a dedicated round icon
- Display cutout-aware chrome: toolbars, overlays, sheets, and grids stay inside safe insets while the paper remains edge-to-edge
- Edge-to-edge layout and press-scale tool feedback

## Requirements

- **Android Studio 2026.2 or newer**
- **Android SDK 36**
- **JDK 17 or newer** — Android Studio's bundled JBR (JDK 25) works
- An Android device or emulator running **Android 8.0 (API 26)** or newer

## Open and run

1. Open Android Studio.
2. Choose **Open** and select this folder:
   ```
   /path/to/NoteCraft
   ```
3. Wait for **Gradle sync** to finish.
4. Create or select an emulator in **Tools → Device Manager**:
   - API 36 system image
   - `arm64-v8a` on Apple Silicon
   - Any phone profile works; the app supports portrait and landscape. Enable **Auto-rotate** in the emulator/device quick settings to rotate with the sensor.
5. Select the `app` run configuration and click **Run ▶**.

### Build from the terminal

```bash
cd /path/to/NoteCraft
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

Rotate to landscape and check that the top bar, tool ribbon, zoom controls, page indicator, sheets, and library grid stay clear of the cutout while the editor paper remains edge-to-edge. Disable it with:

```bash
adb shell cmd overlay disable com.android.internal.display.cutout.emulation.tall
```

`local.properties` points Gradle at the Android SDK:

```properties
sdk.dir=/path/to/Android/sdk
```

Replace `/path/to/Android/sdk` with your local SDK location.

## CI builds

`.github/workflows/ci.yml` runs on pushes to `main`, pull requests, and manual dispatch. It runs the unit tests, builds the debug APK, and uploads it as a workflow artifact.

CI debug artifacts are debug-signed and intended for testing, not Play distribution. Because GitHub-hosted runners generate a fresh debug signing key, uninstall a previous CI build before installing a newer artifact. For a local installable build, use `./gradlew assembleDebug`; the APK is at `app/build/outputs/apk/debug/app-debug.apk`.

## GitHub releases

`.github/workflows/release.yml` runs when a `v*` tag is pushed or when started manually. It builds a signed release APK, verifies the signature, uploads it as a workflow artifact, and attaches it to the GitHub Release for tag pushes. A Google Play developer account is not needed; a self-signed release keystore is enough.

Create a release keystore once and keep it backed up:

```bash
keytool -genkeypair -v \
  -keystore notecraft-release.jks \
  -alias notecraft \
  -keyalg RSA -keysize 2048 -validity 10000
```

Add these repository secrets:

- `ANDROID_KEYSTORE_BASE64` — Base64-encoded `notecraft-release.jks`
- `ANDROID_KEYSTORE_PASSWORD`
- `ANDROID_KEY_ALIAS`
- `ANDROID_KEY_PASSWORD`

Encode the keystore on macOS with:

```bash
base64 -i notecraft-release.jks | pbcopy
```

Tag a release with:

```bash
git tag v1.0.0
git push origin v1.0.0
```

The tag supplies `versionName`; `versionCode` defaults to the workflow run number. Manual runs require a version name. Keep the same keystore for future updates, and never commit it. Play Store AAB publishing is not configured.

## Drawing gestures

| Action | Gesture |
| --- | --- |
| Draw | One finger or stylus with a drawing tool |
| Erase | Drag the eraser over ink; **Partial** splits the stroke, **Object** removes the whole stroke; shapes and text are removed whole |
| Straight line | Enable the ruler, drag its handle to position and rotate it, then draw near the edge |
| Select | Lasso tool → draw a loop around items |
| Move selection | Drag inside the selection bounds with the lasso tool |
| Resize selection | Drag any of the four corner handles |
| Rotate selection | Drag the round handle above the selection box |
| Copy / cut / paste | Use the selection toolbar; the clipboard survives closing a notebook |
| Duplicate / delete selection | Use the selection toolbar above the tool row |
| Pan | Drag with two fingers |
| Zoom | Pinch with two fingers, or use the canvas zoom buttons |
| Add text | Text tool → tap an empty spot on the page |
| Edit text | Text tool → tap existing text |
| Format text | Text dialog → style, list, line-spacing, and highlight controls |
| Resize text box | Text tool → drag the box's bottom-right handle |
| Add sticky note | Sticky tool → tap an empty spot |
| Move sticky note | Sticky tool → drag the note |
| Edit sticky note | Sticky tool → tap the note |
| Bookmark a page | Open the page drawer and tap its star, or use the editor menu |
| Reorder pages | Open the page drawer, long-press a thumbnail, and drag it |
| Change page | Scroll vertically with two fingers, or open the page-thumbnail drawer from the zoom controls |
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
│   │   ├── Format.kt                 # Relative note dates
│   │   ├── Geometry.kt               # Hit tests, partial eraser, lasso, text, sticky bounds
│   │   ├── NoteOrganization.kt       # Folder/tag normalization and library filters
│   │   ├── PageRange.kt              # Page-range validation for PDF export
│   │   ├── PageReorder.kt            # Page drawer drag-to-reorder helper
│   │   ├── PageSharer.kt             # PNG page rendering and Android share sheet
│   │   ├── PdfExporter.kt            # Notebook and page-range PDF rendering
│   │   └── TextLayout.kt             # Cached text layouts and measured bounds
│   └── ui/
│       ├── NoteCraftApp.kt           # Library, editor, and settings switching
│       ├── NoteCraftViewModel.kt     # State, undo/redo, autosave, page operations
│       ├── theme/Theme.kt            # Material 3 colors, typography, palette
│       ├── components/
│       │   ├── Dialogs.kt            # Rename, confirm, and text-entry dialogs
│       │   ├── CoverToneDialog.kt    # Notebook cover-tone chooser
│       │   ├── NoteOrganizationDialog.kt # Folder and tag editor
│       │   ├── NoteTemplateDialog.kt # New-from-template chooser
│       │   └── NoteThumbnail.kt      # Cached library page previews
│       ├── settings/
│       │   └── SettingsScreen.kt     # Dedicated preferences screen
│       ├── library/
│       │   └── LibraryScreen.kt      # Note grid, search, sort, note actions
│       └── editor/
│           ├── CanvasControls.kt     # Zoom dock and ruler handle
│           ├── CanvasDrawing.kt      # Shared canvas draw helpers and Ink conversion
│           ├── DrawingCanvas.kt      # Gestures, drawing input, zoom/pan
│           ├── EditorDialogs.kt      # Page background picker
│           ├── EditorScreen.kt       # Top bar, page drawer, menus, dialogs
│           ├── EditorToolbar.kt      # Compact tool ribbon and selection bar
│           ├── EditorToolOptions.kt  # Tool options, palette, and HSV color picker
│           ├── PageDrawer.kt         # Page thumbnail grid, reorder, and bookmarks
│           └── PageRenderer.kt       # Paper, ink, pencil, shape, text, and sticky rendering
└── res/
    ├── values/                       # Strings, colors, themes
    └── values-night/                 # Dark colors and theme
```

## Data format

Each note is a JSON file at:

```
filesDir/notes/<note-id>.json
```

Files carry `version: 1`. Notes contain pages, and each page contains strokes, text items, and sticky notes. Coordinates are normalized to a **1000 × 1414** logical page, so drawings look the same at any zoom level or screen size.

A note also stores an optional `coverTone` name; when absent, the library derives a stable cover tone from the note id. A note may store `pinned` to keep it above unpinned notebooks, plus an optional `folder` and a `tags` array. A page may also store `bookmarked`. A stroke saves:

- Tool type
- ARGB color
- Width
- Touch points with pressure values
- Optional shape type
- A seed for deterministic pencil texture

A text item stores its position, text, color, font size, optional `width` in page units, an optional measured `height`, and an optional `alignment` (`LEFT`, `CENTER`, `RIGHT`). It may also store `bold`, `italic`, `underline`, `listStyle` (`BULLET` or `NUMBERED`), `lineSpacing`, and `highlightColor`. Missing width means a legacy auto-width box that only breaks on explicit newlines; new text boxes wrap inside their stored width, while height is measured at render time.

A sticky note stores its position, size, text, color, and font size. It renders as a colored card above ink and text and is included in PDF export and page sharing.

Saving is debounced (550 ms) while editing, then flushed when the editor closes or the app goes to the background.

## Architecture notes

- **State** lives in `NoteCraftViewModel`, exposed to Compose through `mutableStateOf`.
- **Persistence** runs on `Dispatchers.IO`; a `Mutex` serializes file writes.
- **Undo/redo** stores immutable page-list snapshots, capped at 80 entries.
- **Drawing** is rendered directly on a Compose `Canvas` in the editor; PDF export rasterizes pages separately.
- **Gestures** use `awaitEachGesture` with a custom state machine so one-finger drawing and two-finger zoom/pan work together.
- **Tool preferences** are stored in `SharedPreferences` under `notecraft_tool_prefs`, including recently used colors and the eraser mode.
- **App preferences** (theme, default tool, default notebook template, default paper, autosave indicator, stylus-only mode) are stored under `notecraft_settings`.
- **PDF export** rasterizes each page through the same renderer at 150 dpi and writes an A4 PDF selected with the system document picker.
- **Page sharing** rasterizes the current page to a PNG in app cache and launches the Android share sheet through `FileProvider`.
- **Ink authoring** uses `androidx.ink:ink-authoring-compose` for fountain, ball, brush, and highlighter tools. Wet strokes are rendered by `InProgressStrokes`; finished strokes are converted to immutable NoteCraft strokes and handed to the existing page state, so the downstream canvas, persistence, history, eraser, and lasso remain the source of truth.
- **Native Ink** requires the Ink native loader libraries, which are packaged for all supported ABIs in the APK.

## Known limitations

This is intentionally a compact version of a full note-taking app:

- The eraser removes the touched portion of a stroke (Partial) or the whole stroke (Object) rather than individual pixels; shapes and text are removed whole.
- Text boxes wrap at their stored width, but height is automatic; there is no vertical box constraint.
- Sticky notes are moved and edited directly with the Sticky tool; they are not part of lasso selection.
- Rotating a selection rotates strokes precisely; typed text boxes orbit the selection center but stay upright, and sticky notes are excluded.
- Dark paper affects the editor and library thumbnails. PDF export and PNG sharing always render light paper so shared documents stay print-friendly.
- PNG sharing works on the current page; PDF export supports the current page, a page range, or the whole notebook.
- Undo history and the selection clipboard last for the current app session only.
- Notes are local to the device; there is no sync, cloud backup, or import yet. PDF export is available.
- Continuous vertical scrolling uses a two-finger drag so one-finger drawing stays untouched, and the page stack has no previous/next buttons.
- Media insertion and collaboration are not implemented yet.
- Ink is used for authoring and wet rendering; dry strokes still use NoteCraft's renderer, so wet and dry Ink strokes can differ slightly.
- Pencil, shape, tape, laser, text, sticky notes, and lasso interactions remain custom.
- Stylus-only mode uses the custom renderer instead of the Ink overlay.

## Design system

The interface follows [DESIGN.md](DESIGN.md):

- **Brand**: Ink Blue `#2563EB`, with Focus Azure containers
- **Paper**: `#FCFCFA` / `#FBF8F1` in light mode; `#1B2432` / `#141B26` with slate rules in dark mode
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

## Credits

- Pen glyph in `pen.svg`: [SVG Repo](https://www.svgrepo.com/), exported with SVG Repo Mixer.


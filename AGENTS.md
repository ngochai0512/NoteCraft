# AGENTS.md — NoteCraft (Android)

Guidance for AI coding assistants working in this repository. Read this file before changing code.
Keep this file current: when a rule is wrong or repeatedly causes trouble, update it deliberately
rather than silently ignoring it.

## 1. What this project is

NoteCraft is a fully offline native Android note-taking app with a GoodNotes-style handwriting
surface.

- **Kotlin + Jetpack Compose + Material 3** (no XML layouts and no legacy Views UI).
- Package `com.notecraft`; single Gradle module `:app`.
- `minSdk 26`, `compileSdk 36`, `targetSdk 36`.
- Gradle 9.8.1, AGP 9.5.0, Kotlin 2.2.10, JVM target 17.
- One `MainActivity`, one app-wide `NoteCraftViewModel`, JSON file persistence.
- **Jetpack Ink 1.0.0** provides low-latency authoring for the fountain pen, ball pen, brush pen,
  and highlighter.
- No network, no database, no dependency-injection framework, no navigation library.

The app must work on phones and tablets, in portrait and landscape, with edge-to-edge paper and
display-cutout-safe chrome.

## 2. Read before editing

Do not answer from memory or guess APIs. Read the files that own the behavior you are changing:

- `README.md` — features, build instructions, data format, known limitations.
- `DESIGN.md` — visual language, tokens, canvas behavior.
- `app/src/main/java/com/notecraft/model/Models.kt` — page size, tools, strokes, text, defaults.
- `app/src/main/java/com/notecraft/ui/NoteCraftViewModel.kt` — all app state, pages, undo/redo,
  autosave.
- `app/src/main/java/com/notecraft/data/NoteRepository.kt` — JSON format and persistence.
- `app/src/main/java/com/notecraft/ui/editor/DrawingCanvas.kt` — gestures, viewport, Ink overlay.
- `app/src/main/java/com/notecraft/ui/editor/PageRenderer.kt` — paper, backgrounds, strokes, text.
- `app/src/main/java/com/notecraft/ui/editor/EditorScreen.kt` — editor scaffold, menus, dialogs.
- `app/src/main/java/com/notecraft/ui/editor/PageDrawer.kt` — page grid, reorder, bookmark star.
- `app/src/main/java/com/notecraft/ui/editor/EditorToolbar.kt` — compact tool ribbon.
- `app/src/main/java/com/notecraft/ui/editor/EditorToolOptions.kt` — tool options and color picker.
- `app/src/main/java/com/notecraft/ui/editor/CanvasControls.kt` — zoom dock and ruler handle.
- `app/src/main/java/com/notecraft/ui/editor/CanvasDrawing.kt` — shared canvas draw helpers and Ink conversion.
- `app/src/main/java/com/notecraft/ui/library/LibraryScreen.kt` — note library.
- `app/src/main/java/com/notecraft/ui/settings/SettingsScreen.kt` — dedicated preferences screen.
- `app/src/main/java/com/notecraft/ui/components/CoverToneDialog.kt` — notebook cover chooser.
- `app/src/main/java/com/notecraft/ui/components/NoteTemplateDialog.kt` — new-from-template chooser.
- `app/src/main/java/com/notecraft/ui/components/NoteThumbnail.kt` — cached library thumbnails.
- `app/src/main/java/com/notecraft/util/PdfExporter.kt` — PDF rendering and export.
- `app/src/main/java/com/notecraft/util/PageSharer.kt` — PNG page sharing through `FileProvider`.
- `app/src/main/java/com/notecraft/util/Geometry.kt` — hit tests, partial eraser, lasso selection.
- `app/src/main/AndroidManifest.xml`, `app/build.gradle.kts`, `settings.gradle.kts`.

If code and documentation disagree, say so and resolve it deliberately rather than silently picking
one.

For general Android/Kotlin/Compose/Material 3 questions, consult the personal
`android-native-dev` skill at `/Users/ngochai/Desktop/android-native-dev/SKILL.md` and its
`references/`. It lives outside this repo and is never committed. Where it conflicts with this
file, this file wins: pinned versions (no BOM), single Gradle module, no flavors/`BuildConfig`,
no navigation library, no network/database/DI.

## 3. Build, install, verify

Use the project wrapper and Android Studio's bundled JDK:

```bash
cd /Users/ngochai/Desktop/NoteCraft
export JAVA_HOME="/Applications/Android Studio.app/Contents/jbr/Contents/Home"
./gradlew :app:assembleDebug
```

Clean offline build (works after dependencies are cached):

```bash
./gradlew clean :app:assembleDebug --offline
```

Notes:

- The first build that resolves Jetpack Ink needs Google Maven (`dl.google.com`). Maven Central may
  be blocked in this environment. Once cached, `--offline` works.
- `local.properties` is machine-specific and ignored. Do not edit it or commit it.
- The plugin mapping in `settings.gradle.kts` for the Compose compiler is intentional for Kotlin
  2.2.10. Do not "simplify" it unless the build is actually broken.
- Do not add extra repositories or change SDK/Gradle/AGP/Kotlin/Compose/Ink versions without
  explicit approval.

Install and smoke-test:

```bash
adb devices
adb install -r app/build/outputs/apk/debug/app-debug.apk
adb shell am start -n com.notecraft/.MainActivity
adb logcat -d -t 200 | grep -E "FATAL EXCEPTION|AndroidRuntime"
```

Available AVDs in this environment may include `Pixel_10_Pro_XL` and `Pixel_Tablet`. Enable
auto-rotate to test portrait and landscape; the app declares `fullUser` orientation.

Pure logic tests live under `app/src/test/java` (`GeometryTest.kt`, `PageReorderTest.kt`,
`ModelTest.kt`, `NoteOrganizationTest.kt`, `PageRangeTest.kt`, and `PaperInkTest.kt`). Run them with `./gradlew :app:testDebugUnitTest --offline`. Run a single class while
iterating with `./gradlew :app:testDebugUnitTest --tests "com.notecraft.util.GeometryTest" --offline`.
UI tests are not set up yet; do not add instrumentation or Robolectric dependencies without approval.
Do not claim runtime verification unless you ran the app on a device or emulator.

Build-failure triage order: read the full error (file + line) → check the most recent diff →
`./gradlew clean :app:assembleDebug --offline` → `./gradlew :app:dependencies` for conflicts →
rerun with `--stacktrace`.

- Treat warnings as signal. Do not introduce new compiler or lint warnings; fix warnings you create.
- If a build or test fails, investigate the failure itself. Do not delete, skip, or weaken a test to
  get green unless the test is provably wrong — and say so explicitly.

## 4. Architecture and state

```
MainActivity
└─ NoteCraftTheme
   └─ NoteCraftApp
      ├─ LibraryScreen
      └─ EditorScreen
```

- `NoteCraftViewModel` (`AndroidViewModel`) owns `library: LibraryState` and `editor: EditorState?`
  as Compose `mutableStateOf`. It is the single source of truth.
- There is no navigation library. `NoteCraftApp` switches between the library and editor on
  `viewModel.editor != null`.
- `EditorState` derives `page`, `color`, `width`, selection, and undo/redo flags. Do not duplicate
  this state in composables; hoist actions back to the ViewModel.
- Do not mutate `Note`, `Page`, stroke, or text lists in place. Models are immutable data classes;
  use `copy` and route mutations through ViewModel methods.
- Keep the ViewModel free of `Activity`, `View`, or Compose references. It holds `Application` only
  because `AndroidViewModel` is used for `NoteRepository` and `SharedPreferences`.
- Do not introduce Hilt, Koin, Room, Retrofit, Compose Navigation, multi-module architecture, or a
  repository abstraction "for later" unless the user explicitly asks.

## 5. Data model and persistence

- Logical page size is **1000 × 1414 page units** (`PAGE_WIDTH`, `PAGE_HEIGHT`). All stroke and
  text coordinates are stored in this space. Do not change it without a migration plan for existing
  JSON notes.
- `Stroke` requires at least one point. Pressure is stored per point (normally 0.15–1.0).
- `PrimaryTools`, `SecondaryTools`, and `PersistentTools` drive the editor UI. The laser is
  transient and must not be persisted; eraser strokes are not stored.
- Notes are JSON files at `filesDir/notes/<note-id>.json`, format `version = 1`. Pages contain
  strokes, text items, and sticky notes; a page may store `bookmarked`, and a note may store an
  optional `coverTone` name or `pinned` flag. Text items may store `width`, `alignment`, `bold`,
  `italic`, `underline`, `listStyle`, `lineSpacing`, and `highlightColor`; missing values mean a
  legacy auto-width, left-aligned, plain box. Sticky notes store position, size, text, color, and
  font size. The repository writes via a temporary file and atomic rename under `Dispatchers.IO`
  with a `Mutex`.
- Treat JSON field names and `SharedPreferences` keys as immutable on-disk identifiers. Never rename
  or repurpose one without a migration path and tolerant defaults; a rename silently resets notes or
  user preferences.
- Saving is debounced (550 ms) and flushed when the editor closes or the app reaches `ON_STOP`.
  Any new user-visible mutation must end in `syncLibrary` + `scheduleSave`, or the existing
  `commitPages`/`applyPages` path.
- Keep parsing tolerant and backward compatible: legacy `PEN` maps to `BALL_PEN`, unknown tools fall
  back to `BALL_PEN`, and malformed items are skipped instead of failing the whole note.
- Tool preferences (per-tool color/width) live in `SharedPreferences` `notecraft_tool_prefs`.
  Recently used inks (`recent_colors`) and the eraser mode (`eraser_mode`) live there too.
- App preferences (theme, default tool, default notebook template, default paper for new pages,
  autosave indicator, stylus-only mode) live in `SharedPreferences` `notecraft_settings`. The
  default template is stored under `default_note_template`; the value is a `NoteTemplate` name and
  missing values fall back to `BLANK`.
- Do not add network access, cloud sync, accounts, or a database without explicit approval.

## 6. Editor rendering and Jetpack Ink

`DrawingCanvas` owns the viewport (`zoom` 1–8, unbounded two-finger pan), gesture state machine,
preview stroke, lasso preview, and laser trail. `PageRenderer` draws the paper, background pattern,
strokes, text, and selection. Sticky notes are page objects rendered above ink and text; they are
included in thumbnails, PDF export, and page sharing.

Key rules:

- **Paper stays borderless.** `drawPaperSurface()` fills the viewport. In the vertical page stack,
  each page's background is drawn inside its own page bounds, separated by the page gap. Do not
  reintroduce the desk gradient, page drop shadow, rounded sheet, or page border.
- **Dark paper follows the app theme.** `NoteCraftTheme` provides `LocalDarkPaper`; paper, rules,
  grids, selection frames, thumbnails, and template previews pass it into `PageRenderer`. Dark ink
  and text are mapped at render time by `paperInkColor` (stored colors are never modified) and
  highlighter blending switches from multiply to screen. PDF export and PNG sharing intentionally
  pass `darkPaper = false` so shared documents stay light.
- Paper templates are BLANK, LINED, GRID, DOTTED, CORNELL, PLANNER, WEEKLY, MUSIC, STORYBOARD and
  CHECKLIST. The editor renders template structures inside the page bounds; the picker preview in
  `EditorDialogs.kt` must stay exhaustive when a template is added.
- Thumbnails intentionally use the page-bounded rendering path. Do not make the editor use that
  path or the paper will stop filling the screen.
- **Jetpack Ink tools:** fountain pen, ball pen, brush pen, highlighter (`InkToolTypes`).
  `createInkBrush` maps them to `StockBrushes`; brush size scales with the current transform.
- `InProgressStrokes` is a full-canvas overlay. Finished strokes are converted with
  `toNoteCraftStroke` using `(input - origin) / scale`. If you change the viewport transform, update
  both conversion and brush sizing together.
- If Ink brush initialization fails, the app falls back to the custom drawing path. Keep that
  fallback working.
- Ruler mode and stylus-only mode intentionally use the custom path (snapped points and pointer
  types must be controlled before authoring). Do not route them through Ink without solving
  snapping and finger filtering first.
- Pages are rendered as one vertical stack in `DrawingCanvas`. `pageIndex` follows the page at the
  viewport center; drawing gestures pass their target page index so any visible page is editable.
  Two-finger pan scrolls/zooms with a decay fling; there are no previous/next page buttons. A
  page-drawer selection uses `scrollRequest` to snap the stack to that page.
- Pencil, shapes, tape, laser, text, and lasso remain custom. Do not route them through Ink.
- Gesture handoff is deliberate: one finger draws (or erases/selects), two fingers zoom/pan. While
  Ink is active, the custom gesture loop waits without consuming single-pointer input, and a
  parent `Initial`-pass handler consumes multi-touch. Preserve this behavior when editing gestures.
- Preview strokes and lasso/laser previews are drawn from Compose state on every move. Avoid
  allocations or expensive work inside draw blocks.

## 7. Undo, redo, and autosave

- Undo/redo stores immutable page-list snapshots, capped at 80 entries, for the current session
  only.
- Discrete page changes (`addStroke`, page operations, text edits, background changes, and
  delete/duplicate selection) must go through `commitPages` so they are undoable.
- Selection state itself (selected IDs) is transient UI state and is not pushed to undo history.
- Continuous gestures use baselines: `beginErase`/`endErase`,
  `beginSelectionMove`/`endSelectionMove`, `beginTextResize`/`endTextResize`, and
  `beginStickyMove`/`endStickyMove`. Do not push an undo snapshot or sync/save per pointer event.
- Destructive edits should remain undoable whenever the existing model supports it. If a change is
  intentionally transient (laser, preview), keep it out of history and persistence.
- After history changes, `canUndo`/`canRedo` must be kept in sync.

## 8. UI, responsive layout, theme, cutouts

- Material 3 only. Theme and design tokens live in `ui/theme/Theme.kt` plus XML themes under
  `res/values` and `res/values-night`.
- Use `MaterialTheme.colorScheme` and the named tokens from `Theme.kt`. Paper, rule, grid, margin,
  and dot colors belong in `PageRenderer.kt`. Avoid new raw hex values unless you are adding a
  documented design token.
- Sizes in `dp`, text in `sp`. Keep touch targets at least ~48×48 dp. Use `maxLines` + ellipsis for
  variable text.
- Follow the skill's numeric UI checks by reference (`android-native-dev/SKILL.md` §7): 8dp spacing
  grid, 4.5:1 body-text / 3:1 large-text and component contrast, 100–500ms animation buckets.
  Touch targets, dark theme, and `contentDescription` on interactive elements are hard requirements,
  not nice-to-haves. Never put the element type in an accessibility label (e.g. "Save", not
  "Save button").
- Responsive breakpoints currently used:
  - tablet: `smallestScreenWidthDp >= 600`
  - compact height: `screenHeightDp < 480`
  - wide tool ribbon: `screenWidthDp >= 600`
- Landscape editor uses a compact icon-only ribbon (max 720 dp when wide) and a horizontal zoom
  dock in short landscape. The page drawer changes column count for tablets/landscape. Preserve
  these behaviors when touching the toolbar or drawer.
- The app is edge-to-edge. Paper intentionally extends behind system bars and display cutouts;
  interactive chrome uses `WindowInsets.safeDrawing` / `WindowInsets.displayCutout`. Do not pad the
  whole canvas or reintroduce letterboxing. The cutout mode is configured in `MainActivity`.
- Most UI copy is currently inline in composables. Keep new copy consistent with surrounding
  screens; for new shared or localization-sensitive strings, add them to
  `res/values/strings.xml` instead of duplicating literals.
- Material icons come from `material-icons-extended`. The adaptive launcher and round icons are
  already configured; do not replace branding without asking.
- Resource naming follows the skill (`ic_`/`img_`/`bg_` prefixes). Never use Android reserved names
  for resources, colors, or IDs (`background`, `icon`, `view`, `text`, `button`, `layout`, etc.);
  they cause build errors or resource conflicts.

## 9. Interaction rules

- Tapping a tool selects it and opens that tool's color/size/options dropdown. Tapping the same tool
  again or touching outside dismisses the options. Keep the collapsed ribbon compact.
- `PrimaryTools` are shown in the ribbon; `SecondaryTools` (shapes, tape, laser) live in the overflow
  menu, except the selection toolbar that appears when a lasso selection exists.
- Ink tools use pressure. Custom tools read the pressure captured on `Pt`.
- Laser is a fading transient trail and is never saved.
- Lasso supports select, move, resize, rotate, copy, cut, paste, duplicate, and delete. Move by
  dragging inside the selection bounds. Drag a corner handle to resize around the opposite corner;
  drag the round handle above the box to rotate around the selection center. Handle sizes and hit
  tolerances must stay in dp so they match at every zoom level. Rotating a mixed selection rotates
  strokes precisely and moves text boxes around the center while keeping the glyphs upright.
  The clipboard is app-session state in the ViewModel and survives closing a notebook; pasted items
  get new ids and a staggered offset, and a paste is one undo step.
- `New notebook` uses `AppSettings.defaultNoteTemplate`; **New from template** opens
  `NoteTemplateDialog` and creates a one-page notebook with that template's paper. `BLANK` keeps
  honoring `defaultPageBackground` for backward compatibility.
- Text tool: tap empty paper to add text; tap existing text to edit it; drag the bottom-right box
  handle to resize its wrapping width. Text alignment and formatting (bold, italic, underline,
  bullet/numbered lists, line spacing, highlight) live in the text dialog and apply to new and
  edited text.
- Sticky tool: tap empty paper to add a colored sticky note, tap a note to edit it, and drag it to
  move it. The eraser removes sticky notes whole. Sticky notes are not part of lasso selection.
  The tool dropdown adjusts card width and text size; long text grows the card at render time.
- Page-level export and sharing use the current page: export as PDF through the document picker or
  share as PNG through `PageSharer`. A page range can also be exported as PDF. Whole-notebook
  export remains the editor/library PDF action.
- The eraser has Partial and Object modes. Partial removes only the touched portion of a stroke and
  splits the remainder into pieces; Object removes whole strokes. Shapes and text are removed whole
  in either mode. Keep the `beginErase`/`endErase` undo baseline intact.
- Recently used inks are shown above the palette in each tool dropdown and persist in
  `notecraft_tool_prefs`.
- The ruler toggle lives in the canvas zoom dock. Strokes that start near the ruler edge snap to a
  straight line; the handle drags the ruler and slides sideways to rotate it.
- Stylus-only mode ignores finger drawing (and palm touches while a stylus is down) but still allows
  two-finger pan/zoom. It uses the custom renderer, not Ink.
- Page backgrounds are undoable.
- Notebook covers can be automatic (derived from the note id) or one of the named tones; persist
  the choice through `Note.coverTone`.
- Notebooks can be pinned from the library card menu and sort above unpinned notebooks for every
  sort mode. Persist the flag through `Note.pinned`; do not change `updatedAt` just to reorder.
- Folders are a single optional string and tags are a normalized list. Library search matches title,
  folder, and tags; the filter menu narrows by folder or tag. Organization changes persist without
  changing `updatedAt`.
- Page bookmarks persist through `Page.bookmarked` and are toggled in the page drawer or editor
  menu. Drag-and-drop reordering in the drawer uses `movePageTo`.
- PDF export uses the system document picker and rasterizes pages at 150 dpi. Do not change the
  rendering path without checking paper, templates, text, and exported page orientation.
- Do not change tool ordering, default widths/colors, or gesture semantics without a clear design
  reason.

## 10. Code style and safety

- Follow official Kotlin style and the existing file conventions (4-space indentation, trailing
  commas where used).
- Prefer small private composables and focused functions over very large composables.
- Use `rememberUpdatedState` for callbacks captured by long-lived gesture loops (existing pattern).
- Avoid `GlobalScope` and blocking IO on the main thread. Never swallow errors silently: an empty
  `catch`, unexplained `runCatching`, or suppressed error needs a one-line justification. Handle,
  log with enough context to reproduce, or propagate failures; keep the Ink fallback/logging intact.
- Never use `!!`; prefer safe calls with defaults (`user?.name ?: "Unknown"`) or `let` handling.
  In lambdas, use labeled returns (`return@forEach`), never a bare `return` that escapes the outer
  function. Avoid `lateinit` for state that may be read before init; prefer nullable or defaulted
  values.
- Threading: UI/state updates on `Dispatchers.Main`, file IO and repository work on
  `Dispatchers.IO`, heavy compute (parsing, sorting) on `Dispatchers.Default`. Suspend functions in
  the repository must be main-safe (`withContext(Dispatchers.IO)` inside). Never update Compose
  state from an IO thread.
- Logging levels: `Log.i` for key flow checkpoints, `Log.w` for recoverable/fallback situations,
  `Log.e` for failures — always with a TAG and enough context (what operation, what input).
- Compose rules (from the skill): only call `@Composable` from `@Composable` scope; side effects go
  in `LaunchedEffect`, never bare suspend calls in composition; use `derivedStateOf` for derived
  values; never construct a ViewModel inside a composable (use parameters/`remember`).
- User-facing failures should show a human-readable message, not a raw exception.
- Prefer multi-line Compose modifier chains and readable blocks. One-line code is fine only for
  simple lambdas, accessors, and single expressions; never compress multiple statements, modifier
  chains, or non-trivial closures onto one line.
- Do not introduce deprecated or soon-to-be-deprecated Android/Compose APIs. Prefer the current
  supported API and check the existing dependencies before reaching for a new one.
- Keep imports tidy and avoid unused code. Do not reformat or reorder unrelated code.
- Do not edit generated files, build outputs, `.gradle/`, `.idea/`, `.kotlin/`, `.DS_Store`, or
  `local.properties`.
- Touch only files named in the task or explicitly approved. Ask before deleting a file. If a task
  seems to require changes outside that scope, stop and ask rather than assuming it is in bounds.
- Never run destructive or irreversible commands without explicit approval: `git reset --hard`,
  `git clean -fd`, `git checkout -- .`, `git push --force`, `git branch -D`, `git stash drop`, or
  `git rebase` on pushed commits/main. Never run `sudo` or `rm -rf`.
- Do not add a dependency when the platform, Compose, or existing code already solves the problem.

## 11. Definition of done and verification

Before reporting a task complete, confirm all of the following:

- [ ] `./gradlew :app:assembleDebug` (or clean offline build) passes.
- [ ] `./gradlew :app:testDebugUnitTest --offline` passes for affected pure logic.
- [ ] No new compiler or lint warnings were introduced.
- [ ] If an emulator/device is available: install, launch, and check logcat for crashes.
- [ ] Exercise the affected flow:
  - library: create from the default template, create from template chooser, open, rename, search,
    sort, duplicate, delete, pin/unpin
  - drawing: fountain, ball, brush, pencil, marker, shapes, tape, laser, text, lasso
  - Ink wet stroke → dry NoteCraft stroke handoff
  - zoom in/out, pan, and drawing alignment at 100% and zoomed
  - partial and object eraser across a stroke, selection move/resize/rotate, copy/cut/paste,
    duplicate/delete, and undo/redo
  - page add/duplicate/delete/reorder, bookmark a page, every background template
  - text wrapping, alignment, formatting, lists, highlight, and corner resize
  - sticky note add/edit/move/erase
  - recently used colors in a tool dropdown
  - ruler straight-line drawing
  - current-page and page-range PDF export, plus PNG page sharing
  - folder/tag organization, library filtering, and search by tag/folder
  - stylus-only finger rejection on a device with a stylus (if available)
  - two-finger vertical page scrolling, page indicator, and page-drawer jump
  - cover-tone chooser and persistence after relaunch
  - settings: theme override, default tool, default notebook template, default paper, autosave
    indicator
  - dark paper: editor, library thumbnails, template previews, and light PDF/PNG exports
  - PDF export of a single-page and multi-page notebook, then open the PDF
  - close/reopen the editor and relaunch the app to verify autosave
  - phone portrait and landscape; tablet if available
  - display cutout simulation: `adb shell cmd overlay enable
    com.android.internal.display.cutout.emulation.tall`
- [ ] `README.md`, `DESIGN.md`, and this file are updated when user-visible behavior, persistence,
  setup, or a rule changes.
- [ ] No dependency was added without approval.
- [ ] No deprecated API was introduced (see section 10).
- [ ] No skill anti-patterns introduced: touch targets ≥ 48dp, 8dp-grid spacing, contrast ratios,
  dark-theme support, `contentDescription` on interactive elements.
- [ ] State clearly what was verified and what was not. Never claim a runtime check without a device
  or emulator.

If any box cannot be checked, say so explicitly rather than omitting or hiding it.

## 12. Known limitations and scope

These are intentional or currently unimplemented. Do not "fix" them opportunistically without
asking:

- The eraser removes stroke portions (Partial) or whole strokes (Object), not individual pixels;
  shapes and text are removed whole.
- Text boxes wrap at their stored width. Height is automatic and there is no vertical box
  constraint.
- Sticky notes are moved and edited directly with the Sticky tool; they are not part of lasso
  selection.
- PNG sharing handles the current page; PDF export supports the current page, a page range, or the whole notebook.
- Undo history is session-only.
- Notes are local only: no sync, cloud backup, import, or collaboration. PDF export and PNG page sharing are available.
- Page navigation is continuous vertical scrolling with two-finger pan; there are no previous/next
  buttons. The thumbnail drawer jumps directly to a page.
- Media insertion and collaboration are not implemented.
- Dry strokes use NoteCraft's renderer, so wet Ink and dry strokes can differ slightly.
- Ruler and stylus-only modes use NoteCraft's custom renderer instead of the Ink overlay.
- Pencil, shapes, tape, laser, text, sticky notes, and lasso remain custom implementations.

New features should be requested before being built. Prefer small, testable additions to the
existing model over speculative abstractions.

## 13. Git and documentation

- Use conventional commits, matching the existing history:
  - `feat(editor): ...`
  - `feat(ui): ...`
  - `fix(android): ...`
  - `docs: ...`
  - `chore: ...`
- Keep commits focused. One logical change per commit: keep refactors behavior-identical and
  separate from feature work; do not bundle unrelated fixes, formatting, and features in one diff.
- Do not commit or push unless the user asks.
- Never commit `local.properties`, `.idea/`, build outputs, APKs/AABs, `.gradle/`, or `.DS_Store`.
- The `android-native-dev` skill lives outside this repo and is never committed. Nothing in the
  build may depend on it being present.
- Update `README.md` and `DESIGN.md` when behavior, persistence, canvas rules, orientation, or
  cutout behavior changes.
- Update this file deliberately when a rule is wrong or repeatedly causes trouble.
- Do not rewrite or amend commits after they are shared.

## 14. Ask before changing

Ask the user before:

- changing SDK, Gradle, AGP, Kotlin, Compose, or Jetpack Ink versions
- changing `PAGE_WIDTH`/`PAGE_HEIGHT` or the JSON persistence schema
- adding a network, database, DI, navigation, or multi-module dependency
- moving or renaming files across `model`, `data`, `ui`, or `util`
- large refactors, mass formatting, or dependency upgrades
- changing rotation, edge-to-edge, or display-cutout behavior
- changing the app icon, branding, or design tokens
- adding tests/CI that require new tooling or external services

When something is ambiguous, state the options and recommend the smallest change that fits the
existing architecture.

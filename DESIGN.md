# DESIGN SYSTEM SPECIFICATION: GoodNotes-Inspired Mobile Note-Taking App

## 1. Executive Summary & Brand Identity
* **Product Archetype**: Tactile, modern digital stationery and vector note-taking ecosystem.
* **Core Value Proposition**: The seamless blend of tactile paper stationery aesthetic with high-precision digital productivity tools (stylus-first, document management, template flexibility).
* **Brand Personality**: Scholarly, focused, tactile, refined, clean, unobtrusive. The interface recedes into the background so the user's handwritten thoughts, sketches, and notes take center stage.
* **Target Device**: Android phones and tablets, portrait and landscape, with edge-to-edge paper and display-cutout-safe chrome.

---

## 2. Color Palette & Theming Tokens

### Primary Brand & Accent
* **Ink Blue (Primary)**: `#2563EB` (Selected tools, primary actions, active tabs)
* **Ink Blue Deep (Primary Active)**: `#1D4ED8`
* **Focus Azure (Primary Container / Hover)**: `#EFF6FF`
* **Highlight Yellow (Secondary / Highlighter)**: `#FEF08A` (Semantics: highlighting, bookmarks)
* **Highlighter Accent (Vibrant Amber)**: `#F59E0B`

### Surface & Background Tokens (Tactile Paper Foundation)
* **Window / Chrome Background**: `#F3F4F6` (Gray-100; sits behind Compose surfaces — the editor paper itself is borderless)
* **Paper Surface Default**: `#FCFCFA` (Warm off-white, reduces eye strain vs pure `#FFFFFF`)
* **Paper Surface Ivory**: `#FBF8F1` (Classic notebook tint)
* **Paper Surface Dark / Slate (Dark Mode)**: `#1B2432` → `#141B26` vertical gradient
* **Dark Paper Rule / Grid**: `#3A4A5F` (margin `#9F3B3B`, dots `#66788F`)
* **Toolbar / Chrome Surface**: `#FFFFFF` (Material 3 surface with `#E5E7EB` outline and elevation; no heavy blur)
* **Divider & Border Rules**: `#E5E7EB` (Subtle boundary definitions)
* **Paper Line / Grid Color**: `#E2E8F0` (Rule lines and graph grid; dots use `#94A3B8` at 45% opacity)

### Typography & Ink Hues
* **Ink Black (Text Primary)**: `#0F172A` (Rich slate black, high legibility)
* **Ink Slate (Text Secondary)**: `#475569` (Metadata, dates, tool captions)
* **Ink Muted (Text Tertiary / Placeholder)**: `#94A3B8` (Search hints, disabled states)

### Functional & Semantic Colors
* **Destructive / Eraser Red**: `#EF4444` (Delete, clear page, red ink preset)
* **Mint Green (Success / Verified)**: `#10B981` (Saved indicator, green ink preset)
* **Tag Purple**: `#8B5CF6`
* **Notebook Cover Tones**:
  * Navy Leather: `#1E3A8A`
  * Terracotta: `#C2410C`
  * Forest Sage: `#15803D`
  * Pastel Lavender: `#DDD6FE`
  * Charcoal Minimal: `#334155`

---

## 3. Typography & Hierarchy System

* **Primary Sans Font**: Platform sans-serif (`FontFamily.SansSerif`, Roboto on Android)
* **Notebook Cover Labels**: System sans-serif with bold, uppercase label styling; no bundled handwriting font

### Scale
* **Display Large (Notebook Title / Library Heading)**: `24sp` | SemiBold (600) | Line-height: `1.25`
* **Title / Modal Header**: `18sp` | SemiBold (600) | Line-height: `1.3`
* **Body Regular (List titles, tool popovers)**: `15sp` | Regular (400) | Line-height: `1.4`
* **Body Medium (Active button, tool preset)**: `14sp` | Medium (500) | Line-height: `1.4`
* **Caption / Tooltips / Meta Labels**: `12sp` | Medium (500) | Tracking: `+0.2sp`
* **Micro Labels (Page numbers, zoom %)**: `10sp` | Bold (700) | Uppercase

---

## 4. Spacing, Elevation & Shapes

### Spacing Scale
* `4dp` (xs): Spacing between tool icons, pill padding
* `8dp` (sm): Tool ribbon gap, badge padding
* `12dp` (md): Document card inner padding
* `16dp` (lg): Screen edge gutter, floating tool margin
* `24dp` (xl): Section separation

### Border Radius & Shapes
* **Tool Icons & Pills**: `8dp` to `12dp` (Soft squircle)
* **Floating Toolbars**: `16dp` to `20dp` (Pill/capsule floating dock)
* **Notebook Covers**: `10dp` (with a subtle right-edge spine crease)
* **Modals & Bottom Sheets**: `24dp` (Top corners rounded)

### Shadows & Depth (Elevation)
* **Level 1 (Notebook in Library)**: 6–10 dp surface shadow plus 2 dp label badges
* **Level 2 (Active Tool Floating Ribbon)**: 10–12 dp surface shadow
* **Level 3 (Modal Sheet / Popover Palette)**: Material 3 dialog/popover elevation
* **Book Spine Gradient**: `linear-gradient(to right, rgba(0,0,0,0.15) 0%, rgba(0,0,0,0.02) 6%, transparent 12%)`

---

## 5. Signature UI Components

### 1. Floating Pen & Tool Ribbon
* **Placement**: Top pinned bar (or floating top ribbon) with a Material 3 surface, `#E5E7EB` outline, and elevation.
* **Tool Order**:
  1. Pen tools (Fountain, Ballpoint, Brush)
  2. Pencil (textured graphite)
  3. Highlighter / Marker (semi-transparent overlay)
  4. Precision Eraser (Partial vs Object toggle)
  5. Lasso Selection Tool
  6. Text Box (`T`) with wrapping, alignment, bold/italic/underline, lists, line spacing, highlight, and a corner resize handle
  7. Sticky Note with a colored card, wrapped text, direct drag-to-move and tap-to-edit
  * The overflow menu holds Shapes (line, arrow, rectangle, ellipse), Tape, and Laser.
* **Contextual Sub-bar**: Recently used inks, the 12-color preset palette plus a custom HSV picker, a per-tool width slider with three quick presets, and tool-specific controls. Undo and redo live in the editor top bar.
* **Selection Bar**: Appears when a lasso selection exists or the clipboard holds items. Shows the selected-item count and scrollable Copy, Cut, Paste, Duplicate, Delete and Clear actions. Dragging a corner handle resizes the selection; dragging the handle above the box rotates it.

### 2. Digital Notebook Card (Documents Home)
* Realistic ratio (`3:4`) notebook preview with spine binding, leather/matte finish, ribbon bookmark, label badge, and edited timestamp.
* Quick-action context menu (`...`) for pin/unpin, rename, tags/folder, cover tone, PDF export, duplicate and delete.
* Pinned notebooks show a pin badge and stay above unpinned notebooks for every sort mode.
* Folders and tags appear on the card and can be used as library filters; search covers titles, folders, and tags.
* Cover-tone chooser with automatic, navy leather, terracotta, forest sage, pastel lavender and charcoal options.

### 3. Canvas Paper
* Authentic simulated paper texture with customizable guidelines:
  * Ruled / Lined (8mm spacing)
  * Dotted Grid (5mm spacing)
  * Squared Graph
  * Cornell Notes
  * Daily planner
  * Weekly planner
  * Music staff
  * Storyboard
  * Checklist
  * Blank Paper
* The paper fills the entire editor viewport edge-to-edge; there is no desk backdrop, page border, or drop shadow.
* In dark mode the paper becomes a deep slate gradient with muted rules, and dark ink/text is lifted toward warm white at render time. Stored ink colors never change; PDF and PNG exports stay on light paper.
* Guidelines and paper tint pan and zoom with the content; the tint fills the viewport, while each page's guideline pattern is drawn inside that page's bounds.
* Drawing is accepted anywhere on the visible paper, including beyond the logical page bounds.
* A straight-edge ruler can be toggled on from the zoom controls. Its handle moves and rotates the guide, and strokes that start near the edge snap to a perfectly straight line.
* Pages are arranged in a continuous vertical stack with a subtle divider between sheets. Two-finger drag scrolls with inertia; a passive page indicator shows the current page, and the thumbnail drawer jumps directly to a page. Previous/next arrow controls are intentionally removed.
* Paper is allowed to extend behind display cutouts and system bars, while toolbars, navigation, zoom controls, and sheets inset themselves using `WindowInsets.safeDrawing` / `WindowInsets.displayCutout`.
* The eraser removes only the touched portion of a stroke; shapes and text are removed whole, and each erase gesture is one undo step.

### 4. Page Thumbnail & Bookmarks
* Adaptive micro-page grid: 3 columns on phones in portrait, 4 in landscape, and 3–6 on tablets.
* A quick add-page tile appends a page; each thumbnail has a bookmark star toggle, and long-press drag reorders with a lift/shadow indicator.
* The star uses Highlighter Amber for bookmarked pages and a muted outline otherwise.

### 5. Settings Screen
* A dedicated full screen opened from the library or editor, never a dialog.
* Rows use the same icon tile, title, description and trailing-control rhythm as the rest of the app.
* Appearance: system, light or dark theme.
* Editor: default tool, default notebook template, default paper for new pages, autosave indicator toggle.
* New notebooks use the default template; **New from template** in the library opens a preview chooser with one row per template.
* Input: stylus-only mode ignores finger drawing and palm touches while two-finger pan and zoom remain available.
* Storage and offline status are described in a read-only section.

---

## 6. Interaction & Micro-Motion Principles
* **Tool Switching**: Instant with crisp scale bounce (`scale(0.92)` on tap down, returning to `scale(1)`).
* **Palette Expansion**: Smooth accordion drop with slight spring curve (`cubic-bezier(0.16, 1, 0.3, 1)`).
* **Page Scrolling**: Continuous vertical movement through stacked pages with two-finger drag; no arrow buttons or page-turn animation.
* **Undo / Redo**: Haptic feedback on use; the top-bar icons reflect availability and mirror the ViewModel's history state.
* **PDF Export**: Uses the system document picker, writes A4 pages at 150 dpi, and keeps the existing paper, template and ink rendering. The current page can be exported alone or shared as a PNG through the system share sheet.

# DESIGN SYSTEM SPECIFICATION: GoodNotes-Inspired Mobile Note-Taking App

## 1. Executive Summary & Brand Identity
* **Product Archetype**: Tactile, modern digital stationery and vector note-taking ecosystem.
* **Core Value Proposition**: The seamless blend of tactile paper stationery aesthetic with high-precision digital productivity tools (stylus-first, document management, template flexibility).
* **Brand Personality**: Scholarly, focused, tactile, refined, clean, unobtrusive. The interface recedes into the background so the user's handwritten thoughts, sketches, and notes take center stage.
* **Target Device**: Mobile / Tablet-first responsive layout (Optimized for iOS/iPadOS tactile standards, ~390px mobile view with tablet scaling).

---

## 2. Color Palette & Theming Tokens

### Primary Brand & Accent
* **Ink Blue (Primary)**: `#2563EB` (Selected tools, primary actions, active tabs)
* **Ink Blue Deep (Primary Active)**: `#1D4ED8`
* **Focus Azure (Primary Container / Hover)**: `#EFF6FF`
* **Highlight Yellow (Secondary / Highlighter)**: `#FEF08A` (Semantics: highlighting, bookmarks)
* **Highlighter Accent (Vibrant Amber)**: `#F59E0B`

### Surface & Background Tokens (Tactile Paper Foundation)
* **Canvas Neutral / Desk Backdrop**: `#F3F4F6` (Gray-100 — realistic desk background)
* **Paper Surface Default**: `#FCFCFA` (Warm off-white, reduces eye strain vs pure `#FFFFFF`)
* **Paper Surface Ivory**: `#FBF8F1` (Classic notebook tint)
* **Paper Surface Dark / Slate (Dark Mode)**: `#1E293B`
* **Toolbar / Chrome Surface**: `#FFFFFF` (Glass-morphism blur `backdrop-blur-md` with `rgba(255, 255, 255, 0.88)`)
* **Divider & Border Rules**: `#E5E7EB` (Subtle boundary definitions)
* **Paper Line / Grid Color**: `#E2E8F0` (Rule lines, dot grid at 30% opacity)

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

* **Primary Sans Font**: `-apple-system, BlinkMacSystemFont, "SF Pro Text", "SF Pro Display", "Inter", sans-serif`
* **Tactile Handwriting / Title Font (Optional Covers)**: `"Caveat", "Architects Daughter", cursive`

### Scale
* **Display Large (Notebook Title / Library Heading)**: `24px` | SemiBold (600) | Line-height: `1.25`
* **Title / Modal Header**: `18px` | SemiBold (600) | Line-height: `1.3`
* **Body Regular (List titles, tool popovers)**: `15px` | Regular (400) | Line-height: `1.4`
* **Body Medium (Active button, tool preset)**: `14px` | Medium (500) | Line-height: `1.4`
* **Caption / Tooltips / Meta Labels**: `12px` | Medium (500) | Tracking: `+0.2px`
* **Micro Labels (Page numbers, zoom %)**: `10px` | Bold (700) | Uppercase

---

## 4. Spacing, Elevation & Shapes

### Spacing Scale
* `4px` (xs): Spacing between tool icons, pill padding
* `8px` (sm): Tool ribbon gap, badge padding
* `12px` (md): Document card inner padding
* `16px` (lg): Screen edge gutter, floating tool margin
* `24px` (xl): Section separation

### Border Radius & Shapes
* **Tool Icons & Pills**: `8px` to `12px` (Soft squircle)
* **Floating Toolbars**: `16px` to `20px` (Pill/capsule floating dock)
* **Notebook Covers**: `10px` (with right-edge `2px` subtle spine crease simulation)
* **Modals & Bottom Sheets**: `24px` (Top corners rounded)

### Shadows & Depth (Elevation)
* **Level 1 (Notebook in Library)**: `0 4px 6px -1px rgba(0, 0, 0, 0.07), 0 2px 4px -2px rgba(0, 0, 0, 0.05)`
* **Level 2 (Active Tool Floating Ribbon)**: `0 10px 25px -3px rgba(0, 0, 0, 0.1), 0 4px 6px -4px rgba(0, 0, 0, 0.05)`
* **Level 3 (Modal Sheet / Popover Palette)**: `0 20px 25px -5px rgba(0, 0, 0, 0.15), 0 8px 10px -6px rgba(0, 0, 0, 0.08)`
* **Book Spine Gradient**: `linear-gradient(to right, rgba(0,0,0,0.15) 0%, rgba(0,0,0,0.02) 6%, transparent 12%)`

---

## 5. Signature UI Components

### 1. Floating Pen & Tool Ribbon
* **Placement**: Top pinned bar (or floating top ribbon) with blurred white surface (`backdrop-blur-md`).
* **Tool Order**:
  1. Pen Tool (Fountain, Ballpoint, Brush)
  2. Highlighter (Semi-transparent overlay preview)
  3. Precision Eraser (Stroke vs Area toggle)
  4. Lasso Selection Tool
  5. Stickers / Elements Picker
  6. Text Box (`T`)
  7. Media / Camera Insert
* **Contextual Sub-bar**: Color dots (3 quick-swap presets) + Stroke width selectors (0.3mm, 0.7mm, 1.5mm) + Undo/Redo controls.

### 2. Digital Notebook Card (Documents Home)
* Realistic ratio (`3:4`) notebook preview with spine binding, leather/matte finish, ribbon bookmark, label badge, and edited timestamp.
* Quick-action context menu (`...`) for export, duplicate, trash.

### 3. Canvas Paper Sheet
* Authentic simulated paper texture with customizable guidelines:
  * Ruled / Lined (8mm spacing)
  * Dotted Grid (5mm spacing)
  * Squared Graph
  * Cornell Notes
  * Blank Paper
* Subtle paper drop shadow against desk gray background to provide realistic page boundaries.

### 4. Page Thumbnail & Bookmarks Drawer
* 2-column or 3-column micro-page grid.
* Quick add page before/after, bookmark star toggle, and drag-and-drop reordering indicator.

---

## 6. Interaction & Micro-Motion Principles
* **Tool Switching**: Instant with crisp scale bounce (`scale(0.92)` on tap down, returning to `scale(1)`).
* **Palette Expansion**: Smooth accordion drop with slight spring curve (`cubic-bezier(0.16, 1, 0.3, 1)`).
* **Page Turn / Swipe**: Fluid horizontal swipe transition simulating page leafing.
* **Undo / Redo Haptic Feedback**: Visual pulse indicator on toolbar state change.

# Warlock Game screen - design spec

**Audience:** the designer working on Warlock's desktop UI.
**Goal:** describe what the Game screen does, every region and element on it, how each is used, and
the states it can show, so it can be redesigned without reading the code. This documents current
behavior; treat the visual treatment (layout, spacing, type, color, iconography) as open for you to
define.

> Platform note: this covers the **desktop** app (Mac, Windows, Linux). The mobile app has a
> simpler, single-pane version of the same screen and is not the subject of this document.

> Domain note: Warlock is a client for Simutronics text MUDs (GemStone IV, DragonRealms, and
> similar). The player reads a stream of game text and types commands. A short glossary of game
> terms is at the end (section 9).

---

## 1. What the Game screen is

The Game screen is what the user sees while connected to and playing a game. It is the application's
core working surface and where the user spends almost all of their time. Its job is to:

1. **Show game text** as it streams in, split across one main window and any number of secondary
   windows (combat, thoughts, room description, deaths, logons, and so on).
2. **Take commands** the user types and send them to the game.
3. **Surface live character state** at a glance: health and other vitals, what each hand holds,
   status effects (poisoned, stunned, hidden, kneeling), available room exits, and action cooldown
   timers.

The screen is reached after a successful login and is left by disconnecting or returning to the
Dashboard.

---

## 2. Entry and exit

- **Shown when:** a login completes (from the Dashboard or the new-connection wizard).
- **Leaves to:**
  - the **Dashboard** (user chooses to go back, or clicks "Go to dashboard" after a disconnect),
  - stays in place but shows a **disconnected banner** when the server drops the connection
    (see section 7).

---

## 3. Overall layout

The whole screen is a vertical stack: an optional alert banner at the top, a large central work
area that fills all remaining space, and a fixed control bar pinned to the bottom.

```
+========================================================================+
|  (optional) DISCONNECTED BANNER                                        |  section 7
+------------------------------------------------------------------------+
|        |  TOP docked windows (resizable height)                |       |
|  SIDE  | ----------------------------------------------------- | SIDE  |
|  BAR   |                                                       | DOCK  |
| (window|                  MAIN WINDOW                          |  L /  |
|  list, |              (primary game text)                      |  R    |
| toggle)|                                                       | docks |
|  240dp | ----------------------------------------------------- |(resiz-|
|        |  BOTTOM docked windows (resizable height)             | able  |
|        |                                                       | width)|
+------------------------------------------------------------------------+
|  BOTTOM CONTROL BAR                                                     |  section 6
|  [ command entry + roundtime overlay ........................ ] [ind][C]|
|  [ vital bars ............................................... ] [ica][o]|
|  [ left hand ] [ right hand ] [ spell ]                        [tor][m] |
+========================================================================+
```

Three columns make up the central work area, left to right:

1. **Sidebar** (optional, 240dp wide): the window list. Toggled from the app chrome.
2. **Docking grid** (fills remaining width): TOP dock, MAIN window, BOTTOM dock stacked vertically,
   with LEFT and RIGHT docks flanking them.
3. (LEFT and RIGHT docks are part of the grid, drawn on the outer edges of column 2.)

The bottom control bar spans the full width below all of this.

---

## 4. The window system (the heart of the screen)

Game text does not arrive as one blob. The server tags each piece of text with a named **stream**
(for example `main`, `thoughts`, `death`, `combat`, `room`, `logons`). Warlock shows each stream in
its own **window**. The user arranges these windows around the screen.

### 4.1 Window locations

Every window lives in one of five **locations**:

| Location | Where it sits | Resizable | Can be moved/hidden? |
|----------|---------------|-----------|----------------------|
| `MAIN`   | center, fills all leftover space | grows to fill | No. Always present, cannot be hidden, dragged, or closed. |
| `TOP`    | row(s) above MAIN | height | Yes |
| `BOTTOM` | row(s) below MAIN | height | Yes |
| `LEFT`   | column left of the center stack | width | Yes |
| `RIGHT`  | column right of the center stack | width | Yes |

The MAIN window is the anchor. Everything else is optional and user-arranged. A location can hold
more than one window (they stack/share the space). Per-location size (top height, bottom height,
left width, right width) and per-window size are remembered between sessions.

### 4.2 Anatomy of a single window

Each window is a rounded rectangle (2dp corners) with a hairline border and a panel background.
It has two parts:

**Header bar** (top of the window):
- A **drag handle** icon on the left (shown for every window except MAIN). Dragging it re-arranges
  windows: drop onto another location to move the window there, or drop within a location to reorder.
  The cursor changes to a move cursor over the handle; the header tints on hover.
- The **title** (and an optional subtitle appended to it), single line, truncates with an ellipsis.
- A **selected** state: the currently selected window's header is highlighted (filled with the
  border color). Only the selected window receives keyboard scroll commands (see 4.4).
- A **right-click context menu** on the header (and on the text body) with: **Window settings...**,
  **Clear window**, and **Hide window** (the last is omitted for MAIN).

**Body** (fills the rest of the window). One of two kinds:

- **Text stream window** (the common case): a vertically scrolling log of game text. Details in 4.3.
- **Dialog window**: a structured panel of UI objects (progress bars and similar) rather than free
  text. Used for game-provided custom panels. Rendered with the window's background color.

### 4.3 Text stream window behavior

This is where most reading happens, so its behaviors matter:

- **Auto-scroll ("sticky to bottom"):** new lines push the view to the newest line automatically.
  If the user scrolls up to read history, auto-scroll pauses; when they scroll back to the bottom,
  it resumes. (No visual "jump to latest" affordance exists today; this is a candidate for design.)
- **Selectable text:** the user can select and copy text. The right-click menu adds the window
  actions noted above beneath the standard copy options.
- **Per-line highlight:** individual lines can carry their own background color (used by the user's
  highlight rules and by the game), drawn as a full-width band behind the line.
- **Clickable text / action links:** some text is interactive. Clicking it can send a command
  directly, or open an **action context menu** at the click point. That menu is a single popup that
  **drills in and out** of nested categories (a back row returns to the previous level) rather than
  opening cascading submenus. Categories show a `>` affordance; the back row shows a left arrow.
- **Inline images:** a stream can include images. They render at a default height (80dp) and
  **expand to full size on hover**, then collapse again.
- **Background image:** a window can have a background image with several fit modes (fill, fit
  width, fit height, full size, and a gradient-masked mode), nine-way alignment, and an opacity
  setting. Text renders on top.
- **Prompts:** the game's input prompt line is de-duplicated (consecutive prompts collapse to one).
- **Timestamps:** windows can optionally show timestamps on lines (per-window setting).

### 4.4 Selecting and scrolling windows

- Clicking or focusing a window **selects** it. The selection drives which window responds to
  scroll macros: page up / page down, line up / line down, and jump to start / end of buffer. These
  come from keyboard macros, not on-screen buttons.

### 4.5 The window list (sidebar)

A 240dp-wide scrollable panel on the far left, shown when the sidebar is toggled on (from the app's
window chrome, outside this screen). It lists **all known windows** sorted by title. Each row has:

- a visibility icon (an open eye when the window is currently shown, a crossed-out eye when hidden),
- the window's title.

Clicking a row toggles that window open or closed. This is how the user brings back a hidden window
or hides one they do not want. The sidebar uses the default text/background colors so it visually
matches the game theme.

### 4.6 Per-window settings dialog

Opened from the header/body context menu ("Window settings..."). Lets the user override, for that
window: **text color**, **background color**, **font family**, **font size**, **font weight**, and a
**name-filter** toggle (when the window supports filtering by speaker/name). Overrides merge on top
of the global default style.

---

## 5. Styling, themes, and skins (what is themeable)

- **Default style:** a single "default" style (text color, background color, font family/size/
  weight) drives the entire game area, including the sidebar, bottom bar, and any window that has no
  override. This is the main lever for the overall look.
- **Entry style:** the command input box has its own style ("entry"), merged over the default.
- **Per-window overrides:** see 4.6. These merge over the default.
- **Native chrome (Jewel theme):** panel backgrounds, borders, and standard text colors come from
  the platform theme (the app uses a native-feeling toolkit). Light vs dark is detected from the
  background's luminance, and some elements adapt (for example the roundtime bar colors).
- **Skins:** graphical elements like the **compass** are drawn from a skin (a sprite sheet defined
  in a JSON skin file; the default is a port of the classic StormFront look). Skins are a real
  extension point if you want to restyle the graphical widgets.

---

## 6. The bottom control bar (always visible)

A full-width bar pinned below the work area. Left side is a stacked column (takes all flexible
width); right side holds the indicators and the compass.

### 6.1 Command entry (top of the left column)

- A single-line text input. This is the primary way the user acts in the game. Pressing **Enter**
  sends the command.
- **Always-focused behavior:** typing anywhere on the screen (any plain character, no modifier)
  routes focus to this box so the user can just start typing. Keys bound to macros are intercepted
  first.
- **Roundtime / casttime overlay:** drawn inside the entry box, on top of the field:
  - a row of **red segments** growing along the **bottom** edge = **roundtime** remaining (the
    cooldown after most actions),
  - a row of **blue segments** along the **top** edge = **cast time** remaining (spell preparation),
  - a numeric countdown (blue cast number, red roundtime number) anchored at the right.
  Each segment is roughly one second; the bars shrink as the timers tick down. When both are zero,
  the overlay is empty and the field looks like a plain input.
- Right-click menu adds a "Settings" item (opens the entry style dialog).

### 6.2 Vital bars (middle of the left column)

- A thin (16dp tall) full-width row of **progress bars** showing the character's vitals: health,
  mana, stamina, spirit, and similar pools the game reports. This is a game-provided dialog panel
  ("minivitals"), so the exact bars depend on the game.
- These are **display only**; they are not clickable.

### 6.3 Hands (bottom of the left column)

Three equal-width boxes in a row, each a rounded box with an icon and a text value:

- **Left hand** (hand icon, rotated/mirrored): what the character holds in the left hand.
- **Right hand** (hand icon): what the character holds in the right hand.
- **Spell** (wand icon): the spell the character currently has prepared.

Empty when nothing is held / prepared.

### 6.4 Status indicators (right side)

A row of **five grouped square icon slots**, each showing the currently active status in its group.
Sizes scale with the window width (clamped between 24 and 60dp). The groups and their states:

1. **Posture:** kneeling, prone, sitting, standing.
2. **Group:** joined (grouped with other players).
3. **Health condition:** bleeding (red cross), dead (skull).
4. **Concealment:** invisible, hidden, webbed.
5. **Stun:** stunned.
6. **Affliction** (shares slot space with posture group): poisoned, diseased.

Only active states draw an icon; inactive slots are empty bordered boxes. These are status readouts,
not buttons.

### 6.5 Compass (far right)

- An 88dp-tall graphic of a compass rose, drawn from the active skin's sprite sheet.
- **Available exits are lit**; unavailable directions are dark. Directions include the eight
  cardinals/diagonals plus up, down, and out.
- **Clicking a lit direction sends that movement command.** Overlapping hit areas resolve to the
  smallest target, so the small directional arrows take priority over the large up/down halves.

---

## 7. States the screen can be in

- **Connected and playing** (the normal state described above).
- **Disconnected:** when the server drops the connection, a banner appears at the top of the screen
  (a box with a thick yellow border) reading "You have been disconnected from the server", with:
  - a **Reconnect** button (only when reconnect is possible for that connection), and
  - a **Go to dashboard** button.
  The rest of the screen stays visible (the user can still read and scroll back through the text).
- **Macro error:** if a user macro fails, a modal alert dialog ("Macro error") shows the message
  with an OK button.

---

## 8. Interaction summary (quick reference)

| User does | Result |
|-----------|--------|
| Types a character anywhere | Focus jumps to the command entry; the character is entered |
| Presses Enter in entry | Command is sent to the game |
| Clicks a window | That window becomes selected (receives scroll macros) |
| Drags a window header | Moves/reorders the window across docks (MAIN excluded) |
| Drags a dock edge | Resizes that dock (top/bottom height, left/right width) |
| Clicks an eye row in sidebar | Shows/hides that window |
| Right-clicks a window | Window settings / Clear / Hide menu |
| Clicks interactive game text | Sends a command or opens a drill-down action menu |
| Hovers an inline image | Image expands to full size |
| Clicks a lit compass direction | Sends that movement command |
| Scroll macros (keyboard) | Scroll the selected window by line/page/buffer |

---

## 9. Glossary (game terms)

- **Stream / window:** a named channel of game text (main, thoughts, combat, death, room, logons,
  and so on). The server says which stream each line belongs to; Warlock routes it to the matching
  window.
- **Roundtime (RT):** a cooldown, in seconds, imposed after most actions before the character can
  act again. Shown as the red bar in the entry box.
- **Cast time:** seconds to prepare a spell. Shown as the blue bar in the entry box.
- **Vitals:** the character's resource pools (health, mana, stamina, spirit). Shown as the vital
  bars.
- **Hands:** what the character is holding (left hand, right hand) plus the currently prepared
  **spell**.
- **Indicators / status:** transient conditions (poisoned, stunned, hidden, kneeling, bleeding,
  dead, and so on).
- **Compass / exits:** the directions the character can currently move from the present room.
- **Macro:** a user-defined keyboard shortcut that sends commands or scrolls windows.
- **Highlight:** a user rule that recolors matching text (drives the per-line background bands).

---

## 10. Where this lives in the code (for reference, not required reading)

- Screen container: `DesktopGameView.kt`
- Window grid (the five docks): `DesktopGameTextWindows.kt`
- A single window (header + text/dialog body, images, menus): `DesktopWindowView.kt`,
  `WindowHeader.jvm.kt`
- Bottom bar: `DesktopGameBottomBar.kt`, entry + roundtime: `DesktopWarlockEntry.kt`,
  hands: `DesktopHandsView.kt`, indicators: `DesktopIndicatorView.kt`, compass: `CompassView.kt`
- Window model: `core/.../window/WindowInfo.kt` (locations and types), `WindowUiState.kt`
- State source: `GameViewModel.kt`

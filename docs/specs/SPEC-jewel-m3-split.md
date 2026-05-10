# SPEC: Migrate Warlock3 UI to Jewel (Desktop) + Material3 (Mobile)

## Goal

Replace Material3 on the desktop target with Jewel (Int UI standalone). Keep Material3 on mobile, **shared between Android and iOS**. Mobile and desktop screens are written independently — no unified component abstraction across the desktop/mobile boundary.

## Non-Goals

- No shared UI abstraction layer between desktop and mobile (no `expect` Button/TextField/Theme spanning the two).
- No Cupertino-styled iOS UI (see "Mobile: Shared Material3" below for rationale).
- No visual redesign of mobile screens.
- No change to non-UI logic: parsing, scripting, networking, persistence, Sentry wiring, the exception handler chain, `SafeClipboard`, `SafeUriHandler`, `JavaProxy.kt`, Ktor TLS handler — all stay as-is.

## Approach

Hard split at the desktop/mobile boundary. `commonMain` keeps state holders, ViewModels/state flows, parsing, scripting engines, models, and any pure-Compose utilities that don't reference Material3 or Jewel (Foundation primitives like `LazyColumn`, `SelectionContainer`, `Canvas`, `AnnotatedString` are fine in `commonMain`).

A new intermediate source set, `mobileMain`, holds the Material3-based screens and theme — shared by `androidMain` and `iosMain`. `desktopMain` (jvm) gets Jewel and has its own independently-written screens.

Screens are duplicated **once** (desktop vs mobile), not twice. Acceptable because desktop and mobile UX for a MUD client diverge anyway (multi-pane vs single-pane, command-line-focused vs touch-focused), but Android and iOS UX are close enough that splitting them buys nothing.

## Source Set Structure

```
:frontend (or whatever the UI module is named)
  commonMain/
    state/         # ViewModels, state flows, models — no UI
    util/          # AnnotatedString helpers, regex utils
    foundation/    # screens/widgets that ONLY use compose.foundation
  mobileMain/      # NEW intermediate source set (parent: commonMain)
    ui/            # M3-based screens shared by Android + iOS
    theme/         # M3 ColorScheme, Typography
  androidMain/     # parent: mobileMain
    # platform glue only: activity, manifest-driven entry, Android-specific resources
  iosMain/         # parent: mobileMain
    # platform glue only: ComposeUIViewController entry, iOS-specific resources
  desktopMain/     # jvmMain — independent of mobileMain
    ui/            # NEW: Jewel-based screens
    theme/         # Jewel theme config + warlock palette overlay
    shim/          # thin Jewel wrapper composables (see below)
```

The `mobileMain` intermediate source set requires hierarchical source set configuration in the KMP block. Both `androidMain` and `iosMain` (and any iOS arch-specific sets like `iosX64Main`, `iosArm64Main`, `iosSimulatorArm64Main`) must declare `mobileMain` as their parent.

Audit current `commonMain` for Material3 imports. Anything importing `androidx.compose.material3.*` must move to `mobileMain` or be rewritten against Foundation.

## Dependencies

Add to the desktop source set only. Pin to a single IJP version line — start with the current latest stable (e.g., `-251` or whatever ships at migration time; check Maven Central for `org.jetbrains.jewel`).

```kotlin
// libs.versions.toml
[versions]
jewel = "<latest stable>"
jewel-ijp = "251"  # or current

[libraries]
jewel-standalone        = { module = "org.jetbrains.jewel:jewel-int-ui-standalone-${jewel-ijp}",        version.ref = "jewel" }
jewel-decorated-window  = { module = "org.jetbrains.jewel:jewel-int-ui-decorated-window-${jewel-ijp}",  version.ref = "jewel" }
jewel-foundation        = { module = "org.jetbrains.jewel:jewel-foundation-${jewel-ijp}",               version.ref = "jewel" }
jewel-ui                = { module = "org.jetbrains.jewel:jewel-ui-${jewel-ijp}",                       version.ref = "jewel" }
intellij-icons          = { module = "com.jetbrains.intellij.platform:icons", version = "<matching ijp>" }
```

Add the Jewel Maven repo in `settings.gradle.kts`:

```kotlin
repositories {
    maven("https://packages.jetbrains.team/maven/p/kpm/public/")
    // existing repos
}
```

**Compose alignment:** Jewel pins a specific Compose Multiplatform version. Verify your CMP version matches Jewel's expected version before merging — mismatches manifest as runtime crashes in the compiler-generated code, not build errors. Document the chosen pair in the root README.

**Icons:** standalone Jewel does not ship IJP icons. Either depend on `com.jetbrains.intellij.platform:icons` or vendor only the icons we use into resources.

## Mobile: Shared Material3 (Android + iOS)

Both Android and iOS targets share a single Material3 implementation in `mobileMain`. Rationale:

- Warlock3 on iOS is currently a "it builds and runs" target, not a polished native app. Investing in Cupertino styling has no payoff until iOS has real users asking for native feel.
- The `compose-cupertino` ecosystem is fragmented — the original repo (alexzhirkevich) is unmaintained and the active work is split across community forks (slanos, schott12521, robinpcrd) with no clear canonical choice. Picking one is a maintenance liability for a side project.
- M3 on iOS via Compose Multiplatform works fine; it just doesn't look native. For a MUD client UX, that's acceptable.
- Sharing the mobile UI code between Android and iOS means one place to fix bugs and add features, instead of duplicating screens across two mobile targets.

If iOS later grows real users and there's demand for native feel, the shape of the future migration is well-defined: introduce `cupertino-adaptive` in `mobileMain`, replace M3 component call sites with adaptive equivalents incrementally. The current spec deliberately does not block that future move — it just doesn't do it now.

## Theme Layer

Don't fight Jewel's theme system. Use `IntUiTheme` (light + dark variants) at the root. For Warlock-specific colors (game text colors, prompt colors, presence indicators), define a separate `WarlockColors` object provided via `CompositionLocal` and read by widgets that need it. Don't try to shoehorn game colors into Jewel's `GlobalColors`.

```kotlin
val LocalWarlockColors = staticCompositionLocalOf<WarlockColors> { error(...) }

@Composable
fun WarlockDesktopTheme(content: @Composable () -> Unit) {
    val isDark = /* from settings */
    IntUiTheme(theme = if (isDark) IntUiThemes.Dark else IntUiThemes.Light) {
        CompositionLocalProvider(LocalWarlockColors provides WarlockColors.from(isDark)) {
            content()
        }
    }
}
```

## Shim / Wrapper Layer

To bound the blast radius of Jewel API churn, every Jewel component used outside trivial cases gets a Warlock-prefixed wrapper in `desktopMain/shim/`:

```
shim/
  WarlockButton.kt       // wraps DefaultButton / OutlinedButton
  WarlockTextField.kt    // wraps Jewel TextField with our state model
  WarlockCheckbox.kt
  WarlockTabs.kt
  WarlockScrollbar.kt
  WarlockSplitLayout.kt
  WarlockTree.kt         // wraps LazyTree
```

Screens import from `shim/`, never directly from `org.jetbrains.jewel.ui.component.*`. When Jewel breaks an API, only the shim files change.

Rule: if a Jewel component is used in exactly one place and is unlikely to drift, skip the shim. Don't over-abstract.

## Component Mapping

| Current (M3 desktop) | New (Jewel)                         | Notes                                                              |
| -------------------- | ----------------------------------- | ------------------------------------------------------------------ |
| `MaterialTheme`      | `IntUiTheme` + `WarlockDesktopTheme`| Wraps both.                                                        |
| `Button`             | `DefaultButton`                     | Primary action.                                                    |
| `OutlinedButton`     | `OutlinedButton`                    | Different package.                                                 |
| `TextField`          | Jewel `TextField`                   | State API differs — wrap in shim.                                  |
| `OutlinedTextField`  | Jewel `TextField` (outlined style)  | Jewel doesn't distinguish; styling controls border.                |
| `Checkbox`           | `Checkbox`                          | Drop-in via shim.                                                  |
| `Switch`             | No direct equivalent                | Use `Checkbox` or build a small custom toggle.                     |
| `RadioButton`        | `RadioButton`                       | Drop-in.                                                           |
| `DropdownMenu`       | `PopupMenu` / `ListComboBox`        | Different API; wrap.                                               |
| `Scaffold`           | None                                | Use a `Column`/`Row` with `DecoratedWindow` content.               |
| `TopAppBar`          | `DecoratedWindow` title bar         | Custom title bar via `decorated-window` artifact.                  |
| `NavigationBar`/Rail | None                                | Use Jewel's tab strip or a side panel pattern.                     |
| `BottomSheet`        | None                                | Not used on desktop anyway — drop or rebuild.                      |
| `Snackbar`           | None                                | Build a small custom toast or use a status-bar style line.         |
| Material `Scrollbar` | Jewel scrollbar in `ScrollableContainer` | Better behavior; replaces current scrollbar workarounds.       |
| Material `Divider`   | `Divider`                           | Drop-in.                                                           |
| `LazyColumn`         | `LazyColumn` (Foundation)           | No change.                                                         |
| `Text`               | Jewel `Text`                        | Picks up theme typography automatically.                           |
| `Icon`               | `Icon` with `AllIconsKeys`          | Bring icons via dep or vendor own.                                 |
| Custom resizable pane| `HorizontalSplitLayout` / `VerticalSplitLayout` | Replace any hand-rolled splitter.                      |
| Tree views (if any)  | `LazyTree`                          | Use for variable/character/script browsers.                        |

## Migration Order

Do this incrementally on a branch, not big-bang. Both toolkits coexist at runtime fine — Jewel is desktop-only and doesn't conflict with M3 on Android.

1. **Plumbing.** Add deps, repo, theme root, empty shim files. Wrap the desktop entry point's content in `IntUiTheme` + `DecoratedWindow`. M3 still in use everywhere — ship-able state.
2. **Mobile source set restructure.** Introduce `mobileMain` as parent of `androidMain` and `iosMain`. Move existing M3 screens and theme out of `androidMain` (or wherever they live) into `mobileMain`. iOS picks them up automatically. No visual change on either mobile target. Independent of any desktop work; can land before, during, or after Jewel migration steps.
3. **Settings/preferences screens.** Self-contained, low risk, exercises Button, TextField, Checkbox, ComboBox. Catches API surprises early.
4. **Connection/profile selection screens.** Similar shape, low traffic.
5. **Main game window chrome.** Title bar (decorated window), menu bar, status bar. Not the text panes yet.
6. **Side panels** (variables, scripts, presence, character info). Trees and lists.
7. **Splitters / pane layout.** Replace hand-rolled splitter with `SplitLayout`.
8. **Main text pane.** Mostly Foundation already (`LazyColumn` + `SelectionContainer` + `AnnotatedString`); only the surrounding chrome changes. Verify the existing `SelectionContainer`/`LazyColumn` crash workaround still applies and the chained exception handler is still wired.
9. **Command line input.** TextField rewrite — biggest single API porting task. Validate history, completion, multi-line behavior.
10. **Dialogs / popups.** Replace M3 `AlertDialog` with Jewel's dialog patterns.
11. **Cleanup.** Remove Material3 from desktop source set's deps. Verify no `androidx.compose.material3` imports remain in `desktopMain`.

Each step is a separate PR. Step 1 is the only one that requires special care to keep both running side-by-side; after that, screens are converted one at a time.

## Decorated Window

Replace the standard CfD `Window { }` with `DecoratedWindow { }` from `jewel-int-ui-decorated-window`. Provides the IntelliJ-style title bar, native window controls, and proper drag handling. Verify on all three desktop targets (Windows, macOS, Linux). On Linux specifically, JBR provides better behavior than stock OpenJDK — confirm runtime expectations match what we ship.

## Risk Mitigations

- **API churn:** Shim layer (above). Pin Jewel version; bump deliberately, not via dependabot auto-merge.
- **Compose version drift:** Document the Jewel ↔ CMP ↔ Kotlin version triple in the root README. Don't bump any of the three in isolation.
- **AGP 9 / CMP 1.10 blocker:** Confirm Jewel's required CMP version is reachable from our current AGP-pinned setup before starting. If Jewel requires CMP 1.10+ and we're stuck pre-AGP-9, this spec stalls.
- **Icon licensing:** Using `com.jetbrains.intellij.platform:icons` brings in a lot of icons; verify the licensing fits a non-IDE app. Fallback is vendoring just the handful we use.
- **Android Studio coexistence:** Jewel docs warn AS leaks an older Jewel/CfD on its classpath. Not relevant for our build (we don't run inside an IDE plugin), but worth a note in CONTRIBUTING.

## Open Questions

1. Do we want `DecoratedWindow` (custom title bar) or stock OS chrome? Custom looks more cohesive but is one more surface that can break per-OS.
2. Keep the existing dark/light toggle mechanism, or adopt Jewel's `IntUiThemes` enum directly?
3. Single Jewel IJP line (e.g., always `-251`), or track latest? Recommend single pinned line, bumped quarterly.
4. Mobile: stay on current M3 version, or bump M3 in lockstep with the desktop CMP bump? Recommend lockstep to avoid two CMP versions in the build.
5. Any third-party Compose libs in current use that pull Material3 transitively? Audit needed before step 10.

## Acceptance

- All desktop screens render under Jewel; no `androidx.compose.material3` imports in `desktopMain`.
- M3 screens and theme live in `mobileMain`; `androidMain` and `iosMain` contain only platform glue.
- Android build unchanged in appearance and dependency footprint.
- iOS build unchanged in appearance and dependency footprint; same screens render on both mobile targets without per-platform branching in `mobileMain`.
- All existing crash workarounds (`SafeClipboard`, `SafeUriHandler`, chained Sentry exception handler, Ktor TLS handler, `JavaProxy.kt` arg parsing) verified still wired and functional.
- Manual smoke test: connect to DR and GS, send commands, scroll history, select+copy text, open settings, switch theme, resize panes, close window cleanly. Mobile smoke test on at least one Android device and one iOS simulator.
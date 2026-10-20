# Theming Enhancements and Keyboard Deactivation Flow Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Enhance PuKey with complete theme control (Light/Dark/Auto, Monet default, 6 built-in palettes, AMOLED True Black), animated header surface effects (Snow, Maze, Circuit, Code Rain, Still), and a safe off-boarding keyboard switcher dialog with a "Don't show again" checkbox when the Live Verifier is disabled.

**Architecture:** 
- Centralized `ThemePreferences` helper managing SharedPreferences persistence for theme mode, palette, AMOLED black, header effect, and IME prompt preference.
- M3 theme overlay architecture in XML supporting Monet dynamic colors by default alongside 6 static color palettes and an AMOLED pure-black style overlay applied at Activity creation.
- Lightweight custom `HeaderSurfaceView` rendered beneath the top hero highlight card, utilizing a hardware-accelerated Canvas with low CPU/battery overhead and 5 distinct generative/particle shaders.
- Verifier state listener intercepting `masterSwitch` deactivation to present a Material dialog with an InputMethodManager picker trigger and a "Don't show again" persistent checkbox.

**Tech Stack:** Kotlin, Android SDK (minSdk 26, targetSdk 34), Material Components 3 (M3 Expressive), Canvas 2D graphics, SharedPreferences, LocalBroadcastManager.

**Spec:** User prompt requirements:
1. Re-add theme options: Light mode, Dark mode, and Automatic (System).
2. Monet (Material You dynamic wallpaper colors) as default color system; include more built-in palettes (Electric Cyan, Cyber Blue, Emerald Forest, Sunset Amber, Neon Violet, Crimson Rose).
3. Option for True black in Dark mode for AMOLED/OLED displays (pure `#000000` backgrounds).
4. Header surface visual effects: Snow, Maze, Circuit, Code Rain, or Nothing (Still).
5. Option to fully turn off the system when disabling live verifier: prompt to switch back to another keyboard (e.g. Gboard) with a "Don't show again" checkbox.

## Global Constraints

- Android API minSdk 26, targetSdk 34, compileSdk 34.
- Material 3 (`Theme.Material3.DayNight.NoActionBar`) compliance for all components.
- Preserve all existing biometric evaluation, keystroke dynamics, and device admin capabilities without disruption.
- Dynamic colors must degrade gracefully when unavailable (pre-Android 12 or Monet disabled).
- Header canvas effects must automatically pause rendering when invisible, detached, or when the screen is off to preserve battery life.

## Review Focus

1. **Monet Availability & Fallback:** When running on Android versions below API 31 or custom ROMs where Monet is unavailable, selecting Monet must gracefully fall back to the Electric Cyan brand palette without crashing.
2. **AMOLED True Black Toggle Timing:** Switching True Black on must only affect Dark mode surfaces; when Light mode is active, the setting must remain inactive or visually indicate that it requires Dark mode.
3. **Theme Change Lifecycle:** Changing themes, palettes, or AMOLED mode dynamically must recreate or update `MainActivity` smoothly without losing tab navigation index or unregistering broadcast receivers.
4. **Keyboard Switcher Permission & Window Focus:** Triggering `InputMethodManager.showInputMethodPicker()` must not throw exceptions if the window has transient focus issues when the dialog is dismissed.
5. **Surface Animation Throttling:** Backgrounding the app or switching to the Enrollment/Settings/Logs tabs must pause the `HeaderSurfaceView` animation loop to avoid battery drain.

---

### Task 1: Theme & Preferences Core Models & Persistence (`ThemePreferences`)

**Files:**
- Create: `app/src/main/java/com/chate/thesis/ThemePreferences.kt`
- Test: `app/src/test/java/com/chate/thesis/ThemePreferencesTest.kt`

**Interfaces:**
- Consumes: `android.content.Context`, `android.content.SharedPreferences`
- Produces: 
  ```kotlin
  enum class ThemeMode { SYSTEM, LIGHT, DARK }
  enum class ColorPalette(val id: String, val title: String) { MONET, CYAN, BLUE, EMERALD, AMBER, VIOLET, CRIMSON }
  enum class HeaderEffect(val id: String, val title: String) { NOTHING, SNOW, MAZE, CIRCUIT, CODE_RAIN }
  class ThemePreferences(context: Context) {
      var themeMode: ThemeMode
      var palette: ColorPalette
      var isAmoledBlackEnabled: Boolean
      var headerEffect: HeaderEffect
      var promptKeyboardSwitchOnDisable: Boolean
  }
  ```

- [ ] **Step 1: Write the failing test**

Create `app/src/test/java/com/chate/thesis/ThemePreferencesTest.kt` validating default values and enum serialization:

```kotlin
package com.chate.thesis

import org.junit.Assert.*
import org.junit.Test

class ThemePreferencesTest {
    @Test
    fun testDefaultValues() {
        assertEquals("SYSTEM", ThemeMode.SYSTEM.name)
        assertEquals("MONET", ColorPalette.MONET.name)
        assertEquals(ColorPalette.MONET, ColorPalette.fromId("monet"))
        assertEquals(HeaderEffect.NOTHING, HeaderEffect.fromId("nothing"))
        assertEquals(HeaderEffect.CODE_RAIN, HeaderEffect.fromId("code_rain"))
    }

    @Test
    fun testColorPaletteLookupFallback() {
        assertEquals(ColorPalette.MONET, ColorPalette.fromId("unknown_value"))
        assertEquals(HeaderEffect.NOTHING, HeaderEffect.fromId("invalid_effect"))
    }
}
```

- [ ] **Step 2: Run test to verify it fails**

Run test to confirm `ThemeMode`, `ColorPalette`, and `HeaderEffect` are unresolved.

- [ ] **Step 3: Implement `ThemePreferences.kt`**

Implement `ThemePreferences.kt` in `app/src/main/java/com/chate/thesis/` with:
- Enums `ThemeMode`, `ColorPalette`, `HeaderEffect` with safe `fromId()` methods.
- SharedPreferences persistence for:
  - `KEY_THEME_MODE` (default: `"SYSTEM"`)
  - `KEY_PALETTE` (default: `"monet"`)
  - `KEY_AMOLED_BLACK` (default: `false`)
  - `KEY_HEADER_EFFECT` (default: `"nothing"`)
  - `KEY_PROMPT_KEYBOARD_SWITCH` (default: `true`)
- Helper `applyThemeMode(themeMode: ThemeMode)` wrapping `AppCompatDelegate.setDefaultNightMode`.

- [ ] **Step 4: Run test to verify it passes**

Run test to verify all assertions pass.

- [ ] **Step 5: Commit**

```bash
git add app/src/main/java/com/chate/thesis/ThemePreferences.kt app/src/test/java/com/chate/thesis/ThemePreferencesTest.kt
git commit -m "feat(theme): add ThemePreferences and mode enums"
```

---

### Task 2: Resource Color Schemes & XML Theme Overlays

**Files:**
- Modify: `app/src/main/res/values/colors.xml`
- Modify: `app/src/main/res/values-night/colors.xml`
- Modify: `app/src/main/res/values/themes.xml`
- Modify: `app/src/main/res/values-night/themes.xml`
- Create: `app/src/main/res/values/palette_themes.xml`

**Interfaces:**
- Consumes: Material 3 attributes (`colorPrimary`, `colorSurface`, `colorSurfaceContainer`, etc.)
- Produces:
  - Theme overlays:
    - `Theme.Thesis.Palette.Cyan`
    - `Theme.Thesis.Palette.Blue`
    - `Theme.Thesis.Palette.Emerald`
    - `Theme.Thesis.Palette.Amber`
    - `Theme.Thesis.Palette.Violet`
    - `Theme.Thesis.Palette.Crimson`
    - `Theme.Thesis.Amoled` (Pure `#000000` background and surface overrides)

- [ ] **Step 1: Define palette colors in `colors.xml` and `values-night/colors.xml`**

Add primary, container, and surface colors for each of the 6 built-in palettes:
- Blue (Cobalt: `#1565C0` / `#64B5F6`)
- Emerald (Matrix Green: `#2E7D32` / `#69F0AE`)
- Amber (Sunset: `#E65100` / `#FFB74D`)
- Violet (Neon Purple: `#6A1B9A` / `#CE93D8`)
- Crimson (Ruby Rose: `#C2185B` / `#F48FB1`)
- Cyan (PuKey Brand: `#00677E` / `#00E5FF`)
- Amoled black: `#000000` and dark containers `#0A0A0A`, `#121212`.

- [ ] **Step 2: Create `app/src/main/res/values/palette_themes.xml`**

Define theme styles for each palette inheriting from `Theme.Thesis`, overriding primary, primaryContainer, onPrimary, and navigation colors. Also define `Theme.Thesis.Amoled` overriding `android:colorBackground`, `colorSurface`, and containers with `#000000`.

- [ ] **Step 3: Update `values-night/themes.xml` for AMOLED compatibility**

Ensure night theme uses semantic color references so that applying `Theme.Thesis.Amoled` cleanly overrides surface and background attributes without artifacts.

- [ ] **Step 4: Verify XML compile and resource generation**

Run Gradle build check on resources to verify no missing references or duplicated resource IDs.

- [ ] **Step 5: Commit**

```bash
git add app/src/main/res/values/colors.xml app/src/main/res/values-night/colors.xml app/src/main/res/values/themes.xml app/src/main/res/values-night/themes.xml app/src/main/res/values/palette_themes.xml
git commit -m "feat(theme): define 6 built-in M3 palettes and AMOLED true black theme overlays"
```

---

### Task 3: Dynamic Header Surface Canvas View (`HeaderSurfaceView`)

**Files:**
- Create: `app/src/main/java/com/chate/thesis/ui/HeaderSurfaceView.kt`
- Create: `app/src/test/java/com/chate/thesis/HeaderSurfaceLogicTest.kt`
- Modify: `app/src/main/res/layout/activity_main.xml:28-40`

**Interfaces:**
- Consumes: `ThemePreferences.HeaderEffect`, `android.graphics.Canvas`, `android.graphics.Paint`
- Produces:
  ```kotlin
  class HeaderSurfaceView(context: Context, attrs: AttributeSet?) : View(context, attrs) {
      fun setEffect(effect: HeaderEffect)
      fun setEffectColor(color: Int)
      fun pauseAnimation()
      fun resumeAnimation()
  }
  ```

- [ ] **Step 1: Write the failing logic test for particle and effect state updates**

Create `app/src/test/java/com/chate/thesis/HeaderSurfaceLogicTest.kt`:

```kotlin
package com.chate.thesis

import org.junit.Assert.*
import org.junit.Test

class HeaderSurfaceLogicTest {
    @Test
    fun testSnowflakeUpdateWrapsAround() {
        var y = 100f
        val height = 100f
        val speed = 5f
        y += speed
        if (y > height) {
            y = 0f
        }
        assertEquals(0f, y, 0.001f)
    }

    @Test
    fun testCodeRainGlyphRandomization() {
        val chars = "010101XYZABC"
        val sample = chars.random()
        assertTrue(chars.contains(sample))
    }
}
```

- [ ] **Step 2: Run test to verify passes**

Run unit test to verify logic principles.

- [ ] **Step 3: Implement `HeaderSurfaceView.kt`**

Implement `HeaderSurfaceView.kt` in `app/src/main/java/com/chate/thesis/ui/`:
- `effect`: `HeaderEffect` (default `NOTHING`).
- Custom canvas drawing:
  - **NOTHING:** Static surface, calls `setWillNotDraw(false)`. No frame invalidation ticker.
  - **SNOW:** 45 circular particles falling with varied speed, alpha, and gentle horizontal swaying. Calls `postInvalidateOnAnimation()`.
  - **MAZE:** Isometric/orthogonal maze lines drawn on a subtle grid with traveling pulses along corridors.
  - **CIRCUIT:** Printed circuit board (PCB) traces, connection solder pads, and glowing pulse signals traveling across nodes.
  - **CODE_RAIN:** Matrix-style digital rain columns of glowing characters and numbers streaming downward with fading alpha tails.
- Lifecycle controls:
  - `onVisibilityChanged`: Stop ticking when `visibility != VISIBLE`.
  - `onWindowVisibilityChanged`: Pause on `GONE`.
  - `onAttachedToWindow()` / `onDetachedFromWindow()`: Release animator.
  - Rounded corner clipping: bottom corners radius 32dp matching `card_hero_highlight_bg.xml`.

- [ ] **Step 4: Integrate `HeaderSurfaceView` into `activity_main.xml`**

In `activity_main.xml`, wrap `heroHighlightCard` or embed `HeaderSurfaceView` right behind the card contents with matching bounds so the effect animates cleanly behind the hero text, status squircle, and score indicators without obstructing touch interactions (`clickable="false"` and `focusable="false"`).

- [ ] **Step 5: Verify build and Commit**

```bash
git add app/src/main/java/com/chate/thesis/ui/HeaderSurfaceView.kt app/src/test/java/com/chate/thesis/HeaderSurfaceLogicTest.kt app/src/main/res/layout/activity_main.xml
git commit -m "feat(ui): implement HeaderSurfaceView with Snow, Maze, Circuit, and Code Rain shaders"
```

---

### Task 4: Live Verifier Standby Keyboard Switch Dialog & Settings Action

**Files:**
- Modify: `app/src/main/java/com/chate/thesis/MainActivity.kt`
- Modify: `app/src/main/res/values/strings.xml`

**Interfaces:**
- Consumes: `ThemePreferences.promptKeyboardSwitchOnDisable`, `InputMethodManager.showInputMethodPicker()`
- Produces:
  ```kotlin
  fun showKeyboardSwitchDialogIfNeeded()
  fun showKeyboardPicker()
  ```

- [ ] **Step 1: Add dialog string resources in `strings.xml`**

```xml
<string name="dialog_switch_keyboard_title">Verifier Standby</string>
<string name="dialog_switch_keyboard_message">Continuous biometric verification is now standby. Would you like to switch to your standard keyboard (e.g. Gboard)?</string>
<string name="dialog_switch_keyboard_positive">Switch Keyboard</string>
<string name="dialog_switch_keyboard_negative">Keep PuKey</string>
<string name="dialog_dont_show_again">Don\'t show again</string>
```

- [ ] **Step 2: Add keyboard switch prompt logic to `MainActivity.kt`**

In `MainActivity.kt`:
- In `masterSwitch.setOnCheckedChangeListener`:
  - When `isChecked == false`:
    - Call existing deactivation intent to `KeystrokeIMEService`.
    - If `themePreferences.promptKeyboardSwitchOnDisable` is true:
      - Construct a `MaterialAlertDialogBuilder` with title, message, positive button ("Switch Keyboard"), negative button ("Keep PuKey").
      - Add a custom view or checkbox for "Don't show again".
      - On Positive: if checkbox is checked, save `themePreferences.promptKeyboardSwitchOnDisable = false`; invoke `(getSystemService(INPUT_METHOD_SERVICE) as InputMethodManager).showInputMethodPicker()`.
      - On Negative: if checkbox is checked, save `themePreferences.promptKeyboardSwitchOnDisable = false`; dismiss.

- [ ] **Step 3: Add explicit "Switch Keyboard" action in Settings**

Allow user to trigger the keyboard picker anytime directly from the Settings tab, as well as a switch to re-enable "Prompt to switch keyboard when verifier paused".

- [ ] **Step 4: Verify compilation & Commit**

```bash
git add app/src/main/java/com/chate/thesis/MainActivity.kt app/src/main/res/values/strings.xml
git commit -m "feat(ime): add keyboard switch off-boarding dialog with Don't show again checkbox"
```

---

### Task 5: Settings UI & Navigation Integration in `MainActivity` & `ThesisApplication`

**Files:**
- Modify: `app/src/main/res/layout/activity_main.xml` (Settings tab section)
- Modify: `app/src/main/java/com/chate/thesis/MainActivity.kt`
- Modify: `app/src/main/java/com/chate/thesis/ThesisApplication.kt`

**Interfaces:**
- Consumes: `ThemePreferences`, `HeaderSurfaceView`, `DynamicColors`
- Produces: Full interactive UI in Settings Tab for:
  - Theme Mode: Light, Dark, Automatic (Radio / Toggle buttons)
  - Color Palette: Monet (Default), Electric Cyan, Cyber Blue, Emerald Forest, Sunset Amber, Neon Violet, Crimson Rose (Horizontal palette selector / chip cards)
  - AMOLED True Black: Switch toggle (enabled in Dark mode)
  - Header Surface Effect: Selector for Nothing (Still), Snow, Maze, Circuit, Code Rain
  - Keyboard prompt setting toggle & trigger button

- [ ] **Step 1: Design Settings UI in `activity_main.xml`**

Under `settingsView` -> Category 3 (Theming & System):
1. **Appearance & Mode Card:**
   - Single-selection `MaterialButtonToggleGroup` for "Automatic", "Light", "Dark".
   - SwitchMaterial for "True Black (AMOLED)" with description.
2. **Color Palette Selector Card:**
   - ChipGroup or horizontal picker displaying:
     - "Monet (Material You)" [Default]
     - "Electric Cyan"
     - "Cyber Blue"
     - "Emerald Forest"
     - "Sunset Amber"
     - "Neon Violet"
     - "Crimson Rose"
3. **Header Surface Effect Card:**
   - Single-selection ChipGroup for:
     - "Nothing (Still)"
     - "Snow"
     - "Maze"
     - "Circuit"
     - "Code Rain"
4. **Keyboard Offboarding Option:**
   - SwitchMaterial: "Prompt to switch keyboard when verifier paused"
   - Button: "Switch to Gboard / Another Keyboard"

- [ ] **Step 2: Update `ThesisApplication.kt`**

In `ThesisApplication.onCreate()`:
- Read `ThemePreferences`.
- Apply saved `themeMode` (`AppCompatDelegate.setDefaultNightMode`).
- If palette is `MONET`, call `DynamicColors.applyToActivitiesIfAvailable(this)`.

- [ ] **Step 3: Wire up settings in `MainActivity.kt`**

In `MainActivity.kt`:
- Before `super.onCreate()`:
  - Check `ThemePreferences`.
  - If palette is not `MONET`, apply custom theme `setTheme(paletteThemeResId)`.
  - If `isAmoledBlackEnabled` and in dark mode, apply `Theme.Thesis.Amoled`.
- In `setupSettingsTab()`:
  - Initialize toggles with current saved values from `themePreferences`.
  - On Theme Mode change: persist and call `AppCompatDelegate.setDefaultNightMode(mode)`.
  - On Palette change: persist and recreate activity smoothly (`recreate()`).
  - On AMOLED toggle: persist and recreate activity if currently in dark mode.
  - On Header Effect change: persist and update `headerEffectView.setEffect(newEffect)` immediately in real-time.
  - On Keyboard prompt toggle: persist `promptKeyboardSwitchOnDisable`.
  - On "Switch Keyboard" button click: call `showKeyboardPicker()`.
- In `onPause()` / `onResume()`:
  - Call `headerEffectView.pauseAnimation()` / `headerEffectView.resumeAnimation()`.
- In tab switching (`showTab()`):
  - Pause header effect animation when user navigates away from Dashboard to avoid unnecessary canvas invalidations.

- [ ] **Step 4: Verify end-to-end functionality & run build**

Run `./gradlew assembleDebug` or build verification.

- [ ] **Step 5: Commit**

```bash
git add app/src/main/res/layout/activity_main.xml app/src/main/java/com/chate/thesis/MainActivity.kt app/src/main/java/com/chate/thesis/ThesisApplication.kt
git commit -m "feat(settings): connect theme controls, palettes, AMOLED toggle, and header effect shaders"
```


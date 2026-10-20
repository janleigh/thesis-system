# Rework Security Controls, Master Switch, and Streamline UI Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Streamline the home and enrollment interfaces by removing quick telemetry, transforming the Live Verifier card in Security Controls into the primary master switch for system power (on/off), removing the duplicate switch from the sandbox, and eliminating all WPM and accuracy metrics from the Enrollment screens.

**Architecture:**
- Modernize the Security Controls UI in `activity_main.xml` to feature a full-width Master Switch card (`btnGridVerifier`) embedding `SwitchMaterial` (`masterSwitch`) directly alongside the biometric shield icon and status label (`gridVerifierStatusText`), paired with an Enforcer Policy card (`btnGridPolicy`).
- Clean up `sandboxContainerCard` by removing the secondary/duplicate `masterSwitch`, converting the sandbox header to an uncluttered title and helper badge.
- Purge the quick telemetry row (`telemetryScoreStat`, `telemetryKeystrokeStat`, `telemetryVectorStat`) from the Home tab.
- Purge all typing speed and accuracy metrics (`monkeytypeWpmText`, `monkeytypeAccText`, `enrollCompleteWpmText`, `enrollCompleteAccText`) from both Step 1 (typing challenge) and Step 2 (enrollment complete) in the Enrollment tab.
- Validate layout integrity and view IDs through unit tests executing with `./gradlew test`.

**Tech Stack:** Android SDK (API 26-34), Kotlin, Android XML Layouts, Material Design 3 (`SwitchMaterial`, `MaterialCardView`, `MaterialButton`), JUnit 4.

**Spec:**
1. Remove the quick telemetry row from the Home screen.
2. Rework the Security Controls section: Make the Live Verifier switch into the master switch to turn the system on and off directly within Security Controls.
3. Remove the redundant switch from the Verification sandbox card.
4. Remove WPM and Acc totally from the Enrollment flow (both in the active typing challenge and on the completion screen).

## Global Constraints

- Preserve all existing core functionality: continuous keystroke verification broadcasts (`KeystrokeIMEService.ACTION_SET_MODE`), profile enrollment verification checks, device admin navigation, and keyboard off-boarding switch prompt.
- Maintain existing view IDs (`masterSwitch`, `btnGridVerifier`, `gridVerifierStatusText`, `btnGridPolicy`, `gridPolicyStatusText`) so Activity logic and state restorations remain unbroken.
- Conform strictly to Material Design 3 styling, theme attributes (`?attr/colorSurfaceContainer`, `?attr/colorOnSurface`, `?attr/colorPrimary`), and theme palette support.
- All unit tests must pass synchronously with `./gradlew test`.

## Review Focus

1. **Card Click & Switch State Synchronization:** Clicking anywhere on the Live Verifier card container must toggle the embedded `masterSwitch` without triggering duplicate conflicting broadcast events or click focus loops.
2. **Profile Readiness Guard:** If a user toggles the master switch when their biometric profile is not yet enrolled, the switch must reject the activation, revert to unchecked, and display the enrollment prompt toast.
3. **Sandbox Independence:** Removing the switch from `sandboxContainerCard` must not disrupt typing verification in `testInput` or the "Clear Input" action button.
4. **Layout Responsiveness on Narrow Screens:** The reworked Security Controls cards must render cleanly across all device widths (down to 320dp width) without text truncation or clipping of the master switch.
5. **Zero Dangling View References:** Ensure no references to deleted view IDs (`telemetryScoreStat`, `monkeytypeWpmText`, etc.) remain in code or cause null pointer exceptions during activity lifecycle events.

---

### Task 1: Add Layout & Structural Contract Unit Test

**Files:**
- Create: `app/src/test/java/com/chate/thesis/SecurityControlsLayoutContractTest.kt`

**Interfaces:**
- Consumes: `activity_main.xml` via standard classpath resource or file reader in test runner.
- Produces: JUnit 4 tests asserting:
  - Quick telemetry row views (`telemetryScoreStat`, `telemetryKeystrokeStat`, `telemetryVectorStat`) do not exist in the layout.
  - Enrollment WPM and Acc views (`monkeytypeWpmText`, `monkeytypeAccText`, `enrollCompleteWpmText`, `enrollCompleteAccText`) do not exist.
  - `masterSwitch` is defined inside `btnGridVerifier` or Security Controls section, and NOT within `sandboxContainerCard`.
  - Required IDs (`btnGridVerifier`, `masterSwitch`, `gridVerifierStatusText`, `btnGridPolicy`, `gridPolicyStatusText`) exist.

- [ ] **Step 1: Write the failing test**

Create `app/src/test/java/com/chate/thesis/SecurityControlsLayoutContractTest.kt`:
```kotlin
package com.chate.thesis

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.File

class SecurityControlsLayoutContractTest {

    private fun loadLayoutXml(): String {
        val candidates = listOf(
            File("app/src/main/res/layout/activity_main.xml"),
            File("src/main/res/layout/activity_main.xml")
        )
        val file = candidates.firstOrNull { it.exists() }
            ?: throw IllegalStateException("activity_main.xml not found")
        return file.readText()
    }

    @Test
    fun testQuickTelemetryRowRemoved() {
        val xml = loadLayoutXml()
        assertFalse("telemetryScoreStat must be removed", xml.contains("@+id/telemetryScoreStat"))
        assertFalse("telemetryKeystrokeStat must be removed", xml.contains("@+id/telemetryKeystrokeStat"))
        assertFalse("telemetryVectorStat must be removed", xml.contains("@+id/telemetryVectorStat"))
    }

    @Test
    fun testWpmAndAccRemovedFromEnrollment() {
        val xml = loadLayoutXml()
        assertFalse("monkeytypeWpmText must be removed", xml.contains("@+id/monkeytypeWpmText"))
        assertFalse("monkeytypeAccText must be removed", xml.contains("@+id/monkeytypeAccText"))
        assertFalse("enrollCompleteWpmText must be removed", xml.contains("@+id/enrollCompleteWpmText"))
        assertFalse("enrollCompleteAccText must be removed", xml.contains("@+id/enrollCompleteAccText"))
    }

    @Test
    fun testMasterSwitchInSecurityControlsAndPreservedIds() {
        val xml = loadLayoutXml()
        assertTrue("masterSwitch must exist", xml.contains("@+id/masterSwitch"))
        assertTrue("btnGridVerifier must exist", xml.contains("@+id/btnGridVerifier"))
        assertTrue("gridVerifierStatusText must exist", xml.contains("@+id/gridVerifierStatusText"))
        assertTrue("btnGridPolicy must exist", xml.contains("@+id/btnGridPolicy"))
        assertTrue("gridPolicyStatusText must exist", xml.contains("@+id/gridPolicyStatusText"))

        // Ensure masterSwitch is NOT inside the sandbox container card
        val sandboxIndex = xml.indexOf("sandboxContainerCard")
        val masterSwitchIndex = xml.indexOf("@+id/masterSwitch")
        val securityControlsIndex = xml.indexOf("Security controls")
        assertTrue("Security controls section must exist", securityControlsIndex >= 0)
        assertTrue("masterSwitch must appear before sandboxContainerCard", masterSwitchIndex < sandboxIndex)
    }
}
```

- [ ] **Step 2: Run test to verify it fails**

Run: `./gradlew test --tests com.chate.thesis.SecurityControlsLayoutContractTest`
Expected: FAIL (because quick telemetry, WPM/Acc, and old switch location are still present).

- [ ] **Step 3: Commit the failing test**

```bash
git add app/src/test/java/com/chate/thesis/SecurityControlsLayoutContractTest.kt
git commit -m "test: add layout contract assertions for security controls and streamlined UI"
```

---

### Task 2: Remove Quick Telemetry Row and Purge Enrollment WPM / Acc

**Files:**
- Modify: `app/src/main/res/layout/activity_main.xml`

**Interfaces:**
- Consumes: `activity_main.xml`
- Produces: Cleaned XML layout free of:
  - Lines 254-290: Quick telemetry stats row (`telemetryScoreStat`, `telemetryKeystrokeStat`, `telemetryVectorStat`).
  - Lines 964-983: Monkeytype live metrics header WPM & ACC (`monkeytypeWpmText`, `monkeytypeAccText`).
  - Lines 1231-1262: Enrollment complete summary badge container (`enrollCompleteWpmText`, `enrollCompleteAccText`).

- [ ] **Step 1: Edit `activity_main.xml` to remove the quick telemetry row**

Remove the `LinearLayout` containing `telemetryScoreStat`, `telemetryKeystrokeStat`, and `telemetryVectorStat`.

- [ ] **Step 2: Edit `activity_main.xml` to remove Monkeytype WPM and ACC**

In Step 1 of Enrollment:
Remove `monkeytypeWpmText` and `monkeytypeAccText`.
Update `monkeytypeWordCountText` so its left alignment is clean (`android:layout_width="0dp"`, `android:layout_weight="1"`, without unnecessary left margin), keeping `btnMonkeytypeRestart` aligned to the right.

- [ ] **Step 3: Edit `activity_main.xml` to remove Enrollment Complete WPM and ACC badges**

In Step 2 of Enrollment:
Remove the `LinearLayout` containing `enrollCompleteWpmText` and `enrollCompleteAccText`.
Retain the "Enrollment Complete!" title, descriptive explanation, and `Status: READY` indicator.

- [ ] **Step 4: Run unit tests to check progress**

Run: `./gradlew test --tests com.chate.thesis.SecurityControlsLayoutContractTest`
Expected: `testQuickTelemetryRowRemoved` and `testWpmAndAccRemovedFromEnrollment` PASS; `testMasterSwitchInSecurityControlsAndPreservedIds` still fails.

- [ ] **Step 5: Commit changes**

```bash
git add app/src/main/res/layout/activity_main.xml
git commit -m "refactor: remove quick telemetry row and enrollment WPM and Acc metrics"
```

---

### Task 3: Rework Security Controls and Establish Master Switch

**Files:**
- Modify: `app/src/main/res/layout/activity_main.xml`
- Modify: `app/src/main/java/com/chate/thesis/MainActivity.kt:475-495`

**Interfaces:**
- Consumes:
  - `activity_main.xml`
  - `MainActivity.kt`
- Produces:
  - Reworked Security Controls section featuring:
    1. Prominent full-width Live Verifier master switch card (`btnGridVerifier`):
       - Shield icon (`@drawable/ic_shield_biometric`, tinted `?attr/colorPrimary`)
       - Title "Live Verifier" (15sp, sans-serif-medium)
       - Subtitle `gridVerifierStatusText` (12sp, "Standby · Inactive" / "Active · System Protected")
       - Direct embedded `SwitchMaterial` (`@+id/masterSwitch`)
    2. Full-width Enforcer Policy card (`btnGridPolicy`):
       - Lock icon (`@drawable/ic_lock_policy`, tinted `?attr/colorPrimary`)
       - Title "Enforcer Policy" (14sp, sans-serif-medium)
       - Subtitle `gridPolicyStatusText` (12sp, "Auto-Lock Policy · Enforced")
       - End chevron (`@drawable/ic_arrow_forward`, tinted `?attr/colorOnSurfaceVariant`)
  - Cleaned `sandboxContainerCard` header displaying:
    - Text "VERIFICATION SANDBOX" with no duplicate switch.
  - Click handling in `MainActivity.kt` ensuring toggling `btnGridVerifier` checks `if (masterSwitch.isEnabled) masterSwitch.isChecked = !masterSwitch.isChecked` safely.

- [ ] **Step 1: Update `activity_main.xml` with reworked Security Controls**

Replace lines 162-252 in `app/src/main/res/layout/activity_main.xml`:
```xml
                    <!-- ACTION / STATUS CARDS (Live Verifier Master Switch & Enforcer Policy) -->
                    <LinearLayout
                        android:layout_width="match_parent"
                        android:layout_height="wrap_content"
                        android:orientation="vertical"
                        android:layout_marginBottom="16dp">

                        <!-- Card 1: Live Verifier Master Switch -->
                        <LinearLayout
                            android:id="@+id/btnGridVerifier"
                            android:layout_width="match_parent"
                            android:layout_height="wrap_content"
                            android:minHeight="72dp"
                            android:orientation="horizontal"
                            android:gravity="center_vertical"
                            android:paddingHorizontal="16dp"
                            android:paddingVertical="12dp"
                            android:background="@drawable/card_feature_grid_bg"
                            android:clickable="true"
                            android:focusable="true">

                            <ImageView
                                android:layout_width="24dp"
                                android:layout_height="24dp"
                                android:src="@drawable/ic_shield_biometric"
                                app:tint="?attr/colorPrimary" />

                            <LinearLayout
                                android:layout_width="0dp"
                                android:layout_height="wrap_content"
                                android:layout_weight="1"
                                android:orientation="vertical"
                                android:layout_marginStart="14dp"
                                android:layout_marginEnd="10dp">

                                <TextView
                                    android:layout_width="wrap_content"
                                    android:layout_height="wrap_content"
                                    android:text="Live Verifier"
                                    android:textColor="?attr/colorOnSurface"
                                    android:textSize="15sp"
                                    android:fontFamily="sans-serif-medium" />

                                <TextView
                                    android:id="@+id/gridVerifierStatusText"
                                    android:layout_width="wrap_content"
                                    android:layout_height="wrap_content"
                                    android:text="Standby · System Inactive"
                                    android:textColor="?attr/colorOnSurfaceVariant"
                                    android:textSize="12sp"
                                    android:layout_marginTop="2dp" />
                            </LinearLayout>

                            <com.google.android.material.switchmaterial.SwitchMaterial
                                android:id="@+id/masterSwitch"
                                android:layout_width="wrap_content"
                                android:layout_height="wrap_content" />
                        </LinearLayout>

                        <!-- Card 2: Enforcer Policy -->
                        <LinearLayout
                            android:id="@+id/btnGridPolicy"
                            android:layout_width="match_parent"
                            android:layout_height="wrap_content"
                            android:minHeight="64dp"
                            android:orientation="horizontal"
                            android:gravity="center_vertical"
                            android:paddingHorizontal="16dp"
                            android:paddingVertical="12dp"
                            android:background="@drawable/card_feature_grid_bg"
                            android:layout_marginTop="10dp"
                            android:clickable="true"
                            android:focusable="true">

                            <ImageView
                                android:layout_width="24dp"
                                android:layout_height="24dp"
                                android:src="@drawable/ic_lock_policy"
                                app:tint="?attr/colorPrimary" />

                            <LinearLayout
                                android:layout_width="0dp"
                                android:layout_height="wrap_content"
                                android:layout_weight="1"
                                android:orientation="vertical"
                                android:layout_marginStart="14dp"
                                android:layout_marginEnd="10dp">

                                <TextView
                                    android:layout_width="wrap_content"
                                    android:layout_height="wrap_content"
                                    android:text="Enforcer Policy"
                                    android:textColor="?attr/colorOnSurface"
                                    android:textSize="14sp"
                                    android:fontFamily="sans-serif-medium" />

                                <TextView
                                    android:id="@+id/gridPolicyStatusText"
                                    android:layout_width="wrap_content"
                                    android:layout_height="wrap_content"
                                    android:text="Auto-Lock"
                                    android:textColor="?attr/colorOnSurfaceVariant"
                                    android:textSize="12sp"
                                    android:layout_marginTop="2dp" />
                            </LinearLayout>

                            <ImageView
                                android:layout_width="20dp"
                                android:layout_height="20dp"
                                android:src="@drawable/ic_arrow_forward"
                                app:tint="?attr/colorOnSurfaceVariant" />
                        </LinearLayout>
                    </LinearLayout>
```

- [ ] **Step 2: Remove duplicate `masterSwitch` from `sandboxContainerCard`**

In `activity_main.xml`, update the sandbox header (inside `sandboxContainerCard`):
```xml
                            <LinearLayout
                                android:layout_width="match_parent"
                                android:layout_height="wrap_content"
                                android:orientation="horizontal"
                                android:gravity="center_vertical"
                                android:layout_marginBottom="10dp">

                                <TextView
                                    android:layout_width="0dp"
                                    android:layout_height="wrap_content"
                                    android:layout_weight="1"
                                    android:text="Verification sandbox"
                                    android:textColor="?attr/colorOnSurfaceVariant"
                                    android:textSize="12sp"
                                    android:fontFamily="sans-serif-medium"
                                    android:textAllCaps="true"
                                    android:letterSpacing="0.06" />

                                <TextView
                                    android:layout_width="wrap_content"
                                    android:layout_height="wrap_content"
                                    android:text="Interactive Test"
                                    android:textColor="?attr/colorPrimary"
                                    android:textSize="11sp"
                                    android:fontFamily="sans-serif-medium"
                                    android:paddingHorizontal="8dp"
                                    android:paddingVertical="3dp"
                                    android:background="@drawable/badge_status_container" />
                            </LinearLayout>
```

- [ ] **Step 3: Update click listener in `MainActivity.kt`**

Ensure `btnGridVerifier` click handler respects enabled state and doesn't conflict with switch toggles:
```kotlin
        btnGridVerifier.setOnClickListener {
            if (masterSwitch.isEnabled) {
                masterSwitch.isChecked = !masterSwitch.isChecked
            } else {
                Toast.makeText(this, "Profile not ready. Complete enrollment first.", Toast.LENGTH_SHORT).show()
            }
        }
```

- [ ] **Step 4: Run unit tests to verify everything passes**

Run: `./gradlew test`
Expected: ALL tests PASS including `SecurityControlsLayoutContractTest`.

- [ ] **Step 5: Commit changes**

```bash
git add app/src/main/res/layout/activity_main.xml app/src/main/java/com/chate/thesis/MainActivity.kt
git commit -m "feat: rework security controls with live verifier as master switch and clean sandbox"
```

---

### Task 4: Full Verification and Regression Testing

**Files:**
- Test: All tests across the test suite

**Interfaces:**
- Consumes: Test suite and build system
- Produces: Clean compile and green test execution

- [ ] **Step 1: Run complete build and test suite**

Run: `./gradlew test`
Expected: `BUILD SUCCESSFUL` with all tests passing.

- [ ] **Step 2: Verify git status and diff**

Run: `git status`
Expected: Clean working tree or ready-to-merge state.


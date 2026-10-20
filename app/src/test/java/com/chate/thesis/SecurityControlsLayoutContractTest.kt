package com.chate.thesis

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.File

class SecurityControlsLayoutContractTest {

    private fun loadLayoutXml(): String {
        val candidates = listOf(
            File("app/src/main/res/layout/activity_main.xml"),
            File("src/main/res/layout/activity_main.xml"),
            File("/workspaces/thesis-system/app/src/main/res/layout/activity_main.xml")
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


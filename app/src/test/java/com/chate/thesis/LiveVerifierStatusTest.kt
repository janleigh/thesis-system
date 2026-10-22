package com.chate.thesis

import org.junit.Assert.assertEquals
import org.junit.Test

object LiveVerifierStatusHelper {
    fun getStatusText(profile: UserProfile?, isChecked: Boolean): String {
        return when {
            profile == null -> "Standby · No Enrolled Profile"
            profile.status == "PENDING" -> "Standby · Calibrating"
            profile.isReady && isChecked -> "Active · System Protected"
            else -> "Standby · System Inactive"
        }
    }
}

class LiveVerifierStatusTest {

    @Test
    fun testNoEnrolledProfileShowsDistinctMessage() {
        val status = LiveVerifierStatusHelper.getStatusText(null, false)
        assertEquals("Standby · No Enrolled Profile", status)

        val statusChecked = LiveVerifierStatusHelper.getStatusText(null, true)
        assertEquals("Standby · No Enrolled Profile", statusChecked)
    }

    @Test
    fun testCalibratingProfileShowsCalibratingMessage() {
        val profile = UserProfile(
            userId = "test_user",
            status = "PENDING",
            keystrokeCount = 50,
            minKeystrokes = 250,
            createdAt = "2026-10-22",
            updatedAt = "2026-10-22",
            vectors = mutableListOf()
        )
        val status = LiveVerifierStatusHelper.getStatusText(profile, false)
        assertEquals("Standby · Calibrating", status)
    }

    @Test
    fun testReadyProfileShowsActiveWhenChecked() {
        val profile = UserProfile(
            userId = "test_user",
            status = "READY",
            keystrokeCount = 250,
            minKeystrokes = 250,
            createdAt = "2026-10-22",
            updatedAt = "2026-10-22",
            vectors = mutableListOf()
        )
        val status = LiveVerifierStatusHelper.getStatusText(profile, true)
        assertEquals("Active · System Protected", status)
    }

    @Test
    fun testReadyProfileShowsStandbyInactiveWhenUnchecked() {
        val profile = UserProfile(
            userId = "test_user",
            status = "READY",
            keystrokeCount = 250,
            minKeystrokes = 250,
            createdAt = "2026-10-22",
            updatedAt = "2026-10-22",
            vectors = mutableListOf()
        )
        val status = LiveVerifierStatusHelper.getStatusText(profile, false)
        assertEquals("Standby · System Inactive", status)
    }
}

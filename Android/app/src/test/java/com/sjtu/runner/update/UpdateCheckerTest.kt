package com.sjtu.runner.update

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class UpdateCheckerTest {

    @Test
    fun parseVersionHandlesPrefixAndPrerelease() {
        assertEquals(listOf(4, 4, 0) to "", UpdateChecker.parseVersion("v4.4.0"))
        assertEquals(listOf(4, 5, 0) to "beta1", UpdateChecker.parseVersion("4.5.0-beta1"))
        assertEquals(listOf(4) to "", UpdateChecker.parseVersion("V4"))
        assertEquals(emptyList<Int>() to "", UpdateChecker.parseVersion(null))
        assertEquals(emptyList<Int>() to "", UpdateChecker.parseVersion("未发布"))
    }

    @Test
    fun newerRemoteTagIsDetected() {
        assertTrue(UpdateChecker.isNewer("v4.6.0", "4.5.0"))
        assertTrue(UpdateChecker.isNewer("v4.10.0", "4.9.9"))
        assertTrue(UpdateChecker.isNewer("v4.5.1", "4.5.0"))
    }

    @Test
    fun olderOrEqualRemoteTagIsIgnored() {
        assertFalse(UpdateChecker.isNewer("v4.4.9", "4.5.0"))
        assertFalse(UpdateChecker.isNewer("v4.5.0", "4.5.0"))
        assertFalse(UpdateChecker.isNewer(null, "4.5.0"))
        assertFalse(UpdateChecker.isNewer("未发布", "4.5.0"))
    }

    @Test
    fun stableVersionWinsOverPrereleaseOnTie() {
        assertFalse(UpdateChecker.isNewer("v4.5.0-beta1", "4.5.0"))
        assertTrue(UpdateChecker.isNewer("v4.5.0", "4.5.0-beta1"))
    }
}

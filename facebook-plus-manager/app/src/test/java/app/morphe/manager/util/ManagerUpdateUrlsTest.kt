/*
 * Copyright 2026 8BiT Softworks.
 * Derived from Morphe Manager.
 * https://github.com/MorpheApp/morphe-manager
 */

package app.morphe.manager.util

import kotlin.test.Test
import kotlin.test.assertContains
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class ManagerUpdateUrlsTest {

    @Test
    fun `manager updates read app-release json from this repo`() {
        assertContains(MANAGER_RELEASE_JSON_URL, "adnanXmacro/Scroll-freedom")
        assertContains(MANAGER_RELEASE_JSON_URL, "app-release.json")
        assertContains(MANAGER_PRERELEASE_JSON_URL, "adnanXmacro/Scroll-freedom")
        assertFalse(MANAGER_RELEASE_JSON_URL.contains("MorpheApp/morphe-manager"))
        assertFalse(MANAGER_PRERELEASE_JSON_URL.contains("MorpheApp/morphe-manager"))
    }

    @Test
    fun `manager repo is this repo not official Morphe`() {
        assertContains(MANAGER_REPO_URL, "adnanXmacro/Scroll-freedom")
        assertFalse(MANAGER_REPO_URL.contains("MorpheApp/morphe-manager"))
        assertTrue(USE_MANAGER_DIRECT_JSON)
    }
}

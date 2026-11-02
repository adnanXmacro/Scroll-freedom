/*
 * Copyright 2026 8BiT Softworks.
 * Derived from Morphe Manager.
 * https://github.com/MorpheApp/morphe-manager
 */

package app.morphe.manager.domain.manager

import kotlin.test.Test
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class ManagerUpdatePolicyTest {

    private val ours =
        "https://github.com/adnanXmacro/Scroll-freedom/releases/download/facebook-plus-manager-v2.1.0-debug/facebook-plus-manager-2.1.0-debug.apk"
    private val morphe =
        "https://github.com/MorpheApp/morphe-manager/releases/download/v2.1.0/morphe-manager-2.1.0.apk"
    private val ourPackage = "app.eightbitsoftworks.facebookplus.manager.debug"

    @Test
    fun `allows this repo release asset`() {
        assertTrue(ManagerUpdatePolicy.isAllowedDownloadUrl(ours))
    }

    @Test
    fun `refuses official Morphe manager apk`() {
        assertFalse(ManagerUpdatePolicy.isAllowedDownloadUrl(morphe))
        assertFalse(
            ManagerUpdatePolicy.isAllowedDownloadUrl(
                "https://raw.githubusercontent.com/MorpheApp/morphe-manager/main/app-release.json",
            ),
        )
    }

    @Test
    fun `refuses blank and non-https urls`() {
        assertFalse(ManagerUpdatePolicy.isAllowedDownloadUrl(""))
        assertFalse(ManagerUpdatePolicy.isAllowedDownloadUrl("   "))
        assertFalse(ManagerUpdatePolicy.isAllowedDownloadUrl("http://github.com/adnanXmacro/Scroll-freedom/x.apk"))
    }

    @Test
    fun `package must match exactly`() {
        assertTrue(ManagerUpdatePolicy.packageMatches(ourPackage, ourPackage))
        assertFalse(ManagerUpdatePolicy.packageMatches("app.morphe.manager", ourPackage))
        assertFalse(ManagerUpdatePolicy.packageMatches(null, ourPackage))
    }

    @Test
    fun `installs only when url and package are ours`() {
        assertTrue(ManagerUpdatePolicy.shouldInstall(ours, ourPackage, ourPackage))
        assertFalse(ManagerUpdatePolicy.shouldInstall(morphe, ourPackage, ourPackage))
        assertFalse(ManagerUpdatePolicy.shouldInstall(ours, "app.morphe.manager", ourPackage))
        assertFalse(ManagerUpdatePolicy.shouldInstall(morphe, "app.morphe.manager", ourPackage))
    }
}

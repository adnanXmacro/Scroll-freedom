/*
 * Copyright 2026 8BiT Softworks.
 * Derived from Morphe Manager.
 * https://github.com/MorpheApp/morphe-manager
 */

package app.morphe.manager.domain.facebookplus

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertIs
import kotlin.test.assertTrue

class FacebookPlusPinTest {

    private val matching = FacebookPlusApk(
        packageName = "com.facebook.katana",
        versionName = "584.0.0.51.56",
        versionCode = 475817393L,
        abis = setOf("arm64-v8a"),
    )

    @Test
    fun `pin targets HushFacebook and the supported Facebook build`() {
        assertEquals("com.facebook.katana", FacebookPlusPin.packageName)
        assertEquals("584.0.0.51.56", FacebookPlusPin.versionName)
        assertEquals(475817393L, FacebookPlusPin.versionCode)
        assertEquals("arm64-v8a", FacebookPlusPin.abi)
        assertEquals("480dpi", FacebookPlusPin.dpi)
        assertEquals("Facebook+", FacebookPlusPin.displayName)
        assertEquals("SysAdminDoc/HushFacebook", FacebookPlusPin.sourceRepo)
        assertTrue(FacebookPlusPin.apkMirrorUrl.contains("584-0-0-51-56"))
        assertTrue(FacebookPlusPin.apkMirrorUrl.contains("apkmirror.com"))
    }

    @Test
    fun `matching apk is accepted`() {
        assertEquals(FacebookPlusPin.Result.Ok, FacebookPlusPin.validate(matching))
    }

    @Test
    fun `wrong package is refused`() {
        val result = FacebookPlusPin.validate(matching.copy(packageName = "com.facebook.lite"))
        assertIs<FacebookPlusPin.Result.WrongPackage>(result)
    }

    @Test
    fun `wrong version name is refused`() {
        val result = FacebookPlusPin.validate(matching.copy(versionName = "583.0.0.0.0"))
        assertIs<FacebookPlusPin.Result.WrongVersion>(result)
    }

    @Test
    fun `wrong version code is refused`() {
        val result = FacebookPlusPin.validate(matching.copy(versionCode = 1L))
        assertIs<FacebookPlusPin.Result.WrongVersion>(result)
    }

    @Test
    fun `apk without arm64 is refused`() {
        val result = FacebookPlusPin.validate(matching.copy(abis = setOf("armeabi-v7a")))
        assertIs<FacebookPlusPin.Result.WrongAbi>(result)
    }

    @Test
    fun `device is supported only with arm64`() {
        assertTrue(FacebookPlusPin.deviceSupported(arrayOf("arm64-v8a", "armeabi-v7a")))
        assertFalse(FacebookPlusPin.deviceSupported(arrayOf("armeabi-v7a")))
        assertFalse(FacebookPlusPin.deviceSupported(emptyArray()))
    }
}

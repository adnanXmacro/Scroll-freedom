/*
 * Copyright 2026 8BiT Softworks.
 * Derived from Morphe Manager.
 * https://github.com/MorpheApp/morphe-manager
 */

package app.morphe.manager.domain.facebookplus

import java.io.File
import java.util.zip.ZipEntry
import java.util.zip.ZipOutputStream
import kotlin.io.path.createTempDirectory
import kotlin.test.AfterTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertIs
import kotlin.test.assertTrue

class FacebookPlusPinTest {

    private val pin = FacebookPlusPin.fromCatalog(
        versionName = "584.0.0.51.56",
        versionCodes = setOf(475817393L),
    )

    private val matching = FacebookPlusApk(
        packageName = "com.facebook.katana",
        versionName = "584.0.0.51.56",
        versionCode = 475817393L,
        abis = setOf("arm64-v8a"),
    )

    @Test
    fun `pin constants stay Facebook only`() {
        assertEquals("com.facebook.katana", FacebookPlusPin.packageName)
        assertEquals("arm64-v8a", FacebookPlusPin.abi)
        assertEquals("Facebook+", FacebookPlusPin.displayName)
        assertEquals("SysAdminDoc/HushFacebook", FacebookPlusPin.sourceRepo)
    }

    @Test
    fun `fromCatalog copies version name and codes`() {
        assertEquals("584.0.0.51.56", pin.versionName)
        assertEquals(setOf(475817393L), pin.versionCodes)
    }

    @Test
    fun `matching apk is accepted`() {
        assertEquals(FacebookPlusPin.Result.Ok, FacebookPlusPin.validate(matching, pin))
    }

    @Test
    fun `wrong package is refused`() {
        val result = FacebookPlusPin.validate(matching.copy(packageName = "com.facebook.lite"), pin)
        assertIs<FacebookPlusPin.Result.WrongPackage>(result)
    }

    @Test
    fun `wrong version name is refused`() {
        val result = FacebookPlusPin.validate(matching.copy(versionName = "583.0.0.0.0"), pin)
        assertIs<FacebookPlusPin.Result.WrongVersion>(result)
    }

    @Test
    fun `version code outside the catalog set is refused`() {
        val result = FacebookPlusPin.validate(matching.copy(versionCode = 1L), pin)
        assertIs<FacebookPlusPin.Result.WrongVersion>(result)
    }

    @Test
    fun `version code in the catalog set is accepted`() {
        val multi = FacebookPlusPin.fromCatalog("584.0.0.51.56", setOf(475817393L, 475817394L))
        assertEquals(
            FacebookPlusPin.Result.Ok,
            FacebookPlusPin.validate(matching.copy(versionCode = 475817394L), multi),
        )
    }

    @Test
    fun `when catalog lists no codes version name and abi are enough`() {
        val nameless = FacebookPlusPin.fromCatalog("584.0.0.51.56")
        assertEquals(
            FacebookPlusPin.Result.Ok,
            FacebookPlusPin.validate(matching.copy(versionCode = 1L), nameless),
        )
    }

    @Test
    fun `apk without arm64 is refused`() {
        val result = FacebookPlusPin.validate(matching.copy(abis = setOf("armeabi-v7a")), pin)
        assertIs<FacebookPlusPin.Result.WrongAbi>(result)
    }

    @Test
    fun `device is supported only with arm64`() {
        assertTrue(FacebookPlusPin.deviceSupported(arrayOf("arm64-v8a", "armeabi-v7a")))
        assertFalse(FacebookPlusPin.deviceSupported(arrayOf("armeabi-v7a")))
        assertFalse(FacebookPlusPin.deviceSupported(emptyArray()))
    }

    private val workspace = createTempDirectory("facebook-plus-pin").toFile()

    @AfterTest
    fun cleanup() {
        workspace.deleteRecursively()
    }

    private fun apks(vararg modules: String): File {
        val file = File(workspace, "facebook.apks")
        ZipOutputStream(file.outputStream()).use { zip ->
            modules.forEach { name ->
                zip.putNextEntry(ZipEntry(name))
                zip.write(name.toByteArray())
                zip.closeEntry()
            }
        }
        return file
    }

    @Test
    fun `split archive with arm64 config is accepted as arm64`() {
        val file = apks("base.apk", "split_config.arm64_v8a.apk", "split_config.xxhdpi.apk")
        val abis = FacebookPlusPin.abisOf(file)
        assertEquals(setOf("arm64-v8a"), abis)
        assertEquals(
            FacebookPlusPin.Result.Ok,
            FacebookPlusPin.validate(matching.copy(abis = abis), pin),
        )
    }

    @Test
    fun `split archive without arm64 is refused`() {
        val file = apks("base.apk", "split_config.armeabi_v7a.apk")
        val abis = FacebookPlusPin.abisOf(file)
        assertIs<FacebookPlusPin.Result.WrongAbi>(
            FacebookPlusPin.validate(matching.copy(abis = abis), pin),
        )
    }
}

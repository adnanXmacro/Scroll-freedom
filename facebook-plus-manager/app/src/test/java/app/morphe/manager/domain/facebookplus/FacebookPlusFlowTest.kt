/*
 * Copyright 2026 8BiT Softworks.
 * Derived from Morphe Manager.
 * https://github.com/MorpheApp/morphe-manager
 */

package app.morphe.manager.domain.facebookplus

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNull
import kotlin.test.assertTrue

class FacebookPlusFlowTest {

    @Test
    fun `facebook shows apk availability like other apps`() {
        assertFalse(FacebookPlusFlow.skipApkAvailability(FacebookPlusPin.packageName))
        assertEquals(
            FacebookPlusFlow.ApkSourceStep.ApkAvailability,
            FacebookPlusFlow.apkSourceStep(FacebookPlusPin.packageName),
        )
    }

    @Test
    fun `other packages still show apk availability`() {
        assertFalse(FacebookPlusFlow.skipApkAvailability("com.google.android.youtube"))
        assertEquals(
            FacebookPlusFlow.ApkSourceStep.ApkAvailability,
            FacebookPlusFlow.apkSourceStep("com.google.android.youtube"),
        )
        assertEquals(
            FacebookPlusFlow.ApkSourceStep.ApkAvailability,
            FacebookPlusFlow.apkSourceStep(null),
        )
    }

    @Test
    fun `pick file opens the file picker without failing the url handoff`() {
        assertEquals(
            FacebookPlusFlow.ApkSourceStep.FilePickerPrompt,
            FacebookPlusFlow.nextAfterPickFile(),
        )
    }

    @Test
    fun `opening apk mirror then continues to the file picker`() {
        assertEquals(
            FacebookPlusFlow.ApkSourceStep.FilePickerPrompt,
            FacebookPlusFlow.nextAfterOpenUrl(opened = true),
        )
        assertNull(FacebookPlusFlow.nextAfterOpenUrl(opened = false))
    }

    @Test
    fun `saved facebook apk is reused only when it matches the pin`() {
        assertTrue(
            FacebookPlusFlow.reuseSavedApk(FacebookPlusPin.packageName, "584.0.0.51.56", "584.0.0.51.56"),
        )
        assertFalse(
            FacebookPlusFlow.reuseSavedApk(FacebookPlusPin.packageName, "583.0.0.0.0", "584.0.0.51.56"),
        )
        assertFalse(
            FacebookPlusFlow.reuseSavedApk("com.google.android.youtube", "584.0.0.51.56", "584.0.0.51.56"),
        )
    }

    @Test
    fun `wrong pin reopens apk availability`() {
        assertEquals(
            FacebookPlusFlow.ApkSourceStep.ApkAvailability,
            FacebookPlusFlow.nextOnPinFailure(),
        )
    }

    @Test
    fun `facebook needs one gigabyte and cannot patch anyway`() {
        assertEquals(1_000_000_000L, FacebookPlusFlow.storageThresholdBytes(FacebookPlusPin.packageName))
        assertEquals(1_000_000_000L, FacebookPlusFlow.storageThresholdBytes("com.google.android.youtube"))
        assertFalse(FacebookPlusFlow.allowsPatchAnywayOnLowDisk(FacebookPlusPin.packageName))
        assertTrue(FacebookPlusFlow.allowsPatchAnywayOnLowDisk("com.google.android.youtube"))
        assertTrue(FacebookPlusFlow.storageOk(1_000_000_000L, FacebookPlusPin.packageName))
        assertFalse(FacebookPlusFlow.storageOk(999_999_999L, FacebookPlusPin.packageName))
    }

    @Test
    fun `gate refuses 32-bit before download`() {
        assertEquals(
            FacebookPlusFlow.Gate.DeviceUnsupported,
            FacebookPlusFlow.gate(
                deviceAbis = arrayOf("armeabi-v7a"),
                riskAccepted = true,
                stockFacebookInstalled = false,
                freeBytes = 4_000_000_000L,
            ),
        )
    }

    @Test
    fun `gate requires first-run risk acceptance`() {
        assertEquals(
            FacebookPlusFlow.Gate.RiskRequired,
            FacebookPlusFlow.gate(
                deviceAbis = arrayOf("arm64-v8a"),
                riskAccepted = false,
                stockFacebookInstalled = false,
                freeBytes = 4_000_000_000L,
            ),
        )
    }

    @Test
    fun `gate blocks stock facebook`() {
        assertEquals(
            FacebookPlusFlow.Gate.StockFacebookBlocks,
            FacebookPlusFlow.gate(
                deviceAbis = arrayOf("arm64-v8a"),
                riskAccepted = true,
                stockFacebookInstalled = true,
                freeBytes = 4_000_000_000L,
            ),
        )
    }

    @Test
    fun `gate blocks low storage`() {
        assertEquals(
            FacebookPlusFlow.Gate.LowStorage,
            FacebookPlusFlow.gate(
                deviceAbis = arrayOf("arm64-v8a"),
                riskAccepted = true,
                stockFacebookInstalled = false,
                freeBytes = 500_000_000L,
            ),
        )
    }

    @Test
    fun `facebook does not block on patcher version after the bundle loaded`() {
        assertTrue(FacebookPlusFlow.skipPatcherVersionGate(FacebookPlusPin.packageName))
        assertFalse(FacebookPlusFlow.skipPatcherVersionGate("com.google.android.youtube"))
        assertFalse(FacebookPlusFlow.skipPatcherVersionGate(null))
    }

    @Test
    fun `hushfacebook is shown as facebook plus`() {
        assertEquals("Facebook+", FacebookPlusFlow.publicSourceName("HushFacebook"))
        assertEquals("Facebook+", FacebookPlusFlow.publicSourceName("hushfacebook"))
        assertEquals("Other", FacebookPlusFlow.publicSourceName("Other"))
    }

    @Test
    fun `gate is ready when device storage and risk are ok`() {
        assertEquals(
            FacebookPlusFlow.Gate.Ready,
            FacebookPlusFlow.gate(
                deviceAbis = arrayOf("arm64-v8a", "armeabi-v7a"),
                riskAccepted = true,
                stockFacebookInstalled = false,
                freeBytes = 3_000_000_000L,
            ),
        )
    }
}

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
    fun `facebook skips apk availability and opens download instructions`() {
        assertTrue(FacebookPlusFlow.skipApkAvailability(FacebookPlusPin.packageName))
        assertEquals(
            FacebookPlusFlow.ApkSourceStep.DownloadInstructions,
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
            FacebookPlusFlow.reuseSavedApk(FacebookPlusPin.packageName, FacebookPlusPin.versionName),
        )
        assertFalse(
            FacebookPlusFlow.reuseSavedApk(FacebookPlusPin.packageName, "583.0.0.0.0"),
        )
        assertFalse(
            FacebookPlusFlow.reuseSavedApk("com.google.android.youtube", FacebookPlusPin.versionName),
        )
    }

    @Test
    fun `wrong pin reopens download instructions`() {
        assertEquals(
            FacebookPlusFlow.ApkSourceStep.DownloadInstructions,
            FacebookPlusFlow.nextOnPinFailure(),
        )
    }

    @Test
    fun `facebook needs two gigabytes and cannot patch anyway`() {
        assertEquals(2_000_000_000L, FacebookPlusFlow.storageThresholdBytes(FacebookPlusPin.packageName))
        assertEquals(1_000_000_000L, FacebookPlusFlow.storageThresholdBytes("com.google.android.youtube"))
        assertFalse(FacebookPlusFlow.allowsPatchAnywayOnLowDisk(FacebookPlusPin.packageName))
        assertTrue(FacebookPlusFlow.allowsPatchAnywayOnLowDisk("com.google.android.youtube"))
        assertTrue(FacebookPlusFlow.storageOk(2_000_000_000L, FacebookPlusPin.packageName))
        assertFalse(FacebookPlusFlow.storageOk(1_999_999_999L, FacebookPlusPin.packageName))
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

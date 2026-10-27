/*
 * Copyright 2026 8BiT Softworks.
 * Derived from Morphe Manager.
 * https://github.com/MorpheApp/morphe-manager
 */

package app.morphe.manager.domain.facebookplus

object FacebookPlusFlow {
    const val DEFAULT_STORAGE_THRESHOLD_BYTES = 1_000_000_000L
    const val FACEBOOK_STORAGE_THRESHOLD_BYTES = 2_000_000_000L

    enum class ApkSourceStep {
        ApkAvailability,
        DownloadInstructions,
        FilePickerPrompt,
    }

    sealed class Gate {
        data object DeviceUnsupported : Gate()
        data object RiskRequired : Gate()
        data object StockFacebookBlocks : Gate()
        data object LowStorage : Gate()
        data object Ready : Gate()
    }

    fun skipApkAvailability(packageName: String?): Boolean =
        packageName == FacebookPlusPin.packageName

    fun apkSourceStep(packageName: String?): ApkSourceStep =
        if (skipApkAvailability(packageName)) ApkSourceStep.DownloadInstructions
        else ApkSourceStep.ApkAvailability

    fun nextAfterPickFile(): ApkSourceStep = ApkSourceStep.FilePickerPrompt

    fun nextAfterOpenUrl(opened: Boolean): ApkSourceStep? =
        if (opened) ApkSourceStep.FilePickerPrompt else null

    fun reuseSavedApk(packageName: String?, savedVersion: String?): Boolean =
        packageName == FacebookPlusPin.packageName && savedVersion == FacebookPlusPin.versionName

    fun nextOnPinFailure(): ApkSourceStep = ApkSourceStep.DownloadInstructions

    fun storageThresholdBytes(packageName: String?): Long =
        if (packageName == FacebookPlusPin.packageName) FACEBOOK_STORAGE_THRESHOLD_BYTES
        else DEFAULT_STORAGE_THRESHOLD_BYTES

    fun allowsPatchAnywayOnLowDisk(packageName: String?): Boolean =
        packageName != FacebookPlusPin.packageName

    fun storageOk(freeBytes: Long, packageName: String?): Boolean =
        freeBytes >= storageThresholdBytes(packageName)

    fun gate(
        deviceAbis: Array<String>,
        riskAccepted: Boolean,
        stockFacebookInstalled: Boolean,
        freeBytes: Long,
    ): Gate = when {
        !FacebookPlusPin.deviceSupported(deviceAbis) -> Gate.DeviceUnsupported
        !riskAccepted -> Gate.RiskRequired
        stockFacebookInstalled -> Gate.StockFacebookBlocks
        !storageOk(freeBytes, FacebookPlusPin.packageName) -> Gate.LowStorage
        else -> Gate.Ready
    }
}

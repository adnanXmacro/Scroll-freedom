/*
 * Copyright 2026 8BiT Softworks.
 * Derived from Morphe Manager.
 * https://github.com/MorpheApp/morphe-manager
 */

package app.morphe.manager.domain.facebookplus

import app.morphe.manager.patcher.split.SplitApkPreparer
import app.morphe.manager.patcher.util.NativeLibs
import java.io.File

data class FacebookPlusApk(
    val packageName: String,
    val versionName: String,
    val versionCode: Long,
    val abis: Set<String>,
)

data class FacebookPlusPinSnapshot(
    val versionName: String,
    val versionCodes: Set<Long> = emptySet(),
)

object FacebookPlusPin {
    const val packageName = "com.facebook.katana"
    const val abi = "arm64-v8a"
    const val displayName = "Facebook+"
    const val sourceRepo = "SysAdminDoc/HushFacebook"

    sealed class Result {
        data object Ok : Result()
        data object WrongPackage : Result()
        data object WrongVersion : Result()
        data object WrongAbi : Result()
    }

    fun fromCatalog(versionName: String, versionCodes: Set<Long> = emptySet()) =
        FacebookPlusPinSnapshot(versionName = versionName, versionCodes = versionCodes)

    fun validate(apk: FacebookPlusApk, pin: FacebookPlusPinSnapshot): Result = when {
        apk.packageName != packageName -> Result.WrongPackage
        apk.versionName != pin.versionName -> Result.WrongVersion
        pin.versionCodes.isNotEmpty() && apk.versionCode !in pin.versionCodes -> Result.WrongVersion
        abi !in apk.abis -> Result.WrongAbi
        else -> Result.Ok
    }

    fun deviceSupported(abis: Array<String>): Boolean = abi in abis

    fun abisOf(file: File): Set<String> =
        if (SplitApkPreparer.isSplitArchive(file)) SplitApkPreparer.splitArchiveAbis(file).toSet()
        else NativeLibs.extractAbisFromApk(file).toSet()
}

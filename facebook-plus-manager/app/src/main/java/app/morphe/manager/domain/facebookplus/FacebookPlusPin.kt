/*
 * Copyright 2026 8BiT Softworks.
 * Derived from Morphe Manager.
 * https://github.com/MorpheApp/morphe-manager
 */

package app.morphe.manager.domain.facebookplus

data class FacebookPlusApk(
    val packageName: String,
    val versionName: String,
    val versionCode: Long,
    val abis: Set<String>,
)

object FacebookPlusPin {
    const val packageName = "com.facebook.katana"
    const val versionName = "584.0.0.51.56"
    const val versionCode = 475817393L
    const val abi = "arm64-v8a"
    const val dpi = "480dpi"
    const val displayName = "Facebook+"
    const val sourceRepo = "SysAdminDoc/HushFacebook"
    const val apkMirrorUrl =
        "https://www.apkmirror.com/apk/facebook-2/facebook/facebook-584-0-0-51-56-release/"

    sealed class Result {
        data object Ok : Result()
        data object WrongPackage : Result()
        data object WrongVersion : Result()
        data object WrongAbi : Result()
    }

    fun validate(apk: FacebookPlusApk): Result = when {
        apk.packageName != packageName -> Result.WrongPackage
        apk.versionName != versionName || apk.versionCode != versionCode -> Result.WrongVersion
        abi !in apk.abis -> Result.WrongAbi
        else -> Result.Ok
    }

    fun deviceSupported(abis: Array<String>): Boolean = abi in abis
}

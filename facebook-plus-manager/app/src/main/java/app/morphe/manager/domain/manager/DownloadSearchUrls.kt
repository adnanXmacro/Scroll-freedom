/*
 * Copyright 2026 8BiT Softworks.
 * Derived from Morphe Manager.
 * https://github.com/MorpheApp/morphe-manager
 */

package app.morphe.manager.domain.manager

import app.morphe.manager.domain.facebookplus.FacebookPlusPin
import app.morphe.manager.util.MORPHE_API_URL
import io.ktor.http.encodeURLPath

object DownloadSearchUrls {
    fun apiSearchUrl(packageName: String, version: String?, abi: String): String {
        val query = "$packageName~${version ?: "any"}~$abi".encodeURLPath()
        return "$MORPHE_API_URL/v2/web-search/$query"
    }

    fun apkMirrorVersionUrl(version: String): String {
        val slug = version.replace('.', '-')
        return "https://www.apkmirror.com/apk/facebook-2/facebook/facebook-$slug-release/"
    }

    fun facebookFallbackUrl(packageName: String, version: String?): String? {
        if (packageName != FacebookPlusPin.packageName || version.isNullOrBlank()) return null
        return apkMirrorVersionUrl(version)
    }
}

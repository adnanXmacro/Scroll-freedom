/*
 * Copyright 2026 8BiT Softworks.
 * Derived from Morphe Manager.
 * https://github.com/MorpheApp/morphe-manager
 */

package app.morphe.manager.domain.manager

object ManagerUpdatePolicy {
    private const val ALLOWED_HOST = "github.com"
    private const val ALLOWED_PATH_PREFIX = "/adnanXmacro/Scroll-freedom/"

    fun isAllowedDownloadUrl(url: String): Boolean {
        val trimmed = url.trim()
        if (trimmed.isEmpty()) return false
        val uri = runCatching { java.net.URI(trimmed) }.getOrNull() ?: return false
        if (!uri.scheme.equals("https", ignoreCase = true)) return false
        if (!uri.host.equals(ALLOWED_HOST, ignoreCase = true)) return false
        val path = uri.path.orEmpty()
        return path.startsWith(ALLOWED_PATH_PREFIX)
    }

    fun packageMatches(downloadedPackage: String?, expectedPackage: String): Boolean =
        downloadedPackage == expectedPackage

    fun shouldInstall(
        downloadUrl: String,
        downloadedPackage: String?,
        expectedPackage: String,
    ): Boolean = isAllowedDownloadUrl(downloadUrl) && packageMatches(downloadedPackage, expectedPackage)
}

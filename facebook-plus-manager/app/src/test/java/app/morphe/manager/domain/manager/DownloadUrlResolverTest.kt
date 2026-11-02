/*
 * Copyright 2026 8BiT Softworks.
 * Derived from Morphe Manager.
 * https://github.com/MorpheApp/morphe-manager
 */

package app.morphe.manager.domain.manager

import app.morphe.manager.util.MORPHE_API_URL
import kotlin.test.Test
import kotlin.test.assertContains
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNull
import kotlin.test.assertTrue

class DownloadUrlResolverTest {

    @Test
    fun `facebook api search uses morphe web-search not a hardcoded listing`() {
        val url = DownloadSearchUrls.apiSearchUrl("com.facebook.katana", "584.0.0.51.56", "arm64-v8a")
        assertTrue(url.startsWith(MORPHE_API_URL))
        assertContains(url, "com.facebook.katana")
        assertContains(url, "584.0.0.51.56")
        assertContains(url, "arm64-v8a")
        assertFalse(url.contains("apkmirror.com/apk/facebook-2/facebook/facebook-584-0-0-51-56-release"))
    }

    @Test
    fun `facebook fallback is the constructed apk mirror version page`() {
        val url = DownloadSearchUrls.facebookFallbackUrl("com.facebook.katana", "584.0.0.51.56")
        assertEquals(
            "https://www.apkmirror.com/apk/facebook-2/facebook/facebook-584-0-0-51-56-release/",
            url,
        )
    }

    @Test
    fun `other packages have no facebook fallback`() {
        assertNull(DownloadSearchUrls.facebookFallbackUrl("com.google.android.youtube", "19.0.0"))
        assertNull(DownloadSearchUrls.facebookFallbackUrl("com.facebook.katana", null))
    }
}

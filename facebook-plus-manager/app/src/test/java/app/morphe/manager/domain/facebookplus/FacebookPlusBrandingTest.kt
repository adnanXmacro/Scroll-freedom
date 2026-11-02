/*
 * Copyright 2026 8BiT Softworks.
 * Derived from Morphe Manager.
 * https://github.com/MorpheApp/morphe-manager
 */

package app.morphe.manager.domain.facebookplus

import java.io.File
import kotlin.test.Test
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class FacebookPlusBrandingTest {

    private val mainAppName = File("src/main/res/values/strings.xml")
    private val debugAppName = File("src/debug/res/values/strings.xml")

    @Test
    fun `main launcher label is Facebook+ Manager`() {
        val xml = mainAppName.readText()
        assertTrue(xml.contains("name=\"app_name\" translatable=\"false\">Facebook+ Manager<"))
        assertFalse(xml.contains("Morphe Debug"))
    }

    @Test
    fun `english copy never names HushFacebook`() {
        val xml = mainAppName.readText()
        assertFalse(xml.contains("HushFacebook"), xml)
        assertFalse(xml.contains("hushfacebook", ignoreCase = true))
    }

    @Test
    fun `debug launcher label is Facebook+ Manager not Morph Debug`() {
        assertTrue(debugAppName.exists(), "debug strings overlay must exist")
        val xml = debugAppName.readText()
        assertTrue(xml.contains("name=\"app_name\""))
        assertTrue(xml.contains(">Facebook+ Manager<"), xml)
        assertFalse(xml.contains("Morph"), xml)
        assertFalse(xml.contains("Morphe"), xml)
    }
}

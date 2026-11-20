package com.example

import android.content.Context
import androidx.test.core.app.ApplicationProvider
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import java.nio.charset.StandardCharsets

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [36])
class ExampleRobolectricTest {

    @Test
    fun `read string from context`() {
        val context = ApplicationProvider.getApplicationContext<Context>()
        val appName = context.getString(R.string.app_name)
        assertEquals("Block Space 3D", appName)
    }

    @Test
    fun `adapted game html embeds android bridge hooks`() {
        val context = ApplicationProvider.getApplicationContext<Context>()
        val html = String(HtmlAssetAdapter.loadAdaptedGameHtml(context), StandardCharsets.UTF_8)

        assertTrue(html.contains("window.__threeMissing = true"))
        assertTrue(html.contains("window.AndroidBridge.onScreenChanged(s)"))
        assertTrue(html.contains("window.AndroidBridge.haptic(n)"))
        assertTrue(html.contains("window.AndroidBridge.onEngineReady()"))
        assertTrue(html.contains("window.handleAndroidBack = function()"))
        assertTrue(html.contains("window.saveWorldNow = function()"))
        assertTrue(html.contains("window.exportWorldsJson = function()"))
        assertTrue(html.contains("window.importWorldsJson = function(rawJson)"))
        assertTrue(html.contains("tip.classList.toggle('on', false);"))
        assertFalse(html.contains("tip.classList.toggle('on', phone && state==='play'"))
    }
}

package com.nexabrowser.app.browser

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Renderer priority is the one thing the platform is told never to reclaim, so
 * the rule for holding it has to be exactly right: only the active tab, only
 * while the browser surface is on screen, and only while the Activity is
 * started.
 */
class RendererPolicyTest {

    @Test
    fun `the active tab is important while the browser surface is showing`() {
        assertTrue(RendererPolicy.important("tab-1", "tab-1", surfaceVisible = true, hostVisible = true))
    }

    @Test
    fun `an inactive tab is never important`() {
        assertFalse(RendererPolicy.important("tab-2", "tab-1", surfaceVisible = true, hostVisible = true))
    }

    @Test
    fun `no tab is important while an in-app screen covers the browser`() {
        assertFalse(RendererPolicy.important("tab-1", "tab-1", surfaceVisible = false, hostVisible = true))
    }

    @Test
    fun `no tab is important while the app is in the background`() {
        assertFalse(RendererPolicy.important("tab-1", "tab-1", surfaceVisible = true, hostVisible = false))
        assertFalse(RendererPolicy.important("tab-1", "tab-1", surfaceVisible = false, hostVisible = false))
    }

    @Test
    fun `a tab with no active id is not important`() {
        assertFalse(RendererPolicy.important("tab-1", null, surfaceVisible = true, hostVisible = true))
    }

    @Test
    fun `only the active tab keeps running while the app is started`() {
        assertTrue(RendererPolicy.running("tab-1", "tab-1", hostVisible = true))
        assertFalse(RendererPolicy.running("tab-2", "tab-1", hostVisible = true))
        assertFalse(RendererPolicy.running("tab-1", "tab-1", hostVisible = false))
    }

    @Test
    fun `an in-app screen does not pause the page`() {
        // The surface being hidden must not change the running decision: audio
        // and video keep playing while the user reads Settings.
        assertTrue(RendererPolicy.running("tab-1", "tab-1", hostVisible = true))
    }
}

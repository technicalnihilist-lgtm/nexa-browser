package com.nexabrowser.app.util

import com.google.common.truth.Truth.assertThat
import org.junit.Test

/**
 * The favicon cache stores every icon at a bounded size. These tests pin the
 * sizing rule that keeps stored icons small without distorting their aspect.
 */
class FaviconSizingTest {

    @Test
    fun `icon at or under the cap keeps its exact size`() {
        assertThat(FaviconSizing.targetSize(32, 32)).isEqualTo(32 to 32)
        assertThat(FaviconSizing.targetSize(96, 96)).isEqualTo(96 to 96)
        assertThat(FaviconSizing.targetSize(16, 48)).isEqualTo(16 to 48)
    }

    @Test
    fun `square icon larger than the cap is scaled down`() {
        assertThat(FaviconSizing.targetSize(512, 512)).isEqualTo(96 to 96)
    }

    @Test
    fun `wide icon keeps its aspect ratio`() {
        val (w, h) = FaviconSizing.targetSize(192, 96)
        assertThat(w).isEqualTo(96)
        assertThat(h).isEqualTo(48)
    }

    @Test
    fun `tall icon keeps its aspect ratio`() {
        val (w, h) = FaviconSizing.targetSize(64, 256)
        assertThat(w).isEqualTo(24)
        assertThat(h).isEqualTo(96)
    }

    @Test
    fun `scaled icon never collapses below one pixel`() {
        val (w, h) = FaviconSizing.targetSize(1024, 1)
        assertThat(w).isEqualTo(96)
        assertThat(h).isAtLeast(1)
    }

    @Test
    fun `degenerate sizes are clamped`() {
        assertThat(FaviconSizing.targetSize(0, 0)).isEqualTo(1 to 1)
        assertThat(FaviconSizing.targetSize(-4, 32)).isEqualTo(1 to 1)
    }

    @Test
    fun `custom cap is honoured`() {
        assertThat(FaviconSizing.targetSize(200, 100, max = 50)).isEqualTo(50 to 25)
    }

    @Test
    fun `icon already inside the cap needs no rescale`() {
        assertThat(FaviconSizing.needsScale(16, 16)).isFalse()
        assertThat(FaviconSizing.needsScale(48, 48)).isFalse()
        assertThat(FaviconSizing.needsScale(96, 96)).isFalse()
        assertThat(FaviconSizing.needsScale(16, 96)).isFalse()
    }

    @Test
    fun `icon larger than the cap needs a rescale`() {
        assertThat(FaviconSizing.needsScale(512, 512)).isTrue()
        assertThat(FaviconSizing.needsScale(97, 97)).isTrue()
        assertThat(FaviconSizing.needsScale(64, 256)).isTrue()
    }

    @Test
    fun `degenerate icon sizes need a rescale`() {
        // targetSize clamps a zero or negative edge to 1px, which is a change.
        assertThat(FaviconSizing.needsScale(0, 0)).isTrue()
        assertThat(FaviconSizing.needsScale(-4, 32)).isTrue()
    }

    @Test
    fun `needsScale follows a custom cap`() {
        assertThat(FaviconSizing.needsScale(60, 60, max = 96)).isFalse()
        assertThat(FaviconSizing.needsScale(60, 60, max = 50)).isTrue()
    }

    @Test
    fun `decode edge follows the drawn size and the screen density`() {
        // 28dp at 2.625x is ~73px: decode there instead of at the stored 96px.
        assertThat(FaviconSizing.decodeEdge(28f, 2.625f)).isEqualTo(73)
        assertThat(FaviconSizing.decodeEdge(24f, 2f)).isEqualTo(48)
        assertThat(FaviconSizing.decodeEdge(20f, 3f)).isEqualTo(60)
    }

    @Test
    fun `decode edge never exceeds the stored icon size`() {
        // A 4x screen would ask for 112px, which the file cannot provide.
        assertThat(FaviconSizing.decodeEdge(28f, 4f)).isEqualTo(FaviconSizing.MAX_ICON_EDGE)
        assertThat(FaviconSizing.decodeEdge(28f, 4f, max = 64)).isEqualTo(64)
    }

    @Test
    fun `decode edge is at least one pixel`() {
        assertThat(FaviconSizing.decodeEdge(0f, 3f)).isEqualTo(1)
        assertThat(FaviconSizing.decodeEdge(-8f, 3f)).isEqualTo(1)
        assertThat(FaviconSizing.decodeEdge(28f, 0f)).isEqualTo(1)
        assertThat(FaviconSizing.decodeEdge(0.1f, 1f)).isEqualTo(1)
    }
}

package com.nexabrowser.app.browser

import com.google.common.truth.Truth.assertThat
import org.junit.Test

class MemoryTrimTest {

    @Test
    fun `no caches are dropped for an unknown or zero level`() {
        assertThat(MemoryTrim.dropCaches(0)).isFalse()
        assertThat(MemoryTrim.dropCaches(-1)).isFalse()
        assertThat(MemoryTrim.dropCaches(1)).isFalse()
    }

    @Test
    fun `every memory-pressure level drops the rebuildable caches`() {
        val levels = listOf(
            MemoryTrim.RUNNING_MODERATE,
            MemoryTrim.RUNNING_LOW,
            MemoryTrim.RUNNING_CRITICAL,
            MemoryTrim.MODERATE,
            MemoryTrim.COMPLETE
        )
        levels.forEach { level ->
            assertThat(MemoryTrim.dropCaches(level)).isTrue()
        }
    }

    /**
     * ON_TRIM_MEMORY_UI_HIDDEN and _BACKGROUND are delivered on every ordinary
     * app switch. They are not pressure signals — they sorted above the
     * RUNNING_* levels, so a `level >=` test dropped the favicon stat memo and
     * the ad blocker's host table every time the user left the app, and the
     * frame they came back to re-stat'ed every stored icon to rebuild them.
     */
    @Test
    fun `routine backgrounding keeps the rebuildable caches`() {
        assertThat(MemoryTrim.dropCaches(MemoryTrim.UI_HIDDEN)).isFalse()
        assertThat(MemoryTrim.dropCaches(MemoryTrim.BACKGROUND)).isFalse()
    }

    @Test
    fun `routine backgrounding does not free renderer memory`() {
        assertThat(MemoryTrim.freeRendererMemory(MemoryTrim.UI_HIDDEN)).isFalse()
        assertThat(MemoryTrim.freeRendererMemory(MemoryTrim.BACKGROUND)).isFalse()
        assertThat(MemoryTrim.freeRendererMemory(MemoryTrim.RUNNING_MODERATE)).isFalse()
        assertThat(MemoryTrim.freeRendererMemory(0)).isFalse()
    }

    @Test
    fun `genuine pressure frees renderer memory`() {
        val levels = listOf(
            MemoryTrim.RUNNING_LOW,
            MemoryTrim.RUNNING_CRITICAL,
            MemoryTrim.MODERATE,
            MemoryTrim.COMPLETE
        )
        levels.forEach { level ->
            assertThat(MemoryTrim.freeRendererMemory(level)).isTrue()
        }
    }

    @Test
    fun `critical levels free renderer memory and drop caches together`() {
        val level = MemoryTrim.RUNNING_CRITICAL
        assertThat(MemoryTrim.dropCaches(level)).isTrue()
        assertThat(MemoryTrim.freeRendererMemory(level)).isTrue()
    }
}

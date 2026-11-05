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
    fun `every real trim level drops the rebuildable caches`() {
        val levels = listOf(
            MemoryTrim.RUNNING_MODERATE,
            MemoryTrim.RUNNING_LOW,
            MemoryTrim.RUNNING_CRITICAL,
            MemoryTrim.UI_HIDDEN,
            MemoryTrim.BACKGROUND,
            MemoryTrim.MODERATE,
            MemoryTrim.COMPLETE
        )
        levels.forEach { level ->
            assertThat(MemoryTrim.dropCaches(level)).isTrue()
        }
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

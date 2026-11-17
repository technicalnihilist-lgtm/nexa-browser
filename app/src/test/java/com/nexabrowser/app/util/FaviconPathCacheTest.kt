package com.nexabrowser.app.util

import com.google.common.truth.Truth.assertThat
import org.junit.After
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder
import java.io.File

/**
 * The favicon existence cache answers from memory, so it must still be truthful:
 * a path only counts as present when a non-empty file is really there, and the
 * writer must be able to invalidate it.
 */
class FaviconPathCacheTest {

    @get:Rule
    val folder = TemporaryFolder()

    @Before
    @After
    fun reset() = FaviconPathCache.clear()

    @Test
    fun `null and blank paths are never present`() {
        assertThat(FaviconPathCache.exists(null)).isFalse()
        assertThat(FaviconPathCache.exists("")).isFalse()
        assertThat(FaviconPathCache.exists("   ")).isFalse()
    }

    @Test
    fun `missing file is reported absent`() {
        val missing = File(folder.root, "nope.png")
        assertThat(FaviconPathCache.exists(missing.absolutePath)).isFalse()
    }

    @Test
    fun `empty file is treated as absent`() {
        val empty = folder.newFile("empty.png")
        assertThat(FaviconPathCache.exists(empty.absolutePath)).isFalse()
    }

    @Test
    fun `stored icon is reported present`() {
        val icon = folder.newFile("icon.png")
        icon.writeBytes(byteArrayOf(1, 2, 3, 4))
        assertThat(FaviconPathCache.exists(icon.absolutePath)).isTrue()
    }

    @Test
    fun `answer is remembered until it is forgotten`() {
        val icon = File(folder.root, "later.png")

        assertThat(FaviconPathCache.exists(icon.absolutePath)).isFalse()

        // The file appears behind the cache's back: the remembered answer stands,
        // which is exactly why the writer invalidates it.
        icon.writeBytes(byteArrayOf(9, 9))
        assertThat(FaviconPathCache.exists(icon.absolutePath)).isFalse()

        FaviconPathCache.forget(icon.absolutePath)
        assertThat(FaviconPathCache.exists(icon.absolutePath)).isTrue()
    }

    @Test
    fun `markStored makes a freshly written icon present`() {
        val icon = File(folder.root, "written.png")
        icon.writeBytes(byteArrayOf(7))
        FaviconPathCache.markStored(icon.absolutePath)
        assertThat(FaviconPathCache.exists(icon.absolutePath)).isTrue()
    }

    /**
     * The memo keeps two generations rather than emptying itself when it fills.
     *
     * 300 paths is past the 256-entry generation, so at least one rollover has
     * happened by the end. Every one of them must still be remembered: the rows
     * these paths belong to are the ones on screen, and a forgotten path is a
     * `stat()` on the main thread the next time that row is drawn.
     */
    @Test
    fun `a rollover does not forget the paths that were already remembered`() {
        val paths = (1..300).map { File(folder.root, "icon-$it.png").absolutePath }
        paths.forEach { FaviconPathCache.markStored(it) }

        assertThat(paths.count { FaviconPathCache.exists(it) }).isEqualTo(300)
    }

    /** The bound is unchanged by the rollover: two generations, 512 paths. */
    @Test
    fun `the memo stays bounded across many rollovers`() {
        repeat(1_000) { FaviconPathCache.markStored(File(folder.root, "p$it.png").absolutePath) }

        assertThat(FaviconPathCache.size).isAtMost(512)
    }

    /** A path that has aged into the older generation is still forgettable. */
    @Test
    fun `forget reaches a path that has aged into the older generation`() {
        val aged = File(folder.root, "aged.png").absolutePath
        FaviconPathCache.markStored(aged)
        repeat(300) { FaviconPathCache.markStored(File(folder.root, "filler-$it.png").absolutePath) }

        // Still remembered after the rollover, then forgotten on request.
        assertThat(FaviconPathCache.exists(aged)).isTrue()
        FaviconPathCache.forget(aged)
        assertThat(FaviconPathCache.exists(aged)).isFalse()
    }

    @Test
    fun `clear drops every remembered answer`() {
        val icon = folder.newFile("gone.png")
        icon.writeBytes(byteArrayOf(5))
        assertThat(FaviconPathCache.exists(icon.absolutePath)).isTrue()

        icon.delete()
        FaviconPathCache.clear()

        assertThat(FaviconPathCache.exists(icon.absolutePath)).isFalse()
    }
}

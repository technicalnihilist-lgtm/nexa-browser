package com.nexabrowser.app

import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.google.common.truth.Truth.assertThat
import com.nexabrowser.app.data.local.NexaDatabase
import com.nexabrowser.app.data.repository.BrowserRepository
import com.nexabrowser.app.domain.model.PermissionDecision
import com.nexabrowser.app.domain.model.SitePermission
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith

/**
 * Instrumented persistence checks. These need a device or emulator, so they are
 * not run in a headless build host — see the README's "Tests" section.
 */
@RunWith(AndroidJUnit4::class)
class BrowserPersistenceTest {

    private lateinit var db: NexaDatabase
    private lateinit var repository: BrowserRepository

    @Before
    fun setUp() {
        db = Room.inMemoryDatabaseBuilder(
            ApplicationProvider.getApplicationContext(),
            NexaDatabase::class.java
        ).allowMainThreadQueries().build()
        repository = BrowserRepository(db)
    }

    @After
    fun tearDown() = db.close()

    @Test
    fun bookmarksPersistAndToggle() = runBlocking {
        assertThat(repository.isBookmarked("https://example.com")).isFalse()
        repository.toggleBookmark("Example", "https://example.com", null)
        assertThat(repository.isBookmarked("https://example.com")).isTrue()
        assertThat(repository.bookmarks.first()).hasSize(1)

        repository.toggleBookmark("Example", "https://example.com", null)
        assertThat(repository.isBookmarked("https://example.com")).isFalse()
    }

    @Test
    fun historyRecordsVisitsAndClears() = runBlocking {
        repository.recordVisit("A", "https://a.example", null)
        repository.recordVisit("B", "https://b.example", null)
        assertThat(repository.history.first()).hasSize(2)

        repository.clearHistory()
        assertThat(repository.history.first()).isEmpty()
    }

    @Test
    fun repeatedVisitToSameUrlIsDeduplicated() = runBlocking {
        repository.recordVisit("A", "https://a.example", null)
        repository.recordVisit("A again", "https://a.example", null)
        val entries = repository.history.first()
        assertThat(entries).hasSize(1)
        assertThat(entries.first().title).isEqualTo("A again")
    }

    @Test
    fun sitePermissionDecisionsAreRememberedAndRevocable() = runBlocking {
        repository.rememberPermission("https://a.example", SitePermission.CAMERA, PermissionDecision.ALLOW)
        assertThat(repository.permissionFor("https://a.example", SitePermission.CAMERA))
            .isEqualTo(PermissionDecision.ALLOW)

        repository.forgetPermission("https://a.example", SitePermission.CAMERA)
        assertThat(repository.permissionFor("https://a.example", SitePermission.CAMERA)).isNull()
    }

    @Test
    fun sessionTabsRoundTrip() = runBlocking {
        repository.saveSessionTabs(
            listOf(
                Triple("https://a.example", "A", 0),
                Triple("https://b.example", "B", 1)
            )
        )
        val restored = repository.loadSessionTabs()
        assertThat(restored).hasSize(2)
        assertThat(restored.map { it.first }).containsExactly("https://a.example", "https://b.example")
    }
}

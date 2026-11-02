package com.nexabrowser.app.data.repository

import com.google.common.truth.Truth.assertThat
import com.nexabrowser.app.data.local.entity.DownloadEntity
import com.nexabrowser.app.domain.model.DownloadStatus
import org.junit.Test

/**
 * The download tracker now polls only while the stored rows say there is work.
 * These tests pin down that decision, so a finished or empty list can never keep
 * the once-a-second DownloadManager query running.
 */
class DownloadActiveTest {

    private fun row(status: DownloadStatus, id: Long = 1L) = DownloadEntity(
        id = id,
        url = "https://example.com/file.zip",
        fileName = "file.zip",
        mimeType = "application/zip",
        totalBytes = 4096L,
        downloadedBytes = 1024L,
        status = status.name,
        localUri = null
    )

    @Test
    fun `an empty table is idle`() {
        assertThat(DownloadActive.anyActive(emptyList())).isFalse()
    }

    @Test
    fun `finished and failed rows are idle`() {
        val rows = listOf(
            row(DownloadStatus.COMPLETED, 1L),
            row(DownloadStatus.FAILED, 2L),
            row(DownloadStatus.CANCELLED, 3L)
        )
        assertThat(DownloadActive.anyActive(rows)).isFalse()
    }

    @Test
    fun `pending running and paused rows keep the poll alive`() {
        listOf(DownloadStatus.PENDING, DownloadStatus.RUNNING, DownloadStatus.PAUSED).forEach { status ->
            assertThat(DownloadActive.anyActive(listOf(row(DownloadStatus.COMPLETED, 1L), row(status, 2L))))
                .isTrue()
        }
    }

    @Test
    fun `an unknown status is treated as idle rather than polling forever`() {
        val row = row(DownloadStatus.COMPLETED).copy(status = "SOMETHING_NEW")
        assertThat(DownloadActive.anyActive(listOf(row))).isFalse()
    }
}

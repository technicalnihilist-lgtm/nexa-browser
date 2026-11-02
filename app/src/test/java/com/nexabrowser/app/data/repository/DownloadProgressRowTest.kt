package com.nexabrowser.app.data.repository

import com.google.common.truth.Truth.assertThat
import com.nexabrowser.app.data.local.entity.DownloadEntity
import com.nexabrowser.app.domain.model.DownloadStatus
import org.junit.Test

/**
 * The download poll runs once a second for as long as any transfer is active.
 * These tests pin down when a polled row is allowed to reach the database: an
 * unchanged row must not write, because every write invalidates the `downloads`
 * Flow and recomposes the list for a progress bar that is not moving.
 */
class DownloadProgressRowTest {

    private fun row(
        downloadedBytes: Long = 1024L,
        totalBytes: Long = 4096L,
        status: String = DownloadStatus.RUNNING.name,
        reason: String? = null,
        localUri: String? = null
    ) = DownloadEntity(
        id = 7L,
        url = "https://example.com/file.zip",
        fileName = "file.zip",
        mimeType = "application/zip",
        totalBytes = totalBytes,
        downloadedBytes = downloadedBytes,
        status = status,
        localUri = localUri,
        reason = reason
    )

    @Test
    fun `identical values need no write`() {
        assertThat(
            DownloadProgressRow.needsWrite(
                row = row(),
                downloadedBytes = 1024L,
                totalBytes = 4096L,
                status = DownloadStatus.RUNNING.name,
                reason = null
            )
        ).isFalse()
    }

    @Test
    fun `stalled download keeps needing no write`() {
        // A paused transfer reports the same byte count forever; every one of
        // those ticks used to be a database update.
        val paused = row(status = DownloadStatus.PAUSED.name)
        repeat(5) {
            assertThat(
                DownloadProgressRow.needsWrite(
                    row = paused,
                    downloadedBytes = 1024L,
                    totalBytes = 4096L,
                    status = DownloadStatus.PAUSED.name,
                    reason = null
                )
            ).isFalse()
        }
    }

    @Test
    fun `new bytes need a write`() {
        assertThat(
            DownloadProgressRow.needsWrite(row(), 2048L, 4096L, DownloadStatus.RUNNING.name, null)
        ).isTrue()
    }

    @Test
    fun `a total that was unknown and is now known needs a write`() {
        assertThat(
            DownloadProgressRow.needsWrite(row(totalBytes = -1L), 1024L, 4096L, DownloadStatus.RUNNING.name, null)
        ).isTrue()
    }

    @Test
    fun `a status change needs a write`() {
        assertThat(
            DownloadProgressRow.needsWrite(row(), 4096L, 4096L, DownloadStatus.COMPLETED.name, null)
        ).isTrue()
    }

    @Test
    fun `a failure reason needs a write`() {
        assertThat(
            DownloadProgressRow.needsWrite(
                row(),
                1024L,
                4096L,
                DownloadStatus.FAILED.name,
                "Not enough storage space"
            )
        ).isTrue()
    }
}

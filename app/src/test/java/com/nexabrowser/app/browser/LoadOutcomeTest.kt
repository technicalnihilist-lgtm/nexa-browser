package com.nexabrowser.app.browser

import com.google.common.truth.Truth.assertThat
import org.junit.Test

/**
 * WebView delivers `onPageFinished` for its own error page as well as for a real
 * one, so the finish callback has to decide which failures still stand. The rule
 * is pinned here: a transport or TLS failure survives the finish (nothing was
 * delivered, and the overlay with Try Again has to stay), while an HTTP status
 * error does not (that page really did render, so covering it would be wrong).
 */
class LoadOutcomeTest {

    private fun error(code: Int) =
        PageError(code, "description", "https://example.com/", PageError.isNetwork(code))

    @Test
    fun `a tab that failed for no reason keeps no error`() {
        assertThat(LoadOutcome.errorAfterFinish(null)).isNull()
    }

    @Test
    fun `a transport failure survives the page finishing`() {
        for (code in listOf(
            PageError.HOST_LOOKUP,
            PageError.CONNECT,
            PageError.IO,
            PageError.TIMEOUT,
            PageError.PROXY_AUTHENTICATION,
            PageError.TOO_MANY_REQUESTS
        )) {
            val kept = LoadOutcome.errorAfterFinish(error(code))
            assertThat(kept).isNotNull()
            assertThat(kept!!.errorCode).isEqualTo(code)
        }
    }

    @Test
    fun `a certificate failure survives the page finishing`() {
        val kept = LoadOutcome.errorAfterFinish(error(PageError.FAILED_SSL_HANDSHAKE))
        assertThat(kept).isNotNull()
        assertThat(kept!!.isNetworkError).isTrue()
    }

    @Test
    fun `an http status error is cleared once the page finishes`() {
        // The server answered with a document — the site's own 404 page — so the
        // overlay must not sit on top of it.
        for (code in listOf(404, 500, 403)) {
            assertThat(LoadOutcome.errorAfterFinish(error(code))).isNull()
        }
    }

    @Test
    fun `a page level failure is cleared once the page finishes`() {
        for (code in listOf(
            PageError.UNKNOWN,
            PageError.BAD_URL,
            PageError.FILE_NOT_FOUND,
            PageError.REDIRECT_LOOP
        )) {
            assertThat(LoadOutcome.errorAfterFinish(error(code))).isNull()
        }
    }

    @Test
    fun `the description and url of the kept error are untouched`() {
        val original = PageError(PageError.CONNECT, "net::ERR_CONNECTION_REFUSED", "https://a.test/x", true)
        val kept = LoadOutcome.errorAfterFinish(original)
        assertThat(kept).isEqualTo(original)
    }
}

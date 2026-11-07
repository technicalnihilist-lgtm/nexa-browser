package com.nexabrowser.app.browser

import com.google.common.truth.Truth.assertThat
import org.junit.Test

/**
 * The network/page split decides which wording the error overlay uses, so the
 * code sets it recognises are pinned here — including the fact that a server
 * error (an HTTP status, which is not a network failure) is not one of them.
 */
class PageErrorTest {

    @Test
    fun `transport failures are network errors`() {
        val transport = listOf(
            PageError.HOST_LOOKUP,
            PageError.CONNECT,
            PageError.IO,
            PageError.TIMEOUT,
            PageError.PROXY_AUTHENTICATION,
            PageError.FAILED_SSL_HANDSHAKE,
            PageError.TOO_MANY_REQUESTS
        )
        for (code in transport) {
            assertThat(PageError.isNetwork(code)).isTrue()
        }
    }

    @Test
    fun `page level failures are not network errors`() {
        val page = listOf(
            PageError.UNKNOWN,
            PageError.UNSUPPORTED_AUTH_SCHEME,
            PageError.AUTHENTICATION,
            PageError.REDIRECT_LOOP,
            PageError.BAD_URL,
            PageError.FILE_NOT_FOUND,
            PageError.FILE_ACCESS_DENIED,
            PageError.UNSAFE_RESOURCE,
            404,
            500
        )
        for (code in page) {
            assertThat(PageError.isNetwork(code)).isFalse()
        }
    }
}

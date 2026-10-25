package com.nexabrowser.app.browser

/** A load failure worth surfacing to the user instead of a blank page. */
data class PageError(
    val errorCode: Int,
    val description: String,
    val url: String,
    val isNetworkError: Boolean
) {
    companion object {
        const val HOST_LOOKUP = -2
        const val UNSUPPORTED_AUTH_SCHEME = -3
        const val AUTHENTICATION = -4
        const val PROXY_AUTHENTICATION = -5
        const val CONNECT = -6
        const val IO = -7
        const val TIMEOUT = -8
        const val REDIRECT_LOOP = -9
        const val UNKNOWN = -10
        const val FAILED_SSL_HANDSHAKE = -11
        const val BAD_URL = -12
        const val FILE_NOT_FOUND = -13
        const val FILE_ACCESS_DENIED = -14
        const val TOO_MANY_REQUESTS = -15
        const val UNSAFE_RESOURCE = -16

        fun isNetwork(code: Int): Boolean = code in setOf(
            HOST_LOOKUP, CONNECT, IO, TIMEOUT, PROXY_AUTHENTICATION, FAILED_SSL_HANDSHAKE, TOO_MANY_REQUESTS
        )
    }
}

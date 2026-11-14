package com.nexabrowser.app.browser

/**
 * The "force HTTPS" extension's rewrite of a cleartext navigation.
 *
 * The rule used to be a single inline line in
 * [com.nexabrowser.app.browser.NexaWebViewClient.shouldOverrideUrlLoading]:
 *
 * ```
 * view.loadUrl(url.replaceFirst("http://", "https://"))
 * return true
 * ```
 *
 * which had two problems, both of them invisible until a page actually used an
 * upper-case scheme:
 *
 *  * **`String.replaceFirst` takes a regular expression.** Every cleartext
 *    navigation compiled a `Pattern` and built a `Matcher` to splice six
 *    characters — on the one path that decides how soon the page starts loading.
 *  * **It matched case-sensitively.** `request.url.scheme` was lower-cased to
 *    decide whether to upgrade, but the rewrite was applied to the url as the
 *    page wrote it. A link to `HTTP://example.com/` therefore entered the branch
 *    (scheme == "http"), came out of `replaceFirst` completely unchanged, and was
 *    handed straight back to `loadUrl()` — which fires this callback again, with
 *    the same url, for the same reason. The navigation span instead of being
 *    upgraded, and the page never loaded.
 *
 * The rewrite is a splice by length against a prefix compared case-insensitively,
 * so an upper-case scheme is upgraded exactly like a lower-case one and nothing
 * is compiled. A url that does not actually carry `http://` — an opaque form such
 * as `http:foo`, which a `Uri` reports with the scheme `http` but no authority —
 * returns null so the caller leaves it alone rather than mangling it.
 *
 * Kept free of Android types so the rule is unit tested on the JVM.
 */
object HttpsUpgrade {

    private const val HTTP_PREFIX = "http://"
    private const val HTTPS_PREFIX = "https://"

    /**
     * [url] with `http://` replaced by `https://`, or null when [url] is not a
     * hierarchical cleartext url and must not be rewritten.
     */
    fun upgraded(url: String): String? {
        if (url.length <= HTTP_PREFIX.length) return null
        if (!url.regionMatches(0, HTTP_PREFIX, 0, HTTP_PREFIX.length, ignoreCase = true)) return null
        return HTTPS_PREFIX + url.substring(HTTP_PREFIX.length)
    }
}

package com.nexabrowser.app.browser

/**
 * Decides whether the ad blocker may answer a request itself.
 *
 * The blocker is a subresource filter. It used to run on *every* request the
 * WebView makes, including the top-level document, and answer a blocked one with
 * an empty `text/plain` body. Nothing else happens for that navigation: no
 * network error, no `onReceivedError`, no error overlay, no page — so opening a
 * link whose host is on the list (or following a site's top-level redirect that
 * lands on one, which tracking and shortener hosts do constantly) left the user
 * on a blank white screen with the address in the bar and no way to tell that
 * anything had been blocked. The tab looked hung, and reloading it did the same
 * thing again.
 *
 * The document itself is the page the user asked for; only the subresources
 * around it are worth filtering. Skipping the main frame also removes a
 * blocklist walk and a response object from the one request that decides how
 * soon the page starts painting.
 *
 * Kept free of Android types so the rule is unit tested.
 */
object AdBlockPolicy {

    /**
     * True when this request should be answered with [AdBlocker.emptyResponse].
     *
     * @param isForMainFrame the request is the top-level document
     * @param adBlockEnabled the user's ad-blocking preference
     * @param blocked the host is on the blocklist
     */
    fun shouldIntercept(
        isForMainFrame: Boolean,
        adBlockEnabled: Boolean,
        blocked: Boolean
    ): Boolean = adBlockEnabled && !isForMainFrame && blocked
}

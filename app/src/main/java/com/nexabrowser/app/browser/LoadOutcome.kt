package com.nexabrowser.app.browser

/**
 * What a tab should still show once its page reports that it has finished
 * loading.
 *
 * `onPageFinished` used to clear the tab's error unconditionally. That looked
 * harmless — a finished page has presumably loaded — but WebView delivers
 * `onPageFinished` for the *error page* too: a main-frame failure arrives as
 * `onReceivedError` (or `onReceivedSslError`), the renderer paints its own grey
 * "web page not available" document, and only then does `onPageFinished` fire.
 * Clearing the error at that point erased the message that had just been set, so
 * the "This page couldn't be loaded" overlay with its **Try Again** button never
 * appeared at all — it was set and wiped within the same page load. The user was
 * left with the bare platform error page and no way back into the site except
 * retyping the address.
 *
 * Two kinds of failure have to be told apart:
 *
 *  * **Network and TLS failures** (`PageError.isNetwork`): nothing was
 *    delivered. The page "finishing" says nothing about whether the site is
 *    reachable, so the error stays and the overlay is shown.
 *  * **HTTP status errors** (a 404, a 500): the server answered with a real
 *    document, which the user is looking at. The error is cleared, exactly as
 *    before, so the overlay does not cover a page that actually rendered.
 *
 * A stale error cannot survive a navigation either way: `onPageStarted` clears
 * it at the start of every new load, so an error still present here always
 * belongs to the document that just finished.
 *
 * Kept free of Android types so the rule is unit tested rather than inferred
 * from a device.
 */
object LoadOutcome {

    /**
     * The error the tab should keep after `onPageFinished`, or null when the
     * finished page means the failure no longer stands.
     */
    fun errorAfterFinish(current: PageError?): PageError? =
        if (current != null && current.isNetworkError) current else null
}

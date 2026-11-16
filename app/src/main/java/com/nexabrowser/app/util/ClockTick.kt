package com.nexabrowser.app.util

/**
 * When the start page's clock should next be redrawn.
 *
 * The clock is drawn from a `Date` held in snapshot state and refreshed from a
 * `while (true) { now = Date(); delay(30_000) }` loop. A fixed 30-second period
 * is wrong in both directions:
 *
 *  * **Half the ticks change nothing.** The clock shows `h:mm`, so the string it
 *    formats is the same on two consecutive ticks. The second one still rebuilt
 *    the `Date`, ran `SimpleDateFormat.format` over it and recomposed the two
 *    `Text` nodes that make the clock (the gradient fill and the stroked rim
 *    over a wallpaper), for a picture that was already on screen.
 *  * **The minute can be shown up to 30 seconds late.** The loop starts at an
 *    arbitrary phase, so the tick that crosses a minute boundary lands wherever
 *    the loop happened to begin — a clock that says 09:41 while the phone says
 *    09:42 is the visible symptom.
 *
 * Sleeping until just past the next minute boundary fixes both: the clock is
 * redrawn once a minute, and within a fraction of a second of the minute
 * actually changing.
 *
 * Kept free of Android types so the arithmetic is unit tested on the JVM.
 */
object ClockTick {

    /** Length of the period the clock displays. */
    const val MINUTE_MS = 60_000L

    /**
     * A small margin past the boundary, so the tick lands after the minute has
     * really changed rather than on the edge of it. Without it a `delay` that
     * returns a millisecond early would re-format the *old* minute and then wait
     * another full minute for the next attempt.
     */
    const val MARGIN_MS = 250L

    /**
     * How long to wait, from [nowMillis], before formatting the clock again.
     *
     * Always in `MARGIN_MS..MINUTE_MS + MARGIN_MS`, so the caller can pass it
     * straight to `delay` — there is no input that produces a zero-length sleep
     * and no way to spin.
     */
    fun delayToNextMinute(nowMillis: Long): Long {
        val intoMinute = ((nowMillis % MINUTE_MS) + MINUTE_MS) % MINUTE_MS
        return MINUTE_MS - intoMinute + MARGIN_MS
    }
}

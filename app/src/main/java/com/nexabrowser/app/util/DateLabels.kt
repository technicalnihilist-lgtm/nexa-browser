package com.nexabrowser.app.util

import java.text.SimpleDateFormat
import java.util.Calendar
import java.util.Date
import java.util.Locale

/**
 * Day and time labels for the history list.
 *
 * The history screen groups its rows by day and shows a time on every row, and
 * both labels used to be built with a *fresh* formatter and a *fresh* Calendar
 * per call: `dayLabel` called `Calendar.getInstance()` twice and, for any entry
 * that was not from today or yesterday, constructed a `SimpleDateFormat`; the
 * row's `timeLabel` constructed another `SimpleDateFormat` for every row it drew.
 *
 * Opening a 5,000-row history therefore built ~10,000 Calendars and thousands of
 * SimpleDateFormats on the main thread inside composition — `SimpleDateFormat`
 * pulls in a `DecimalFormat`, a `DateFormatSymbols` and a locale lookup on every
 * construction, which is tens of microseconds each — and every scrolled row paid
 * for another one. None of that work depends on anything but the timestamp.
 *
 * Here the formatters and the scratch Calendar/Date are created once per thread
 * (Compose calls these from the main thread; a worker that ever asks gets its own
 * copy, so nothing is shared unsafely), and the Today/Yesterday decision is a
 * pure comparison of calendar fields so it can be unit tested without a device.
 */
internal object DateLabels {

    /** Which of the four label forms a timestamp falls into. */
    enum class Bucket { TODAY, YESTERDAY, SAME_YEAR, EARLIER }

    /**
     * The bucket for a timestamp, given the calendar fields of "now" and of the
     * timestamp itself. Pure: no clock, no time zone, no formatting.
     */
    fun bucket(nowYear: Int, nowDayOfYear: Int, thenYear: Int, thenDayOfYear: Int): Bucket = when {
        nowYear == thenYear && nowDayOfYear == thenDayOfYear -> Bucket.TODAY
        nowYear == thenYear && nowDayOfYear - thenDayOfYear == 1 -> Bucket.YESTERDAY
        nowYear == thenYear -> Bucket.SAME_YEAR
        else -> Bucket.EARLIER
    }

    /**
     * The heading a history row belongs under: "Today", "Yesterday", the weekday
     * and date for an earlier day in the same year, or the full date otherwise.
     */
    fun dayLabel(timestamp: Long, now: Long = System.currentTimeMillis()): String {
        val cal = calendar.get()
        cal.timeInMillis = now
        val nowYear = cal.get(Calendar.YEAR)
        val nowDay = cal.get(Calendar.DAY_OF_YEAR)
        cal.timeInMillis = timestamp
        val thenYear = cal.get(Calendar.YEAR)
        val thenDay = cal.get(Calendar.DAY_OF_YEAR)
        return when (bucket(nowYear, nowDay, thenYear, thenDay)) {
            Bucket.TODAY -> "Today"
            Bucket.YESTERDAY -> "Yesterday"
            Bucket.SAME_YEAR -> weekdayFormat.get().format(dateOf(timestamp))
            Bucket.EARLIER -> yearFormat.get().format(dateOf(timestamp))
        }
    }

    /** The time of day shown on a history row. */
    fun timeLabel(timestamp: Long): String = clockFormat.get().format(dateOf(timestamp))

    // One instance per thread. SimpleDateFormat is not thread safe, so it is
    // never shared between threads; Calendar and Date are reused in place so a
    // label costs no allocation at all beyond the string it returns.
    private val calendar: ThreadLocal<Calendar> = threadLocal { Calendar.getInstance() }
    private val weekdayFormat: ThreadLocal<SimpleDateFormat> =
        threadLocal { SimpleDateFormat("EEEE, d MMMM", Locale.getDefault()) }
    private val yearFormat: ThreadLocal<SimpleDateFormat> =
        threadLocal { SimpleDateFormat("d MMMM yyyy", Locale.getDefault()) }
    private val clockFormat: ThreadLocal<SimpleDateFormat> =
        threadLocal { SimpleDateFormat("h:mm a", Locale.getDefault()) }
    private val scratchDate: ThreadLocal<Date> = threadLocal { Date(0L) }

    private fun dateOf(timestamp: Long): Date {
        val date: Date = scratchDate.get()
        date.time = timestamp
        return date
    }

    private fun <T : Any> threadLocal(create: () -> T): ThreadLocal<T> =
        object : ThreadLocal<T>() {
            override fun initialValue(): T = create()
        }
}

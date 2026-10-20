package com.ferhatozcelik.jetpackcomposetemplate.ui.activitys

import org.junit.Assert.assertEquals
import org.junit.Test

class FormatTimeTest {
    @Test
    fun minutesAndSeconds() {
        assertEquals("0:00", formatTime(0))
        assertEquals("0:09", formatTime(9_999))
        assertEquals("1:01", formatTime(61_000))
        assertEquals("59:59", formatTime(3_599_000))
    }

    @Test
    fun hours() {
        assertEquals("1:00:00", formatTime(3_600_000))
        assertEquals("1:02:03", formatTime(3_723_000))
    }

    @Test
    fun negativeIsZero() {
        assertEquals("0:00", formatTime(-5_000))
    }
}

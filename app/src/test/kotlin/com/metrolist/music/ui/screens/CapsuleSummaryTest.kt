package com.metrolist.music.ui.screens

import java.time.LocalDate
import java.time.YearMonth
import org.junit.Assert.*
import org.junit.Test

class CapsuleSummaryTest {
    @Test fun `leap month totals aggregate before rounding and exclude other months`() {
        val month=YearMonth.of(2024,2)
        val result=capsuleSummary(month,listOf(LocalDate.of(2024,2,28) to 30000L,LocalDate.of(2024,2,28) to 30000L,LocalDate.of(2024,2,29) to 120000L,LocalDate.of(2024,3,1) to 999999L,LocalDate.of(2024,2,27) to -1L))
        assertEquals(29,result.dailyMinutes.size);assertEquals(3L,result.totalMinutes);assertEquals(1L,result.dailyMinutes[27]);assertEquals(2,result.longestStreak)
    }
    @Test fun `empty month has no streak`() { assertEquals(0,capsuleSummary(YearMonth.of(2026,10),emptyList()).longestStreak) }
}

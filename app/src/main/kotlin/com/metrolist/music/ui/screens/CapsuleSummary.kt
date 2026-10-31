package com.metrolist.music.ui.screens

import java.time.LocalDate
import java.time.YearMonth

internal data class CapsuleSummary(val totalMillis: Long, val dailyMinutes: List<Long>, val longestStreak: Int) {
    val totalMinutes: Long get() = totalMillis / 60000
}

internal fun capsuleSummary(month: YearMonth, plays: List<Pair<LocalDate, Long>>): CapsuleSummary {
    val daily = LongArray(month.lengthOfMonth())
    plays.filter { YearMonth.from(it.first) == month && it.second > 0 }.forEach { (day, duration) -> daily[day.dayOfMonth - 1] += duration }
    var streak = 0
    var longest = 0
    daily.forEach { duration ->
        streak = if (duration > 0) streak + 1 else 0
        longest = maxOf(longest, streak)
    }
    return CapsuleSummary(daily.sum(), daily.map { it / 60000 }, longest)
}

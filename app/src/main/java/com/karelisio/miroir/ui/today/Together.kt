package com.karelisio.miroir.ui.today

import java.time.LocalDate
import java.time.MonthDay
import java.time.Period
import java.time.temporal.ChronoUnit
import java.util.Locale

/** Jours écoulés depuis la date de votre anniversaire de couple (null si absente ou à venir). */
fun daysTogether(since: LocalDate?, today: LocalDate): Long? =
    since?.let { ChronoUnit.DAYS.between(it, today) }?.takeIf { it >= 0 }

data class Milestone(val emoji: String, val title: String)

/** Palier à fêter : chaque anniversaire et chaque centaine de jours. */
fun milestoneOf(since: LocalDate?, today: LocalDate): Milestone? {
    val days = daysTogether(since, today)?.takeIf { it > 0 } ?: return null
    val years = Period.between(since, today).years
    return when {
        years > 0 && MonthDay.from(since) == MonthDay.from(today) -> Milestone("💍", "$years an${if (years > 1) "s" else ""} ensemble aujourd'hui")
        days % 100 == 0L -> Milestone("🎉", "Aujourd'hui : %,d jours ensemble".format(Locale.FRENCH, days))
        else -> null
    }
}

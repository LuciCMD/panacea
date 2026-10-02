package com.clementine.panacea.ui.settings

import com.clementine.panacea.data.db.KEEP_REMOVED_MS
import com.clementine.panacea.ui.counted
import java.time.Instant
import java.time.ZonedDateTime
import java.time.temporal.ChronoUnit
import kotlin.math.ceil

/** A medication waiting in Recently Removed. */
data class RemovedItem(val id: Long, val name: String, val status: String)

object RemovedText {
    private const val DAY_MS = 24 * 60 * 60_000.0

    /** "Removed today · deleted in 30 days", "Removed 3 days ago · deleted in 27 days", "… deleted within a day". */
    fun status(removedAt: Long, now: ZonedDateTime): String {
        val removed = Instant.ofEpochMilli(removedAt).atZone(now.zone)
        val removedWhen = when (val ago = ChronoUnit.DAYS.between(removed.toLocalDate(), now.toLocalDate())) {
            0L -> "Removed today"
            1L -> "Removed yesterday"
            else -> "Removed $ago days ago"
        }
        val left = ceil((removedAt + KEEP_REMOVED_MS - now.toInstant().toEpochMilli()) / DAY_MS).toInt()
        val goes = if (left <= 1) "deleted within a day" else "deleted in ${counted(left, "day")}"
        return "$removedWhen · $goes"
    }
}

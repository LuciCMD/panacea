package com.clementine.panacea.ui

import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flow
import java.time.ZonedDateTime

/** The time now, then again at the start of each new minute, for screens that say "4 h ago". */
fun minuteTicks(): Flow<ZonedDateTime> = flow {
    while (true) {
        val now = ZonedDateTime.now()
        emit(now)
        delay(60_000L - now.second * 1000L - now.nano / 1_000_000 + 50)
    }
}

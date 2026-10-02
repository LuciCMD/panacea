package com.clementine.panacea.data.db

/** Keys of the [MetaEntity] table. Stored; never rename one. */
object MetaKeys {
    const val LEGACY34_IMPORTED = "legacy34.imported"
    const val LEGACY34_PROBLEMS = "legacy34.problems"
    /** Present once the user has read the import problems and put the card away. */
    const val LEGACY34_PROBLEMS_SEEN = "legacy34.problems.seen"
    /** Amount multipliers offered as presets, comma separated; absent until first saved. */
    const val AMOUNT_PRESETS = "amount.presets"
    /** Every reminder stays quiet until this time (epoch ms); absent or 0 when not muted. */
    const val MUTE_ALL_UNTIL = "mute.all.until"
}

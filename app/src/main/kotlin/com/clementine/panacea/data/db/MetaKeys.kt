package com.clementine.panacea.data.db

/** Keys of the [MetaEntity] table. Stored; never rename one. */
object MetaKeys {
    const val LEGACY34_IMPORTED = "legacy34.imported"
    const val LEGACY34_PROBLEMS = "legacy34.problems"
    /** Amount multipliers offered as presets, comma separated; absent until first saved. */
    const val AMOUNT_PRESETS = "amount.presets"
}

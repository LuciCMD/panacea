package com.clementine.panacea.model

/** Preset categories, one per medication. Keys are stored (and match 3.4); never rename one. */
enum class Category(val key: String, val label: String) {
    PRESCRIBED("Prescribed", "Prescribed"),
    OTC("OTC", "OTC"),
    RECREATIONAL("Recreational", "Recreational"),
    UNCATEGORIZED("Uncategorized", "None");

    companion object {
        fun fromKey(key: String?): Category = entries.firstOrNull { it.key == key } ?: UNCATEGORIZED
    }
}

/** Dose forms. Keys are stored (and match 3.4); never rename one, add new ones with a fresh key. */
/** [noun] is one pill or item of this form, as in "50 mg per tablet". */
enum class MedicationType(val key: String, val label: String, val noun: String) {
    ORAL_TABLET("ORAL_TABLET", "Tablet", "tablet"),
    ORAL_CAPSULE("ORAL_CAPSULE", "Capsule", "capsule"),
    LIQUID_SYRUP("LIQUID_SYRUP", "Liquid", "dose"),
    IV_INJECTION("IV_INJECTION", "Injection", "injection"),
    SUBLINGUAL("SUBLINGUAL", "Sublingual", "tablet"),
    TOPICAL("TOPICAL", "Cream", "application"),
    INHALER("INHALER", "Inhaler", "puff"),
    DROPS("DROPS", "Drops", "drop"),
    PATCH("PATCH", "Patch", "patch"),
    SUPPOSITORY("SUPPOSITORY", "Suppository", "suppository"),
    POWDER("POWDER", "Powder", "scoop"),
    EDIBLE("EDIBLE", "Gummy", "gummy"),
    OTHER("OTHER", "Other", "item"),
    UNSPECIFIED("UNSPECIFIED", "Unspecified", "item");

    companion object {
        fun fromKey(key: String?): MedicationType = entries.firstOrNull { it.key == key } ?: UNSPECIFIED
    }
}

enum class RepeatType { HOURLY, DAILY, WEEKLY, MONTHLY }

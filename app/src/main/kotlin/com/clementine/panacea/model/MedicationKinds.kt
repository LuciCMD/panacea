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
enum class MedicationType(val key: String, val label: String) {
    ORAL_TABLET("ORAL_TABLET", "Tablet"),
    ORAL_CAPSULE("ORAL_CAPSULE", "Capsule"),
    LIQUID_SYRUP("LIQUID_SYRUP", "Liquid"),
    IV_INJECTION("IV_INJECTION", "Injection"),
    SUBLINGUAL("SUBLINGUAL", "Sublingual"),
    TOPICAL("TOPICAL", "Cream"),
    INHALER("INHALER", "Inhaler"),
    DROPS("DROPS", "Drops"),
    PATCH("PATCH", "Patch"),
    SUPPOSITORY("SUPPOSITORY", "Suppository"),
    POWDER("POWDER", "Powder"),
    EDIBLE("EDIBLE", "Gummy"),
    OTHER("OTHER", "Other"),
    UNSPECIFIED("UNSPECIFIED", "Unspecified");

    companion object {
        fun fromKey(key: String?): MedicationType = entries.firstOrNull { it.key == key } ?: UNSPECIFIED
    }
}

enum class RepeatType { HOURLY, DAILY, WEEKLY, MONTHLY }

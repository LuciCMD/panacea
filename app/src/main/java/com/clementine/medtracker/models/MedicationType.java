package com.clementine.medtracker.models;

import com.clementine.medtracker.R;

/**
 * Preset medication forms with a matching icon each.
 *
 * TO ADD / CHANGE AN ICON: edit the third argument below (the drawable resource).
 * Drop a vector or PNG into app/src/main/res/drawable/ and reference it here as
 * R.drawable.your_icon. To add a whole new type, add an enum constant with a unique
 * key (the key is what gets saved to disk, so don't rename existing keys).
 *
 * @author LuciCMD
 */
public enum MedicationType {
    ORAL_TABLET("ORAL_TABLET", "Oral Tablet", R.drawable.ic_type_tablet),
    ORAL_CAPSULE("ORAL_CAPSULE", "Oral Capsule", R.drawable.ic_type_capsule),
    LIQUID_SYRUP("LIQUID_SYRUP", "Liquid / Syrup", R.drawable.ic_type_liquid),
    IV_INJECTION("IV_INJECTION", "IV / Injection", R.drawable.ic_type_injection),
    SUBLINGUAL("SUBLINGUAL", "Sublingual", R.drawable.ic_type_sublingual),
    TOPICAL("TOPICAL", "Topical / Cream", R.drawable.ic_type_topical),
    INHALER("INHALER", "Inhaler", R.drawable.ic_type_inhaler),
    DROPS("DROPS", "Drops", R.drawable.ic_type_drops),
    PATCH("PATCH", "Patch", R.drawable.ic_type_patch),
    SUPPOSITORY("SUPPOSITORY", "Suppository", R.drawable.ic_type_suppository),
    POWDER("POWDER", "Powder", R.drawable.ic_type_powder),
    EDIBLE("EDIBLE", "Edible / Gummy", R.drawable.ic_type_edible),
    OTHER("OTHER", "Other", R.drawable.ic_type_other),
    UNSPECIFIED("UNSPECIFIED", "Unspecified", R.drawable.ic_type_unspecified);

    private final String key;
    private final String displayName;
    private final int iconRes;

    MedicationType(String key, String displayName, int iconRes) {
        this.key = key;
        this.displayName = displayName;
        this.iconRes = iconRes;
    }

    public String getKey() { return key; }
    public String getDisplayName() { return displayName; }
    public int getIconRes() { return iconRes; }

    public static MedicationType fromKey(String key) {
        if (key != null) {
            for (MedicationType type : values()) {
                if (type.key.equals(key)) return type;
            }
        }
        return UNSPECIFIED;
    }

    /** Types offered to the user when picking (everything except the internal "Unspecified"). */
    public static MedicationType[] selectable() {
        MedicationType[] all = values();
        MedicationType[] result = new MedicationType[all.length - 1];
        int i = 0;
        for (MedicationType type : all) {
            if (type != UNSPECIFIED) result[i++] = type;
        }
        return result;
    }
}

package com.clementine.panacea.ui.theme

import androidx.compose.ui.graphics.Color

/**
 * The themes to pick from; every one keeps the same roles and meets their contrast, checked in
 * ThemesTest. Keys are stored, so a theme keeps its key when its look and label change
 */
enum class Themes(val key: String, val label: String, val palette: Palette) {
    /** Neutral graphite, after JetBrains' and GitHub Dimmed's lighter darks, with the launcher icon's orange; the default */
    SLATE(
        "slate", "Graphite",
        Palette(
            abyss = Color(0xFF1C1D20), ground = Color(0xFF222326), surface = Color(0xFF2B2C30),
            raised = Color(0xFF333438), field = Color(0xFF3B3C41), line = Color(0xFF4B4C53), lineSoft = Color(0xFF36373C),
            ink = Color(0xFFEDEDEF), muted = Color(0xFFA9AAB2), faint = Color(0xFF8C8D95), onAccent = Color(0xFF2A1405),
            accent = Color(0xFFF5A05A), accentDim = Color(0xFFA86A38),
            good = Color(0xFF74C98A), warn = Color(0xFFEDCB5C), bad = Color(0xFFF47B85),
        ),
    ),

    /** Warm paper for bright rooms; a burnt orange keeps the accent readable on white */
    DAYLIGHT(
        "daylight", "Paper",
        Palette(
            abyss = Color(0xFFEAE7E0), ground = Color(0xFFF5F3EE), surface = Color(0xFFFFFFFF),
            raised = Color(0xFFF1EEE8), field = Color(0xFFE8E4DC), line = Color(0xFFCBC5BA), lineSoft = Color(0xFFDED9CF),
            ink = Color(0xFF201E1A), muted = Color(0xFF58544C), faint = Color(0xFF77726A), onAccent = Color(0xFFFFFFFF),
            accent = Color(0xFF943F0A), accentDim = Color(0xFFE8B48E),
            good = Color(0xFF2A7A3B), warn = Color(0xFF8A6200), bad = Color(0xFFB42318),
            isLight = true,
        ),
    ),

    /** Grey-green with cream text and a leaf green, after Everforest */
    MOSS(
        "moss", "Moss",
        Palette(
            abyss = Color(0xFF272E33), ground = Color(0xFF2D353B), surface = Color(0xFF343F44),
            raised = Color(0xFF3A454A), field = Color(0xFF404B51), line = Color(0xFF56635F), lineSoft = Color(0xFF3D484D),
            ink = Color(0xFFEFE9D7), muted = Color(0xFFC0BEAD), faint = Color(0xFFA3AA9F), onAccent = Color(0xFF1E2A16),
            accent = Color(0xFFC0D79C), accentDim = Color(0xFF7D9564),
            good = Color(0xFF8CC79A), warn = Color(0xFFEBC874), bad = Color(0xFFF7A199),
        ),
    ),

    /** Arctic blue-grey with frost blue, after Nord */
    OCEAN(
        "ocean", "Fjord",
        Palette(
            abyss = Color(0xFF292E39), ground = Color(0xFF2E3440), surface = Color(0xFF363D4B),
            raised = Color(0xFF3D4555), field = Color(0xFF434C5E), line = Color(0xFF56607A), lineSoft = Color(0xFF404859),
            ink = Color(0xFFF0F2F6), muted = Color(0xFFC8CFDB), faint = Color(0xFFA3ADBD), onAccent = Color(0xFF1B2430),
            accent = Color(0xFFADD9E5), accentDim = Color(0xFF6698A7),
            good = Color(0xFFA9C493), warn = Color(0xFFEBCB8B), bad = Color(0xFFF5A6AD),
        ),
    ),

    /** Warm brown-grey with parchment text and gold, after Gruvbox; warnings orange and errors coral to stay apart from it */
    EMBER(
        "ember", "Ember",
        Palette(
            abyss = Color(0xFF1D2021), ground = Color(0xFF282828), surface = Color(0xFF32302F),
            raised = Color(0xFF3C3836), field = Color(0xFF45403D), line = Color(0xFF5A524C), lineSoft = Color(0xFF413B37),
            ink = Color(0xFFEBDBB2), muted = Color(0xFFBDAE93), faint = Color(0xFFA08F7E), onAccent = Color(0xFF2A1E05),
            accent = Color(0xFFFABD2F), accentDim = Color(0xFFB08A28),
            good = Color(0xFFB8BB26), warn = Color(0xFFFE8019), bad = Color(0xFFFC8572),
        ),
    ),

    /** True black for OLED screens, with Graphite's orange */
    MIDNIGHT(
        "midnight", "Midnight",
        Palette(
            abyss = Color(0xFF000000), ground = Color(0xFF000000), surface = Color(0xFF111113),
            raised = Color(0xFF18181B), field = Color(0xFF212125), line = Color(0xFF3A3A40), lineSoft = Color(0xFF242428),
            ink = Color(0xFFEDEDEF), muted = Color(0xFFA9AAB2), faint = Color(0xFF8C8D95), onAccent = Color(0xFF2A1405),
            accent = Color(0xFFF5A05A), accentDim = Color(0xFFA86A38),
            good = Color(0xFF74C98A), warn = Color(0xFFEDCB5C), bad = Color(0xFFF47B85),
        ),
    ),

    /** Black and white with a yellow accent and bright edges, for low vision */
    HIGH_CONTRAST(
        "contrast", "High Contrast",
        Palette(
            abyss = Color(0xFF000000), ground = Color(0xFF000000), surface = Color(0xFF000000),
            raised = Color(0xFF121212), field = Color(0xFF1C1C1C), line = Color(0xFFFFFFFF), lineSoft = Color(0xFF9A9A9A),
            ink = Color(0xFFFFFFFF), muted = Color(0xFFEDEDED), faint = Color(0xFFCFCFCF), onAccent = Color(0xFF000000),
            accent = Color(0xFFFFE14D), accentDim = Color(0xFFB8A030),
            good = Color(0xFF6BFF8A), warn = Color(0xFFFF9F45), bad = Color(0xFFFF7B7B),
        ),
    );

    companion object {
        val DEFAULT = SLATE

        fun fromKey(key: String?): Themes? = entries.firstOrNull { it.key == key }

        /** The 3.4 theme ids (ThemePrefs `selected_theme`) and their nearest 4.0 theme. */
        fun from34(id: Int): Themes = when (id) {
            1 -> MOSS          // Forest Green
            3 -> OCEAN         // Ocean Blue
            4, 5 -> EMBER      // Sunset Orange, Crimson Ember
            6 -> MIDNIGHT      // Midnight (AMOLED)
            7 -> DAYLIGHT      // Daylight
            else -> SLATE      // Alchemist Dark, Royal Purple
        }
    }
}

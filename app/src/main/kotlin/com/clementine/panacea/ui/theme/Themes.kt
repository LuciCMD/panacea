package com.clementine.panacea.ui.theme

import androidx.compose.ui.graphics.Color

/**
 * The themes to pick from. Every one keeps Slate's roles and meets its contrast (checked in
 * ThemesTest). Keys are stored; never rename one.
 */
enum class Themes(val key: String, val label: String, val palette: Palette) {
    /** Neutral slate with a lavender accent; the house theme and the default. */
    SLATE(
        "slate", "Slate",
        Palette(
            abyss = Color(0xFF1A1B21), ground = Color(0xFF1F2027), surface = Color(0xFF2A2B33),
            raised = Color(0xFF31323B), field = Color(0xFF3A3B46), line = Color(0xFF474855), lineSoft = Color(0xFF34353F),
            ink = Color(0xFFE4E4E8), muted = Color(0xFFA4A5AF), faint = Color(0xFF888995), onAccent = Color(0xFF1F1B2B),
            accent = Color(0xFFB39DF0), accentDim = Color(0xFF7D6AB5),
            good = Color(0xFF6CC77A), warn = Color(0xFFF5A742), bad = Color(0xFFF27671),
        ),
    ),

    /** Light, for bright rooms and daytime; a deeper violet keeps the accent readable on white. */
    DAYLIGHT(
        "daylight", "Daylight",
        Palette(
            abyss = Color(0xFFE8E8EE), ground = Color(0xFFF3F3F6), surface = Color(0xFFFFFFFF),
            raised = Color(0xFFF0F0F4), field = Color(0xFFE6E6EC), line = Color(0xFFC6C6D0), lineSoft = Color(0xFFDDDDE4),
            ink = Color(0xFF1C1D24), muted = Color(0xFF545560), faint = Color(0xFF6E6F7A), onAccent = Color(0xFFFFFFFF),
            accent = Color(0xFF5B3FC4), accentDim = Color(0xFFA898E6),
            good = Color(0xFF1F7A3A), warn = Color(0xFF9A5800), bad = Color(0xFFC02E2A),
            isLight = true,
        ),
    ),

    /** Green-grey with a sage accent. */
    MOSS(
        "moss", "Moss",
        Palette(
            abyss = Color(0xFF151A17), ground = Color(0xFF1A201C), surface = Color(0xFF232A25),
            raised = Color(0xFF29312B), field = Color(0xFF323B35), line = Color(0xFF434E46), lineSoft = Color(0xFF2E3730),
            ink = Color(0xFFE2E8E3), muted = Color(0xFFA2AEA6), faint = Color(0xFF7C8981), onAccent = Color(0xFF13201A),
            accent = Color(0xFF9DD3A8), accentDim = Color(0xFF5E9A6D),
            good = Color(0xFF7CC98A), warn = Color(0xFFE8B04E), bad = Color(0xFFF27671),
        ),
    ),

    /** Deep navy with a sea-blue accent. */
    OCEAN(
        "ocean", "Ocean",
        Palette(
            abyss = Color(0xFF121820), ground = Color(0xFF161D26), surface = Color(0xFF1E2732),
            raised = Color(0xFF24303C), field = Color(0xFF2C3946), line = Color(0xFF3D4C5B), lineSoft = Color(0xFF283542),
            ink = Color(0xFFE2E8EF), muted = Color(0xFF9FADBD), faint = Color(0xFF788698), onAccent = Color(0xFF0B1820),
            accent = Color(0xFF6FC6EA), accentDim = Color(0xFF3F86A8),
            good = Color(0xFF6CC77A), warn = Color(0xFFF5A742), bad = Color(0xFFF27671),
        ),
    ),

    /** Warm charcoal with a coral accent, after the launcher icon; warnings turn yellow and errors rose to stay apart from it. */
    EMBER(
        "ember", "Ember",
        Palette(
            abyss = Color(0xFF1D1917), ground = Color(0xFF221E1B), surface = Color(0xFF2C2723),
            raised = Color(0xFF332D28), field = Color(0xFF3D3630), line = Color(0xFF514840), lineSoft = Color(0xFF383029),
            ink = Color(0xFFEDE6E0), muted = Color(0xFFB5A99F), faint = Color(0xFF8C8178), onAccent = Color(0xFF2A1206),
            accent = Color(0xFFFF9A6B), accentDim = Color(0xFFB5633C),
            good = Color(0xFF7CC98A), warn = Color(0xFFF2C744), bad = Color(0xFFF46A83),
        ),
    ),

    /** True black for OLED screens, with Slate's accent. */
    MIDNIGHT(
        "midnight", "Midnight",
        Palette(
            abyss = Color(0xFF000000), ground = Color(0xFF000000), surface = Color(0xFF101013),
            raised = Color(0xFF17171B), field = Color(0xFF202026), line = Color(0xFF37373F), lineSoft = Color(0xFF222228),
            ink = Color(0xFFE4E4E8), muted = Color(0xFFA4A5AF), faint = Color(0xFF888995), onAccent = Color(0xFF1F1B2B),
            accent = Color(0xFFB39DF0), accentDim = Color(0xFF7D6AB5),
            good = Color(0xFF6CC77A), warn = Color(0xFFF5A742), bad = Color(0xFFF27671),
        ),
    ),

    /** Black and white with a yellow accent and bright edges, for low vision. */
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

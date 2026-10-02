package com.clementine.medtracker.utils;

import android.content.Context;
import android.content.SharedPreferences;
import androidx.appcompat.app.AppCompatActivity;
import com.clementine.medtracker.R;

/**
 * Manages application themes and color schemes
 * @author LuciCMD
 */
public class ThemeManager {
    private static final String PREFS_NAME = "ThemePrefs";
    private static final String THEME_KEY = "selected_theme";
    
    public enum Theme {
        ALCHEMIST_DARK(0, "Alchemist Dark", R.style.AppTheme),
        FOREST_GREEN(1, "Forest Green", R.style.Theme_ForestGreen),
        ROYAL_PURPLE(2, "Royal Purple", R.style.Theme_RoyalPurple),
        OCEAN_BLUE(3, "Ocean Blue", R.style.Theme_OceanBlue),
        SUNSET_ORANGE(4, "Sunset Orange", R.style.Theme_SunsetOrange),
        CRIMSON_EMBER(5, "Crimson Ember", R.style.Theme_CrimsonEmber),
        MIDNIGHT_AMOLED(6, "Midnight (AMOLED)", R.style.Theme_MidnightAmoled),
        DAYLIGHT(7, "Daylight", R.style.Theme_Daylight);
        
        private final int id;
        private final String displayName;
        private final int styleRes;
        
        Theme(int id, String displayName, int styleRes) {
            this.id = id;
            this.displayName = displayName;
            this.styleRes = styleRes;
        }
        
        public int getId() { return id; }
        public String getDisplayName() { return displayName; }
        public int getStyleRes() { return styleRes; }
        
        public static Theme fromId(int id) {
            for (Theme theme : values()) {
                if (theme.id == id) return theme;
            }
            return ALCHEMIST_DARK; // Default fallback
        }
    }
    
    private final SharedPreferences prefs;
    
    public ThemeManager(Context context) {
        this.prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE);
    }
    
    public void setTheme(Theme theme) {
        prefs.edit().putInt(THEME_KEY, theme.getId()).apply();
    }
    
    public Theme getCurrentTheme() {
        int themeId = prefs.getInt(THEME_KEY, Theme.ALCHEMIST_DARK.getId());
        return Theme.fromId(themeId);
    }
    
    public void applyTheme(AppCompatActivity activity) {
        Theme currentTheme = getCurrentTheme();
        activity.setTheme(currentTheme.getStyleRes());
    }
    
    public static String[] getThemeNames() {
        Theme[] themes = Theme.values();
        String[] names = new String[themes.length];
        for (int i = 0; i < themes.length; i++) {
            names[i] = themes[i].getDisplayName();
        }
        return names;
    }
}
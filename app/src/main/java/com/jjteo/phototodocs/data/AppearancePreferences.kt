package com.jjteo.phototodocs.data

import android.content.Context

/** App-wide appearance; kept separate from document content and exported files. */
class AppearancePreferences(context: Context) {
    private val preferences = context.applicationContext.getSharedPreferences("appearance", Context.MODE_PRIVATE)

    fun read(): ThemeMode = ThemeMode.fromPreference(preferences.getString("theme", null))

    fun write(mode: ThemeMode) {
        preferences.edit().putString("theme", mode.preferenceValue).apply()
    }
}

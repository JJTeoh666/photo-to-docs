package com.jjteo.phototodocs.data

import org.junit.Assert.assertEquals
import org.junit.Test

class ThemeModeTest {
    @Test fun storedChoicesRoundTrip() {
        ThemeMode.entries.forEach { assertEquals(it, ThemeMode.fromPreference(it.preferenceValue)) }
    }

    @Test fun missingOrUnknownPreferenceUsesSystem() {
        listOf(null, "", "old-theme", "DARK").forEach { assertEquals(ThemeMode.SYSTEM, ThemeMode.fromPreference(it)) }
    }

    @Test fun explicitChoicesOverridePhoneWhileSystemTracksIt() {
        for (phoneDark in listOf(false, true)) {
            assertEquals(false, ThemeMode.LIGHT.isDark(phoneDark))
            assertEquals(true, ThemeMode.DARK.isDark(phoneDark))
            assertEquals(phoneDark, ThemeMode.SYSTEM.isDark(phoneDark))
        }
    }
}

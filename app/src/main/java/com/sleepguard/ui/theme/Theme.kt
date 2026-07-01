// SPDX-License-Identifier: GPL-3.0-or-later
// Copyright (C) 2026 Olaf Drechsler
//
// This file is part of SleepGuard, free software licensed under the GNU General
// Public License v3.0 or later. See the LICENSE file in the project root.

package com.sleepguard.ui.theme

import android.os.Build
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.dynamicDarkColorScheme
import androidx.compose.material3.dynamicLightColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext

// Marken-Fallbackfarben (rot, passend zum SleepGuard-Kreis), falls keine
// dynamischen Systemfarben verfuegbar sind (Android < 12).
private val LightColors = lightColorScheme(
    primary = Color(0xFFC00011),
    onPrimary = Color(0xFFFFFFFF),
    primaryContainer = Color(0xFFFFDAD5),
    onPrimaryContainer = Color(0xFF410002),
    secondary = Color(0xFF775652),
    tertiary = Color(0xFF725B2E),
)

private val DarkColors = darkColorScheme(
    primary = Color(0xFFFFB4AB),
    onPrimary = Color(0xFF690005),
    primaryContainer = Color(0xFF93000A),
    onPrimaryContainer = Color(0xFFFFDAD5),
    secondary = Color(0xFFE7BDB6),
    tertiary = Color(0xFFE0C38C),
)

/**
 * Material-3-Theme der App. Nutzt auf Android 12+ die dynamischen Systemfarben
 * (Material You), sonst die rote Markenpalette. Hell/Dunkel folgt dem System.
 */
@Composable
fun SleepGuardTheme(
    darkTheme: Boolean = isSystemInDarkTheme(),
    dynamicColor: Boolean = true,
    content: @Composable () -> Unit
) {
    val context = LocalContext.current
    val colorScheme = when {
        dynamicColor && Build.VERSION.SDK_INT >= Build.VERSION_CODES.S ->
            if (darkTheme) dynamicDarkColorScheme(context) else dynamicLightColorScheme(context)
        darkTheme -> DarkColors
        else -> LightColors
    }
    MaterialTheme(
        colorScheme = colorScheme,
        content = content
    )
}

package com.netapp.marketplacescanner.ui.theme

import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color

private val Green = Color(0xFF2E7D32)
private val GreenDark = Color(0xFF1B5E20)
private val Amber = Color(0xFFFFB300)

private val LightColors = lightColorScheme(
    primary = Green,
    secondary = GreenDark,
    tertiary = Amber,
)

private val DarkColors = darkColorScheme(
    primary = Color(0xFF7CCF80),
    secondary = Color(0xFFA5D6A7),
    tertiary = Amber,
)

@Composable
fun MarketplaceTheme(
    darkTheme: Boolean = isSystemInDarkTheme(),
    content: @Composable () -> Unit,
) {
    MaterialTheme(
        colorScheme = if (darkTheme) DarkColors else LightColors,
        content = content,
    )
}

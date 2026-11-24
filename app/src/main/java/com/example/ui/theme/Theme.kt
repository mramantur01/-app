package com.example.ui.theme

import android.app.Activity
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Shapes
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.SideEffect
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.unit.dp
import androidx.core.view.WindowCompat

// Light theme: white and turquoise, no black (passenger side and registration).
private val LightColors = lightColorScheme(
    primary = KgTurquoise,
    onPrimary = UberWhite,
    primaryContainer = KgTurquoiseLight,
    onPrimaryContainer = KgInk,
    secondary = KgTurquoiseDark,
    onSecondary = UberWhite,
    secondaryContainer = KgTurquoiseLight,
    onSecondaryContainer = KgTurquoiseDark,
    tertiary = UberGreen,
    onTertiary = UberWhite,
    tertiaryContainer = UberGreen.copy(alpha = 0.12f),
    onTertiaryContainer = UberGreen,
    error = UberRed,
    onError = UberWhite,
    errorContainer = UberRed.copy(alpha = 0.12f),
    onErrorContainer = UberRed,
    background = UberWhite,
    onBackground = KgInk,
    surface = UberWhite,
    onSurface = KgInk,
    surfaceVariant = Color(0xFFEFF7F7),
    onSurfaceVariant = Color(0xFF51666A),
    outline = Color(0xFFCFE0E2),
    outlineVariant = Color(0xFFE3EFF0),
    inverseSurface = KgTurquoiseDark,
    inverseOnSurface = UberWhite,
    inversePrimary = KgTurquoiseLight
)

private val DarkColors = darkColorScheme(
    primary = UberWhite,
    onPrimary = UberBlack,
    primaryContainer = UberDarkSurfaceElevated,
    onPrimaryContainer = UberWhite,
    secondary = UberBlueDark,
    onSecondary = UberBlack,
    secondaryContainer = UberBlueDark.copy(alpha = 0.18f),
    onSecondaryContainer = UberBlueDark,
    tertiary = UberGreenDark,
    onTertiary = UberBlack,
    tertiaryContainer = UberGreenDark.copy(alpha = 0.18f),
    onTertiaryContainer = UberGreenDark,
    error = UberRedDark,
    onError = UberWhite,
    errorContainer = UberRedDark.copy(alpha = 0.18f),
    onErrorContainer = UberRedDark,
    background = UberDarkBg,
    onBackground = UberWhite,
    surface = UberDarkSurface,
    onSurface = UberWhite,
    surfaceVariant = UberDarkSurfaceElevated,
    onSurfaceVariant = UberDarkSecondaryText,
    outline = UberDarkBorder,
    outlineVariant = UberDarkSurfaceElevated
)

private val UberShapes = Shapes(
    small = RoundedCornerShape(10.dp),
    medium = RoundedCornerShape(16.dp),
    large = RoundedCornerShape(22.dp),
    extraLarge = RoundedCornerShape(28.dp)
)

@Composable
fun KgTaxiTheme(
    darkTheme: Boolean = isSystemInDarkTheme(),
    content: @Composable () -> Unit
) {
    val colorScheme = if (darkTheme) DarkColors else LightColors
    val view = LocalView.current
    if (!view.isInEditMode) {
        SideEffect {
            val window = (view.context as? Activity)?.window
            if (window != null) {
                WindowCompat.getInsetsController(window, view).apply {
                    isAppearanceLightStatusBars = !darkTheme
                    isAppearanceLightNavigationBars = !darkTheme
                }
            }
        }
    }

    MaterialTheme(
        colorScheme = colorScheme,
        typography = Typography,
        shapes = UberShapes,
        content = content
    )
}

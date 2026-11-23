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
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.unit.dp
import androidx.core.view.WindowCompat

private val LightColors = lightColorScheme(
    primary = UberBlack,
    onPrimary = UberWhite,
    primaryContainer = UberGrayLight,
    onPrimaryContainer = UberBlack,
    secondary = UberBlue,
    onSecondary = UberWhite,
    secondaryContainer = UberBlue.copy(alpha = 0.12f),
    onSecondaryContainer = UberBlue,
    tertiary = UberGreen,
    onTertiary = UberWhite,
    tertiaryContainer = UberGreen.copy(alpha = 0.12f),
    onTertiaryContainer = UberGreen,
    error = UberRed,
    onError = UberWhite,
    errorContainer = UberRed.copy(alpha = 0.12f),
    onErrorContainer = UberRed,
    background = UberWhite,
    onBackground = UberBlack,
    surface = UberWhite,
    onSurface = UberBlack,
    surfaceVariant = UberGrayLight,
    onSurfaceVariant = UberGrayText,
    outline = UberGrayBorder,
    outlineVariant = UberGraySurface
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

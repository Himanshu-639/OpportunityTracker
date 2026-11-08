package com.opportunitytracker.app.ui.theme

import android.os.Build
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext

object AppThemes {
    const val DYNAMIC = "dynamic"
    const val AMOLED = "amoled"
    const val CYBERPUNK = "cyberpunk"
    const val SLATE = "slate"
    const val FOREST = "forest"
}

// AMOLED Pitch Black Scheme
private val AmoledColorScheme = darkColorScheme(
    primary = AmoledPrimary,
    onPrimary = Color.Black,
    secondary = AmoledSecondary,
    onSecondary = Color.Black,
    tertiary = AmoledTertiary,
    background = AmoledBackground,
    onBackground = Color.White,
    surface = AmoledSurface,
    onSurface = Color.White,
    surfaceVariant = AmoledSurfaceVariant,
    onSurfaceVariant = Color(0xFFE2E8F0)
)

// Cyberpunk Neon Scheme
private val CyberpunkColorScheme = darkColorScheme(
    primary = CyberpunkPrimary,
    onPrimary = Color.Black,
    secondary = CyberpunkSecondary,
    onSecondary = Color.White,
    tertiary = CyberpunkTertiary,
    background = CyberpunkBackground,
    onBackground = Color(0xFFF8FAFC),
    surface = CyberpunkSurface,
    onSurface = Color(0xFFF8FAFC),
    surfaceVariant = CyberpunkSurfaceVariant,
    onSurfaceVariant = Color(0xFFCBD5E1)
)

// Minimal Slate Scheme
private val SlateColorScheme = darkColorScheme(
    primary = SlatePrimary,
    onPrimary = Color.Black,
    secondary = SlateSecondary,
    onSecondary = Color.Black,
    tertiary = SlateTertiary,
    background = SlateBackground,
    onBackground = Color(0xFFF8FAFC),
    surface = SlateSurface,
    onSurface = Color(0xFFF8FAFC),
    surfaceVariant = SlateSurfaceVariant,
    onSurfaceVariant = Color(0xFF94A3B8)
)

// Deep Forest Scheme
private val ForestColorScheme = darkColorScheme(
    primary = ForestPrimary,
    onPrimary = Color(0xFF06140D),
    secondary = ForestSecondary,
    onSecondary = Color(0xFF06140D),
    tertiary = ForestTertiary,
    background = ForestBackground,
    onBackground = Color(0xFFF0FDF4),
    surface = ForestSurface,
    onSurface = Color(0xFFF0FDF4),
    surfaceVariant = ForestSurfaceVariant,
    onSurfaceVariant = Color(0xFFBBF7D0)
)

private val DefaultDarkColorScheme = darkColorScheme(
    primary = Purple80,
    secondary = PurpleGrey80,
    tertiary = Pink80,
    background = DarkNavy,
    surface = SurfaceDark
)

private val DefaultLightColorScheme = lightColorScheme(
    primary = PrimaryBlue,
    secondary = PurpleGrey40,
    tertiary = Pink40
)

@Composable
fun OpportunityTrackerTheme(
    themeMode: String = AppThemes.DYNAMIC,
    darkTheme: Boolean = isSystemInDarkTheme(),
    content: @Composable () -> Unit
) {
    val context = LocalContext.current

    val colorScheme = when (themeMode) {
        AppThemes.AMOLED -> AmoledColorScheme
        AppThemes.CYBERPUNK -> CyberpunkColorScheme
        AppThemes.SLATE -> SlateColorScheme
        AppThemes.FOREST -> ForestColorScheme
        AppThemes.DYNAMIC -> {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
                if (darkTheme) dynamicDarkColorScheme(context) else dynamicLightColorScheme(context)
            } else {
                if (darkTheme) DefaultDarkColorScheme else DefaultLightColorScheme
            }
        }
        else -> {
            if (darkTheme) DefaultDarkColorScheme else DefaultLightColorScheme
        }
    }

    MaterialTheme(
        colorScheme = colorScheme,
        typography = Typography,
        content = content
    )
}

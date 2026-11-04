package com.notecraft.ui.theme

import android.app.Activity
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Typography
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.SideEffect
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.sp
import androidx.core.view.WindowCompat

val Plum: Color = Color(0xFF5B4B8A)
val PlumLight: Color = Color(0xFF8B7BBE)
val Apricot: Color = Color(0xFFE9A178)
val Paper: Color = Color(0xFFF7F3EC)
val PaperDark: Color = Color(0xFF141318)
val Ink: Color = Color(0xFF23222B)

/** Preset inks shown in the editor color strip. */
val InkPalette: List<Long> = listOf(
    0xFF23222BL, // ink black
    0xFF5B4B8AL, // plum
    0xFF2D6CDFL, // blue
    0xFF118AB2L, // teal
    0xFF06A77DL, // green
    0xFF7CB518L, // lime
    0xFFF4C430L, // gold
    0xFFFFD966L, // highlighter yellow
    0xFFF08A24L, // orange
    0xFFE4572EL, // vermilion
    0xFFEF476FL, // rose
    0xFFB05CE6L, // violet
    0xFF8D6E63L, // brown
)

private val LightColors = lightColorScheme(
    primary = Plum,
    onPrimary = Color.White,
    primaryContainer = Color(0xFFE7E0FF),
    onPrimaryContainer = Color(0xFF1D1438),
    secondary = Color(0xFF6D5E7A),
    onSecondary = Color.White,
    secondaryContainer = Color(0xFFF0E8F7),
    onSecondaryContainer = Color(0xFF261A32),
    tertiary = Color(0xFFC97B5A),
    onTertiary = Color.White,
    tertiaryContainer = Color(0xFFFFDBCB),
    onTertiaryContainer = Color(0xFF351004),
    background = Paper,
    onBackground = Ink,
    surface = Color(0xFFFFFBF5),
    onSurface = Ink,
    surfaceVariant = Color(0xFFEDE6DC),
    onSurfaceVariant = Color(0xFF4A453D),
    outline = Color(0xFFB9B0A4),
    outlineVariant = Color(0xFFDDD4C7),
    error = Color(0xFFBA1A1A),
    onError = Color.White,
    errorContainer = Color(0xFFFFDAD6),
    onErrorContainer = Color(0xFF410002),
)

private val DarkColors = darkColorScheme(
    primary = Color(0xFFCFC0FF),
    onPrimary = Color(0xFF2E2155),
    primaryContainer = Color(0xFF433570),
    onPrimaryContainer = Color(0xFFE7E0FF),
    secondary = Color(0xFFD5C4E4),
    onSecondary = Color(0xFF3B2D48),
    secondaryContainer = Color(0xFF52445F),
    onSecondaryContainer = Color(0xFFF0E8F7),
    tertiary = Color(0xFFE9A178),
    onTertiary = Color(0xFF55200A),
    tertiaryContainer = Color(0xFF793C21),
    onTertiaryContainer = Color(0xFFFFDBCB),
    background = PaperDark,
    onBackground = Color(0xFFE8E2E9),
    surface = Color(0xFF1C1B21),
    onSurface = Color(0xFFE8E2E9),
    surfaceVariant = Color(0xFF2B2932),
    onSurfaceVariant = Color(0xFFCAC4CF),
    outline = Color(0xFF958F99),
    outlineVariant = Color(0xFF48454F),
    error = Color(0xFFFFB4AB),
    onError = Color(0xFF690005),
    errorContainer = Color(0xFF93000A),
    onErrorContainer = Color(0xFFFFDAD6),
)

private val NoteCraftTypography = Typography(
    displaySmall = TextStyle(
        fontFamily = FontFamily.SansSerif,
        fontWeight = FontWeight.Bold,
        fontSize = 36.sp,
        lineHeight = 42.sp,
        letterSpacing = (-0.5).sp,
    ),
    headlineMedium = TextStyle(
        fontFamily = FontFamily.SansSerif,
        fontWeight = FontWeight.Bold,
        fontSize = 28.sp,
        lineHeight = 34.sp,
    ),
    titleLarge = TextStyle(
        fontFamily = FontFamily.SansSerif,
        fontWeight = FontWeight.SemiBold,
        fontSize = 20.sp,
        lineHeight = 26.sp,
    ),
    titleMedium = TextStyle(
        fontFamily = FontFamily.SansSerif,
        fontWeight = FontWeight.SemiBold,
        fontSize = 16.sp,
        lineHeight = 22.sp,
    ),
    bodyLarge = TextStyle(
        fontFamily = FontFamily.SansSerif,
        fontWeight = FontWeight.Normal,
        fontSize = 16.sp,
        lineHeight = 24.sp,
    ),
    bodyMedium = TextStyle(
        fontFamily = FontFamily.SansSerif,
        fontWeight = FontWeight.Normal,
        fontSize = 14.sp,
        lineHeight = 20.sp,
    ),
    labelLarge = TextStyle(
        fontFamily = FontFamily.SansSerif,
        fontWeight = FontWeight.Medium,
        fontSize = 14.sp,
        lineHeight = 20.sp,
    ),
)

@Composable
fun NoteCraftTheme(
    darkTheme: Boolean = isSystemInDarkTheme(),
    content: @Composable () -> Unit,
) {
    val colorScheme = if (darkTheme) DarkColors else LightColors
    val view = LocalView.current
    if (!view.isInEditMode) {
        SideEffect {
            val window = (view.context as? Activity)?.window ?: return@SideEffect
            WindowCompat.getInsetsController(window, view).apply {
                isAppearanceLightStatusBars = !darkTheme
                isAppearanceLightNavigationBars = !darkTheme
            }
        }
    }
    MaterialTheme(
        colorScheme = colorScheme,
        typography = NoteCraftTypography,
        content = content,
    )
}

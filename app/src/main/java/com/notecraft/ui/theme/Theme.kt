package com.notecraft.ui.theme

import android.app.Activity
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Shapes
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
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.core.view.WindowCompat

// ---------------------------------------------------------------------------
// Design tokens from DESIGN.md
// ---------------------------------------------------------------------------

val InkBlue: Color = Color(0xFF2563EB)
val InkBlueDeep: Color = Color(0xFF1D4ED8)
val FocusAzure: Color = Color(0xFFEFF6FF)
val HighlightYellow: Color = Color(0xFFFEF08A)
val HighlighterAmber: Color = Color(0xFFF59E0B)

val DeskBackdrop: Color = Color(0xFFF3F4F6)
val PaperSurface: Color = Color(0xFFFCFCFA)
val PaperIvory: Color = Color(0xFFFBF8F1)
val DarkSlate: Color = Color(0xFF1E293B)
val ChromeSurface: Color = Color(0xFFFFFFFF)
val DividerRule: Color = Color(0xFFE5E7EB)
val PaperLine: Color = Color(0xFFE2E8F0)

val InkBlackColor: Color = Color(0xFF0F172A)
val InkSlate: Color = Color(0xFF475569)
val InkMuted: Color = Color(0xFF94A3B8)

val EraserRed: Color = Color(0xFFEF4444)
val MintGreen: Color = Color(0xFF10B981)
val TagPurple: Color = Color(0xFF8B5CF6)

/** Preset inks shown in the editor color strip. */
val InkPalette: List<Long> = listOf(
    0xFF0F172AL, // ink black
    0xFF2563EBL, // ink blue
    0xFF1D4ED8L, // deep blue
    0xFF0891B2L, // cyan
    0xFF10B981L, // mint green
    0xFF15803DL, // forest
    0xFFF59E0BL, // amber
    0xFFFEF08AL, // highlighter yellow
    0xFFEF4444L, // eraser red
    0xFFEC4899L, // pink
    0xFF8B5CF6L, // tag purple
    0xFF78350FL, // brown
)

/** Notebook cover tones from the design specification. */
data class NotebookCoverTone(
    val name: String,
    val cover: Color,
    val coverDeep: Color,
    val ribbon: Color,
)

val NotebookCoverTones: List<NotebookCoverTone> = listOf(
    NotebookCoverTone(
        name = "Navy Leather",
        cover = Color(0xFF1E3A8A),
        coverDeep = Color(0xFF172554),
        ribbon = Color(0xFFF59E0B),
    ),
    NotebookCoverTone(
        name = "Terracotta",
        cover = Color(0xFFC2410C),
        coverDeep = Color(0xFF7C2D12),
        ribbon = Color(0xFFFEF08A),
    ),
    NotebookCoverTone(
        name = "Forest Sage",
        cover = Color(0xFF15803D),
        coverDeep = Color(0xFF14532D),
        ribbon = Color(0xFFFDE68A),
    ),
    NotebookCoverTone(
        name = "Pastel Lavender",
        cover = Color(0xFFDDD6FE),
        coverDeep = Color(0xFFA78BFA),
        ribbon = Color(0xFF2563EB),
    ),
    NotebookCoverTone(
        name = "Charcoal Minimal",
        cover = Color(0xFF334155),
        coverDeep = Color(0xFF0F172A),
        ribbon = Color(0xFFEF4444),
    ),
)

fun notebookToneFor(id: String): NotebookCoverTone =
    NotebookCoverTones[(id.hashCode() and Int.MAX_VALUE) % NotebookCoverTones.size]

// ---------------------------------------------------------------------------
// Theme
// ---------------------------------------------------------------------------

private val LightColors = lightColorScheme(
    primary = InkBlue,
    onPrimary = Color.White,
    primaryContainer = FocusAzure,
    onPrimaryContainer = InkBlueDeep,
    secondary = HighlighterAmber,
    onSecondary = InkBlackColor,
    secondaryContainer = HighlightYellow,
    onSecondaryContainer = Color(0xFF713F12),
    tertiary = TagPurple,
    onTertiary = Color.White,
    tertiaryContainer = Color(0xFFF3E8FF),
    onTertiaryContainer = Color(0xFF4C1D95),
    background = DeskBackdrop,
    onBackground = InkBlackColor,
    surface = ChromeSurface,
    onSurface = InkBlackColor,
    surfaceVariant = Color(0xFFF1F5F9),
    onSurfaceVariant = InkSlate,
    surfaceTint = InkBlue,
    surfaceContainerLowest = Color(0xFFFFFFFF),
    surfaceContainerLow = Color(0xFFFCFCFA),
    surfaceContainer = Color(0xFFF8F7F4),
    surfaceContainerHigh = Color(0xFFF5F3EF),
    surfaceContainerHighest = Color(0xFFEFECE7),
    outline = Color(0xFFCBD5E1),
    outlineVariant = DividerRule,
    error = EraserRed,
    onError = Color.White,
    errorContainer = Color(0xFFFEE2E2),
    onErrorContainer = Color(0xFF7F1D1D),
)

private val DarkColors = darkColorScheme(
    primary = Color(0xFF60A5FA),
    onPrimary = Color(0xFF0B2545),
    primaryContainer = Color(0xFF1E3A8A),
    onPrimaryContainer = Color(0xFFDBEAFE),
    secondary = Color(0xFFFDE047),
    onSecondary = Color(0xFF3F2D00),
    secondaryContainer = Color(0xFF713F12),
    onSecondaryContainer = HighlightYellow,
    tertiary = Color(0xFFA78BFA),
    onTertiary = Color(0xFF2E1065),
    tertiaryContainer = Color(0xFF4C1D95),
    onTertiaryContainer = Color(0xFFF3E8FF),
    background = DarkSlate,
    onBackground = Color(0xFFF1F5F9),
    surface = Color(0xFF1E293B),
    onSurface = Color(0xFFF1F5F9),
    surfaceVariant = Color(0xFF334155),
    onSurfaceVariant = Color(0xFFCBD5E1),
    surfaceTint = Color(0xFF60A5FA),
    surfaceContainerLowest = Color(0xFF0F172A),
    surfaceContainerLow = Color(0xFF1E293B),
    surfaceContainer = Color(0xFF243247),
    surfaceContainerHigh = Color(0xFF2B3A52),
    surfaceContainerHighest = Color(0xFF334155),
    outline = Color(0xFF475569),
    outlineVariant = Color(0xFF334155),
    error = Color(0xFFFCA5A5),
    onError = Color(0xFF450A0A),
    errorContainer = Color(0xFF7F1D1D),
    onErrorContainer = Color(0xFFFEE2E2),
)

private val NoteCraftTypography = Typography(
    displaySmall = TextStyle(
        fontFamily = FontFamily.SansSerif,
        fontWeight = FontWeight.SemiBold,
        fontSize = 32.sp,
        lineHeight = 38.sp,
        letterSpacing = (-0.5).sp,
    ),
    headlineMedium = TextStyle(
        fontFamily = FontFamily.SansSerif,
        fontWeight = FontWeight.SemiBold,
        fontSize = 24.sp,
        lineHeight = 30.sp,
        letterSpacing = (-0.3).sp,
    ),
    headlineSmall = TextStyle(
        fontFamily = FontFamily.SansSerif,
        fontWeight = FontWeight.SemiBold,
        fontSize = 20.sp,
        lineHeight = 26.sp,
    ),
    titleLarge = TextStyle(
        fontFamily = FontFamily.SansSerif,
        fontWeight = FontWeight.SemiBold,
        fontSize = 18.sp,
        lineHeight = 24.sp,
        letterSpacing = (-0.1).sp,
    ),
    titleMedium = TextStyle(
        fontFamily = FontFamily.SansSerif,
        fontWeight = FontWeight.Medium,
        fontSize = 15.sp,
        lineHeight = 21.sp,
    ),
    titleSmall = TextStyle(
        fontFamily = FontFamily.SansSerif,
        fontWeight = FontWeight.Medium,
        fontSize = 13.sp,
        lineHeight = 18.sp,
    ),
    bodyLarge = TextStyle(
        fontFamily = FontFamily.SansSerif,
        fontWeight = FontWeight.Normal,
        fontSize = 15.sp,
        lineHeight = 21.sp,
    ),
    bodyMedium = TextStyle(
        fontFamily = FontFamily.SansSerif,
        fontWeight = FontWeight.Normal,
        fontSize = 14.sp,
        lineHeight = 20.sp,
    ),
    bodySmall = TextStyle(
        fontFamily = FontFamily.SansSerif,
        fontWeight = FontWeight.Normal,
        fontSize = 12.sp,
        lineHeight = 16.sp,
    ),
    labelLarge = TextStyle(
        fontFamily = FontFamily.SansSerif,
        fontWeight = FontWeight.Medium,
        fontSize = 14.sp,
        lineHeight = 20.sp,
    ),
    labelMedium = TextStyle(
        fontFamily = FontFamily.SansSerif,
        fontWeight = FontWeight.Medium,
        fontSize = 12.sp,
        lineHeight = 16.sp,
        letterSpacing = 0.2.sp,
    ),
    labelSmall = TextStyle(
        fontFamily = FontFamily.SansSerif,
        fontWeight = FontWeight.Bold,
        fontSize = 10.sp,
        lineHeight = 14.sp,
        letterSpacing = 0.4.sp,
    ),
)

private val NoteCraftShapes = Shapes(
    extraSmall = RoundedCornerShape(6.dp),
    small = RoundedCornerShape(8.dp),
    medium = RoundedCornerShape(12.dp),
    large = RoundedCornerShape(16.dp),
    extraLarge = RoundedCornerShape(24.dp),
)

object NoteCraftSpacing {
    val xs = 4.dp
    val sm = 8.dp
    val md = 12.dp
    val lg = 16.dp
    val xl = 24.dp
}

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
        shapes = NoteCraftShapes,
        content = content,
    )
}

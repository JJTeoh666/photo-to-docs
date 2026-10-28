package com.jjteo.phototodocs.ui

import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.activity.ComponentActivity
import androidx.activity.compose.LocalActivity
import androidx.activity.SystemBarStyle
import androidx.activity.enableEdgeToEdge
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.ExperimentalTextApi
import androidx.compose.ui.text.font.Font
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontVariation
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.jjteo.phototodocs.R

@OptIn(ExperimentalTextApi::class)
private val Manrope = FontFamily(
    Font(R.font.manrope, FontWeight.Normal, variationSettings = FontVariation.Settings(FontVariation.weight(400))),
    Font(R.font.manrope, FontWeight.Medium, variationSettings = FontVariation.Settings(FontVariation.weight(500))),
    Font(R.font.manrope, FontWeight.SemiBold, variationSettings = FontVariation.Settings(FontVariation.weight(600))),
    Font(R.font.manrope, FontWeight.Bold, variationSettings = FontVariation.Settings(FontVariation.weight(700))),
)

private val LightColors = lightColorScheme(
    primary = Color(0xFF184CB8), onPrimary = Color.White,
    primaryContainer = Color(0xFFE8F0FA), onPrimaryContainer = Color(0xFF173149),
    secondary = Color(0xFF087B85), onSecondary = Color.White,
    secondaryContainer = Color(0xFFE8F0FA), onSecondaryContainer = Color(0xFF173149),
    background = Color(0xFFF4F8FC), onBackground = Color(0xFF173149),
    surface = Color.White, onSurface = Color(0xFF173149),
    surfaceVariant = Color(0xFFE8F0FA), onSurfaceVariant = Color(0xFF52677B),
    surfaceContainerLowest = Color.White, surfaceContainerLow = Color(0xFFF4F8FC),
    surfaceContainer = Color(0xFFE8F0FA), surfaceContainerHigh = Color(0xFFDFE9F5),
    outline = Color(0xFF718298), outlineVariant = Color(0xFFD8E3ED),
    error = Color(0xFFB42335), errorContainer = Color(0xFFFFE9EC), onErrorContainer = Color(0xFF7B1725),
)

private val DarkColors = darkColorScheme(
    primary = Color(0xFFA8D8E6), onPrimary = Color(0xFF143947),
    primaryContainer = Color(0xFF2B4150), onPrimaryContainer = Color(0xFFEDF3F8),
    secondary = Color(0xFFA8D8E6), onSecondary = Color(0xFF143947),
    secondaryContainer = Color(0xFF2B4150), onSecondaryContainer = Color(0xFFEDF3F8),
    background = Color(0xFF18212B), onBackground = Color(0xFFEDF3F8),
    surface = Color(0xFF23303E), onSurface = Color(0xFFEDF3F8),
    surfaceVariant = Color(0xFF2B4150), onSurfaceVariant = Color(0xFFBBC8D5),
    surfaceContainerLowest = Color(0xFF18212B), surfaceContainerLow = Color(0xFF1E2935),
    surfaceContainer = Color(0xFF2B4150), surfaceContainerHigh = Color(0xFF324757),
    outline = Color(0xFF8DA4B8), outlineVariant = Color(0xFF425468),
    error = Color(0xFFFFB1BA), errorContainer = Color(0xFF52232D), onErrorContainer = Color(0xFFFFD9DF),
)

internal data class WorkspaceAccent(val start: Color, val end: Color)
private val OceanAccent = WorkspaceAccent(Color(0xFF184CB8), Color(0xFF087B85))
private val GraphiteAccent = WorkspaceAccent(Color(0xFF234D69), Color(0xFF316C76))
internal val LocalWorkspaceAccent = staticCompositionLocalOf { OceanAccent }

private fun type(size: Int, line: Int, weight: FontWeight = FontWeight.Normal) = TextStyle(
    fontFamily = Manrope, fontSize = size.sp, lineHeight = line.sp, fontWeight = weight,
    letterSpacing = 0.sp,
)

@Composable
internal fun PhotoToDocsTheme(darkTheme: Boolean = isSystemInDarkTheme(), content: @Composable () -> Unit) {
    MaterialTheme(
        colorScheme = if (darkTheme) DarkColors else LightColors,
        typography = Typography(
            displaySmall = type(36, 44, FontWeight.Bold),
            headlineLarge = type(28, 36, FontWeight.Bold),
            headlineMedium = type(26, 34, FontWeight.Bold),
            headlineSmall = type(23, 31, FontWeight.SemiBold),
            titleLarge = type(20, 28, FontWeight.Bold),
            titleMedium = type(16, 24, FontWeight.SemiBold),
            titleSmall = type(14, 21, FontWeight.SemiBold),
            bodyLarge = type(16, 26), bodyMedium = type(14, 21), bodySmall = type(12, 18),
            labelLarge = type(14, 20, FontWeight.SemiBold),
            labelMedium = type(12, 18, FontWeight.SemiBold), labelSmall = type(12, 18, FontWeight.Medium),
        ),
        shapes = Shapes(
            extraSmall = RoundedCornerShape(6.dp), small = RoundedCornerShape(12.dp),
            medium = RoundedCornerShape(16.dp), large = RoundedCornerShape(20.dp),
            extraLarge = RoundedCornerShape(24.dp),
        ), content = {
            CompositionLocalProvider(LocalWorkspaceAccent provides if (darkTheme) GraphiteAccent else OceanAccent, content = content)
        },
    )
}

/** A forced app theme must also update Android's status/navigation icon contrast. */
@Composable
internal fun ThemeSystemBars(darkTheme: Boolean) {
    val activity = LocalActivity.current as? ComponentActivity
    val background = MaterialTheme.colorScheme.background.toArgb()
    DisposableEffect(activity, darkTheme, background) {
        activity?.enableEdgeToEdge(
            statusBarStyle = SystemBarStyle.auto(android.graphics.Color.TRANSPARENT, android.graphics.Color.TRANSPARENT) { darkTheme },
            navigationBarStyle = SystemBarStyle.auto(background, background) { darkTheme },
        )
        onDispose { }
    }
}

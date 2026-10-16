package tech.deanju.messenger

import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalLayoutDirection
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.*
import androidx.compose.ui.unit.LayoutDirection
import androidx.compose.ui.unit.sp

val MiyarBlue = Color(0xFF0584FE)
val Cairo = FontFamily(Font(R.font.cairo_regular), Font(R.font.cairo_semibold, FontWeight.SemiBold), Font(R.font.cairo_bold, FontWeight.Bold))
private val day = lightColorScheme(primary = MiyarBlue, onPrimary = Color.White, background = Color.White, surface = Color.White,
    surfaceVariant = Color(0xFFF1F2F4), onSurfaceVariant = Color(0xFF646A73), secondaryContainer = Color(0xFFE8F2FF))
private val night = darkColorScheme(primary = MiyarBlue, onPrimary = Color.White, background = Color(0xFF101114), surface = Color(0xFF101114),
    surfaceVariant = Color(0xFF272A30), onSurfaceVariant = Color(0xFFB4B8C0), secondaryContainer = Color(0xFF17314F))
private fun type(size: Int, line: Int, weight: FontWeight = FontWeight.Normal) = TextStyle(fontFamily = Cairo, fontSize = size.sp, lineHeight = line.sp, fontWeight = weight)
private val typography = Typography(
    displayLarge = type(40, 54, FontWeight.Bold), displayMedium = type(36, 48, FontWeight.Bold), displaySmall = type(32, 44, FontWeight.Bold),
    headlineLarge = type(30, 42, FontWeight.Bold), headlineMedium = type(26, 38, FontWeight.Bold), headlineSmall = type(24, 36, FontWeight.Bold),
    titleLarge = type(21, 32, FontWeight.Bold), titleMedium = type(17, 26, FontWeight.SemiBold), titleSmall = type(15, 24, FontWeight.SemiBold),
    bodyLarge = type(16, 26), bodyMedium = type(14, 23), bodySmall = type(12, 20),
    labelLarge = type(14, 22, FontWeight.SemiBold), labelMedium = type(12, 20, FontWeight.SemiBold), labelSmall = type(11, 18))

@Composable fun MiyarTheme(appearance: String = "system", content: @Composable () -> Unit) {
    val dark = appearance == "dark" || (appearance == "system" && isSystemInDarkTheme())
    CompositionLocalProvider(LocalLayoutDirection provides LayoutDirection.Rtl) { MaterialTheme(colorScheme = if (dark) night else day, typography = typography, content = content) }
}

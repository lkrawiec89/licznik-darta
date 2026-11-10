package pl.licznikdarta

import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color

val DartGreen = Color(0xFF35C759)
val DartRed = Color(0xFFE53935)
val DropdownGreen = Color(0xFF6BAA61)
val DropdownRed = Color(0xFFD2483E)
val DartOrange = Color(0xFFFF9500)
val DartDarkBg = Color(0xFF0D0D0D)
val DartCardBg = Color(0xFF242424)
val DartKeypadBg = Color(0xFF48484A)
val DartSubtle = Color(0xFF8E8E93)
val DartSurfaceVariant = Color(0xFF1C1C1E)
val DartOutline = Color(0xFF333333)

// Backward compatibility aliases
val BoardGreen = DartGreen
val BoardRed = DartRed
val BoardOrange = DartOrange
val Cream = Color(0xFFFFFFFF)
val DarkBg = DartDarkBg
val CardBg = DartCardBg
val Accent = DartGreen

private val Scheme = darkColorScheme(
    primary = DartGreen,
    onPrimary = Color.Black,
    secondary = DartGreen,
    tertiary = DartRed,
    background = DartDarkBg,
    onBackground = Color.White,
    surface = DartCardBg,
    onSurface = Color.White,
    surfaceVariant = DartSurfaceVariant,
    onSurfaceVariant = DartSubtle,
    error = DartRed,
    outline = DartOutline
)

@Composable
fun LicznikDartaTheme(content: @Composable () -> Unit) {
    MaterialTheme(colorScheme = Scheme, content = content)
}

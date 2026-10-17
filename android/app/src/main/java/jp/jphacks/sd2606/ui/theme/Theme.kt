package jp.jphacks.sd2606.ui.theme

import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color

private val Teal = Color(0xFF00796B)
private val TealLight = Color(0xFF4DB6AC)
private val Amber = Color(0xFFFFA000)

private val LightColors = lightColorScheme(
    primary = Teal,
    secondary = Amber,
    tertiary = Color(0xFF5C6BC0),
)

private val DarkColors = darkColorScheme(
    primary = TealLight,
    secondary = Amber,
    tertiary = Color(0xFF9FA8DA),
)

@Composable
fun AppTheme(darkTheme: Boolean = isSystemInDarkTheme(), content: @Composable () -> Unit) {
    MaterialTheme(colorScheme = if (darkTheme) DarkColors else LightColors, content = content)
}

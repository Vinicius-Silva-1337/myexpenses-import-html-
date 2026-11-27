package br.com.feira.nfce.ui

import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.FilterChip
import androidx.compose.material3.FilterChipDefaults
import androidx.compose.material3.Shapes
import androidx.compose.material3.Typography
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp

object ExpensePalette {
    val Purple = Color(0xFF7138F2)
    val PurpleDark = Color(0xFF5224C9)
    val PurpleLight = Color(0xFFEEE8FF)
    val Background = Color(0xFFF5F5FA)
    val Ink = Color(0xFF1D1D27)
    val Muted = Color(0xFF686876)
    val Border = Color(0xFFECECF2)
    val Green = Color(0xFF1F8F45)
    val GreenLight = Color(0xFFE6F6EC)
    val Red = Color(0xFFD93A3A)
    val RedLight = Color(0xFFFDEAEA)
    val Cyan = Color(0xFF06B6D4)
    val Magenta = Color(0xFFD946EF)
    val PurpleGradientStart = Color(0xFF6632E9)
    val PurpleGradientEnd = Color(0xFF8148F4)
    val Chart = listOf(Purple, Color(0xFF06B6D4), Color(0xFFF59E0B), Color(0xFF22C55E), Color(0xFFEC4899), Color(0xFF64748B))
}

private val ExpensesColors = lightColorScheme(
    primary = ExpensePalette.Purple,
    onPrimary = Color.White,
    primaryContainer = ExpensePalette.PurpleLight,
    onPrimaryContainer = ExpensePalette.PurpleDark,
    secondary = ExpensePalette.Cyan,
    onSecondary = Color.White,
    background = ExpensePalette.Background,
    onBackground = ExpensePalette.Ink,
    surface = Color.White,
    onSurface = ExpensePalette.Ink,
    surfaceVariant = Color(0xFFF0EFF5),
    onSurfaceVariant = ExpensePalette.Muted,
    outline = ExpensePalette.Border,
    error = ExpensePalette.Red,
    errorContainer = ExpensePalette.RedLight,
    onErrorContainer = ExpensePalette.Red
)

private val ExpensesShapes = Shapes(
    extraSmall = RoundedCornerShape(10.dp),
    small = RoundedCornerShape(12.dp),
    medium = RoundedCornerShape(20.dp),
    large = RoundedCornerShape(20.dp),
    extraLarge = RoundedCornerShape(24.dp)
)

@Composable
fun MyExpensesTheme(content: @Composable () -> Unit) {
    MaterialTheme(colorScheme = ExpensesColors, typography = Typography(), shapes = ExpensesShapes, content = content)
}

@Composable
fun MyExpensesFilterChip(selected: Boolean, onClick: () -> Unit, label: @Composable () -> Unit) {
    FilterChip(
        selected = selected,
        onClick = onClick,
        label = label,
        colors = FilterChipDefaults.filterChipColors(
            containerColor = Color.White,
            labelColor = ExpensePalette.Muted,
            selectedContainerColor = ExpensePalette.Purple,
            selectedLabelColor = Color.White
        )
    )
}

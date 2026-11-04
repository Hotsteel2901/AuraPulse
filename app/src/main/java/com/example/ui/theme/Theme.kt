package com.example.ui.theme

import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialExpressiveTheme
import androidx.compose.material3.ExperimentalMaterial3ExpressiveApi
import androidx.compose.material3.MotionScheme
import androidx.compose.material3.Shapes
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.Typography
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp

private val AuraColors = darkColorScheme(
  primary = Color(0xFFC4E9AE), onPrimary = Color(0xFF18331E),
  primaryContainer = Color(0xFF304A36), onPrimaryContainer = Color(0xFFE1F7D2),
  secondary = Color(0xFFB8D3C0), onSecondary = Color(0xFF24372B),
  secondaryContainer = Color(0xFF34483C), onSecondaryContainer = Color(0xFFD4E8D6),
  tertiary = Color(0xFFF0C898), onTertiary = Color(0xFF483119),
  background = Color(0xFF0F1512), onBackground = Color(0xFFF0F3E9),
  surface = Color(0xFF151D18), onSurface = Color(0xFFF0F3E9),
  surfaceVariant = Color(0xFF29362D), onSurfaceVariant = Color(0xFFBFCBBF),
  outline = Color(0xFF788B7A), outlineVariant = Color(0xFF36463B),
  error = Color(0xFFFFB4AB),
)

@OptIn(ExperimentalMaterial3ExpressiveApi::class)
@Composable
fun AuraPulseTheme(content: @Composable () -> Unit) {
  MaterialExpressiveTheme(
    motionScheme = MotionScheme.expressive(),
    colorScheme = AuraColors,
    typography = Typography(),
    shapes = Shapes(
      extraSmall = RoundedCornerShape(8.dp), small = RoundedCornerShape(12.dp),
      medium = RoundedCornerShape(18.dp), large = RoundedCornerShape(24.dp),
      extraLarge = RoundedCornerShape(30.dp),
    ),
    content = content,
  )
}

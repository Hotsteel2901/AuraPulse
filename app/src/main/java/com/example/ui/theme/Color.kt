package com.example.ui.theme

import androidx.compose.ui.graphics.Color

val ObsidianBackground = Color(0xFF0A0D14)
val ObsidianSurface = Color(0xFF121723)
val ObsidianSurfaceVariant = Color(0xFF1C2334)
val ObsidianSurfaceHigh = Color(0xFF242D42)

val ElectricCyan = Color(0xFF00F5D4)
val NeonViolet = Color(0xFF9B5DE5)
val WarmAmber = Color(0xFFFF9F1C)
val CoralPulse = Color(0xFFF72585)
val CustomApiEmerald = Color(0xFF10B981)
val SubsonicOrange = Color(0xFFFF6B35)
val GlobalSkyBlue = Color(0xFF38BDF8)

val TextPrimary = Color(0xFFF4F7FF)
val TextSecondary = Color(0xFFA7B3CC)
val TextMuted = Color(0xFF6C7A96)

fun parseHexColor(hex: String, fallback: Color = ElectricCyan): Color {
  return try {
    val clean = hex.trim().removePrefix("#")
    val longVal = clean.toLong(16)
    when (clean.length) {
      6 -> Color(0xFF000000 or longVal)
      8 -> Color(longVal)
      else -> fallback
    }
  } catch (_: Exception) {
    fallback
  }
}

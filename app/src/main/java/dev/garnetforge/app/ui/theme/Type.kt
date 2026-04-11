package dev.garnetforge.app.ui.theme

import androidx.compose.material3.Typography
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.sp

val GarnetTypography = Typography(
    titleLarge  = TextStyle(fontWeight = FontWeight.Bold,   fontSize = 20.sp, lineHeight = 26.sp),
    titleMedium = TextStyle(fontWeight = FontWeight.SemiBold, fontSize = 16.sp),
    bodyLarge   = TextStyle(fontWeight = FontWeight.Normal, fontSize = 14.sp),
    bodyMedium  = TextStyle(fontWeight = FontWeight.Normal, fontSize = 12.sp),
    labelSmall  = TextStyle(fontWeight = FontWeight.Medium, fontSize = 10.sp, letterSpacing = 0.5.sp),
)

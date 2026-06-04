package com.musicplayer.app.ui.theme

import androidx.compose.material3.Typography
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.sp

fun getTypography(fontWeightScale: Float = 1f): Typography {
    fun scaleWeight(weight: FontWeight): FontWeight {
        val scaledValue = (weight.weight * fontWeightScale).toInt().coerceIn(1, 1000)
        return FontWeight(scaledValue)
    }

    val defaultFontFamily = FontFamily.SansSerif

    return Typography(
        displayLarge = TextStyle(
            fontFamily = defaultFontFamily,
            fontWeight = scaleWeight(FontWeight.Normal),
            fontSize = 57.sp,
            lineHeight = 64.sp,
            letterSpacing = (-0.25).sp,
            color = SoftCream
        ),
        displayMedium = TextStyle(
            fontFamily = defaultFontFamily,
            fontWeight = scaleWeight(FontWeight.Normal),
            fontSize = 45.sp,
            lineHeight = 52.sp,
            letterSpacing = 0.sp
        ),
        displaySmall = TextStyle(
            fontFamily = defaultFontFamily,
            fontWeight = scaleWeight(FontWeight.Normal),
            fontSize = 36.sp,
            lineHeight = 44.sp,
            letterSpacing = 0.sp
        ),
        headlineLarge = TextStyle(
            fontFamily = defaultFontFamily,
            fontWeight = scaleWeight(FontWeight.Normal),
            fontSize = 32.sp,
            lineHeight = 40.sp,
            letterSpacing = 0.sp
        ),
        headlineMedium = TextStyle(
            fontFamily = defaultFontFamily,
            fontWeight = scaleWeight(FontWeight.Bold),
            fontSize = 28.sp,
            lineHeight = 36.sp,
            letterSpacing = 0.sp,
            color = SoftCream
        ),
        headlineSmall = TextStyle(
            fontFamily = defaultFontFamily,
            fontWeight = scaleWeight(FontWeight.Normal),
            fontSize = 24.sp,
            lineHeight = 32.sp,
            letterSpacing = 0.sp
        ),
        titleLarge = TextStyle(
            fontFamily = defaultFontFamily,
            fontWeight = scaleWeight(FontWeight.SemiBold),
            fontSize = 22.sp,
            lineHeight = 28.sp,
            letterSpacing = 0.sp,
            color = SoftCream
        ),
        titleMedium = TextStyle(
            fontFamily = defaultFontFamily,
            fontWeight = scaleWeight(FontWeight.Bold),
            fontSize = 20.sp,
            lineHeight = 24.sp,
            letterSpacing = 0.15.sp,
            color = SoftCream
        ),
        titleSmall = TextStyle(
            fontFamily = defaultFontFamily,
            fontWeight = scaleWeight(FontWeight.Medium),
            fontSize = 14.sp,
            lineHeight = 20.sp,
            letterSpacing = 0.1.sp
        ),
        bodyLarge = TextStyle(
            fontFamily = defaultFontFamily,
            fontWeight = scaleWeight(FontWeight.Normal),
            fontSize = 20.sp,
            lineHeight = 34.sp,
            letterSpacing = 0.5.sp,
            color = SoftCream
        ),
        bodyMedium = TextStyle(
            fontFamily = defaultFontFamily,
            fontWeight = scaleWeight(FontWeight.Normal),
            fontSize = 14.sp,
            lineHeight = 20.sp,
            letterSpacing = 0.25.sp,
            color = MutedCream
        ),
        bodySmall = TextStyle(
            fontFamily = defaultFontFamily,
            fontWeight = scaleWeight(FontWeight.Normal),
            fontSize = 12.sp,
            lineHeight = 16.sp,
            letterSpacing = 0.4.sp
        ),
        labelLarge = TextStyle(
            fontFamily = defaultFontFamily,
            fontWeight = scaleWeight(FontWeight.Medium),
            fontSize = 14.sp,
            lineHeight = 20.sp,
            letterSpacing = 0.1.sp
        ),
        labelMedium = TextStyle(
            fontFamily = defaultFontFamily,
            fontWeight = scaleWeight(FontWeight.Medium),
            fontSize = 12.sp,
            lineHeight = 16.sp,
            letterSpacing = 0.5.sp,
            color = SilverGrey
        ),
        labelSmall = TextStyle(
            fontFamily = defaultFontFamily,
            fontWeight = scaleWeight(FontWeight.Medium),
            fontSize = 11.sp,
            lineHeight = 16.sp,
            letterSpacing = 0.5.sp
        )
    )
}

val Typography = getTypography()

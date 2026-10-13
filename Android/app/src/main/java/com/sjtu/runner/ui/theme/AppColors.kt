package com.sjtu.runner.ui.theme

import androidx.compose.runtime.Immutable
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.graphics.Color

/** 品牌化色调层，补足 Material 3 1.1 缺少的 surfaceContainer 层级。 */
@Immutable
data class AppColors(
    val card: Color,
    val cardHigh: Color,
    val cardLow: Color,
    val console: Color,
    val consoleOn: Color,
    val outlineStrong: Color,
    val success: Color,
    val warning: Color,
    val gradientStart: Color,
    val gradientEnd: Color
)

val LocalAppColors = staticCompositionLocalOf {
    AppColors(
        card = SurfaceLight,
        cardHigh = SurfaceHighLight,
        cardLow = SurfaceLowLight,
        console = ConsoleLight,
        consoleOn = ConsoleOnLight,
        outlineStrong = SurfaceVariantLight,
        success = SuccessGreen,
        warning = WarningYellow,
        gradientStart = Color(0xFFC9E7FA),
        gradientEnd = SurfaceLight
    )
}

val LightAppColors = AppColors(
    card = SurfaceHighLight,
    cardHigh = SurfaceHighLight,
    cardLow = SurfaceLowLight,
    console = ConsoleLight,
    consoleOn = ConsoleOnLight,
    outlineStrong = SurfaceVariantLight,
    success = SuccessGreen,
    warning = WarningYellow,
    gradientStart = Color(0xFFD6ECFB),
    gradientEnd = SurfaceLight
)

val DarkAppColors = AppColors(
    card = SurfaceHighDark,
    cardHigh = SurfaceHighDark,
    cardLow = SurfaceLowDark,
    console = ConsoleDark,
    consoleOn = ConsoleOnDark,
    outlineStrong = SurfaceVariantDark,
    success = SuccessGreenDark,
    warning = WarningYellow,
    gradientStart = Color(0xFF12293A),
    gradientEnd = SurfaceDark
)

package com.sjtu.runner.ui.theme

import android.app.Activity
import android.graphics.Color as AndroidColor
import android.os.Build
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.dynamicDarkColorScheme
import androidx.compose.material3.dynamicLightColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.SideEffect
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalView
import androidx.core.view.WindowCompat
import androidx.core.view.WindowInsetsControllerCompat

private val White = Color.White

private val LightColorScheme = lightColorScheme(
    primary = SJTUBlue,
    onPrimary = White,
    primaryContainer = Color(0xFFC9E7FA),
    onPrimaryContainer = Color(0xFF001F2E),
    inversePrimary = SJTUBlueLight,
    secondary = Color(0xFF4F616E),
    onSecondary = White,
    secondaryContainer = Color(0xFFD2E6F4),
    onSecondaryContainer = Color(0xFF0A1E28),
    tertiary = SJTUOrange,
    onTertiary = White,
    tertiaryContainer = Color(0xFFFFDCC2),
    onTertiaryContainer = Color(0xFF2D1300),
    background = SurfaceLight,
    onBackground = Color(0xFF181C20),
    surface = SurfaceLight,
    onSurface = Color(0xFF181C20),
    surfaceVariant = SurfaceVariantLight,
    onSurfaceVariant = Color(0xFF40484E),
    outline = Color(0xFF71787E),
    outlineVariant = Color(0xFFC1C8CE),
    inverseSurface = Color(0xFF2D3136),
    inverseOnSurface = Color(0xFFEEF1F6),
    error = ErrorRed,
    onError = White,
    errorContainer = Color(0xFFFFDAD6),
    onErrorContainer = Color(0xFF410002),
    surfaceTint = SJTUBlue
)

private val DarkColorScheme = darkColorScheme(
    primary = SJTUBlueLight,
    onPrimary = Color(0xFF00344F),
    primaryContainer = SJTUBlueDark,
    onPrimaryContainer = Color(0xFFC9E7FA),
    inversePrimary = SJTUBlue,
    secondary = Color(0xFFB6C9D8),
    onSecondary = Color(0xFF21333E),
    secondaryContainer = Color(0xFF374956),
    onSecondaryContainer = Color(0xFFD2E6F4),
    tertiary = SJTUOrangeLight,
    onTertiary = Color(0xFF4A2900),
    tertiaryContainer = SJTUOrangeDark,
    onTertiaryContainer = Color(0xFFFFDCC2),
    background = SurfaceDark,
    onBackground = Color(0xFFE1E7ED),
    surface = SurfaceDark,
    onSurface = Color(0xFFE1E7ED),
    surfaceVariant = SurfaceVariantDark,
    onSurfaceVariant = Color(0xFFBFC8CF),
    outline = Color(0xFF889199),
    outlineVariant = Color(0xFF3E464C),
    inverseSurface = Color(0xFFE1E7ED),
    inverseOnSurface = Color(0xFF181C20),
    error = ErrorRedDark,
    onError = Color(0xFF68000D),
    errorContainer = Color(0xFF8C1D18),
    onErrorContainer = Color(0xFFFFDAD6),
    surfaceTint = SJTUBlueLight
)

/**
 * 主题跟随系统亮暗。默认保留交大品牌配色，需要 Material You 动态取色时把 dynamicColor 置为 true。
 */
@Composable
fun SJtuRunnerTheme(
    darkTheme: Boolean = isSystemInDarkTheme(),
    dynamicColor: Boolean = false,
    content: @Composable () -> Unit
) {
    val context = LocalContext.current
    val colorScheme = when {
        dynamicColor && Build.VERSION.SDK_INT >= Build.VERSION_CODES.S ->
            if (darkTheme) dynamicDarkColorScheme(context) else dynamicLightColorScheme(context)

        darkTheme -> DarkColorScheme
        else -> LightColorScheme
    }

    val appColors = if (darkTheme) DarkAppColors else LightAppColors

    val view = LocalView.current
    if (!view.isInEditMode) {
        SideEffect {
            val window = (view.context as Activity).window
            window.statusBarColor = AndroidColor.TRANSPARENT
            window.navigationBarColor = AndroidColor.TRANSPARENT
            WindowCompat.setDecorFitsSystemWindows(window, false)
            val controller = WindowInsetsControllerCompat(window, view)
            controller.isAppearanceLightStatusBars = !darkTheme
            controller.isAppearanceLightNavigationBars = !darkTheme
        }
    }

    CompositionLocalProvider(LocalAppColors provides appColors) {
        MaterialTheme(
            colorScheme = colorScheme,
            typography = Typography,
            content = content
        )
    }
}

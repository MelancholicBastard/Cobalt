package com.melancholicbastard.cobalt.ui.theme

import android.os.Build
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.dynamicDarkColorScheme
import androidx.compose.material3.dynamicLightColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext

private val DarkColorScheme = darkColorScheme(
    primary = Color(0xFF0047AB),
    onPrimary = Color(0xFFFFFFFF),
    primaryContainer = Color(0xFF002366),
    onPrimaryContainer = Color(0xFFD6E3FF),

    secondary = Color(0xFF6B76C2),
    onSecondary = Color(0xFFFFFFFF),
    secondaryContainer = Color(0xFF4A559C),
    onSecondaryContainer = Color(0xFFE0E2FF),

    tertiary = Color(0xFF212121),
    onTertiary = Color(0xFFEEEEEE),

    // Фоны и поверхности
    background = Color(0xFF001233),
    onBackground = Color(0xFFE0E5FF),

    surface = Color(0xFF1A237E),
    onSurface = Color(0xFFE8EAF6),
    surfaceVariant = Color(0xFF303F9F),
    onSurfaceVariant = Color(0xFFC5CAE9),

    // Специальные цвета
    error = Color(0xFFCF6679),
    onError = Color(0xFF000000),
    outline = Color(0xFF6D7AFF),
    outlineVariant = Color(0xFF3D5AFE)

)

private val LightColorScheme = lightColorScheme(
    primary = Color(0xFF1565C0),
    onPrimary = Color(0xFFFFFFFF),
    primaryContainer = Color(0xFFE3F2FD),
    onPrimaryContainer = Color(0xFF0D47A1),

    secondary = Color(0xFF5C6BC0),
    onSecondary = Color(0xFFFFFFFF),
    secondaryContainer = Color(0xFFE8EAF6),
    onSecondaryContainer = Color(0xFF3949AB),

    tertiary = Color(0xFFE0E0E0),
    onTertiary = Color(0xFF212121),

    background = Color(0xFFE3F2FD),
    onBackground = Color(0xFF0D47A1),

    surface = Color(0xFFE8EAF6),
    onSurface = Color(0xFF1A237E),
    surfaceVariant = Color(0xFFC5CAE9),
    onSurfaceVariant = Color(0xFF303F9F),

    error = Color(0xFFD32F2F),
    onError = Color(0xFFFFFFFF),
    outline = Color(0xFF3D5AFE),
    outlineVariant = Color(0xFF6D7AFF)

//private val DarkColorScheme = darkColorScheme(
//    primary = Purple80,
//    secondary = PurpleGrey80,
//    tertiary = Pink80,
//    background = Color(0xFF4B00FF)
//)
//
//private val LightColorScheme = lightColorScheme(
//    primary = Purple40,
//    secondary = PurpleGrey40,
//    tertiary = Pink40,
//    background = Color(0xFF4B00FF)

    /* Other default colors to override
    ,
    surface = Color(0xFFFFFBFE),
    onPrimary = Color.White,
    onSecondary = Color.White,
    onTertiary = Color.White,
    onBackground = Color(0xFF1C1B1F),
    onSurface = Color(0xFF1C1B1F),
    */
)

@Composable
fun CobaltTheme(
    darkTheme: Boolean = isSystemInDarkTheme(),
    // Dynamic color is available on Android 12+
    dynamicColor: Boolean = false,
    content: @Composable () -> Unit
) {
    val colorScheme = when {
        dynamicColor && Build.VERSION.SDK_INT >= Build.VERSION_CODES.S -> {
            val context = LocalContext.current
            if (darkTheme) dynamicDarkColorScheme(context) else dynamicLightColorScheme(context)
        }

        darkTheme -> DarkColorScheme
        else -> LightColorScheme
    }

    MaterialTheme(
        colorScheme = colorScheme,
        typography = Typography,
        content = content
    )
}
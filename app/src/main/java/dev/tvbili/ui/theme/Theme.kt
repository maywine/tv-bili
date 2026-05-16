package dev.tvbili.ui.theme

import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.runtime.Composable

private val TvBiliDarkColorScheme = darkColorScheme(
    primary = BrandPink,
    onPrimary = Black,
    secondary = BrandPinkSoft,
    onSecondary = Black,
    background = Black,
    onBackground = OnSurface,
    surface = SurfaceDark,
    onSurface = OnSurface,
    surfaceVariant = SurfaceDark,
    onSurfaceVariant = OnSurfaceDim,
)

@Composable
fun TvBiliTheme(
    content: @Composable () -> Unit,
) {
    // 客厅 10 ft 暗色 only — 忽略 isSystemInDarkTheme，强制暗。
    MaterialTheme(
        colorScheme = TvBiliDarkColorScheme,
        content = content,
    )
}

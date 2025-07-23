package warlockfe.warlock3.compose.util

import androidx.compose.runtime.staticCompositionLocalOf

val LocalSkin = staticCompositionLocalOf<Map<String, SkinObject>> { emptyMap() }
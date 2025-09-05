package warlockfe.warlock3.compose.util

import androidx.compose.runtime.staticCompositionLocalOf
import warlockfe.warlock3.compose.model.SkinObject

val LocalSkin = staticCompositionLocalOf<Map<String, SkinObject>> { emptyMap() }
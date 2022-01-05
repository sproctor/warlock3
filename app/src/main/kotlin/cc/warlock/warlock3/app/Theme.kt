package cc.warlock.warlock3.app

import androidx.compose.material.Colors
import androidx.compose.material.icons.Icons
import androidx.compose.ui.graphics.Color

val warlockPurple = Color(0xFF6804f3)

val Colors.onPrimarySurface: Color
    get() = if (isLight) onPrimary else onSurface

val WarlockIcons = Icons.Filled
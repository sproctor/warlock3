package cc.warlock.warlock3.core.highlights

import cc.warlock.warlock3.core.text.WarlockStyle

data class Highlight(val pattern: String, val styles: List<WarlockStyle>, val isRegex: Boolean)

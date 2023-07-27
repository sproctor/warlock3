package cc.warlock.warlock3.core.prefs.adapters

import app.cash.sqldelight.ColumnAdapter
import cc.warlock.warlock3.core.text.WarlockColor

object WarlockColorAdapter : ColumnAdapter<WarlockColor, Long> {
    override fun decode(databaseValue: Long): WarlockColor {
        return WarlockColor(databaseValue)
    }

    override fun encode(value: WarlockColor): Long {
        return value.argb
    }
}
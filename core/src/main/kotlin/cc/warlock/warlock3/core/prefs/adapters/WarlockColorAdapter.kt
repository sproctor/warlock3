package cc.warlock.warlock3.core.prefs.adapters

import cc.warlock.warlock3.core.text.WarlockColor
import cc.warlock.warlock3.core.text.isUnspecified
import cc.warlock.warlock3.core.util.toWarlockColor
import com.squareup.sqldelight.ColumnAdapter

object WarlockColorAdapter : ColumnAdapter<WarlockColor, Long> {
    override fun decode(databaseValue: Long): WarlockColor {
        return WarlockColor(databaseValue)
    }

    override fun encode(value: WarlockColor): Long {
        return value.argb
    }
}
package cc.warlock.warlock3.core.prefs.adapters

import app.cash.sqldelight.ColumnAdapter
import cc.warlock.warlock3.core.window.WindowLocation

object LocationAdapter : ColumnAdapter<WindowLocation, String> {
    override fun decode(databaseValue: String): WindowLocation {
        return WindowLocation.entries.first { it.value == databaseValue }
    }

    override fun encode(value: WindowLocation): String {
        return value.value
    }
}
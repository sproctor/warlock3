package cc.warlock.warlock3.core.prefs.adapters

import cc.warlock.warlock3.core.window.WindowLocation
import com.squareup.sqldelight.ColumnAdapter

object LocationAdapter : ColumnAdapter<WindowLocation, String> {
    override fun decode(databaseValue: String): WindowLocation {
        return WindowLocation.values().first { it.value == databaseValue }
    }

    override fun encode(value: WindowLocation): String {
        return value.value
    }
}
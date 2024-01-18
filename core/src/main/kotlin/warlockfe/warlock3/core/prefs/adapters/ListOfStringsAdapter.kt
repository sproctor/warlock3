package warlockfe.warlock3.core.prefs.adapters

import app.cash.sqldelight.ColumnAdapter

object ListOfStringsAdapter : ColumnAdapter<List<String>, String> {
    override fun decode(databaseValue: String) =
        if (databaseValue.isEmpty()) {
            listOf()
        } else {
            databaseValue.split(",")
        }

    override fun encode(value: List<String>) = value.joinToString(separator = ",")
}

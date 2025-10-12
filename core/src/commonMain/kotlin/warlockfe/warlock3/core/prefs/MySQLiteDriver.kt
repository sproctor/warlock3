package warlockfe.warlock3.core.prefs

import androidx.sqlite.SQLiteConnection
import androidx.sqlite.SQLiteDriver
import androidx.sqlite.execSQL

class MySQLiteDriver(
    private val actual: SQLiteDriver,
) : SQLiteDriver by actual {
    override fun open(fileName: String): SQLiteConnection {
        val connection = actual.open(fileName)
        // Configure busy_timeout (in milliseconds) for all opened connections
        connection.execSQL("PRAGMA busy_timeout = 5000")
        return connection
    }
}

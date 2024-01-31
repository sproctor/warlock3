package warlockfe.warlock3.android

import android.app.Application
import app.cash.sqldelight.adapter.primitive.IntColumnAdapter
import app.cash.sqldelight.driver.android.AndroidSqliteDriver
import warlockfe.warlock3.android.di.AndroidAppContainer
import warlockfe.warlock3.compose.AppContainer
import warlockfe.warlock3.compose.util.insertDefaultMacrosIfNeeded
import warlockfe.warlock3.core.prefs.adapters.LocationAdapter
import warlockfe.warlock3.core.prefs.adapters.UUIDAdapter
import warlockfe.warlock3.core.prefs.adapters.WarlockColorAdapter
import warlockfe.warlock3.core.prefs.sql.Alias
import warlockfe.warlock3.core.prefs.sql.Alteration
import warlockfe.warlock3.core.prefs.sql.Database
import warlockfe.warlock3.core.prefs.sql.Highlight
import warlockfe.warlock3.core.prefs.sql.HighlightStyle
import warlockfe.warlock3.core.prefs.sql.PresetStyle
import warlockfe.warlock3.core.prefs.sql.WindowSettings

class WarlockApplication : Application() {

    lateinit var appContainer: AppContainer
        private set

    override fun onCreate() {
        super.onCreate()

        val configDir = filesDir
        configDir.mkdirs()
        val driver = AndroidSqliteDriver(Database.Schema, this, "prefs.db")
        val database = Database(
            driver = driver,
            HighlightAdapter = Highlight.Adapter(idAdapter = UUIDAdapter),
            HighlightStyleAdapter = HighlightStyle.Adapter(
                highlightIdAdapter = UUIDAdapter,
                groupNumberAdapter = IntColumnAdapter,
                textColorAdapter = WarlockColorAdapter,
                backgroundColorAdapter = WarlockColorAdapter,
            ),
            PresetStyleAdapter = PresetStyle.Adapter(
                textColorAdapter = WarlockColorAdapter,
                backgroundColorAdapter = WarlockColorAdapter,
            ),
            WindowSettingsAdapter = WindowSettings.Adapter(
                widthAdapter = IntColumnAdapter,
                heightAdapter = IntColumnAdapter,
                locationAdapter = LocationAdapter,
                positionAdapter = IntColumnAdapter,
                textColorAdapter = WarlockColorAdapter,
                backgroundColorAdapter = WarlockColorAdapter,
            ),
            AliasAdapter = Alias.Adapter(
                idAdapter = UUIDAdapter,
            ),
            AlterationAdapter = Alteration.Adapter(
                idAdapter = UUIDAdapter,
            )
        )
        database.insertDefaultMacrosIfNeeded()
        appContainer = AndroidAppContainer(this, database)
    }
}

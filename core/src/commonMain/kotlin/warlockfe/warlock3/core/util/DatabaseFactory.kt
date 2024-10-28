package warlockfe.warlock3.core.util

import app.cash.sqldelight.adapter.primitive.FloatColumnAdapter
import app.cash.sqldelight.adapter.primitive.IntColumnAdapter
import app.cash.sqldelight.db.SqlDriver
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

fun createDatabase(driver: SqlDriver): Database {
    return Database(
        driver = driver,
        HighlightAdapter = Highlight.Adapter(idAdapter = UUIDAdapter),
        HighlightStyleAdapter = HighlightStyle.Adapter(
            highlightIdAdapter = UUIDAdapter,
            groupNumberAdapter = IntColumnAdapter,
            textColorAdapter = WarlockColorAdapter,
            backgroundColorAdapter = WarlockColorAdapter,
            fontSizeAdapter = FloatColumnAdapter,
        ),
        PresetStyleAdapter = PresetStyle.Adapter(
            textColorAdapter = WarlockColorAdapter,
            backgroundColorAdapter = WarlockColorAdapter,
            fontSizeAdapter = FloatColumnAdapter,
        ),
        WindowSettingsAdapter = WindowSettings.Adapter(
            widthAdapter = IntColumnAdapter,
            heightAdapter = IntColumnAdapter,
            locationAdapter = LocationAdapter,
            positionAdapter = IntColumnAdapter,
            textColorAdapter = WarlockColorAdapter,
            backgroundColorAdapter = WarlockColorAdapter,
            fontSizeAdapter = FloatColumnAdapter,
        ),
        AliasAdapter = Alias.Adapter(
            idAdapter = UUIDAdapter,
        ),
        AlterationAdapter = Alteration.Adapter(
            idAdapter = UUIDAdapter,
        )
    )
}


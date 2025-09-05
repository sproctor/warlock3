package warlockfe.warlock3.core.prefs

import androidx.room.AutoMigration
import androidx.room.Database
import androidx.room.DeleteColumn
import androidx.room.RoomDatabase
import androidx.room.TypeConverters
import androidx.room.migration.AutoMigrationSpec
import warlockfe.warlock3.core.prefs.adapters.DatabaseConverters
import warlockfe.warlock3.core.prefs.dao.AccountDao
import warlockfe.warlock3.core.prefs.dao.AliasDao
import warlockfe.warlock3.core.prefs.dao.AlterationDao
import warlockfe.warlock3.core.prefs.dao.CharacterDao
import warlockfe.warlock3.core.prefs.dao.CharacterSettingDao
import warlockfe.warlock3.core.prefs.dao.ClientSettingDao
import warlockfe.warlock3.core.prefs.dao.ConnectionDao
import warlockfe.warlock3.core.prefs.dao.ConnectionSettingDao
import warlockfe.warlock3.core.prefs.dao.HighlightDao
import warlockfe.warlock3.core.prefs.dao.MacroDao
import warlockfe.warlock3.core.prefs.dao.NameDao
import warlockfe.warlock3.core.prefs.dao.PresetStyleDao
import warlockfe.warlock3.core.prefs.dao.ScriptDirDao
import warlockfe.warlock3.core.prefs.dao.VariableDao
import warlockfe.warlock3.core.prefs.dao.WindowSettingsDao
import warlockfe.warlock3.core.prefs.models.AccountEntity
import warlockfe.warlock3.core.prefs.models.AliasEntity
import warlockfe.warlock3.core.prefs.models.AlterationEntity
import warlockfe.warlock3.core.prefs.models.CharacterEntity
import warlockfe.warlock3.core.prefs.models.CharacterSettingEntity
import warlockfe.warlock3.core.prefs.models.ClientSettingEntity
import warlockfe.warlock3.core.prefs.models.ConnectionEntity
import warlockfe.warlock3.core.prefs.models.ConnectionSettingEntity
import warlockfe.warlock3.core.prefs.models.HighlightEntity
import warlockfe.warlock3.core.prefs.models.HighlightStyleEntity
import warlockfe.warlock3.core.prefs.models.MacroEntity
import warlockfe.warlock3.core.prefs.models.NameEntity
import warlockfe.warlock3.core.prefs.models.PresetStyleEntity
import warlockfe.warlock3.core.prefs.models.ScriptDirEntity
import warlockfe.warlock3.core.prefs.models.VariableEntity
import warlockfe.warlock3.core.prefs.models.WindowSettingsEntity

@Database(
    entities = [
        AccountEntity::class,
        AliasEntity::class,
        AlterationEntity::class,
        CharacterEntity::class,
        CharacterSettingEntity::class,
        ConnectionEntity::class,
        ConnectionSettingEntity::class,
        ClientSettingEntity::class,
        HighlightEntity::class,
        HighlightStyleEntity::class,
        MacroEntity::class,
        NameEntity::class,
        PresetStyleEntity::class,
        ScriptDirEntity::class,
        VariableEntity::class,
        WindowSettingsEntity::class,
    ],
    version = 15,
    autoMigrations = [
        AutoMigration(
            from = 11,
            to = 12,
            spec = PrefsDatabase.AutoMigration12::class,
        ),
        AutoMigration(from = 12, to = 13),
        AutoMigration(from = 13, to = 14),
        AutoMigration(from = 14, to = 15),
    ]
)
@TypeConverters(DatabaseConverters::class)
abstract class PrefsDatabase : RoomDatabase() {
    @DeleteColumn(tableName = "character", columnName = "accountId")
    class AutoMigration12 : AutoMigrationSpec

    abstract fun accountDao(): AccountDao
    abstract fun aliasDao(): AliasDao
    abstract fun alterationDao(): AlterationDao
    abstract fun characterDao(): CharacterDao
    abstract fun characterSettingDao(): CharacterSettingDao
    abstract fun connectionDao(): ConnectionDao
    abstract fun connectionSettingDao(): ConnectionSettingDao
    abstract fun clientSettingDao(): ClientSettingDao
    abstract fun highlightDao(): HighlightDao
    abstract fun macroDao(): MacroDao
    abstract fun nameDao(): NameDao
    abstract fun presetStyleDao(): PresetStyleDao
    abstract fun scriptDirDao(): ScriptDirDao
    abstract fun variableDao(): VariableDao
    abstract fun windowSettingsDao(): WindowSettingsDao
}

package warlockfe.warlock3.core.prefs

import androidx.room3.migration.Migration
import androidx.sqlite.SQLiteConnection
import androidx.sqlite.execSQL

val MIGRATION_10_11 =
    object : Migration(10, 11) {
        override suspend fun migrate(connection: SQLiteConnection) {
            connection.execSQL("DROP TABLE Alteration")
            connection.execSQL(
                """
                CREATE TABLE alteration (
                    id BLOB NOT NULL PRIMARY KEY,
                    characterId TEXT NOT NULL,
                    pattern TEXT NOT NULL,
                    sourceStream TEXT,
                    destinationStream TEXT,
                    result TEXT,
                    ignoreCase INTEGER NOT NULL,
                    keepOriginal INTEGER NOT NULL
                );
                """.trimIndent(),
            )
        }
    }

// Not an AutoMigration because of the backfill: under the old schema "placed in the layout" was
// location IS NOT NULL, and those windows must come up open.
val MIGRATION_20_21 =
    object : Migration(20, 21) {
        override suspend fun migrate(connection: SQLiteConnection) {
            connection.execSQL("ALTER TABLE WindowSettings ADD COLUMN `open` INTEGER NOT NULL DEFAULT 0")
            connection.execSQL("UPDATE WindowSettings SET `open` = 1 WHERE location IS NOT NULL")
        }
    }

val MIGRATION_14_16 =
    object : Migration(14, 16) {
        public override suspend fun migrate(connection: SQLiteConnection) {
            connection.execSQL("delete from Highlight where rowid not in (select min(rowid) from Highlight group by characterId, pattern)")
            connection.execSQL(
                "CREATE TABLE IF NOT EXISTS `_new_Highlight` (`id` BLOB NOT NULL, `characterId` TEXT NOT NULL, `pattern` TEXT NOT NULL, `isRegex` INTEGER NOT NULL, `matchPartialWord` INTEGER NOT NULL, `ignoreCase` INTEGER NOT NULL, PRIMARY KEY(`id`))",
            )
            connection.execSQL(
                "INSERT INTO `_new_Highlight` (`id`,`characterId`,`pattern`,`isRegex`,`matchPartialWord`,`ignoreCase`) SELECT `id`,`characterId`,`pattern`,`isRegex`,`matchPartialWord`,`ignoreCase` FROM `Highlight`",
            )
            connection.execSQL("DROP TABLE `Highlight`")
            connection.execSQL("ALTER TABLE `_new_Highlight` RENAME TO `Highlight`")
            connection.execSQL(
                "CREATE UNIQUE INDEX IF NOT EXISTS `index_Highlight_characterId_pattern` ON `Highlight` (`characterId`, `pattern`)",
            )
            connection.execSQL(
                "CREATE TABLE IF NOT EXISTS `_new_Name` (`id` BLOB NOT NULL, `characterId` TEXT NOT NULL, `text` TEXT NOT NULL, `textColor` INTEGER NOT NULL, `backgroundColor` INTEGER NOT NULL, `bold` INTEGER NOT NULL, `italic` INTEGER NOT NULL, `underline` INTEGER NOT NULL, `fontFamily` TEXT, `fontSize` REAL, PRIMARY KEY(`id`))",
            )
            connection.execSQL(
                "INSERT INTO `_new_Name` (`id`,`characterId`,`text`,`textColor`,`backgroundColor`,`bold`,`italic`,`underline`,`fontFamily`,`fontSize`) SELECT `id`,`characterId`,`text`,`textColor`,`backgroundColor`,`bold`,`italic`,`underline`,`fontFamily`,`fontSize` FROM `Name`",
            )
            connection.execSQL("DROP TABLE `Name`")
            connection.execSQL("ALTER TABLE `_new_Name` RENAME TO `Name`")
            connection.execSQL("CREATE UNIQUE INDEX IF NOT EXISTS `index_Name_characterId_text` ON `Name` (`characterId`, `text`)")
        }
    }

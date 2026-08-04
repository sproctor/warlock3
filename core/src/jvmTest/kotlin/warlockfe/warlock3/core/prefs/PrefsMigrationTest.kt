package warlockfe.warlock3.core.prefs

import androidx.room3.Room
import androidx.sqlite.driver.bundled.BundledSQLiteDriver
import androidx.sqlite.execSQL
import kotlinx.coroutines.runBlocking
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import java.io.File
import java.nio.file.Files
import kotlin.io.path.ExperimentalPathApi
import kotlin.io.path.deleteRecursively
import kotlin.test.AfterTest
import kotlin.test.BeforeTest
import kotlin.test.Test
import kotlin.test.assertEquals

/**
 * Runs MIGRATION_20_21 against a real SQLite database built from the exported v20 schema. A
 * migration defect fails at first launch for every upgrading user, and the versioned-database
 * machinery then deletes the failed target and re-seeds it on every launch - a permanent crash
 * loop - so the ALTER/backfill pair is exercised here against the real engine rather than only
 * through the in-memory DAO fake.
 */
class PrefsMigrationTest {
    private val character = "gs4:warlock"
    private lateinit var dir: java.nio.file.Path

    @BeforeTest
    fun setUp() {
        dir = Files.createTempDirectory("prefs-migration-test")
    }

    @OptIn(ExperimentalPathApi::class)
    @AfterTest
    fun tearDown() {
        dir.deleteRecursively()
    }

    @Test
    fun migration20To21MarksPlacedWindowsOpenAndOthersClosed() =
        runBlocking {
            val dbFile = File(dir.toFile(), "prefs.db")
            createV20Database(dbFile) { connection ->
                // A docked window, and a style-only row that was never placed.
                connection.execSQL(
                    "INSERT INTO WindowSettings (characterId, name, location, position, textColor, backgroundColor) " +
                        "VALUES ('$character', 'thoughts', 'right', 0, -1, -1)",
                )
                connection.execSQL(
                    "INSERT INTO WindowSettings (characterId, name, textColor, backgroundColor) " +
                        "VALUES ('$character', 'styleOnly', -1, -1)",
                )
            }

            val database =
                Room
                    .databaseBuilder<PrefsDatabase>(name = dbFile.absolutePath)
                    .setDriver(BundledSQLiteDriver())
                    .addMigrations(MIGRATION_20_21)
                    .build()
            try {
                val rows = database.windowSettingsDao().getByCharacter(character).associateBy { it.name }
                assertEquals(true, rows.getValue("thoughts").open)
                assertEquals(0, rows.getValue("thoughts").position)
                assertEquals(false, rows.getValue("styleOnly").open)
            } finally {
                database.close()
            }
        }

    /**
     * Builds a database exactly as a v20 build left it: every table and index from the exported
     * schema (room/.../20.json), the Room identity row, and user_version 20.
     */
    private fun createV20Database(
        dbFile: File,
        seed: (androidx.sqlite.SQLiteConnection) -> Unit,
    ) {
        val schema =
            Json
                .parseToJsonElement(schemaFile("20.json").readText())
                .jsonObject
                .getValue("database")
                .jsonObject
        val connection = BundledSQLiteDriver().open(dbFile.absolutePath)
        try {
            schema.getValue("entities").jsonArray.forEach { entity ->
                val obj = entity.jsonObject
                val tableName = obj.getValue("tableName").jsonPrimitive.content
                connection.execSQL(
                    obj
                        .getValue("createSql")
                        .jsonPrimitive.content
                        .replace("\${TABLE_NAME}", tableName),
                )
                obj["indices"]?.jsonArray?.forEach { index ->
                    connection.execSQL(
                        index.jsonObject
                            .getValue("createSql")
                            .jsonPrimitive.content
                            .replace("\${TABLE_NAME}", tableName),
                    )
                }
            }
            val identityHash = schema.getValue("identityHash").jsonPrimitive.content
            connection.execSQL("CREATE TABLE IF NOT EXISTS room_master_table (id INTEGER PRIMARY KEY, identity_hash TEXT)")
            connection.execSQL("INSERT OR REPLACE INTO room_master_table (id, identity_hash) VALUES (42, '$identityHash')")
            connection.execSQL("PRAGMA user_version = 20")
            seed(connection)
        } finally {
            connection.close()
        }
    }

    private fun schemaFile(name: String): File {
        // The test's working directory is the module directory, but be tolerant of a repo-root one.
        val candidates =
            listOf(
                File("room/warlockfe.warlock3.core.prefs.PrefsDatabase/$name"),
                File("core/room/warlockfe.warlock3.core.prefs.PrefsDatabase/$name"),
            )
        return candidates.firstOrNull { it.exists() }
            ?: error("Exported Room schema $name not found relative to ${File(".").absolutePath}")
    }
}

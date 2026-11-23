package warlockfe.warlock3.scripting

import kotlinx.io.files.Path
import kotlinx.io.files.SystemFileSystem
import kotlinx.io.files.SystemTemporaryDirectory
import kotlin.random.Random

private val runId = Random.nextLong().toULong().toString(16)
private var sequence = 0
private val handedOut = mutableListOf<Path>()

/**
 * A path in the temp directory that no other test uses. These common tests run under `jvmTest`
 * and `testAndroidHostTest` at the same time, in separate JVMs sharing one temp directory, so a
 * fixed name, or a counter that starts at 0 in each JVM, lets one run overwrite or delete a file
 * the other is still reading. Whatever ends up at the path is removed by [deleteTestTempPaths].
 */
fun testTempPath(
    prefix: String,
    suffix: String = "",
): Path = Path(SystemTemporaryDirectory, "$prefix-$runId-${sequence++}$suffix").also { handedOut += it }

/**
 * Deletes everything at the paths [testTempPath] has handed out, directories with their contents.
 * Call it after each test of a class whose tests leave files behind; tests in one JVM run one at a
 * time, so nothing handed out is still in use.
 */
fun deleteTestTempPaths() {
    handedOut.forEach(::deleteRecursively)
    handedOut.clear()
}

private fun deleteRecursively(path: Path) {
    val metadata = SystemFileSystem.metadataOrNull(path) ?: return
    if (metadata.isDirectory) SystemFileSystem.list(path).forEach(::deleteRecursively)
    SystemFileSystem.delete(path, mustExist = false)
}

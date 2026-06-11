package warlockfe.warlock3.core.prefs.config

import dev.eav.tomlkt.Toml
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.launch
import kotlinx.io.files.FileSystem
import kotlinx.io.files.Path
import kotlin.uuid.Uuid

/**
 * Human-editable, file-backed store for the client-wide settings that aren't tied to a character:
 * `client.toml` (app settings that used to live in the `clientsetting` table) and `connections.toml`
 * (the character + connection registry). Both are single fixed-path files, so each is backed by a
 * [TomlFileStore]; the heavy lifting (atomic writes, cross-process locks, comment-preserving
 * re-encode, in-memory source of truth) lives there.
 */
class ClientConfigStore(
    configDirectory: String,
    fileSystem: FileSystem,
) {
    private val rootDir = Path(configDirectory)

    private val toml =
        Toml {
            ignoreUnknownKeys = true
            explicitNulls = false
        }

    private val clientStore =
        TomlFileStore(
            path = Path(rootDir, "client.toml"),
            fileSystem = fileSystem,
            serializer = ClientConfig.serializer(),
            toml = toml,
            default = ClientConfig(),
        )

    private val connectionsStore =
        TomlFileStore(
            path = Path(rootDir, "connections.toml"),
            fileSystem = fileSystem,
            serializer = ConnectionRegistryConfig.serializer(),
            toml = toml,
            default = ConnectionRegistryConfig(),
            normalize = { it.withGeneratedIds() },
        )

    fun observeClient(): Flow<ClientConfig> = clientStore.observe()

    fun currentClient(): ClientConfig = clientStore.current()

    suspend fun mutateClient(transform: (ClientConfig) -> ClientConfig) = clientStore.mutate(transform)

    fun observeConnections(): Flow<ConnectionRegistryConfig> = connectionsStore.observe()

    fun currentConnections(): ConnectionRegistryConfig = connectionsStore.current()

    suspend fun mutateConnections(transform: (ConnectionRegistryConfig) -> ConnectionRegistryConfig) = connectionsStore.mutate(transform)

    suspend fun load() {
        clientStore.load()
        connectionsStore.load()
    }

    fun startWatching(scope: CoroutineScope) {
        scope.launch {
            watchConfigChanges(rootDir.toString()).collect { changed ->
                val path = Path(changed)
                when {
                    clientStore.owns(path) -> clientStore.reloadIfChanged()
                    connectionsStore.owns(path) -> connectionsStore.reloadIfChanged()
                }
            }
        }
    }
}

// Connections are normally machine-written with ids, but a hand-added entry may omit one; fill it in
// (and persist) so deletes/edits keyed by id stay stable, mirroring CharacterConfigStore.
private fun ConnectionRegistryConfig.withGeneratedIds(): Pair<ConnectionRegistryConfig, Boolean> {
    var changed = false
    val connections =
        connections.map { connection ->
            if (connection.id.isBlank()) {
                changed = true
                connection.copy(id = Uuid.random().toString())
            } else {
                connection
            }
        }
    return copy(connections = connections) to changed
}

package warlockfe.warlock3.core.client

import kotlinx.coroutines.flow.Flow

interface WarlockProxy : AutoCloseable {
    val isAlive: Boolean
    val stdOut: Flow<String>
    val stdErr: Flow<String>

    override fun close()

    interface Factory {
        fun create(command: String): WarlockProxy
    }
}
package warlockfe.warlock3.compose.ui.window

import co.touchlab.kermit.Logger
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.channels.BufferOverflow
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.launch

class StreamWorkQueue(
    scope: CoroutineScope,
    capacity: Int = 1000,
) {
    private val logger = Logger.withTag("StreamWorkQueue")

    private val channel =
        Channel<suspend () -> Unit>(
            capacity = capacity,
            onBufferOverflow = BufferOverflow.SUSPEND,
        )

    init {
        scope.launch(Dispatchers.Default) {
            for (op in channel) {
                try {
                    op()
                } catch (e: CancellationException) {
                    throw e
                } catch (e: Throwable) {
                    logger.e(e) { "Stream op threw" }
                }
            }
        }
    }

    suspend fun submit(op: suspend () -> Unit) {
        channel.send(op)
    }
}

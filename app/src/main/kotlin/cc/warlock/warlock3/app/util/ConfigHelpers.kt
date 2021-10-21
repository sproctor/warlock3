package cc.warlock.warlock3.app.util

import com.uchuhimo.konf.Config
import com.uchuhimo.konf.Item
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.channels.awaitClose
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.callbackFlow

@OptIn(ExperimentalCoroutinesApi::class)
inline fun <reified T> Config.observe(item: Item<T>): Flow<T> =
    callbackFlow {
        send(get(item))
        val handler = afterSet { updatedItem, value ->
            if (updatedItem == item && value is T) trySend(value)
        }
        awaitClose { handler.cancel() }
    }
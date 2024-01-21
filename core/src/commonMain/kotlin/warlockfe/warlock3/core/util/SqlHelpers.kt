package warlockfe.warlock3.core.util

import app.cash.sqldelight.Query
import app.cash.sqldelight.Transacter
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import app.cash.sqldelight.async.coroutines.awaitAsList
import app.cash.sqldelight.async.coroutines.awaitAsOne
import app.cash.sqldelight.async.coroutines.awaitAsOneOrNull

fun <T : Any> Flow<Query<T>>.mapToList(): Flow<List<T>> = map {
    it.awaitAsList()
}

fun <T : Any> Flow<Query<T>>.mapToOne(): Flow<T> = map {
    it.awaitAsOne()
}

fun <T : Any> Flow<Query<T>>.mapToOneOrNull(): Flow<T?> = map {
    it.awaitAsOneOrNull()
}

fun <T : Any> Flow<Query<T>>.mapToOne(
    transacter: Transacter,
): Flow<T> = map {
    transacter.transactionWithResult {
        it.executeAsOne()
    }
}

fun <T : Any> Flow<Query<T>>.mapToOneOrNull(
    transacter: Transacter,
): Flow<T?> = map {
    transacter.transactionWithResult {
        it.executeAsOneOrNull()
    }
}

fun <T : Any> Flow<Query<T>>.mapToList(
    transacter: Transacter,
): Flow<List<T>> = map {
    transacter.transactionWithResult {
        it.executeAsList()
    }
}

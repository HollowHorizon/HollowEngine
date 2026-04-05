package ru.hollowhorizon.hollowengine.common.coroutines

import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.CoroutineStart
import kotlin.coroutines.CoroutineContext
import kotlin.coroutines.EmptyCoroutineContext

data class SerializableCoroutineDefinition(
    val key: SerializableCoroutineKey,
    val contextFactory: () -> SerializableCoroutineContextElement,
    val context: CoroutineContext = EmptyCoroutineContext,
    val start: CoroutineStart = CoroutineStart.DEFAULT,
    val block: suspend CoroutineScope.() -> Unit,
)

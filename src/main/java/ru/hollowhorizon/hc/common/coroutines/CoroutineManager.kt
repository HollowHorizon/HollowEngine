package ru.hollowhorizon.hc.common.coroutines

import kotlinx.coroutines.*

internal val hollowCoroutineScope = CoroutineScope(SupervisorJob() + Dispatchers.IO)

fun scopeSync(block: suspend CoroutineScope.() -> Unit) = hollowCoroutineScope.launch(block = block)
fun scopeAsync(block: suspend CoroutineScope.() -> Unit) = hollowCoroutineScope.async(block = block)
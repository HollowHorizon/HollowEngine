package ru.hollowhorizon.hollowengine.common.scripting.story.coroutines

import kotlinx.coroutines.*

object ScriptContext {
    val scope = CoroutineScope(Dispatchers.Default)
    @OptIn(DelicateCoroutinesApi::class)
    val scriptContext = newSingleThreadContext("Story Scripting Thread")
}

suspend fun test(n: Long) {
    delay(n)
    println(n)
}

fun main() {
    ScriptContext.scope.async(ScriptContext.scriptContext) {

    }
    ScriptContext.scope.async(ScriptContext.scriptContext) {

    }
    println(1)
    runBlocking {
        delay(3000)
    }
    println(2)
}
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
        Thread.sleep(1000)
        println(Thread.currentThread().name)
    }
    ScriptContext.scope.async(ScriptContext.scriptContext) {
        Thread.sleep(2000)
        println(Thread.currentThread().name)
    }
    runBlocking {
        delay(3000)
    }
}
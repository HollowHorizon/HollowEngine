package ru.hollowhorizon.hollowengine.common.compiler

import java.util.concurrent.CountDownLatch
import java.util.concurrent.atomic.AtomicReference
import kotlin.coroutines.Continuation
import kotlin.coroutines.EmptyCoroutineContext
import kotlin.coroutines.startCoroutine

internal fun <T> runScriptingBlocking(block: suspend () -> T): T {
    val thread = Thread.currentThread()
    val previousClassLoader = thread.contextClassLoader
    thread.contextClassLoader = ScriptingCompilerImpl::class.java.classLoader
    val latch = CountDownLatch(1)
    val outcome = AtomicReference<Result<T>>()

    try {
        block.startCoroutine(object : Continuation<T> {
            override val context = EmptyCoroutineContext

            override fun resumeWith(result: Result<T>) {
                outcome.set(result)
                latch.countDown()
            }
        })

        try {
            latch.await()
        } catch (exception: InterruptedException) {
            thread.interrupt()
            throw IllegalStateException("Interrupted while waiting for scripting coroutine", exception)
        }
    } finally {
        thread.contextClassLoader = previousClassLoader
    }

    return outcome.get()?.getOrThrow()
        ?: error("Scripting coroutine completed without a result")
}

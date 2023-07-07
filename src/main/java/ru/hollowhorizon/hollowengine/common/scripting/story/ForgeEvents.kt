package ru.hollowhorizon.hollowengine.common.scripting.story

import net.minecraftforge.common.MinecraftForge
import net.minecraftforge.eventbus.api.Event
import net.minecraftforge.eventbus.api.SubscribeEvent

interface IForgeEventScriptSupport {
    val forgeEvents: MutableSet<ForgeEvent<*>>
}

inline fun <reified T : Event> waitForgeEvent(noinline function: (T) -> Boolean) {
    val event = ForgeEvent(T::class.java, function)
    MinecraftForge.EVENT_BUS.register(event)
    event.waitEvent()
}

inline fun <reified T : Event> IForgeEventScriptSupport.whenForgeEvent(noinline function: (T) -> Unit) {
    val event = ForgeEvent(T::class.java) { function(it); return@ForgeEvent false }
    MinecraftForge.EVENT_BUS.register(event)
    this.forgeEvents.add(event)
}

class ForgeEvent<T : Event>(private val type: Class<T>, private val function: (T) -> Boolean) {
    private val waiter = Object()

    @SubscribeEvent
    fun onEvent(event: T) {
        val etype = event::class.java

        if (!etype.isAssignableFrom(type)) return

        try {
            if (function.invoke(event)) {
                MinecraftForge.EVENT_BUS.unregister(this)
                synchronized(waiter) { waiter.notifyAll() }
            }
        } catch (e: Exception) {
            e.printStackTrace()
        }
    }

    fun waitEvent() {
        synchronized(waiter) { waiter.wait() }
    }
}
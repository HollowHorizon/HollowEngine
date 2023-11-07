package ru.hollowhorizon.hollowengine.common.scripting.story.nodes.base

import net.minecraft.nbt.CompoundTag
import net.minecraftforge.common.MinecraftForge
import net.minecraftforge.event.ServerChatEvent
import net.minecraftforge.eventbus.api.Event
import net.minecraftforge.eventbus.api.SubscribeEvent
import ru.hollowhorizon.hollowengine.common.scripting.story.nodes.IContextBuilder
import ru.hollowhorizon.hollowengine.common.scripting.story.nodes.Node
import kotlin.properties.ReadWriteProperty
import kotlin.reflect.KProperty

open class ForgeEventNode<T : Event>(private val type: Class<T>, open val action: (T) -> Boolean) : Node() {
    private var isStarted = false
    private var isEnded = false

    @SubscribeEvent
    fun onEvent(event: T) {
        val etype = event::class.java

        if (!etype.isAssignableFrom(type)) return

        if (action(event)) {
            isEnded = true
            MinecraftForge.EVENT_BUS.unregister(this)
        }
    }

    override fun tick(): Boolean {
        if (!isStarted) {
            isStarted = true
            MinecraftForge.EVENT_BUS.register(this)
        }
        return !isEnded
    }

    override fun serializeNBT() = CompoundTag()
    override fun deserializeNBT(nbt: CompoundTag) = Unit
}

inline fun <reified T : Event> IContextBuilder.waitForgeEvent(noinline function: (T) -> Boolean) =
    +ForgeEventNode(T::class.java, function)
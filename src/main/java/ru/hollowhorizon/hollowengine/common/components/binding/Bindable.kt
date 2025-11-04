package ru.hollowhorizon.hollowengine.common.components.binding

import net.minecraft.nbt.CompoundTag
import org.jetbrains.kotlin.container.ComponentProvider
import ru.hollowhorizon.hollowengine.common.components.Component
import ru.hollowhorizon.hollowengine.common.components.isClientSide

interface Bindable {
    fun onAttach()
    fun onDetach()
    fun onSave(tag: CompoundTag)
    fun onLoad(tag: CompoundTag)
    fun onUpdate()
}

enum class Side {
    CLIENT, SERVER, BOTH;

    fun whenOn(isClient: Boolean, body: () -> Unit) {
        when (this) {
            CLIENT -> if (isClient) body() else Unit
            SERVER -> if (!isClient) body() else Unit
            BOTH -> body()
        }
    }
}

fun Component<*>.bind(location: String, target: Bindable, side: Side = Side.SERVER) {
    onAttach {
        side.whenOn(isClientSide) { target.onAttach() }
    }
    onDetach {
        side.whenOn(isClientSide) { target.onDetach() }
    }
    onSave {
        side.whenOn(isClientSide) {
            val tag = CompoundTag()
            target.onSave(tag)
            put(location, tag)
        }
    }
    onLoad {
        side.whenOn(isClientSide) {
            target.onLoad(getCompound(location))

        }
    }
    onUpdate {
        side.whenOn(isClientSide) { target.onUpdate() }
    }
}

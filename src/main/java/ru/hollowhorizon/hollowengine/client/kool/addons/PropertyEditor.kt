package ru.hollowhorizon.hollowengine.client.kool.addons

import de.fabmax.kool.modules.ui2.TextField
import de.fabmax.kool.modules.ui2.UiScope
import de.fabmax.kool.modules.ui2.onChange
import kotlinx.serialization.Serializable
import net.minecraft.nbt.StringTag
import net.minecraft.nbt.Tag
import net.minecraft.world.entity.Entity
import net.minecraft.world.entity.player.Player
import ru.hollowhorizon.hollowengine.common.components.Component
import ru.hollowhorizon.hollowengine.common.components.ComponentDispatcher
import ru.hollowhorizon.hollowengine.common.components.property.Property
import ru.hollowhorizon.hollowengine.common.components.annotations.ComponentMeta
import ru.hollowhorizon.hollowengine.common.components.registry.ComponentRegistry
import ru.hollowhorizon.hollowengine.common.network.HollowPacket
import ru.hollowhorizon.hollowengine.common.network.HollowPacketHandler
import ru.hollowhorizon.hollowengine.common.util.PlayerPermissions
import ru.hollowhorizon.hollowengine.common.utils.nbt.ForTag
import ru.hollowhorizon.hollowengine.common.utils.nbt.NBTFormat
import ru.hollowhorizon.hollowengine.common.utils.rl
import kotlin.reflect.full.findAnnotation

interface Renderer<V : Any> {
    fun UiScope.render(component: Component<*>, property: Property<V>)
}

object StringRenderer : Renderer<String> {
    override fun UiScope.render(component: Component<*>, property: Property<String>) {
        val value = property.value ?: return
        TextField(value) {
            modifier.onChange {
                property.value = it
                UpdatePropertyPacket(
                    (component.owner as Entity).id,
                    ComponentRegistry.getIdByLocation(component::class.findAnnotation<ComponentMeta>()!!.location.rl)!!,
                    property.name!!,
                    StringTag.valueOf(it),
                ).send()
            }
        }
    }
}

@Serializable
@HollowPacketHandler(HollowPacketHandler.Direction.TO_SERVER)
class UpdatePropertyPacket(
    val entityId: Int,
    val componentId: Int,
    val property: String,
    val tag: @Serializable(ForTag::class) Tag,
) : HollowPacket {
    override fun handle(player: Player) {
        if (!player.hasPermissions(PlayerPermissions.GAMEMASTER)) return

        val entity = player.level().getEntity(entityId) ?: return
        val dispatcher = entity as? ComponentDispatcher ?: return
        val location = ComponentRegistry.getLocationById(componentId)
        dispatcher.`hollowcore$components`[location]?.properties?.get(property)?.deserialize(NBTFormat, tag)

    }
}
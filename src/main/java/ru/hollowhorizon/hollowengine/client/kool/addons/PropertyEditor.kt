package ru.hollowhorizon.hollowengine.client.kool.addons

import de.fabmax.kool.math.Vec2f
import de.fabmax.kool.modules.ui2.*
import de.fabmax.kool.util.Color
import de.fabmax.kool.util.MsdfFont
import kotlinx.serialization.Serializable
import net.minecraft.nbt.StringTag
import net.minecraft.nbt.Tag
import net.minecraft.resources.ResourceLocation
import net.minecraft.world.entity.Entity
import net.minecraft.world.entity.player.Player
import ru.hollowhorizon.hollowengine.client.gui.scripting.HACK_FONT
import ru.hollowhorizon.hollowengine.client.gui.scripting.tools.hoverColors
import ru.hollowhorizon.hollowengine.common.components.Component
import ru.hollowhorizon.hollowengine.common.components.ComponentDispatcher
import ru.hollowhorizon.hollowengine.common.components.annotations.ComponentMeta
import ru.hollowhorizon.hollowengine.common.components.property.Property
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

    fun UiScope.editor(component: Component<*>, property: Property<V>) {}
}

fun <V : Any> save(component: Component<*>, property: Property<V>, tag: Tag) {
    UpdatePropertyPacket(
        (component.owner as Entity).id,
        ComponentRegistry.getIdByLocation(component::class.findAnnotation<ComponentMeta>()!!.location.rl)!!,
        property.name!!,
        tag,
    ).send()
}

object StringRenderer : Renderer<String> {
    override fun UiScope.render(component: Component<*>, property: Property<String>) {
        val value = property.get()
        TextField(value) {
            modifier.onChange {
                property.set(it)
                save(component, property, StringTag.valueOf(it))
            }
        }
    }
}

object BooleanRenderer : Renderer<Boolean> {
    override fun UiScope.render(component: Component<*>, property: Property<Boolean>) {
        Checkbox(property.get()) {
            modifier.onToggle { property.set(it) }
        }
    }
}

class ResourceLocationRenderer(private val choices: List<String>, var hint: String) : Renderer<String> {
    var position = mutableStateOf(Vec2f(0f, 0f))
    var value = mutableStateOf("")

    override fun UiScope.render(component: Component<*>, property: Property<String>) {
        value = remember { mutableStateOf(property.get()) }

        TextField {
            modifier.textColor =
                if (ResourceLocation.isValidResourceLocation(value.use()) && value.use() in choices) colors.onBackground else Color.DARK_RED
            modifier.hint(hint)

            modifier.alignY(AlignmentY.Center)
                .width(Grow.Std)
                .text(value.use())
                .onChange {
                    value.set(it)
                    if (ResourceLocation.isValidResourceLocation(it) && it in choices) {
                        property.set(it)
                        save(component, property, StringTag.valueOf(it))
                    }
                }
                .onPositioned {
                    position.set(Vec2f(it.leftPx, it.bottomPx))
                }
        }
    }

    override fun UiScope.editor(component: Component<*>, property: Property<String>) {
        val values = choices.filter { it.startsWith(value.use(), ignoreCase = true) && it != value.use() }.sorted()
        if (values.isNotEmpty()) {
            val font = MsdfFont(HACK_FONT, 18f)
            val length = values.maxByOrNull { it.length } ?: ""
            val width = font.textDimensions(length).width.dp + sizes.smallGap * 2f + sizes.gap * 2f
            Popup(position.use().x, position.use().y) {
                modifier.background(null).border(null).zLayer(UiSurface.LAYER_POPUP)
                    .size(
                        width,
                        (22.dp + sizes.smallGap) * values.size.coerceAtMost(10) + sizes.smallGap
                    )

                LazyColumn(
                    withVerticalScrollbar = true,
                    withHorizontalScrollbar = false,
                    containerModifier = {
                        it.background(null)
                    }
                ) {
                    modifier.margin(end = sizes.gap)
                    items(values) { resource ->
                        Box(Grow.Std) {
                            val color = hoverColors(1f, Color("1B1E23FF"), Color("252930FF"))
                            modifier.backgroundColor(color).padding(sizes.smallGap)
                                .onClick {
                                    value.set(resource)
                                    property.set(resource)
                                    save(component, property, StringTag.valueOf(resource))
                                }

                            Text(resource) {
                                modifier.font(font)
                                    .width(Grow.Std)
                            }
                        }
                    }
                }
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
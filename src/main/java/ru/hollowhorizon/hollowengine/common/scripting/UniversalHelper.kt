package ru.hollowhorizon.hollowengine.common.scripting

import dev.ftb.mods.ftbteams.data.Team
import net.minecraft.nbt.CompoundTag
import net.minecraft.nbt.TagParser
import net.minecraft.server.level.ServerPlayer
import net.minecraft.tags.TagKey
import net.minecraft.world.item.Item
import net.minecraft.world.item.ItemStack
import net.minecraftforge.registries.ForgeRegistries
import net.minecraftforge.server.ServerLifecycleHooks
import ru.hollowhorizon.hc.client.models.gltf.manager.AnimatedEntityCapability
import ru.hollowhorizon.hc.client.utils.rl
import kotlin.properties.ReadOnlyProperty
import kotlin.properties.ReadWriteProperty
import kotlin.reflect.KProperty

fun main() {
    val book = item("minecraft:written_book", 1, "{author:\"Dev\",filtered_title:\"Hollow\",pages:['{\"text\":\"Hello...\"}','{\"text\":\"There is letters\"}','{\"text\":\"на русском тоже!\\n\\n\\nда\"}'],title:\"Hollow\"}")
}

fun AnimatedEntityCapability.skin(name: String) = "skins/$name"

val Team.randomPlayer get() = onlineMembers.random()

val Team.ownerPlayer get() = ServerLifecycleHooks.getCurrentServer().playerList.getPlayer(owner)

fun Team.forEachPlayer(action: (ServerPlayer) -> Unit) {
    onlineMembers.forEach { action(it) }
}

fun item(item: String, count: Int = 1, nbt: CompoundTag? = null) = ItemStack(
    ForgeRegistries.ITEMS.getValue(item.rl) ?: throw IllegalStateException("Item $item not found!"),
    count,
    nbt
)

fun item(item: String, count: Int = 1, nbt: String): ItemStack {
    return item(item, count, TagParser.parseTag(nbt))
}

fun tag(tag: String): TagKey<Item> {
    val manager = ForgeRegistries.ITEMS.tags() ?: throw IllegalStateException("Tag $tag not found!")
    return manager.createTagKey(tag.rl)
}

fun <T> runtime(default: () -> T) = RuntimeVariable(default)
fun <T> runtime() = RuntimeVariable<T> { throw IllegalStateException("Default value not found, runtime property does not exists") }

val RUNTIME_PROPERTIES = mutableMapOf<String, Any?>()

class RuntimeVariable<T>(val default: () -> T): ReadWriteProperty<Any?, T> {

    @Suppress("UNCHECKED_CAST")
    override fun getValue(thisRef: Any?, property: KProperty<*>): T {
        return RUNTIME_PROPERTIES.computeIfAbsent(property.name) { default() } as T
    }

    override fun setValue(thisRef: Any?, property: KProperty<*>, value: T) {
        RUNTIME_PROPERTIES[property.name] = value
    }
}
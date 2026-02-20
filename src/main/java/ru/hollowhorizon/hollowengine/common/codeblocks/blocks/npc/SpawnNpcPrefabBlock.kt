package ru.hollowhorizon.hollowengine.common.codeblocks.blocks.npc

import com.mineinabyss.geary.helpers.temporaryEntity
import de.fabmax.kool.modules.ui2.*
import de.fabmax.kool.util.Color
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import kotlinx.serialization.Transient
import net.minecraft.resources.ResourceKey
import net.minecraft.server.MinecraftServer
import net.minecraft.world.level.Level
import net.minecraft.world.phys.Vec3
import com.mineinabyss.geary.prefabs.entityOfOrNull
import ru.hollowhorizon.hollowengine.client.gui.codeblocks.InputSlotScope
import ru.hollowhorizon.hollowengine.client.gui.colors.Dimensions
import ru.hollowhorizon.hollowengine.common.codeblocks.model.ExpressionBlock
import ru.hollowhorizon.hollowengine.common.codeblocks.typeOf
import ru.hollowhorizon.hollowengine.common.entities.NpcEntity
import ru.hollowhorizon.hollowengine.common.files.DirectoryManager.fromReadablePath
import ru.hollowhorizon.hollowengine.common.geary.api.entity
import ru.hollowhorizon.hollowengine.common.geary.api.geary
import ru.hollowhorizon.hollowengine.common.geary.tracking.datastore.setAllSyncablePersisting
import ru.hollowhorizon.hollowengine.common.geary.tracking.datastore.formats
import ru.hollowhorizon.hollowengine.common.geary.tracking.datastore.serializers
import ru.hollowhorizon.hollowengine.common.utils.currentServer
import ru.hollowhorizon.hollowengine.common.utils.rl

@Serializable
@SerialName("hollowengine:npc/spawn_prefab")
class SpawnNpcPrefabBlock(
    val prefabPath: String = "",
) : ExpressionBlock() {
    @Transient
    override val expressionType = typeOf<NpcEntity>()

    val pos by input<Vec3>("pos")
    val dimension by input<ResourceKey<Level>>("dimension")

    @Serializable
    private data class PrefabYaml(
        val components: List<PrefabComponentYaml> = emptyList(),
    )

    @Serializable
    private data class PrefabComponentYaml(
        val key: String,
        val data: String,
    )

    override suspend fun execute(): Any? {
        val server: MinecraftServer = currentServer
        val level = server.getLevel(dimension())
            ?: error("Dimension ${dimension().location()} is not loaded!")

        val spawnPos = pos()
        val npc = NpcEntity(level).apply {
            setPos(spawnPos.x, spawnPos.y, spawnPos.z)
            moveTo(spawnPos.x, spawnPos.y, spawnPos.z, 0f, 0f)
            refreshDimensions()
            level.addFreshEntity(this)
        }

        if (prefabPath.isBlank()) return npc

        val file = prefabPath.fromReadablePath()
        if (file.exists()) {
            val yaml = file.readText()
            val prefabYaml = YamlFormat.decodeFromString<PrefabYaml>(yaml)

            if (prefabYaml.components.isNotEmpty()) {
                val geary = npc.level().geary
                geary.temporaryEntity { prefabEntity ->
                    val decodedComponents =
                        ArrayList<com.mineinabyss.geary.datatypes.Component>(prefabYaml.components.size)
                    prefabYaml.components.forEach { comp ->
                        val key = comp.key.rl
                        val holder =
                            ru.hollowhorizon.hollowengine.common.geary.components.ComponentRegistry.getOrNull(key)
                                ?: return@forEach

                        @Suppress("UNCHECKED_CAST")
                        val serializer = holder.serializer as kotlinx.serialization.KSerializer<Any>
                        val decoded = runCatching { YamlFormat.yaml.decodeFromString(serializer, comp.data) }
                            .getOrNull() as? com.mineinabyss.geary.datatypes.Component
                            ?: return@forEach

                        decodedComponents += decoded
                    }

                    if (decodedComponents.isNotEmpty()) {
                        prefabEntity.setAllSyncablePersisting(decodedComponents)
                    }

                    npc.entity.extend(prefabEntity)
                }
            }
        }

        return npc
    }

    override fun InputSlotScope.composeContent() {
        Column(Grow.Std) {
            Row(Grow.Std) {
                Text("Создать ${prefabPath.removeSuffix(".entity.prefab").ifBlank { "null" }}") {
                    modifier.textColor(Color.WHITE).alignY(AlignmentY.Center).bold()
                }
            }
            Box { modifier.margin(Dimensions.PaddingNormal.scaled()) }
            Row(Grow.Std) {
                Text("Измерение:") { modifier.textColor(Color.WHITE).alignY(AlignmentY.Center).bold() }
                Box(Grow.Std) {}
                InputSlot(dimension)
            }
            Box { modifier.margin(Dimensions.PaddingNormal.scaled()) }
            Row(Grow.Std) {
                Text("Позиция:") { modifier.textColor(Color.WHITE).alignY(AlignmentY.Center).bold() }
                Box(Grow.Std) {}
                InputSlot(pos)
            }
        }
    }
}

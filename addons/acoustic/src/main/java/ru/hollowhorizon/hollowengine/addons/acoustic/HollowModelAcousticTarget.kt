package ru.hollowhorizon.hollowengine.addons.acoustic

import kotlinx.serialization.Serializable
import net.minecraft.resources.ResourceLocation
import net.minecraft.world.entity.player.Player
import ru.hollowhorizon.hollowengine.addons.acoustic.client.AcousticModelAttachmentPublisher
import ru.hollowhorizon.hollowengine.common.integrations.acoustic.AcousticSource
import ru.hollowhorizon.hollowengine.common.integrations.acoustic.HollowModelAcousticAnchor
import ru.hollowhorizon.hollowengine.common.network.HollowAddonPacket
import ru.hollowhorizon.hollowengine.common.network.HollowPacketHandler
import ru.hollowhorizon.hollowengine.common.utils.nbt.ForUuid
import java.util.UUID

@Serializable
internal data class HollowModelAcousticTarget(
    val entityUuid: @Serializable(ForUuid::class) UUID,
    val nodeId: @Serializable(ForUuid::class) UUID,
    val anchorKind: HollowModelAnchorKind,
    val anchorSegments: List<String> = emptyList(),
) {
    init {
        when (anchorKind) {
            HollowModelAnchorKind.ROOT -> require(anchorSegments.isEmpty()) {
                "A model-root Acoustic target cannot contain bone segments"
            }
            HollowModelAnchorKind.UNIQUE_BONE_NAME -> require(anchorSegments.size == 1) {
                "A bone-name Acoustic target needs exactly one name"
            }
            HollowModelAnchorKind.BONE_PATH -> require(anchorSegments.isNotEmpty()) {
                "A bone-path Acoustic target cannot be empty"
            }
        }
        require(anchorSegments.none(String::isBlank)) { "Acoustic bone segments cannot be blank" }
    }

    val attachmentId: ResourceLocation
        get() = ResourceLocation.fromNamespaceAndPath(RESOURCE_NAMESPACE, buildString {
            append("acoustic/model/")
            append(entityUuid.compact())
            append('/')
            append(nodeId.compact())
            append('/')
            when (anchorKind) {
                HollowModelAnchorKind.ROOT -> append("root")
                HollowModelAnchorKind.UNIQUE_BONE_NAME -> {
                    append("bone/")
                    append(anchorSegments.single().utf8Hex())
                }
                HollowModelAnchorKind.BONE_PATH -> {
                    append("path")
                    anchorSegments.forEach { segment -> append('/').append(segment.utf8Hex()) }
                }
            }
        })

    companion object {
        fun from(source: AcousticSource.HollowModel): HollowModelAcousticTarget {
            val (kind, segments) = when (val anchor = source.anchor) {
                HollowModelAcousticAnchor.Root -> HollowModelAnchorKind.ROOT to emptyList()
                is HollowModelAcousticAnchor.BoneName ->
                    HollowModelAnchorKind.UNIQUE_BONE_NAME to listOf(anchor.name)
                is HollowModelAcousticAnchor.BonePath -> HollowModelAnchorKind.BONE_PATH to anchor.segments
            }
            return HollowModelAcousticTarget(source.entity.uuid, source.nodeId, kind, segments)
        }

        private const val RESOURCE_NAMESPACE = "hollowengine"
    }
}

@Serializable
internal enum class HollowModelAnchorKind {
    ROOT,
    UNIQUE_BONE_NAME,
    BONE_PATH,
}

@Serializable
@HollowPacketHandler(HollowPacketHandler.Direction.TO_CLIENT)
internal data class SetHollowModelAcousticTargetPacket(
    val playbackId: String,
    val target: HollowModelAcousticTarget?,
) : HollowAddonPacket {
    init {
        require(playbackId.isNotBlank()) { "Acoustic playback ID cannot be blank" }
    }

    override fun handle(player: Player) {
        if (!player.level().isClientSide) return
        AcousticModelAttachmentPublisher.setTarget(playbackId, target, player.level())
    }
}

private fun UUID.compact(): String = toString().replace("-", "")

private fun String.utf8Hex(): String = encodeToByteArray().joinToString("") { byte ->
    byte.toUByte().toString(16).padStart(2, '0')
}

package ru.hollowhorizon.hollowengine.client.models.internal.manager

import net.minecraft.client.Minecraft
import net.minecraft.client.resources.DefaultPlayerSkin
import net.minecraft.client.resources.PlayerSkin
import net.minecraft.resources.ResourceLocation
import net.minecraft.world.level.block.entity.SkullBlockEntity
import ru.hollowhorizon.hollowengine.common.events.ClientOnly
import ru.hollowhorizon.hollowengine.common.models.MaterialSource
import ru.hollowhorizon.hollowengine.common.models.PlayerSkinPart
import java.util.*
import java.util.concurrent.CompletableFuture
import java.util.concurrent.ConcurrentHashMap

/** A material source turned into something the renderer can bind. */
data class ResolvedMaterial(
    val texture: ResourceLocation?,
    val normal: ResourceLocation? = null,
    val specular: ResourceLocation? = null,
    val color: String? = null,
    val slim: Boolean? = null,
)

/**
 * Turns a [MaterialSource] into textures.
 */
@ClientOnly
object MaterialSources {
    private val skins = ConcurrentHashMap<String, PlayerSkin>()
    private val pending = ConcurrentHashMap.newKeySet<String>()

    @Volatile
    var generation: Int = 0
        private set

    fun resolve(source: MaterialSource): ResolvedMaterial = when (source) {
        is MaterialSource.Texture -> ResolvedMaterial(
            texture = source.texture,
            normal = source.normal,
            specular = source.specular,
            color = source.color,
            slim = source.slim,
        )

        is MaterialSource.Player -> skinOf(source.player).let { skin ->
            ResolvedMaterial(
                texture = when (source.part) {
                    PlayerSkinPart.SKIN -> skin.texture()
                    PlayerSkinPart.CAPE -> skin.capeTexture()
                    PlayerSkinPart.ELYTRA -> skin.elytraTexture()
                },
                slim = skin.model() == PlayerSkin.Model.SLIM,
            )
        }
    }

    fun clear() {
        skins.clear()
        pending.clear()
    }

    /**
     * The skin of a player named by nickname or uuid.
     */
    private fun skinOf(player: String): PlayerSkin {
        val uuid = player.asUuid()
        val connection = Minecraft.getInstance().connection
        val online = uuid?.let { connection?.getPlayerInfo(it) } ?: connection?.getPlayerInfo(player)
        if (online != null) return online.skin

        skins[player]?.let { return it }
        lookUp(player, uuid)
        return DEFAULT_SKIN(uuid ?: offlineUuid(player))
    }

    private fun lookUp(player: String, uuid: UUID?) {
        if (!pending.add(player)) return

        val profile = uuid?.let { SkullBlockEntity.fetchGameProfile(it) } ?: SkullBlockEntity.fetchGameProfile(player)

        profile.thenCompose { found ->
            found.map(Minecraft.getInstance().skinManager::getOrLoad)
                .orElseGet { CompletableFuture.completedFuture(null) }
        }.whenComplete { skin, _ ->
            pending.remove(player)
            if (skin == null) return@whenComplete
            skins[player] = skin
            generation++
        }
    }

    private fun String.asUuid(): UUID? = runCatching { UUID.fromString(this) }.getOrNull()

    private fun offlineUuid(name: String): UUID =
        UUID.nameUUIDFromBytes("OfflinePlayer:$name".toByteArray(Charsets.UTF_8))

    private val DEFAULT_SKIN: (UUID) -> PlayerSkin = DefaultPlayerSkin::get
}

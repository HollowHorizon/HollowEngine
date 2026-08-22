package ru.hollowhorizon.hollowengine.common.network

import kotlinx.serialization.Serializable
import net.minecraft.resources.ResourceLocation
import net.minecraft.server.level.ServerPlayer
import net.minecraft.server.packs.PackType
import net.minecraft.world.entity.player.Player
import ru.hollowhorizon.hollowengine.client.ui.ide.asset.RemoteServerAssetState
import ru.hollowhorizon.hollowengine.common.utils.PlayerPermissions
import ru.hollowhorizon.hollowengine.common.utils.listPackResources

@Serializable
data class RemoteAssetEntry(
    val namespace: String,
    val path: String,
    val directory: Boolean,
    val sourcePackId: String = "",
)

@HollowPacketHandler(HollowPacketHandler.Direction.TO_SERVER)
@Serializable
class RequestServerAssetDirectoryPacket(
    val namespace: String = "",
    val path: String = "",
    val offset: Int = 0,
    val generation: Int = 0,
) : HollowPacket {
    override fun handle(player: Player) {
        val serverPlayer = player as? ServerPlayer ?: return
        if (!player.hasPermissions(PlayerPermissions.GAMEMASTER)) {
            ServerAssetDirectoryPacket(namespace, path, generation = generation, error = OperatorRequiredError)
                .send(serverPlayer)
            return
        }
        val manager = player.server?.resourceManager ?: run {
            ServerAssetDirectoryPacket(namespace, path, generation = generation, error = ServerUnavailableError)
                .send(serverPlayer)
            return
        }
        val entries = runCatching {
            if (namespace.isEmpty()) {
                manager.namespaces.map { currentNamespace ->
                    RemoteAssetEntry(currentNamespace, "", directory = true)
                }
            } else {
                val cleanPath = path.trim('/')
                require(ResourceLocation.tryBuild(namespace, cleanPath.ifEmpty { "root" }) != null) {
                    "Invalid resource directory: $namespace:$cleanPath"
                }
                val prefix = cleanPath.takeIf(String::isNotEmpty)?.plus('/') ?: ""
                val resources = if (cleanPath.isEmpty()) {
                    manager.listPackResources(PackType.SERVER_DATA, namespace)
                        .map { resource -> resource.location to resource.sourcePackId }
                } else {
                    manager.listResources(cleanPath) { location ->
                        location.namespace == namespace && location.path.startsWith(prefix)
                    }.map { (location, resource) -> location to resource.sourcePackId() }
                }
                resources.mapNotNull { (location, sourcePackId) ->
                    val remainder = location.path.removePrefix(prefix)
                    if (remainder.isEmpty()) return@mapNotNull null
                    val childName = remainder.substringBefore('/')
                    val childPath = if (cleanPath.isEmpty()) childName else "$cleanPath/$childName"
                    RemoteAssetEntry(
                        namespace = namespace,
                        path = childPath,
                        directory = '/' in remainder,
                        sourcePackId = sourcePackId,
                    )
                }.distinctBy { entry -> entry.directory to entry.path }
            }.sortedWith(compareBy<RemoteAssetEntry>({ !it.directory }, { it.namespace }, { it.path }))
        }.getOrElse {
            ServerAssetDirectoryPacket(
                namespace,
                path,
                generation = generation,
                error = ListResourcesFailedError,
            ).send(serverPlayer)
            return
        }

        val first = offset.coerceIn(0, entries.size)
        val end = (first + DirectoryPageSize).coerceAtMost(entries.size)
        ServerAssetDirectoryPacket(
            namespace = namespace,
            path = path.trim('/'),
            entries = entries.subList(first, end),
            nextOffset = end.takeIf { it < entries.size },
            generation = generation,
        ).send(serverPlayer)
    }
}

@HollowPacketHandler(HollowPacketHandler.Direction.TO_CLIENT)
@Serializable
class ServerAssetDirectoryPacket(
    val namespace: String,
    val path: String,
    val entries: List<RemoteAssetEntry> = emptyList(),
    val nextOffset: Int? = null,
    val generation: Int = 0,
    val error: String? = null,
) : HollowPacket {
    override fun handle(player: Player) {
        RemoteServerAssetState.accept(this)
    }
}

@HollowPacketHandler(HollowPacketHandler.Direction.TO_SERVER)
@Serializable
class RequestServerAssetFilePacket(
    val namespace: String,
    val path: String,
    val offset: Int = 0,
    val generation: Int,
) : HollowPacket {
    override fun handle(player: Player) {
        val serverPlayer = player as? ServerPlayer ?: return
        if (!player.hasPermissions(PlayerPermissions.GAMEMASTER)) {
            ServerAssetFilePacket(namespace, path, generation = generation, error = OperatorRequiredError)
                .send(serverPlayer)
            return
        }
        val manager = player.server?.resourceManager ?: run {
            ServerAssetFilePacket(namespace, path, generation = generation, error = ServerUnavailableError)
                .send(serverPlayer)
            return
        }
        val location = ResourceLocation.tryBuild(namespace, path)
        val bytes = runCatching {
            requireNotNull(location) { "Invalid resource location: $namespace:$path" }
            require(offset in 0 until MaxRemoteAssetBytes) { "Invalid resource chunk offset." }
            manager.getResource(location).orElseThrow().open().use { input ->
                input.skipNBytes(offset.toLong())
                input.readNBytes(FileChunkSize + 1)
            }
        }.getOrElse {
            ServerAssetFilePacket(
                namespace,
                path,
                generation = generation,
                error = ReadResourceFailedError,
            ).send(serverPlayer)
            return
        }
        val hasMore = bytes.size > FileChunkSize
        if (hasMore && offset + FileChunkSize >= MaxRemoteAssetBytes) {
            ServerAssetFilePacket(
                namespace,
                path,
                generation = generation,
                error = PreviewTooLargeError,
            ).send(serverPlayer)
            return
        }
        ServerAssetFilePacket(
            namespace,
            path,
            generation = generation,
            bytes = if (hasMore) bytes.copyOf(FileChunkSize) else bytes,
            nextOffset = (offset + FileChunkSize).takeIf { hasMore },
        ).send(serverPlayer)
    }
}

@HollowPacketHandler(HollowPacketHandler.Direction.TO_CLIENT)
@Serializable
class ServerAssetFilePacket(
    val namespace: String,
    val path: String,
    val generation: Int,
    val bytes: ByteArray = ByteArray(0),
    val nextOffset: Int? = null,
    val error: String? = null,
) : HollowPacket {
    override fun handle(player: Player) {
        RemoteServerAssetState.accept(this)
    }
}

private const val DirectoryPageSize = 512
private const val FileChunkSize = 256 * 1024
private const val MaxRemoteAssetBytes = 16 * 1024 * 1024
private const val AssetManagerErrorRoot = "hollowengine.gui.ide.asset_manager.error."
private const val OperatorRequiredError = AssetManagerErrorRoot + "operator_required"
private const val ServerUnavailableError = AssetManagerErrorRoot + "server_unavailable"
private const val ListResourcesFailedError = AssetManagerErrorRoot + "list_resources_failed"
private const val ReadResourceFailedError = AssetManagerErrorRoot + "read_resource_failed"
private const val PreviewTooLargeError = AssetManagerErrorRoot + "preview_too_large"

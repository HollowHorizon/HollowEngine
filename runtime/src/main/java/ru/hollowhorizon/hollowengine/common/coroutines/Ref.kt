package ru.hollowhorizon.hollowengine.common.coroutines

import net.minecraft.server.MinecraftServer
import net.minecraft.server.level.ServerPlayer
import net.minecraft.world.entity.Entity
import ru.hollowhorizon.hollowengine.common.events.entity.EntityLoadedEvent
import ru.hollowhorizon.hollowengine.common.events.entity.player.PlayerEvent
import ru.hollowhorizon.hollowengine.common.events.factory.await
import java.util.*

interface Ref<T> {
    val isLinkAlive: Boolean

    suspend fun resolve(): T

    suspend fun <V> read(value: T.() -> V) = resolve().value()
    suspend fun update(action: T.() -> Unit) = resolve().action()
}

@Suppress("UNCHECKED_CAST")
context(server: MinecraftServer)
fun <T : Entity> entityRef(uuid: UUID): Ref<T> {
    return object : Ref<T> {
        override val isLinkAlive: Boolean
            get() = server.allLevels.any { it.getEntity(uuid) != null }

        override suspend fun resolve(): T {
            server.allLevels.forEach { level ->
                level.getEntity(uuid)?.let { return it as T }
            }

            return EntityLoadedEvent.await { it.entity.uuid == uuid }.entity as T
        }
    }
}

context(server: MinecraftServer)
val <T: Entity> T.entityRef: Ref<T>
    get() = entityRef(this.uuid)

context(server: MinecraftServer)
fun playerRef(name: String) = object : Ref<ServerPlayer> {
    override val isLinkAlive: Boolean
        get() = server.playerList.getPlayerByName(name) != null

    override suspend fun resolve(): ServerPlayer {
        server.playerList.getPlayerByName(name)?.let { return it }

        return PlayerEvent.Join.await { it.player.gameProfile.name == name }.player as ServerPlayer
    }
}

context(server: MinecraftServer)
fun playerRef(uuid: UUID) = object : Ref<ServerPlayer> {
    override val isLinkAlive: Boolean
        get() = server.playerList.getPlayer(uuid) != null

    override suspend fun resolve(): ServerPlayer {
        server.playerList.getPlayer(uuid)?.let { return it }

        return PlayerEvent.Join.await { it.player.uuid == uuid }.player as ServerPlayer
    }
}

suspend fun <A, B, C> borrow(first: Ref<A>, second: Ref<B>, action: (first: A, second: B) -> C): C {
    while (true) {
        val f = first.resolve()
        val s = second.resolve()

        // За время получения второй сущности вторая могла устареть
        if (first.isLinkAlive && second.isLinkAlive) {
            return action(f, s)
        }
    }
}

suspend fun <A, B, C, D> borrow(
    first: Ref<A>,
    second: Ref<B>,
    thrid: Ref<C>,
    action: (first: A, second: B, thrid: C) -> D,
): D {
    while (true) {
        val f = first.resolve()
        val s = second.resolve()
        val t = thrid.resolve()

        // За время получения второй сущности вторая могла устареть
        if (first.isLinkAlive && second.isLinkAlive && thrid.isLinkAlive) {
            return action(f, s, t)
        }
    }
}
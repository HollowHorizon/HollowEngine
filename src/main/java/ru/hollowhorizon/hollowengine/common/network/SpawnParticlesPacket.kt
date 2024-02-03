package ru.hollowhorizon.hollowengine.common.network

import net.minecraft.client.Minecraft
import net.minecraft.core.particles.ParticleOptions
import net.minecraft.core.particles.ParticleType
import net.minecraft.network.FriendlyByteBuf
import net.minecraftforge.network.NetworkEvent
import net.minecraftforge.registries.ForgeRegistries
import java.util.function.Supplier

//TODO: Сделать нормальный пакет с нормальной сериализацией...
class SpawnParticlesPacket(
    val options: ParticleOptions,
    val spawnX: Double,
    val spawnY: Double,
    val spawnZ: Double,
    val moveX: Double,
    val moveY: Double,
    val moveZ: Double
) {
    fun write(buf: FriendlyByteBuf) {
        buf.writeRegistryId(options.type)
        options.writeToNetwork(buf)
        buf.writeDouble(this.spawnX)
        buf.writeDouble(this.spawnY)
        buf.writeDouble(this.spawnZ)
        buf.writeDouble(this.moveX)
        buf.writeDouble(this.moveY)
        buf.writeDouble(this.moveZ)
    }

    companion object {
        @JvmStatic
        fun read(buf: FriendlyByteBuf): SpawnParticlesPacket {
            val type = buf.readRegistryId<ParticleType<*>>() as ParticleType<ParticleOptions>
            return SpawnParticlesPacket(
                type.deserializer.fromNetwork(type, buf),
                buf.readDouble(), buf.readDouble(), buf.readDouble(),
                buf.readDouble(), buf.readDouble(), buf.readDouble()
            )
        }

        @JvmStatic
        fun handle(data: SpawnParticlesPacket, ctx: Supplier<NetworkEvent.Context>) {
            ctx.get().apply {
                packetHandled = true
                enqueueWork {
                    val world = Minecraft.getInstance().level

                    world?.addParticle(
                        data.options,
                        true,
                        data.spawnX, data.spawnY, data.spawnZ,
                        data.moveX, data.moveY, data.moveZ
                    )
                }
            }
        }
    }
}
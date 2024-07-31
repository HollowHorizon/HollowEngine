package ru.hollowhorizon.hollowengine.common.entities

import net.minecraft.core.BlockPos
import net.minecraft.core.Direction
import net.minecraft.nbt.CompoundTag
import net.minecraft.network.protocol.Packet
import net.minecraft.util.Mth
import net.minecraft.world.entity.Entity
import net.minecraft.world.entity.LivingEntity
import net.minecraft.world.entity.ai.behavior.SetWalkTargetAwayFrom.pos
import net.minecraft.world.entity.player.Player
import net.minecraft.world.entity.vehicle.DismountHelper
import net.minecraft.world.level.Level
import net.minecraft.world.phys.AABB
import net.minecraft.world.phys.Vec3
import net.minecraftforge.network.NetworkHooks
import ru.hollowhorizon.hollowengine.common.registry.ModEntities


class SeatEntity(pLevel: Level) : Entity(ModEntities.SEAT.get(), pLevel) {
    companion object {
        @JvmStatic
        fun seat(player: Player, p: BlockPos, yOffset: Double, dir: Direction) {
            val level = player.level

            val pos = if (p.x == 0 && p.y == 0 && p.z == 0) BlockPos(player.position()) else p

            if (!level.isClientSide()) {
                val seats: List<SeatEntity> = level.getEntitiesOfClass(
                    SeatEntity::class.java,
                    AABB(pos.x.toDouble(), pos.y.toDouble(), pos.z.toDouble(), pos.x + 1.0, pos.y + 1.0, pos.z + 1.0)
                )
                if (seats.isEmpty()) {
                    val seat = SeatEntity(level, pos, yOffset, dir)
                    level.addFreshEntity(seat)
                    player.startRiding(seat, false)
                }
            }
        }
    }

    constructor(level: Level, pos: BlockPos, yOffset: Double, dir: Direction): this(level) {
        this.setPos(pos.x + 0.5, pos.y + yOffset, pos.z + 0.5)
        this.setRot(dir.opposite.toYRot(), 0F)
    }

    init {
        this.noPhysics = true
    }

    override fun tick() {
        super.tick()
        if (!this.level.isClientSide) {
            if (this.passengers.isEmpty() || this.level.isEmptyBlock(this.blockPosition())) {
                this.remove(RemovalReason.DISCARDED)
                this.level.updateNeighbourForOutputSignal(this.blockPosition(), this.level.getBlockState(this.blockPosition()).block)
            }
        }
    }

    override fun defineSynchedData() {}

    override fun readAdditionalSaveData(p0: CompoundTag) {}

    override fun addAdditionalSaveData(p0: CompoundTag) {}

    override fun getPassengersRidingOffset(): Double = 0.0

    override fun canRide(pVehicle: Entity): Boolean = true

    override fun getAddEntityPacket(): Packet<*> = NetworkHooks.getEntitySpawningPacket(this)

    override fun getDismountLocationForPassenger(entity: LivingEntity): Vec3 {
        val original = this.direction
        val offsets = arrayOf(original, original.clockWise, original.counterClockWise, original.opposite)
        for (dir in offsets) {
            val safeVec = DismountHelper.findSafeDismountLocation(
                entity.type, this.level,
                blockPosition().relative(dir), false
            )
            if (safeVec != null) {
                return safeVec.add(0.0, 0.25, 0.0)
            }
        }
        return super.getDismountLocationForPassenger(entity)
    }

    override fun addPassenger(entity: Entity) {
        super.addPassenger(entity)
        entity.yRot = yRot
    }

    override fun positionRider(entity: Entity) {
        super.positionRider(entity)
        this.yaw(entity)
    }

    override fun onPassengerTurned(entity: Entity) {
        this.yaw(entity)
    }

    private fun yaw(passenger: Entity) {
        passenger.setYBodyRot(this.yRot)
        val wrappedYaw = Mth.wrapDegrees(passenger.yRot - this.yRot)
        val clampedYaw = Mth.clamp(wrappedYaw, -120.0f, 120.0f)
        passenger.yRotO += clampedYaw - wrappedYaw
        passenger.yRot = passenger.yRot + clampedYaw - wrappedYaw
        passenger.yHeadRot = passenger.yRot
    }
}
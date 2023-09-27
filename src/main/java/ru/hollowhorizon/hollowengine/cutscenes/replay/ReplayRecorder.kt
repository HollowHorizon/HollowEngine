package ru.hollowhorizon.hollowengine.cutscenes.replay

import net.minecraft.world.entity.player.Player
import net.minecraftforge.common.MinecraftForge
import net.minecraftforge.event.TickEvent
import net.minecraftforge.event.TickEvent.ServerTickEvent
import net.minecraftforge.event.entity.player.PlayerInteractEvent
import net.minecraftforge.event.world.BlockEvent

import net.minecraftforge.eventbus.api.SubscribeEvent
import ru.hollowhorizon.hc.client.utils.nbt.save
import java.io.File

class ReplayRecorder(val player: Player) {
    val replay = Replay()
    var isRecording = false
    val brokenBlocks: ArrayList<ReplayBlock> = ArrayList()
    val placedBlocks: ArrayList<ReplayBlock> = ArrayList()
    val usedBlocks: ArrayList<ReplayBlock> = ArrayList()
    var name = "replay"

    fun startRecording(name: String) {
        replay.clear()
        isRecording = true
        MinecraftForge.EVENT_BUS.register(this)
        this.name = name
    }

    fun stopRecording() {
        isRecording = false
        MinecraftForge.EVENT_BUS.unregister(this)
        val nbt = Replay.toNBT(replay)

        val file = File("replays/$name.hc")
        if(!file.exists()) {
            if(!file.parentFile.exists()) file.parentFile.mkdirs()
            file.createNewFile()
        }
        val outStream = file.outputStream()
        nbt.save(outStream)
    }

    fun recordTick() {
        replay.addPointFromPlayer(this, player)
        brokenBlocks.clear()
        placedBlocks.clear()
        usedBlocks.clear()
    }

    @SubscribeEvent
    fun onPlayerTick(event: ServerTickEvent) {
        if (event.phase == TickEvent.Phase.END && isRecording) {
            recordTick()
        }
    }

    @SubscribeEvent
    fun onBlockPlaced(event: BlockEvent.BreakEvent) {
        if (isRecording) {
            //brokenBlocks.add(ReplayBlock(event.pos, event.state.block.registryName!!.toString()))
        }
    }

    @SubscribeEvent
    fun onBlockBroken(event: BlockEvent.EntityPlaceEvent) {
        if (isRecording) {
            //placedBlocks.add(ReplayBlock(event.pos, event.state.block.registryName!!.toString()))
        }
    }

    fun onBlockUse(event: PlayerInteractEvent.RightClickBlock) {
        if (isRecording) {
            //usedBlocks.add(ReplayBlock(event.pos, event.itemStack.item.registryName!!.toString()))
        }
    }

    companion object {
        val recorders: HashMap<Player, ReplayRecorder> = HashMap()

        fun getRecorder(player: Player): ReplayRecorder {
            if (!recorders.containsKey(player)) {
                recorders[player] = ReplayRecorder(player)
            }
            return recorders[player]!!
        }
    }
}
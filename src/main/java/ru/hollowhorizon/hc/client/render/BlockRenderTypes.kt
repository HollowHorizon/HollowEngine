package ru.hollowhorizon.hc.client.render

//? if fabric {
import net.fabricmc.fabric.api.blockrenderlayer.v1.BlockRenderLayerMap
//?} else {
/*import net.minecraft.client.renderer.ItemBlockRenderTypes
*///?}
import net.minecraft.client.renderer.RenderType
import net.minecraft.world.level.block.Block

object BlockRenderTypes {
    operator fun set(block: Block, type: RenderType) {
        //? if fabric {
        BlockRenderLayerMap.INSTANCE.putBlock(block, type)
        //?} else {
        /*ItemBlockRenderTypes.setRenderLayer(block, type)
        *///?}
    }
}
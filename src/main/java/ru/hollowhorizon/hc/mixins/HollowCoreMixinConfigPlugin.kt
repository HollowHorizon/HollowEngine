package ru.hollowhorizon.hc.mixins

import org.objectweb.asm.tree.ClassNode
import org.spongepowered.asm.mixin.extensibility.IMixinConfigPlugin
import org.spongepowered.asm.mixin.extensibility.IMixinInfo
import ru.hollowhorizon.hc.LOGGER

class HollowCoreMixinConfigPlugin : IMixinConfigPlugin {
    init {
        LOGGER.info("HollowCore Loading Plugin")
    }

    override fun onLoad(mixinPackage: String) {}
    override fun getRefMapperConfig() = null
    override fun shouldApplyMixin(targetClassName: String, mixinClassName: String) = true
    override fun acceptTargets(myTargets: MutableSet<String>, otherTargets: MutableSet<String>) {}
    override fun getMixins() = emptyList<String>()
    override fun preApply(
        targetClassName: String,
        targetClass: ClassNode,
        mixinClassName: String,
        mixinInfo: IMixinInfo,
    ) {
    }
    override fun postApply(
        targetClassName: String,
        targetClass: ClassNode,
        mixinClassName: String,
        mixinInfo: IMixinInfo,
    ) {
    }
}
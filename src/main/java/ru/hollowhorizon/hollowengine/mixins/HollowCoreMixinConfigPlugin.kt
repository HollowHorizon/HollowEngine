package ru.hollowhorizon.hollowengine.mixins

import org.objectweb.asm.tree.ClassNode
import org.spongepowered.asm.mixin.extensibility.IMixinConfigPlugin
import org.spongepowered.asm.mixin.extensibility.IMixinInfo
import ru.hollowhorizon.hollowengine.LOGGER

class HollowCoreMixinConfigPlugin : IMixinConfigPlugin {
    private val hasIrisClasses by lazy { isClassPresent("net.irisshaders.iris.Iris") }

    init {
        LOGGER.info("HollowEngine Loading Plugin")
    }

    override fun onLoad(mixinPackage: String) {}
    override fun getRefMapperConfig() = null
    override fun shouldApplyMixin(targetClassName: String, mixinClassName: String): Boolean {
        if (mixinClassName.contains(".client.iris.")) {
            return hasIrisClasses
        }
        return true
    }
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

    private fun isClassPresent(name: String): Boolean {
        return try {
            Class.forName(name, false, javaClass.classLoader)
            true
        } catch (_: ClassNotFoundException) {
            false
        }
    }
}

package ru.hollowhorizon.hollowengine.common.vfx

import kotlinx.serialization.KSerializer
import kotlinx.serialization.modules.PolymorphicModuleBuilder
import kotlinx.serialization.modules.SerializersModuleBuilder
import kotlinx.serialization.modules.polymorphic
import net.minecraft.resources.ResourceLocation
import ru.hollowhorizon.hollowengine.api.extensions.ExtensionHandle
import ru.hollowhorizon.hollowengine.api.extensions.ExtensionPoints
import ru.hollowhorizon.hollowengine.common.utils.rl
import ru.hollowhorizon.hollowengine.common.vfx.modules.VfxCollisionSpec
import ru.hollowhorizon.hollowengine.common.vfx.modules.VfxForceSpec
import ru.hollowhorizon.hollowengine.common.vfx.modules.VfxModuleSpec
import ru.hollowhorizon.hollowengine.common.vfx.modules.VfxNoiseSpec
import ru.hollowhorizon.hollowengine.common.vfx.modules.VfxUvAnimationSpec
import ru.hollowhorizon.hollowengine.common.vfx.modules.VfxVelocityOverLifetimeSpec
import kotlin.reflect.KClass

/**
 * One kind of node an effect may contain: how it is stored, and what the editor calls it.
 */
class VfxNodeType<S : VfxNodeSpec>(
    val id: String,
    val specClass: KClass<S>,
    val serializer: KSerializer<S>,
    val titleKey: String,
    val icon: String? = null,
    val createDefault: (() -> S)? = null,
) {
    val key: ResourceLocation = id.rl
}

object VfxNodeTypes {
    val point = ExtensionPoints.create<VfxNodeType<*>>("hollowengine:vfx/node_types".rl)

    init {
        point.onChange(VfxModuleRevision::invalidate)

        register(
            VfxNodeType(
                id = "hollowengine:vfx/quad_emitter",
                specClass = VfxQuadEmitterSpec::class,
                serializer = VfxQuadEmitterSpec.serializer(),
                titleKey = "hollowengine.gui.vfx.node_quad_emitter",
                icon = "hollowengine:textures/gui/icons/box.svg",
                createDefault = { VfxQuadEmitterSpec() },
            )
        )
        register(
            VfxNodeType(
                id = "hollowengine:vfx/mesh_emitter",
                specClass = VfxMeshEmitterSpec::class,
                serializer = VfxMeshEmitterSpec.serializer(),
                titleKey = "hollowengine.gui.vfx.node_mesh_emitter",
                icon = "hollowengine:textures/gui/icons/file_model.svg",
                createDefault = { VfxMeshEmitterSpec() },
            )
        )
    }

    fun register(type: VfxNodeType<*>): ExtensionHandle = point.register(type.key, type)

    val all: List<VfxNodeType<*>> get() = point.extensions

    fun of(spec: VfxNodeSpec): VfxNodeType<*>? = all.firstOrNull { it.specClass.isInstance(spec) }

    fun registerInto(builder: SerializersModuleBuilder) {
        builder.polymorphic(VfxNodeSpec::class) {
            all.forEach { type -> subclass(type) }
        }
        builder.polymorphic(VfxEmitterSpec::class) {
            all.filter { VfxEmitterSpec::class.java.isAssignableFrom(it.specClass.java) }
                .forEach { type -> emitterSubclass(type) }
        }
    }

    @Suppress("UNCHECKED_CAST")
    private fun PolymorphicModuleBuilder<VfxNodeSpec>.subclass(type: VfxNodeType<*>) = subclass(
        type.specClass as KClass<VfxNodeSpec>,
        type.serializer as KSerializer<VfxNodeSpec>,
    )

    @Suppress("UNCHECKED_CAST")
    private fun PolymorphicModuleBuilder<VfxEmitterSpec>.emitterSubclass(type: VfxNodeType<*>) = subclass(
        type.specClass as KClass<VfxEmitterSpec>,
        type.serializer as KSerializer<VfxEmitterSpec>,
    )
}

/**
 * One kind of emitter module. How it is stored, what the editor calls it, and which nodes accept it.
 */
class VfxModuleType<S : VfxModuleSpec>(
    val id: String,
    val specClass: KClass<S>,
    val serializer: KSerializer<S>,
    val titleKey: String,
    val createDefault: (() -> S)? = null,
    val appliesTo: (VfxEmitterSpec) -> Boolean = { true },
    val repeatable: Boolean = false,
) {
    val key: ResourceLocation = id.rl
}

object VfxModuleTypes {
    val point = ExtensionPoints.create<VfxModuleType<*>>("hollowengine:vfx/module_types".rl)

    init {
        point.onChange(VfxModuleRevision::invalidate)

        register(
            VfxModuleType(
                id = "hollowengine:vfx/velocity_over_lifetime",
                specClass = VfxVelocityOverLifetimeSpec::class,
                serializer = VfxVelocityOverLifetimeSpec.serializer(),
                titleKey = "hollowengine.gui.vfx.module_velocity_over_lifetime",
                createDefault = { VfxVelocityOverLifetimeSpec() },
            )
        )
        register(
            VfxModuleType(
                id = "hollowengine:vfx/force",
                specClass = VfxForceSpec::class,
                serializer = VfxForceSpec.serializer(),
                titleKey = "hollowengine.gui.vfx.module_force",
                createDefault = { VfxForceSpec() },
                repeatable = true,
            )
        )
        register(
            VfxModuleType(
                id = "hollowengine:vfx/noise",
                specClass = VfxNoiseSpec::class,
                serializer = VfxNoiseSpec.serializer(),
                titleKey = "hollowengine.gui.vfx.module_noise",
                createDefault = { VfxNoiseSpec() },
            )
        )
        register(
            VfxModuleType(
                id = "hollowengine:vfx/collision",
                specClass = VfxCollisionSpec::class,
                serializer = VfxCollisionSpec.serializer(),
                titleKey = "hollowengine.gui.vfx.module_collision",
                createDefault = { VfxCollisionSpec() },
            )
        )
        register(
            VfxModuleType(
                id = "hollowengine:vfx/uv_animation",
                specClass = VfxUvAnimationSpec::class,
                serializer = VfxUvAnimationSpec.serializer(),
                titleKey = "hollowengine.gui.vfx.module_uv_animation",
                createDefault = { VfxUvAnimationSpec() },
                appliesTo = { it is VfxQuadEmitterSpec },
            )
        )
    }

    fun register(type: VfxModuleType<*>): ExtensionHandle = point.register(type.key, type)

    val all: List<VfxModuleType<*>> get() = point.extensions

    fun of(spec: VfxModuleSpec): VfxModuleType<*>? = all.firstOrNull { it.specClass.isInstance(spec) }

    fun forEmitter(emitter: VfxEmitterSpec): List<VfxModuleType<*>> = all.filter { it.appliesTo(emitter) }

    fun registerInto(builder: SerializersModuleBuilder) {
        builder.polymorphic(VfxModuleSpec::class) {
            all.forEach { type -> subclass(type) }
        }
    }

    @Suppress("UNCHECKED_CAST")
    private fun PolymorphicModuleBuilder<VfxModuleSpec>.subclass(type: VfxModuleType<*>) = subclass(
        type.specClass as KClass<VfxModuleSpec>,
        type.serializer as KSerializer<VfxModuleSpec>,
    )
}

/**
 * Says when the serializers module of [VfxFormat] has to be built again, which is whenever an addon
 * registers a node or module kind.
 */
object VfxModuleRevision {
    @Volatile
    var current: Int = 0
        private set

    @Synchronized
    fun invalidate() {
        current++
    }
}

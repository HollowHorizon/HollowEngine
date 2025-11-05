package ru.hollowhorizon.hollowengine.client.models.internal


import com.mojang.blaze3d.vertex.PoseStack
import de.fabmax.kool.math.Mat4f
import de.fabmax.kool.math.MutableMat4f
import de.fabmax.kool.math.MutableQuatF
import de.fabmax.kool.math.QuatF
import de.fabmax.kool.scene.TrsTransformF
import net.minecraft.client.renderer.MultiBufferSource
import net.minecraft.resources.ResourceLocation
import net.minecraft.world.entity.EquipmentSlot
import net.minecraft.world.item.ArmorItem
import org.joml.Quaternionf
import ru.hollowhorizon.hollowengine.client.utils.toTexture
import ru.hollowhorizon.hollowengine.client.utils.use
import ru.hollowhorizon.hollowengine.common.utils.getArmorTexture
import java.util.*

class Node(
    val index: Int,
    val children: MutableList<Node>,
    val transform: TrsTransformF,
    val mesh: Mesh? = null,
    val skin: Skin? = null,
    val name: String? = null,
) {
    val baseTransform = TrsTransformF().apply {
        translate(transform.translation)
        rotate(transform.rotation)
        scale(transform.scale)
    }

    fun renderBatching(
        stack: PoseStack,
        nodeRenderer: NodeRenderer,
        data: ModelData,
        source: MultiBufferSource,
        overlayCoords: Int,
        packedLight: Int,
    ) {
        stack.use {
            translate(transform.translation.x, transform.translation.y, transform.translation.z)
            mulPose(Quaternionf(transform.rotation.x, transform.rotation.y, transform.rotation.z, transform.rotation.w))
            scale(transform.scale.x, transform.scale.y, transform.scale.z)

            mesh?.renderBatching(stack, source, overlayCoords, packedLight)
            data.entity?.let {
                nodeRenderer(it, this, this@Node, source, packedLight)
            }

            children.forEach { it.renderBatching(stack, nodeRenderer, data, source, overlayCoords, packedLight) }
        }
    }

    fun transformSkinning() {
        mesh?.transformSkinning(this@Node)
        children.forEach { it.transformSkinning() }
    }

    val isArmor = name?.contains("armor", ignoreCase = true) == true
    val isHelmet = isArmor && name?.contains("helmet", ignoreCase = true) == true
    val isChestplate = isArmor && name?.contains("chestplate", ignoreCase = true) == true
    val isLeggings = isArmor && name?.contains("leggings", ignoreCase = true) == true
    val isBoots = isArmor && name?.contains("boots", ignoreCase = true) == true
    var isVisible = true

    fun renderVAO(
        stack: PoseStack
    ) {
        if (!isVisible) return

        stack.use {
            translate(transform.translation.x, transform.translation.y, transform.translation.z)
            mulPose(Quaternionf(transform.rotation.x, transform.rotation.y, transform.rotation.z, transform.rotation.w))
            scale(transform.scale.x, transform.scale.y, transform.scale.z)

            mesh?.renderVAO(stack)
            children.forEach { it.renderVAO(stack) }
        }
    }

    var parent: Node? = null
    val root: Node by lazy { parent?.root ?: this }
    val isHead: Boolean get() = name?.lowercase()?.contains("head") == true && parent?.isHead == false

    val globalMatrix: Mat4f
        get() {
            return MutableMat4f(NODE_GLOBAL_TRANSFORMATION_LOOKUP_CACHE.computeIfAbsent(this) {
                val matrix = MutableMat4f(parent?.globalMatrix ?: return@computeIfAbsent localMatrix)
                return@computeIfAbsent matrix.mul(localMatrix)
            })
        }

    val globalRotation: QuatF
        get() {
            var rotation = parent?.globalRotation ?: return transform.rotation
            transform.apply {
                rotation = rotation.mul(this.rotation, MutableQuatF())
            }
            return rotation
        }

    private val localMatrix get() = transform.matrixF

    fun allBones(): Set<Node> = setOf(this) + children.flatMap { it.allBones() }
    val path: String get() = parent?.let { it.name + "/" + name } ?: name ?: "Unnamed Bone"

    override fun toString(): String {
        return "Node $name [Mesh: $mesh, Skin: $skin]"
    }
}

val NODE_GLOBAL_TRANSFORMATION_LOOKUP_CACHE = IdentityHashMap<Node, Mat4f>()
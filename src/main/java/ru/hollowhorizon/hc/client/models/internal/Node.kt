package ru.hollowhorizon.hc.client.models.internal


import com.mojang.blaze3d.vertex.PoseStack
import de.fabmax.kool.math.*
import de.fabmax.kool.scene.TrsTransformF
import net.minecraft.client.renderer.MultiBufferSource
import net.minecraft.resources.ResourceLocation
import net.minecraft.world.entity.EquipmentSlot
import net.minecraft.world.item.ArmorItem
import org.joml.Quaternionf
import ru.hollowhorizon.hc.client.utils.toTexture
import ru.hollowhorizon.hc.client.utils.use
import ru.hollowhorizon.hc.common.utils.getArmorTexture
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

    fun renderDecorations(
        stack: PoseStack,
        nodeRenderer: NodeRenderer,
        data: ModelData,
        source: MultiBufferSource,
        packedLight: Int,
    ) {
        stack.use {
            translate(transform.translation.x, transform.translation.y, transform.translation.z)
            mulPose(Quaternionf(transform.rotation.x, transform.rotation.y, transform.rotation.z, transform.rotation.w))
            scale(transform.scale.x, transform.scale.y, transform.scale.z)

            data.entity?.let {
                nodeRenderer(it, this, this@Node, source, packedLight)
            }

            children.forEach { it.renderDecorations(stack, nodeRenderer, data, source, packedLight) }
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

    fun render(
        stack: PoseStack,
        nodeRenderer: NodeRenderer,
        data: ModelData,
        consumer: (ResourceLocation) -> Int,
        light: Int,
    ) {
        val entity = data.entity
        var changedTexture = consumer
        if (isArmor) {
            if (entity == null) return
            when {
                !entity.getItemBySlot(EquipmentSlot.HEAD).isEmpty && isHelmet -> {
                    val armorItem = entity.getItemBySlot(EquipmentSlot.HEAD)
                    if (armorItem.item is ArmorItem) {
                        val texture = armorItem.getArmorTexture(entity, EquipmentSlot.HEAD)
                        changedTexture = { texture.toTexture().id }
                    }
                }

                !entity.getItemBySlot(EquipmentSlot.CHEST).isEmpty && isChestplate -> {
                    val armorItem = entity.getItemBySlot(EquipmentSlot.CHEST)
                    if (armorItem.item is ArmorItem) {
                        val texture = armorItem.getArmorTexture(entity, EquipmentSlot.CHEST)
                        changedTexture = { texture.toTexture().id }
                    }
                }

                !entity.getItemBySlot(EquipmentSlot.LEGS).isEmpty && isLeggings -> {
                    val armorItem = entity.getItemBySlot(EquipmentSlot.LEGS)
                    if (armorItem.item is ArmorItem) {
                        val texture = armorItem.getArmorTexture(entity, EquipmentSlot.LEGS)
                        changedTexture = { texture.toTexture().id }
                    }
                }

                !entity.getItemBySlot(EquipmentSlot.FEET).isEmpty && isBoots -> {
                    val armorItem = entity.getItemBySlot(EquipmentSlot.FEET)
                    if (armorItem.item is ArmorItem) {
                        val texture = armorItem.getArmorTexture(entity, EquipmentSlot.FEET)
                        changedTexture = { texture.toTexture().id }
                    }
                }

                else -> return
            }
        }

        stack.use {
            translate(transform.translation.x, transform.translation.y, transform.translation.z)
            mulPose(Quaternionf(transform.rotation.x, transform.rotation.y, transform.rotation.z, transform.rotation.w))
            scale(transform.scale.x, transform.scale.y, transform.scale.z)

            mesh?.render(this@Node, stack, changedTexture)
            children.forEach { it.render(stack, nodeRenderer, data, changedTexture, light) }
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
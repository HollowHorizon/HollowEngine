package ru.hollowhorizon.hollowengine.client.screen.npcs

import com.mojang.blaze3d.vertex.PoseStack
import com.mojang.math.Quaternion
import com.mojang.math.Vector3f
import net.minecraft.client.Minecraft
import ru.hollowhorizon.hc.client.models.gltf.GltfTree
import ru.hollowhorizon.hc.client.models.gltf.Transformation
import ru.hollowhorizon.hc.client.models.gltf.manager.AnimatedEntityCapability
import ru.hollowhorizon.hc.client.models.gltf.manager.GltfManager
import ru.hollowhorizon.hc.client.models.gltf.manager.Pose
import ru.hollowhorizon.hc.client.models.gltf.manager.RawPose
import ru.hollowhorizon.hc.client.screens.HollowScreen
import ru.hollowhorizon.hc.common.ui.Alignment
import ru.hollowhorizon.hc.client.screens.widget.LabelWidget
import ru.hollowhorizon.hc.client.screens.widget.button.BaseButton
import ru.hollowhorizon.hc.client.screens.widget.layout.PlacementType
import ru.hollowhorizon.hc.client.screens.widget.layout.box
import ru.hollowhorizon.hc.client.utils.*
import ru.hollowhorizon.hollowengine.client.screen.widget.FloatTextField
import ru.hollowhorizon.hollowengine.client.screen.widget.ModelPreviewWidget
import ru.hollowhorizon.hollowengine.common.entities.NPCEntity
import java.util.*

class ModelEditScreen(val npc: NPCEntity) : HollowScreen() {
    var lastHovered: GltfTree.Node? = null
    val capability = npc[AnimatedEntityCapability::class]
    val model = GltfManager.getOrCreate(capability.model.rl)
    var pose = capability.rawPose
    var xPos: FloatTextField? = null
    var yPos: FloatTextField? = null
    var zPos: FloatTextField? = null
    var xRot: FloatTextField? = null
    var yRot: FloatTextField? = null
    var zRot: FloatTextField? = null
    var xScale: FloatTextField? = null
    var yScale: FloatTextField? = null
    var zScale: FloatTextField? = null

    init {
        Minecraft.getInstance().keyboardHandler.setSendRepeatsToGui(true)
    }

    override fun init() {
        super.init()

        if (pose == null) {
            val node = model.modelTree.walkNodes()[0]
            capability.rawPose = Pose(IdentityHashMap<GltfTree.Node, Transformation>().apply {
                put(node, node.baseTransform.copy())
            })
            pose = capability.rawPose
        }

        addRenderableWidget(ModelPreviewWidget(npc, 0, 0, width / 3, height, width, height))
        box {
            size = 33.pc x 100.pc
            align = Alignment.CENTER
            spacing = 0.px x 0.px

            elements {
                model.modelTree.scenes.flatMap { it.nodes }.forEach {
                    it.depthTraversal({ node, depth ->
                        +BaseButton(
                            depth * 20, 0, 90.pc.w().value, 20, (node.name ?: "Unnamed").mcText,
                            {
                                lastHovered?.isHovered = false
                                node.isHovered = true
                                lastHovered = node

                                xPos?.float = 0f
                                yPos?.float = 0f
                                zPos?.float = 0f

                                xRot?.float = 0f
                                yRot?.float = 0f
                                zRot?.float = 0f

                                xScale?.float = 1f
                                yScale?.float = 1f
                                zScale?.float = 1f
                            },
                            "hollowengine:textures/gui/long_button.png".rl
                        )
                    })
                }
            }
        }

        box {
            size = 33.pc x 100.pc
            align = Alignment.RIGHT_CENTER
            spacing = 0.px x 5.px
            placementType = PlacementType.GRID

            elements {
                val pos = +LabelWidget("Pos".mcText, color = 0xFFFFFF, hoveredColor = 0xFFFFFF)
                xPos = +FloatTextField(
                    0f, 25.pc.w().value, 20, "hollowengine:textures/gui/text_field.png".rl
                ) { float ->
                    lastHovered?.let {
                        pose!!.map.computeIfAbsent(it) { Transformation() }.apply {
                            translationX = float
                        }
                    }
                }.apply {
                    modifier = 0.1f
                }
                yPos = +FloatTextField(
                    0f, 25.pc.w().value, 20, "hollowengine:textures/gui/text_field.png".rl
                ) { float ->
                    lastHovered?.let {
                        pose!!.map.computeIfAbsent(it) { Transformation() }.apply {
                            translationY = float
                        }
                    }
                }.apply {
                    modifier = 0.1f
                }
                zPos = +FloatTextField(
                    0f, 25.pc.w().value, 20, "hollowengine:textures/gui/text_field.png".rl
                ) { float ->
                    lastHovered?.let {
                        pose!!.map.computeIfAbsent(it) { Transformation() }.apply {
                            translationZ = float
                        }
                    }
                }.apply {
                    modifier = 0.1f
                }
                lineBreak()

                val rot = +LabelWidget("Rot".mcText, color = 0xFFFFFF, hoveredColor = 0xFFFFF5)

                xRot = +FloatTextField(0f, 25.pc.w().value, 20, "hollowengine:textures/gui/text_field.png".rl) {
                    val x = it
                    val y = yRot!!.float
                    val z = zRot!!.float
                    lastHovered?.let {
                        pose!!.map.computeIfAbsent(it) { Transformation() }.apply {
                            setRotation(Quaternion.fromXYZDegrees(Vector3f(x, y, z)))
                        }
                    }
                }
                yRot = +FloatTextField(0f, 25.pc.w().value, 20, "hollowengine:textures/gui/text_field.png".rl) {
                    val x = xRot!!.float
                    val y = it
                    val z = zRot!!.float
                    lastHovered?.let {
                        pose!!.map.computeIfAbsent(it) { Transformation() }.apply {
                            setRotation(Quaternion.fromXYZDegrees(Vector3f(x, y, z)))
                        }
                    }
                }
                zRot = +FloatTextField(0f, 25.pc.w().value, 20, "hollowengine:textures/gui/text_field.png".rl) {
                    val x = xRot!!.float
                    val y = yRot!!.float
                    val z = it
                    lastHovered?.let {
                        pose!!.map.computeIfAbsent(it) { Transformation() }.apply {
                            setRotation(Quaternion.fromXYZDegrees(Vector3f(x, y, z)))
                        }
                    }
                }
                lineBreak()

                val scale = +LabelWidget("Scale".mcText, color = 0xFFFFFF, hoveredColor = 0xFFFFFF)
                rot.width = scale.width
                pos.width = scale.width

                xScale = +FloatTextField(
                    1f, 25.pc.w().value, 20, "hollowengine:textures/gui/text_field.png".rl
                ) { float ->
                    lastHovered?.let {
                        pose!!.map.computeIfAbsent(it) { Transformation() }.apply {
                            scaleX = float
                        }
                    }
                }.apply {
                    modifier = 0.1f
                }
                yScale = +FloatTextField(
                    1f, 25.pc.w().value, 20, "hollowengine:textures/gui/text_field.png".rl
                ) { float ->
                    lastHovered?.let {
                        pose!!.map.computeIfAbsent(it) { Transformation() }.apply {
                            scaleY = float
                        }
                    }
                }.apply {
                    modifier = 0.1f
                }
                zScale = +FloatTextField(
                    1f, 25.pc.w().value, 20, "hollowengine:textures/gui/text_field.png".rl
                ) { float ->
                    lastHovered?.let {
                        pose!!.map.computeIfAbsent(it) { Transformation() }.apply {
                            scaleZ = float
                        }
                    }
                }.apply {
                    modifier = 0.1f
                }
                lineBreak()

                +BaseButton(
                    0, 0, 29.pc.w().value, 20,
                    "hollowengine.save".mcTranslate,
                    {
                        lastHovered?.isHovered = false
                        SavePoseScreen(RawPose(pose!!.map.map { it.key.index to it.value }.toMap())).open()
                        capability.rawPose = null
                    },
                    "hollowengine:textures/gui/long_button.png".rl
                )
            }
        }
    }

    fun GltfTree.Node.depthTraversal(action: (GltfTree.Node, depth: Int) -> Unit, depth: Int = 0) {
        action(this, depth)
        children.forEach { it.depthTraversal(action, depth + 1) }
    }

    override fun render(pPoseStack: PoseStack, pMouseX: Int, pMouseY: Int, pPartialTick: Float) {
        renderBackground(pPoseStack)
        super.render(pPoseStack, pMouseX, pMouseY, pPartialTick)
    }

    override fun isPauseScreen() = false

    override fun onClose() {
        super.onClose()

        capability.rawPose = null
        Minecraft.getInstance().keyboardHandler.setSendRepeatsToGui(false)
        lastHovered?.isHovered = false
    }
}

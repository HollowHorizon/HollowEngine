package ru.hollowhorizon.hollowengine.client.models.internal.v2

import de.fabmax.kool.util.Time
import net.minecraft.client.Minecraft
import ru.hollowhorizon.hollowengine.client.models.internal.Model
import ru.hollowhorizon.hollowengine.client.models.internal.animations.AnimationInstance
import ru.hollowhorizon.hollowengine.client.models.internal.animations.headRotation
import ru.hollowhorizon.hollowengine.client.models.internal.rendering.RenderPipeline
import ru.hollowhorizon.hollowengine.fabric.internal.IrisHelper

class ClientModel(val model: Model, parent: RuntimeNode? = null) : RuntimeNode("Root", parent = parent), RenderNode {
    override val children: MutableList<RuntimeNode> =
        model.scenes.flatMap { scene -> scene.nodes.map { ClientNode(it, this) } }
            .toMutableList()

    val animations = model.animations.associate { it.name to AnimationInstance(it) }

    internal val nodes =
        children.filterIsInstance<ClientNode>().flatMap { it.walk() }.associateBy { it.definition.index }


    override fun collectCommands(pipeline: RenderPipeline) {
        pipeline.addInitializable {
            animations.values.filter(AnimationInstance::enabled).forEach {
                nodes.values.forEach { node -> node.transform.set(node.definition.baseTransform) }
                it.update(this, if(IrisHelper.isShadowRendering()) 0f else Time.deltaT)
            }

            if(parent == null) {
                val player = Minecraft.getInstance().player ?: return@addInitializable

                val head = child("Unnamed_86").child("Model").child("Body").child("BodyUp")
                    .child("Head")
                head.transform.rotate(player.headRotation)
                val eyes = head.child("GolovaAnimated").child("Face").child("Eyes")

                val eyeTranslation = ((player.yHeadRot - player.yBodyRot) % 360f).coerceIn(-30f, 30f) / 30f * 0.05f

                eyes.child("LeftEye").child("LeftLib").transform.translate(eyeTranslation.coerceAtMost(0f), 0f, 0f)
                eyes.child("RightEye").child("RightLib").transform.translate(eyeTranslation.coerceAtLeast(0f), 0f, 0f)
            }

            updateGlobalMatrix()
        }
        children.filterIsInstance<RenderNode>().forEach {
            it.collectCommands(pipeline)
        }
    }
}
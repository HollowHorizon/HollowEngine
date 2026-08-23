package ru.hollowhorizon.hollowengine.client.models.internal.v2

import ru.hollowhorizon.hollowengine.client.handlers.TickHandler
import ru.hollowhorizon.hollowengine.client.models.internal.animator.AnimatorEvaluationContext
import ru.hollowhorizon.hollowengine.client.models.internal.animator.ModelAnimator
import ru.hollowhorizon.hollowengine.client.models.internal.manager.HollowModelManager
import ru.hollowhorizon.hollowengine.common.attachments.components.AnimationsComponent
import ru.hollowhorizon.hollowengine.common.attachments.components.MaterialsComponent
import ru.hollowhorizon.hollowengine.common.attachments.api.AttachmentRegistry
import ru.hollowhorizon.hollowengine.common.attachments.tracking.MCEntity
import java.util.UUID

/**
 * One model node of one entity.
 */
class ModelInstance(val attachment: ModelAttachment) {
    val animator = ModelAnimator()
    private var posedFrame = Long.MIN_VALUE
    private var materials: MaterialsComponent? = null

    init {
        // A reloaded model may bring new materials
        attachment.onModelChange(::dress)
    }

    /**
     * Sets what this instance plays and what it wears.
     */
    fun configure(animations: AnimationsComponent?, materials: MaterialsComponent?) {
        animator.configure(HollowModelManager.animatorOf(attachment.location), animations)
        if (this.materials == materials) return

        this.materials = materials
        dress()
    }

    /**
     * Advances the animation and leaves the nodes ready to draw, once per frame.
     */
    fun update(context: AnimatorEvaluationContext, frame: Long = TickHandler.renderFrame) {
        if (posedFrame == frame) return
        posedFrame = frame

        attachment.beginPose()
        animator.applyTo(attachment, context)
        attachment.endPose()
    }

    private fun dress() = attachment.applyMaterials(materials?.materials ?: emptyMap())
}

/** Identifies one model node of an entity; two nodes of the same entity are two instances. */
private data class ModelInstanceKey(val nodeId: UUID, val model: String)

/**
 * The instance of [model] on [nodeId], created on first use.
 */
fun MCEntity.modelInstance(nodeId: UUID, model: String): ModelInstance =
    AttachmentRegistry.attachments(this).runtime.getOrPut(ModelInstanceKey(nodeId, model)) {
        ModelInstance(ModelAttachment(model))
    }

/** The instance already drawn for this node, or null when nothing has drawn it yet. */
fun MCEntity.modelInstanceOrNull(nodeId: UUID, model: String): ModelInstance? =
    AttachmentRegistry.attachmentsOrNull(this)?.runtime?.getOrNull(ModelInstanceKey(nodeId, model))

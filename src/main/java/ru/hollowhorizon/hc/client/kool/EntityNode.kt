package ru.hollowhorizon.hc.client.kool

import de.fabmax.kool.input.PointerInput
import de.fabmax.kool.math.Vec2f
import de.fabmax.kool.modules.ui2.*
import net.minecraft.world.entity.LivingEntity
import org.lwjgl.opengl.GL33
import ru.hollowhorizon.hc.client.render.render
import kotlin.contracts.ExperimentalContracts
import kotlin.contracts.InvocationKind
import kotlin.contracts.contract
import kotlin.math.atan

interface EntityScope : ImageScope {
    override val modifier: EntityModifier

    fun EntityModifier.mouseRotation(headRotationModifier: Float = 2f): EntityModifier {
        surface.onUpdate {
            val pointer = PointerInput.primaryPointer.pos
            val xOffset = uiNode.leftPx+ uiNode.widthPx / 2f
            val yOffset = uiNode.topPx + uiNode.heightPx / 2f
            val rotationX = atan((xOffset - pointer.x) / 150.0f / 3) * 20f
            val rotationY = atan((yOffset - pointer.y) / 150.0f / 3) * 20f
            yaw = rotationX
            pitch = -rotationY
            this.headRotationModifier = headRotationModifier
        }
        val pointer = PointerInput.primaryPointer.pos
        val xOffset = uiNode.leftPx + uiNode.paddingStartPx + uiNode.innerWidthPx / 2f
        val yOffset = uiNode.topPx + uiNode.paddingTopPx + uiNode.innerHeightPx / 2f
        val rotationX = atan((xOffset - pointer.x) / 150.0f / 3) * 20f
        val rotationY = atan((yOffset - pointer.y) / 150.0f / 3) * 20f
        yaw = rotationX
        pitch = -rotationY
        this.headRotationModifier = headRotationModifier
        return this
    }
}


open class EntityModifier(surface: UiSurface) : GlCanvasModifier(surface) {
    var isCustomNameVisible by property(false)
    var isRenderShadow by property(false)
    var scale by property(1f)
    var offset by property(Vec2f(0f, 0f))
    var yaw by property(0f)
    var pitch by property(0f)
    var headRotationModifier by property(1f)
}

fun EntityModifier.scale(scale: Float): EntityModifier = apply { this.scale = scale }
fun EntityModifier.offset(offset: Vec2f): EntityModifier = apply { this.offset = offset }
fun EntityModifier.yaw(yaw: Float): EntityModifier = apply { this.yaw = yaw }
fun EntityModifier.pitch(pitch: Float): EntityModifier = apply { this.pitch = pitch }
fun EntityModifier.headRotationModifier(headRotationModifier: Float): EntityModifier = apply { this.headRotationModifier = headRotationModifier }
fun EntityModifier.isCustomNameVisible(visible: Boolean): EntityModifier = apply { this.isCustomNameVisible = visible }
fun EntityModifier.isRenderShadow(visible: Boolean): EntityModifier = apply { this.isRenderShadow = visible }

@OptIn(ExperimentalContracts::class)
inline fun UiScope.Entity(
    entity: LivingEntity, scopeName: String? = null, block: EntityScope.() -> Unit,
) {
    contract {
        callsInPlace(block, InvocationKind.EXACTLY_ONCE)
    }

    val image = uiNode.createChild(scopeName, EntityNode::class, EntityNode.factory)
    image.modifier.drawer = {
        GL33.glDepthFunc(GL33.GL_LEQUAL)
        entity.render(x, y, width, height, image.modifier)
    }
    image.block()
}

class EntityNode(parent: UiNode?, surface: UiSurface) : GlCanvasNode(parent, surface), EntityScope {
    override val modifier: EntityModifier = EntityModifier(surface)

    companion object {
        val factory: (UiNode, UiSurface) -> EntityNode = { parent, surface -> EntityNode(parent, surface) }
    }
}
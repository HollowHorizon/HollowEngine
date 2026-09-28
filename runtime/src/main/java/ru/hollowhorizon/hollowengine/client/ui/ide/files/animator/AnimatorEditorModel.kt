package ru.hollowhorizon.hollowengine.client.ui.ide.files.animator

import ru.hollowhorizon.hollowengine.client.ui.UiColor
import ru.hollowhorizon.hollowengine.client.utils.lang
import ru.hollowhorizon.hollowengine.client.ui.widgets.ExpressionEditing
import ru.hollowhorizon.hollowengine.client.models.internal.animator.AnimationExpressionLanguage
import ru.hollowhorizon.hollowengine.common.models.AnimatorLayerType
import ru.hollowhorizon.hollowengine.common.models.AnimatorLayerTypes
import ru.hollowhorizon.hollowengine.common.models.AnimatorLayerSpec
import ru.hollowhorizon.hollowengine.common.models.AnimationControllerStateSpec
import ru.hollowhorizon.hollowengine.common.models.AnimatorStateType
import ru.hollowhorizon.hollowengine.common.models.AnimatorStateTypes
import ru.hollowhorizon.hollowengine.common.models.UnknownAnimatorLayerSpec
import ru.hollowhorizon.hollowengine.common.models.UnknownAnimatorStateSpec

/** What the inspector is looking at. */
sealed interface AnimatorSelection {
    data object None : AnimatorSelection

    data class Layer(val layerId: String) : AnimatorSelection

    data class State(val layerId: String, val stateId: String) : AnimatorSelection

    /** Transitions have no id of their own, so they are addressed by position in their layer. */
    data class Transition(val layerId: String, val index: Int) : AnimatorSelection
}

object AnimatorColors {
    val Panel = UiColor(0.13f, 0.14f, 0.17f)
    val Canvas = UiColor(0.10f, 0.11f, 0.13f)
    val Grid = UiColor(1f, 1f, 1f, 0.04f)
    val Border = UiColor(1f, 1f, 1f, 0.10f)
    val Hover = UiColor(1f, 1f, 1f, 0.08f)
    val Text = UiColor(0.86f, 0.88f, 0.92f)
    val Muted = UiColor(0.55f, 0.58f, 0.64f)
    val Node = UiColor(0.18f, 0.20f, 0.24f)
    val NodeTop = UiColor(0.21f, 0.23f, 0.28f)
    val NodeTopHover = UiColor(0.26f, 0.29f, 0.35f)
    val NodeBottom = UiColor(0.15f, 0.16f, 0.20f)
    val NodeShadow = UiColor(0f, 0f, 0f, 0.45f)
    val Chip = UiColor(0.12f, 0.13f, 0.16f)
    val ChipText = UiColor(0.70f, 0.75f, 0.85f)
    val AnyState = UiColor(0.62f, 0.52f, 0.92f)
    val NodeSelected = UiColor(0.92f, 0.58f, 0.20f)
    val NodeEntry = UiColor(0.36f, 0.74f, 0.42f)
    val Edge = UiColor(0.60f, 0.64f, 0.72f)
    val EdgeSelected = UiColor(0.92f, 0.58f, 0.20f)
    val EdgeHover = UiColor(0.82f, 0.86f, 0.94f)
    val Accent = UiColor(0.38f, 0.60f, 0.92f)
    val Danger = UiColor(0.85f, 0.34f, 0.34f)
}

internal fun animatorText(name: String): String = "hollowengine.gui.animator_editor.$name".lang

fun AnimatorLayerSpec.kindName(): String =
    AnimatorLayerTypes.of(this)?.title() ?: (this as? UnknownAnimatorLayerSpec)?.typeId ?: "layer"

fun AnimatorLayerType<*>.title(): String = titleOf(titleKey, id)

fun AnimationControllerStateSpec.kindName(): String =
    AnimatorStateTypes.of(this)?.title() ?: (this as? UnknownAnimatorStateSpec)?.typeId ?: "state"

fun AnimatorStateType<*>.title(): String = titleOf(titleKey, id)

private fun titleOf(titleKey: String, id: String): String {
    val translated = titleKey.lang
    return if (translated == titleKey) id.substringAfterLast('/') else translated
}

/** Highlighting, completion and diagnostics for the expressions of the animator dialect. */
val AnimationExpressionEditing = ExpressionEditing(AnimationExpressionLanguage)

internal fun UiColor.mixedWith(other: UiColor, amount: Float = 0.22f): UiColor =
    interpolate(other.copy(alpha = alpha), amount)

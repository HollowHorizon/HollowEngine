package ru.hollowhorizon.hollowengine.client.editor

internal data class TransformGizmoTarget(
    val type: TransformGizmoTargetType,
    val title: String,
    val icon: String,
)

internal enum class TransformGizmoTargetType {
    MODEL,
    TRANSFORM;

}

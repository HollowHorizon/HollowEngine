package ru.hollowhorizon.hc.client.models.gltf

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

/**
 * Тип камеры: перспективная или ортографическая
 */
@Serializable
enum class CameraType {
    @SerialName("perspective")
    PERSPECTIVE,

    @SerialName("orthographic")
    ORTHOGRAPHIC
}

/**
 * Параметры перспективной камеры
 */
@Serializable
data class GltfCameraPerspective(
    /** Вертикальный угол обзора в радианах */
    @SerialName("yfov")
    val yfov: Float,

    /** Ближняя плоскость отсечения */
    @SerialName("znear")
    val znear: Float,

    /** Дальняя плоскость отсечения (только для PERSPECTIVE) */
    @SerialName("zfar")
    val zfar: Float? = null,

    /** Опциональное соотношение сторон; если не указано, вычисляется из размеров канваса */
    @SerialName("aspectRatio")
    val aspectRatio: Float? = null
)

/**
 * Параметры ортографической камеры
 */
@Serializable
data class GltfCameraOrthographic(
    /** Половина ширины объёма просмотра */
    @SerialName("xmag")
    val xmag: Float,

    /** Половина высоты объёма просмотра */
    @SerialName("ymag")
    val ymag: Float,

    /** Ближняя плоскость отсечения */
    @SerialName("znear")
    val znear: Float,

    /** Дальняя плоскость отсечения */
    @SerialName("zfar")
    val zfar: Float
)

/**
 * Описание одной камеры в glTF
 */
@Serializable
data class GltfCamera(
    /** Человеко-читаемое имя (не уникально) */
    val name: String? = null,

    /** Тип камеры */
    val type: CameraType,

    /** Блок параметров для перспективной камеры */
    val perspective: GltfCameraPerspective? = null,

    /** Блок параметров для ортографической камеры */
    val orthographic: GltfCameraOrthographic? = null,
)
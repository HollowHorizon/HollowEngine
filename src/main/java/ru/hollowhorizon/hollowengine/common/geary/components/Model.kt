@file:OptIn(ExperimentalSerializationApi::class)

package ru.hollowhorizon.hollowengine.common.geary.components

import kotlinx.serialization.ExperimentalSerializationApi
import kotlinx.serialization.SerialInfo
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

@SerialInfo
@Target(AnnotationTarget.PROPERTY)
annotation class EditorName(val name: String)

@SerialInfo
@Target(AnnotationTarget.PROPERTY)
annotation class EditorRange(val min: Float, val max: Float)

@SerialInfo
@Target(AnnotationTarget.CLASS, AnnotationTarget.PROPERTY)
annotation class EditorIcon(val icon: String)

@SerialInfo
@Target(AnnotationTarget.PROPERTY)
annotation class EditorHidden

@Serializable
@SerialName("hollowengine:model")
@EditorIcon("hollowengine:textures/gui/icons/eye.svg")
data class Model(
    @EditorName("Модель")
    val model: String,
    @EditorRange(min = 0f, max = 100f)
    val scale: Float,
)

@Serializable
@SerialName("hollowengine:transform")
@EditorIcon("hollowengine:textures/gui/icons/world.svg")
data class TransformComponent(
    @EditorName("Позиция X")
    @EditorRange(-1000f, 1000f)
    val x: Float = 0f,

    @EditorName("Позиция Y")
    @EditorRange(-100f, 300f)
    val y: Float = 0f,

    @EditorName("Позиция Z")
    @EditorRange(-1000f, 1000f)
    val z: Float = 0f,

    @EditorName("Поворот (Yaw)")
    @EditorRange(0f, 360f)
    val yaw: Float = 0f,

    @EditorName("Наклон (Pitch)")
    @EditorRange(-90f, 90f)
    val pitch: Float = 0f,

    @EditorName("Масштаб")
    @EditorRange(0.1f, 10f)
    @EditorIcon("hollowengine:textures/gui/icons/maximize.svg")
    val scale: Float = 1f
)

@Serializable
@SerialName("hollowengine:interaction")
@EditorIcon("hollowengine:textures/gui/icons/interaction.svg")
data class InteractionComponent(
    @EditorHidden // Это поле не должно быть в редакторе
    val interactionId: String = "uuid_default",

    @EditorName("Активно")
    val isInteractable: Boolean = true,

    @EditorName("Радиус действия")
    @EditorRange(1f, 64f)
    val radius: Float = 3.0f,

    @EditorName("Текст подсказки")
    @EditorIcon("hollowengine:textures/gui/icons/dialogue.png")
    val hintText: String = "Нажмите Е чтобы говорить",

    @EditorName("Скрипт события")
    @EditorIcon("hollowengine:textures/gui/icons/file_kts.svg")
    val scriptPath: String = "scripts/npc/dialogue_start.kts"
)

@Serializable
@SerialName("hollowengine:advanced_model")
@EditorIcon("hollowengine:textures/gui/icons/folder_npcs.svg")
data class AdvancedModelComponent(
    @EditorName("Путь к модели")
    val modelPath: String = "models/entity/custom_npc.gltf",

    @EditorName("Текстура скина")
    @EditorIcon("hollowengine:textures/gui/icons/file_image.svg")
    val texturePath: String = "textures/entity/skin.png",

    @EditorName("Прозрачность")
    @EditorRange(0f, 1f)
    val alpha: Float = 1.0f,

    @EditorName("Светящийся")
    @EditorIcon("hollowengine:textures/gui/icons/eye.svg")
    val glow: Boolean = false,

    @EditorName("Анимация покоя")
    @EditorIcon("hollowengine:textures/gui/icons/pose_editor.png")
    val idleAnimation: String = "idle_loop"
)
package ru.hollowhorizon.hollowengine.client.gui.timeline

import de.fabmax.kool.modules.ui2.UiScope
import de.fabmax.kool.modules.ui2.mutableStateOf
import de.fabmax.kool.util.Color

/**
 * Описывает поведение конкретного типа данных на таймлайне
 * @param T Тип значения, которое анимируется.
 */
interface PropertyDriver<T> {
    /**
     * Вычисляет промежуточное значение между start и end.
     * @param fraction Прогресс от 0.0 до 1.0 (уже с учетом Easing).
     */
    fun interpolate(start: T, end: T, fraction: Float): T

    /**
     * Применяет значение к объекту сцены.
     */
    fun apply(value: T)

    /**
     * Рисует поля ввода в панели свойств.
     * @param value Текущее значение ключа.
     * @param onChange Коллбек, который нужно вызвать при изменении значения.
     */
    fun UiScope.drawEditor(value: T, onChange: (T) -> Unit)
}

abstract class BaseAnimTrack(name: String, val color: Color = Color.WHITE) {
    val nameState = mutableStateOf(name)
    val isLocked = mutableStateOf(false)
    val isVisible = mutableStateOf(true)

    abstract fun update(time: Float)
    abstract fun getKeysAsList(): MutableList<out Keyframe<*>>
}
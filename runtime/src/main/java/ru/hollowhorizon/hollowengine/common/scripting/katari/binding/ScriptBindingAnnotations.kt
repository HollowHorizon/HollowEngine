package ru.hollowhorizon.hollowengine.common.scripting.katari.binding

import com.sunnychung.lib.multiplatform.kotlite.katari.ValueRestoreContext
import com.sunnychung.lib.multiplatform.kotlite.katari.ValueSnapshot
import kotlin.reflect.KClass

@MustBeDocumented
@Retention(AnnotationRetention.SOURCE)
@Target(AnnotationTarget.CLASS, AnnotationTarget.FUNCTION, AnnotationTarget.PROPERTY)
annotation class ScriptBinding(val value: String = "")

@MustBeDocumented
@Retention(AnnotationRetention.SOURCE)
@Target(AnnotationTarget.CONSTRUCTOR, AnnotationTarget.FUNCTION, AnnotationTarget.PROPERTY)
annotation class ScriptIgnore

@MustBeDocumented
@Retention(AnnotationRetention.SOURCE)
@Target(AnnotationTarget.CLASS)
annotation class ScriptType(val typeId: String, vararg val superTypes: KClass<*>)

interface ScriptSnapshot<T : Any> {
    suspend fun restore(context: ValueRestoreContext): T
}

interface ScriptSnapshotFactory<T : Any, S>
        where S : ValueSnapshot,
              S : ScriptSnapshot<T> {
    fun capture(value: T): S
}

package ru.hollowhorizon.hollowengine.common.runtime

object RuntimeAnnotationEnvironment {
    @Volatile
    var annotationIndex: RuntimeAnnotationIndex = EmptyRuntimeAnnotationIndex
}

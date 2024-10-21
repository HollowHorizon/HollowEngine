package ru.hollowhorizon.hollowengine.common.scripting.core.remapper

import net.fabricmc.tinyremapper.TinyRemapper
import net.fabricmc.tinyremapper.api.TrClass
import org.objectweb.asm.ClassVisitor


object KotlinMetadataTinyRemapperExtension : TinyRemapper.ApplyVisitorProvider, TinyRemapper.Extension {
    override fun insertApplyVisitor(
        cls: TrClass,
        next: ClassVisitor?
    ): ClassVisitor {
        return KotlinMetadataRemappingClassVisitor(cls.environment.remapper, next)
    }

    override fun attach(builder: TinyRemapper.Builder) {
        builder.extraPreApplyVisitor(this)
    }
}
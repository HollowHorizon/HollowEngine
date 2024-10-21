package ru.hollowhorizon.hollowengine.common.scripting.core.remapper

import org.objectweb.asm.AnnotationVisitor
import org.objectweb.asm.Opcodes
import org.objectweb.asm.tree.AnnotationNode
import org.slf4j.LoggerFactory
import kotlin.metadata.jvm.KotlinClassMetadata
import kotlin.metadata.jvm.Metadata
import org.objectweb.asm.commons.Remapper

class KotlinClassMetadataRemappingAnnotationVisitor(
    private val remapper: Remapper,
    val next: AnnotationVisitor,
    val className: String?,
) : AnnotationNode(Opcodes.ASM9, KotlinMetadataRemappingClassVisitor.ANNOTATION_DESCRIPTOR) {
    private val logger = LoggerFactory.getLogger(javaClass)

    override fun visit(
        name: String?,
        value: Any?,
    ) {
        super.visit(name, value)
    }

    override fun visitEnd() {
        super.visitEnd()

        val header = readHeader() ?: return

        when (val metadata = KotlinClassMetadata.readLenient(header)) {
            is KotlinClassMetadata.Class -> {
                var klass = metadata.kmClass
                klass = KotlinClassRemapper(remapper).remap(klass)
                val remapped = KotlinClassMetadata.Class(klass, metadata.version, metadata.flags).write()
                writeClassHeader(remapped)
            }
            is KotlinClassMetadata.SyntheticClass -> {
                var klambda = metadata.kmLambda

                if (klambda != null) {
                    klambda = KotlinClassRemapper(remapper).remap(klambda)
                    val remapped = KotlinClassMetadata.SyntheticClass(klambda, metadata.version, metadata.flags).write()
                    writeClassHeader(remapped)
                    //validateKotlinClassHeader(remapped, header)
                } else {
                    accept(next)
                }
            }
            is KotlinClassMetadata.FileFacade -> {
                var kpackage = metadata.kmPackage
                kpackage = KotlinClassRemapper(remapper).remap(kpackage)
                val remapped = KotlinClassMetadata.FileFacade(kpackage, metadata.version, metadata.flags).write()
                writeClassHeader(remapped)
                //validateKotlinClassHeader(remapped, header)
            }
            is KotlinClassMetadata.MultiFileClassPart -> {
                var kpackage = metadata.kmPackage
                kpackage = KotlinClassRemapper(remapper).remap(kpackage)
                val remapped =
                    KotlinClassMetadata.MultiFileClassPart(
                        kpackage,
                        metadata.facadeClassName,
                        metadata.version,
                        metadata.flags,
                    ).write()
                writeClassHeader(remapped)
                //validateKotlinClassHeader(remapped, header)
            }
            is KotlinClassMetadata.MultiFileClassFacade, is KotlinClassMetadata.Unknown -> {
                // do nothing
                accept(next)
            }
        }
    }

    @Suppress("UNCHECKED_CAST")
    private fun readHeader(): Metadata? {
        var kind: Int? = null
        var metadataVersion: IntArray? = null
        var data1: Array<String>? = null
        var data2: Array<String>? = null
        var extraString: String? = null
        var packageName: String? = null
        var extraInt: Int? = null

        if (values == null) {
            return null
        }

        values.chunked(2).forEach { (name, value) ->
            when (name) {
                "k" -> kind = value as Int
                "mv" -> metadataVersion = (value as List<Int>).toIntArray()
                "d1" -> data1 = (value as List<String>).toTypedArray()
                "d2" -> data2 = (value as List<String>).toTypedArray()
                "xs" -> extraString = value as String
                "pn" -> packageName = value as String
                "xi" -> extraInt = value as Int
            }
        }

        return Metadata(kind, metadataVersion, data1, data2, extraString, packageName, extraInt)
    }

    private fun writeClassHeader(header: Metadata) {
        val newNode = AnnotationNode(api, desc)
        newNode.values = this.values.toMutableList()

        newNode.run {
            for (i in values.indices step 2) {
                when (values[i]) {
                    "k" -> values[i + 1] = header.kind
                    "mv" -> values[i + 1] = header.metadataVersion.toList()
                    "d1" -> values[i + 1] = header.data1.toList()
                    "d2" -> values[i + 1] = header.data2.toList()
                    "xs" -> values[i + 1] = header.extraString
                    "pn" -> values[i + 1] = header.packageName
                    "xi" -> values[i + 1] = header.extraInt
                }
            }
        }

        newNode.accept(next)
    }

    private fun validateKotlinClassHeader(
        remapped: Metadata,
        original: Metadata,
    ) {
        // This can happen when the remapper is ran on a kotlin version
        // that does not match the version the class was compiled with.
        if (remapped.data2.size != original.data2.size) {
            logger.info(
                "Kotlin class metadata size mismatch: data2 size does not match original in class $className. " +
                        "New: ${remapped.data2.size} Old: ${original.data2.size}",
            )
        }
    }
}
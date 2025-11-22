@file:JvmName("Remapping")

package ru.hollowhorizon.hollowengine.common.compiler.mappings


import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.sync.Semaphore
import kotlinx.coroutines.sync.withPermit
import org.objectweb.asm.*
import org.objectweb.asm.commons.AnnotationRemapper
import org.objectweb.asm.commons.ClassRemapper
import org.objectweb.asm.commons.MethodRemapper
import org.objectweb.asm.commons.Remapper
import ru.hollowhorizon.hollowengine.common.compiler.mappings.metadata.KotlinMetadataRemappingClassVisitor
import java.io.File
import java.util.jar.JarEntry
import java.util.jar.JarFile
import java.util.jar.JarOutputStream

class LambdaAwareRemapper(parent: ClassVisitor, remapper: Remapper) : ClassRemapper(Opcodes.ASM9, parent, remapper) {
    override fun createMethodRemapper(parent: MethodVisitor): MethodRemapper =
        LambdaAwareMethodRemapper(parent, remapper)
}

open class LambdaAwareMethodRemapper(
    private val parent: MethodVisitor,
    remapper: Remapper,
) : MethodRemapper(Opcodes.ASM9, parent, remapper) {
    override fun visitInvokeDynamicInsn(name: String, descriptor: String, handle: Handle, vararg args: Any) {
        val remappedName = if (
            handle.owner == "java/lang/invoke/LambdaMetafactory" &&
            (handle.name == "metafactory" || handle.name == "altMetafactory")
        ) {
            remapper.mapMethodName(
                Type.getReturnType(descriptor).internalName,
                name,
                (args.first() as Type).descriptor
            )
        } else name

        parent.visitInvokeDynamicInsn(
            remappedName,
            remapper.mapMethodDesc(descriptor),
            remapper.mapValue(handle) as Handle,
            *args.map { remapper.mapValue(it) }.toTypedArray()
        )
    }
}

class MappingsRemapper(
    val mappings: Mappings,
    val from: String,
    val to: String,
    private val shouldRemapDesc: Boolean = mappings.namespaces.indexOf(from) != 0,
    private val loader: (name: String) -> ByteArray?,
) : Remapper() {
    private val map = mappings.asASMMapping(from, to)
    private val baseMapper by lazy {
        MappingsRemapper(mappings, from, mappings.namespaces.first(), shouldRemapDesc = false, loader)
    }

    override fun map(internalName: String): String = map[internalName] ?: internalName
    override fun mapMethodName(owner: String, name: String, desc: String): String {
        if (name == "<init>" || name == "<clinit>") return name
        return if (desc.startsWith("(")) {
            val actualDesc = if (shouldRemapDesc) baseMapper.mapMethodDesc(desc) else desc
            walk(owner, name) { map["$it.$name$actualDesc"] }
        } else mapFieldName(owner, name, desc)
    }

    override fun mapFieldName(owner: String, name: String, desc: String?): String =
        walk(owner, name) { map["$it.$name"] }

    override fun mapRecordComponentName(owner: String, name: String, desc: String): String =
        mapFieldName(owner, name, desc)

    override fun mapSignature(signature: String?, typeSignature: Boolean): String? =
        if (signature?.isEmpty() == true) null else super.mapSignature(signature, typeSignature)

    private inline fun walk(
        owner: String,
        name: String,
        applicator: (owner: String) -> String?,
    ) = walkInheritance(loader, owner) { applicator(it) != null }?.let(applicator) ?: name

    fun reverse(loader: (name: String) -> ByteArray? = this.loader): MappingsRemapper =
        MappingsRemapper(mappings, to, from, loader = loader)
}

private data class MethodDeclaration(
    val owner: String,
    val name: String,
    val descriptor: String,
    val returnType: Type,
    val parameterTypes: List<Type>,
)

private data class FieldDeclaration(
    val owner: String,
    val name: String,
)

class SimpleAnnotationNode(
    parent: AnnotationVisitor?,
    val descriptor: String?,
) : AnnotationVisitor(Opcodes.ASM9, parent) {
    val values: MutableList<Pair<String?, Any?>> = mutableListOf()
    override fun visit(name: String?, value: Any?) {
        values += name to value
        super.visit(name, value)
    }
}

open class ModJarRemapper(
    parent: ClassVisitor,
    remapper: Remapper,
) : ClassRemapper(Opcodes.ASM9, parent, remapper) {
    private var annotationNode: SimpleAnnotationNode? = null
    lateinit var targetClass: String
    override fun createAnnotationRemapper(
        descriptor: String?,
        parent: AnnotationVisitor?,
    ): AnnotationVisitor {
        if (descriptor != "Lnet/weavemc/api/mixin/Mixin;")
            return super.createAnnotationRemapper(descriptor, parent)

        annotationNode = SimpleAnnotationNode(parent, descriptor)

        return object : AnnotationVisitor(Opcodes.ASM9, annotationNode) {
            override fun visit(name: String?, value: Any?) {
                val newValue = if (name == "targetClass") {
                    targetClass = (value as Type).internalName
                    Type.getObjectType(remapper.map(targetClass))
                } else value

                super.visit(name, newValue)
            }
        }
    }

    override fun createMethodRemapper(parent: MethodVisitor): MethodVisitor {
        if (annotationNode == null)
            return super.createMethodRemapper(parent)

        return ModMethodRemapper(targetClass, parent, remapper)
    }
}

open class ModMethodRemapper(
    private val targetMixinClass: String,
    private val parent: MethodVisitor,
    remapper: Remapper,
) : LambdaAwareMethodRemapper(parent, remapper) {
    override fun visitAnnotation(descriptor: String?, visible: Boolean): AnnotationVisitor {
        val visitor = parent.visitAnnotation(descriptor, visible)
        return if (visitor != null) MixinAnnotationRemapper(targetMixinClass, visitor, descriptor, remapper)
        else super.visitAnnotation(descriptor, visible)
    }
}

class MixinAnnotationRemapper(
    private val targetMixinClass: String,
    private val parent: AnnotationVisitor,
    descriptor: String?,
    remapper: Remapper,
) : AnnotationRemapper(Opcodes.ASM9, descriptor, parent, remapper) {
    override fun visit(name: String, value: Any) {
        val remappedValue = when {
            value is String && shouldRemap() -> {
                val annotationName = descriptor.substringAfterLast('/')
                when (name) {
                    "method" -> {
                        if (!value.isMethod())
                            error("Failed to identify $value as a method in $annotationName annotation")

                        val method = parseMethod(targetMixinClass, value)
                            ?: error("Failed to parse mixin method value $value of annotation $annotationName")

                        val mappedName = remapper.mapMethodName(method.owner, method.name, method.descriptor)
                        val mappedDesc = remapper.mapMethodDesc(method.descriptor)
                        mappedName + mappedDesc
                    }

                    "field" -> {
                        if (value.isMethod())
                            error("Identified $value as a method when field is expected in $annotationName annotation")

                        remapper.mapFieldName(targetMixinClass, value, null)
                    }

                    "target" -> name.parseAndRemapTarget()

                    else -> value
                }
            }

            else -> value
        }
        parent.visit(name, remappedValue)
    }

    private fun String.isMethod(): Boolean {
        val startIndex = this.indexOf('(')
        val endIndex = this.indexOf(')')
        return startIndex != -1 && endIndex != -1 && startIndex < endIndex
    }

    private fun String.parseAndRemapTarget(): String {
        return if (this.isMethod()) {
            val method = parseTargetMethod(this) ?: return this
            val mappedName = remapper.mapMethodName(method.owner, method.name, method.descriptor)
            val mappedDesc = remapper.mapMethodDesc(method.descriptor)
            mappedName + mappedDesc
        } else {
            val field = parseTargetField(this) ?: return this
            remapper.mapFieldName(field.owner, field.name, null)
        }
    }

    private fun shouldRemap(): Boolean =
        descriptor != null && descriptor.startsWith("Lnet/weavemc/api/mixin")
}

val MAPPINGS = (
        MappingsRemapper::class.java.getResourceAsStream("/mappings-1.20.1.tiny")
            ?: error("Mappings not found")
        ).use(MappingsLoader::loadMappings)

@JvmOverloads
fun remapClass(
    input: ByteArray,
    loader: (String) -> ByteArray? = { null },
    classpath: List<File>,
    mappings: Mappings = MAPPINGS,
    from: String = "named",
    to: String = "intermediary",
): ByteArray {
    val cache = hashMapOf<String, ByteArray?>()
    val jarsToUse = classpath.map { JarFile(it) }
    val lookup = jarsToUse.flatMap { j ->
        j.entries().asSequence().filter { it.name.endsWith(".class") }
            .map { it.name.dropLast(6) to { j.getInputStream(it).readBytes() } }
    }.toMap()

    val remapper = MappingsRemapper(
        mappings, from, to,
        loader = { name ->
            if (name in lookup) cache.getOrPut(name) { lookup.getValue(name)() }
            else loader(name)
        }
    )

    val reader = ClassReader(input)
    val writer = ClassWriter(reader, 0)
    val kotlinMetadataRemapper = KotlinMetadataRemappingClassVisitor(remapper, writer)
    reader.accept(LambdaAwareRemapper(kotlinMetadataRemapper, remapper), 0)
    return writer.toByteArray()
}

fun remapJars(
    mappings: Mappings,
    inputs: List<File>,
    outputDir: File,
    postfix: String = "-deobf",
    from: String = "named",
    to: String = "intermediary",
    classpath: List<File> = listOf(),
) = runBlocking {
    if (!outputDir.exists()) outputDir.mkdirs()

    val cache = hashMapOf<String, ByteArray?>()
    val jarsToUse = (classpath + inputs).map { JarFile(it) }
    val lookup = jarsToUse.flatMap { j ->
        j.entries().asSequence().filter { it.name.endsWith(".class") }
            .map { it.name.dropLast(6) to { j.getInputStream(it).readBytes() } }
    }.toMap()

    val remapper = MappingsRemapper(
        mappings, from, to,
        loader = { name -> if (name in lookup) cache.getOrPut(name) { lookup.getValue(name)() } else null }
    )

    val semaphore = Semaphore(8)

    inputs.map { file ->
        async(Dispatchers.IO) {
            semaphore.withPermit {
                val (name, extension) = file.nameWithoutExtension to file.extension
                remapJar(remapper, file, outputDir, name, postfix, extension)
            }
        }
    }.awaitAll()
}

private fun remapJar(
    remapper: MappingsRemapper,
    file: File,
    outputDir: File,
    name: String,
    postfix: String,
    extension: String,
) {
    JarFile(file).use { jar ->
        val output = outputDir.resolve("$name$postfix.$extension")
        JarOutputStream(output.outputStream()).use { out ->
            val entries = mutableSetOf<String>()
            val (classes, resources) = jar.entries().asSequence().partition { it.name.endsWith(".class") }

            fun write(name: String, bytes: ByteArray) {
                if (name in entries) return // Ignore duplicates
                entries += name
                out.putNextEntry(JarEntry(name))
                out.write(bytes)
            }

            resources.filterNot { it.name.endsWith(".RSA") || it.name.endsWith(".SF") }
                .forEach { write(it.name, jar.getInputStream(it).readBytes()) }

            classes.forEach { entry ->
                val reader = ClassReader(jar.getInputStream(entry).readBytes())
                val writer = ClassWriter(reader, 0)
                val kotlinMetadataRemapper = KotlinMetadataRemappingClassVisitor(remapper, writer)
                reader.accept(LambdaAwareRemapper(kotlinMetadataRemapper, remapper), 0)

                write("${remapper.map(reader.className)}.class", writer.toByteArray())
            }
        }
    }
}

fun remapJar(
    mappings: Mappings,
    input: File,
    output: File,
    postfix: String,
    from: String = "named",
    to: String = "intermediary",
    classpath: List<File> = listOf(),
) {
    val cache = hashMapOf<String, ByteArray?>()
    val jarsToUse = (classpath + input).map { JarFile(it) }
    val lookup = jarsToUse.flatMap { j ->
        j.entries().asSequence().filter { it.name.endsWith(".class") }
            .map { it.name.dropLast(6) to { j.getInputStream(it).readBytes() } }
    }.toMap()

    val remapper = MappingsRemapper(
        mappings, from, to,
        loader = { name -> if (name in lookup) cache.getOrPut(name) { lookup.getValue(name)() } else null }
    )

    val (name, extension) = input.nameWithoutExtension to input.extension
    remapJar(remapper, input, output, name, postfix, extension)

    jarsToUse.forEach { it.close() }
}

fun ByteArray.remap(remapper: Remapper): ByteArray {
    val reader = ClassReader(this)

    val writer = ClassWriter(reader, 0)
    val kotlinMetadataRemapper = KotlinMetadataRemappingClassVisitor(remapper, writer)
    reader.accept(LambdaAwareRemapper(kotlinMetadataRemapper, remapper), 0)

    return writer.toByteArray()
}

private fun parseMethod(owner: String, methodDeclaration: String): MethodDeclaration? {
    try {
        val methodName = methodDeclaration.substringBefore('(')
        val methodDesc = methodDeclaration.drop(methodName.length)
        val methodType = Type.getMethodType(methodDesc)
        return MethodDeclaration(
            owner,
            methodName,
            methodDesc,
            methodType.returnType,
            methodType.argumentTypes.toList()
        )
    } catch (ex: Exception) {
        println("Failed to parse method declaration: $methodDeclaration")
        ex.printStackTrace()
    }
    return null
}

private fun parseTargetField(fieldDeclaration: String): FieldDeclaration? {
    try {
        val (classPath, fieldName) = fieldDeclaration.splitAround('.')
        return FieldDeclaration(classPath, fieldName)
    } catch (ex: Exception) {
        println("Failed to parse field declaration: $fieldDeclaration")
        ex.printStackTrace()
    }
    return null
}

private fun parseTargetMethod(methodDeclaration: String): MethodDeclaration? {
    try {
        val (classPath, fullMethod) = methodDeclaration.splitAround('.')
        val methodName = fullMethod.substringBefore('(')
        val methodDesc = fullMethod.drop(methodName.length)
        val methodType = Type.getMethodType(methodDesc)
        return MethodDeclaration(
            classPath,
            methodName,
            methodDesc,
            methodType.returnType,
            methodType.argumentTypes.toList()
        )
    } catch (ex: Exception) {
        println("Failed to parse method declaration: $methodDeclaration")
        ex.printStackTrace()
    }
    return null
}
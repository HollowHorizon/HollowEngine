package ru.hollowhorizon.hollowengine.common.scripting.story.nodes

import net.minecraft.nbt.CompoundTag
import net.minecraft.nbt.Tag
import org.objectweb.asm.ClassReader
import org.objectweb.asm.tree.ClassNode
import ru.hollowhorizon.hc.client.utils.nbt.INBTSerializable
import java.io.File

fun interface Node : INBTSerializable {
    // Возвращает, выполнен ли код в ноде
    fun execute(): Boolean

    override fun serialize() = CompoundTag()

    override fun deserialize(tag: Tag) {}

    open fun reset() {}
}

// По какой-то странной причине компилятор не может сам привести первый тип ко второму, так что есть разве что такой варинат по приведению типов
// Как через Kotlin IR генерировать сразу функциональные интерфейсы я без понятия
fun (() -> Boolean).toNode(): Node = Node { this() }

fun main() {
    val node = ClassNode()
    val reader = ClassReader(File("C:\\Users\\Artem\\Downloads\\TestKt.class").inputStream())
    reader.accept(node, 0)

    println(node)
}
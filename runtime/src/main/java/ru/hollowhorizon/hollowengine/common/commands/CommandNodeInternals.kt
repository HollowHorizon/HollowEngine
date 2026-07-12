package ru.hollowhorizon.hollowengine.common.commands

import com.mojang.brigadier.tree.ArgumentCommandNode
import com.mojang.brigadier.tree.CommandNode
import com.mojang.brigadier.tree.LiteralCommandNode
import ru.hollowhorizon.hollowengine.common.utils.UnsafeTools
import java.lang.invoke.VarHandle

internal object CommandNodeInternals {
    private val childrenHandle = CommandNode::class.java.mapHandle("children")
    private val literalsHandle = CommandNode::class.java.mapHandle("literals")
    private val argumentsHandle = CommandNode::class.java.mapHandle("arguments")

    private fun Class<*>.mapHandle(name: String): VarHandle =
        UnsafeTools.lookup.findVarHandle(this, name, Map::class.java)

    @Suppress("UNCHECKED_CAST")
    private fun <S> children(node: CommandNode<S>): MutableMap<String, CommandNode<S>> =
        childrenHandle.get(node) as MutableMap<String, CommandNode<S>>

    @Suppress("UNCHECKED_CAST")
    private fun <S> literals(
        node: CommandNode<S>,
    ): MutableMap<String, LiteralCommandNode<S>> =
        literalsHandle.get(node) as MutableMap<String, LiteralCommandNode<S>>

    @Suppress("UNCHECKED_CAST")
    private fun <S> arguments(
        node: CommandNode<S>,
    ): MutableMap<String, ArgumentCommandNode<S, *>> =
        argumentsHandle.get(node) as MutableMap<String, ArgumentCommandNode<S, *>>

    fun <S> removeChild(parent: CommandNode<S>, name: String, expected: CommandNode<S>): Boolean =
        synchronized(parent) {
            val children = children(parent)
            if (children[name] !== expected) return@synchronized false

            children.remove(name)
            when (expected) {
                is LiteralCommandNode<S> -> literals(parent).remove(name)
                is ArgumentCommandNode<S, *> -> arguments(parent).remove(name)
            }
            true
        }
}

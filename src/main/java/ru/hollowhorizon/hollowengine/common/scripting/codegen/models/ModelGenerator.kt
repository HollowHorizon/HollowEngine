package ru.hollowhorizon.hollowengine.common.scripting.codegen.models

import net.minecraft.resources.ResourceLocation
import ru.hollowhorizon.hollowengine.client.models.internal.AnimatedModel
import ru.hollowhorizon.hollowengine.client.models.internal.Node
import ru.hollowhorizon.hollowengine.client.models.internal.manager.HollowModelManager
import ru.hollowhorizon.hollowengine.client.models.internal.manager.RegisterModelLoaderEvent
import ru.hollowhorizon.hollowengine.client.models.internal.manager.registerModelLoaders
import ru.hollowhorizon.hollowengine.common.events.EventBus
import ru.hollowhorizon.hollowengine.common.events.EventListener
import ru.hollowhorizon.hollowengine.common.utils.rl

object ModelGenerator {
    val SOURCES = HashMap<ResourceLocation, String>()
    fun generateSource(location: ResourceLocation): String {
        return SOURCES.computeIfAbsent(location) {
            val model = HollowModelManager.getOrCreate(location)
            val name = location.path.substringAfterLast('/').substringBefore('.')

            val nodes = model.model.scenes.flatMap { it.nodes }

            """
                import ru.hollowhorizon.hollowengine.client.models.internal.animations.*
                
                val ${name.uppercase()} = ${name.replaceFirstChar { it.uppercase() }}()
                
                class ${name.replaceFirstChar { it.uppercase() }}: ModelInstance("$location") {
                    val animations = Animations()
                    
                    inner class Animations {
                        ${generateAnimations(model, 24)}
                    }
                    
                    ${generateNodes(nodes)}
                }
            """.trimIndent()
        }
    }

    private fun generateNodes(nodes: List<Node>): String {
        return nodes.joinToString("\n") { generateNode(it) }
    }

    private fun generateNode(node: Node, depth: Int = 20): String {
        val nodeName = generateNodeName(node).replace(Regex("[^a-zA-Z0-9]"), "_")
            .replaceFirstChar { it.uppercase() }

        return """
            
            val $nodeName = ${nodeName}Node()
            
            inner class ${nodeName}Node: NodeInstance(model.model.node(${node.index})) {
                ${generateNodes(node.children)}
            }
        """.replaceIndent(" ".repeat(depth))
    }

    private fun generateNodeName(node: Node): String {
        val name = node.name
        if (name == null) {
            return if (node.parent == null) "Root"
            else "Node${node.index}"
        } else {
            val parent = node.parent ?: return name

            if(parent.children.count { it.name == name } <= 1) return name

            return name + (parent.children.indexOf(node) + 1)
        }
    }

    private fun generateAnimations(model: AnimatedModel, depth: Int): String {
        return model.animations.map {
            it.key
        }.joinToString("\n" + " ".repeat(depth)) {
            val variable = it.replace(Regex("[^a-zA-Z0-9]"), "_").uppercase()
            "val $variable = AnimationInstance(model.animations[\"$it\"]!!)"
        }
    }
}
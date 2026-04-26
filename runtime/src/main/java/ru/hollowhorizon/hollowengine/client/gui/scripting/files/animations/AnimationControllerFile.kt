package ru.hollowhorizon.hollowengine.client.gui.scripting.files.animations

import de.fabmax.kool.modules.ui2.Grow
import de.fabmax.kool.modules.ui2.Row
import de.fabmax.kool.modules.ui2.UiScope
import de.fabmax.kool.modules.ui2.margin
import ru.hollowhorizon.hollowengine.HollowEngine
import ru.hollowhorizon.hollowengine.client.gui.animations.AnimationControllerGraph
import ru.hollowhorizon.hollowengine.client.gui.animations.GraphEditor
import ru.hollowhorizon.hollowengine.client.gui.animations.NodeType
import ru.hollowhorizon.hollowengine.client.gui.colors.Dimensions
import ru.hollowhorizon.hollowengine.client.gui.scripting.files.EditorFile
import ru.hollowhorizon.hollowengine.client.models.internal.controller.WrapMode
import ru.hollowhorizon.hollowengine.client.models.internal.manager.HollowModelManager
import ru.hollowhorizon.hollowengine.common.files.DirectoryManager.fromReadablePath
import ru.hollowhorizon.hollowengine.common.utils.isValidRL
import ru.hollowhorizon.hollowengine.common.utils.json.JsonFormat
import ru.hollowhorizon.hollowengine.common.utils.rl
import ru.hollowhorizon.hollowengine.common.utils.serialization.serialize

class AnimationControllerFile(path: String, bytes: ByteArray) : EditorFile(path) {

    private val editor = GraphEditor()
    
    private var lastSavedGraph: AnimationControllerGraph? = null

    init {
        editor.onModelPathChanged = { path ->
            loadModelAnimations(path)
        }
        
        if (bytes.isNotEmpty()) {
            try {
                val jsonString = bytes.toString(Charsets.UTF_8)
                val graph = JsonFormat.decodeFromString<AnimationControllerGraph>(jsonString)
                editor.loadGraph(graph)
                lastSavedGraph = graph
                loadModelAnimations(editor.modelPath.value)
            } catch (e: Exception) {
                HollowEngine.LOGGER.error("Controller file $filePath cannot be loaded!", e)
            }
        } else {
            // fresh file: try to infer model from name or leave default
            loadModelAnimations(editor.modelPath.value)
        }
    }

    private fun loadModelAnimations(path: String) {
        editor.availableAnimations.clear()
        if (!path.isValidRL()) return
        val rl = path.rl
        if (!HollowModelManager.supports(rl)) return

        try {
            val attachment = ru.hollowhorizon.hollowengine.client.models.internal.v2.ModelAttachment(path)
            editor.availableAnimations.addAll(attachment.animations.map { it.name })
        } catch (e: Exception) {
            HollowEngine.LOGGER.error("Cannot load model animations for controller $filePath", e)
        }
    }

    override fun save() {
        val file = filePath.fromReadablePath()
        if (!file.exists()) {
            file.parentFile.mkdirs()
            file.createNewFile()
        }

        try {
            val graph = editor.toGraph()
            val jsonString = JsonFormat.serialize(graph)
            file.writeText(JsonFormat.encodeToString(jsonString))

            // additionally generate a Kotlin script with compiled transitions (conditions)
            generateControllerClass(graph)
            
            lastSavedGraph = graph
        } catch (e: Exception) {
            HollowEngine.LOGGER.error("Controller file $filePath cannot be saved!", e)
        }
    }

    private fun generateControllerClass(graph: AnimationControllerGraph) {
        val file = filePath.fromReadablePath()
        val className = file.nameWithoutExtension
            .replace('.', '_')
            .replace('-', '_')
            .replace(' ', '_')
            .ifEmpty { "GeneratedController" }

        val ktFile = file.parentFile.resolve("$className.animation-controller.kts")

        // Build node ID to node data mapping
        val nodeMap = graph.nodes.associateBy { it.id }

        // Generate state definitions
        val stateDefinitions = graph.nodes
            .filter { it.type == NodeType.STATE }.joinToString("\n") { node ->
                val stateName = node.title.ifEmpty { node.animationName.ifEmpty { "state_${node.id.take(6)}" } }
                val animName = node.animationName.ifEmpty { "idle" }
                val wrapMode = when (node.wrapMode) {
                    WrapMode.Once -> "WrapMode.Once"
                    WrapMode.Loop -> "WrapMode.Loop"
                    WrapMode.PingPong -> "WrapMode.PingPong"
                    WrapMode.ClampForever -> "WrapMode.ClampForever"
                }

                buildString {
                    append("            state(\n")
                    append("                name = \"$stateName\",\n")
                    append("                animationName = \"$animName\",\n")
                    append("                wrapMode = $wrapMode,\n")
                    append("                speed = ${node.speed}f,\n")
                    append("                weight = ${node.weight}f,\n")
                    append("                priority = ${node.priority},\n")
                    append("                overrideTranslation = ${node.overrideTranslation},\n")
                    append("                overrideRotation = ${node.overrideRotation},\n")
                    append("                overrideScale = ${node.overrideScale}\n")
                    append("            )\n")
                }
            }

        // Generate entry transitions (ENTRY -> STATE connections)
        // These define the initial state and its entry properties
        val entryTransitions = graph.connections
            .filter { conn ->
                val fromNode = nodeMap[conn.fromNodeId]
                val toNode = nodeMap[conn.toNodeId]
                fromNode?.type == NodeType.ENTRY && toNode?.type == NodeType.STATE && !conn.properties.mute
            }.joinToString("\n") { conn ->
                val toNode = nodeMap[conn.toNodeId]!!
                val toStateName = toNode.title.ifEmpty { toNode.animationName.ifEmpty { "state_${toNode.id.take(6)}" } }

                buildString {
                    append("            entry(\"$toStateName\")\n")
                }
            }

        // Generate transitions from ANY state (transitions that can happen from any state)
        // Filter out muted transitions
        val anyStateTransitions = graph.connections
            .filter { conn ->
                val fromNode = nodeMap[conn.fromNodeId]
                val toNode = nodeMap[conn.toNodeId]
                fromNode?.type == NodeType.ANY && toNode?.type == NodeType.STATE && !conn.properties.mute
            }.joinToString("\n") { conn ->
                val toNode = nodeMap[conn.toNodeId]!!
                val toStateName = toNode.title.ifEmpty { toNode.animationName.ifEmpty { "state_${toNode.id.take(6)}" } }
                val conditionBody = conn.properties.condition.ifBlank { "true" }

                buildString {
                    append("            any(\n")
                    append("                toState = \"$toStateName\",\n")
                    append("                duration = ${conn.properties.duration}f,\n")
                    append("                condition = { $conditionBody },\n")
                    append("            )\n")
                }
            }

        // Generate normal state-to-state transitions
        // Filter out muted transitions
        val stateTransitions = graph.connections
            .filter { conn ->
                val fromNode = nodeMap[conn.fromNodeId]
                val toNode = nodeMap[conn.toNodeId]
                fromNode?.type == NodeType.STATE && toNode?.type == NodeType.STATE && !conn.properties.mute
            }.joinToString("\n") { conn ->
                val fromNode = nodeMap[conn.fromNodeId]!!
                val toNode = nodeMap[conn.toNodeId]!!
                val fromStateName = fromNode.title.ifEmpty { fromNode.animationName.ifEmpty { "state_${fromNode.id.take(6)}" } }
                val toStateName = toNode.title.ifEmpty { toNode.animationName.ifEmpty { "state_${toNode.id.take(6)}" } }

                val conditionBody = conn.properties.condition.ifBlank { "true" }

                buildString {
                    append("            transition(\n")
                    append("                fromState = \"$fromStateName\",\n")
                    append("                toState = \"$toStateName\",\n")
                    append("                duration = ${conn.properties.duration}f,\n")
                    append("                condition = { $conditionBody },\n")
                    append("            )\n")
                }
            }
        
        // Combine all transitions
        val allTransitions = buildString {
            if (entryTransitions.isNotEmpty()) {
                append(entryTransitions)
                append("\n")
            }
            if (anyStateTransitions.isNotEmpty()) {
                append(anyStateTransitions)
                append("\n")
            }
            if (stateTransitions.isNotEmpty()) {
                append(stateTransitions)
            }
        }

        // Count transitions by type (including muted for accurate reporting)
        val anyTransitionsCount = graph.connections.count { c -> nodeMap[c.fromNodeId]?.type == NodeType.ANY && nodeMap[c.toNodeId]?.type == NodeType.STATE }
        val stateTransitionsCount = graph.connections.count { c -> nodeMap[c.fromNodeId]?.type == NodeType.STATE && nodeMap[c.toNodeId]?.type == NodeType.STATE }
        val entryTransitionsCount = graph.connections.count { c -> nodeMap[c.fromNodeId]?.type == NodeType.ENTRY && nodeMap[c.toNodeId]?.type == NodeType.STATE }
        val mutedTransitionsCount = graph.connections.count { c -> c.properties.mute }

        // Generate the full class content
        val content = buildString {
            appendLine("// Auto-generated from $filePath, do not edit manually")
            appendLine("// Animation Controller Script for Kotlin Scripting System")
            appendLine()
            appendLine("import net.minecraft.world.entity.LivingEntity")
            appendLine("import ru.hollowhorizon.hollowengine.client.models.internal.controller.AnimationController")
            appendLine("import ru.hollowhorizon.hollowengine.client.models.internal.controller.AnimationSystem")
            appendLine("import ru.hollowhorizon.hollowengine.client.models.internal.controller.WrapMode")
            appendLine()
            appendLine("/**")
            appendLine(" * Generated Animation Controller for model: ${graph.modelPath}")
            appendLine(" * ")
            appendLine(" * States: ${graph.nodes.count { it.type == NodeType.STATE }}")
            appendLine(" * Entry Transitions: $entryTransitionsCount")
            appendLine(" * Any-State Transitions: $anyTransitionsCount")
            appendLine(" * State-to-State Transitions: $stateTransitionsCount")
            if (mutedTransitionsCount > 0) {
                appendLine(" * Muted Transitions: $mutedTransitionsCount (excluded from generated code)")
            }
            appendLine(" */")
            appendLine("configure {")
            appendLine(stateDefinitions)
            appendLine(allTransitions)
            appendLine("}")
        }

        try {
            ktFile.writeText(content)
            HollowEngine.LOGGER.info("Generated animation controller: ${ktFile.absolutePath}")
        } catch (e: Exception) {
            HollowEngine.LOGGER.error("Cannot write generated controller for $filePath", e)
        }
    }

    override fun UiScope.compose() {
        Row(Grow.Std, Grow.Std) {
            modifier.margin(Dimensions.PaddingNormal)
            editor.EditorLayout()
        }
    }
}

package ru.hollowhorizon.hollowengine.client.gui.scripting.files.animations

import de.fabmax.kool.modules.ui2.*
import ru.hollowhorizon.hollowengine.HollowEngine
import ru.hollowhorizon.hollowengine.client.gui.animations.AnimationControllerGraph
import ru.hollowhorizon.hollowengine.client.gui.animations.GraphEditor
import ru.hollowhorizon.hollowengine.client.gui.colors.ColorTheme
import ru.hollowhorizon.hollowengine.client.gui.colors.Dimensions
import ru.hollowhorizon.hollowengine.client.gui.scripting.files.EditorFile
import ru.hollowhorizon.hollowengine.client.models.internal.manager.HollowModelManager
import ru.hollowhorizon.hollowengine.common.files.DirectoryManager.fromReadablePath
import ru.hollowhorizon.hollowengine.common.utils.isValidRL
import ru.hollowhorizon.hollowengine.common.utils.json.JsonFormat
import ru.hollowhorizon.hollowengine.common.utils.rl
import ru.hollowhorizon.hollowengine.common.utils.serialization.serialize

class AnimationControllerFile(path: String, bytes: ByteArray) : EditorFile(path) {

    private val editor = GraphEditor()

    init {
        if (bytes.isNotEmpty()) {
            try {
                val jsonString = bytes.toString(Charsets.UTF_8)
                val graph = JsonFormat.decodeFromString<AnimationControllerGraph>(jsonString)
                editor.loadGraph(graph)
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

            // additionally generate a Kotlin stub with compiled transitions (conditions)
            generateControllerClass(graph)
        } catch (e: Exception) {
            HollowEngine.LOGGER.error("Controller file $filePath cannot be saved!", e)
        }
    }

    private fun generateControllerClass(graph: AnimationControllerGraph) {
        if (!HollowEngine.compilerLoader.isLoaded) return

        val file = filePath.fromReadablePath()
        val className = file.nameWithoutExtension
            .replace('.', '_')
            .replace('-', '_')
            .replace(' ', '_')
            .ifEmpty { "GeneratedController" }

        val ktFile = file.parentFile.resolve("$className.generated.kts")

        val conditions = graph.connections
            .mapIndexed { index, conn ->
                val id = conn.id.replace('-', '_')
                val body = conn.properties.condition.ifBlank { "true" }
                "    val cond_$id: net.minecraft.world.entity.LivingEntity.() -> Boolean = { $body }\n"
            }
            .joinToString("\n")

        val content = buildString {
            appendLine("// Auto-generated from $filePath, do not edit manually")
            appendLine("import net.minecraft.world.entity.LivingEntity")
            appendLine()
            appendLine("object $className {")
            appendLine(conditions)
            appendLine("}")
        }

        try {
            ktFile.writeText(content)
        } catch (e: Exception) {
            HollowEngine.LOGGER.error("Cannot write generated controller for $filePath", e)
        }
    }

    override fun UiScope.compose() {
        Column(Grow.Std, Grow.Std) {
            modifier.margin(Dimensions.PaddingNormal)

            // Simple header with model path info
            Row(Grow.Std) {
                modifier.padding(Dimensions.PaddingSmall)
                    .background(
                        RoundRectBackground(
                            ColorTheme.UI.BackgroundSecondary,
                            Dimensions.PaddingSmall
                        )
                    )

                Text("Model: ${editor.modelPath.use()}") {
                    modifier.textColor(ColorTheme.UI.WhiteReplacement)
                }
            }

            Row(Grow.Std, Grow.Std) {
                modifier.margin(top = Dimensions.PaddingNormal)
                editor.EditorLayout()
            }
        }
    }
}


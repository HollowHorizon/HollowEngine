package ru.hollowhorizon.hollowengine.client.ui.ide.files.shadergraph

import androidx.compose.runtime.Composable
import ru.hollowhorizon.hollowengine.client.shadergraph.*
import ru.hollowhorizon.hollowengine.client.ui.*
import ru.hollowhorizon.hollowengine.client.ui.ide.files.HollowIdeShaderGraphDocument
import ru.hollowhorizon.hollowengine.client.ui.inspector.*
import ru.hollowhorizon.hollowengine.client.utils.lang

private const val GraphIcon = "hollowengine:textures/gui/icons/files/shader.svg"
private const val RemoveIcon = "hollowengine:textures/gui/icons/remove.svg"

/** What the inspector shows for [selection]: the one node selected, or the graph with its properties, preview and problems. */
internal fun shaderGraphInspectorTarget(
    document: HollowIdeShaderGraphDocument,
    selection: ShaderGraphSelection,
    problems: List<ShaderDiagnostic>,
): InspectorTarget {
    val node = selection.single?.let(document.graph::node)
    val kind = node?.let { ShaderNodeTypes.of(it.type) }
    if (node != null && kind != null) {
        return InspectorTarget(
            id = "shadergraph-node-${node.id}", title = kind.title(), subtitle = node.id, icon = GraphIcon
        ) {
            NodeFields(document, node, kind)
        }
    }
    return InspectorTarget(id = "shadergraph", title = graphText("graph"), icon = GraphIcon) {
        GraphFields(document, problems)
    }
}

@Composable
private fun NodeFields(document: HollowIdeShaderGraphDocument, node: ShaderGraphNode, kind: ShaderNodeType) {
    if (kind.options.isNotEmpty()) {
        Section(graphText("options")) {
            kind.options.forEach { option ->
                val current = node.options[option.name] ?: option.default
                fun set(value: String) = document.edit { it.withOption(node.id, option.name, value) }
                val id = "sg-inspector-option-${node.id}-${option.name}"
                when (option.kind) {
                    ShaderOptionKind.CHOICE -> {
                        Label(optionTitle(option))
                        Pills(option.values, current, { optionValueTitle(option, it) }, ::set)
                    }

                    ShaderOptionKind.TOGGLE -> ToggleRow(optionTitle(option), current == "true") { set(it.toString()) }
                    ShaderOptionKind.TEXT -> TextRow(optionTitle(option), current, id = id, onChange = ::set)
                    ShaderOptionKind.EXPRESSION -> TextRow(
                        optionTitle(option),
                        current,
                        id = id,
                        completions = ShaderExpressionEditing.completions,
                        highlighter = ShaderExpressionEditing.highlighter,
                        diagnostics = ShaderExpressionEditing.diagnostics(current),
                        onChange = ::set,
                    )

                    ShaderOptionKind.PROPERTY -> {
                        Label(optionTitle(option))
                        val names = document.graph.properties.map { it.name }
                        if (names.isEmpty()) Hint(graphText("no_properties")) else Pills(names, current, { it }, ::set)
                    }
                }
            }
        }
    }
    val unlinked = kind.inputs(node).filter { pin ->
        document.graph.linkInto(
            node.id, pin.name
        ) == null && pin.fallback == null && pin.type.fixed != ShaderType.TEXTURE
    }
    if (unlinked.isNotEmpty()) {
        Section(graphText("inputs")) {
            unlinked.forEach { pin -> ValueRow(document, node, pin) }
        }
    }
    ToggleRow(graphText("show_preview"), kind.showsPreview(node)) { shown ->
        document.edit {
            it.withPreview(
                node.id, shown
            )
        }
    }
}

/** One number per component of what an unlinked input holds. */
@Composable
private fun ValueRow(document: HollowIdeShaderGraphDocument, node: ShaderGraphNode, pin: ShaderPinSpec) {
    val values = node.values[pin.name]?.takeIf { it.isNotEmpty() } ?: pin.default.ifEmpty { listOf(0f) }
    val width = pin.type.fixed?.width ?: values.size.coerceIn(1, 4)
    Label(pin.name)
    Row(tags = listOf("insp-input-row")) {
        repeat(width) { component ->
            NumberInput(
                path = "sg-value-${node.id}-${pin.name}-$component",
                value = values.getOrElse(component) { values.last() }.toDouble(),
                whole = false,
                modifier = Modifier.size(0.px).grow(1f),
            ) { typed ->
                val next = (0 until width).map { values.getOrElse(it) { values.last() } }.toMutableList()
                next[component] = typed.toFloat()
                document.edit { it.withValue(node.id, pin.name, next) }
            }
        }
    }
}

@Composable
private fun GraphFields(document: HollowIdeShaderGraphDocument, problems: List<ShaderDiagnostic>) {
    val preview = document.graph.preview
    Section(graphText("preview")) {
        Label(graphText("preview_mesh"))
        Pills(ShaderPreviewMesh.entries, preview.mesh, { graphText("mesh.${it.name.lowercase()}") }) { mesh ->
            document.edit { it.withPreviewSettings(preview.copy(mesh = mesh)) }
        }
        ToggleRow(graphText("preview_rotate"), preview.rotate) { rotate ->
            document.edit { it.withPreviewSettings(preview.copy(rotate = rotate)) }
        }
        TextRow(graphText("preview_texture"), preview.texture, id = "sg-preview-texture") { texture ->
            document.edit { it.withPreviewSettings(preview.copy(texture = texture)) }
        }
    }
    Section(graphText("properties")) {
        document.graph.properties.forEachIndexed { index, property -> PropertyFields(document, index, property) }
        InspectorButton(graphText("add_property")) {
            document.edit { it.withProperty(ShaderGraphProperty(it.freePropertyName(), ShaderType.FLOAT, listOf(0f))) }
        }
    }
    Section(graphText("problems")) {
        if (problems.isEmpty()) Hint(graphText("no_problems"))
        problems.forEach { problem -> Hint(listOfNotNull(problem.node, problemText(problem)).joinToString(": ")) }
    }
}

/**
 * The fields of one property. They are named after its place in the list rather than after the
 * property, so renaming it keeps the field being typed in.
 */
@Composable
private fun PropertyFields(document: HollowIdeShaderGraphDocument, index: Int, property: ShaderGraphProperty) {
    fun change(next: ShaderGraphProperty) = document.edit { it.withPropertyChanged(property.name, next) }
    Row(tags = listOf("insp-input-row")) {
        Row(modifier = Modifier.size(0.px).grow(1f)) {
            NameRow("", property.name, id = "sg-property-$index") { name -> change(property.copy(name = name)) }
        }
        InspectorIconButton(RemoveIcon, graphText("remove_property"), tags = listOf("insp-inline-icon", "danger")) {
            document.edit { it.withoutProperty(property.name) }
        }
    }
    Pills(ShaderType.entries, property.type, { it.glsl }) { type -> change(property.copy(type = type)) }
    if (property.type == ShaderType.TEXTURE) {
        TextRow(graphText("texture"), property.texture, id = "sg-property-texture-$index") { texture ->
            change(property.copy(texture = texture))
        }
    } else {
        Row(tags = listOf("insp-input-row")) {
            repeat(property.type.width) { component ->
                val values = property.default.ifEmpty { listOf(0f) }
                NumberInput(
                    path = "sg-property-$index-$component",
                    value = values.getOrElse(component) { values.last() }.toDouble(),
                    whole = false,
                    modifier = Modifier.size(0.px).grow(1f),
                ) { typed ->
                    val next =
                        (0 until property.type.width).map { values.getOrElse(it) { values.last() } }.toMutableList()
                    next[component] = typed.toFloat()
                    change(property.copy(default = next))
                }
            }
        }
    }
}

/** What a problem of the compiler says, in words. */
internal fun problemText(diagnostic: ShaderDiagnostic): String {
    val text = "hollowengine.gui.shadergraph.problem.${diagnostic.problem.name.lowercase()}".lang
    return when {
        diagnostic.reason != null -> "$text: ${expressionProblemText(diagnostic.reason, diagnostic.detail)}"
        diagnostic.problem == ShaderProblem.GLSL_ERROR -> "$text\n${diagnostic.detail}"
        diagnostic.detail.isBlank() -> text
        else -> "$text (${diagnostic.detail})"
    }
}

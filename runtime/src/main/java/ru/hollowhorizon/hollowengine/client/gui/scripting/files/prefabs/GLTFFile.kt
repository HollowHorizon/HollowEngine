package ru.hollowhorizon.hollowengine.client.gui.scripting.files.prefabs

import de.fabmax.kool.modules.ui2.*
import de.fabmax.kool.toString
import ru.hollowhorizon.hollowengine.client.gui.colors.ColorTheme
import ru.hollowhorizon.hollowengine.client.gui.colors.Dimensions
import ru.hollowhorizon.hollowengine.client.models.internal.Primitive
import ru.hollowhorizon.hollowengine.client.models.internal.v2.RuntimeNode
import ru.hollowhorizon.hollowengine.client.models.internal.v2.walk
import kotlin.math.roundToInt

class GLTFFile(path: String) : ModelEditorFile(path) {
    init {
        if (path.startsWith("assets")) {
            modelController.model.set(path.substringAfter('/').replaceFirst('/', ':'))
        }
    }

    override fun save() {
        // GLTF files are read-only in this editor
    }

    override val hasSidebar = true

    override fun UiScope.composeSidebar() {
        val currentModel = modelController.model.use()
        val attachment = modelController.attachment

        val roots = attachment.nodes
        val allNodes = roots.flatMap { it.walk() }
        val meshes = allNodes.mapNotNull { it.definition.mesh }
        val primitives = meshes.flatMap { it.primitives }
        val skinnedNodes = allNodes.count { it.definition.skin != null }
        val totalJoints = allNodes.sumOf { it.definition.skin?.jointsIds?.size ?: 0 }
        val totalMorphTargets = primitives.sumOf { it.morphTargets.size }

        val sectionStates = remember { mutableMapOf<String, MutableStateValue<Boolean>>() }
        val treeStates = remember { mutableMapOf<Int, MutableStateValue<Boolean>>() }
        val shapekeyStates = remember { mutableMapOf<String, MutableStateValue<Float>>() }
        val loadedModel = remember { mutableStateOf(currentModel) }
        if (loadedModel.value != currentModel) {
            loadedModel.set(currentModel)
            treeStates.clear()
            shapekeyStates.clear()
        }

        ScrollArea(
            Grow.Std,
            Grow.Std,
            containerModifier = {
                it.backgroundColor(ColorTheme.UI.BackgroundSecondary)
            },
            withHorizontalScrollbar = false,
            vScrollbarModifier = {
                it.colors(
                    trackColor = ColorTheme.UI.BackgroundSecondary.withAlpha(0f),
                    trackHoverColor = ColorTheme.UI.BackgroundElements,
                    color = ColorTheme.UI.BackgroundAccent,
                    hoverColor = ColorTheme.UI.WhiteReplacement
                ).width(Dimensions.PaddingMedium)
            }
        ) {
            modifier.layout(ColumnLayout).width(Grow.Std)
                .padding(Dimensions.PaddingMedium)

            InspectorSection("Model Summary", "summary", sectionStates) {
                MetaRow("Resource", currentModel)
                MetaRow("Scene", "${attachment.model.scene}")
                MetaRow("Root Nodes", "${roots.size}")
                MetaRow("All Nodes", "${allNodes.size}")
                MetaRow("Meshes", "${meshes.size}")
                MetaRow("Primitives", "${primitives.size}")
                MetaRow("Materials", "${attachment.materials.size}")
                MetaRow("Animations", "${attachment.animations.size}")
                MetaRow("Skinned Nodes", "$skinnedNodes")
                MetaRow("Skin Joints", "$totalJoints")
                MetaRow("Shapekeys", "$totalMorphTargets")
                MetaRow("Triangles", "${attachment.triangles}")
            }

            InspectorSection("Animations", "animations", sectionStates) {
                if (attachment.animations.isEmpty()) {
                    EmptyHint("No animations in this model")
                } else {
                    attachment.animations.forEach { animation ->
                        val nodeCount = attachment.model.animations
                            .firstOrNull { it.name == animation.name }
                            ?.nodes
                            ?.size ?: 0
                        MetaRow(animation.name, "${animation.duration.toString(2)}s | nodes: $nodeCount")
                    }
                }
            }

            InspectorSection("Bone Hierarchy", "bones", sectionStates) {
                if (roots.isEmpty()) {
                    EmptyHint("No runtime nodes found")
                } else {
                    roots.forEach { root ->
                        BoneNode(
                            node = root,
                            depth = 0,
                            expandedStates = treeStates
                        )
                    }
                }
            }

            InspectorSection("Shapekey Tester", "shapekeys", sectionStates) {
                if (totalMorphTargets == 0) {
                    EmptyHint("No shapekeys in this model")
                } else {
                    var shapeCounter = 0
                    allNodes.forEach { node ->
                        val nodePrimitives = node.definition.mesh?.primitives ?: emptyList()
                        nodePrimitives.forEachIndexed { primitiveIndex, primitive ->
                            if (primitive.morphTargets.isEmpty()) return@forEachIndexed

                            Row(Grow.Std) {
                                modifier.margin(top = Dimensions.PaddingSmall)
                                Text("${node.name} [P$primitiveIndex]") {
                                    modifier.textColor(ColorTheme.UI.WhiteReplacement)
                                }
                            }

                            primitive.morphTargets.indices.forEach { targetIndex ->
                                shapeCounter++
                                ShapeKeySlider(
                                    node = node,
                                    primitive = primitive,
                                    primitiveIndex = primitiveIndex,
                                    targetIndex = targetIndex,
                                    states = shapekeyStates
                                )
                            }
                        }
                    }

                    if (shapeCounter > 0) {
                        Row(Grow.Std) {
                            modifier.margin(top = Dimensions.PaddingMedium)
                            Button("Reset All Weights") {
                                modifier.width(Grow.Std)
                                    .onClick {
                                        allNodes.forEach { node ->
                                            node.definition.mesh?.primitives?.forEach { primitive ->
                                                primitive.weights.fill(0f)
                                            }
                                        }
                                        shapekeyStates.values.forEach { it.set(0f) }
                                    }
                            }
                        }
                    }
                }
            }
        }
    }

    private fun UiScope.InspectorSection(
        title: String,
        id: String,
        states: MutableMap<String, MutableStateValue<Boolean>>,
        body: ColumnScope.() -> Unit,
    ) {
        val expanded = states.getOrPut(id) { mutableStateOf(true) }
        val isExpanded = expanded.use()

        Column(Grow.Std) {
            modifier.margin(bottom = Dimensions.PaddingMedium)
                .padding(Dimensions.PaddingMedium)
                .background(RoundRectBackground(ColorTheme.UI.BackgroundElements, Dimensions.PaddingMedium))
                .border(
                    RoundRectBorder(
                        ColorTheme.UI.BackgroundAccent,
                        Dimensions.PaddingMedium,
                        Dimensions.PaddingSmall * 0.5f
                    )
                )

            Row(Grow.Std) {
                modifier.onClick { expanded.set(!expanded.value) }
                Text(title) {
                    modifier.width(Grow.Std)
                        .textColor(ColorTheme.UI.WhiteReplacement)
                        .alignY(AlignmentY.Center)
                }
                Arrow(if (isExpanded) ArrowScope.ROTATION_DOWN else ArrowScope.ROTATION_RIGHT) {
                    modifier.size(Dimensions.PaddingHuge, Dimensions.PaddingHuge)
                        .colors(ColorTheme.UI.BackgroundAccent, ColorTheme.UI.WhiteReplacement)
                        .alignY(AlignmentY.Center)
                }
            }

            if (isExpanded) {
                Column(Grow.Std) {
                    modifier.margin(top = Dimensions.PaddingMedium)
                    body()
                }
            }
        }
    }

    private fun UiScope.MetaRow(label: String, value: String) {
        Row(Grow.Std) {
            modifier.margin(bottom = Dimensions.PaddingSmall)
            Text(label) {
                modifier.width(Grow.Std)
                    .textColor(ColorTheme.UI.WhiteReplacement.withAlpha(0.9f))
            }
            Text(value) {
                modifier.textColor(ColorTheme.UI.BackgroundAccent)
                    .textAlignX(AlignmentX.End)
            }
        }
    }

    private fun UiScope.EmptyHint(text: String) {
        Text(text) {
            modifier.textColor(ColorTheme.UI.WhiteReplacement.withAlpha(0.7f))
        }
    }

    private fun UiScope.BoneNode(
        node: RuntimeNode,
        depth: Int,
        expandedStates: MutableMap<Int, MutableStateValue<Boolean>>,
    ) {
        val nodeState = expandedStates.getOrPut(node.definition.index) { mutableStateOf(depth < 2) }
        val expanded = nodeState.use()
        val hasChildren = node.children.isNotEmpty()

        Row(Grow.Std) {
            modifier.padding(vertical = Dimensions.PaddingSmall)
                .onClick { if (hasChildren) nodeState.set(!nodeState.value) }

            if (depth > 0) {
                Box {
                    modifier.width((Dimensions.PaddingMedium + Dimensions.PaddingSmall) * depth.toFloat())
                }
            }

            if (hasChildren) {
                Arrow(if (expanded) ArrowScope.ROTATION_DOWN else ArrowScope.ROTATION_RIGHT) {
                    modifier.size(Dimensions.PaddingHuge, Dimensions.PaddingHuge)
                        .margin(end = Dimensions.PaddingSmall)
                        .colors(ColorTheme.UI.BackgroundAccent, ColorTheme.UI.WhiteReplacement)
                        .alignY(AlignmentY.Center)
                }
            } else {
                Box {
                    modifier.width(Dimensions.PaddingHuge + Dimensions.PaddingSmall)
                }
            }

            Text(node.name) {
                modifier.width(Grow.Std)
                    .textColor(ColorTheme.UI.WhiteReplacement)
                    .alignY(AlignmentY.Center)
            }

            val flags = buildString {
                append("#${node.definition.index}")
                if (node.definition.mesh != null) append(" M")
                if (node.definition.skin != null) append(" S")
                if (node.children.isNotEmpty()) append(" C${node.children.size}")
            }
            Text(flags) {
                modifier.textColor(ColorTheme.UI.BackgroundAccent)
                    .alignY(AlignmentY.Center)
            }
        }

        if (expanded) {
            node.children.forEach {
                BoneNode(it, depth + 1, expandedStates)
            }
        }
    }

    private fun UiScope.ShapeKeySlider(
        node: RuntimeNode,
        primitive: Primitive,
        primitiveIndex: Int,
        targetIndex: Int,
        states: MutableMap<String, MutableStateValue<Float>>,
    ) {
        val key = "${node.definition.index}:$primitiveIndex:$targetIndex"
        val initial = primitive.weights.getOrNull(targetIndex)?.coerceIn(0f, 1f) ?: 0f
        val sliderState = states.getOrPut(key) { mutableStateOf(initial) }

        if (primitive.weights.getOrNull(targetIndex) != sliderState.value) {
            primitive.weights[targetIndex] = sliderState.value
        }

        Column(Grow.Std) {
            modifier.margin(bottom = Dimensions.PaddingSmall)
            Text("Target $targetIndex: ${(sliderState.use() * 100f).roundToInt()}%") {
                modifier.textColor(ColorTheme.UI.WhiteReplacement.withAlpha(0.9f))
            }
            Slider(sliderState.use(), 0f, 1f) {
                modifier.width(Grow.Std)
                    .onChange {
                        val value = it.coerceIn(0f, 1f)
                        sliderState.set(value)
                        primitive.weights[targetIndex] = value
                    }
                    .colors(
                        ColorTheme.UI.WhiteReplacement,
                        ColorTheme.UI.BackgroundAccent,
                        ColorTheme.UI.BackgroundAccent.withAlpha(0.5f)
                    )
            }
        }
    }
}

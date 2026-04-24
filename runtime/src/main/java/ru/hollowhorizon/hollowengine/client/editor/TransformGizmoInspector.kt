package ru.hollowhorizon.hollowengine.client.editor

import ru.hollowhorizon.hollowengine.common.geary.api.Component as GearyComponent
import de.fabmax.kool.math.Vec2f
import de.fabmax.kool.modules.ui2.*
import kotlinx.serialization.KSerializer
import net.minecraft.resources.ResourceLocation
import ru.hollowhorizon.hollowengine.client.gui.colors.ColorTheme
import ru.hollowhorizon.hollowengine.client.gui.colors.Dimensions
import ru.hollowhorizon.hollowengine.client.gui.scripting.popup.ItemPopupMenu
import ru.hollowhorizon.hollowengine.client.gui.scripting.popup.SubMenuItem
import ru.hollowhorizon.hollowengine.client.kool.KoolManager
import ru.hollowhorizon.hollowengine.client.kool.minecraft.Image
import ru.hollowhorizon.hollowengine.common.geary.anchor.AnchoredSnapshotUpdatePacket
import ru.hollowhorizon.hollowengine.common.geary.anchor.stableKeyOrNull
import ru.hollowhorizon.hollowengine.common.geary.components.ComponentDescriptorRegistry
import ru.hollowhorizon.hollowengine.common.geary.components.ComponentHolder
import ru.hollowhorizon.hollowengine.common.geary.components.ComponentSchemaRegistry
import ru.hollowhorizon.hollowengine.common.geary.components.EditorIcon
import ru.hollowhorizon.hollowengine.common.geary.components.GenericEditor
import ru.hollowhorizon.hollowengine.common.geary.components.SmallActionButton
import ru.hollowhorizon.hollowengine.common.geary.snapshot.EntitySerialization
import ru.hollowhorizon.hollowengine.common.geary.snapshot.EntitySnapshot
import ru.hollowhorizon.hollowengine.common.network.send
import ru.hollowhorizon.hollowengine.common.utils.rl
import kotlin.reflect.full.findAnnotation

private const val INSPECTOR_WIDTH = 420f
private const val INSPECTOR_MARGIN = 16f

internal data class TransformGizmoTarget(
    val type: TransformGizmoTargetType,
    val title: String,
    val icon: ResourceLocation,
)

internal enum class TransformGizmoTargetType {
    MODEL,
    POINT_LIGHT,
    SPOT_LIGHT,
    TRANSFORM;

    val isLight: Boolean
        get() = this == POINT_LIGHT || this == SPOT_LIGHT
}

internal object TransformGizmoInspectorPackets {
    fun sendSnapshot(snapshot: EntitySnapshot) {
        AnchoredSnapshotUpdatePacket(snapshot).send()
    }
}

internal class TransformGizmoInspectorState(
    private val onSnapshotUpdated: (EntitySnapshot) -> Unit,
) {
    private val visibleState = mutableStateOf(false)
    private val selectedKeyState = mutableStateOf<java.util.UUID?>(null)
    private val headerState = mutableStateOf<InspectorHeader?>(null)
    private val snapshotState = mutableStateOf<EntitySnapshot?>(null)
    private val components = mutableStateListOf<EditableComponentState>()
    private val hiddenComponents = mutableListOf<GearyComponent>()

    val isVisible: Boolean get() = visibleState.value

    fun isVisibleFor(stableKey: java.util.UUID): Boolean =
        visibleState.value && selectedKeyState.value == stableKey

    fun open(snapshot: EntitySnapshot, target: TransformGizmoTarget) {
        visibleState.set(true)
        rebuild(snapshot, target)
    }

    fun refresh(snapshot: EntitySnapshot, target: TransformGizmoTarget) {
        if (!visibleState.value) return
        if (selectedKeyState.value != snapshot.stableKeyOrNull()) return
        if (snapshotState.value == snapshot && headerState.value?.title == target.title && headerState.value?.icon == target.icon) return
        rebuild(snapshot, target)
    }

    fun close() {
        visibleState.set(false)
        selectedKeyState.set(null)
        headerState.set(null)
        snapshotState.set(null)
        components.clear()
        hiddenComponents.clear()
    }

    private fun rebuild(snapshot: EntitySnapshot, target: TransformGizmoTarget) {
        val stableKey = snapshot.stableKeyOrNull() ?: return close()
        selectedKeyState.set(stableKey)
        headerState.set(InspectorHeader(stableKey, target.title, target.icon))
        snapshotState.set(snapshot)
        components.clear()
        hiddenComponents.clear()

        snapshot.components.forEach { component ->
            val descriptor = EntitySerialization.descriptorFor(component) ?: run {
                hiddenComponents += component
                return@forEach
            }
            if (!descriptor.editable) {
                hiddenComponents += component
                return@forEach
            }

            val editable: GearyComponent = component
            val state = mutableStateOf(editable)
            state.onChange { _, _ -> commitSnapshot() }
            components += EditableComponentState(descriptor.id, descriptor, state)
        }
    }

    private fun commitSnapshot() {
        val base = snapshotState.value ?: return
        val updatedSnapshot = base.copy(components = components.map { it.state.value } + hiddenComponents)
        snapshotState.set(updatedSnapshot)
        onSnapshotUpdated(updatedSnapshot)
    }

    private fun addComponent(key: ResourceLocation) {
        val holder = ComponentDescriptorRegistry.getOrNull(key) ?: return
        if (!holder.editable) return
        val component: GearyComponent = holder.create()
        val state = mutableStateOf(component)
        state.onChange { _, _ -> commitSnapshot() }
        components += EditableComponentState(key, holder, state)
        commitSnapshot()
    }

    private fun buildComponentMenu(menu: ItemPopupMenu<Unit>): SubMenuItem<Unit> = SubMenuItem("Components") {
        val existing = components.map { it.key }.toSet()
        val available = ComponentDescriptorRegistry
            .map { it.value }
            .filter { it.editable && it.id !in existing }
            .sortedBy { it.id.toString() }

        if (available.isEmpty()) {
            item("All editable components are already added") {}
        } else {
            available.groupBy { it.id.namespace }.toSortedMap().forEach { (namespace, descriptors) ->
                subMenu(namespace) {
                    descriptors.forEach { descriptor ->
                        val schema = ComponentSchemaRegistry.descriptorSchema(descriptor.id)
                        val icon = (schema?.icon ?: descriptor.value.findAnnotation<EditorIcon>()?.icon)?.rl
                        val label = buildString {
                            append(schema?.displayName ?: descriptor.serializer.descriptor.serialName)
                            append(" [")
                            append(descriptor.id)
                            append(']')
                        }
                        item(label, icon) {
                            addComponent(descriptor.id)
                            menu.hide()
                        }
                    }
                }
            }
        }
    }

    fun UiScope.RenderPanel() {
        val header = headerState.use() ?: return
        if (!visibleState.use()) return

        val addComponentPopup = remember { ItemPopupMenu<Unit>("transform-inspector-add-component") }
        addComponentPopup()

        Column(width = Dp.fromPx(INSPECTOR_WIDTH), height = Grow.Std) {
            modifier
                .margin(start = Dp.fromPx(KoolManager.context.window.size.x - INSPECTOR_WIDTH - INSPECTOR_MARGIN), top = Dp.fromPx(INSPECTOR_MARGIN))
                .padding(Dimensions.PaddingMedium)
                .background(RoundRectBackground(ColorTheme.UI.BackgroundSecondary.withAlpha(0.98f), sizes.smallGap))
                .border(RoundRectBorder(ColorTheme.UI.BackgroundElements, sizes.smallGap, Dimensions.PaddingSmall))

            Row(Grow.Std) {
                Image(header.icon) {
                    modifier.size(Dimensions.PaddingHuge, Dimensions.PaddingHuge)
                        .margin(end = Dimensions.PaddingMedium)
                        .alignY(AlignmentY.Center)
                }
                Column(Grow.Std) {
                    Text(header.title) {
                        modifier.textColor(ColorTheme.UI.WhiteReplacement)
                    }
                    Text(header.stableKey.toString()) {
                        modifier.margin(top = Dimensions.PaddingSmall)
                            .textColor(ColorTheme.UI.BackgroundAccent)
                    }
                }
                SmallActionButton("Close") { close() }
            }

            Box(Grow.Std, Dimensions.PaddingSmall) {}

            ScrollArea(Grow.Std, Grow.Std, containerModifier = { it.background(null) }, withHorizontalScrollbar = false) {
                modifier.layout(ColumnLayout)
                components.forEach { component ->
                    ComponentEditor(component)
                }
            }

            Box(Grow.Std, Dimensions.PaddingSmall) {}

            Row(Grow.Std) {
                SmallActionButton("Add Component", highlighted = true) {
                    addComponentPopup.show(
                        Vec2f(KoolManager.context.window.size.x - INSPECTOR_WIDTH, 120f),
                        buildComponentMenu(addComponentPopup),
                        Unit,
                    )
                }
            }
        }
    }

    private fun UiScope.ComponentEditor(component: EditableComponentState) {
        @Suppress("UNCHECKED_CAST")
        val state = component.state

        @Suppress("UNCHECKED_CAST")
        val serializer = component.holder.serializer as KSerializer<Any>
        GenericEditor(state, serializer) {
            components.remove(component)
            commitSnapshot()
        }
    }

    private data class EditableComponentState(
        val key: ResourceLocation,
        val holder: ComponentHolder<*>,
        val state: MutableStateValue<GearyComponent>,
    )

    private data class InspectorHeader(
        val stableKey: java.util.UUID,
        val title: String,
        val icon: ResourceLocation,
    )
}

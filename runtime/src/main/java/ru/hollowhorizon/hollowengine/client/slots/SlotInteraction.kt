package ru.hollowhorizon.hollowengine.client.slots

import androidx.compose.runtime.mutableStateOf
import net.minecraft.world.item.ItemStack
import ru.hollowhorizon.hollowengine.client.ui.layout.UiRect
import ru.hollowhorizon.hollowengine.common.slots.SlotButton
import ru.hollowhorizon.hollowengine.common.slots.SlotDistributeMode
import ru.hollowhorizon.hollowengine.common.slots.SlotIntent

/**
 * Turns pointer and key input into slot intents.
 *
 * The gestures that span more than one slot need to know where every slot ended up on screen. Rather than
 * teaching the UI framework about slots, each [Slot] reports its own laid-out bounds here through
 * `Modifier.onPlaced`, and the hit test against them happens in this class.
 *
 * Pressing while holding a stack does not act right away: the same press begins either a plain click or a
 * drag across several slots, and which one it was is only known on release. Vanilla behaves the same way,
 * and acting immediately would place the whole stack into the first slot and leave nothing to distribute.
 */
class SlotInteraction internal constructor(private val session: ClientSlotSession) {
    private val bounds = HashMap<Int, UiRect>()
    private val hoveredState = mutableStateOf(-1)

    private var pendingButton: Int? = null
    private var pendingSlot = -1
    private val dragged = LinkedHashSet<Int>()

    /** Flat index the pointer is over, or -1. Drives the tooltip and the key gestures. */
    val hovered: Int get() = hoveredState.value

    val hoveredStack: ItemStack
        get() = hoveredState.value.takeIf { it >= 0 }?.let { session[it] } ?: ItemStack.EMPTY

    val carriedStack: ItemStack get() = session.carried

    internal fun place(slot: Int, rect: UiRect) {
        bounds[slot] = rect
    }

    internal fun forget(slot: Int) {
        bounds.remove(slot)
        if (hoveredState.value == slot) hoveredState.value = -1
    }

    internal fun enter(slot: Int) {
        hoveredState.value = slot
    }

    internal fun exit(slot: Int) {
        if (hoveredState.value == slot) hoveredState.value = -1
    }

    internal fun press(slot: Int, button: Int, shift: Boolean) {
        cancelPending()
        if (button != LEFT_BUTTON && button != RIGHT_BUTTON) return

        if (shift) {
            session.submit(SlotIntent.quickMove(slot))
            return
        }

        // With an empty cursor a press can only ever be a pick-up, so there is nothing to defer.
        if (session.carried.isEmpty) {
            session.submit(SlotIntent.click(slot, button.asSlotButton()))
            return
        }

        pendingButton = button
        pendingSlot = slot
        dragged += slot
        session.beginPreview()
    }

    /** Collects the slots a drag crosses and previews the split locally as it grows. */
    internal fun drag(x: Float, y: Float) {
        val button = pendingButton ?: return
        val slot = slotAt(x, y)
        if (slot < 0 || !dragged.add(slot)) return
        if (dragged.size < 2) return
        session.preview(distributeIntent(button, dragged.toList()))
    }

    /** Ends the gesture: several slots make it a distribution, a single one the click that was deferred. */
    internal fun release() {
        val button = pendingButton ?: return
        val slot = pendingSlot
        // Read the targets out before clearing the gesture: they are what gets submitted.
        val targets = dragged.toList()
        cancelPending()

        if (targets.size >= 2) session.submit(distributeIntent(button, targets))
        else if (slot >= 0) session.submit(SlotIntent.click(slot, button.asSlotButton()))
    }

    /** Q on the hovered slot, or on the cursor stack when the pointer is over nothing. */
    internal fun dropHovered(all: Boolean) {
        if (!session.carried.isEmpty) return session.submit(SlotIntent.dropCarried(all))
        val slot = hoveredState.value
        if (slot >= 0) session.submit(SlotIntent.drop(slot, all))
    }

    /** A press outside the container window puts the cursor stack on the ground, as in vanilla. */
    internal fun dropOutside(button: Int) {
        if (session.carried.isEmpty) return
        session.submit(SlotIntent.dropCarried(all = button == LEFT_BUTTON))
    }

    private fun distributeIntent(button: Int, targets: List<Int>) = SlotIntent.distribute(
        targets,
        if (button == RIGHT_BUTTON) SlotDistributeMode.SINGLE else SlotDistributeMode.EVEN,
    )

    private fun cancelPending() {
        if (pendingButton != null) session.cancelPreview()
        pendingButton = null
        pendingSlot = -1
        dragged.clear()
    }

    private fun Int.asSlotButton() = if (this == RIGHT_BUTTON) SlotButton.RIGHT else SlotButton.LEFT

    private fun slotAt(x: Float, y: Float): Int =
        bounds.entries.firstOrNull { (_, rect) -> rect.contains(x, y) }?.key ?: -1

    private companion object {
        const val LEFT_BUTTON = 0
        const val RIGHT_BUTTON = 1
    }
}

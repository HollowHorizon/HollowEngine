package ru.hollowhorizon.hollowengine.client.gui.codeblocks

import de.fabmax.kool.math.Vec2f
import de.fabmax.kool.modules.ui2.MutableStateValue
import ru.hollowhorizon.hollowengine.common.codeblocks.isStatement
import ru.hollowhorizon.hollowengine.common.codeblocks.model.BlockModel
import ru.hollowhorizon.hollowengine.common.codeblocks.model.StatementBlock

interface EditorAction {
    fun execute()
    fun undo()
}

class HistoryManager(val editor: BlockEditor) {
    private val undoStack = ArrayDeque<EditorAction>()
    private val redoStack = ArrayDeque<EditorAction>()

    fun perform(action: EditorAction) {
        action.execute()
        undoStack.addLast(action)
        redoStack.clear()

        if (undoStack.size > HISTORY_SIZE) undoStack.removeFirst()
        editor.notifyChanged()
    }

    fun undo() {
        if (undoStack.isNotEmpty()) {
            val action = undoStack.removeLast()
            action.undo()
            redoStack.addLast(action)
            editor.notifyChanged()
        }
    }

    fun redo() {
        if (redoStack.isNotEmpty()) {
            val action = redoStack.removeLast()
            action.execute()
            undoStack.addLast(action)
            editor.notifyChanged()
        }
    }

    companion object {
        const val HISTORY_SIZE = 500
    }
}

class CompoundAction(private val actions: List<EditorAction>) : EditorAction {
    override fun execute() = actions.forEach { it.execute() }
    override fun undo() = actions.asReversed().forEach { it.undo() }
}

class MoveBlockAction(
    private val moves: Map<BlockModel, Pair<Vec2f, Vec2f>>
) : EditorAction {
    override fun execute() {
        moves.forEach { (block, positions) ->
            block.positionX.set(positions.second.x)
            block.positionY.set(positions.second.y)
        }
    }

    override fun undo() {
        moves.forEach { (block, positions) ->
            block.positionX.set(positions.first.x)
            block.positionY.set(positions.first.y)
        }
    }
}

class AddBlocksAction(
    private val editor: BlockEditor,
    private val blocks: List<BlockModel>
) : EditorAction {
    override fun execute() {
        editor.rootBlocks.addAll(blocks)
    }

    override fun undo() {
        editor.rootBlocks.removeAll(blocks)
    }
}

class RemoveBlocksAction(
    private val editor: BlockEditor,
    private val blocks: List<BlockModel>
) : EditorAction {
    override fun execute() {
        editor.rootBlocks.removeAll(blocks)
    }

    override fun undo() {
        editor.rootBlocks.addAll(blocks)
    }
}

class ValueChangeAction<T>(
    private val property: MutableStateValue<T>,
    private val oldValue: T,
    private val newValue: T
) : EditorAction {
    override fun execute() { property.set(newValue) }
    override fun undo() { property.set(oldValue) }
}

data class ConnectionState(
    val parentBlock: BlockModel?,
    val parentInputName: String?,
    val parentStatement: BlockModel?,
    val nextStatement: BlockModel?,
    val indexInRoot: Int = -1 // -1 if not in root
)

class ConnectionAction(
    private val editor: BlockEditor,
    private val block: BlockModel,
    private val oldState: ConnectionState,
    private val newState: ConnectionState
) : EditorAction {

    override fun execute() {
        applyState(newState)
    }

    override fun undo() {
        applyState(oldState)
    }

    private fun applyState(state: ConnectionState) {
        editor.controller.detachBlockInternal(block)

        val shouldBeInRoot = state.parentBlock == null && state.parentStatement == null

        if (shouldBeInRoot) {
            if (!editor.rootBlocks.contains(block)) {
                if (state.indexInRoot != -1 && state.indexInRoot <= editor.rootBlocks.size) {
                    editor.rootBlocks.add(state.indexInRoot, block)
                } else {
                    editor.rootBlocks.add(block)
                }
            }
        } else {
            editor.rootBlocks.remove(block)
        }

        if (state.parentBlock != null && state.parentInputName != null) {
            state.parentBlock.inputs[state.parentInputName] = block
            block.parentBlock = state.parentBlock
            block.parentInputName = state.parentInputName
        } else if (state.parentStatement != null && block.isStatement() && state.parentStatement.isStatement()) {
            state.parentStatement.next = block
            block.parent = state.parentStatement
        }

        if (block is StatementBlock && state.nextStatement != null && state.nextStatement.isStatement()) {
            val child = state.nextStatement

            if (child.parent != null && child.parent != block) {
                child.parent!!.next = null
            }

            block.next = child
            child.parent = block

            editor.rootBlocks.remove(child)
        } else if (block is StatementBlock && state.nextStatement == null) {
            val currentChild = block.next
            if (currentChild != null) {
                currentChild.parent = null
                if (!editor.rootBlocks.contains(currentChild)) {
                    editor.rootBlocks.add(currentChild)
                }
                block.next = null
            }
        }
    }
}
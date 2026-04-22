
import de.fabmax.kool.math.Vec2f
import de.fabmax.kool.modules.ui2.mutableStateListOf
import de.fabmax.kool.util.Color
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import ru.hollowhorizon.hollowengine.client.gui.codeblocks.*
import ru.hollowhorizon.hollowengine.common.codeblocks.*
import ru.hollowhorizon.hollowengine.common.codeblocks.model.BlockModel
import ru.hollowhorizon.hollowengine.common.codeblocks.model.ContainerBlock
import ru.hollowhorizon.hollowengine.common.codeblocks.model.ExpressionBlock
import ru.hollowhorizon.hollowengine.common.codeblocks.model.StatementBlock
import kotlin.test.*

// --- Mocks ---

class MockBlockProvider : BlockProvider("Test", BlockCategory("Test", null))

// Minimal implementation of concrete blocks for testing
class TestStatementBlock() : StatementBlock() {
    override val color: Color get() = Color.WHITE
    override suspend fun execute() {}
    override fun InputSlotScope.composeContent() {}
}

class TestContainerBlock() : StatementBlock(), ContainerBlock {
    override val color: Color get() = Color.WHITE
    override suspend fun execute() {}
    override fun InputSlotScope.composeContent() {}
}

class TestExpressionBlock(type: ExpressionType) : ExpressionBlock() {
    override val color: Color get() = Color.WHITE
    override val expressionType: ExpressionType = type
    override suspend fun execute() {}
    override fun InputSlotScope.composeContent() {}
}

class TestConsumerBlock(inputName: String = "VALUE", inputType: ExpressionType) : StatementBlock() {
    override val color: Color get() = Color.WHITE

    init {
        inputTypes[inputName] = inputType
    }

    override suspend fun execute() {}
    override fun InputSlotScope.composeContent() {}
}

class MockEditor : BlockEditor(MockBlockProvider(), {}) {
    override val rootBlocks = mutableStateListOf<BlockModel>()

    init {
        scale = 1f
    }

    override fun playConnectSound() {
        // В тестах нам не нужны звуки
    }
}

// --- Tests ---

class BlockControllerTests {
    private lateinit var editor: MockEditor

    @BeforeEach
    fun setup() {
        editor = MockEditor()
        // We use the real controller but with our MockEditor
        // Note: You might need to make 'snapAnimations' or 'creationPopup' accessible or mockable if they crash,
        // but based on the code provided, logic is mostly decoupled from rendering.
        try {
            // Need to utilize reflection or open up BlockEditor properties if they are private and crashing
            // Assuming BlockEditor constructor works fine without OpenGL context for logic tests
        } catch (e: Exception) {
            println("Warning: UI dependencies might prevent pure unit testing without headless mocking.")
        }
    }

    @Test
    fun `test drag and drop moves block`() {
        val block = TestStatementBlock()
        block.positionX.set(10f)
        block.positionY.set(10f)
        editor.rootBlocks.add(block)

        val controller = editor.controller

        // Start Drag
        controller.handleDragStart(block, Vec2f(10f, 10f), Vec2f(0f, 0f))

        // Drag
        controller.handleDrag(block, Vec2f(60f, 60f)) // Moved 50px

        // End Drag
        controller.handleDragEnd(block)

        assertEquals(60f, block.positionX.value, 0.1f)
        assertEquals(60f, block.positionY.value, 0.1f)
    }

    @Test
    fun `test undo redo movement`() {
        val block = TestStatementBlock()
        block.positionX.set(0f)
        block.positionY.set(0f)
        editor.rootBlocks.add(block)

        val controller = editor.controller

        // Move 1
        controller.handleDragStart(block, Vec2f(0f, 0f), Vec2f(0f, 0f))
        controller.handleDrag(block, Vec2f(100f, 100f))
        controller.handleDragEnd(block)

        assertEquals(100f, block.positionX.value)

        // Undo
        controller.history.undo()
        assertEquals(0f, block.positionX.value)

        // Redo
        controller.history.redo()
        assertEquals(100f, block.positionX.value)
    }

    @Test
    fun `test connect two statements`() {
        val blockA = TestStatementBlock()
        val blockB = TestStatementBlock()
        editor.rootBlocks.add(blockA)
        editor.rootBlocks.add(blockB)

        val controller = editor.controller

        // Simulate attaching B after A
        // We need to simulate the DropTarget detection.
        // Since we can't easily mock UI hit testing, we manually setup the action.

        // 1. Start dragging B
        controller.handleDragStart(blockB, Vec2f(0f, 0f), Vec2f(0f, 0f))

        // 2. Mock the drop target finding
        val action = DropAction.AttachAfter(blockA)
        // We inject this via reflection or by modifying Controller to allow setting potentialAction for tests
        // OR better: call the logic method directly if it was public.
        // Since handleDragEnd uses private potentialAction, let's use reflection to set it.
        setPotentialAction(controller, action)

        // 3. End Drag
        controller.handleDragEnd(blockB)

        // Assertions
        assertEquals(blockB, blockA.next)
        assertEquals(blockA, blockB.parent)
        assertFalse(editor.rootBlocks.contains(blockB)) // B should be removed from root
    }

    @Test
    fun `test undo connection restores blocks to root`() {
        val blockA = TestStatementBlock()
        val blockB = TestStatementBlock()

        blockA.positionX.set(10f)
        blockB.positionX.set(200f) // Far away

        editor.rootBlocks.add(blockA)
        editor.rootBlocks.add(blockB)

        val controller = editor.controller

        // Connect B to A
        controller.handleDragStart(blockB, Vec2f(200f, 0f), Vec2f(0f, 0f))
        setPotentialAction(controller, DropAction.AttachAfter(blockA))
        controller.handleDragEnd(blockB)

        // Verify connected
        assertNotNull(blockA.next)

        // Undo
        controller.history.undo()

        // Verify disconnected
        assertNull(blockA.next)
        assertNull(blockB.parent)
        assertTrue(editor.rootBlocks.contains(blockB))

        // Verify positions restored
        assertEquals(200f, blockB.positionX.value, 0.1f)
    }

    @Test
    fun `test insert block between two blocks`() {
        val blockA = TestStatementBlock()
        val blockB = TestStatementBlock()
        val blockC = TestStatementBlock()

        // Setup A -> B
        blockA.next = blockB
        blockB.parent = blockA
        editor.rootBlocks.add(blockA)
        // B is not in root
        editor.rootBlocks.add(blockC)

        val controller = editor.controller

        // Drag C to insert before B (which effectively means AttachAfter A or InsertBefore B)
        // Let's use InsertBefore B
        controller.handleDragStart(blockC, Vec2f(0f, 0f), Vec2f(0f, 0f))
        setPotentialAction(controller, DropAction.InsertBefore(blockB))
        controller.handleDragEnd(blockC)

        // Expected: A -> C -> B
        assertEquals(blockC, blockA.next, "A should point to C")
        assertEquals(blockA, blockC.parent, "C parent should be A")
        assertEquals(blockB, blockC.next, "C should point to B")
        assertEquals(blockC, blockB.parent, "B parent should be C")
    }

    @Test
    fun `test undo insertion`() {
        // Setup same as above
        val blockA = TestStatementBlock()
        val blockB = TestStatementBlock()
        val blockC = TestStatementBlock()

        blockA.next = blockB
        blockB.parent = blockA
        editor.rootBlocks.add(blockA)
        editor.rootBlocks.add(blockC)

        val controller = editor.controller
        controller.handleDragStart(blockC, Vec2f(0f, 0f), Vec2f(0f, 0f))
        setPotentialAction(controller, DropAction.InsertBefore(blockB))
        controller.handleDragEnd(blockC)

        // Undo
        controller.history.undo()

        // Expected: A -> B, C separate
        assertEquals(blockB, blockA.next)
        assertEquals(blockA, blockB.parent)
        assertNull(blockC.parent)
        assertNull(blockC.next)
        assertTrue(editor.rootBlocks.contains(blockC))
    }

    @Test
    fun `test delete chain logic`() {
        val blockA = TestStatementBlock()
        val blockB = TestStatementBlock()
        val blockC = TestStatementBlock()

        // A -> B -> C
        blockA.next = blockB
        blockB.parent = blockA
        blockB.next = blockC
        blockC.parent = blockB

        editor.rootBlocks.add(blockA)

        val controller = editor.controller

        // Select B
        controller.selectSingle(blockB)

        // Delete B
        controller.deleteSelected()

        // Expected: A -> C
        assertEquals(blockC, blockA.next)
        assertEquals(blockA, blockC.parent)
        assertFalse(editor.rootBlocks.contains(blockB))
    }

    @Test
    fun `test undo delete chain`() {
        val blockA = TestStatementBlock()
        val blockB = TestStatementBlock()
        val blockC = TestStatementBlock()

        blockA.next = blockB
        blockB.parent = blockA
        blockB.next = blockC
        blockC.parent = blockB
        editor.rootBlocks.add(blockA)

        val controller = editor.controller
        controller.selectSingle(blockB)
        controller.deleteSelected()

        // Undo
        controller.history.undo()

        // Expected: A -> B -> C
        assertEquals(blockB, blockA.next, "A should point back to B")
        assertEquals(blockA, blockB.parent)
        assertEquals(blockC, blockB.next, "B should point back to C")
        assertEquals(blockB, blockC.parent)
    }

    @Test
    fun `test container nesting undo`() {
        val container = TestContainerBlock()
        val statement = TestStatementBlock()

        editor.rootBlocks.add(container)
        editor.rootBlocks.add(statement)

        val controller = editor.controller

        // Drag statement into container input "CONTENT"
        // Assuming TestContainerBlock has an input called "CONTENT" or we add it dynamically if the map is mutable
        // The implementation uses inputs map.

        controller.handleDragStart(statement, Vec2f(0f, 0f), Vec2f(0f, 0f))
        setPotentialAction(controller, DropAction.AttachToInput(container, "CONTENT", isStatementSlot = true))
        controller.handleDragEnd(statement)

        assertEquals(statement, container.inputs["CONTENT"])
        assertEquals(container, statement.parentBlock)

        // Undo
        controller.history.undo()

        assertNull(container.inputs["CONTENT"])
        assertNull(statement.parentBlock)
        assertTrue(editor.rootBlocks.contains(statement))
    }

    @Test
    fun `test safety overwrite protection`() {
        val parent = TestStatementBlock()
        val oldChild = TestStatementBlock()
        val newChild = TestStatementBlock()

        // 1. Исходное состояние: Parent -> OldChild
        parent.next = oldChild
        oldChild.parent = parent

        editor.rootBlocks.add(parent)
        // oldChild не в root, так как он присоединен
        editor.rootBlocks.add(newChild) // newChild пока болтается отдельно

        // 2. Создаем Action, который пытается прицепить newChild к Parent
        // При этом мы намеренно НЕ отсоединяем oldChild вручную,
        // имитируя ошибку в порядке действий Undo или сбой логики.

        val targetState = ConnectionState(
            parentBlock = null,
            parentInputName = null,
            parentOutputName = null,
            parentStatement = parent, // Цепляем к Parent
            nextStatement = null,
            indexInRoot = -1,
            positionX = 0f,
            positionY = 0f
        )

        // ВАЖНО: Мы используем ConnectionAction напрямую, чтобы проверить его устойчивость
        val action = ConnectionAction(
            editor, newChild,
            oldState = editor.controller.captureConnectionState(newChild), // Неважно
            newState = targetState
        )

        action.execute()

        // --- ПРОВЕРКИ ---

        // 1. Parent теперь указывает на NewChild? (Это произойдет в любом случае)
        assertEquals(newChild, parent.next, "Parent should point to NewChild")
        assertEquals(parent, newChild.parent, "NewChild should point to Parent")

        // 2. А вот здесь важный момент:
        // Что случилось с OldChild?
        // Если OldChild.parent всё ещё Parent, но Parent.next уже NewChild, то это не правильно, т.к. родитель в этом случае может быть только один.
        // В этом случае жто поломанный граф. OldChild просто пропадёт.

        assertNull(oldChild.parent, "OldChild must be detached from Parent!")

        // 3. OldChild должен был вернуться в rootBlocks (или быть корректно отброшен)
        assertTrue(editor.rootBlocks.contains(oldChild), "Displaced OldChild must return to rootBlocks")

        // В теории такого в принципе произойти не должно и редактор сам должен не допускать подобного поведения, но на всякий случай пусть будет
    }

    @Test
    fun `test expression connects to compatible input`() {
        val consumer = TestConsumerBlock("TEXT", typeOf<String>())
        val expression = TestExpressionBlock(typeOf<String>())

        editor.rootBlocks.add(consumer)
        editor.rootBlocks.add(expression)

        editor.controller.handleDragStart(expression, Vec2f.ZERO, Vec2f.ZERO)

        setPotentialAction(editor.controller, DropAction.AttachToInput(consumer, "TEXT", true))

        editor.controller.handleDragEnd(expression)

        assertEquals(expression, consumer.inputs["TEXT"], "Expression should be in consumer input")
        assertEquals(consumer, expression.parentBlock, "Consumer should be parentBlock")
        assertEquals("TEXT", expression.parentInputName, "Input name should be set")
        assertFalse(editor.rootBlocks.contains(expression), "Expression should be removed from root")
    }

    @Test
    fun `test expression undo connection`() {
        val consumer = TestConsumerBlock("TEXT", typeOf<String>())
        val expression = TestExpressionBlock(typeOf<String>())

        editor.rootBlocks.add(consumer)
        editor.rootBlocks.add(expression)
        val startX = expression.positionX.value

        editor.controller.handleDragStart(expression, Vec2f.ZERO, Vec2f.ZERO)
        setPotentialAction(editor.controller, DropAction.AttachToInput(consumer, "TEXT", true))
        editor.controller.handleDragEnd(expression)

        editor.controller.history.undo()

        // Проверки восстановления
        assertNull(consumer.inputs["TEXT"], "Input should be empty")
        assertNull(expression.parentBlock, "Parent block link should be cleared")
        assertTrue(editor.rootBlocks.contains(expression), "Expression should be back in root")
        assertEquals(startX, expression.positionX.value, 0.1f, "Position should be restored")
    }

    @Test
    fun `test type mismatch prevention`() {
        val mathBlock = TestConsumerBlock("NUMBER", typeOf<Int>())
        val stringExpr = TestExpressionBlock(typeOf<String>())

        editor.rootBlocks.add(mathBlock)
        editor.rootBlocks.add(stringExpr)

        editor.controller.handleDragStart(stringExpr, Vec2f.ZERO, Vec2f.ZERO)
        val action = DropAction.AttachToInput(mathBlock, "NUMBER", true)

        editor.controller.isValidDrop(stringExpr, action)
        val isValid = editor.controller.isValidDrop(stringExpr, action)

        assertFalse(isValid, "Controller should reject String -> Int connection")
    }

    @Test
    fun `test expression swap (replace existing)`() {
        val consumer = TestConsumerBlock("TEXT", typeOf<String>())
        val oldExpr = TestExpressionBlock(typeOf<String>())
        val newExpr = TestExpressionBlock(typeOf<String>())

        consumer.inputs["TEXT"] = oldExpr
        oldExpr.parentBlock = consumer
        oldExpr.parentInputName = "TEXT"

        editor.rootBlocks.add(consumer)
        editor.rootBlocks.add(newExpr)

        // 2. Драгаем New поверх Old (или в тот же слот)
        editor.controller.handleDragStart(newExpr, Vec2f.ZERO, Vec2f.ZERO)
        setPotentialAction(editor.controller, DropAction.AttachToInput(consumer, "TEXT", true))
        editor.controller.handleDragEnd(newExpr)

        // Проверки
        assertEquals(newExpr, consumer.inputs["TEXT"], "New expr should occupy the slot")
        assertEquals(consumer, newExpr.parentBlock)

        // oldExpr должен был быть "выплюнуть" в корень
        assertTrue(editor.rootBlocks.contains(oldExpr), "Old expr should be ejected to root")
        assertNull(oldExpr.parentBlock, "Old expr should be detached")
    }

    @Test
    fun `test undo swap`() {
        // Тест на "Коллизию" и восстановление при замене
        val consumer = TestConsumerBlock("TEXT", typeOf<String>())
        val oldExpr = TestExpressionBlock(typeOf<String>())
        val newExpr = TestExpressionBlock(typeOf<String>())

        // Начальное состояние
        consumer.inputs["TEXT"] = oldExpr
        oldExpr.parentBlock = consumer
        oldExpr.parentInputName = "TEXT"
        editor.rootBlocks.add(consumer)
        editor.rootBlocks.add(newExpr)

        // Выполняем замену
        editor.controller.handleDragStart(newExpr, Vec2f.ZERO, Vec2f.ZERO)
        setPotentialAction(editor.controller, DropAction.AttachToInput(consumer, "TEXT", true))
        editor.controller.handleDragEnd(newExpr)

        // Undo
        editor.controller.history.undo()

        // Проверяем возврат к начальному состоянию
        assertEquals(oldExpr, consumer.inputs["TEXT"], "Old expr should be back in slot")
        assertEquals(consumer, oldExpr.parentBlock)

        assertTrue(editor.rootBlocks.contains(newExpr), "New expr should be back in root")
        assertNull(newExpr.parentBlock)
    }

    @Test
    fun `test any type accepts everything`() {
        // Блок принимает AnyType (например, "Print Object")
        val logBlock = TestConsumerBlock("OBJ", AnyType)

        val stringExpr = TestExpressionBlock(typeOf<String>())
        val intExpr = TestExpressionBlock(typeOf<Int>())

        // Проверка String -> Any
        assertTrue(editor.controller.isValidDrop(stringExpr, DropAction.AttachToInput(logBlock, "OBJ", false)))

        // Проверка Int -> Any
        assertTrue(editor.controller.isValidDrop(intExpr, DropAction.AttachToInput(logBlock, "OBJ", false)))
    }

    // --- Helpers ---

    private fun setPotentialAction(controller: BlockController, action: DropAction?) {
        val field = BlockController::class.java.getDeclaredField("potentialAction")
        field.isAccessible = true
        field.set(controller, action)
    }
}


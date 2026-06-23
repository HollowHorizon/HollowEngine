import androidx.compose.runtime.mutableStateOf
import org.lwjgl.glfw.GLFW
import ru.hollowhorizon.hollowengine.client.ui.*
import ru.hollowhorizon.hollowengine.client.ui.hss.CompiledHss
import ru.hollowhorizon.hollowengine.client.ui.hss.compileHss
import ru.hollowhorizon.hollowengine.client.ui.render.textFieldIndentGuideColumns
import ru.hollowhorizon.hollowengine.client.ui.xml.UiXmlContent
import ru.hollowhorizon.hollowengine.client.ui.xml.UiXmlTree
import ru.hollowhorizon.hollowengine.client.ui.xml.parseUiXml
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertSame
import kotlin.test.assertTrue

class UiComposeTests {
    @Test
    fun `compose updates existing ui nodes on state change`() {
        val label = mutableStateOf("First")

        HollowUiComposition().use { composition ->
            val root = composition.setContent {
                Column(id = "panel") {
                    Text(label.value, id = "label")
                }
            }
            val panel = root.children.single() as BoxNode
            val text = panel.children.single() as TextNode

            label.value = "Second"
            composition.applyPendingChanges()

            val recomposedPanel = root.children.single() as BoxNode
            val recomposedText = recomposedPanel.children.single() as TextNode
            assertSame(panel, recomposedPanel)
            assertSame(text, recomposedText)
            assertEquals("Second", recomposedText.text.template)
        }
    }

    @Test
    fun `compose preserves text field state across unrelated recomposition`() {
        val title = mutableStateOf("Title")
        val serverValue = mutableStateOf("server")

        HollowUiComposition().use { composition ->
            val root = composition.setContent {
                Column {
                    Text(title.value, id = "title")
                    TextField(serverValue.value, id = "field")
                }
            }
            val field = root.textField()
            field.insert("!")

            title.value = "Changed title"
            composition.applyPendingChanges()

            assertSame(field, root.textField())
            assertEquals("server!", root.textField().value)

            serverValue.value = "remote"
            composition.applyPendingChanges()

            assertSame(field, root.textField())
            assertEquals("remote", root.textField().value)
        }
    }

    @Test
    fun `controlled text field updates do not invoke on change`() {
        val serverValue = mutableStateOf("first")
        var changes = 0

        HollowUiComposition().use { composition ->
            val root = composition.setContent {
                TextField(serverValue.value, id = "field", onChange = { changes++ })
            }
            val field = root.textField()

            serverValue.value = "second"
            composition.applyPendingChanges()

            assertEquals("second", field.value)
            assertEquals(0, changes)

            field.insert("!")
            assertEquals(1, changes)
        }
    }

    @Test
    fun `text field normalizes carriage return line endings`() {
        val field = TextFieldNode(mode = UiTextFieldMode.MULTI_LINE)

        field.insert("first\r\nsecond\rthird")

        assertEquals("first\nsecond\nthird", field.value)
    }

    @Test
    fun `text field merges rapid edits into one undo entry`() {
        val field = TextFieldNode(mode = UiTextFieldMode.MULTI_LINE)

        field.insert("a")
        field.insert("b")
        field.insert("c")

        assertTrue(field.undo())
        assertEquals("", field.value)
        assertTrue(field.redo())
        assertEquals("abc", field.value)

        field.moveCaret(0)
        field.insert("x")
        assertTrue(field.undo())
        assertEquals("abc", field.value)
    }

    @Test
    fun `text field applies insertion to multiple carets`() {
        val field = TextFieldNode("ac", mode = UiTextFieldMode.MULTI_LINE, multiCaret = true)

        field.setCaretRanges(listOf(UiTextCaret(1), UiTextCaret(2)))
        field.insert("b")

        assertEquals("abcb", field.value)
        assertEquals(listOf(2, 4), field.carets)
    }

    @Test
    fun `text field can delete whole words around the caret`() {
        val field = TextFieldNode("alpha beta gamma")

        field.moveCaret("alpha beta".length)
        field.backspace(word = true)
        assertEquals("alpha  gamma", field.value)

        field.moveCaret("alpha  ".length)
        field.deleteForward(word = true)
        assertEquals("alpha  ", field.value)
    }

    @Test
    fun `text completion replaces prefix and moves caret inside template`() {
        val field = TextFieldNode(
            "Text",
            completionContributor = UiCompletionContributor {
                listOf(UiTextCompletion("TextField", "TextField(value = \"\")", caretOffset = "TextField(value = \"".length))
            },
        )

        field.moveCaret(4)
        field.openCompletions()
        field.acceptCompletion()

        assertEquals("TextField(value = \"\")", field.value)
        assertEquals("TextField(value = \"".length, field.caret)
    }

    @Test
    fun `typing identifier opens and refreshes completions`() {
        val input = HollowUiInputController()

        HollowUiSurface().use { runtime ->
            runtime.setContent {
                TextField(
                    value = "",
                    id = "field",
                    completionContributor = UiCompletionContributor { context ->
                        listOf(UiTextCompletion(context.text.uppercase()))
                    },
                    modifier = Modifier.size(120.px, 20.px),
                )
            }
            val frame = runtime.frame(140f, 40f)
            val field = frame.resolved.styles.keys.filterIsInstance<TextFieldNode>().single()
            input.focus(frame, "field") { it.node.dispatch(it) }

            input.charTyped(frame, 'a', modifiers = 0) { it.node.dispatch(it) }

            assertTrue(field.completionActive)
            assertEquals(listOf("A"), field.completionItems.map { it.label })
        }
    }

    @Test
    fun `alt enter opens completions`() {
        val input = HollowUiInputController()

        HollowUiSurface().use { runtime ->
            runtime.setContent {
                TextField(
                    value = "value",
                    id = "field",
                    completionContributor = UiCompletionContributor { listOf(UiTextCompletion("valueOf")) },
                    modifier = Modifier.size(120.px, 20.px),
                )
            }
            val frame = runtime.frame(140f, 40f)
            val field = frame.resolved.styles.keys.filterIsInstance<TextFieldNode>().single()
            input.focus(frame, "field") { it.node.dispatch(it) }

            val result = input.keyPressed(
                frame,
                GLFW.GLFW_KEY_ENTER,
                scanCode = 0,
                modifiers = GLFW.GLFW_MOD_ALT,
            ) { it.node.dispatch(it) }

            assertTrue(result.changed)
            assertTrue(field.completionActive)
            assertEquals(listOf("valueOf"), field.completionItems.map { it.label })
        }
    }

    @Test
    fun `text field normalizes carriage returns before analysis offsets are used`() {
        val field = TextFieldNode("first\r\nsecond\rthird", mode = UiTextFieldMode.MULTI_LINE)

        assertEquals("first\nsecond\nthird", field.value)
        assertEquals(field.value.length, field.caret)
    }

    @Test
    fun `text completion state no longer emits legacy text field popup commands`() {
        HollowUiSurface().use { runtime ->
            runtime.setContent {
                TextField(
                    value = "A",
                    id = "field",
                    completionContributor = UiCompletionContributor {
                        listOf(UiTextCompletion("AlphaOption", "AlphaOption()", "template"))
                    },
                    modifier = Modifier.size(140.px, 40.px),
                )
            }
            val initial = runtime.frame(160f, 80f)
            val field = initial.resolved.styles.keys.filterIsInstance<TextFieldNode>().single()
            field.moveCaret(1)
            field.openCompletions()

            val frame = runtime.frame(160f, 80f)
            val popupBoxes = frame.commands.filterIsInstance<DrawBoxCommand>().filter { it.node == field }
            val popupTexts = frame.commands.filterIsInstance<DrawTextCommand>().filter { it.node == field }

            assertEquals(listOf("AlphaOption"), field.completionItems.map { it.label })
            assertTrue(popupBoxes.none { it.phase == UiRenderPhase.OVERLAY })
            assertTrue(popupTexts.none { it.text == "AlphaOption" || it.text == "template" })
        }
    }

    @Test
    fun `background and text commands use stable render phases`() {
        HollowUiSurface().use { runtime ->
            val frame = runtime.frame(
                content = {
                    Box(
                        id = "card",
                        modifier = Modifier.then(
                            Modifier.size(100.px, 24.px),
                            Modifier.background(UiColor(0.12f, 0.14f, 0.18f, 1f)),
                        ),
                    ) {
                        Text("Title", id = "title")
                    }
                },
                width = 120f,
                height = 40f,
            )
            val card = frame.resolved.styles.keys.single { it.id == "card" }
            val title = frame.resolved.styles.keys.single { it.id == "title" }
            val background = frame.commands.filterIsInstance<DrawBoxCommand>().single { it.node == card }
            val text = frame.commands.filterIsInstance<DrawTextCommand>().single { it.node == title }

            assertEquals(UiRenderPhase.BACKGROUND, background.phase)
            assertEquals(UiRenderPhase.CONTENT, text.phase)
        }
    }

    @Test
    fun `tab accepts text completion before focus traversal`() {
        val input = HollowUiInputController()

        HollowUiSurface().use { runtime ->
            runtime.setContent {
                Column {
                    TextField(
                        value = "Fo",
                        id = "field",
                        completionContributor = UiCompletionContributor {
                            listOf(UiTextCompletion("FooBar", "FooBar"))
                        },
                        modifier = Modifier.size(120.px, 20.px),
                    )
                    TextField(value = "", id = "next", modifier = Modifier.size(120.px, 20.px))
                }
            }
            val frame = runtime.frame(160f, 60f)
            val field = frame.resolved.styles.keys.filterIsInstance<TextFieldNode>().single { it.id == "field" }

            input.focus(frame, "field") { it.node.dispatch(it) }
            field.moveCaret(2)
            field.openCompletions()
            val result = input.keyPressed(frame, GLFW.GLFW_KEY_TAB, scanCode = 0, modifiers = 0) { it.node.dispatch(it) }

            assertTrue(result.changed)
            assertEquals("FooBar", field.value)
            assertEquals("field", input.focusedKey)
        }
    }

    @Test
    fun `tab moves focus when focused text field does not consume it`() {
        val input = HollowUiInputController()

        HollowUiSurface().use { runtime ->
            runtime.setContent {
                Column {
                    TextField(value = "first", id = "first", modifier = Modifier.size(120.px, 20.px))
                    TextField(value = "second", id = "second", modifier = Modifier.size(120.px, 20.px))
                }
            }
            val frame = runtime.frame(160f, 60f)

            input.focus(frame, "first") { it.node.dispatch(it) }
            input.keyPressed(frame, GLFW.GLFW_KEY_TAB, scanCode = 0, modifiers = 0) { it.node.dispatch(it) }

            assertEquals("second", input.focusedKey)
        }
    }

    @Test
    fun `input scroll target falls back to focused scrollable node`() {
        val input = HollowUiInputController()

        HollowUiSurface().use { runtime ->
            runtime.setContent {
                TextField(
                    value = "line 1\nline 2\nline 3\nline 4\nline 5",
                    mode = UiTextFieldMode.MULTI_LINE,
                    id = "editor",
                    modifier = Modifier.then(
                        Modifier.size(80.px, 20.px),
                        Modifier.input(scrollable = true),
                    ),
                )
            }
            val frame = runtime.frame(160f, 80f)
            val editor = frame.resolved.styles.keys.filterIsInstance<TextFieldNode>().single { it.id == "editor" }

            input.focus(frame, "editor") { it.node.dispatch(it) }

            assertTrue(frame.layout[editor].scrollRange.y > 0f)
            assertSame(editor, input.scrollTargetAt(frame, x = 140f, y = 60f))
        }
    }

    @Test
    fun `text field keeps empty completion result pending without activating popup`() {
        var calls = 0
        val field = TextFieldNode(
            value = "val value = ",
            mode = UiTextFieldMode.MULTI_LINE,
            completionContributor = UiCompletionContributor {
                calls++
                if (calls == 1) emptyList() else listOf(UiTextCompletion("valueOf"))
            },
        )

        assertFalse(field.openCompletions())
        assertFalse(field.completionActive)
        assertEquals(emptyList(), field.completionItems)
        assertTrue(field.resolvePendingCompletions())
        assertTrue(field.completionActive)
        assertEquals(listOf("valueOf"), field.completionItems.map { it.label })

        val pendingField = TextFieldNode(
            value = "val value = ",
            mode = UiTextFieldMode.MULTI_LINE,
            completionContributor = UiCompletionContributor { emptyList() },
        )
        assertFalse(pendingField.openCompletions())

        val restored = TextFieldNode(
            value = "val value = ",
            mode = UiTextFieldMode.MULTI_LINE,
            completionContributor = UiCompletionContributor { listOf(UiTextCompletion("valueOf")) },
        )
        restored.importState(pendingField.exportState())
        assertTrue(restored.resolvePendingCompletions())
        assertTrue(restored.completionActive)
        assertEquals(listOf("valueOf"), restored.completionItems.map { it.label })

        val activeField = TextFieldNode(
            value = "Fo",
            completionContributor = UiCompletionContributor { listOf(UiTextCompletion("Foo")) },
        )
        assertTrue(activeField.openCompletions())
        activeField.applyExternalValue("bar")
        assertFalse(activeField.completionActive)
        assertEquals(emptyList(), activeField.completionItems)
    }

    @Test
    fun `input controller resolves pending completions after state is restored`() {
        val input = HollowUiInputController()
        val pendingField = TextFieldNode(
            value = "HollowEngine.",
            mode = UiTextFieldMode.MULTI_LINE,
            id = "editor",
            completionContributor = UiCompletionContributor { emptyList() },
        )
        val pendingRoot = BoxNode()
        pendingRoot.children += pendingField

        input.prepareRoot(pendingRoot)
        assertFalse(pendingField.openCompletions())
        input.saveState(pendingField)

        val restoredField = TextFieldNode(
            value = "HollowEngine.",
            mode = UiTextFieldMode.MULTI_LINE,
            id = "editor",
            completionContributor = UiCompletionContributor { listOf(UiTextCompletion("LOGGER")) },
        )
        val restoredRoot = BoxNode()
        restoredRoot.children += restoredField

        input.prepareRoot(restoredRoot)

        assertTrue(restoredField.completionActive)
        assertEquals(listOf("LOGGER"), restoredField.completionItems.map { it.label })
    }

    @Test
    fun `ctrl word navigation stops at line breaks`() {
        val input = HollowUiInputController()

        HollowUiSurface().use { runtime ->
            runtime.setContent {
                TextField(
                    value = "one\ntwo",
                    mode = UiTextFieldMode.MULTI_LINE,
                    id = "editor",
                    modifier = Modifier.size(120.px, 50.px),
                )
            }
            val frame = runtime.frame(140f, 70f)
            val editor = frame.resolved.styles.keys.filterIsInstance<TextFieldNode>().single { it.id == "editor" }
            input.focus(frame, "editor") { it.node.dispatch(it) }

            editor.moveCaret("one".length)
            input.keyPressed(frame, GLFW.GLFW_KEY_RIGHT, scanCode = 0, modifiers = GLFW.GLFW_MOD_CONTROL) { it.node.dispatch(it) }
            assertEquals("one\n".length, editor.caret)

            input.keyPressed(frame, GLFW.GLFW_KEY_LEFT, scanCode = 0, modifiers = GLFW.GLFW_MOD_CONTROL) { it.node.dispatch(it) }
            assertEquals("one".length, editor.caret)
        }
    }

    @Test
    fun `ctrl word deletion stops at line breaks`() {
        val backspaceField = TextFieldNode("one\ntwo", mode = UiTextFieldMode.MULTI_LINE)
        backspaceField.moveCaret("one\n".length)

        assertTrue(backspaceField.backspace(word = true))
        assertEquals("onetwo", backspaceField.value)
        assertEquals("one".length, backspaceField.caret)

        val deleteField = TextFieldNode("one\ntwo", mode = UiTextFieldMode.MULTI_LINE)
        deleteField.moveCaret("one".length)

        assertTrue(deleteField.deleteForward(word = true))
        assertEquals("onetwo", deleteField.value)
        assertEquals("one".length, deleteField.caret)
    }

    @Test
    fun `code text field inserts configured spaces on tab without changing focus`() {
        val input = HollowUiInputController()

        HollowUiSurface().use { runtime ->
            runtime.setContent {
                Row {
                    TextField(
                        value = "",
                        mode = UiTextFieldMode.MULTI_LINE,
                        indentSize = 4,
                        id = "editor",
                    )
                    TextField(value = "", id = "next")
                }
            }
            val frame = runtime.frame(160f, 60f)
            val editor = frame.resolved.styles.keys.filterIsInstance<TextFieldNode>().single { it.id == "editor" }

            input.focus(frame, "editor") { it.node.dispatch(it) }
            input.keyPressed(frame, GLFW.GLFW_KEY_TAB, scanCode = 0, modifiers = 0) { it.node.dispatch(it) }

            assertEquals("editor", input.focusedKey)
            assertEquals("    ", editor.value)
        }
    }

    @Test
    fun `plain text field keeps tab focus navigation when indent is not configured`() {
        val input = HollowUiInputController()

        HollowUiSurface().use { runtime ->
            runtime.setContent {
                Row {
                    TextField(value = "", id = "first")
                    TextField(value = "", id = "second")
                }
            }
            val frame = runtime.frame(160f, 60f)

            input.focus(frame, "first") { it.node.dispatch(it) }
            input.keyPressed(frame, GLFW.GLFW_KEY_TAB, scanCode = 0, modifiers = 0) { it.node.dispatch(it) }

            assertEquals("second", input.focusedKey)
        }
    }

    @Test
    fun `text field auto pairs brackets and skips matching closing quote`() {
        val field = TextFieldNode(
            value = "",
            mode = UiTextFieldMode.MULTI_LINE,
            autoPairs = true,
        )

        assertTrue(field.typeCharacter('"'))
        assertEquals("\"\"", field.value)
        assertEquals(1, field.caret)

        assertTrue(field.typeCharacter('"'))
        assertEquals("\"\"", field.value)
        assertEquals(2, field.caret)
    }

    @Test
    fun `text field backspace removes matching auto pair`() {
        val field = TextFieldNode(
            value = "",
            mode = UiTextFieldMode.MULTI_LINE,
            autoPairs = true,
        )

        field.typeCharacter('(')
        assertEquals("()", field.value)
        assertEquals(1, field.caret)

        assertTrue(field.backspace())
        assertEquals("", field.value)
        assertEquals(0, field.caret)
    }

    @Test
    fun `code text field keeps contextual indent on newline`() {
        val field = TextFieldNode(
            value = "story {}",
            mode = UiTextFieldMode.MULTI_LINE,
            indentSize = 4,
        )
        field.moveCaret("story {".length)

        assertTrue(field.insertNewlineWithIndent())
        assertEquals("story {\n    \n}", field.value)
        assertEquals("story {\n    ".length, field.caret)

        assertTrue(field.insertNewlineWithIndent())
        assertEquals("story {\n    \n    \n}", field.value)
        assertEquals("story {\n    \n    ".length, field.caret)
    }

    @Test
    fun `backspace removes whitespace only line`() {
        val field = TextFieldNode("first\n    \nsecond", mode = UiTextFieldMode.MULTI_LINE)
        field.moveCaret("first\n  ".length)

        assertTrue(field.backspace())
        assertEquals("first\nsecond", field.value)
        assertEquals("first".length, field.caret)
    }

    @Test
    fun `triple click selects whole text field line`() {
        val input = HollowUiInputController()

        HollowUiSurface().use { runtime ->
            runtime.setContent {
                TextField(
                    value = "alpha beta\nnext",
                    mode = UiTextFieldMode.MULTI_LINE,
                    id = "editor",
                    modifier = Modifier.size(180.px, 60.px),
                )
            }
            val frame = runtime.frame(200f, 80f)
            val editor = frame.resolved.styles.keys.filterIsInstance<TextFieldNode>().single { it.id == "editor" }

            repeat(3) {
                input.mouseClicked(frame, mouseX = 8f, mouseY = 8f, button = 0, dispatch = { it.node.dispatch(it) }, openUrl = {})
            }

            assertEquals(0, editor.selectionStart)
            assertEquals("alpha beta".length, editor.selectionEnd)
        }
    }

    @Test
    fun `completion import is inserted once and sorted before applying item`() {
        val field = TextFieldNode(
            value = "import z.Z\n\nfun main() {\n    Foo\n}",
            mode = UiTextFieldMode.MULTI_LINE,
            completionContributor = UiCompletionContributor {
                listOf(UiTextCompletion("Foo", importFqName = "a.Foo"))
            },
        )
        field.moveCaret(field.value.indexOf("Foo") + "Foo".length)

        assertTrue(field.openCompletions())
        assertTrue(field.acceptCompletion())

        assertEquals(
            "import a.Foo\nimport z.Z\n\nfun main() {\n    Foo\n}",
            field.value,
        )
    }

    @Test
    fun `indent guides render one indent level before text`() {
        assertEquals(emptyList(), textFieldIndentGuideColumns("    value", indentSize = 4))
        assertEquals(listOf(4), textFieldIndentGuideColumns("        value", indentSize = 4))
        assertEquals(listOf(4, 8), textFieldIndentGuideColumns("            value", indentSize = 4))
    }

    @Test
    fun `text selection fills line gaps and empty lines`() {
        val layout = UiTextLayouter.layout(
            "a\n\nb",
            80f,
            Float.POSITIVE_INFINITY,
            false,
            UiTextAlign.LEFT,
            12f,
            preserveWhitespace = true,
            lineSpacing = 4f,
        )

        val rects = layout.selectionRects(0, 4, 12f, fillLineGaps = true)

        assertTrue(rects.any { it.height > 12f })
        assertTrue(rects.any { it.width == layout.width })
    }

    @Test
    fun `text field default keymap is a modifier fallback`() {
        var intercepted = false

        HollowUiSurface().use { runtime ->
            val frame = runtime.frame(
                content = {
                    TextField(
                        value = "abc",
                        id = "field",
                        modifier = Modifier.onKeyInput { input ->
                            intercepted = input.key == GLFW.GLFW_KEY_LEFT
                            intercepted
                        },
                    )
                },
                width = 120f,
                height = 32f,
            )
            val field = frame.resolved.styles.keys.filterIsInstance<TextFieldNode>().single()
            field.moveCaret(3)

            val event = UiEvent(UiEventKind.KEY_PRESSED, field, frame = frame, key = GLFW.GLFW_KEY_LEFT)
            assertTrue(field.dispatch(event))

            assertTrue(intercepted)
            assertEquals(3, field.caret)
        }
    }

    @Test
    fun `lazy column keeps scroll range but places only visible children`() {
        val root = BoxNode(layout = UiLayout.LazyColumn, modifiers = listOf(Modifier.size(100.px, 30.px), Modifier.input(scrollable = true)))
        repeat(10) { index ->
            root.children += BoxNode(id = "row-$index", modifiers = listOf(Modifier.size(100.px, 10.px)))
        }

        UiNodeKeys.assign(root)
        val resolved = UiStyleResolver().resolve(root, animate = false)
        val layout = UiLayoutPipeline().compute(resolved, width = 100f, height = 30f)
        val placedChildren = root.children.filter { it in layout.nodes }.map { it.id }

        assertTrue(layout[root].scrollRange.y > 0f)
        assertEquals(listOf("row-0", "row-1", "row-2"), placedChildren)
    }

    @Test
    fun `scroll target ignores virtualized lazy children without layout nodes`() {
        HollowUiSurface().use { runtime ->
            val frame = runtime.frame(
                content = {
                    LazyColumn(
                        id = "lazy",
                        modifier = Modifier.then(
                            Modifier.size(100.px, 30.px),
                            Modifier.input(scrollable = true),
                        ),
                    ) {
                        repeat(10) { index ->
                            Box(id = "row-$index", modifier = Modifier.size(100.px, 10.px))
                        }
                    }
                },
                width = 100f,
                height = 30f,
            )
            val target = frame.scrollTargetAt(8f, 8f)

            assertEquals("lazy", target?.id)
        }
    }

    @Test
    fun `lazy column places partially visible children after scroll offset`() {
        HollowUiSurface().use { runtime ->
            runtime.setContent {
                LazyColumn(
                    id = "lazy",
                    modifier = Modifier.then(
                        Modifier.size(100.px, 30.px),
                        Modifier.input(scrollable = true),
                    ),
                ) {
                    repeat(10) { index ->
                        Box(id = "row-$index", modifier = Modifier.size(100.px, 10.px))
                    }
                }
            }
            val initial = runtime.frame(100f, 30f)
            val lazy = initial.resolved.styles.keys.single { it.id == "lazy" }
            runtime.setScrollImmediate(lazy, y = 15f)
            val scrolled = runtime.frame(100f, 30f)
            val placedChildren = scrolled.resolved.styles.keys
                .filter { it.id?.startsWith("row-") == true && it in scrolled.layout.nodes }
                .mapNotNull { it.id }
                .sorted()

            assertEquals(listOf("row-1", "row-2", "row-3", "row-4"), placedChildren)
        }
    }

    @Test
    fun `scrollbar uses layout geometry and generic box commands`() {
        HollowUiSurface().use { runtime ->
            val frame = runtime.frame(
                content = {
                    Box(
                        id = "scroll",
                        modifier = Modifier.then(
                            Modifier.size(100.px, 30.px),
                            Modifier.input(scrollable = true),
                        ),
                    ) {
                        Box(id = "content", modifier = Modifier.size(80.px, 100.px))
                    }
                },
                width = 100f,
                height = 30f,
            )
            val scroll = frame.resolved.styles.keys.single { it.id == "scroll" }
            val scrollbar = frame.layout[scroll].scrollbars.single()
            val scrollbarCommands = frame.commands.filterIsInstance<DrawBoxCommand>().filter { it.node == scroll }
            val hit = frame.scrollbarAt(
                scrollbar.thumb.x + scrollbar.thumb.width * 0.5f,
                scrollbar.thumb.y + scrollbar.thumb.height * 0.5f,
            )

            assertEquals(ScrollbarOrientation.VERTICAL, scrollbar.orientation)
            assertEquals(2, scrollbarCommands.size)
            assertTrue(scrollbarCommands.all { it.phase == UiRenderPhase.OVERLAY })
            assertEquals(scrollbar.track.width, scrollbarCommands[0].rect.width)
            assertEquals(scrollbar.thumb.height, scrollbarCommands[1].rect.height)
            assertSame(scroll, hit?.node)
        }
    }

    @Test
    fun `lazy row keeps horizontal scroll range but places only visible children`() {
        val root = BoxNode(layout = UiLayout.LazyRow, modifiers = listOf(Modifier.size(30.px, 100.px), Modifier.input(scrollable = true)))
        repeat(10) { index ->
            root.children += BoxNode(id = "column-$index", modifiers = listOf(Modifier.size(10.px, 100.px)))
        }

        UiNodeKeys.assign(root)
        val resolved = UiStyleResolver().resolve(root, animate = false)
        val layout = UiLayoutPipeline().compute(resolved, width = 30f, height = 100f)
        val placedChildren = root.children.filter { it in layout.nodes }.map { it.id }

        assertTrue(layout[root].scrollRange.x > 0f)
        assertEquals(listOf("column-0", "column-1", "column-2"), placedChildren)
    }

    @Test
    fun `scroll target ignores virtualized lazy row children without layout nodes`() {
        HollowUiSurface().use { runtime ->
            val frame = runtime.frame(
                content = {
                    LazyRow(
                        id = "lazy",
                        modifier = Modifier.then(
                            Modifier.size(30.px, 100.px),
                            Modifier.input(scrollable = true),
                        ),
                    ) {
                        repeat(10) { index ->
                            Box(id = "column-$index", modifier = Modifier.size(10.px, 100.px))
                        }
                    }
                },
                width = 30f,
                height = 100f,
            )
            val target = frame.scrollTargetAt(8f, 8f)

            assertEquals("lazy", target?.id)
        }
    }

    @Test
    fun `lazy row places partially visible children after scroll offset`() {
        HollowUiSurface().use { runtime ->
            runtime.setContent {
                LazyRow(
                    id = "lazy",
                    modifier = Modifier.then(
                        Modifier.size(30.px, 100.px),
                        Modifier.input(scrollable = true),
                    ),
                ) {
                    repeat(10) { index ->
                        Box(id = "column-$index", modifier = Modifier.size(10.px, 100.px))
                    }
                }
            }
            val initial = runtime.frame(30f, 100f)
            val lazy = initial.resolved.styles.keys.single { it.id == "lazy" }
            runtime.setScrollImmediate(lazy, x = 15f)
            val scrolled = runtime.frame(30f, 100f)
            val placedChildren = scrolled.resolved.styles.keys
                .filter { it.id?.startsWith("column-") == true && it in scrolled.layout.nodes }
                .mapNotNull { it.id }
                .sorted()

            assertEquals(listOf("column-1", "column-2", "column-3", "column-4"), placedChildren)
        }
    }

    @Test
    fun `compose custom attributes do not erase widget attributes`() {
        val status = mutableStateOf("idle")

        HollowUiComposition().use { composition ->
            val root = composition.setContent {
                TextField(
                    value = "value",
                    id = "field",
                    attributes = mapOf("status" to status.value),
                )
            }

            status.value = "ready"
            composition.applyPendingChanges()

            val field = root.children.single() as TextFieldNode
            assertEquals("value", field.attributes["value"])
            assertEquals("ready", field.attributes["status"])
        }
    }

    @Test
    fun `compose recreates inlay widget when its offset changes`() {
        val text = mutableStateOf("val a = 1")
        val hints = mutableStateOf(listOf(UiInlayHint(5, ": Int")))

        HollowUiComposition().use { composition ->
            val root = composition.setContent {
                TextField(
                    value = text.value,
                    mode = UiTextFieldMode.MULTI_LINE,
                    inlayHints = hints.value,
                    id = "editor",
                )
            }
            val field = root.textField()
            val firstWidget = field.children.single()

            text.value = "\nval a = 1"
            hints.value = listOf(UiInlayHint(6, ": Int"))
            composition.applyPendingChanges()

            val movedWidget = field.children.single()
            assertEquals(textFieldInlayWidgetId(UiInlayHint(6, ": Int"), 0), movedWidget.id)
            assertTrue(firstWidget !== movedWidget)
        }
    }

    @Test
    fun `compose updates tags on reused widget nodes`() {
        val selected = mutableStateOf(false)

        HollowUiComposition().use { composition ->
            val root = composition.setContent {
                Row(id = "tab", tags = if (selected.value) listOf("dock-tab", "selected") else listOf("dock-tab")) {
                    Image(
                        "hollowengine:textures/gui/icons/code_editor.svg",
                        id = "icon",
                        tags = if (selected.value) listOf("icon", "selected") else listOf("icon"),
                    )
                }
            }

            selected.value = true
            composition.applyPendingChanges()

            val tab = root.children.single() as BoxNode
            val icon = tab.children.single() as ImageNode
            assertEquals(setOf("dock-tab", "selected"), tab.tags)
            assertEquals(setOf("icon", "selected"), icon.tags)

            selected.value = false
            composition.applyPendingChanges()

            assertEquals(setOf("dock-tab"), tab.tags)
            assertEquals(setOf("icon"), icon.tags)
        }
    }

    @Test
    fun `compose runtime produces layout frames from composed nodes`() {
        HollowUiSurface().use { runtime ->
            val frame = runtime.frame(
                content = {
                    Row(
                        modifier = Modifier.then(
                            Modifier.size(120.px, 40.px),
                            Modifier.gap(8.px),
                        ),
                    ) {
                        Box(id = "fixed", modifier = Modifier.size(20.px, 10.px))
                        Box(id = "grown", modifier = Modifier.then(Modifier.size(100.percent, 10.px), Modifier.grow(1f)))
                    }
                },
                width = 120f,
                height = 40f,
            )
            val fixed = frame.resolved.styles.keys.first { it.id == "fixed" }
            val grown = frame.resolved.styles.keys.first { it.id == "grown" }

            assertEquals(0f, frame.layout[fixed].rect.x)
            assertEquals(28f, frame.layout[grown].rect.x)
            assertEquals(92f, frame.layout[grown].rect.width)
        }
    }

    @Test
    fun `ui surface produces composed layout frames`() {
        HollowUiSurface().use { surface ->
            val frame = surface.frame(
                content = {
                    Row(modifier = Modifier.then(Modifier.size(80.px, 20.px), Modifier.gap(4.px))) {
                        Box(id = "left", modifier = Modifier.size(20.px, 10.px))
                        Box(id = "right", modifier = Modifier.size(20.px, 10.px))
                    }
                },
                width = 80f,
                height = 20f,
            )
            val left = frame.resolved.styles.keys.single { it.id == "left" }
            val right = frame.resolved.styles.keys.single { it.id == "right" }

            assertEquals(0f, frame.layout[left].rect.x)
            assertEquals(24f, frame.layout[right].rect.x)
        }
    }

    @Test
    fun `custom layout policy measures and places children explicitly`() {
        HollowUiSurface().use { runtime ->
            val frame = runtime.frame(
                content = {
                    Layout(
                        content = {
                            Box(id = "avatar", modifier = Modifier.size(40.px, 40.px))
                            Box(id = "text-box", modifier = Modifier.size(100.px, 30.px))
                        },
                    ) { measurables, constraints ->
                        val avatar = measurables[0].measure(constraints)
                        val overlap = 18f
                        val topPadding = 4f
                        val text = measurables[1].measure(
                            constraints.copy(maxWidth = constraints.maxWidth - avatar.width + overlap)
                        )
                        val textWithPadding = text.height + topPadding
                        val width = avatar.width + text.width - overlap
                        val height = maxOf(avatar.height, textWithPadding)
                        layout(width, height) {
                            avatar.place(0, 0)
                            text.place(avatar.width - overlap, topPadding)
                        }
                    }
                },
                width = 200f,
                height = 80f,
            )
            val avatar = frame.resolved.styles.keys.first { it.id == "avatar" }
            val text = frame.resolved.styles.keys.first { it.id == "text-box" }

            assertEquals(0f, frame.layout[avatar].rect.x)
            assertEquals(22f, frame.layout[text].rect.x)
            assertEquals(4f, frame.layout[text].rect.y)
        }
    }

    @Test
    fun `text places inline widget children from text layout`() {
        val content = UiTextContent(
            listOf(
                UiTextSegment.Text("Status ".bound()),
                UiTextSegment.inlineWidget("badge", align = UiInlineAlign.MIDDLE),
                UiTextSegment.Text(" ready".bound()),
            )
        )

        HollowUiSurface().use { runtime ->
            val frame = runtime.frame(
                content = {
                    Text(textContent = content, modifier = Modifier.fontSize(10f)) {
                        InlineWidget("badge", modifier = Modifier.size(24.px, 10.px))
                    }
                },
                width = 160f,
                height = 40f,
            )
            val text = frame.resolved.styles.keys.filterIsInstance<TextNode>().single()
            val badge = frame.resolved.styles.keys.single { it.id == "badge" }
            val command = frame.commands.filterIsInstance<DrawTextCommand>().single { it.node == text }
            val widgetRun = command.layout.lines
                .flatMap { line -> line.fragments.map { line to it } }
                .single { (_, fragment) -> fragment is UiInlineWidgetRun }
            val line = widgetRun.first
            val fragment = widgetRun.second as UiInlineWidgetRun

            assertEquals(24f, frame.layout[badge].rect.width)
            assertEquals(10f, frame.layout[badge].rect.height)
            assertEquals(frame.layout[text].content.x + line.x + fragment.x, frame.layout[badge].rect.x)
            assertEquals(frame.layout[text].content.y + line.y + fragment.y, frame.layout[badge].rect.y)
        }
    }

    @Test
    fun `text render command reuses layout engine text layout`() {
        HollowUiSurface().use { runtime ->
            val frame = runtime.frame(
                content = {
                    Text(
                        "One two three four five six",
                        modifier = Modifier.then(
                            Modifier.size(74.px, 80.px),
                            Modifier.fontSize(10f),
                        ),
                    )
                },
                width = 90f,
                height = 90f,
            )
            val text = frame.resolved.styles.keys.filterIsInstance<TextNode>().single()
            val command = frame.commands.filterIsInstance<DrawTextCommand>().single { it.node == text }

            assertSame(frame.layout[text].textLayout, command.layout)
            assertTrue(command.layout.lines.size > 1)
        }
    }

    @Test
    fun `text inline widget follows justified line offsets`() {
        val content = UiTextContent(
            listOf(
                UiTextSegment.Text("left ".bound()),
                UiTextSegment.inlineWidget("badge", align = UiInlineAlign.MIDDLE),
                UiTextSegment.Text(" right side keeps going enough to wrap".bound()),
            )
        )

        HollowUiSurface().use { runtime ->
            val frame = runtime.frame(
                content = {
                    Text(
                        textContent = content,
                        modifier = Modifier.then(
                            Modifier.size(180.px, 60.px),
                            Modifier.fontSize(10f),
                            Modifier.textAlign(UiTextAlign.JUSTIFY),
                        ),
                    ) {
                        InlineWidget("badge", modifier = Modifier.size(24.px, 10.px))
                    }
                },
                width = 200f,
                height = 80f,
            )
            val text = frame.resolved.styles.keys.filterIsInstance<TextNode>().single()
            val badge = frame.resolved.styles.keys.single { it.id == "badge" }
            val command = frame.commands.filterIsInstance<DrawTextCommand>().single { it.node == text }
            val line = command.layout.lines.single { line -> line.fragments.any { it is UiInlineWidgetRun } }
            val fragment = line.fragments.filterIsInstance<UiInlineWidgetRun>().single()

            assertTrue(line.justify)
            assertEquals(frame.layout[text].content.x + line.x + fragment.x, frame.layout[badge].rect.x)
        }
    }

    @Test
    fun `text fixed width is constrained by parent before wrapping`() {
        val content = UiTextContent(
            listOf(
                UiTextSegment.Text("Inline text can host ".bound()),
                UiTextSegment.inlineWidget("badge", align = UiInlineAlign.MIDDLE),
                UiTextSegment.Text(" measured widgets and keep wrapping consistent.".bound()),
            )
        )

        HollowUiSurface().use { runtime ->
            val frame = runtime.frame(
                content = {
                    Column(
                        id = "constrained-parent",
                        modifier = Modifier.then(
                            Modifier.size(330.px, 120.px),
                            Modifier.padding(12.px),
                        ),
                    ) {
                        Text(
                            textContent = content,
                            id = "constrained-text",
                            modifier = Modifier.then(
                                Modifier.size(320.px, 80.px),
                                Modifier.fontSize(10f),
                                Modifier.textAlign(UiTextAlign.JUSTIFY),
                            ),
                        ) {
                            InlineWidget("badge", modifier = Modifier.size(58.px, 22.px)) {
                                Text("AUTO", modifier = Modifier.fontSize(9f))
                            }
                        }
                    }
                },
                width = 360f,
                height = 140f,
            )
            val parent = frame.resolved.styles.keys.single { it.id == "constrained-parent" }
            val text = frame.resolved.styles.keys.filterIsInstance<TextNode>().single { it.id == "constrained-text" }
            val command = frame.commands.filterIsInstance<DrawTextCommand>().single { it.node == text }

            assertEquals(306f, frame.layout[parent].content.width)
            assertEquals(306f, command.rect.width)
            command.layout.lines.forEach { line ->
                val right = line.fragments.maxOfOrNull { it.x + it.width } ?: 0f
                assertTrue(line.x + right <= command.rect.width + 0.01f)
            }
        }
    }

    @Test
    fun `text line after tall inline widget starts below widget bottom`() {
        val content = UiTextContent(
            listOf(
                UiTextSegment.Text("A ".bound()),
                UiTextSegment.inlineWidget("badge", align = UiInlineAlign.MIDDLE),
                UiTextSegment.Text(" B C D E F G H".bound()),
            )
        )

        HollowUiSurface().use { runtime ->
            val frame = runtime.frame(
                content = {
                    Text(
                        textContent = content,
                        modifier = Modifier.then(Modifier.size(64.px, 90.px), Modifier.fontSize(10f)),
                    ) {
                        InlineWidget("badge", modifier = Modifier.size(20.px, 28.px))
                    }
                },
                width = 80f,
                height = 100f,
            )
            val text = frame.resolved.styles.keys.filterIsInstance<TextNode>().single()
            val command = frame.commands.filterIsInstance<DrawTextCommand>().single { it.node == text }
            val widgetLine = command.layout.lines.single { line -> line.fragments.any { it is UiInlineWidgetRun } }
            val widget = widgetLine.fragments.filterIsInstance<UiInlineWidgetRun>().single()
            val nextLine = command.layout.lines.first { it.y > widgetLine.y }

            assertTrue(nextLine.y >= widgetLine.y + widget.y + widget.height)
        }
    }

    @Test
    fun `text line after inline widget includes configured spacing from widget bottom`() {
        val content = UiTextContent(
            listOf(
                UiTextSegment.Text("A ".bound()),
                UiTextSegment.inlineWidget("badge", align = UiInlineAlign.MIDDLE),
                UiTextSegment.Text(" B C D E F G H".bound()),
            )
        )

        HollowUiSurface().use { runtime ->
            val frame = runtime.frame(
                content = {
                    Text(
                        textContent = content,
                        modifier = Modifier.then(
                            Modifier.size(64.px, 100.px),
                            Modifier.fontSize(10f),
                            Modifier.lineSpacing(5f),
                        ),
                    ) {
                        InlineWidget("badge", modifier = Modifier.size(20.px, 28.px))
                    }
                },
                width = 80f,
                height = 110f,
            )
            val text = frame.resolved.styles.keys.filterIsInstance<TextNode>().single()
            val command = frame.commands.filterIsInstance<DrawTextCommand>().single { it.node == text }
            val widgetLine = command.layout.lines.single { line -> line.fragments.any { it is UiInlineWidgetRun } }
            val widget = widgetLine.fragments.filterIsInstance<UiInlineWidgetRun>().single()
            val nextLine = command.layout.lines.first { it.y > widgetLine.y }

            assertEquals(widgetLine.y + widget.y + widget.height + 5f, nextLine.y)
        }
    }

    @Test
    fun `text inline widget is positioned from the unified text layout`() {
        val content = UiTextContent(
            listOf(
                UiTextSegment.inlineWidget("aside", align = UiInlineAlign.TOP),
                UiTextSegment.Text("Inline text wraps after the measured widget using the same line width as every other run.".bound()),
            )
        )

        HollowUiSurface().use { runtime ->
            val frame = runtime.frame(
                content = {
                    Text(
                        textContent = content,
                        modifier = Modifier.then(Modifier.size(140.px, 90.px), Modifier.fontSize(10f)),
                    ) {
                        InlineWidget("aside", modifier = Modifier.size(42.px, 28.px))
                    }
                },
                width = 160f,
                height = 110f,
            )
            val text = frame.resolved.styles.keys.filterIsInstance<TextNode>().single()
            val aside = frame.resolved.styles.keys.single { it.id == "aside" }
            val command = frame.commands.filterIsInstance<DrawTextCommand>().single { it.node == text }
            val widgetRun = command.layout.lines
                .flatMap { line -> line.fragments.map { line to it } }
                .single { (_, fragment) -> fragment is UiInlineWidgetRun }
            val firstTextRun = command.layout.lines.first().fragments.filterIsInstance<UiTextRun>().first()

            assertEquals(42f, frame.layout[aside].rect.width)
            assertEquals(28f, frame.layout[aside].rect.height)
            assertEquals(frame.layout[text].content.x + widgetRun.first.x + widgetRun.second.x, frame.layout[aside].rect.x)
            assertEquals(frame.layout[text].content.y + widgetRun.first.y + widgetRun.second.y, frame.layout[aside].rect.y)
            assertEquals(0f, command.layout.lines.first().x)
            assertTrue(firstTextRun.x >= 42f)
        }
    }

    @Test
    fun `text inline widget does not offset following wrapped lines`() {
        val content = UiTextContent(
            listOf(
                UiTextSegment.inlineWidget("aside", align = UiInlineAlign.TOP),
                UiTextSegment.Text(
                    "One two three four five six seven eight nine ten eleven twelve thirteen fourteen fifteen.".bound(),
                ),
            )
        )

        HollowUiSurface().use { runtime ->
            val frame = runtime.frame(
                content = {
                    Text(
                        textContent = content,
                        modifier = Modifier.then(Modifier.size(126.px, 110.px), Modifier.fontSize(10f)),
                    ) {
                        InlineWidget("aside", modifier = Modifier.size(42.px, 36.px))
                    }
                },
                width = 140f,
                height = 120f,
            )
            val text = frame.resolved.styles.keys.filterIsInstance<TextNode>().single()
            val command = frame.commands.filterIsInstance<DrawTextCommand>().single { it.node == text }
            val widgetLine = command.layout.lines.single { line -> line.fragments.any { it is UiInlineWidgetRun } }
            val nextLine = command.layout.lines.first { it.y > widgetLine.y }

            assertTrue(command.layout.lines.all { it.x == 0f })
            assertTrue(nextLine.y >= widgetLine.y + 36f)
        }
    }

    @Test
    fun `text line spacing increases distance between lines`() {
        HollowUiSurface().use { runtime ->
            val frame = runtime.frame(
                content = {
                    Text(
                        "one two three four",
                        modifier = Modifier.then(
                            Modifier.size(36.px, 80.px),
                            Modifier.fontSize(10f),
                            Modifier.lineSpacing(6f),
                        ),
                    )
                },
                width = 50f,
                height = 90f,
            )
            val command = frame.commands.filterIsInstance<DrawTextCommand>().single()
            val first = command.layout.lines[0]
            val second = command.layout.lines[1]

            assertEquals(first.height + 6f, second.y - first.y)
        }
    }

    @Test
    fun `text space width can be configured`() {
        HollowUiSurface().use { runtime ->
            val frame = runtime.frame(
                content = {
                    Text(
                        "A B",
                        modifier = Modifier.then(
                            Modifier.fontSize(10f),
                            Modifier.spaceWidth(18f),
                        ),
                    )
                },
                width = 100f,
                height = 40f,
            )
            val command = frame.commands.filterIsInstance<DrawTextCommand>().single()
            val space = command.layout.lines.single().fragments.filterIsInstance<UiTextSpaceRun>().single()

            assertEquals(18f, space.width)
        }
    }

    @Test
    fun `xml text uses hss line spacing and space width`() {
        val stylesheet = compileHss(
            """
            .spaced-text {
                line-spacing: 5px;
                space-width: 16px;
            }
            """.trimIndent(),
        )
        val xml = parseUiXml(
            """
            <text tags="spaced-text" font-size="10" width="42px" height="80px">
                A B C D
            </text>
            """.trimIndent(),
        )

        HollowUiSurface(stylesheet = stylesheet).use { runtime ->
            val frame = runtime.frame(
                content = { UiXmlContent(xml) },
                width = 70f,
                height = 90f,
            )
            val command = frame.commands.filterIsInstance<DrawTextCommand>().single()
            val first = command.layout.lines[0]
            val second = command.layout.lines[1]
            val space = command.layout.lines.flatMap { it.fragments }.filterIsInstance<UiTextSpaceRun>().first()

            assertEquals(first.height + 5f, second.y - first.y)
            assertEquals(16f, space.width)
        }
    }

    @Test
    fun `xml text places nested widget children from measured layout`() {
        val xml = parseUiXml(
            """
            <text font-size="10">
                Message with <box id="badge" width="24px" height="10px" /> widget
            </text>
            """.trimIndent(),
        )

        HollowUiSurface().use { runtime ->
            val frame = runtime.frame(
                content = { UiXmlContent(xml) },
                width = 180f,
                height = 40f,
            )
            val text = frame.resolved.styles.keys.filterIsInstance<TextNode>().single()
            val badge = frame.resolved.styles.keys.single { it.id == "badge" }
            val command = frame.commands.filterIsInstance<DrawTextCommand>().single { it.node == text }
            val widgetRun = command.layout.lines
                .flatMap { line -> line.fragments.map { line to it } }
                .single { (_, fragment) -> fragment is UiInlineWidgetRun }

            assertEquals(24f, frame.layout[badge].rect.width)
            assertEquals(10f, frame.layout[badge].rect.height)
            assertEquals(frame.layout[text].content.x + widgetRun.first.x + widgetRun.second.x, frame.layout[badge].rect.x)
        }
    }

    @Test
    fun `xml text inline widget uses hss sized child layout run`() {
        val stylesheet = compileHss(
            """
            .slot-aside {
                size: 36px 24px;
            }
            """.trimIndent(),
        )
        val xml = parseUiXml(
            """
            <text font-size="10" width="130px" height="80px">
                <box id="aside" tags="slot-aside" />
                Text wraps after the HSS-sized child as a normal inline run.
            </text>
            """.trimIndent(),
        )

        HollowUiSurface(stylesheet = stylesheet).use { runtime ->
            val frame = runtime.frame(
                content = { UiXmlContent(xml) },
                width = 160f,
                height = 100f,
            )
            val text = frame.resolved.styles.keys.filterIsInstance<TextNode>().single()
            val aside = frame.resolved.styles.keys.single { it.id == "aside" }
            val command = frame.commands.filterIsInstance<DrawTextCommand>().single { it.node == text }
            val widgetRun = command.layout.lines
                .flatMap { line -> line.fragments.map { line to it } }
                .single { (_, fragment) -> fragment is UiInlineWidgetRun }

            assertEquals(36f, frame.layout[aside].rect.width)
            assertEquals(24f, frame.layout[aside].rect.height)
            assertEquals(frame.layout[text].content.x + widgetRun.first.x + widgetRun.second.x, frame.layout[aside].rect.x)
            assertEquals(0f, command.layout.lines.first().x)
        }
    }

    @Test
    fun `popup is positioned relative to anchor node alignment`() {
        HollowUiSurface().use { runtime ->
            val frame = runtime.frame(
                content = {
                    Box(modifier = Modifier.size(200.px, 120.px)) {
                        Box(id = "anchor", modifier = Modifier.then(Modifier.position(50.px, 10.px), Modifier.size(40.px, 20.px)))
                        Popup(
                            id = "popup",
                            anchor = UiPopupAnchor.Node("anchor"),
                            alignment = UiPopupAlignment(offsetY = 4f),
                            modifier = Modifier.size(60.px, 18.px),
                        )
                    }
                },
                width = 200f,
                height = 120f,
            )
            val popup = frame.resolved.styles.keys.single { it.id == "popup" }

            assertEquals(50f, frame.layout[popup].rect.x)
            assertEquals(34f, frame.layout[popup].rect.y)
        }
    }

    @Test
    fun `cursor popup follows pointer bindings`() {
        HollowUiSurface().use { runtime ->
            runtime.setContent {
                Box(modifier = Modifier.size(200.px, 120.px)) {
                    Popup(
                        id = "popup",
                        anchor = UiPopupAnchor.Cursor(),
                        alignment = UiPopupAlignment.Cursor,
                        modifier = Modifier.size(30.px, 10.px),
                    )
                }
            }
            val first = runtime.frame(200f, 120f, bindings = UiBindingContext().withPointer(10f, 20f))
            val firstPopup = first.resolved.styles.keys.single { it.id == "popup" }
            val second = runtime.frame(200f, 120f, bindings = UiBindingContext().withPointer(50f, 60f))
            val secondPopup = second.resolved.styles.keys.single { it.id == "popup" }

            assertEquals(18f, first.layout[firstPopup].rect.x)
            assertEquals(28f, first.layout[firstPopup].rect.y)
            assertEquals(58f, second.layout[secondPopup].rect.x)
            assertEquals(68f, second.layout[secondPopup].rect.y)
        }
    }

    @Test
    fun `compose runtime keeps scroll state across recomposition`() {
        val label = mutableStateOf("before")

        HollowUiSurface().use { runtime ->
            runtime.setContent {
                Column {
                    Text(label.value)
                    Box(
                        id = "scroll",
                        modifier = Modifier.then(Modifier.size(80.px, 30.px), Modifier.input(scrollable = true)),
                    ) {
                        Box(id = "row", modifier = Modifier.then(Modifier.position(0.px, 90.px), Modifier.size(50.px, 20.px)))
                    }
                }
            }
            val initial = runtime.frame(120f, 80f)
            val scroller = initial.resolved.styles.keys.first { it.id == "scroll" }
            val initialRow = initial.resolved.styles.keys.first { it.id == "row" }
            runtime.setScrollImmediate(scroller, y = 24f)

            label.value = "after"
            val scrolled = runtime.frame(120f, 80f)
            val row = scrolled.resolved.styles.keys.first { it.id == "row" }

            assertEquals(initial.layout[initialRow].rect.y - 24f, scrolled.layout[row].rect.y)
        }
    }

    @Test
    fun `render commands skip children outside scroll clip`() {
        HollowUiSurface().use { runtime ->
            runtime.setContent {
                Box(
                    id = "scroll",
                    modifier = Modifier.then(Modifier.size(80.px, 30.px), Modifier.input(scrollable = true)),
                ) {
                    Text(
                        "visible",
                        id = "visible",
                        modifier = Modifier.then(Modifier.position(0.px, 0.px), Modifier.size(80.px, 12.px)),
                    )
                    Text(
                        "hidden",
                        id = "hidden",
                        modifier = Modifier.then(Modifier.position(0.px, 60.px), Modifier.size(80.px, 12.px)),
                    )
                }
            }

            val frame = runtime.frame(120f, 80f)
            val textCommands = frame.commands.filterIsInstance<DrawTextCommand>()

            assertTrue(textCommands.any { it.node.id == "visible" })
            assertTrue(textCommands.none { it.node.id == "hidden" })
        }
    }

    @Test
    fun `compose xml content produces layout frames`() {
        val xml = parseUiXml(
            """
            <row width="120px" height="40px" gap="8px">
                <box id="fixed" width="20px" height="10px" />
                <box id="grown" width="100%" height="10px" grow="1" />
            </row>
            """.trimIndent(),
        )

        HollowUiSurface().use { runtime ->
            val frame = runtime.frame(
                content = { UiXmlContent(xml) },
                width = 120f,
                height = 40f,
            )
            val fixed = frame.resolved.styles.keys.first { it.id == "fixed" }
            val grown = frame.resolved.styles.keys.first { it.id == "grown" }

            assertEquals(0f, frame.layout[fixed].rect.x)
            assertEquals(28f, frame.layout[grown].rect.x)
            assertEquals(92f, frame.layout[grown].rect.width)
        }
    }

    @Test
    fun `compose xml content preserves text field state across server sibling update`() {
        fun tree(title: String, value: String) = UiXmlTree(
            "column",
            children = listOf(
                UiXmlTree("text", mapOf("id" to "title", "text" to title)),
                UiXmlTree("text-field", mapOf("id" to "field", "value" to value)),
            ),
        )

        val serverTree = mutableStateOf(tree("Before", "server"))

        HollowUiSurface().use { runtime ->
            val root = runtime.setContent {
                UiXmlContent(serverTree.value)
            }
            val field = root.textField()
            field.insert("!")

            serverTree.value = tree("After", "server")
            runtime.frame(120f, 80f)

            assertSame(field, root.textField())
            assertEquals("server!", root.textField().value)

            serverTree.value = tree("After", "remote")
            runtime.frame(120f, 80f)

            assertSame(field, root.textField())
            assertEquals("remote", root.textField().value)
        }
    }

    @Test
    fun `compose closing without closing motion ignores active hover transition`() {
        val stylesheet = compileHss(
            """
            .dialog {
                scale: 1;
                transition: scale 1000ms linear;
            }

            .dialog:hover {
                scale: 1.2;
            }
            """.trimIndent(),
        )

        HollowUiSurface(stylesheet = stylesheet).use { runtime ->
            runtime.setContent {
                Box(id = "dialog", tags = listOf("dialog"))
            }
            runtime.frame(100f, 40f, nowMillis = 0L)
            val dialog = runtime.root.child("dialog")
            dialog.states += UiState.HOVER
            runtime.frame(100f, 40f, nowMillis = 0L)

            dialog.states -= UiState.HOVER
            val closeBase = runtime.frame(100f, 40f, nowMillis = 0L)
            runtime.root.setClosingState(true)
            val closing = runtime.frame(100f, 40f, nowMillis = 0L)

            assertEquals(0L, closing.motionDurationMillis(closeBase))
        }
    }

    @Test
    fun `compose closing transition contributes close motion duration`() {
        val stylesheet = compileHss(
            """
            .dialog {
                opacity: 1;
                transition: opacity 250ms linear;
            }

            .dialog:closing {
                opacity: 0;
            }
            """.trimIndent(),
        )

        HollowUiSurface(stylesheet = stylesheet).use { runtime ->
            runtime.setContent {
                Box(id = "dialog", tags = listOf("dialog"))
            }
            val opened = runtime.frame(100f, 40f, nowMillis = 0L)

            runtime.root.setClosingState(true)
            val closing = runtime.frame(100f, 40f, nowMillis = 0L)

            assertEquals(250L, closing.motionDurationMillis(opened))
        }
    }

    @Test
    fun `standard style modifiers are structurally equal`() {
        assertEquals(
            Modifier.then(
                Modifier.size(120.px, 40.px),
                Modifier.gap(4.px),
                Modifier.fontSize(9f),
                Modifier.translate(1f, 2f),
            ),
            Modifier.then(
                Modifier.size(120.px, 40.px),
                Modifier.gap(4.px),
                Modifier.fontSize(9f),
                Modifier.translate(1f, 2f),
            ),
        )
    }

    @Test
    fun `style resolver reuses unchanged node style`() {
        var applyCount = 0
        val root = BoxNode(
            modifiers = listOf(
                StyleModifier {
                    applyCount += 1
                    it.opacity = 0.75f
                },
            ),
        )
        val resolver = UiStyleResolver()

        UiNodeKeys.assign(root)
        val first = resolver.resolve(root, animate = false)
        val second = resolver.resolve(root, animate = false)

        assertSame(first, second)
        assertEquals(0.75f, first[root].opacity)
        assertEquals(0.75f, second[root].opacity)
        assertEquals(1, applyCount)
    }

    @Test
    fun `style resolver invalidates cached style when node state changes`() {
        val stylesheet = compileHss(
            """
            .panel {
                opacity: 1;
            }

            .panel:hover {
                opacity: 0.5;
            }
            """.trimIndent(),
        )
        val root = BoxNode(tags = listOf("panel"))
        val resolver = UiStyleResolver(stylesheet = stylesheet)

        UiNodeKeys.assign(root)
        val initial = resolver.resolve(root, animate = false)
        assertEquals(1f, initial[root].opacity)

        root.states += UiState.HOVER
        val hovered = resolver.resolve(root, animate = false)
        assertTrue(initial !== hovered)
        assertEquals(0.5f, hovered[root].opacity)
    }

    @Test
    fun `compose resource stylesheet reloads only when loader revision changes`() {
        val loader = VersionedHssLoader(
            """
            .panel {
                opacity: 1;
            }
            """.trimIndent(),
        )

        HollowUiSurface().use { runtime ->
            runtime.setContent {
                Box(id = "panel", tags = listOf("panel"), modifier = Modifier.style("test:panel.hss", loader))
            }

            val first = runtime.frame(100f, 40f, nowMillis = 0L)
            val second = runtime.frame(100f, 40f, nowMillis = 1L)
            assertEquals(1f, first.resolved[first.resolved.node("panel")].opacity)
            assertEquals(1f, second.resolved[second.resolved.node("panel")].opacity)
            assertEquals(1, loader.loadCount)

            loader.update(
                """
                .panel {
                    opacity: 0.5;
                }
                """.trimIndent(),
            )
            val updated = runtime.frame(100f, 40f, nowMillis = 2L)

            assertEquals(0.5f, updated.resolved[updated.resolved.node("panel")].opacity)
            assertEquals(2, loader.loadCount)
        }
    }

    private fun BoxNode.textField(): TextFieldNode {
        return children
            .flatMap { child -> if (child is BoxNode) child.children else listOf(child) }
            .filterIsInstance<TextFieldNode>()
            .single()
    }

    private fun BoxNode.child(id: String): BoxNode {
        return children.filterIsInstance<BoxNode>().single { it.id == id }
    }

    private fun ResolvedUiTree.node(id: String): BoxNode {
        return styles.keys.filterIsInstance<BoxNode>().single { it.id == id }
    }

    private class VersionedHssLoader(
        private var source: String,
    ) : HssResourceLoader {
        var loadCount = 0
            private set
        private var revision = 0L

        override fun load(location: String): CompiledHss {
            loadCount += 1
            return compileHss(source)
        }

        override fun version(location: String): Long = revision

        fun update(source: String) {
            this.source = source
            revision += 1L
        }
    }
}

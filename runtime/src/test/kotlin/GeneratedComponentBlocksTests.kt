import de.fabmax.kool.modules.ui2.mutableStateListOf
import de.fabmax.kool.util.Color
import kotlinx.coroutines.test.runTest
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import net.minecraft.resources.ResourceLocation
import net.minecraft.world.phys.Vec3
import org.junit.jupiter.api.AfterEach
import org.junit.jupiter.api.Test
import ru.hollowhorizon.hollowengine.client.gui.codeblocks.InputSlotScope
import ru.hollowhorizon.hollowengine.common.codeblocks.*
import ru.hollowhorizon.hollowengine.common.codeblocks.blocks.BoolBlock
import ru.hollowhorizon.hollowengine.common.codeblocks.blocks.NumberBlock
import ru.hollowhorizon.hollowengine.common.codeblocks.blocks.StringValueBlock
import ru.hollowhorizon.hollowengine.common.codeblocks.blocks.components.generated.*
import ru.hollowhorizon.hollowengine.common.codeblocks.blocks.types.PositionBlock
import ru.hollowhorizon.hollowengine.common.codeblocks.model.BlockModel
import ru.hollowhorizon.hollowengine.common.codeblocks.model.ExpressionBlock
import ru.hollowhorizon.hollowengine.common.codeblocks.modules.GeneratedComponentBlocksModule
import ru.hollowhorizon.hollowengine.common.codeblocks.serialization.CodeBlockFormat
import ru.hollowhorizon.hollowengine.common.geary.components.*
import ru.hollowhorizon.hollowengine.common.geary.components.ai.EntityReference
import ru.hollowhorizon.hollowengine.common.utils.nbt.ForResourceLocation
import ru.hollowhorizon.hollowengine.common.utils.nbt.ForUuid
import ru.hollowhorizon.hollowengine.common.utils.nbt.ForVec3
import ru.hollowhorizon.hollowengine.common.utils.rl
import java.io.ByteArrayInputStream
import java.util.*
import kotlin.test.*

class GeneratedComponentBlocksTests {
    private val componentId = "test:generated_component".rl

    @AfterEach
    fun cleanup() {
        ComponentDescriptorRegistry.unregisterDescriptor(componentId)
    }

    @Test
    fun `schema exposes visible field metadata and omits hidden fields`() {
        registerDescriptor()

        val schema = ComponentSchemaRegistry.descriptorSchema(componentId)

        assertNotNull(schema)
        assertEquals("Generated Component", schema.displayName)
        assertEquals(
            listOf("playerName", "level", "enabled", "mode", "uuid", "itemId", "position", "target", "nested", "tags"),
            schema.fields.map { it.name }
        )

        val levelField = schema.fields.first { it.name == "level" }
        val nestedField = schema.fields.first { it.name == "nested" }
        val tagsField = schema.fields.first { it.name == "tags" }

        assertEquals(FieldValueKind.NUMBER, levelField.valueKind)
        assertEquals(0f, levelField.range?.min)
        assertEquals(10f, levelField.range?.max)
        assertEquals(FieldValueKind.CLASS, nestedField.valueKind)
        assertNotNull(nestedField.nestedSchemaKey)
        assertEquals(FieldValueKind.LIST, tagsField.valueKind)
        assertEquals(FieldValueKind.TEXT, tagsField.listElementKind)
        assertNull(schema.fields.firstOrNull { it.name == "hiddenValue" })
    }

    @Test
    fun `generated repository exposes component block families`() {
        registerDescriptor()
        val repository = createRepository()
        val scope = TestBlocksScope()

        val entries = repository.rootCategory.allEntries(scope)
        val createEntry = entries.firstOrNull {
            val block = it.createItem()
            block is CreateComponentBlock && block.schemaKey == componentId.toString()
        }
        val setEntry = entries.firstOrNull {
            val block = it.createItem()
            block is SetComponentBlock && block.descriptorId == componentId.toString()
        }
        val removeEntry = entries.firstOrNull {
            val block = it.createItem()
            block is RemoveComponentBlock && block.descriptorId == componentId.toString()
        }
        val hasEntry = entries.firstOrNull {
            val block = it.createItem()
            block is HasComponentBlock && block.descriptorId == componentId.toString()
        }
        val getterEntry = entries.firstOrNull {
            val block = it.createItem()
            block is GetComponentFieldBlock &&
                    block.descriptorId == componentId.toString() &&
                    block.fieldName == "playerName"
        }

        assertNotNull(createEntry)
        assertNotNull(setEntry)
        assertNotNull(removeEntry)
        assertNotNull(hasEntry)
        assertNotNull(getterEntry)

        val createBlock = createEntry.createItem() as CreateComponentBlock
        assertEquals("Создать Generated Component", repository.resolveDisplayName(createBlock, scope))
        assertIs<StringValueBlock>(createBlock.inputs["playerName"])
        assertIs<NumberBlock>(createBlock.inputs["level"])
        assertIs<UuidLiteralBlock>(createBlock.inputs["uuid"])
        assertIs<ResourceLocationLiteralBlock>(createBlock.inputs["itemId"])
        assertIs<PositionBlock>(createBlock.inputs["position"])
        assertIs<EntityReferenceLiteralBlock>(createBlock.inputs["target"])
        assertIs<CreateComponentBlock>(createBlock.inputs["nested"])
        assertIs<ListBuilderBlock>(createBlock.inputs["tags"])
    }

    @Test
    fun `create component block builds typed component from nested expressions`() = runTest {
        registerDescriptor()
        val schema = requireNotNull(ComponentSchemaRegistry.descriptorSchema(componentId))
        val nestedSchemaKey = requireNotNull(schema.fields.first { it.name == "nested" }.nestedSchemaKey)

        val block = CreateComponentBlock(schema.key).apply {
            applyDefaults()
            attachInput("playerName", StringValueBlock("Alex"))
            attachInput("level", NumberBlock(7.0))
            attachInput("mode", EnumLiteralBlock(schema.key, "mode", "AGGRESSIVE"))
            attachInput("uuid", UuidLiteralBlock("123e4567-e89b-12d3-a456-426614174000"))
            attachInput("itemId", ResourceLocationLiteralBlock("minecraft:diamond"))
            attachInput(
                "position",
                ConstantExpressionBlock(
                    Vec3(1.25, 64.0, -3.5),
                    ru.hollowhorizon.hollowengine.common.codeblocks.typeOf<Vec3>()
                )
            )
            attachInput(
                "target",
                EntityReferenceLiteralBlock(
                    uuidValue = "123e4567-e89b-12d3-a456-426614174001",
                    dimensionValue = "minecraft:the_nether",
                )
            )
            attachInput(
                "nested",
                CreateComponentBlock(nestedSchemaKey).apply {
                    applyDefaults()
                    attachInput("description", StringValueBlock("quest"))
                    attachInput("amount", NumberBlock(3.0))
                }
            )
            attachInput(
                "tags",
                ListBuilderBlock(schema.key, "tags").apply {
                    attachInput("item_0", StringValueBlock("alpha"))
                    attachInput("item_1", StringValueBlock("beta"))
                }
            )
        }

        val result = block.execute() as GeneratedComponent

        assertEquals("Alex", result.playerName)
        assertEquals(7, result.level)
        assertEquals(TestMode.AGGRESSIVE, result.mode)
        assertEquals(UUID.fromString("123e4567-e89b-12d3-a456-426614174000"), result.uuid)
        assertEquals("minecraft:diamond".rl, result.itemId)
        assertEquals(Vec3(1.25, 64.0, -3.5), result.position)
        assertEquals(UUID.fromString("123e4567-e89b-12d3-a456-426614174001"), result.target.uuid)
        assertEquals("minecraft:the_nether".rl, result.target.level)
        assertEquals(GeneratedNested("quest", 3), result.nested)
        assertEquals(listOf("alpha", "beta"), result.tags)
        assertTrue(result.enabled)
        assertEquals("hidden-default", result.hiddenValue)
    }

    @Test
    fun `generated blocks roundtrip through code block format`() {
        registerDescriptor()
        val schema = requireNotNull(ComponentSchemaRegistry.descriptorSchema(componentId))
        val repository = createRepository()
        val format = CodeBlockFormat(repository)

        val block = CreateComponentBlock(schema.key).apply {
            applyDefaults()
        }

        val encoded = format.encodeBlocks(listOf(block))
        val decoded = format.loadBlocks(ByteArrayInputStream(encoded.toByteArray())).single()

        val decodedCreate = decoded as CreateComponentBlock
        assertEquals(schema.key, decodedCreate.schemaKey)
        assertIs<StringValueBlock>(decodedCreate.inputs["playerName"])
        assertIs<NumberBlock>(decodedCreate.inputs["level"])
        assertIs<UuidLiteralBlock>(decodedCreate.inputs["uuid"])
        assertIs<ResourceLocationLiteralBlock>(decodedCreate.inputs["itemId"])
        assertIs<EntityReferenceLiteralBlock>(decodedCreate.inputs["target"])
        assertIs<CreateComponentBlock>(decodedCreate.inputs["nested"])
        assertIs<ListBuilderBlock>(decodedCreate.inputs["tags"])
    }

    @Test
    fun `getter and has blocks expose correct expression types`() {
        registerDescriptor()

        val hasBlock = HasComponentBlock(componentId.toString())
        val getterBlock = GetComponentFieldBlock(componentId.toString(), "playerName")
        val uuidGetter = GetComponentFieldBlock(componentId.toString(), "uuid")

        assertEquals("kotlin.Boolean", hasBlock.expressionType.toString())
        assertEquals("kotlin.String", getterBlock.expressionType.toString())
        assertEquals("java.util.UUID", uuidGetter.expressionType.toString())
    }

    private fun registerDescriptor() {
        if (ComponentDescriptorRegistry.descriptorOrNull(componentId) != null) return
        ComponentDescriptorRegistry.register(
            ComponentDescriptor(
                id = componentId,
                value = GeneratedComponent::class,
                serializer = GeneratedComponent.serializer(),
            )
        )
    }

    private fun createRepository() = BlockRepository.create("Test") {
        include(MinimalGeneratedComponentTypesModule)
        include(GeneratedComponentBlocksModule)
    }

    private class TestBlocksScope : BlocksScope {
        override val rootBlocks = mutableStateListOf<BlockModel>()
    }
}

private object MinimalGeneratedComponentTypesModule : BlockModule {
    override fun ru.hollowhorizon.hollowengine.common.codeblocks.BlockCategoryBuilder.build() {
        category("Types", null) {
            block("String") { StringValueBlock("") }
            block("Number") { NumberBlock() }
            block("Boolean") { BoolBlock() }
            block("Position") { PositionBlock() }
        }
    }
}

private class ConstantExpressionBlock<T : Any>(
    private val stored: T,
    override val expressionType: ru.hollowhorizon.hollowengine.common.codeblocks.ExpressionType,
) : ExpressionBlock() {
    override val color: Color get() = Color.WHITE
    override suspend fun execute(): Any = stored
    override fun InputSlotScope.composeContent() = Unit
}

private fun BlockCategory.allEntries(scope: BlocksScope): List<BlockEntry<*>> = buildList {
    addAll(entries(scope))
    subCategories.forEach { addAll(it.allEntries(scope)) }
}

@Serializable
@SerialName("test:generated_component")
private data class GeneratedComponent(
    @EditorName("Player Name")
    val playerName: String = "",
    @EditorRange(0f, 10f)
    val level: Int = 0,
    val enabled: Boolean = true,
    val mode: TestMode = TestMode.PASSIVE,
    val uuid: @Serializable(ForUuid::class) UUID = UUID(0L, 0L),
    val itemId: @Serializable(ForResourceLocation::class) ResourceLocation = "minecraft:air".rl,
    val position: @Serializable(ForVec3::class) Vec3 = Vec3.ZERO,
    val target: EntityReference = EntityReference(),
    val nested: GeneratedNested = GeneratedNested(),
    val tags: List<String> = emptyList(),
    @EditorHidden
    val hiddenValue: String = "hidden-default",
)

@Serializable
@SerialName("test:generated_nested")
private data class GeneratedNested(
    val description: String = "",
    val amount: Int = 0,
)

@Serializable
private enum class TestMode {
    PASSIVE,
    AGGRESSIVE,
}

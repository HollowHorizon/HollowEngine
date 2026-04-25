package ru.hollowhorizon.hollowengine.common.codeblocks.blocks.components.generated

import de.fabmax.kool.modules.ui2.*
import de.fabmax.kool.util.Color
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import kotlinx.serialization.Transient
import kotlinx.serialization.descriptors.PrimitiveKind
import kotlinx.serialization.json.*
import net.minecraft.resources.ResourceLocation
import net.minecraft.world.entity.Entity
import net.minecraft.world.phys.Vec3
import ru.hollowhorizon.hollowengine.client.gui.codeblocks.InputSlotScope
import ru.hollowhorizon.hollowengine.client.gui.colors.Dimensions
import ru.hollowhorizon.hollowengine.client.gui.scripting.EditorTheme
import ru.hollowhorizon.hollowengine.common.codeblocks.*
import ru.hollowhorizon.hollowengine.common.codeblocks.blocks.BoolBlock
import ru.hollowhorizon.hollowengine.common.codeblocks.blocks.DefaultText
import ru.hollowhorizon.hollowengine.common.codeblocks.blocks.NumberBlock
import ru.hollowhorizon.hollowengine.common.codeblocks.blocks.StringValueBlock
import ru.hollowhorizon.hollowengine.common.codeblocks.blocks.types.PositionBlock
import ru.hollowhorizon.hollowengine.common.codeblocks.execution.CodeBlockInterpreter
import ru.hollowhorizon.hollowengine.common.codeblocks.execution.ExpressionBlockInterpreter
import ru.hollowhorizon.hollowengine.common.codeblocks.model.BlockModel
import ru.hollowhorizon.hollowengine.common.codeblocks.model.ExpressionBlock
import ru.hollowhorizon.hollowengine.common.codeblocks.model.StatementBlock
import ru.hollowhorizon.hollowengine.common.geary.api.Component
import ru.hollowhorizon.hollowengine.common.geary.components.*
import ru.hollowhorizon.hollowengine.common.geary.components.ai.EntityReference
import ru.hollowhorizon.hollowengine.common.utils.rl
import java.util.*
import kotlin.reflect.KClass
import kotlin.reflect.full.createType

private const val ENTITY_SLOT = "entity"
private const val COMPONENT_SLOT = "component"
private const val ITEM_SLOT = "item"

private val ENTITY_EXPRESSION_TYPE = KTypeExpressionType(Entity::class.createType())
private val BOOLEAN_EXPRESSION_TYPE = KTypeExpressionType(Boolean::class.createType())
private val UUID_EXPRESSION_TYPE = KTypeExpressionType(UUID::class.createType())
private val RESOURCE_LOCATION_EXPRESSION_TYPE = KTypeExpressionType(ResourceLocation::class.createType())
private val ENTITY_REFERENCE_EXPRESSION_TYPE = KTypeExpressionType(EntityReference::class.createType())

object ComponentBlockRuntime {
    fun hasComponent(entity: Entity, descriptor: ComponentDescriptor<*>): Boolean {
        return false // entity.entity.get(descriptor.value) != null
    }

    fun getComponent(entity: Entity, descriptor: ComponentDescriptor<*>): Any {
//        return entity.entity.get(descriptor.value)
//            ?:
        error("Entity ${entity.stringUUID} does not have component ${descriptor.id}.")
    }

    fun setComponent(entity: Entity, descriptor: ComponentDescriptor<*>, component: Any) {
        require(descriptor.value.isInstance(component)) {
            "Component ${descriptor.id} expects ${descriptor.value.simpleName}, got ${component::class.simpleName}."
        }

        @Suppress("UNCHECKED_CAST")
        val componentClass = descriptor.value as KClass<Component>

        @Suppress("UNCHECKED_CAST")
        val value = component as Component
        //val gearyEntity = entity.entity

        if (descriptor.syncPolicy.name == "SYNC") {
          //  gearyEntity.setSyncing(value, componentClass)
        } else {
            //gearyEntity.set(value, componentClass)
        }
    }

    fun removeComponent(entity: Entity, descriptor: ComponentDescriptor<*>) {
        //entity.entity.remove(descriptor.value)
    }
}

@Serializable
@SerialName("hollowengine:components/create")
class CreateComponentBlock(var schemaKey: String = "") : ExpressionBlock(), DynamicDisplayNameProvider {
    override val color: Color get() = CodeBlocksColors.ENTITIES
    override val expressionType: ExpressionType get() = schema().expressionType

    @Transient
    private var configured = false

    init {
        ensureConfigured()
    }

    override suspend fun execute(): Any {
        ensureConfigured()
        val schema = schema()
        val values = linkedMapOf<String, JsonElement>()
        schema.fields.forEach { field ->
            val attached = inputs[field.name]
            val jsonValue = if (attached == null) {
                field.defaultJson
            } else {
                encodeFieldValue(field, evaluateBlock(attached))
            }
            if (jsonValue != null) values[field.name] = jsonValue
        }
        return ru.hollowhorizon.hollowengine.common.geary.components.AutoEditor.json.decodeFromJsonElement(
            schema.serializer,
            JsonObject(values)
        )
    }

    override fun InputSlotScope.composeContent() {
        ensureConfigured()
        val schema = schema()
        Column(Grow.Std) {
            Row(Grow.Std) {
                Text("Создать ${schema.displayName}") {
                    modifier.textColor(Color.WHITE).alignY(AlignmentY.Center).bold()
                }
            }
            schema.fields.forEachIndexed { index, field ->
                Box {
                    modifier.margin(
                        top = if (index == 0) Dimensions.PaddingNormal.scaled() else Dimensions.PaddingSmall.scaled()
                    )
                }
                Row(Grow.Std) {
                    Text(field.displayName) {
                        modifier.textColor(Color.WHITE).alignY(AlignmentY.Center).bold()
                    }
                    Box(Grow.Std) {}
                    InputSlot(field.name, field.inputExpressionType)
                }
            }
        }
    }

    override fun InputSlotScope.composeContentCollapsed() {
        DefaultText("Создать ${schema().displayName}")
    }

    override fun resolveDisplayName(scope: BlocksScope): String = "Создать ${schema().displayName}"

    private fun schema(): ComponentSchema =
        ComponentSchemaRegistry.schema(schemaKey) ?: error("Component schema '$schemaKey' is not registered.")

    private fun ensureConfigured() {
        if (configured || schemaKey.isBlank()) return
        schema().fields.forEach { field ->
            inputTypes[field.name] = field.inputExpressionType
            defaultFactory(field)?.let { factory ->
                setInputDefault(field.name, factory)
            }
        }
        configured = true
    }
}

@Serializable
@SerialName("hollowengine:components/set")
class SetComponentBlock(var descriptorId: String = "") : StatementBlock(), DynamicDisplayNameProvider {
    override val color: Color get() = CodeBlocksColors.ENTITIES

    @Transient
    private var configured = false

    init {
        ensureConfigured()
    }

    override suspend fun execute() {
        ensureConfigured()
        val entity = evaluateBlock(inputs.getValue(ENTITY_SLOT)) as Entity
        val component = evaluateBlock(inputs.getValue(COMPONENT_SLOT))
            ?: error("Component input for ${schema().displayName} returned null.")
        ComponentBlockRuntime.setComponent(entity, descriptor(), component)
    }

    override fun InputSlotScope.composeContent() {
        ensureConfigured()
        Text("Установить ${schema().displayName}") {
            modifier.textColor(Color.WHITE).alignY(AlignmentY.Center).bold()
        }
        Text("сущность:") {
            modifier.textColor(Color.WHITE).alignY(AlignmentY.Center).margin(start = Dimensions.PaddingSmall.scaled())
        }
        InputSlot(ENTITY_SLOT, ENTITY_EXPRESSION_TYPE)
        Text("компонент:") {
            modifier.textColor(Color.WHITE).alignY(AlignmentY.Center).margin(start = Dimensions.PaddingSmall.scaled())
        }
        InputSlot(COMPONENT_SLOT, schema().expressionType)
    }

    override fun InputSlotScope.composeContentCollapsed() {
        DefaultText("Установить ${schema().displayName}")
    }

    override fun resolveDisplayName(scope: BlocksScope): String = "Установить ${schema().displayName}"

    private fun ensureConfigured() {
        if (configured || descriptorId.isBlank()) return
        inputTypes[ENTITY_SLOT] = ENTITY_EXPRESSION_TYPE
        inputTypes[COMPONENT_SLOT] = schema().expressionType
        setInputDefault(COMPONENT_SLOT) { CreateComponentBlock(schema().key) }
        configured = true
    }

    private fun descriptor(): ComponentDescriptor<*> =
        ComponentDescriptorRegistry.descriptorOrNull(descriptorId.rl)
            ?: error("Component descriptor '$descriptorId' is not registered.")

    private fun schema(): ComponentSchema =
        ComponentSchemaRegistry.descriptorSchema(descriptorId.rl)
            ?: error("Component schema '$descriptorId' is not registered.")
}

@Serializable
@SerialName("hollowengine:components/remove")
class RemoveComponentBlock(var descriptorId: String = "") : StatementBlock(), DynamicDisplayNameProvider {
    override val color: Color get() = CodeBlocksColors.ENTITIES

    @Transient
    private var configured = false

    init {
        ensureConfigured()
    }

    override suspend fun execute() {
        ensureConfigured()
        val entity = evaluateBlock(inputs.getValue(ENTITY_SLOT)) as Entity
        ComponentBlockRuntime.removeComponent(entity, descriptor())
    }

    override fun InputSlotScope.composeContent() {
        ensureConfigured()
        Text("Убрать ${schema().displayName}") {
            modifier.textColor(Color.WHITE).alignY(AlignmentY.Center).bold()
        }
        Text("у сущности:") {
            modifier.textColor(Color.WHITE).alignY(AlignmentY.Center).margin(start = Dimensions.PaddingSmall.scaled())
        }
        InputSlot(ENTITY_SLOT, ENTITY_EXPRESSION_TYPE)
    }

    override fun InputSlotScope.composeContentCollapsed() {
        DefaultText("Убрать ${schema().displayName}")
    }

    override fun resolveDisplayName(scope: BlocksScope): String = "Убрать ${schema().displayName}"

    private fun ensureConfigured() {
        if (configured || descriptorId.isBlank()) return
        inputTypes[ENTITY_SLOT] = ENTITY_EXPRESSION_TYPE
        configured = true
    }

    private fun descriptor(): ComponentDescriptor<*> =
        ComponentDescriptorRegistry.descriptorOrNull(descriptorId.rl)
            ?: error("Component descriptor '$descriptorId' is not registered.")

    private fun schema(): ComponentSchema =
        ComponentSchemaRegistry.descriptorSchema(descriptorId.rl)
            ?: error("Component schema '$descriptorId' is not registered.")
}

@Serializable
@SerialName("hollowengine:components/has")
class HasComponentBlock(var descriptorId: String = "") : ExpressionBlock(), DynamicDisplayNameProvider {
    override val color: Color get() = CodeBlocksColors.ENTITIES
    override val expressionType: ExpressionType get() = BOOLEAN_EXPRESSION_TYPE

    @Transient
    private var configured = false

    init {
        ensureConfigured()
    }

    override suspend fun execute(): Boolean {
        ensureConfigured()
        val entity = evaluateBlock(inputs.getValue(ENTITY_SLOT)) as Entity
        return ComponentBlockRuntime.hasComponent(entity, descriptor())
    }

    override fun InputSlotScope.composeContent() {
        ensureConfigured()
        Text("Есть ${schema().displayName}") {
            modifier.textColor(Color.WHITE).alignY(AlignmentY.Center).bold()
        }
        Text("у:") {
            modifier.textColor(Color.WHITE).alignY(AlignmentY.Center).margin(start = Dimensions.PaddingSmall.scaled())
        }
        InputSlot(ENTITY_SLOT, ENTITY_EXPRESSION_TYPE)
    }

    override fun InputSlotScope.composeContentCollapsed() {
        DefaultText("Есть ${schema().displayName}")
    }

    override fun resolveDisplayName(scope: BlocksScope): String = "Есть ${schema().displayName}"

    private fun ensureConfigured() {
        if (configured || descriptorId.isBlank()) return
        inputTypes[ENTITY_SLOT] = ENTITY_EXPRESSION_TYPE
        configured = true
    }

    private fun descriptor(): ComponentDescriptor<*> =
        ComponentDescriptorRegistry.descriptorOrNull(descriptorId.rl)
            ?: error("Component descriptor '$descriptorId' is not registered.")

    private fun schema(): ComponentSchema =
        ComponentSchemaRegistry.descriptorSchema(descriptorId.rl)
            ?: error("Component schema '$descriptorId' is not registered.")
}

@Serializable
@SerialName("hollowengine:components/get_field")
class GetComponentFieldBlock(var descriptorId: String = "", var fieldName: String = "") : ExpressionBlock(),
    DynamicDisplayNameProvider {
    override val color: Color get() = CodeBlocksColors.ENTITIES
    override val expressionType: ExpressionType get() = field().outputExpressionType

    @Transient
    private var configured = false

    init {
        ensureConfigured()
    }

    override suspend fun execute(): Any {
        ensureConfigured()
        val entity = evaluateBlock(inputs.getValue(ENTITY_SLOT)) as Entity
        val component = ComponentBlockRuntime.getComponent(entity, descriptor())
        val componentJson = ru.hollowhorizon.hollowengine.common.geary.components.AutoEditor.json
            .encodeToJsonElement(schema().serializer, component)
        val json = componentJson
            .jsonObject
        val field = field()
        val fieldJson = json[field.name] ?: field.defaultJson ?: JsonNull
        return ru.hollowhorizon.hollowengine.common.geary.components.AutoEditor.json.decodeFromJsonElement(
            field.serializer,
            fieldJson
        )
    }

    override fun InputSlotScope.composeContent() {
        ensureConfigured()
        Text("Получить ${field().displayName}") {
            modifier.textColor(Color.WHITE).alignY(AlignmentY.Center).bold()
        }
        Text("у ${schema().displayName}:") {
            modifier.textColor(Color.WHITE).alignY(AlignmentY.Center).margin(start = Dimensions.PaddingSmall.scaled())
        }
        InputSlot(ENTITY_SLOT, ENTITY_EXPRESSION_TYPE)
    }

    override fun InputSlotScope.composeContentCollapsed() {
        DefaultText("Получить ${field().displayName}")
    }

    override fun resolveDisplayName(scope: BlocksScope): String =
        "Получить ${field().displayName} у ${schema().displayName}"

    private fun ensureConfigured() {
        if (configured || descriptorId.isBlank()) return
        inputTypes[ENTITY_SLOT] = ENTITY_EXPRESSION_TYPE
        configured = true
    }

    private fun descriptor(): ComponentDescriptor<*> =
        ComponentDescriptorRegistry.descriptorOrNull(descriptorId.rl)
            ?: error("Component descriptor '$descriptorId' is not registered.")

    private fun schema(): ComponentSchema =
        ComponentSchemaRegistry.descriptorSchema(descriptorId.rl)
            ?: error("Component schema '$descriptorId' is not registered.")

    private fun field(): ComponentFieldSchema =
        schema().fields.firstOrNull { it.name == fieldName }
            ?: error("Field '$fieldName' is not present in component '$descriptorId'.")
}

@Serializable
@SerialName("hollowengine:components/list")
class ListBuilderBlock(var ownerSchemaKey: String = "", var fieldName: String = "") : ExpressionBlock(),
    DynamicDisplayNameProvider {
    override val color: Color get() = CodeBlocksColors.TYPES
    override val expressionType: ExpressionType get() = field().outputExpressionType

    override suspend fun execute(): List<Any?> {
        val values = inputs.keys
            .filter { it.startsWith("${ITEM_SLOT}_") }
            .sortedBy { it.substringAfterLast('_').toIntOrNull() ?: Int.MAX_VALUE }
            .mapNotNull(inputs::get)
        return values.map { evaluateBlock(it) }
    }

    override fun InputSlotScope.composeContent() {
        val field = field()
        Text("Список ${field.displayName}") {
            modifier.textColor(Color.WHITE).alignY(AlignmentY.Center).bold()
        }
        InputSlotList(ITEM_SLOT, resolveListElementInputType(field))
    }

    override fun InputSlotScope.composeContentCollapsed() {
        DefaultText("Список ${field().displayName}")
    }

    override fun resolveDisplayName(scope: BlocksScope): String = "Список ${field().displayName}"

    private fun field(): ComponentFieldSchema {
        val schema = ComponentSchemaRegistry.schema(ownerSchemaKey)
            ?: error("Component schema '$ownerSchemaKey' is not registered.")
        return schema.fields.firstOrNull { it.name == fieldName }
            ?: error("Field '$fieldName' is not present in schema '$ownerSchemaKey'.")
    }
}

@Serializable
@SerialName("hollowengine:components/enum_literal")
class EnumLiteralBlock(
    var ownerSchemaKey: String = "",
    var fieldName: String = "",
    var selectedEntry: String = "",
) : ExpressionBlock(), DynamicDisplayNameProvider {
    override val color: Color get() = CodeBlocksColors.TYPES
    override val expressionType: ExpressionType get() = field().outputExpressionType

    override suspend fun execute(): Any {
        val field = field()
        val value = selectedEntry.ifBlank { field.enumEntries.firstOrNull().orEmpty() }
        return ru.hollowhorizon.hollowengine.common.geary.components.AutoEditor.json.decodeFromJsonElement(
            field.serializer,
            JsonPrimitive(value)
        )
    }

    override fun InputSlotScope.composeContent() {
        val field = field()
        val entries = field.enumEntries.ifEmpty { listOf("<empty>") }
        if (selectedEntry.isBlank()) selectedEntry = entries.first()

        Text(field.displayName) {
            modifier.textColor(Color.WHITE).alignY(AlignmentY.Center).bold()
        }

        Box {
            modifier
                .margin(start = Dimensions.PaddingSmall.scaled())
                .padding(horizontal = Dimensions.PaddingMedium.scaled(), vertical = Dimensions.PaddingSmall.scaled())
                .background(RoundRectBackground(Color.BLACK.withAlpha(0.3f), Dimensions.PaddingSmall.scaled()))
                .onClick {
                    if (!it.isLeftClick) return@onClick
                    val nextIndex = (entries.indexOf(selectedEntry).takeIf { index -> index >= 0 } ?: -1) + 1
                    selectedEntry = entries[nextIndex % entries.size]
                    notifyChanged()
                    surface.triggerUpdate()
                }

            Text(selectedEntry) {
                modifier.textColor(Color.WHITE).alignY(AlignmentY.Center).regular()
            }
        }
    }

    override fun resolveDisplayName(scope: BlocksScope): String =
        "${field().displayName} = ${selectedEntry.ifBlank { field().enumEntries.firstOrNull().orEmpty() }}"

    private fun field(): ComponentFieldSchema {
        val schema = ComponentSchemaRegistry.schema(ownerSchemaKey)
            ?: error("Component schema '$ownerSchemaKey' is not registered.")
        return schema.fields.firstOrNull { it.name == fieldName }
            ?: error("Field '$fieldName' is not present in schema '$ownerSchemaKey'.")
    }
}

@Serializable
@SerialName("hollowengine:components/uuid_literal")
class UuidLiteralBlock(var value: String = "") : ExpressionBlock(), DynamicDisplayNameProvider {
    override val color: Color get() = CodeBlocksColors.TYPES
    override val expressionType: ExpressionType get() = UUID_EXPRESSION_TYPE

    override suspend fun execute(): UUID {
        return if (value.isBlank()) UUID(0L, 0L)
        else ru.hollowhorizon.hollowengine.common.geary.components.AutoEditor.parseUuid(value)
            ?: error("Invalid UUID literal '$value'.")
    }

    override fun InputSlotScope.composeContent() {
        Text("UUID") { modifier.textColor(Color.WHITE).alignY(AlignmentY.Center).bold() }
        TextField(value) {
            modifier
                .margin(start = Dimensions.PaddingSmall.scaled())
                .font(font)
                .onChange { value = it; notifyChanged() }
                .colors(
                    lineColor = Color.WHITE,
                    textColor = Color.WHITE,
                    selectionColor = EditorTheme.selection,
                    cursorColor = EditorTheme.caret,
                )
                .hint("uuid")
        }
    }

    override fun resolveDisplayName(scope: BlocksScope): String = "UUID"
}

@Serializable
@SerialName("hollowengine:components/resource_location_literal")
class ResourceLocationLiteralBlock(var value: String = "") : ExpressionBlock(), DynamicDisplayNameProvider {
    override val color: Color get() = CodeBlocksColors.TYPES
    override val expressionType: ExpressionType get() = RESOURCE_LOCATION_EXPRESSION_TYPE

    override suspend fun execute(): ResourceLocation {
        return ru.hollowhorizon.hollowengine.common.geary.components.AutoEditor.parseResourceLocation(value)
            ?: error("Invalid resource location literal '$value'.")
    }

    override fun InputSlotScope.composeContent() {
        Text("ID") { modifier.textColor(Color.WHITE).alignY(AlignmentY.Center).bold() }
        TextField(value) {
            modifier
                .margin(start = Dimensions.PaddingSmall.scaled())
                .font(font)
                .onChange { value = it; notifyChanged() }
                .colors(
                    lineColor = Color.WHITE,
                    textColor = Color.WHITE,
                    selectionColor = EditorTheme.selection,
                    cursorColor = EditorTheme.caret,
                )
                .hint("namespace:path")
        }
    }

    override fun resolveDisplayName(scope: BlocksScope): String = "Identifier"
}

@Serializable
@SerialName("hollowengine:components/entity_reference_literal")
class EntityReferenceLiteralBlock(
    var uuidValue: String = "",
    var dimensionValue: String = "minecraft:overworld",
) : ExpressionBlock(), DynamicDisplayNameProvider {
    override val color: Color get() = CodeBlocksColors.TYPES
    override val expressionType: ExpressionType get() = ENTITY_REFERENCE_EXPRESSION_TYPE

    override suspend fun execute(): EntityReference {
        val uuid = if (uuidValue.isBlank()) UUID(0L, 0L)
        else ru.hollowhorizon.hollowengine.common.geary.components.AutoEditor.parseUuid(uuidValue)
            ?: error("Invalid UUID literal '$uuidValue'.")
        val dimension =
            ru.hollowhorizon.hollowengine.common.geary.components.AutoEditor.parseResourceLocation(dimensionValue)
                ?: error("Invalid resource location literal '$dimensionValue'.")
        return EntityReference(uuid, dimension)
    }

    override fun InputSlotScope.composeContent() {
        Text("Ссылка") { modifier.textColor(Color.WHITE).alignY(AlignmentY.Center).bold() }
        TextField(uuidValue) {
            modifier
                .margin(start = Dimensions.PaddingSmall.scaled())
                .font(font)
                .onChange { uuidValue = it; notifyChanged() }
                .colors(
                    lineColor = Color.WHITE,
                    textColor = Color.WHITE,
                    selectionColor = EditorTheme.selection,
                    cursorColor = EditorTheme.caret,
                )
                .hint("uuid")
        }
        TextField(dimensionValue) {
            modifier
                .margin(start = Dimensions.PaddingSmall.scaled())
                .font(font)
                .onChange { dimensionValue = it; notifyChanged() }
                .colors(
                    lineColor = Color.WHITE,
                    textColor = Color.WHITE,
                    selectionColor = EditorTheme.selection,
                    cursorColor = EditorTheme.caret,
                )
                .hint("dimension")
        }
    }

    override fun resolveDisplayName(scope: BlocksScope): String = "Ссылка на сущность"
}

@Serializable
@SerialName("hollowengine:components/entity_uuid")
class GetEntityUuidBlock : ExpressionBlock(), DynamicDisplayNameProvider {
    override val color: Color get() = CodeBlocksColors.ENTITIES
    override val expressionType: ExpressionType get() = UUID_EXPRESSION_TYPE

    init {
        inputTypes[ENTITY_SLOT] = ENTITY_EXPRESSION_TYPE
    }

    override suspend fun execute(): UUID {
        val entity = evaluateBlock(inputs.getValue(ENTITY_SLOT)) as Entity
        return entity.uuid
    }

    override fun InputSlotScope.composeContent() {
        Text("UUID сущности") { modifier.textColor(Color.WHITE).alignY(AlignmentY.Center).bold() }
        InputSlot(ENTITY_SLOT, ENTITY_EXPRESSION_TYPE)
    }

    override fun resolveDisplayName(scope: BlocksScope): String = "UUID сущности"
}

@Serializable
@SerialName("hollowengine:components/entity_reference_from_entity")
class EntityReferenceFromEntityBlock : ExpressionBlock(), DynamicDisplayNameProvider {
    override val color: Color get() = CodeBlocksColors.ENTITIES
    override val expressionType: ExpressionType get() = ENTITY_REFERENCE_EXPRESSION_TYPE

    init {
        inputTypes[ENTITY_SLOT] = ENTITY_EXPRESSION_TYPE
    }

    override suspend fun execute(): EntityReference {
        val entity = evaluateBlock(inputs.getValue(ENTITY_SLOT)) as Entity
        return EntityReference(entity.uuid, entity.level().dimension().location())
    }

    override fun InputSlotScope.composeContent() {
        Text("Ссылка на сущность") { modifier.textColor(Color.WHITE).alignY(AlignmentY.Center).bold() }
        InputSlot(ENTITY_SLOT, ENTITY_EXPRESSION_TYPE)
    }

    override fun resolveDisplayName(scope: BlocksScope): String = "Ссылка на сущность"
}

private fun defaultFactory(field: ComponentFieldSchema): (() -> BlockModel)? {
    return when (field.valueKind) {
        FieldValueKind.TEXT -> {
            val defaultValue = (field.defaultJson as? JsonPrimitive)?.content.orEmpty()
            ({ StringValueBlock(defaultValue) })
        }

        FieldValueKind.NUMBER -> {
            val defaultValue = (field.defaultJson as? JsonPrimitive)?.doubleOrNull ?: 0.0
            ({ NumberBlock(defaultValue) })
        }

        FieldValueKind.BOOLEAN -> {
            val defaultValue = (field.defaultJson as? JsonPrimitive)?.booleanOrNull ?: false
            ({ BoolBlock(defaultValue) })
        }

        FieldValueKind.ENUM -> {
            val defaultValue = (field.defaultJson as? JsonPrimitive)?.content.orEmpty()
            ({ EnumLiteralBlock(field.ownerSchemaKey, field.name, defaultValue) })
        }

        FieldValueKind.UUID -> {
            val defaultValue = runCatching {
                ru.hollowhorizon.hollowengine.common.geary.components.AutoEditor.formatUuid(
                    ru.hollowhorizon.hollowengine.common.geary.components.AutoEditor.json.decodeFromJsonElement(
                        field.serializer,
                        field.defaultJson ?: JsonNull
                    ) as UUID
                )
            }.getOrDefault("")
            ({ UuidLiteralBlock(defaultValue) })
        }

        FieldValueKind.RESOURCE_LOCATION -> {
            val defaultValue = runCatching {
                ru.hollowhorizon.hollowengine.common.geary.components.AutoEditor.formatResourceLocation(
                    ru.hollowhorizon.hollowengine.common.geary.components.AutoEditor.json.decodeFromJsonElement(
                        field.serializer,
                        field.defaultJson ?: JsonNull
                    ) as ResourceLocation
                )
            }.getOrDefault("")
            ({ ResourceLocationLiteralBlock(defaultValue) })
        }

        FieldValueKind.VEC3 -> {
            val defaultValue = runCatching {
                ru.hollowhorizon.hollowengine.common.geary.components.AutoEditor.json.decodeFromJsonElement(
                    field.serializer,
                    field.defaultJson ?: JsonNull
                ) as Vec3
            }.getOrDefault(Vec3.ZERO)
            ({
                PositionBlock().apply {
                    attachInput("x", NumberBlock(defaultValue.x))
                    attachInput("y", NumberBlock(defaultValue.y))
                    attachInput("z", NumberBlock(defaultValue.z))
                }
            })
        }

        FieldValueKind.ENTITY_REFERENCE -> {
            val defaultValue = runCatching {
                ru.hollowhorizon.hollowengine.common.geary.components.AutoEditor.json.decodeFromJsonElement(
                    field.serializer,
                    field.defaultJson ?: JsonNull
                ) as EntityReference
            }.getOrDefault(EntityReference())
            ({
                EntityReferenceLiteralBlock(
                    uuidValue = if (defaultValue.isEmpty()) "" else defaultValue.uuid.toString(),
                    dimensionValue = defaultValue.level.toString(),
                )
            })
        }

        FieldValueKind.CLASS -> {
            val key = field.nestedSchemaKey ?: return null
            ({ CreateComponentBlock(key) })
        }

        FieldValueKind.LIST -> ({ ListBuilderBlock(field.ownerSchemaKey, field.name) })

        FieldValueKind.UNSUPPORTED -> null
    }
}

private suspend fun evaluateBlock(block: BlockModel): Any? {
    return when (block) {
        is ExpressionBlock -> ExpressionBlockInterpreter<Any>(block).execute()
        is StatementBlock -> CodeBlockInterpreter<Any>(block).execute()
        else -> error("Unsupported block type ${block::class.simpleName}.")
    }
}

private fun encodeFieldValue(field: ComponentFieldSchema, value: Any?): JsonElement {
    if (value == null) return JsonNull
    if (field.valueKind == FieldValueKind.NUMBER) {
        val number =
            value as? Number ?: error("Field ${field.displayName} expects a number, got ${value::class.simpleName}.")
        return when (field.serializer.descriptor.kind) {
            PrimitiveKind.INT -> JsonPrimitive(number.toInt())
            PrimitiveKind.LONG -> JsonPrimitive(number.toLong())
            PrimitiveKind.SHORT -> JsonPrimitive(number.toInt())
            PrimitiveKind.BYTE -> JsonPrimitive(number.toInt())
            PrimitiveKind.FLOAT -> JsonPrimitive(number.toFloat())
            PrimitiveKind.DOUBLE -> JsonPrimitive(number.toDouble())
            else -> JsonPrimitive(number.toDouble())
        }
    }
    return ru.hollowhorizon.hollowengine.common.geary.components.AutoEditor.json.encodeToJsonElement(
        field.serializer,
        value
    )
}

private fun resolveListElementInputType(field: ComponentFieldSchema): ExpressionType {
    return field.listElementType?.let {
        ru.hollowhorizon.hollowengine.common.geary.components.resolveInputExpressionType(
            it,
            field.listElementKind ?: FieldValueKind.UNSUPPORTED,
        )
    } ?: field.inputExpressionType
}

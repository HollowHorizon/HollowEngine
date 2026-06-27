package ru.hollowhorizon.hollowengine.common.geary.components

import de.fabmax.kool.modules.ui2.*
import de.fabmax.kool.toString
import de.fabmax.kool.util.Color
import de.fabmax.kool.util.MsdfFont
import kotlinx.serialization.*
import kotlinx.serialization.descriptors.PrimitiveKind
import kotlinx.serialization.descriptors.SerialKind
import kotlinx.serialization.descriptors.StructureKind
import kotlinx.serialization.json.*
import net.minecraft.resources.ResourceLocation
import net.minecraft.world.phys.Vec3
import ru.hollowhorizon.hollowengine.client.gui.colors.ColorTheme
import ru.hollowhorizon.hollowengine.client.gui.colors.Dimensions
import ru.hollowhorizon.hollowengine.client.gui.scripting.AccordionColumnLayout
import ru.hollowhorizon.hollowengine.client.kool.minecraft.Image
import ru.hollowhorizon.hollowengine.client.utils.lang
import ru.hollowhorizon.hollowengine.common.geary.components.ai.EntityReference
import ru.hollowhorizon.hollowengine.common.utils.nbt.NBTFormat
import ru.hollowhorizon.hollowengine.common.utils.rl
import ru.hollowhorizon.hollowengine.generated.Assets.Hollowengine.Textures.Gui.Icons.REMOVE
import java.util.*
import kotlin.reflect.KClass
import kotlin.reflect.KType
import kotlin.reflect.full.createType
import kotlin.reflect.full.findAnnotation

object AutoEditor {
    val json = Json {
        encodeDefaults = true
        ignoreUnknownKeys = true
        useArrayPolymorphism = true
        serializersModule = NBTFormat.serializersModule
    }

    @OptIn(ExperimentalSerializationApi::class)
    fun serializerOrNull(type: KType?): KSerializer<Any>? {
        if (type == null) return null
        return runCatching {
            @Suppress("UNCHECKED_CAST")
            NBTFormat.serializersModule.serializer(type) as KSerializer<Any>
        }.getOrNull()
    }

    fun defaultJson(serializer: KSerializer<Any>): JsonElement? {
        val descriptor = serializer.descriptor
        return when (val kind = descriptor.kind) {
            PrimitiveKind.STRING -> JsonPrimitive("")
            PrimitiveKind.INT,
            PrimitiveKind.LONG,
            PrimitiveKind.SHORT,
            PrimitiveKind.BYTE,
            -> JsonPrimitive(0)

            PrimitiveKind.FLOAT,
            PrimitiveKind.DOUBLE,
            -> JsonPrimitive(0.0)

            PrimitiveKind.BOOLEAN -> JsonPrimitive(false)
            SerialKind.ENUM -> descriptor.getElementName(0).let(::JsonPrimitive)
            StructureKind.CLASS -> runCatching {
                json.encodeToJsonElement(serializer, json.decodeFromJsonElement(serializer, JsonObject(emptyMap())))
            }.getOrNull()

            StructureKind.LIST -> JsonArray(emptyList())
            else -> null
        }
    }

    fun parseUuid(raw: String): UUID? {
        val normalized = raw.trim().removeSurrounding("{", "}")
        if (normalized.isBlank()) return null
        return runCatching { UUID.fromString(normalized) }.getOrElse {
            val compact = normalized.replace("-", "")
            if (compact.length != 32 || compact.any { ch -> !ch.isDigit() && ch.lowercaseChar() !in 'a'..'f' }) null
            else runCatching {
                UUID.fromString(
                    compact.replaceFirst(
                        Regex("([0-9a-fA-F]{8})([0-9a-fA-F]{4})([0-9a-fA-F]{4})([0-9a-fA-F]{4})([0-9a-fA-F]{12})"),
                        "$1-$2-$3-$4-$5"
                    )
                )
            }.getOrNull()
        }
    }

    fun parseResourceLocation(raw: String): ResourceLocation? {
        val normalized = raw.trim()
        if (normalized.isBlank()) return null
        return ResourceLocation.tryParse(normalized)
            ?: if (':' !in normalized) ResourceLocation.tryParse("minecraft:$normalized") else null
    }

    fun parseVec3(raw: String): Vec3? {
        val normalized = raw.trim()
            .removePrefix("[")
            .removeSuffix("]")
            .replace(',', ' ')
            .replace(';', ' ')
        if (normalized.isBlank()) return null
        val parts = normalized.split(Regex("\\s+"))
            .map(String::trim)
            .filter(String::isNotEmpty)
        if (parts.size != 3) return null
        val x = parts[0].toDoubleOrNull() ?: return null
        val y = parts[1].toDoubleOrNull() ?: return null
        val z = parts[2].toDoubleOrNull() ?: return null
        return Vec3(x, y, z)
    }

    fun formatUuid(value: UUID): String = value.toString()

    fun formatResourceLocation(value: ResourceLocation): String = value.toString()

    fun formatVec3(value: Vec3): String = listOf(value.x, value.y, value.z).joinToString(" ") { it.compactNumber() }
}

inline fun <reified T : Any> UiScope.GenericEditor(
    state: MutableStateValue<T>,
    noinline onRemove: () -> Unit,
    noinline onChange: () -> Unit = {},
    allowRemove: Boolean = true,
) {
    GenericEditor(
        state = state,
        serializer = serializer<T>(),
        onRemove = onRemove,
        onChange = onChange,
        ownerType = runCatching { state.value::class.createType() }.getOrNull(),
        allowRemove = allowRemove,
    )
}

fun <T : Any> UiScope.GenericEditor(
    state: MutableStateValue<T>,
    serializer: KSerializer<T>,
    onRemove: () -> Unit,
) {
    GenericEditor(
        state = state,
        serializer = serializer,
        onRemove = onRemove,
        onChange = {},
        ownerType = runCatching { state.value::class.createType() }.getOrNull(),
        allowRemove = true,
    )
}

@OptIn(InternalSerializationApi::class, ExperimentalSerializationApi::class)
fun <T : Any> UiScope.GenericEditor(
    state: MutableStateValue<T>,
    serializer: KSerializer<T>,
    onRemove: () -> Unit,
    onChange: () -> Unit = {},
    ownerType: KType? = runCatching { state.value::class.createType() }.getOrNull(),
    allowRemove: Boolean = true,
) {
    @Suppress("UNCHECKED_CAST")
    val schema = ComponentSchemaRegistry.schemaFor(ownerType, serializer as KSerializer<Any>)
    @Suppress("UNCHECKED_CAST")
    val resolvedSerializer = serializer as KSerializer<Any>
    val icon = schema?.icon ?: resolveSchemaIcon(resolvedSerializer)
    val displayName = schema?.displayName ?: resolveSchemaDisplayName(resolvedSerializer)
    val categoryTitle = schema?.descriptorId?.let { "$displayName [$it]" } ?: displayName

    Category(icon.rl, categoryTitle, onRemove = onRemove, showRemoveButton = allowRemove) {
        val currentElement = AutoEditor.json.encodeToJsonElement(serializer, state.value)
        renderEditorValue(
            label = displayName,
            current = currentElement,
            serializer = serializer as KSerializer<Any>,
            ownerType = ownerType,
            onUpdate = { updated ->
                state.set(AutoEditor.json.decodeFromJsonElement(serializer, updated))
                onChange()
            },
            onChange = onChange,
            showInlineLabel = false,
        )
    }
}

@OptIn(ExperimentalSerializationApi::class)
private fun UiScope.renderEditorValue(
    label: String,
    current: JsonElement,
    serializer: KSerializer<Any>,
    ownerType: KType?,
    onUpdate: (JsonElement) -> Unit,
    onChange: () -> Unit,
    showInlineLabel: Boolean = true,
) {
    when (resolveFieldValueKind(ownerType, serializer)) {
        FieldValueKind.TEXT -> TextProperty(
            label = label.takeIf { showInlineLabel },
            value = (current as? JsonPrimitive)?.content ?: "",
            hint = label,
        ) {
            onUpdate(JsonPrimitive(it))
            onChange()
        }

        FieldValueKind.NUMBER -> NumberProperty(
            label = label.takeIf { showInlineLabel },
            value = (current as? JsonPrimitive)?.content ?: "0",
            kind = serializer.descriptor.kind,
        ) {
            onUpdate(it)
            onChange()
        }

        FieldValueKind.BOOLEAN -> BoolProperty(
            label = label.takeIf { showInlineLabel },
            value = (current as? JsonPrimitive)?.booleanOrNull ?: false,
        ) {
            onUpdate(JsonPrimitive(it))
            onChange()
        }

        FieldValueKind.ENUM -> {
            val enumNames = (0 until serializer.descriptor.elementsCount).map(serializer.descriptor::getElementName)
            EnumProperty(
                label = label.takeIf { showInlineLabel },
                items = enumNames,
                selectedIndex = enumNames.indexOf((current as? JsonPrimitive)?.content).coerceAtLeast(0),
            ) { newIndex ->
                enumNames.getOrNull(newIndex)?.let {
                    onUpdate(JsonPrimitive(it))
                    onChange()
                }
            }
        }

        FieldValueKind.UUID -> UuidProperty(
            label = label.takeIf { showInlineLabel },
            value = runCatching { AutoEditor.json.decodeFromJsonElement(serializer, current) as UUID }.getOrNull(),
        ) {
            onUpdate(AutoEditor.json.encodeToJsonElement(serializer, it))
            onChange()
        }

        FieldValueKind.RESOURCE_LOCATION -> ResourceLocationProperty(
            label = label.takeIf { showInlineLabel },
            value = runCatching { AutoEditor.json.decodeFromJsonElement(serializer, current) as ResourceLocation }.getOrNull(),
        ) {
            onUpdate(AutoEditor.json.encodeToJsonElement(serializer, it))
            onChange()
        }

        FieldValueKind.VEC3 -> Vec3Property(
            label = label.takeIf { showInlineLabel },
            value = runCatching { AutoEditor.json.decodeFromJsonElement(serializer, current) as Vec3 }.getOrElse { Vec3.ZERO },
        ) {
            onUpdate(AutoEditor.json.encodeToJsonElement(serializer, it))
            onChange()
        }

        FieldValueKind.ENTITY_REFERENCE -> renderEntityReferenceField(current, onUpdate, onChange)

        FieldValueKind.CLASS -> {
            val jsonObject = current as? JsonObject ?: JsonObject(emptyMap())
            renderClassFields(
                currentJson = jsonObject,
                serializer = serializer,
                ownerType = ownerType,
                onUpdate = onUpdate,
                onChange = onChange,
                label = label.takeIf { showInlineLabel },
            )
        }

        FieldValueKind.LIST -> renderListField(
            label = label.takeIf { showInlineLabel } ?: label,
            current = current as? JsonArray ?: JsonArray(emptyList()),
            serializer = serializer,
            ownerType = ownerType,
            onUpdate = onUpdate,
            onChange = onChange,
        )

        FieldValueKind.UNSUPPORTED -> UnsupportedProperty(
            label.takeIf { showInlineLabel },
            "hollowengine.gui.codeblocks.label.component_unsupported_kind".lang(serializer.descriptor.kind.toString())
        )
    }
}

@OptIn(ExperimentalSerializationApi::class)
private fun UiScope.renderClassFields(
    currentJson: JsonObject,
    serializer: KSerializer<Any>,
    ownerType: KType?,
    onUpdate: (JsonElement) -> Unit,
    onChange: () -> Unit,
    label: String? = null,
) {
    label?.let(::SectionTitle)
    val fields = ComponentSchemaRegistry.schemaFor(ownerType, serializer)?.fields ?: emptyList()

    for (field in fields) {
        val fieldJson = currentJson[field.name] ?: field.defaultJson ?: JsonNull

        fun updateField(newValue: JsonElement) {
            val newMap = currentJson.toMutableMap()
            newMap[field.name] = newValue
            onUpdate(JsonObject(newMap))
        }

        when (field.valueKind) {
            FieldValueKind.CLASS,
            FieldValueKind.LIST,
            FieldValueKind.ENTITY_REFERENCE,
            -> PropertySection(field.displayName, field.icon.rl) {
                renderEditorValue(
                    label = field.displayName,
                    current = fieldJson,
                    serializer = field.serializer,
                    ownerType = field.ownerType,
                    onUpdate = {
                        updateField(it)
                        onChange()
                    },
                    onChange = onChange,
                    showInlineLabel = false,
                )
            }

            else -> PropertyRow(field.displayName, field.icon.rl) {
                renderEditorValue(
                    label = field.displayName,
                    current = fieldJson,
                    serializer = field.serializer,
                    ownerType = field.ownerType,
                    onUpdate = {
                        updateField(it)
                        onChange()
                    },
                    onChange = onChange,
                    showInlineLabel = false,
                )
            }
        }
    }
}

private fun UiScope.renderEntityReferenceField(
    current: JsonElement,
    onUpdate: (JsonElement) -> Unit,
    onChange: () -> Unit,
) {
    val currentRef = runCatching {
        AutoEditor.json.decodeFromJsonElement(EntityReference.serializer(), current)
    }.getOrElse { EntityReference() }
    val emptyUuid = UUID(0L, 0L)

    Column(Grow.Std) {
        TextProperty(
            label = "hollowengine.gui.codeblocks.label.component_uuid_label".lang,
            value = currentRef.uuid.takeUnless { it == emptyUuid }?.let(AutoEditor::formatUuid).orEmpty(),
            hint = "hollowengine.gui.codeblocks.label.component_uuid_hint".lang,
            validationError = { raw ->
                if (raw.isBlank() || AutoEditor.parseUuid(raw) != null) null
                else "hollowengine.gui.codeblocks.label.component_invalid_uuid".lang
            },
        ) { raw ->
            val parsed = if (raw.isBlank()) emptyUuid else AutoEditor.parseUuid(raw) ?: return@TextProperty
            onUpdate(AutoEditor.json.encodeToJsonElement(EntityReference.serializer(), currentRef.copy(uuid = parsed)))
            onChange()
        }

        PropertySpacer()

        TextProperty(
            label = "hollowengine.gui.codeblocks.label.component_dimension_label".lang,
            value = AutoEditor.formatResourceLocation(currentRef.level),
            hint = "hollowengine.gui.codeblocks.label.component_identifier_hint".lang,
            validationError = { raw ->
                if (AutoEditor.parseResourceLocation(raw) != null) null
                else "hollowengine.gui.codeblocks.label.component_invalid_identifier".lang
            },
        ) { raw ->
            val parsed = AutoEditor.parseResourceLocation(raw) ?: return@TextProperty
            onUpdate(AutoEditor.json.encodeToJsonElement(EntityReference.serializer(), currentRef.copy(level = parsed)))
            onChange()
        }

        if (currentRef.isEmpty()) {
            ValidationMessage("hollowengine.gui.codeblocks.label.component_empty_reference".lang, isError = false)
        }
    }
}

@OptIn(ExperimentalSerializationApi::class)
private fun UiScope.renderListField(
    label: String,
    current: JsonArray,
    serializer: KSerializer<Any>,
    ownerType: KType?,
    onUpdate: (JsonElement) -> Unit,
    onChange: () -> Unit,
) {
    val elementType = ownerType?.arguments?.firstOrNull()?.type
    val elementSerializer = AutoEditor.serializerOrNull(elementType)
        ?: serializer.descriptor.getElementDescriptor(0).kind.let(::serializerFromDescriptorKind)

    Column(Grow.Std) {
        SectionTitle(label)

        if (current.isEmpty()) {
            ValidationMessage("hollowengine.gui.codeblocks.label.component_empty".lang, isError = false)
        }

        LazyColumn(Grow.Std, 210.dp, withHorizontalScrollbar = false) {
            itemsIndexed(current) { index, item ->
                CollectionItemCard(
                    index = index,
                    summary = elementSerializer?.let { collectionItemSummary(item, it, elementType) },
                    canMoveUp = index > 0,
                    canMoveDown = index < current.lastIndex,
                    onMoveUp = {
                        val updated = current.toMutableList().apply { add(index - 1, removeAt(index)) }
                        onUpdate(JsonArray(updated))
                        onChange()
                    },
                    onMoveDown = {
                        val updated = current.toMutableList().apply { add(index + 1, removeAt(index)) }
                        onUpdate(JsonArray(updated))
                        onChange()
                    },
                    onRemove = {
                        val updated = current.toMutableList().apply { removeAt(index) }
                        onUpdate(JsonArray(updated))
                        onChange()
                    },
                ) {
                    if (elementSerializer == null) {
                        UnsupportedProperty(null, "hollowengine.gui.codeblocks.label.component_unsupported_item".lang)
                    } else {
                        renderEditorValue(
                            label = "${"hollowengine.gui.codeblocks.label.component_item".lang} ${index + 1}",
                            current = item,
                            serializer = elementSerializer,
                            ownerType = elementType,
                            onUpdate = { replacement ->
                                val updated = current.toMutableList().apply { this[index] = replacement }
                                onUpdate(JsonArray(updated))
                                onChange()
                            },
                            onChange = onChange,
                            showInlineLabel = false,
                        )
                    }
                }
            }
        }

        SmallActionButton("hollowengine.gui.codeblocks.label.component_add_item".lang) {
            val defaultItem = elementSerializer?.let(AutoEditor::defaultJson)
            if (defaultItem != null) {
                onUpdate(JsonArray(current + defaultItem))
                onChange()
            }
        }

        if (elementSerializer == null) {
            ValidationMessage("hollowengine.gui.codeblocks.label.component_cannot_create_default".lang)
        }
    }
}

private fun collectionItemSummary(item: JsonElement, serializer: KSerializer<Any>, ownerType: KType?): String? {
    return when (resolveFieldValueKind(ownerType, serializer)) {
        FieldValueKind.VEC3 -> runCatching {
            AutoEditor.formatVec3(AutoEditor.json.decodeFromJsonElement(serializer, item) as Vec3)
        }.getOrNull()
        FieldValueKind.UUID -> runCatching {
            AutoEditor.formatUuid(AutoEditor.json.decodeFromJsonElement(serializer, item) as UUID)
        }.getOrNull()
        FieldValueKind.RESOURCE_LOCATION -> runCatching {
            AutoEditor.formatResourceLocation(AutoEditor.json.decodeFromJsonElement(serializer, item) as ResourceLocation)
        }.getOrNull()
        FieldValueKind.TEXT,
        FieldValueKind.NUMBER,
        FieldValueKind.ENUM,
        -> (item as? JsonPrimitive)?.contentOrNull
        else -> null
    }
}

private fun numberJson(kind: SerialKind, raw: String): JsonPrimitive? {
    return when (kind) {
        PrimitiveKind.INT -> raw.toIntOrNull()?.let(::JsonPrimitive)
        PrimitiveKind.LONG -> raw.toLongOrNull()?.let(::JsonPrimitive)
        PrimitiveKind.SHORT -> raw.toShortOrNull()?.let(::JsonPrimitive)
        PrimitiveKind.BYTE -> raw.toByteOrNull()?.let(::JsonPrimitive)
        PrimitiveKind.FLOAT -> raw.toFloatOrNull()?.let(::JsonPrimitive)
        PrimitiveKind.DOUBLE -> raw.toDoubleOrNull()?.let(::JsonPrimitive)
        else -> null
    }
}


@OptIn(InternalSerializationApi::class)
fun UiScope.PolymorphicField(label: String, baseClass: KClass<*>, current: JsonElement, onUpdate: (JsonElement) -> Unit) {
    val implementations = ru.hollowhorizon.hollowengine.common.utils.nbt.NBT_TAGS[baseClass] ?: emptyList<KClass<*>>()
    val currentType = (current as? JsonObject)?.get("type")?.jsonPrimitive?.content ?: ""

    Column(Grow.Std) {
        Text(label) { modifier.textColor(Color.WHITE).margin(Dimensions.PaddingSmall) }

        Row {
            implementations.forEach { impl ->
                val implSerialName = impl.findAnnotation<SerialName>()?.value ?: impl.simpleName ?: ""
                Button(implSerialName) {
                    modifier.onClick {
                        val serializer = impl.serializerOrNull() as? KSerializer<Any> ?: return@onClick
                        val newObj = impl.java.getDeclaredConstructor().newInstance()
                        val json = AutoEditor.json.encodeToJsonElement(serializer, newObj).jsonObject.toMutableMap()
                        json["type"] = JsonPrimitive(implSerialName)
                        onUpdate(JsonObject(json))
                    }
                    if (currentType == implSerialName) modifier.backgroundColor(ColorTheme.Accents.Main)
                }
            }
        }

        val currentImpl = implementations.find { it.findAnnotation<SerialName>()?.value == currentType }
        val serializer = currentImpl?.serializerOrNull() as? KSerializer<Any>
        if (serializer != null) {
            val value = AutoEditor.json.decodeFromJsonElement(serializer, current)
            val state = mutableStateOf(value)
            state.onChange { _, newValue ->
                val json = AutoEditor.json.encodeToJsonElement(serializer, newValue).jsonObject.toMutableMap()
                json["type"] = JsonPrimitive(currentType)
                onUpdate(JsonObject(json))
            }
            GenericEditor(state, serializer, onRemove = {}, onChange = {}, allowRemove = false)
        }
    }
}

fun UiScope.Category(
    icon: ResourceLocation,
    name: String,
    onRemove: () -> Unit,
    showRemoveButton: Boolean = true,
    block: ColumnScope.() -> Unit,
) {
    Column(Grow.Std) {
        modifier.margin(Dimensions.PaddingMedium)
            .background(RoundRectBackground(ColorTheme.UI.BackgroundElements, Dimensions.PaddingMedium))
            .border(
                RoundRectBorder(
                    ColorTheme.UI.BackgroundAccent,
                    Dimensions.PaddingMedium,
                    Dimensions.PaddingSmall * 0.5f,
                )
            )

        val isExpanded = remember(true)

        Row(Grow.Std) {
            modifier.margin(Dimensions.PaddingMedium)
                .padding(Dimensions.PaddingMedium)

            Image(icon) {
                modifier.size(Dimensions.PaddingHuge, Dimensions.PaddingHuge)
            }

            Text(name) {
                modifier.font(remember { MsdfFont(ColorTheme.Fonts.MONOCRAFT, 16f) })
                    .textColor(Color.WHITE)
                    .margin(Dimensions.PaddingMedium)
                    .align(AlignmentX.Start, AlignmentY.Center)
                    .width(Grow.Std)
            }

            Arrow(if (isExpanded.use()) ArrowScope.ROTATION_UP else ArrowScope.ROTATION_RIGHT) {
                modifier.size(Dimensions.PaddingHuge, Dimensions.PaddingHuge)
                    .margin(Dimensions.PaddingMedium)
                    .colors(ColorTheme.UI.BackgroundAccent, ColorTheme.UI.WhiteReplacement)
                    .alignY(AlignmentY.Center)
                    .onClick { isExpanded.set(!isExpanded.value) }
            }

            if (showRemoveButton) {
                Image(REMOVE) {
                    modifier.size(Dimensions.PaddingHuge, Dimensions.PaddingHuge)
                        .margin(end = Dimensions.PaddingSmall)
                        .alignY(AlignmentY.Center)
                        .onClick { if (it.isLeftClick) onRemove() }
                }
            }
        }

        val height by animateFloatAsState(if (isExpanded.use()) 1f else 0f)
        if (isExpanded.use() || height > 0) {
            Box(Grow.Std, Dimensions.PaddingSmall * 0.5f) {
                modifier.backgroundColor(ColorTheme.UI.BackgroundAccent)
            }
            Column(Grow(1f)) {
                modifier.layout(AccordionColumnLayout(height))
                    .padding(Dimensions.PaddingHuge)
                block()
            }
        }
    }
}

fun UiScope.SectionTitle(text: String) {
    Text(text) {
        modifier.font(remember { MsdfFont(ColorTheme.Fonts.MONOCRAFT, 15f) })
            .textColor(ColorTheme.UI.WhiteReplacement.withAlpha(0.85f))
            .padding(bottom = Dimensions.PaddingMedium)
    }
}

fun UiScope.PropertyRow(label: String, icon: ResourceLocation, content: UiScope.() -> Unit) {
    Column(Grow.Std) {
        modifier.padding(vertical = Dimensions.PaddingSmall)

        Row(Grow.Std) {
            Image(icon) {
                modifier.size(Dimensions.PaddingHuge, Dimensions.PaddingHuge)
                    .margin(end = Dimensions.PaddingMedium)
                    .alignY(AlignmentY.Center)
            }
            Text(label) {
                modifier.alignY(AlignmentY.Center)
                    .font(remember { MsdfFont(ColorTheme.Fonts.MONOCRAFT, 14f) })
                    .textColor(ColorTheme.UI.WhiteReplacement.withAlpha(0.9f))
            }
        }

        Box(Grow.Std) {
            modifier.margin(top = Dimensions.PaddingSmall)
            content()
        }
    }
}

fun UiScope.PropertySection(label: String, icon: ResourceLocation, body: ColumnScope.() -> Unit) {
    Column(Grow.Std) {
        modifier.padding(vertical = Dimensions.PaddingSmall)
        Row(Grow.Std) {
            Image(icon) {
                modifier.size(Dimensions.PaddingHuge, Dimensions.PaddingHuge)
                    .margin(end = Dimensions.PaddingMedium)
                    .alignY(AlignmentY.Center)
            }
            Text(label) {
                modifier.font(remember { MsdfFont(ColorTheme.Fonts.MONOCRAFT, 14f) })
                    .textColor(ColorTheme.UI.WhiteReplacement.withAlpha(0.9f))
                    .padding(vertical = Dimensions.PaddingSmall)
            }
        }
        Column(Grow.Std) {
            modifier.margin(start = Dimensions.PaddingMedium)
                .padding(top = Dimensions.PaddingSmall)
            body()
        }
    }
}

fun UiScope.CollectionItemCard(
    index: Int,
    summary: String?,
    canMoveUp: Boolean,
    canMoveDown: Boolean,
    onMoveUp: () -> Unit,
    onMoveDown: () -> Unit,
    onRemove: () -> Unit,
    body: ColumnScope.() -> Unit,
) {
    Column(Grow.Std) {
        modifier.margin(bottom = Dimensions.PaddingMedium)
            .padding(Dimensions.PaddingMedium)
            .background(RoundRectBackground(ColorTheme.UI.BackgroundSecondary, Dimensions.PaddingMedium))
            .border(
                RoundRectBorder(
                    ColorTheme.UI.BackgroundAccent,
                    Dimensions.PaddingMedium,
                    Dimensions.PaddingSmall * 0.5f,
                )
            )

        Column(Grow.Std) {
            Text(summary ?: "${"hollowengine.gui.codeblocks.label.component_item".lang} ${index + 1}") {
                modifier.textColor(ColorTheme.UI.WhiteReplacement)
                    .font(remember { MsdfFont(ColorTheme.Fonts.MONOCRAFT, 13f) })
                    .width(Grow.Std)
            }
            Row(Grow.Std) {
                modifier.margin(top = Dimensions.PaddingSmall)
                SmallActionButton("Up", enabled = canMoveUp, onClick = onMoveUp)
                SmallActionButton("Down", enabled = canMoveDown, onClick = onMoveDown)
                SmallActionButton("X", onClick = onRemove)
            }
        }

        Box(Grow.Std, Dimensions.PaddingSmall) {}
        body()
    }
}

fun UiScope.UnsupportedProperty(label: String?, message: String) {
    Column(Grow.Std) {
        label?.let(::SectionTitle)
        ValidationMessage(message)
    }
}

fun UiScope.TextProperty(
    label: String?,
    value: String,
    hint: String = "",
    error: String? = null,
    validationError: ((String) -> String?)? = null,
    onChange: (String) -> Unit,
) {
    Column(Grow.Std) {
        label?.let(::SectionTitle)
        var editText by remember(value)
        val resolvedError = error ?: validationError?.invoke(editText)

        Box(Grow.Std) {
            modifier.background(RoundRectBackground(ColorTheme.UI.BackgroundDarker, Dimensions.PaddingMedium))
                .border(
                    RoundRectBorder(
                        if (resolvedError == null) ColorTheme.UI.BackgroundAccent else Color.RED,
                        Dimensions.PaddingMedium,
                        Dimensions.PaddingSmall * 0.5f,
                    )
                )
                .padding(horizontal = Dimensions.PaddingMedium, vertical = Dimensions.PaddingSmall)

            TextField(editText) {
                if (!isFocused.use() && editText != value) editText = value
                modifier.onChange {
                    editText = it
                    onChange(it)
                }.hint(hint)
                    .width(Grow.Std)
                    .colors(
                        ColorTheme.UI.WhiteReplacement,
                        ColorTheme.UI.WhiteReplacement.withAlpha(0.45f),
                        ColorTheme.CodeWindow.Selection,
                        ColorTheme.UI.WhiteReplacement,
                        ColorTheme.UI.BackgroundAccent.withAlpha(0f),
                        ColorTheme.UI.BackgroundAccent.withAlpha(0f),
                    )
            }
        }

        resolvedError?.let { ValidationMessage(it) }
    }
}

fun UiScope.NumberProperty(label: String?, value: String, kind: SerialKind, onChange: (JsonPrimitive) -> Unit) {
    TextProperty(
        label = label,
        value = value,
        hint = label ?: "0",
        validationError = { raw ->
            if (raw.isBlank() || numberJson(kind, raw) != null) null
            else "hollowengine.gui.codeblocks.label.component_invalid_number".lang
        },
    ) { raw ->
        numberJson(kind, raw)?.let(onChange)
    }
}

fun UiScope.UuidProperty(label: String?, value: UUID?, onChange: (UUID) -> Unit) {
    TextProperty(
        label = label,
        value = value?.let(AutoEditor::formatUuid).orEmpty(),
        hint = "hollowengine.gui.codeblocks.label.component_uuid_hint".lang,
        validationError = { raw ->
            if (raw.isBlank() || AutoEditor.parseUuid(raw) != null) null
            else "hollowengine.gui.codeblocks.label.component_invalid_uuid".lang
        },
    ) { raw ->
        AutoEditor.parseUuid(raw)?.let(onChange)
    }
}

fun UiScope.ResourceLocationProperty(label: String?, value: ResourceLocation?, onChange: (ResourceLocation) -> Unit) {
    TextProperty(
        label = label,
        value = value?.let(AutoEditor::formatResourceLocation).orEmpty(),
        hint = "hollowengine.gui.codeblocks.label.component_identifier_hint".lang,
        validationError = { raw ->
            if (raw.isBlank() || AutoEditor.parseResourceLocation(raw) != null) null
            else "hollowengine.gui.codeblocks.label.component_invalid_identifier".lang
        },
    ) { raw ->
        AutoEditor.parseResourceLocation(raw)?.let(onChange)
    }
}

fun UiScope.Vec3Property(label: String?, value: Vec3, onChange: (Vec3) -> Unit) {
    TextProperty(
        label = label,
        value = AutoEditor.formatVec3(value),
        hint = "hollowengine.gui.codeblocks.label.component_vector_hint".lang,
        validationError = { raw ->
            if (raw.isBlank() || AutoEditor.parseVec3(raw) != null) null
            else "hollowengine.gui.codeblocks.label.component_invalid_vector".lang
        },
    ) { raw ->
        AutoEditor.parseVec3(raw)?.let(onChange)
    }
}

fun UiScope.EnumProperty(label: String?, items: List<String>, selectedIndex: Int, onItemSelected: (Int) -> Unit) {
    Column(Grow.Std) {
        label?.let(::SectionTitle)
        ComboBox {
            modifier.width(Grow.Std)
                .items(items)
                .font(remember { MsdfFont(ColorTheme.Fonts.MONOCRAFT, 14f) })
                .background(RoundRectBackground(ColorTheme.UI.BackgroundDarker, Dimensions.PaddingSmall))
                .padding(Dimensions.PaddingSmall)
            modifier.selectedIndex(selectedIndex)
            modifier.onItemSelected(onItemSelected)
        }
    }
}

fun UiScope.BoolProperty(label: String?, value: Boolean, onToggle: (Boolean) -> Unit) {
    Row(Grow.Std) {
        modifier.background(RoundRectBackground(ColorTheme.UI.BackgroundDarker, Dimensions.PaddingMedium))
            .border(
                RoundRectBorder(
                    ColorTheme.UI.BackgroundAccent,
                    Dimensions.PaddingMedium,
                    Dimensions.PaddingSmall * 0.5f,
                )
            )
            .padding(horizontal = Dimensions.PaddingMedium, vertical = Dimensions.PaddingSmall)

        Checkbox(value) {
            modifier.onToggle(onToggle)
                .alignY(AlignmentY.Center)
                .colors(
                    borderColor = ColorTheme.Accents.Main,
                    backgroundColor = ColorTheme.UI.BackgroundElements,
                    fillColor = ColorTheme.Accents.Main,
                    checkMarkColor = Color.WHITE,
                )
                .margin(end = Dimensions.PaddingMedium)
        }

        label?.let {
            Text(it) {
                modifier.alignY(AlignmentY.Center)
                    .textColor(ColorTheme.UI.WhiteReplacement)
            }
        }
    }
}

fun UiScope.SmallActionButton(
    label: String,
    enabled: Boolean = true,
    highlighted: Boolean = false,
    onClick: () -> Unit,
) {
    Box {
        modifier.margin(start = Dimensions.PaddingSmall)
            .background(
                RoundRectBackground(
                    when {
                        highlighted -> ColorTheme.Accents.Main
                        enabled -> ColorTheme.UI.BackgroundAccent
                        else -> ColorTheme.UI.BackgroundAccent.withAlpha(0.3f)
                    },
                    Dimensions.PaddingSmall,
                )
            )
            .padding(horizontal = Dimensions.PaddingMedium, vertical = Dimensions.PaddingSmall)
            .onClick { if (enabled) onClick() }

        Text(label) {
            modifier.textColor(if (enabled) ColorTheme.UI.WhiteReplacement else ColorTheme.UI.WhiteReplacement.withAlpha(0.4f))
                .font(remember { MsdfFont(ColorTheme.Fonts.MONOCRAFT, 13f) })
        }
    }
}

fun UiScope.ValidationMessage(message: String, isError: Boolean = true) {
    Text(message) {
        modifier.textColor(if (isError) Color.RED else ColorTheme.UI.WhiteReplacement.withAlpha(0.55f))
            .font(remember { MsdfFont(ColorTheme.Fonts.MONOCRAFT, 12f) })
            .padding(top = Dimensions.PaddingSmall)
    }
}

fun UiScope.PropertySpacer() {
    Box(Grow.Std, Dimensions.PaddingSmall) {}
}

private fun Double.compactNumber(): String {
    if (!isFinite()) return toString()
    val value = toString(3)
    return value.trimEnd('0').trimEnd('.').ifBlank { "0" }
}


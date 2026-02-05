package ru.hollowhorizon.hollowengine.common.geary.components

import de.fabmax.kool.modules.ui2.*
import de.fabmax.kool.toString
import de.fabmax.kool.util.Color
import de.fabmax.kool.util.MsdfFont
import kotlinx.serialization.InternalSerializationApi
import kotlinx.serialization.KSerializer
import kotlinx.serialization.descriptors.PrimitiveKind
import kotlinx.serialization.descriptors.StructureKind
import kotlinx.serialization.json.*
import kotlinx.serialization.serializer
import kotlinx.serialization.serializerOrNull
import net.minecraft.resources.ResourceLocation
import ru.hollowhorizon.hollowengine.client.gui.colors.ColorTheme
import ru.hollowhorizon.hollowengine.client.gui.colors.Dimensions
import ru.hollowhorizon.hollowengine.client.gui.scripting.AccordionColumnLayout
import ru.hollowhorizon.hollowengine.client.kool.minecraft.Image
import ru.hollowhorizon.hollowengine.common.codeblocks.modules.icons
import ru.hollowhorizon.hollowengine.common.utils.rl
import ru.hollowhorizon.hollowengine.api.utils.Polymorphic
import kotlin.reflect.KClass
import kotlin.reflect.full.findAnnotation

object AutoEditor {
    val json = Json {
        encodeDefaults = true
        ignoreUnknownKeys = true
    }
}

inline fun <reified T : Any> UiScope.GenericEditor(
    state: MutableStateValue<T>,
    noinline onRemove: () -> Unit,
) {
    GenericEditor(state, serializer<T>(), onRemove)
}

@OptIn(InternalSerializationApi::class)
fun <T : Any> UiScope.GenericEditor(
    state: MutableStateValue<T>,
    serializer: KSerializer<T>,
    onRemove: () -> Unit,
) {
    val descriptor = serializer.descriptor

    val currentJson = AutoEditor.json.encodeToJsonElement(serializer, state.value).jsonObject
    val icon = descriptor.annotations.filterIsInstance<EditorIcon>().firstOrNull()?.icon
        ?: "hollowengine:textures/gui/icons/autocomplete_class.svg"
    val displayName = descriptor.annotations.filterIsInstance<EditorName>().firstOrNull()?.name
        ?: descriptor.serialName

    Category(icon.rl, displayName, onRemove = onRemove) {

        for (i in 0 until descriptor.elementsCount) {
            val elementName = descriptor.getElementName(i)
            val elementDescriptor = descriptor.getElementDescriptor(i)
            val annotations = descriptor.getElementAnnotations(i)

            if (annotations.any { it is EditorHidden }) continue

            val fieldDisplayName = annotations.filterIsInstance<EditorName>().firstOrNull()?.name ?: elementName
            val range = annotations.filterIsInstance<EditorRange>().firstOrNull()
            val fieldIcon = annotations.filterIsInstance<EditorIcon>().firstOrNull()?.icon
                ?: "hollowengine:textures/gui/icons/autocomplete_class.svg"

            fun updateField(newValue: JsonElement) {
                val newMap = currentJson.toMutableMap()
                newMap[elementName] = newValue
                val newObj = AutoEditor.json.decodeFromJsonElement(serializer, JsonObject(newMap))
                state.set(newObj)
            }

            // Check for @Polymorphic
            val polymorphic = annotations.filterIsInstance<Polymorphic>().firstOrNull()
            if (polymorphic != null) {
                PolymorphicField(fieldDisplayName, polymorphic.baseClass, currentJson[elementName] ?: JsonNull) {
                    updateField(it)
                }
                continue
            }

            Row(Grow.Std) {
                Image(fieldIcon.rl) {
                    modifier.size(Dimensions.PaddingHuge, Dimensions.PaddingHuge)
                        .margin(vertical = Dimensions.PaddingMedium, horizontal = Dimensions.PaddingNormal)
                }

                when (elementDescriptor.kind) {
                    PrimitiveKind.STRING -> {
                        val proxyState = mutableStateOf((currentJson[elementName] as? JsonPrimitive)?.content ?: "")
                        proxyState.onChange { _, newValue -> updateField(JsonPrimitive(newValue)) }
                        TextProperty(fieldDisplayName, proxyState, hint = fieldDisplayName)
                    }

                    PrimitiveKind.FLOAT -> {
                        val currentVal = (currentJson[elementName] as? JsonPrimitive)?.float ?: 0f
                        val proxyState = mutableStateOf(currentVal)
                        proxyState.onChange { _, newValue -> updateField(JsonPrimitive(newValue)) }
                        FloatProperty(fieldDisplayName, proxyState, min = range?.min ?: 0f, max = range?.max ?: 1f)
                    }

                    PrimitiveKind.INT -> {
                        val currentVal = (currentJson[elementName] as? JsonPrimitive)?.int ?: 0
                        val proxyState = mutableStateOf(currentVal.toFloat())
                        proxyState.onChange { _, newValue -> updateField(JsonPrimitive(newValue.toInt())) }
                        FloatProperty(fieldDisplayName, proxyState, min = range?.min ?: 0f, max = range?.max ?: 100f)
                    }

                    PrimitiveKind.BOOLEAN -> {
                        val currentVal = (currentJson[elementName] as? JsonPrimitive)?.boolean ?: false
                        val proxyState = mutableStateOf(currentVal)
                        proxyState.onChange { _, newValue -> updateField(JsonPrimitive(newValue)) }
                        BoolProperty(fieldDisplayName, proxyState)
                    }

                    StructureKind.CLASS -> {
                        // Nested Object Support
                        val fieldKClass = try {
                            Class.forName(elementDescriptor.serialName).kotlin
                        } catch (e: Exception) {
                            null
                        }
                        
                        val fieldSerializer = fieldKClass?.serializerOrNull() as? KSerializer<Any>
                        if (fieldSerializer != null) {
                            val fieldValue = AutoEditor.json.decodeFromJsonElement(fieldSerializer, currentJson[elementName] ?: JsonObject(emptyMap()))
                            val fieldState = mutableStateOf(fieldValue)
                            fieldState.onChange { _, newValue -> updateField(AutoEditor.json.encodeToJsonElement(fieldSerializer, newValue)) }
                            
                            GenericEditor(fieldState, fieldSerializer) {
                                // Nested remove logic if needed
                            }
                        } else {
                            Text("Unsupported Nested: ${elementDescriptor.serialName}") { modifier.textColor(Color.RED) }
                        }
                    }

                    else -> {
                        Text("Unsupported Type: ${elementDescriptor.kind}") { modifier.textColor(Color.YELLOW) }
                    }
                }
            }
        }
    }
}

@OptIn(InternalSerializationApi::class)
fun UiScope.PolymorphicField(label: String, baseClass: KClass<*>, current: JsonElement, onUpdate: (JsonElement) -> Unit) {
    val implementations = ru.hollowhorizon.hollowengine.common.utils.nbt.NBT_TAGS[baseClass] ?: emptyList<KClass<*>>()
    val currentType = (current as? JsonObject)?.get("type")?.jsonPrimitive?.content ?: ""
    
    Column(Grow.Std) {
        Text(label) { modifier.textColor(Color.WHITE).margin(Dimensions.PaddingSmall) }
        
        // Simplified implementation selection (could be a dropdown)
        Row {
            implementations.forEach { impl ->
                val implSerialName = impl.findAnnotation<kotlinx.serialization.SerialName>()?.value ?: impl.simpleName ?: ""
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
        
        val currentImpl = implementations.find { it.findAnnotation<kotlinx.serialization.SerialName>()?.value == currentType }
        val serializer = currentImpl?.serializerOrNull() as? KSerializer<Any>
        if (serializer != null) {
            val value = AutoEditor.json.decodeFromJsonElement(serializer, current)
            val state = mutableStateOf(value)
            state.onChange { _, newValue -> 
                val json = AutoEditor.json.encodeToJsonElement(serializer, newValue).jsonObject.toMutableMap()
                json["type"] = JsonPrimitive(currentType)
                onUpdate(JsonObject(json))
            }
            GenericEditor(state, serializer) {}
        }
    }
}

fun UiScope.Category(icon: ResourceLocation, name: String, onRemove: () -> Unit, block: ColumnScope.() -> Unit) {
    Column(Grow.Std) {
        modifier.margin(Dimensions.PaddingMedium)
            .background(RoundRectBackground(ColorTheme.UI.BackgroundElements, Dimensions.PaddingMedium))
            .border(
                RoundRectBorder(
                    ColorTheme.UI.BackgroundAccent,
                    Dimensions.PaddingMedium,
                    Dimensions.PaddingSmall * 0.5f
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
                modifier
                    .font(remember {
                        MsdfFont(ColorTheme.Fonts.MONOCRAFT, 16f)
                    })
                    .textColor(Color.WHITE)
                    .margin(Dimensions.PaddingMedium)
                    .align(AlignmentX.Start, AlignmentY.Center)
                    .width(Grow.Std)
            }

            Arrow(if (isExpanded.use()) ArrowScope.ROTATION_UP else ArrowScope.ROTATION_RIGHT) {
                modifier.size(Dimensions.PaddingHuge, Dimensions.PaddingHuge)
                    .margin(Dimensions.PaddingMedium)
                    .colors(
                        ColorTheme.UI.BackgroundAccent,
                        ColorTheme.UI.WhiteReplacement
                    ).alignY(AlignmentY.Center)
                    .onClick {
                        isExpanded.set(!isExpanded.value)
                    }
            }

            Image(icons.REMOVE) {
                modifier.size(Dimensions.PaddingHuge, Dimensions.PaddingHuge)
                    .margin(end = Dimensions.PaddingSmall)
                    .alignY(AlignmentY.Center)
                    .onClick { if (it.isLeftClick) onRemove() }
            }
        }


        val height by animateFloatAsState(if (isExpanded.use()) 1f else 0f)

        if (isExpanded.use() || height > 0) {
            Box(Grow.Std, Dimensions.PaddingSmall * 0.5f) {
                modifier.backgroundColor(ColorTheme.UI.BackgroundAccent)
            }
            Column(Grow(1f)) {
                modifier
                    .layout(AccordionColumnLayout(height))
                    .padding(Dimensions.PaddingHuge)

                block()
            }
        }
    }
}

fun UiScope.TextProperty(label: String, field: MutableStateValue<String>, hint: String = "") {
    Column(Grow.Std) {
        Text(label) {
            modifier
                .font(remember {
                    MsdfFont(ColorTheme.Fonts.MONOCRAFT, 16f)
                })
                .textColor(ColorTheme.UI.WhiteReplacement)
                .padding(vertical = Dimensions.PaddingMedium)
                .align(AlignmentX.Start, AlignmentY.Center)
        }

        Box(Grow.Std) {
            modifier.padding(Dimensions.PaddingMedium)
                .background(RoundRectBackground(ColorTheme.UI.BackgroundDarker, Dimensions.PaddingMedium))
                .border(
                    RoundRectBorder(
                        ColorTheme.UI.BackgroundAccent,
                        Dimensions.PaddingMedium,
                        Dimensions.PaddingSmall * 0.5f
                    )
                )

            TextField(field.use()) {
                modifier
                    .onChange { field.set(it) }
                    .hint(hint)
                    .width(Grow.Std)
                    .colors(
                        ColorTheme.UI.WhiteReplacement,
                        ColorTheme.UI.WhiteReplacement.withAlpha(0.5f),
                        ColorTheme.CodeWindow.Selection,
                        ColorTheme.UI.WhiteReplacement,
                        ColorTheme.UI.BackgroundAccent.withAlpha(0f),
                        ColorTheme.UI.BackgroundAccent.withAlpha(0.5f)
                    )
            }
        }
    }
}

fun UiScope.FloatProperty(label: String, field: MutableStateValue<Float>, min: Float = 0f, max: Float = 1f) {
    Column(Grow.Std) {
        Text(label) {
            modifier
                .font(remember {
                    MsdfFont(ColorTheme.Fonts.MONOCRAFT, 16f)
                })
                .textColor(ColorTheme.UI.WhiteReplacement)
                .padding(vertical = Dimensions.PaddingMedium)
                .align(AlignmentX.Start, AlignmentY.Center)
        }

        Row(Grow.Std) {
            modifier.padding(Dimensions.PaddingMedium)
                .background(RoundRectBackground(ColorTheme.UI.BackgroundDarker, Dimensions.PaddingMedium))
                .border(
                    RoundRectBorder(
                        ColorTheme.UI.BackgroundAccent,
                        Dimensions.PaddingMedium,
                        Dimensions.PaddingSmall * 0.5f
                    )
                )

            Text(field.use().toString(3)) {
                modifier.margin(Dimensions.PaddingNormal)
                    .alignY(AlignmentY.Center)
            }
            Slider(field.use(), min, max) {
                modifier.onChange { field.set(it) }
                    .width(Grow.Std)
                    .colors(
                        ColorTheme.UI.WhiteReplacement,
                        ColorTheme.UI.BackgroundAccent,
                        ColorTheme.UI.BackgroundAccent.withAlpha(0.5f)
                    )
            }
        }
    }
}

fun UiScope.BoolProperty(label: String, field: MutableStateValue<Boolean>) {
    Row(Grow.Std) {
        modifier.padding(Dimensions.PaddingMedium)
            .background(RoundRectBackground(ColorTheme.UI.BackgroundDarker, Dimensions.PaddingMedium))
            .border(
                RoundRectBorder(
                    ColorTheme.UI.BackgroundAccent,
                    Dimensions.PaddingMedium,
                    Dimensions.PaddingSmall * 0.5f
                )
            )

        Checkbox(field.use()) {
            modifier.onToggle { field.set(it) }
                .alignY(AlignmentY.Center)
                .colors(
                    borderColor = ColorTheme.Accents.Main,
                    backgroundColor = ColorTheme.UI.BackgroundElements,
                    fillColor = ColorTheme.Accents.Main,
                    checkMarkColor = Color.WHITE
                )
                .margin(Dimensions.PaddingMedium)
        }

        Text(label) {
            modifier.alignY(AlignmentY.Center)
        }
    }
}

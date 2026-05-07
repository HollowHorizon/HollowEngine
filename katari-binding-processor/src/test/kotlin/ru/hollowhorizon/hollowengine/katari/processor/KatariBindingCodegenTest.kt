package ru.hollowhorizon.hollowengine.katari.processor

import kotlin.test.Test
import kotlin.test.assertContains

class KatariBindingCodegenTest {
    @Test
    fun `generated registrar wires snapshot host type function and property`() {
        val hostType = ScriptTypeModel(
            typeId = "Example",
            targetType = "test.Example",
            snapshotType = "test.ExampleSnapshot",
            superTypes = emptyList(),
            source = null,
        )
        val hostModel = TypeModel(
            kotlinType = "test.Example",
            katariTypeExpression = "Example",
            hostTypeId = "Example",
            converter = null,
            enumTypeId = null,
            nullable = false,
        )
        val textModel = TypeModel(
            kotlinType = "String",
            katariTypeExpression = "String",
            hostTypeId = null,
            converter = "asString",
            enumTypeId = null,
            nullable = false,
        )
        val function = FunctionModel(
            scriptName = "rename",
            receiver = hostModel,
            parameters = listOf(ParameterModel("name", textModel, hasDefault = false, isVararg = false)),
            returnType = hostModel,
            call = "receiver.__CALL__(__ARGS__)",
            isSuspend = false,
            passesReceiverAsArgument = false,
            importQualifiedName = "test.rename",
        )
        val property = PropertyModel(
            scriptName = "title",
            receiver = hostModel,
            receiverKotlinType = "test.Example",
            valueType = textModel,
            writable = true,
            getter = "typedReceiver.title",
            setter = "typedReceiver.title",
            importQualifiedName = null,
            source = null,
        )
        val code = KatariBindingCodegen(
            scriptTypes = listOf(hostType),
            functions = listOf(function),
            classes = listOf(ClassModel(hostType, emptyList(), emptyList(), listOf(property), null)),
        ).generate()

        assertContains(code, "registerHostType(")
        assertContains(code, "test.Example::class,")
        assertContains(code, "test.ExampleSnapshot.serializer()")
        assertContains(code, "immediateFunction(")
        assertContains(code, "\"rename\"")
        assertContains(code, "import test.rename as generatedKatariFunction0")
        assertContains(code, "receiverType = \"Example\"")
        assertContains(code, "receiver.generatedKatariFunction0(name = name)")
        assertContains(code, "registerKotliteExtensionProperty(")
        assertContains(code, "typedReceiver.title = KatariGeneratedBindingRuntime.asString(value, \"title\")")
    }

    @Test
    fun `generated registrar wires top level extension property through import alias`() {
        val hostType = ScriptTypeModel(
            typeId = "Example",
            targetType = "test.Example",
            snapshotType = "test.ExampleSnapshot",
            superTypes = emptyList(),
            source = null,
        )
        val hostModel = TypeModel(
            kotlinType = "test.Example",
            katariTypeExpression = "Example",
            hostTypeId = "Example",
            converter = null,
            enumTypeId = null,
            nullable = false,
        )
        val textModel = TypeModel(
            kotlinType = "String",
            katariTypeExpression = "String",
            hostTypeId = null,
            converter = "asString",
            enumTypeId = null,
            nullable = false,
        )
        val property = PropertyModel(
            scriptName = "displayName",
            receiver = hostModel,
            receiverKotlinType = "test.Example",
            valueType = textModel,
            writable = true,
            getter = "typedReceiver.__PROPERTY__",
            setter = "typedReceiver.__PROPERTY__",
            importQualifiedName = "test.title",
            source = null,
        )

        val code = KatariBindingCodegen(
            scriptTypes = listOf(hostType),
            functions = emptyList(),
            classes = emptyList(),
            properties = listOf(property),
        ).generate()

        assertContains(code, "import test.title as generatedKatariProperty0")
        assertContains(code, "declaredName = \"displayName\"")
        assertContains(code, "KatariGeneratedBindingRuntime.toRuntimeValue(typedReceiver.generatedKatariProperty0, null")
        assertContains(code, "typedReceiver.generatedKatariProperty0 = KatariGeneratedBindingRuntime.asString(value, \"displayName\")")
    }

    @Test
    fun `generated function supports default vararg and suspend dispatch`() {
        val textModel = TypeModel(
            kotlinType = "String",
            katariTypeExpression = "String",
            hostTypeId = null,
            converter = "asString",
            enumTypeId = null,
            nullable = false,
        )
        val function = FunctionModel(
            scriptName = "announce",
            receiver = null,
            parameters = listOf(
                ParameterModel("prefix", textModel, hasDefault = true, defaultValueExpression = "\"system\"", isVararg = false),
                ParameterModel("lines", textModel, hasDefault = false, isVararg = true),
            ),
            returnType = TypeModel.unit(),
            call = "test.announce(__ARGS__)",
            isSuspend = true,
            passesReceiverAsArgument = false,
            importQualifiedName = null,
        )

        val code = KatariBindingCodegen(emptyList(), listOf(function), emptyList()).generate()

        assertContains(code, "suspendableFunction(")
        assertContains(code, "CustomFunctionParameter(\"prefix\", \"String\", defaultValueExpression = \"\\\"system\\\"\")")
        assertContains(code, "CustomFunctionParameter(\"lines\", \"String.repeated()\", modifiers = setOf(\"vararg\"))")
        assertContains(code, "test.announce(prefix = prefix, lines = lines)")
        assertContains(code, "GeneratedRuntimeValueResponse(it)")
    }

    @Test
    fun `generated registrar registers enum types used by functions`() {
        val enumModel = TypeModel(
            kotlinType = "test.Mode",
            katariTypeExpression = "Mode",
            hostTypeId = null,
            converter = null,
            enumTypeId = "Mode",
            nullable = false,
        )
        val function = FunctionModel(
            scriptName = "mode",
            receiver = null,
            parameters = listOf(ParameterModel("value", enumModel, hasDefault = false, isVararg = false)),
            returnType = enumModel,
            call = "test.mode(__ARGS__)",
            isSuspend = false,
            passesReceiverAsArgument = false,
            importQualifiedName = null,
        )

        val code = KatariBindingCodegen(
            scriptTypes = emptyList(),
            functions = listOf(function),
            classes = emptyList(),
            enumTypes = listOf(EnumTypeModel("Mode", "test.Mode", null)),
        ).generate()

        assertContains(code, "registerEnum(test.Mode::class, \"Mode\", test.Mode::class.java.enumConstants.toList())")
        assertContains(code, "KatariGeneratedBindingRuntime.asEnum<test.Mode>(valueArgument, \"Mode\", \"value\")")
        assertContains(code, "KatariGeneratedBindingRuntime.toRuntimeValue(test.mode(value = value), \"Mode\"")
    }

    @Test
    fun `generated function preserves generic collection signature`() {
        val generic = TypeModel.generic("T", TypeModel.any(nullable = false), nullable = false)
        val list = TypeModel.collection("List", "List", listOf(generic), CollectionKind.LIST, nullable = false)
        val map = TypeModel.collection(
            "Map",
            "Map",
            listOf(
                TypeModel.primitive("String", "String", "asString", nullable = false),
                generic,
            ),
            CollectionKind.MAP,
            nullable = false,
        )
        val function = FunctionModel(
            scriptName = "collect",
            receiver = null,
            parameters = listOf(
                ParameterModel("items", list, hasDefault = false, isVararg = false),
                ParameterModel("named", map, hasDefault = false, isVararg = false),
            ),
            returnType = list,
            typeParameters = listOf(TypeParameterModel("T", TypeModel.any(nullable = false))),
            call = "test.collect(__ARGS__)",
            isSuspend = false,
            passesReceiverAsArgument = false,
            importQualifiedName = null,
        )

        val code = KatariBindingCodegen(emptyList(), listOf(function), emptyList()).generate()

        assertContains(code, "TypeParameter(\"T\", \"Any\")")
        assertContains(code, "CustomFunctionParameter(\"items\", \"List<T>\")")
        assertContains(code, "CustomFunctionParameter(\"named\", \"Map<String, T>\")")
        assertContains(code, "KatariGeneratedBindingRuntime.asList(itemsArgument, \"items\")")
        assertContains(code, "KatariGeneratedBindingRuntime.asMap(namedArgument, \"named\"")
    }

    @Test
    fun `generated inline function preserves generic function parameter signature`() {
        val generic = TypeModel.generic("T", null, nullable = false)
        val defaultValue = TypeModel.function(
            parameterTypes = emptyList(),
            returnType = TypeModel.any(nullable = true),
            nullable = false,
        )
        val function = FunctionModel(
            scriptName = "getOrCreate",
            receiver = TypeModel(
                kotlinType = "test.Store",
                katariTypeExpression = "Store",
                hostTypeId = "Store",
                converter = null,
                enumTypeId = null,
                nullable = false,
            ),
            parameters = listOf(
                ParameterModel("key", TypeModel.primitive("String", "String", "asString", nullable = false), hasDefault = false, isVararg = false),
                ParameterModel("defaultValue", defaultValue, hasDefault = false, isVararg = false),
            ),
            returnType = generic,
            typeParameters = listOf(TypeParameterModel("T", null)),
            call = "receiver.__CALL__(__ARGS__)",
            isSuspend = false,
            passesReceiverAsArgument = false,
            importQualifiedName = "test.getOrCreate",
            inlineBody = "{ return defaultValue() }",
            inlineBodyFormat = InlineBodyFormat.Block,
        )

        val code = KatariBindingCodegen(emptyList(), listOf(function), emptyList()).generate()

        assertContains(code, "register(object : NarrativeCallable")
        assertContains(code, "override val id: String = \"getOrCreate\"")
        assertContains(code, "override val receiverType: String? = \"Store\"")
        assertContains(code, "override val returnType: String = \"T\"")
        assertContains(code, "CustomFunctionParameter(\"defaultValue\", \"() -> Any?\")")
        assertContains(code, "modifiers = setOf(FunctionModifier.inline)")
        assertContains(code, "inlineFunctionBody = \"{ return defaultValue() }\"")
    }
}

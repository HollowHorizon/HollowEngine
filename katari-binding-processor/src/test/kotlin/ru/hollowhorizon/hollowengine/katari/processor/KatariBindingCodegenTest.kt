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
            katariTypeExpression = "KatariParameterType(\"Example\")",
            hostTypeId = "Example",
            converter = null,
            nullable = false,
        )
        val textModel = TypeModel(
            kotlinType = "String",
            katariTypeExpression = "KatariTypes.Text",
            hostTypeId = null,
            converter = "asString",
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
        assertContains(code, "test.Example::class.toKatari(\"Example\")")
        assertContains(code, "test.ExampleSnapshot.serializer()")
        assertContains(code, "ImmediateKatariFunctionDefinition(")
        assertContains(code, "id = \"rename\"")
        assertContains(code, "import test.rename as generatedKatariFunction0")
        assertContains(code, "dispatchReceiverType = KatariParameterType(\"Example\")")
        assertContains(code, "receiver.generatedKatariFunction0(name = name)")
        assertContains(code, "extensionProperty(")
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
            katariTypeExpression = "KatariParameterType(\"Example\")",
            hostTypeId = "Example",
            converter = null,
            nullable = false,
        )
        val textModel = TypeModel(
            kotlinType = "String",
            katariTypeExpression = "KatariTypes.Text",
            hostTypeId = null,
            converter = "asString",
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
        assertContains(code, "name = \"displayName\"")
        assertContains(code, "KatariGeneratedBindingRuntime.toKatariValue(typedReceiver.generatedKatariProperty0, null)")
        assertContains(code, "typedReceiver.generatedKatariProperty0 = KatariGeneratedBindingRuntime.asString(value, \"displayName\")")
    }

    @Test
    fun `generated function supports default vararg and suspend dispatch`() {
        val textModel = TypeModel(
            kotlinType = "String",
            katariTypeExpression = "KatariTypes.Text",
            hostTypeId = null,
            converter = "asString",
            nullable = false,
        )
        val function = FunctionModel(
            scriptName = "announce",
            receiver = null,
            parameters = listOf(
                ParameterModel("prefix", textModel, hasDefault = true, isVararg = false),
                ParameterModel("lines", textModel, hasDefault = false, isVararg = true),
            ),
            returnType = TypeModel.unit(),
            call = "test.announce(__ARGS__)",
            isSuspend = true,
            passesReceiverAsArgument = false,
            importQualifiedName = null,
        )

        val code = KatariBindingCodegen(emptyList(), listOf(function), emptyList()).generate()

        assertContains(code, "SuspendableKatariFunctionDefinition(")
        assertContains(code, "KatariTypes.Text.asValueParameter(\"prefix\", hasDefault = true)")
        assertContains(code, "KatariTypes.Text.repeated().asValueParameter(\"lines\", hasDefault = false)")
        assertContains(code, "prefixArgument == KatariValue.DefaultArgument")
        assertContains(code, "test.announce(lines = lines)")
        assertContains(code, "test.announce(prefix = prefix, lines = lines)")
        assertContains(code, "GeneratedKatariValueResponse(it)")
    }
}

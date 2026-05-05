package com.sunnychung.lib.multiplatform.kotlite.model

import com.sunnychung.lib.multiplatform.kotlite.Interpreter
import com.sunnychung.lib.multiplatform.kotlite.Parser
import com.sunnychung.lib.multiplatform.kotlite.lexer.Lexer

fun String.toTypeNode(filename: String) = Parser(Lexer(filename, this))
    .type(isParseDottedIdentifiers = true, isIncludeLastIdentifierAsTypeName = true)

class CustomFunctionDeclarationNode(
    private val def: CustomFunctionDefinition,
    position: SourcePosition? = null,
    name: String? = null,
    receiver: TypeNode? = null,
    returnType: TypeNode? = null,
    typeParameters: List<TypeParameterNode>? = null,
    valueParameters: List<FunctionValueParameterNode>? = null,
    modifiers: Set<FunctionModifier>? = null,
    body: BlockNode? = null,
    transformedRefName: String? = null,
) : FunctionDeclarationNode(
    position = position ?: def.position,
    name = name ?: def.functionName,
    receiver = receiver ?: def.receiverType?.toTypeNode(def.position.filename),
    declaredReturnType = returnType ?: def.returnType.toTypeNode(def.position.filename),
    typeParameters = typeParameters ?: def.typeParameters.map {
        TypeParameterNode(def.position, it.name, it.typeUpperBound?.toTypeNode(def.position.filename))
    },
    valueParameters = valueParameters ?: def.parameterTypes.map {
        FunctionValueParameterNode(
            position = def.position,
            name = it.name,
            declaredType = it.type.toTypeNode(def.position.filename),
            defaultValue = it.defaultValueExpression?.let { Parser(Lexer(def.position.filename, it)).expression() },
            modifiers = with(Parser(Lexer("", ""))) { it.modifiers.toFunctionValueParameterModifiers() }
        )
    },
    declaredModifiers = modifiers ?: def.modifiers,
    body = body ?: def.inlineFunctionBody?.let {
        Parser(Lexer(def.position.filename, it)).functionBody()
    } ?: BlockNode(emptyList(), SourcePosition(def.position.filename, 1, 1), ScopeType.Function, FunctionBodyFormat.Block, def.returnType.toTypeNode(def.position.filename)),
    transformedRefName = transformedRefName,
) {
    override fun execute(interpreter: Interpreter, receiver: RuntimeValue?, arguments: List<RuntimeValue>, typeArguments: Map<String, DataType>): RuntimeValue {
        return def.executable(interpreter, receiver, arguments, typeArguments)
    }

    override fun copy(
        name: String,
        receiver: TypeNode?,
        declaredReturnType: TypeNode?,
        typeParameters: List<TypeParameterNode>,
        valueParameters: List<FunctionValueParameterNode>,
        modifiers: Set<FunctionModifier>,
        body: BlockNode?,
        inlineBodySource: String?,
        inlineBodyFormat: FunctionBodyFormat?,
        transformedRefName: String?,
        inferredReturnType: TypeNode?,
    ): FunctionDeclarationNode {
        if (this::class != CustomFunctionDeclarationNode::class) {
            throw UnsupportedOperationException("Copying subclasses is not supported")
        }
        return CustomFunctionDeclarationNode(
            def/*.copy(
                receiverType = receiver,
            )*/,
            name = name,
            receiver = receiver,
            returnType = declaredReturnType,
            typeParameters = typeParameters,
            valueParameters = valueParameters,
            modifiers = modifiers,
            body = body,
            transformedRefName = transformedRefName
        )
    }
}

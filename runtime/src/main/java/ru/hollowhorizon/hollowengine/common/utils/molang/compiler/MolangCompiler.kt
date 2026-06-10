package ru.hollowhorizon.hollowengine.common.utils.molang.compiler

import de.fabmax.kool.math.Vec3f
import it.unimi.dsi.fastutil.objects.Object2ObjectOpenHashMap
import kotlinx.serialization.KSerializer
import kotlinx.serialization.Serializable
import kotlinx.serialization.SerializationException
import kotlinx.serialization.descriptors.SerialDescriptor
import kotlinx.serialization.encoding.Decoder
import kotlinx.serialization.encoding.Encoder
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonDecoder
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonPrimitive
import org.objectweb.asm.ClassWriter
import org.objectweb.asm.Label
import org.objectweb.asm.MethodVisitor
import org.objectweb.asm.Opcodes.*
import org.objectweb.asm.Type
import ru.hollowhorizon.hollowengine.common.utils.molang.lexer.Lexer
import ru.hollowhorizon.hollowengine.common.utils.molang.lexer.Token
import ru.hollowhorizon.hollowengine.common.utils.molang.parser.*
import ru.hollowhorizon.hollowengine.common.utils.molang.runtime.MolangContext
import ru.hollowhorizon.hollowengine.common.utils.molang.runtime.Query
import ru.hollowhorizon.hollowengine.common.utils.molang.runtime.Variables
import java.beans.Introspector
import java.beans.PropertyDescriptor
import java.lang.reflect.Modifier
import java.util.concurrent.atomic.AtomicInteger
import kotlin.reflect.KClass
import kotlin.reflect.full.memberProperties
import kotlin.reflect.jvm.javaGetter

fun FloatExpr.eval(context: MolangContext) = getFloat(context.query, context.variables)

@Serializable(FloatExprSerializer::class)
fun interface FloatExpr {
    fun getFloat(query: Query, variables: Variables): Float

    companion object {
        fun literal(value: Float): FloatExpr = FloatExpr { _, _ -> value }
        val ZERO = FloatExpr { _, _ -> 0f }
        val ONE = FloatExpr { _, _ -> 1f }
    }
}

@Serializable(FloatVec3ExprSerializer::class)
class FloatVec3Expr(val x: FloatExpr, val y: FloatExpr, val z: FloatExpr) {
    fun eval(context: MolangContext) = Vec3f(x.eval(context), y.eval(context), z.eval(context))

    companion object {
        val ZERO = FloatVec3Expr(FloatExpr.ZERO, FloatExpr.ZERO, FloatExpr.ZERO)
        val UNIT_X = FloatVec3Expr(FloatExpr.ONE, FloatExpr.ZERO, FloatExpr.ZERO)
        val UNIT_Y = FloatVec3Expr(FloatExpr.ZERO, FloatExpr.ONE, FloatExpr.ZERO)
        val UNIT_Z = FloatVec3Expr(FloatExpr.ZERO, FloatExpr.ZERO, FloatExpr.ONE)
    }
}

object FloatVec3ExprSerializer : KSerializer<FloatVec3Expr> {
    override val descriptor: SerialDescriptor = JsonElement.serializer().descriptor
    override fun deserialize(decoder: Decoder): FloatVec3Expr = parse((decoder as JsonDecoder).decodeJsonElement())
    override fun serialize(encoder: Encoder, value: FloatVec3Expr) = throw UnsupportedOperationException()

    private fun parse(json: JsonElement): FloatVec3Expr = when (json) {
        is JsonArray -> {
            val first = (json[0] as JsonPrimitive).parseMolangExpression()
            val second = (json.getOrNull(1) as JsonPrimitive?)?.parseMolangExpression() ?: first
            val third = (json.getOrNull(2) as JsonPrimitive?)?.parseMolangExpression() ?: second
            FloatVec3Expr(first, second, third)
        }
        is JsonPrimitive -> when (json.content) {
            "x" -> FloatVec3Expr.UNIT_X
            "y" -> FloatVec3Expr.UNIT_Y
            "z" -> FloatVec3Expr.UNIT_Z
            else -> json.parseMolangExpression().let { FloatVec3Expr(it, it, it) }
        }
        else -> throw SerializationException("Expected array or primitive, got $json")
    }
}

fun JsonPrimitive.parseMolangExpression() = MolangCompiler.compileFloat(content)

object FloatExprSerializer: KSerializer<FloatExpr> {
    override val descriptor: SerialDescriptor = JsonElement.serializer().descriptor

    override fun deserialize(decoder: Decoder): FloatExpr = parse((decoder as JsonDecoder).decodeJsonElement())
    override fun serialize(encoder: Encoder, value: FloatExpr) =
        throw UnsupportedOperationException("Molang serialization not supported yet!")

    private fun parse(json: JsonElement): FloatExpr = (json as JsonPrimitive).parseMolangExpression()
}

fun interface BoolExpr {
    fun getBoolean(query: Query, variables: Variables): Boolean
}

object MolangCompiler {
    private class BytecodeClassLoader : ClassLoader(MolangCompiler::class.java.classLoader) {
        fun defineClass(name: String, bytecode: ByteArray): Class<*> {
            val loaded = findLoadedClass(name)
            if (loaded != null) return loaded

            try {
                return super.defineClass(name, bytecode, 0, bytecode.size)
            } catch (e: Throwable) {
                println("Error loading class $name: ${e.message}")
                throw e
            }

        }
    }

    private val classLoader = BytecodeClassLoader()

    private var generatedIndex = AtomicInteger()

    private val floatFunctions = Object2ObjectOpenHashMap<String, FloatExpr>()
    private val boolFunctions = Object2ObjectOpenHashMap<String, BoolExpr>()

    fun compileBoolean(expression: String) = boolFunctions.getOrPut(expression) {
        compile(Parser(Lexer(expression).tokenize()).parseBooleanExpr())
    }

    fun compileFloat(expression: String) = floatFunctions.getOrPut(expression) {
        compile(Parser(Lexer(expression).tokenize()).parseFloatExpr())
    }

    internal fun compile(ast: AstBoolean): BoolExpr {
        return ast as? BoolLiteral ?: codegenBoolean(ast)
    }

    internal fun compile(ast: AstFloat): FloatExpr {
        return ast as? NumberLiteral ?: codegenFloat(ast)
    }

    private fun codegenBoolean(ast: AstBoolean): BoolExpr {
        val className = "GeneratedBooleanExpr${generatedIndex.andIncrement}"
        val cw = ClassWriter(ClassWriter.COMPUTE_FRAMES or ClassWriter.COMPUTE_MAXS)
        cw.visit(
            V17,
            ACC_PUBLIC or ACC_FINAL,
            className,
            null,
            "java/lang/Object",
            arrayOf("ru/hollowhorizon/hollowengine/common/utils/molang/compiler/BoolExpr")
        )
        generateCtor(cw)
        val mv = cw.visitMethod(
            ACC_PUBLIC,
            "getBoolean",
            "(${QUERY.descriptor}${VARIABLES.descriptor})Z",
            null,
            null
        )
        mv.visitCode()
        generateBooleanExpression(mv, ast)
        mv.visitInsn(IRETURN)
        mv.visitMaxs(0, 0)
        mv.visitEnd()
        cw.visitEnd()

        val bytecode = cw.toByteArray()

        val clazz = classLoader.defineClass(className, bytecode)
        val instance = clazz.getDeclaredConstructor().newInstance()
        return instance as BoolExpr
    }

    private fun codegenFloat(ast: AstFloat): FloatExpr {
        val className = "GeneratedFloatExpr${generatedIndex.andIncrement}"
        val cw = ClassWriter(ClassWriter.COMPUTE_FRAMES or ClassWriter.COMPUTE_MAXS)
        cw.visit(
            V17,
            ACC_PUBLIC or ACC_FINAL,
            className,
            null,
            "java/lang/Object",
            arrayOf("ru/hollowhorizon/hollowengine/common/utils/molang/compiler/FloatExpr")
        )
        generateCtor(cw)
        val mv = cw.visitMethod(
            ACC_PUBLIC,
            "getFloat",
            "(${QUERY.descriptor}${VARIABLES.descriptor})F",
            null,
            null
        )
        mv.visitCode()
        generateFloatExpression(mv, ast)
        mv.visitInsn(FRETURN)
        mv.visitMaxs(0, 0)
        mv.visitEnd()
        cw.visitEnd()

        val bytecode = cw.toByteArray()

        val clazz = classLoader.defineClass(className, bytecode)
        val instance = clazz.getDeclaredConstructor().newInstance()
        return instance as FloatExpr
    }

    private fun generateFloatExpression(mv: MethodVisitor, ast: AstFloat) {
        when (ast) {
            is NumberLiteral -> mv.visitLdcInsn(ast.value)
            is VariableAccess -> generateVariableAccess(ast, mv, false)
            is Assignment -> {
                generateFloatExpression(mv, ast.expr)
                mv.visitInsn(DUP)
                mv.visitVarInsn(ALOAD, 2) // Загружаем variables.svg
                mv.visitInsn(SWAP)
                mv.visitLdcInsn(ast.variable.path.joinToString("."))
                mv.visitInsn(SWAP)
                mv.visitMethodInsn(
                    INVOKEINTERFACE,
                    VARIABLES.internalName,
                    "set",
                    "(Ljava/lang/String;F)V",
                    true
                )
            }
            is BinaryOp -> {
                generateFloatExpression(mv, ast.left)
                generateFloatExpression(mv, ast.right)
                val opcode = when (ast.op) {
                    Token.Type.ADD -> FADD
                    Token.Type.SUB -> FSUB
                    Token.Type.MUL -> FMUL
                    Token.Type.DIV -> FDIV
                    Token.Type.MOD -> FREM
                    else -> error("Unsupported binary operation: ${ast.op}")
                }
                mv.visitInsn(opcode)
            }

            is Conditional -> {
                val elseLabel = Label()
                val endLabel = Label()
                generateBooleanExpression(mv, ast.condition)
                mv.visitJumpInsn(IFEQ, elseLabel)
                generateFloatExpression(mv, ast.thenBranch)
                mv.visitJumpInsn(GOTO, endLabel)
                mv.visitLabel(elseLabel)
                generateFloatExpression(mv, ast.elseBranch)
                mv.visitLabel(endLabel)
            }

            is FunctionCall -> {
                val descriptor = MolangFunctions.resolve(ast.name, ast.args.size)

                ast.args.forEach {
                    generateFloatExpression(mv, it)
                }

                val opcode =
                    if (descriptor.className.startsWith("java/") || descriptor.isStatic) INVOKESTATIC
                    else INVOKEVIRTUAL

                mv.visitMethodInsn(
                    opcode,
                    descriptor.className,
                    descriptor.methodName,
                    Type.getMethodDescriptor(
                        Type.getType(descriptor.returnType),
                        *(0..<descriptor.argCount).map { Type.getType(Float::class.java) }.toTypedArray()
                    ),
                    false
                )
            }
        }
    }

    private fun generateVariableAccess(
        ast: VariableAccess,
        mv: MethodVisitor,
        isBoolean: Boolean,
    ) {
        val path = ast.path
        if (path.isNotEmpty() && (path[0] == "q" || path[0] == "query")) {
            mv.visitVarInsn(ALOAD, 1)
            var currentClass: KClass<*> = Query::class

            for (i in 1 until path.size) {
                val propName = path[i]
                val property = currentClass.memberProperties.find { it.name == propName }
                    ?: error("Property $propName not found in ${currentClass.simpleName}!")

                val getter = property.javaGetter
                    ?: error("Property '$propName' has no getter in ${currentClass.simpleName}")
                val declaringClass = getter.declaringClass


                mv.visitMethodInsn(
                    when {
                        declaringClass.isInterface -> INVOKEINTERFACE
                        Modifier.isStatic(getter.modifiers) -> INVOKESTATIC
                        else -> INVOKEVIRTUAL
                    },
                    Type.getInternalName(getter.declaringClass),
                    getter.name,
                    Type.getMethodDescriptor(getter),
                    declaringClass.isInterface
                )

                currentClass = property.returnType.classifier as KClass<*>
            }

            if (currentClass == Boolean::class) {
                if (!isBoolean) convertBooleanToFloat(mv)
            } else if (currentClass == Float::class) {
                if (isBoolean) convertFloatToBoolean(mv)
            } else if (currentClass != Float::class) {
                error("Unsupported return type: $currentClass")
            }
        } else {
            mv.visitVarInsn(ALOAD, 2)
            mv.visitLdcInsn(ast.path.joinToString("."))
            mv.visitMethodInsn(
                INVOKEINTERFACE,
                VARIABLES.internalName,
                "get",
                "(Ljava/lang/String;)F",
                true
            )

            if (isBoolean) {
                convertFloatToBoolean(mv)
            }
        }
    }

    private fun convertBooleanToFloat(mv: MethodVisitor) {
        val trueLabel = Label()
        val endLabel = Label()
        mv.visitJumpInsn(IFNE, trueLabel)
        mv.visitInsn(FCONST_0)
        mv.visitJumpInsn(GOTO, endLabel)
        mv.visitLabel(trueLabel)
        mv.visitInsn(FCONST_1)
        mv.visitLabel(endLabel)
    }

    private fun convertFloatToBoolean(mv: MethodVisitor) {
        val trueLabel = Label()
        val endLabel = Label()

        mv.visitInsn(FCONST_0)
        mv.visitInsn(FCMPL)
        mv.visitJumpInsn(IFNE, trueLabel)
        mv.visitInsn(ICONST_0)
        mv.visitJumpInsn(GOTO, endLabel)
        mv.visitLabel(trueLabel)
        mv.visitInsn(ICONST_1)
        mv.visitLabel(endLabel)
    }

    fun findPropertyRecursive(name: String, clazz: Class<*>): PropertyDescriptor? {
        for (pd in Introspector.getBeanInfo(clazz).propertyDescriptors) {
            if (pd.name == name) return pd
        }
        return null
    }

    private fun generateBooleanExpression(mv: MethodVisitor, ast: AstBoolean) {
        when (ast) {
            is BoolLiteral -> mv.visitInsn(if (ast.value) ICONST_1 else ICONST_0)
            is VariableAccess -> generateVariableAccess(ast, mv, true)
            is CompareOp -> {
                generateFloatExpression(mv, ast.left)
                generateFloatExpression(mv, ast.right)
                val trueLabel = Label()
                val endLabel = Label()

                when (ast.op) {
                    Token.Type.EQ -> {
                        mv.visitInsn(FCMPL); mv.visitJumpInsn(IFEQ, trueLabel)
                    }

                    Token.Type.NEQ -> {
                        mv.visitInsn(FCMPL); mv.visitJumpInsn(IFNE, trueLabel)
                    }

                    Token.Type.LT -> {
                        mv.visitInsn(FCMPL); mv.visitJumpInsn(IFLT, trueLabel)
                    }

                    Token.Type.GT -> {
                        mv.visitInsn(FCMPL); mv.visitJumpInsn(IFGT, trueLabel)
                    }

                    Token.Type.LTE -> {
                        mv.visitInsn(FCMPL); mv.visitJumpInsn(IFLE, trueLabel)
                    }

                    Token.Type.GTE -> {
                        mv.visitInsn(FCMPL); mv.visitJumpInsn(IFGE, trueLabel)
                    }

                    else -> error("Unsupported comparison operator: ${ast.op}")
                }

                mv.visitInsn(ICONST_0)
                mv.visitJumpInsn(GOTO, endLabel)
                mv.visitLabel(trueLabel)
                mv.visitInsn(ICONST_1)
                mv.visitLabel(endLabel)
            }

            is LogicalOp -> {
                when (ast.op) {
                    Token.Type.AND -> {
                        val shortCircuitLabel = Label()
                        val endLabel = Label()
                        generateBooleanExpression(mv, ast.left)
                        mv.visitInsn(DUP)
                        mv.visitJumpInsn(IFEQ, shortCircuitLabel)
                        mv.visitInsn(POP)
                        generateBooleanExpression(mv, ast.right)
                        mv.visitJumpInsn(GOTO, endLabel)
                        mv.visitLabel(shortCircuitLabel)
                        mv.visitLabel(endLabel)
                    }

                    Token.Type.OR -> {
                        val shortCircuitLabel = Label()
                        val endLabel = Label()
                        generateBooleanExpression(mv, ast.left)
                        mv.visitInsn(DUP)
                        mv.visitJumpInsn(IFNE, shortCircuitLabel)
                        mv.visitInsn(POP)
                        generateBooleanExpression(mv, ast.right)
                        mv.visitJumpInsn(GOTO, endLabel)
                        mv.visitLabel(shortCircuitLabel)
                        mv.visitLabel(endLabel)
                    }

                    else -> error("Unsupported logical operator: ${ast.op}")
                }
            }

            is NotOp -> {
                val trueLabel = Label()
                val endLabel = Label()
                generateBooleanExpression(mv, ast.expr)
                mv.visitJumpInsn(IFEQ, trueLabel)
                mv.visitInsn(ICONST_0)
                mv.visitJumpInsn(GOTO, endLabel)
                mv.visitLabel(trueLabel)
                mv.visitInsn(ICONST_1)
                mv.visitLabel(endLabel)
            }

            is FloatToBool -> {
                generateFloatExpression(mv, ast.expr)
                convertFloatToBoolean(mv)
            }
        }
    }

    private fun generateCtor(cw: ClassWriter) {
        val ctor = cw.visitMethod(ACC_PUBLIC, "<init>", "()V", null, null)
        ctor.visitCode()
        ctor.visitVarInsn(ALOAD, 0)
        ctor.visitMethodInsn(INVOKESPECIAL, "java/lang/Object", "<init>", "()V", false)
        ctor.visitInsn(RETURN)
        ctor.visitMaxs(0, 0)
        ctor.visitEnd()
    }

    private val QUERY = Type.getType(Query::class.java)
    private val VARIABLES = Type.getType(Variables::class.java)
}

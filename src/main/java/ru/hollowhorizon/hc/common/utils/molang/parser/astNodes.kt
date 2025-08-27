package ru.hollowhorizon.hc.common.utils.molang.parser

import ru.hollowhorizon.hc.common.utils.molang.compiler.BoolExpr
import ru.hollowhorizon.hc.common.utils.molang.compiler.FloatExpr
import ru.hollowhorizon.hc.common.utils.molang.compiler.MolangFunctions
import ru.hollowhorizon.hc.common.utils.molang.lexer.Token
import ru.hollowhorizon.hc.common.utils.molang.runtime.Query
import ru.hollowhorizon.hc.common.utils.molang.runtime.Variables

sealed interface AstNode
sealed interface AstFloat : AstNode, FloatExpr
sealed interface AstBoolean : AstNode, BoolExpr

data class NumberLiteral(val value: Float) : AstFloat {
    override fun getFloat(query: Query, variables: Variables): Float = value
}

data class VariableAccess(val path: List<String>) : AstFloat, AstBoolean {
    private val base = path[0]

    // При вычислении до завершения компиляции нормально получить доступ к переменной без рефлексии нельзя

    override fun getFloat(query: Query, variables: Variables): Float {
        return if (base == "q" || base == "query") 0f
        else variables[path.joinToString(".")]
    }

    override fun getBoolean(query: Query, variables: Variables): Boolean {
        return if (base == "q" || base == "query") false
        else variables[path.joinToString(".")] != 0f
    }
}

data class Assignment(val variable: VariableAccess, val expr: AstFloat) : AstFloat {
    override fun getFloat(query: Query, variables: Variables): Float {
        val value = expr.getFloat(query, variables)
        variables[variable.path.joinToString(".")] = value
        return value
    }
}

data class BinaryOp(val left: AstFloat, val op: Token.Type, val right: AstFloat) : AstFloat {
    val action: (Query, Variables) -> Float = when (op) {
        Token.Type.ADD -> { query, variables ->
            left.getFloat(query, variables) + right.getFloat(query, variables)
        }

        Token.Type.SUB -> { query, variables ->
            left.getFloat(query, variables) - right.getFloat(query, variables)
        }

        Token.Type.MUL -> { query, variables ->
            left.getFloat(query, variables) * right.getFloat(query, variables)
        }

        Token.Type.DIV -> { query, variables ->
            left.getFloat(query, variables) / right.getFloat(query, variables)
        }

        Token.Type.MOD -> { query, variables ->
            left.getFloat(query, variables) % right.getFloat(query, variables)
        }

        else -> error("Unsupported operation $op")
    }

    override fun getFloat(query: Query, variables: Variables): Float {
        return action(query, variables)
    }

}

data class FunctionCall(val name: String, val args: List<AstFloat>) : AstFloat {
    val function: (Query, Variables) -> Float by lazy {
        val function = MolangFunctions.resolve(name, args.size)
        val method = Class.forName(function.className.replace('/', '.'))
            .declaredMethods.find { it.name == function.methodName && it.parameters.size == function.argCount }
            ?: error("Function ${function.className}::${function.methodName} with ${function.argCount} parameters not found!")

        return@lazy { query, variables ->
            method.invoke(null, *args.map { it.getFloat(query, variables) }.toTypedArray()) as Float
        }
    }

    override fun getFloat(query: Query, variables: Variables): Float {
        return function(query, variables)
    }
}

data class Conditional(val condition: AstBoolean, val thenBranch: AstFloat, val elseBranch: AstFloat) : AstFloat {
    override fun getFloat(query: Query, variables: Variables): Float {
        return if (condition.getBoolean(query, variables)) thenBranch.getFloat(query, variables)
        else elseBranch.getFloat(query, variables)
    }
}

data class BoolLiteral(val value: Boolean) : AstBoolean {
    override fun getBoolean(query: Query, variables: Variables): Boolean = value
}

data class CompareOp(val left: AstFloat, val op: Token.Type, val right: AstFloat) : AstBoolean {
    val action: (Query, Variables) -> Boolean = when (op) {
        Token.Type.EQ -> { query, variables ->
            left.getFloat(query, variables) == right.getFloat(query, variables)
        }

        Token.Type.NEQ -> { query, variables ->
            left.getFloat(query, variables) != right.getFloat(query, variables)
        }

        Token.Type.LT -> { query, variables ->
            left.getFloat(query, variables) < right.getFloat(query, variables)
        }

        Token.Type.GT -> { query, variables ->
            left.getFloat(query, variables) > right.getFloat(query, variables)
        }

        Token.Type.LTE -> { query, variables ->
            left.getFloat(query, variables) <= right.getFloat(query, variables)
        }

        Token.Type.GTE -> { query, variables ->
            left.getFloat(query, variables) >= right.getFloat(query, variables)
        }

        else -> error("Unsupported comparison operator: $op")
    }

    override fun getBoolean(query: Query, variables: Variables): Boolean {
        return action(query, variables)
    }
}

data class LogicalOp(val left: AstBoolean, val op: Token.Type, val right: AstBoolean) : AstBoolean {
    val action: (Query, Variables) -> Boolean = when (op) {
        Token.Type.AND -> { query, variables ->
            left.getBoolean(query, variables) && right.getBoolean(
                query,
                variables
            )
        }

        Token.Type.OR -> { query, variables -> left.getBoolean(query, variables) || right.getBoolean(query, variables) }
        else -> error("Unsupported logical operator: $op")
    }

    override fun getBoolean(query: Query, variables: Variables): Boolean {
        return action(query, variables)
    }
}

data class NotOp(val expr: AstBoolean) : AstBoolean {
    override fun getBoolean(query: Query, variables: Variables): Boolean {
        return !expr.getBoolean(query, variables)
    }
}

data class FloatToBool(val expr: AstFloat) : AstBoolean {
    override fun getBoolean(query: Query, variables: Variables): Boolean {
        return expr.getFloat(query, variables) != 0f
    }
}
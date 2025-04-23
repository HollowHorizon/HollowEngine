package ru.hollowhorizon.hollowengine.compiler;

import kotlin.metadata.KmClass;
import org.jetbrains.kotlin.ir.expressions.IrCall;

public class JvmHacks {
    public static void initializeTargetShapeFromSymbol(IrCall call) {
        call.initializeTargetShapeFromSymbol$ir_tree(false);
    }

    public static void initializeEmptyTypeArguments(IrCall call) {
        call.initializeEmptyTypeArguments$ir_tree(call.getTypeArgumentsCount());
    }

    public static <R, K> K forceCast(R original) {
        return (K) original;
    }

    public static void setFlags(KmClass kmClass, int flags) {
        kmClass.setFlags$kotlin_metadata(flags);
    }

    public static int getFlags(KmClass kmClass) {
        return kmClass.getFlags$kotlin_metadata();
    }
}

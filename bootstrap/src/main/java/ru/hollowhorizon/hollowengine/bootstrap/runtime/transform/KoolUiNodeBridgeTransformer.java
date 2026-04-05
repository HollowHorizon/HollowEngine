package ru.hollowhorizon.hollowengine.bootstrap.runtime.transform;

import org.objectweb.asm.tree.ClassNode;

final class KoolUiNodeBridgeTransformer extends AbstractAsmClassTransformer {
    private static final String BRIDGE = "ru/hollowhorizon/hollowengine/runtime/transform/kool/UiNodeAccessor";

    KoolUiNodeBridgeTransformer() {
        super("de.fabmax.kool.modules.ui2.UiNode");
    }

    @Override
    protected void transform(ClassNode classNode) {
        addInterface(classNode, BRIDGE);
        addGetterBridge(classNode, "getScopeName", "()Ljava/lang/String;", "scopeName", "Ljava/lang/String;");
    }
}

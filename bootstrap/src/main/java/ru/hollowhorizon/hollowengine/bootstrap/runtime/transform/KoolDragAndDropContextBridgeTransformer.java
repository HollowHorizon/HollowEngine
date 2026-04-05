package ru.hollowhorizon.hollowengine.bootstrap.runtime.transform;

import org.objectweb.asm.tree.ClassNode;

final class KoolDragAndDropContextBridgeTransformer extends AbstractAsmClassTransformer {
    private static final String BRIDGE = "ru/hollowhorizon/hollowengine/runtime/transform/kool/DragAndDropContextAccessor";

    KoolDragAndDropContextBridgeTransformer() {
        super("de.fabmax.kool.modules.ui2.DragAndDropContext");
    }

    @Override
    protected void transform(ClassNode classNode) {
        addInterface(classNode, BRIDGE);
        addGetterBridge(classNode, "getDragItem", "()Ljava/lang/Object;", "dragItem", "Ljava/lang/Object;");
    }
}

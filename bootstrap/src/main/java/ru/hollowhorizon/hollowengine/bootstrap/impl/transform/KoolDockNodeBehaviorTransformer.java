package ru.hollowhorizon.hollowengine.bootstrap.impl.transform;

import org.objectweb.asm.Opcodes;
import org.objectweb.asm.tree.AbstractInsnNode;
import org.objectweb.asm.tree.ClassNode;
import org.objectweb.asm.tree.MethodInsnNode;
import org.objectweb.asm.tree.MethodNode;

final class KoolDockNodeBehaviorTransformer extends AbstractAsmClassTransformer {
    private static final String HOOK_OWNER = "ru/hollowhorizon/hollowengine/runtime/transform/KoolRuntimeHooks";
    private static final String DOCK_NODE = "de/fabmax/kool/modules/ui2/docking/DockNode";
    private static final String DOCKABLE = "de/fabmax/kool/modules/ui2/docking/Dockable";
    private static final String SLOT_POSITION = "de/fabmax/kool/modules/ui2/docking/DockNode$SlotPosition";

    KoolDockNodeBehaviorTransformer() {
        super("de.fabmax.kool.modules.ui2.docking.DockNode");
    }

    @Override
    protected void transform(ClassNode classNode) {
        transformReceive(classNode);
    }

    private static void transformReceive(ClassNode classNode) {
        MethodNode method = requireMethod(
                classNode,
                "receive",
                "(Lde/fabmax/kool/modules/ui2/docking/Dockable;Lde/fabmax/kool/modules/ui2/PointerEvent;Lde/fabmax/kool/modules/ui2/DragAndDropHandler;)Z"
        );

        for (AbstractInsnNode instruction = method.instructions.getFirst(); instruction != null; instruction = instruction.getNext()) {
            if (!(instruction instanceof MethodInsnNode call)) {
                continue;
            }
            if (call.getOpcode() != Opcodes.INVOKEVIRTUAL) {
                continue;
            }
            if (!DOCK_NODE.equals(call.owner) || !"insertItem".equals(call.name)
                    || !("(L" + DOCKABLE + ";L" + SLOT_POSITION + ";)V").equals(call.desc)) {
                continue;
            }

            method.instructions.set(
                    call,
                    new MethodInsnNode(
                            Opcodes.INVOKESTATIC,
                            HOOK_OWNER,
                            "receiveInsertItem",
                            "(L" + DOCK_NODE + ";L" + DOCKABLE + ";L" + SLOT_POSITION + ";)V",
                            false
                    )
            );
            return;
        }

        throw new IllegalStateException("Failed to patch DockNode.receive in " + classNode.name);
    }
}

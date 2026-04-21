package ru.hollowhorizon.hollowengine.bootstrap.impl.transform;

import org.objectweb.asm.tree.ClassNode;

final class KoolClipboardTransformer extends AbstractAsmClassTransformer {
    private static final String HOOK_OWNER = "ru/hollowhorizon/hollowengine/runtime/transform/KoolRuntimeHooks";

    KoolClipboardTransformer() {
        super("de.fabmax.kool.Clipboard");
    }

    @Override
    protected void transform(ClassNode classNode) {
        replaceWithStaticCall(
                requireMethod(classNode, "copyToClipboard", "(Ljava/lang/String;)V"),
                HOOK_OWNER,
                "copyToClipboard",
                "(Ljava/lang/String;)V"
        );
        replaceWithStaticCall(
                requireMethod(classNode, "getStringFromClipboard", "(Lkotlin/jvm/functions/Function1;)V"),
                HOOK_OWNER,
                "getStringFromClipboard",
                "(Lkotlin/jvm/functions/Function1;)V"
        );
    }
}

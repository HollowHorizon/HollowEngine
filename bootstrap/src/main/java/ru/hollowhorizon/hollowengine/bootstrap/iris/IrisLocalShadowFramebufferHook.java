package ru.hollowhorizon.hollowengine.bootstrap.iris;

import net.irisshaders.iris.gl.framebuffer.GlFramebuffer;
import ru.hollowhorizon.hollowengine.bootstrap.impl.BootstrapRuntimeManager;

public final class IrisLocalShadowFramebufferHook {
    private IrisLocalShadowFramebufferHook() {
    }

    public static void rebindIfNeeded() {
        if (!BootstrapRuntimeManager.bridge().isIrisLocalShadowPassActive()) {
            return;
        }

        Object framebuffer = BootstrapRuntimeManager.bridge().getIrisLocalShadowFramebuffer();
        if (framebuffer instanceof GlFramebuffer glFramebuffer) {
            glFramebuffer.bind();
        }
    }
}

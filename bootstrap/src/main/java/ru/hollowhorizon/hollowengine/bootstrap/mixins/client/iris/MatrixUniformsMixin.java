package ru.hollowhorizon.hollowengine.bootstrap.mixins.client.iris;

import net.irisshaders.iris.compat.dh.DHCompat;
import net.irisshaders.iris.gl.uniform.UniformHolder;
import net.irisshaders.iris.shaderpack.properties.PackDirectives;
import net.irisshaders.iris.shadows.ShadowMatrices;
import net.irisshaders.iris.shadows.ShadowRenderer;
import net.irisshaders.iris.uniforms.CapturedRenderingState;
import net.irisshaders.iris.uniforms.MatrixUniforms;
import net.minecraft.util.Mth;
import org.joml.Matrix4f;
import org.joml.Matrix4fc;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import ru.hollowhorizon.hollowengine.bootstrap.runtime.BootstrapRuntimeManager;

import java.util.function.Supplier;

import static net.irisshaders.iris.gl.uniform.UniformUpdateFrequency.PER_FRAME;

@Mixin(value = MatrixUniforms.class, remap = false)
public class MatrixUniformsMixin {
    @Inject(method = "addMatrixUniforms", at = @At("HEAD"), cancellable = true)
    private static void hollowengine$overrideShadowMatrices(UniformHolder uniforms, PackDirectives directives, CallbackInfo ci) {
        addMatrix(uniforms, "ModelView", CapturedRenderingState.INSTANCE::getGbufferModelView);
        addMatrix(uniforms, "Projection", CapturedRenderingState.INSTANCE::getGbufferProjection);
        addDHMatrix(uniforms, "Projection", DHCompat::getProjection);
        addShadowMatrix(uniforms, "ModelView", () -> {
            if (BootstrapRuntimeManager.bridge().isIrisLocalShadowPassActive()) {
                return BootstrapRuntimeManager.bridge().getIrisLocalShadowViewMatrix();
            }

            return new Matrix4f(
                    ShadowRenderer.createShadowModelView(
                            directives.getSunPathRotation(),
                            directives.getShadowDirectives().getIntervalSize(),
                            directives.getShadowDirectives().getNearPlane(),
                            directives.getShadowDirectives().getFarPlane()
                    ).last().pose()
            );
        });
        addShadowMatrix(uniforms, "Projection", () -> {
            if (BootstrapRuntimeManager.bridge().isIrisLocalShadowPassActive()) {
                return BootstrapRuntimeManager.bridge().getIrisLocalShadowProjectionMatrix();
            }

            return ShadowMatrices.createOrthoMatrix(
                    directives.getShadowDirectives().getDistance(),
                    Mth.equal(directives.getShadowDirectives().getNearPlane(), -1.0f)
                            ? -DHCompat.getRenderDistance() * 16
                            : directives.getShadowDirectives().getNearPlane(),
                    Mth.equal(directives.getShadowDirectives().getFarPlane(), -1.0f)
                            ? DHCompat.getRenderDistance() * 16
                            : directives.getShadowDirectives().getFarPlane()
            );
        });
        ci.cancel();
    }

    private static void addMatrix(UniformHolder uniforms, String name, Supplier<Matrix4fc> supplier) {
        uniforms
                .uniformMatrix(PER_FRAME, "gbuffer" + name, supplier)
                .uniformMatrix(PER_FRAME, "gbuffer" + name + "Inverse", new Inverted(supplier))
                .uniformMatrix(PER_FRAME, "gbufferPrevious" + name, new Previous(supplier));
    }

    private static void addDHMatrix(UniformHolder uniforms, String name, Supplier<Matrix4fc> supplier) {
        uniforms
                .uniformMatrix(PER_FRAME, "dh" + name, supplier)
                .uniformMatrix(PER_FRAME, "dh" + name + "Inverse", new Inverted(supplier))
                .uniformMatrix(PER_FRAME, "dhPrevious" + name, new Previous(supplier));
    }

    private static void addShadowMatrix(UniformHolder uniforms, String name, Supplier<Matrix4fc> supplier) {
        uniforms
                .uniformMatrix(PER_FRAME, "shadow" + name, supplier)
                .uniformMatrix(PER_FRAME, "shadow" + name + "Inverse", new Inverted(supplier));
    }

    private static class Inverted implements Supplier<Matrix4fc> {
        private final Supplier<Matrix4fc> parent;

        private Inverted(Supplier<Matrix4fc> parent) {
            this.parent = parent;
        }

        @Override
        public Matrix4f get() {
            return new Matrix4f(parent.get()).invert();
        }
    }

    private static class Previous implements Supplier<Matrix4fc> {
        private final Supplier<Matrix4fc> parent;
        private Matrix4f previous = new Matrix4f();

        private Previous(Supplier<Matrix4fc> parent) {
            this.parent = parent;
        }

        @Override
        public Matrix4fc get() {
            Matrix4f copy = new Matrix4f(parent.get());
            Matrix4f result = new Matrix4f(previous);
            previous = copy;
            return result;
        }
    }
}

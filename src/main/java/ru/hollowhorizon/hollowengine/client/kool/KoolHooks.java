package ru.hollowhorizon.hollowengine.client.kool;

import de.fabmax.kool.KoolContext;
import de.fabmax.kool.KoolSystem;
import de.fabmax.kool.pipeline.CullMethod;
import de.fabmax.kool.pipeline.DepthCompareOp;
import de.fabmax.kool.pipeline.backend.gl.RenderBackendGl;
import de.fabmax.kool.pipeline.backend.gl.ShaderManager;
import de.fabmax.kool.util.Time;
import de.fabmax.kool.util.TriggeredCoroutineDispatcher;
import ru.hollowhorizon.hollowengine.common.utils.UnsafeTools;
import sun.misc.Unsafe;

import java.lang.reflect.Field;
import java.lang.reflect.Modifier;

/**
 * Bypasses internal modificators with support for both static and instance fields in Kotlin objects.
 */
public class KoolHooks {
    private static final Unsafe unsafe = UnsafeTools.INSTANCE.getUNSAFE();

    // ShaderManager (Instance fields)
    private static final long boundShaderOffset;

    // Time (Object/Static fields)
    private static final Object deltaTBase;
    private static final long deltaTOffset;

    // GlRenderPass.GlState Fields (Dynamic resolution: Static vs Instance)
    private static final Object actIsWriteDepthBase;
    private static final long actIsWriteDepthOffset;

    private static final Object actDepthTestBase;
    private static final long actDepthTestOffset;

    private static final Object actCullMethodBase;
    private static final long actCullMethodOffset;

    private static final Object lineWidthBase;
    private static final long lineWidthOffset;

    static {
        try {
            // 1. ShaderManager (Class, boundShader is private instance var)
            Field field = ShaderManager.class.getDeclaredField("boundShader");
            boundShaderOffset = unsafe.objectFieldOffset(field);

            // 2. Time (Object, deltaT is likely static in bytecode)
            field = Time.class.getDeclaredField("deltaT");
            if (Modifier.isStatic(field.getModifiers())) {
                deltaTBase = unsafe.staticFieldBase(field);
                deltaTOffset = unsafe.staticFieldOffset(field);
            } else {
                // Fallback if it becomes instance field
                Field instanceField = Time.class.getDeclaredField("INSTANCE");
                deltaTBase = unsafe.getObject(unsafe.staticFieldBase(instanceField), unsafe.staticFieldOffset(instanceField));
                deltaTOffset = unsafe.objectFieldOffset(field);
            }

            // 3. GlRenderPass.GlState (Private Object)
            Class<?> glStateClass = Class.forName("de.fabmax.kool.pipeline.backend.gl.GlRenderPass$GlState");

            // Try to get the singleton instance (needed only if fields are NOT static)
            Object singletonInstance = null;
            try {
                Field instanceField = glStateClass.getDeclaredField("INSTANCE");
                singletonInstance = unsafe.getObject(unsafe.staticFieldBase(instanceField), unsafe.staticFieldOffset(instanceField));
            } catch (Exception ignored) {
                // Sometimes private objects don't expose INSTANCE easily or fields are purely static
            }

            // Resolve fields dynamically
            FieldInfo isWriteDepthInfo = resolveField(glStateClass, "actIsWriteDepth", singletonInstance);
            actIsWriteDepthBase = isWriteDepthInfo.base;
            actIsWriteDepthOffset = isWriteDepthInfo.offset;

            FieldInfo depthTestInfo = resolveField(glStateClass, "actDepthTest", singletonInstance);
            actDepthTestBase = depthTestInfo.base;
            actDepthTestOffset = depthTestInfo.offset;

            FieldInfo cullMethodInfo = resolveField(glStateClass, "actCullMethod", singletonInstance);
            actCullMethodBase = cullMethodInfo.base;
            actCullMethodOffset = cullMethodInfo.offset;

            FieldInfo lineWidthInfo = resolveField(glStateClass, "lineWidth", singletonInstance);
            lineWidthBase = lineWidthInfo.base;
            lineWidthOffset = lineWidthInfo.offset;

        } catch (Exception e) {
            throw new RuntimeException("Failed to initialize KoolHooks Unsafe access", e);
        }
    }

    // Helper class to hold resolved Unsafe coordinates
    private static class FieldInfo {
        Object base;
        long offset;

        FieldInfo(Object base, long offset) {
            this.base = base;
            this.offset = offset;
        }
    }

    // Helper method to resolve field base and offset regardless of static/instance nature
    private static FieldInfo resolveField(Class<?> clazz, String fieldName, Object instance) throws NoSuchFieldException {
        Field f = clazz.getDeclaredField(fieldName);
        if (Modifier.isStatic(f.getModifiers())) {
            return new FieldInfo(unsafe.staticFieldBase(f), unsafe.staticFieldOffset(f));
        } else {
            if (instance == null) {
                throw new IllegalStateException("Field '" + fieldName + "' is an instance field, but GlState.INSTANCE could not be retrieved.");
            }
            return new FieldInfo(instance, unsafe.objectFieldOffset(f));
        }
    }

    // --- Public Methods ---

    public static void createContext(KoolContext context) {
        KoolSystem.INSTANCE.onContextCreated$kool_core(context);
    }

    public static void executeCoroutineTasks(TriggeredCoroutineDispatcher dispatcher) {
        dispatcher.executeDispatchedTasks$kool_core();
    }

    public static void resetShaders(MCKoolContext context) {
        ShaderManager manager = context.getBackend().getShaderMgr$kool_core();
        unsafe.putObject(manager, boundShaderOffset, null);
    }

    public static KoolContext getContext(RenderBackendGl backend) {
        return backend.getCtx$kool_core();
    }

    public static void setDeltaT(float deltaT) {
        unsafe.putFloat(deltaTBase, deltaTOffset, deltaT);
    }

    public static void addGameTime(double gameTime) {
        Time.INSTANCE.setGameTime$kool_core(Time.INSTANCE.getGameTime() + gameTime);
    }

    public static void incrementFrameCount() {
        Time.INSTANCE.setFrameCount$kool_core(Time.INSTANCE.getFrameCount() + 1);
    }

    // --- GlState Accessors using pre-resolved Base/Offset ---

    public static boolean getGlStateIsWriteDepth() {
        return unsafe.getBoolean(actIsWriteDepthBase, actIsWriteDepthOffset);
    }

    public static void setGlStateIsWriteDepth(boolean value) {
        unsafe.putBoolean(actIsWriteDepthBase, actIsWriteDepthOffset, value);
    }

    public static DepthCompareOp getGlStateDepthTest() {
        return (DepthCompareOp) unsafe.getObject(actDepthTestBase, actDepthTestOffset);
    }

    public static void setGlStateDepthTest(DepthCompareOp value) {
        unsafe.putObject(actDepthTestBase, actDepthTestOffset, value);
    }

    public static CullMethod getGlStateCullMethod() {
        return (CullMethod) unsafe.getObject(actCullMethodBase, actCullMethodOffset);
    }

    public static void setGlStateCullMethod(CullMethod value) {
        unsafe.putObject(actCullMethodBase, actCullMethodOffset, value);
    }

    public static float getGlStateLineWidth() {
        return unsafe.getFloat(lineWidthBase, lineWidthOffset);
    }

    public static void setGlStateLineWidth(float value) {
        unsafe.putFloat(lineWidthBase, lineWidthOffset, value);
    }
}
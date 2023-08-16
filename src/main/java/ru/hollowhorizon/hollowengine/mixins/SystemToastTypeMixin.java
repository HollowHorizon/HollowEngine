package ru.hollowhorizon.hollowengine.mixins;

import net.minecraft.client.gui.toasts.SystemToast;
import org.spongepowered.asm.mixin.*;
import org.spongepowered.asm.mixin.gen.Invoker;

import java.util.ArrayList;
import java.util.Arrays;

@SuppressWarnings("MissingUnique")
@Mixin(SystemToast.Type.class)
@Unique
public class TypeMixin {
    @Final
    @Mutable
    @Shadow
    private static SystemToast.Type[] $VALUES;

    private static final SystemToast.Type HOLLOWENGINE_TOAST = hollowengine$addValue("HOLLOWENGINE_TOAST");

    @Invoker("<init>")
    public static SystemToast.Type hollowengine$invokeInit(String par1, int par2) {
        throw new AssertionError();
    }

    private static SystemToast.Type hollowengine$addValue(String enid) {
        ArrayList<SystemToast.Type> storyvalues = new ArrayList<>(Arrays.asList(TypeMixin.$VALUES));
        SystemToast.Type toastType = hollowengine$invokeInit(enid, storyvalues.get(storyvalues.size() -1).ordinal() + 1);
        storyvalues.add(toastType);
        TypeMixin.$VALUES = storyvalues.toArray(new SystemToast.Type[0]);
        return toastType;
    }
}

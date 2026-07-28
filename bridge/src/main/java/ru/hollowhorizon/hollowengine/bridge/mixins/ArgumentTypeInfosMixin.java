package ru.hollowhorizon.hollowengine.bridge.mixins;

import com.mojang.brigadier.arguments.ArgumentType;
import net.minecraft.commands.synchronization.ArgumentTypeInfo;
import net.minecraft.commands.synchronization.ArgumentTypeInfos;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;
import ru.hollowhorizon.hollowengine.bridge.commands.HollowArgumentTypes;

/**
 * Lets the engine's own argument types take part in command-tree serialization. Vanilla only knows the
 * types registered during its own bootstrap, so both lookups fall back to {@link HollowArgumentTypes}.
 */
@Mixin(ArgumentTypeInfos.class)
public abstract class ArgumentTypeInfosMixin {
    @Inject(method = "isClassRecognized", at = @At("HEAD"), cancellable = true)
    private static void hollowengine$isClassRecognized(Class<?> type, CallbackInfoReturnable<Boolean> callback) {
        if (HollowArgumentTypes.contains(type)) callback.setReturnValue(true);
    }

    @Inject(method = "byClass", at = @At("HEAD"), cancellable = true)
    private static void hollowengine$byClass(
            ArgumentType<?> argumentType,
            CallbackInfoReturnable<ArgumentTypeInfo<?, ?>> callback
    ) {
        ArgumentTypeInfo<?, ?> info = HollowArgumentTypes.find(argumentType.getClass());
        if (info != null) callback.setReturnValue(info);
    }
}

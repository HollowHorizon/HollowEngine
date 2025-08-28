package ru.hollowhorizon.hollowengine.mixins.compact;

import net.minecraft.world.item.crafting.RecipeManager;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import ru.hollowhorizon.hollowengine.common.compat.util.JEIHelper;

@Mixin(RecipeManager.class)
public class RecipeManagerMixin {
    @Inject(method = "<init>", at = @At("RETURN"))
    public void initInject(CallbackInfo ci) {
        JEIHelper.setRecipeManager((RecipeManager) (Object) this);
    }
}

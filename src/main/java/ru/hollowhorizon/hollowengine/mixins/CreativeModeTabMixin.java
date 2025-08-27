package ru.hollowhorizon.hollowengine.mixins;

import net.minecraft.world.item.CreativeModeTab;
import net.minecraft.world.item.ItemStack;
import org.spongepowered.asm.mixin.Final;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import ru.hollowhorizon.hollowengine.common.utils.JavaHacks;
import ru.hollowhorizon.hollowengine.common.handlers.CreativeTabHandler;
import java.util.*;

@Mixin(CreativeModeTab.class)
public class CreativeModeTabMixin {
    @Shadow
    private Collection<ItemStack> displayItems;

    @Shadow
    private Set<ItemStack> displayItemsSearchTab;

    @Shadow @Final private CreativeModeTab.DisplayItemsGenerator displayItemsGenerator;

    @Inject(method = "buildContents", at = @At("TAIL"))
    private void onUpdateContents(CreativeModeTab.ItemDisplayParameters parameters, CallbackInfo ci) {
        var mutableDisplayStacks = new LinkedHashSet<>(displayItems);
        var mutableSearchTabStacks = new LinkedHashSet<>(displayItemsSearchTab);

        mutableDisplayStacks.addAll(CreativeTabHandler.INSTANCE.getITEMS().computeIfAbsent(JavaHacks.forceCast(this), (i) -> new ArrayList<>()));
        mutableSearchTabStacks.addAll(CreativeTabHandler.INSTANCE.getITEMS().computeIfAbsent(JavaHacks.forceCast(this), (i) -> new ArrayList<>()));

        displayItems = mutableDisplayStacks;
        displayItemsSearchTab = mutableSearchTabStacks;
    }
}

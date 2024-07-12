package ru.hollowhorizon.hollowengine.mixins;

import net.minecraft.client.gui.components.WidgetSprites;
import net.minecraft.client.gui.screens.inventory.EffectRenderingInventoryScreen;
import net.minecraft.client.gui.screens.inventory.InventoryScreen;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.inventory.InventoryMenu;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import ru.hollowhorizon.hc.client.utils.ForgeKotlinKt;
import ru.hollowhorizon.hollowengine.HollowEngine;
import ru.hollowhorizon.hollowengine.client.gui.ImageTextButton;
import ru.hollowhorizon.hollowengine.client.gui.QuestsGui;

@Mixin(InventoryScreen.class)
public abstract class InventoryMixin extends EffectRenderingInventoryScreen<InventoryMenu> {
    @Unique
    private WidgetSprites hollowengine$sprites = new WidgetSprites(
            ResourceLocation.fromNamespaceAndPath(HollowEngine.MODID, "textures/gui/quests/inventory_button.png"),
            ResourceLocation.fromNamespaceAndPath(HollowEngine.MODID, "textures/gui/quests/inventory_button_hovered.png")
    );

    public InventoryMixin(InventoryMenu $$0, Inventory $$1, Component $$2) {
        super($$0, $$1, $$2);
    }

    @Inject(method = "init", at = @At("TAIL"))
    private void onInit(CallbackInfo ci) {
        this.addRenderableWidget(new ImageTextButton(this.leftPos + 131, this.topPos + 58, 22, 24, hollowengine$sprites, button -> {
            ForgeKotlinKt.open(new QuestsGui());
        }));
    }
}

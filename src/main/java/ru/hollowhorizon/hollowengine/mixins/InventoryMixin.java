package ru.hollowhorizon.hollowengine.mixins;

import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.screens.inventory.EffectRenderingInventoryScreen;
import net.minecraft.client.gui.screens.inventory.InventoryScreen;
import net.minecraft.network.chat.Component;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.inventory.InventoryMenu;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import ru.hollowhorizon.hollowengine.client.gui.ImageTextButton;

@Mixin(InventoryScreen.class)
public abstract class InventoryMixin extends EffectRenderingInventoryScreen<InventoryMenu> {
    @Unique
    private ImageTextButton hollowengine$button;

    public InventoryMixin(InventoryMenu $$0, Inventory $$1, Component $$2) {
        super($$0, $$1, $$2);
    }

    @Inject(method = "init", at = @At("TAIL"))
    private void onInit(CallbackInfo ci) {
        hollowengine$button = this.addRenderableWidget(new ImageTextButton(
                this.leftPos + 131,
                this.topPos + 58,
                22, 24,
                "hollowengine:textures/gui/quests/inventory_button.png",
                "hollowengine:textures/gui/quests/inventory_button_hovered.png",
                () -> {
                }));
    }

    @Inject(method = "render", at = @At(value = "TAIL"))
    private void onResize(GuiGraphics guiGraphics, int mouseX, int mouseY, float partialTick, CallbackInfo ci) {
        hollowengine$button.setPosition(this.leftPos + 131, this.topPos + 58);
    }
}

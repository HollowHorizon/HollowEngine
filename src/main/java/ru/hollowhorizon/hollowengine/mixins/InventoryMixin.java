package ru.hollowhorizon.hollowengine.mixins;

import net.minecraft.client.gui.components.Button;
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
import ru.hollowhorizon.hc.client.utils.ForgeKotlinKt;
import ru.hollowhorizon.hollowengine.client.gui.ImageTextButton;
import ru.hollowhorizon.hollowengine.client.gui.QuestsListGui;

@Mixin(InventoryScreen.class)
public abstract class InventoryMixin extends EffectRenderingInventoryScreen<InventoryMenu> {

    public InventoryMixin(InventoryMenu $$0, Inventory $$1, Component $$2) {
        super($$0, $$1, $$2);
    }

    @Inject(method = "init", at = @At("TAIL"))
    private void onInit(CallbackInfo ci) {
        this.addRenderableWidget(new ImageTextButton(
                this.leftPos + 131,
                this.topPos + 58,
                22, 24,
                "hollowengine:textures/gui/quests/inventory_button.png",
                "hollowengine:textures/gui/quests/inventory_button_hovered.png",
                () -> ForgeKotlinKt.open(new QuestsListGui())));
    }

    @Inject(method = "method_19891", at = @At(value = "INVOKE",
            //? if >=1.20.1 {
            target = "Lnet/minecraft/client/gui/components/Button;setPosition(II)V"
            //?} else {
            /*target = "Lnet/minecraft/client/gui/components/ImageButton;setPosition(II)V"
            *///?}
    ))
    private void onResize(Button button, CallbackInfo ci) {
        //? if >=1.20.1 {
        button.setPosition(this.leftPos + 131, this.topPos + 58);
        //?} else {
        /*hollowengine$questsButton.x = this.leftPos + 131;
        hollowengine$questsButton.y = this.topPos + 58;
         *///?}
    }
}

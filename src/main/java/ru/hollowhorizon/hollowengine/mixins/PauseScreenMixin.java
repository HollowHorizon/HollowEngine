package ru.hollowhorizon.hollowengine.mixins;

import kotlin.Unit;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.screens.PauseScreen;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.commands.Commands;
import net.minecraft.network.chat.Component;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import ru.hollowhorizon.hollowengine.client.gui.HollowEngineGui;
import ru.hollowhorizon.hollowengine.client.gui.scripting.IDEGui;
import ru.hollowhorizon.hollowengine.client.gui.scripting.ScaleableButton;

@Mixin(PauseScreen.class)
public class PauseScreenMixin extends Screen {
    protected PauseScreenMixin(Component pTitle) {
        super(pTitle);
    }

    @Inject(method = "init", at = @At("TAIL"))
    private void onInit(CallbackInfo ci) {
        var player = Minecraft.getInstance().player;

        if (player == null) return;

        if (player.hasPermissions(Commands.LEVEL_GAMEMASTERS)) {
            addRenderableWidget(new ScaleableButton(5, 5, 20, 20, "hollowengine:textures/gui/hollowengine.png", "HollowEngine: Scripting", button -> {
                Minecraft.getInstance().setScreen(HollowEngineGui.INSTANCE);
                return Unit.INSTANCE;
            }));
        }
    }
}

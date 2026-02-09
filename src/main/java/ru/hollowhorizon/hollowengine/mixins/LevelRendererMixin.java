package ru.hollowhorizon.hollowengine.mixins;

import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.client.renderer.LevelRenderer;
import org.jetbrains.annotations.Nullable;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.Constant;
import org.spongepowered.asm.mixin.injection.ModifyConstant;
import ru.hollowhorizon.hollowengine.common.events.EventBus;
import ru.hollowhorizon.hollowengine.common.events.client.render.SkyRenderEvent;

@Mixin(value = LevelRenderer.class)
public class LevelRendererMixin {

    @Shadow @Nullable private ClientLevel level;

    @ModifyConstant(
            method = "renderSky",
            constant = @Constant(floatValue = 30.0F)
    )
    private float changeSunSize(float original) {
        if (level != null) {
            var event = new SkyRenderEvent.SunSize(level, original);
            EventBus.post(event);
            return event.getSunSize();
        }
        return original;
    }

    @ModifyConstant(
            method = "renderSky",
            constant = @Constant(floatValue = 20.0F)
    )
    private float changeMoonSize(float original) {
        if (level != null) {
            var event = new SkyRenderEvent.MoonSize(level, original);
            EventBus.post(event);
            return event.getMoonSize();
        }
        return original;
    }
}

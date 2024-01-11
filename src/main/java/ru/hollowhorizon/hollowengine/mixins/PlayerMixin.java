package ru.hollowhorizon.hollowengine.mixins;

import net.minecraft.world.entity.player.Player;
import org.spongepowered.asm.mixin.Mixin;
import ru.hollowhorizon.hc.client.models.gltf.manager.IAnimated;

@Mixin(Player.class)
public class PlayerMixin implements IAnimated {
}

package ru.hollowhorizon.hollowengine.bootstrap.fabric;

import net.fabricmc.fabric.api.entity.FakePlayer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import org.jetbrains.annotations.NotNull;
import ru.hollowhorizon.hollowengine.api.extensions.FakePlayerFactory;

public class FabricFakePlayerFactory implements FakePlayerFactory {
    @Override
    public @NotNull ServerPlayer create(@NotNull ServerLevel level) {
        return FakePlayer.get(level);
    }
}

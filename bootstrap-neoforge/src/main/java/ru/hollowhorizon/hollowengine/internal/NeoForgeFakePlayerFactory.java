package ru.hollowhorizon.hollowengine.internal;

import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import org.jetbrains.annotations.NotNull;
import ru.hollowhorizon.hollowengine.api.extensions.FakePlayerFactory;

public class NeoForgeFakePlayerFactory implements FakePlayerFactory {
    @Override
    public @NotNull ServerPlayer create(@NotNull ServerLevel level) {
        return net.neoforged.neoforge.common.util.FakePlayerFactory.getMinecraft(level);
    }
}

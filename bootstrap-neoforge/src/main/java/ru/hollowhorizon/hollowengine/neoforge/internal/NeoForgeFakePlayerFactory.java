package ru.hollowhorizon.hollowengine.neoforge.internal;

import com.mojang.authlib.GameProfile;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import org.jetbrains.annotations.NotNull;
import ru.hollowhorizon.hollowengine.api.extensions.FakePlayerFactory;

import static net.neoforged.neoforge.common.util.FakePlayerFactory.get;

public class NeoForgeFakePlayerFactory implements FakePlayerFactory {
    @Override
    public @NotNull ServerPlayer create(@NotNull ServerLevel level, @NotNull GameProfile profile) {
        return get(level, profile);
    }
}

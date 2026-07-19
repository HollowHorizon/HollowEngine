package ru.hollowhorizon.hollowengine.api.extensions;

import com.mojang.authlib.GameProfile;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;

public interface FakePlayerFactory {
    ServerPlayer create(ServerLevel level, GameProfile profile);
}

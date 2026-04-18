package ru.hollowhorizon.hollowengine.api.extensions;

import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;

public interface FakePlayerFactory {
    ServerPlayer create(ServerLevel level);
}

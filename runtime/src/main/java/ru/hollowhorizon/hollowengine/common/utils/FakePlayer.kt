package ru.hollowhorizon.hollowengine.common.utils

import com.mojang.authlib.GameProfile
import net.minecraft.server.level.ServerLevel
import net.minecraft.server.level.ServerPlayer
import ru.hollowhorizon.hollowengine.api.extensions.FakePlayerFactory

object FakePlayer {
    private lateinit var factory: FakePlayerFactory

    fun create(level: ServerLevel, profile: GameProfile): ServerPlayer {
        return factory.create(level, profile)
    }

    fun init(factory: FakePlayerFactory) {
        this.factory = factory
    }
}

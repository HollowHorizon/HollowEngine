package ru.hollowhorizon.hollowengine.common.utils

import net.minecraft.server.level.ServerLevel
import net.minecraft.server.level.ServerPlayer
import ru.hollowhorizon.hollowengine.api.extensions.FakePlayerFactory

object FakePlayer {
    private lateinit var factory: FakePlayerFactory

    fun create(level: ServerLevel): ServerPlayer {
        return factory.create(level)
    }

    fun init(factory: FakePlayerFactory) {
        this.factory = factory
    }
}
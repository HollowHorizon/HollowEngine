package ru.hollowhorizon.hollowengine.client

import net.minecraft.locale.Language

val String.translate: String
    get() = Language.getInstance().getOrDefault(this)
package ru.hollowhorizon.hollowengine.client.utils

import net.minecraft.locale.Language

val String.lang: String get() = Language.getInstance().getOrDefault(this)
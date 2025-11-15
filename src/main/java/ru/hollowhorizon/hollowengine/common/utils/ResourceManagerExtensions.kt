package ru.hollowhorizon.hollowengine.common.utils

import net.minecraft.resources.ResourceLocation
import net.minecraft.server.packs.resources.ResourceManager

fun ResourceManager.walk(path: String, suffix: String = ""): Set<ResourceLocation> =
    this.listResources(path) { location ->
        // Если суффикс пуст, принимаем все.
        // Иначе проверяем, оканчивается ли путь ресурса на суффикс.
        suffix.isEmpty() || location.path.endsWith(suffix)
    }.keys
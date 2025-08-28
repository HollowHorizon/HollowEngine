package ru.hollowhorizon.hollowengine.common.components.registry

import ru.hollowhorizon.hollowengine.common.components.Component
import ru.hollowhorizon.hollowengine.common.registry.system.RegistryManager
import ru.hollowhorizon.hollowengine.common.utils.rl

val ComponentRegistry = RegistryManager.create<() -> Component<*>>("hollowengine:components".rl)
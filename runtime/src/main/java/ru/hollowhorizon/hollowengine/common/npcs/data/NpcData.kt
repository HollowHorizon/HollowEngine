package ru.hollowhorizon.hollowengine.common.npcs.data

import ru.hollowhorizon.hollowengine.common.data.NbtDataStore

/**
 * The typed NBT key/store system now lives in [ru.hollowhorizon.hollowengine.common.data]. This
 * alias keeps the `NpcDataStore` name (used by [ru.hollowhorizon.hollowengine.common.entities.NpcEntity]
 * and NPC scripts) pointing at the shared store, so existing code does not have to change. New code
 * should prefer `common.data.NbtDataStore` and `common.data.DataKey` directly.
 */
typealias NpcDataStore = NbtDataStore

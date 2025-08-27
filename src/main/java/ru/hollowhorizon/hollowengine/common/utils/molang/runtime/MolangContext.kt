package ru.hollowhorizon.hollowengine.common.utils.molang.runtime

import ru.hollowhorizon.hollowengine.api.ICapabilityDispatcher

data class MolangContext(val query: Query, val variables: Variables) {
    constructor(provider: ICapabilityDispatcher) : this(provider.createQuery(), VariablesMap())
}
package ru.hollowhorizon.hc.common.utils.molang.runtime

import ru.hollowhorizon.hc.api.ICapabilityDispatcher

data class MolangContext(val query: Query, val variables: Variables) {
    constructor(provider: ICapabilityDispatcher) : this(provider.createQuery(), VariablesMap())
}
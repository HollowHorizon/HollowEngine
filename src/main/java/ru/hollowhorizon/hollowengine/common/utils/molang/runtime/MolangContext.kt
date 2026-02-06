package ru.hollowhorizon.hollowengine.common.utils.molang.runtime

import ru.hollowhorizon.hollowengine.common.components.ComponentDispatcher


data class MolangContext(val query: Query, val variables: Variables = VariablesMap()) {
    constructor(provider: ComponentDispatcher) : this(provider.createQuery(), VariablesMap())

    companion object {
        val EMPTY = MolangContext(Query.EMPTY)
    }
}
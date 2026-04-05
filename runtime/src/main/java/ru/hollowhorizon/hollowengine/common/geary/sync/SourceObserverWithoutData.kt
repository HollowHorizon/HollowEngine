package ru.hollowhorizon.hollowengine.common.geary.sync

import com.mineinabyss.geary.datatypes.*
import com.mineinabyss.geary.datatypes.family.family
import com.mineinabyss.geary.engine.ComponentProvider
import com.mineinabyss.geary.engine.id
import com.mineinabyss.geary.helpers.componentId
import com.mineinabyss.geary.modules.Geary
import com.mineinabyss.geary.observers.Observer
import com.mineinabyss.geary.observers.builders.ExecutableObserver
import com.mineinabyss.geary.systems.query.Query
import com.mineinabyss.geary.systems.query.QueryShorthands
import com.mineinabyss.geary.systems.query.ShorthandQuery

data class SourceObserverWithoutData(
    override val listenToEvents: List<ComponentId>,
    override val world: Geary,
    override val onBuild: (Observer) -> Unit,
) : SourceObserverEventsBuilder<ContextWithSource>() {
    override val mustHoldData: Boolean = false
    inline fun <reified R> or() = copy(listenToEvents = listenToEvents + comp.id<R>())

    private val context = object : ContextWithSource {
        override var entity: Entity = world.NO_ENTITY
        override var source: ComponentId = 0uL
    }

    override fun provideContext(entity: EntityId, data: Any?, source: ComponentId): ContextWithSource {
        context.entity = GearyEntity(entity, world)
        context.source = source
        return context
    }
}

data class SourceObserverWithData<R>(
    override val listenToEvents: List<ComponentId>,
    override val world: Geary,
    override val onBuild: (Observer) -> Unit,
) : SourceObserverEventsBuilder<ContextWithDataAndSource<R>>() {
    override val mustHoldData: Boolean = true

    private val context = object : ContextWithDataAndSource<R> {
        var data: R? = null
        override val event: R get() = data!!
        override var entity: Entity = world.NO_ENTITY
        override var source: ComponentId = 0uL
    }

    override fun provideContext(entity: EntityId, data: Any?, source: ComponentId): ContextWithDataAndSource<R> {
        context.entity = GearyEntity(entity, world)
        context.data = data as R
        context.source = source
        return context
    }
}


abstract class SourceObserverEventsBuilder<Context> : ExecutableObserver<Context> {
    abstract val world: Geary
    abstract val listenToEvents: List<ComponentId>
    abstract val mustHoldData: Boolean
    abstract val onBuild: (Observer) -> Unit

    val comp: ComponentProvider get() = world.componentProvider

    // Ключевое изменение: добавляем source
    abstract fun provideContext(entity: EntityId, data: Any?, source: ComponentId): Context

    fun involving(components: EntityType): SourceObserverBuilder<Context> {
        return SourceObserverBuilder(comp, this, components)
    }

    inline fun <reified A : Any> involving(size1: QueryShorthands.Size1? = null): SourceObserverBuilder<Context> {
        return SourceObserverBuilder(comp, this, entityTypeOf(comp.id<A>()))
    }

    // (Остальные методы involving аналогичны, возвращают SourceObserverBuilder...)
    inline fun <reified A : Any, reified B : Any> involving(size2: QueryShorthands.Size2? = null): SourceObserverBuilder<Context> {
        return SourceObserverBuilder(comp, this, entityTypeOf(comp.id<A>(), comp.id<B>()))
    }

    fun involvingAny(): SourceObserverBuilder<Context> {
        return SourceObserverBuilder(comp, this, entityTypeOf())
    }

    override fun filter(vararg queries: Query) = involvingAny().filter(*queries)

    override fun exec(handle: Context.() -> Unit) = involvingAny().exec { handle() }

    fun <Q : ShorthandQuery> involving(involvingQuery: Q) =
        SourceQueryInvolvingObserverBuilder(
            involvingQuery,
            SourceObserverBuilder(comp, this, involvingQuery.involves, listOf(involvingQuery))
        )
}

data class SourceObserverBuilder<Context>(
    val comp: ComponentProvider,
    val events: SourceObserverEventsBuilder<Context>,
    val involvedComponents: EntityType,
    val matchQueries: List<Query> = emptyList(),
) : ExecutableObserver<Context> {

    override fun filter(vararg queries: Query): SourceObserverBuilder<Context> {
        return copy(matchQueries = matchQueries + queries.toList())
    }

    override fun exec(handle: Context.() -> Unit): Observer {
        val observer = Observer(
            matchQueries,
            family { matchQueries.forEach { add(it.buildFamily()) } },
            involvedComponents,
            GearyEntityType(events.listenToEvents),
            events.mustHoldData,
            // Ключевое изменение: захватываем source (3-й аргумент) и передаем в provideContext
            handle = { entity, data, source ->
                events.provideContext(entity, data, source ?: error("ComponentId for $entity not found!")).handle()
            }
        )
        events.onBuild(observer)
        return observer
    }
}

data class SourceQueryInvolvingObserverBuilder<Context, Q : ShorthandQuery>(
    val involvingQuery: Q,
    val inner: SourceObserverBuilder<Context>
) {
    fun exec(handle: Context.(Q) -> Unit): Observer {
        return inner.exec { handle(involvingQuery) }
    }

    fun <Q1 : Query> exec(query: Q1, handle: Context.(Q, Q1) -> Unit): Observer {
        return inner.exec { handle(involvingQuery, query) }
    }
}

inline fun <reified T : Any> Geary.observeSource(name: String? = null): SourceObserverWithoutData {
    return SourceObserverWithoutData(listOf(componentId<T>()), world = this) {
        eventRunner.addObserver(it)
    }
}

inline fun <reified T : Any> Geary.observeSourceWithData(name: String? = null): SourceObserverWithData<T> {
    return SourceObserverWithData(listOf(componentId<T>()), world = this) {
        eventRunner.addObserver(it)
    }
}
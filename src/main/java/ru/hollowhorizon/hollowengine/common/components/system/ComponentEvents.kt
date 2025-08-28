package ru.hollowhorizon.hollowengine.common.components.system

import ru.hollowhorizon.hollowengine.common.components.Component
import ru.hollowhorizon.hollowengine.common.events.Event

open class ComponentEvent(val component: Component<*>): Event {
    class Added(component: Component<*>): ComponentEvent(component)
    class Removed(component: Component<*>): ComponentEvent(component)
    class Enabled(component: Component<*>): ComponentEvent(component)
    class Disabled(component: Component<*>): ComponentEvent(component)
    class Updated(component: Component<*>): ComponentEvent(component)
}
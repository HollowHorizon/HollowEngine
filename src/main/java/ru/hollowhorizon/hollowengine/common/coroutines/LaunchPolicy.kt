package ru.hollowhorizon.hollowengine.common.coroutines

enum class LaunchPolicy {
    CANCEL_OLD,
    DROP_NEW,
    ENQUEUE,
}
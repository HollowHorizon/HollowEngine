package ru.hollowhorizon.hollowengine.common.network

@Target(AnnotationTarget.CLASS, AnnotationTarget.FUNCTION)
annotation class HollowPacketHandler(val toTarget: Direction = Direction.ANY) {
    enum class Direction { TO_CLIENT, TO_SERVER, ANY }
}

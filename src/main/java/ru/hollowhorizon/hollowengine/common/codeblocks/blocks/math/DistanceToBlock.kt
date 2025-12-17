package ru.hollowhorizon.hollowengine.common.codeblocks.blocks.math


import de.fabmax.kool.modules.ui2.AlignmentY
import de.fabmax.kool.modules.ui2.Text
import de.fabmax.kool.modules.ui2.alignY
import de.fabmax.kool.modules.ui2.textColor
import de.fabmax.kool.util.Color
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import kotlinx.serialization.Transient
import net.minecraft.world.phys.Vec3
import ru.hollowhorizon.hollowengine.client.gui.codeblocks.InputSlotScope
import ru.hollowhorizon.hollowengine.common.codeblocks.BlockContext
import ru.hollowhorizon.hollowengine.common.codeblocks.model.ExpressionBlock
import ru.hollowhorizon.hollowengine.common.codeblocks.typeOf
import kotlin.math.PI

@Serializable
@SerialName("hollowengine:math/distance_to")
class DistanceToBlock : ExpressionBlock() {
    val from by input<Vec3>("from")
    val to by input<Vec3>("to")

    @Transient
    override val expressionType = typeOf<Vec3>()

    override suspend fun BlockContext.execute() = PI

    override fun InputSlotScope.composeContent() {
        Text("Расстояние между") {
            modifier.textColor(Color.WHITE).alignY(AlignmentY.Center).bold()
        }
        InputSlot(from)
        Text("и") {
            modifier.textColor(Color.WHITE).alignY(AlignmentY.Center).bold()
        }
        InputSlot(to)
    }
}

@Serializable
@SerialName("hollowengine:math/vector_length")
class VectorLengthBlock : ExpressionBlock() {
    val vector by input<Vec3>("vector")

    @Transient
    override val expressionType = typeOf<Number>()

    override suspend fun BlockContext.execute() = vector().length()

    override fun InputSlotScope.composeContent() {
        Text("Длина вектора") {
            modifier.textColor(Color.WHITE).alignY(AlignmentY.Center).bold()
        }
        InputSlot(vector)
    }
}

@Serializable
@SerialName("hollowengine:math/normalize_vector")
class NormalizeVectorBlock : ExpressionBlock() {
    val vector by input<Vec3>("vector")

    @Transient
    override val expressionType = typeOf<Vec3>()

    override suspend fun BlockContext.execute(): Vec3 = vector().normalize()

    override fun InputSlotScope.composeContent() {
        Text("Нормализовать") {
            modifier.textColor(Color.WHITE).alignY(AlignmentY.Center).bold()
        }
        InputSlot(vector)
    }
}

@Serializable
@SerialName("hollowengine:math/vector_multiply_scalar")
class VectorMultiplyScalarBlock : ExpressionBlock() {
    val vector by input<Vec3>("vector")
    val scalar by input<Number>("scalar")

    @Transient
    override val expressionType = typeOf<Vec3>()

    override suspend fun BlockContext.execute(): Vec3 {
        val vec = vector()
        val scl = scalar().toDouble()
        return Vec3(vec.x * scl, vec.y * scl, vec.z * scl)
    }

    override fun InputSlotScope.composeContent() {
        Text("Умножить вектор") {
            modifier.textColor(Color.WHITE).alignY(AlignmentY.Center).bold()
        }
        InputSlot(vector)
        Text("на скаляр") {
            modifier.textColor(Color.WHITE).alignY(AlignmentY.Center).bold()
        }
        InputSlot(scalar)
    }
}

@Serializable
@SerialName("hollowengine:math/vector_get_x")
class VectorGetXBlock : ExpressionBlock() {
    val vector by input<Vec3>("vector")

    @Transient
    override val expressionType = typeOf<Number>()

    override suspend fun BlockContext.execute(): Double = vector().x

    override fun InputSlotScope.composeContent() {
        Text("Координата X вектора") {
            modifier.textColor(Color.WHITE).alignY(AlignmentY.Center).bold()
        }
        InputSlot(vector)
    }
}

@Serializable
@SerialName("hollowengine:math/vector_get_y")
class VectorGetYBlock : ExpressionBlock() {
    val vector by input<Vec3>("vector")

    @Transient
    override val expressionType = typeOf<Number>()

    override suspend fun BlockContext.execute(): Double = vector().y

    override fun InputSlotScope.composeContent() {
        Text("Координата Y вектора") {
            modifier.textColor(Color.WHITE).alignY(AlignmentY.Center).bold()
        }
        InputSlot(vector)
    }
}

@Serializable
@SerialName("hollowengine:math/vector_get_z")
class VectorGetZBlock : ExpressionBlock() {
    val vector by input<Vec3>("vector")

    @Transient
    override val expressionType = typeOf<Number>()

    override suspend fun BlockContext.execute(): Double = vector().z

    override fun InputSlotScope.composeContent() {
        Text("Координата Z вектора") {
            modifier.textColor(Color.WHITE).alignY(AlignmentY.Center).bold()
        }
        InputSlot(vector)
    }
}

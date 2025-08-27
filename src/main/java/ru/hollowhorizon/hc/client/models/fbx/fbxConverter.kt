package ru.hollowhorizon.hc.client.models.fbx

import com.mojang.blaze3d.platform.NativeImage
import com.mojang.blaze3d.systems.RenderSystem
import de.fabmax.kool.math.*
import de.fabmax.kool.scene.TrsTransformF
import de.fabmax.kool.util.Color
import net.minecraft.client.Minecraft
import net.minecraft.client.renderer.texture.DynamicTexture
import net.minecraft.resources.ResourceLocation
import ru.hollowhorizon.hc.HollowCore
import ru.hollowhorizon.hc.client.models.fbx.TransformationComp
import ru.hollowhorizon.hc.client.models.internal.Mesh
import ru.hollowhorizon.hc.client.models.internal.Node
import ru.hollowhorizon.hc.client.models.internal.Primitive
import ru.hollowhorizon.hc.client.models.internal.Scene
import ru.hollowhorizon.hc.client.models.internal.animations.Animation
import ru.hollowhorizon.hc.common.utils.rl
import java.io.IOException
import kotlin.math.abs
import ru.hollowhorizon.hc.client.models.fbx.FileGlobalSettings.FrameRate as Fr
import ru.hollowhorizon.hc.client.models.fbx.TransformationComp as Tc
import ru.hollowhorizon.hc.client.models.internal.Material as InternalMaterial
import ru.hollowhorizon.hc.client.models.internal.Model as InternalModel

enum class TransformationComp {
    Translation, RotationOffset, RotationPivot, PreRotation, Rotation, PostRotation,
    RotationPivotInverse, ScalingOffset, ScalingPivot, Scaling, ScalingPivotInverse, GeometricTranslation,
    GeometricRotation, GeometricScaling;

    val i = ordinal

    /** note: this returns the REAL fbx property names  */
    val nameProperty
        get() = when (this) {
            Tc.Translation -> "Lcl Translation"
            Tc.Rotation -> "Lcl Rotation"
            Tc.Scaling -> "Lcl Scaling"
            else -> toString()
        }

    /** XXX a neat way to solve the never-ending special cases for scaling would be to do everything in log space! */
    val defaultValue
        get() = when (this) {
            Scaling -> Vec3f(1f)
            else -> Vec3f(0f)
        }

    companion object {
        val MAX = entries.size
    }
}

operator fun Array<Mat4f>.get(transf: Tc) = get(transf.i)
operator fun Array<Mat4f>.set(transf: Tc, mat: Mat4f) = set(transf.i, mat)

fun Document.convert(location: ResourceLocation): InternalModel {
    return InternalModel(0, listOf(Scene(convertNodes(0L, location))), setOf()).apply {
        isBlockBench = creator.contains("blockbench", ignoreCase = true)
        if(isBlockBench) {
            scenes[scene].nodes.forEach {
                // BlockBench зачем-то скейлит модели
                it.transform.scale(0.01)
                it.baseTransform.scale(0.01)
            }
        }
    }
}

fun Document.convertNodes(parentId: Long, location: ResourceLocation): List<Node> {
    val connections = getConnectionsByDestinationSequenced(parentId, "Model")

    val nodes = ArrayList<Node>()

    for (connection in connections) {
        if (connection.prop.isNotEmpty()) continue

        val `object` = connection.sourceObject
        if (`object` == null) {
            HollowCore.LOGGER.warn("failed to convert source object for Model link")
            continue
        }

        val model = `object` as? Model

        if (model != null) {
            val nodeTransform = generateTransformationNodeChain(model)
            val node = convertModel(model, nodeTransform, location)

            node.children.addAll(convertNodes(model.id, location))

            nodes.add(node)
        }
    }

    return nodes
}

fun generateTransformationNodeChain(model: Model): TrsTransformF {

    val props = model.props
    val rot = model.rotationOrder

    val chain = Array(Tc.MAX) { Mat4f.IDENTITY }

    // generate transformation matrices for all the different transformation components
    val zeroEpsilon = 1e-6f
    var isComplex = false

    props<Vec3f>("PreRotation")?.let {
        if (it.sqrLength() > zeroEpsilon) {
            isComplex = true
            chain[Tc.PreRotation] = getRotationMatrix(rot, it)
        }
    }

    props<Vec3f>("PostRotation")?.let {
        if (it.sqrLength() > zeroEpsilon) {
            isComplex = true
            chain[Tc.PostRotation] = getRotationMatrix(rot, it)
        }
    }

    props<Vec3f>("RotationPivot")?.let {
        if (it.sqrLength() > zeroEpsilon) {
            isComplex = true
            chain[Tc.RotationPivot] = Mat4f.translation(it)
            chain[Tc.RotationPivotInverse] = Mat4f.translation(Vec3f(-it.x, -it.y, -it.z))
        }
    }

    props<Vec3f>("RotationOffset")?.let {
        if (it.sqrLength() > zeroEpsilon) {
            isComplex = true
            chain[Tc.RotationOffset] = Mat4f.translation(it)
        }
    }

    props<Vec3f>("ScalingOffset")?.let {
        if (it.sqrLength() > zeroEpsilon) {
            isComplex = true
            chain[Tc.ScalingOffset] = Mat4f.translation(it)
        }
    }

    props<Vec3f>("ScalingPivot")?.let {
        if (it.sqrLength() > zeroEpsilon) {
            isComplex = true
            chain[Tc.ScalingPivot] = Mat4f.translation(it)
            chain[Tc.ScalingPivotInverse] = Mat4f.translation(Vec3f(-it.x, -it.y, -it.z))
        }
    }

    props<Vec3f>("Lcl Translation")?.let {
        if (it.sqrLength() > zeroEpsilon)
            chain[Tc.Translation] = Mat4f.translation(it)
    }

    props<Vec3f>("Lcl Scaling")?.let {
        if (abs(it.sqrLength() - 1f) > zeroEpsilon)
            chain[Tc.Scaling] = Mat4f.scale(it)
    }

    props<Vec3f>("Lcl Rotation")?.let {
        if (it.sqrLength() > zeroEpsilon)
            chain[Tc.Rotation] = getRotationMatrix(rot, it)
    }

    props<Vec3f>("GeometricScaling")?.let {
        if (abs(it.sqrLength() - 1f) > zeroEpsilon)
            chain[Tc.GeometricScaling] = Mat4f.scale(it)
    }

    props<Vec3f>("GeometricRotation")?.let {
        if (it.sqrLength() > zeroEpsilon)
            chain[Tc.GeometricRotation] = getRotationMatrix(rot, it)
    }

    props<Vec3f>("GeometricTranslation")?.let {
        if (it.sqrLength() > zeroEpsilon)
            chain[Tc.GeometricTranslation] = Mat4f.translation(it)
    }

    /*  isComplex needs to be consistent with needsComplexTransformationChain() or the interplay between this code
        and the animation converter would not be guaranteed.    */
    assert(needsComplexTransformationChain(model) == isComplex)

    return TrsTransformF().apply {
        val transform = MutableMat4f()
        chain.forEach {
            transform *= it
        }
        setMatrix(transform)
    }
}

fun convertModel(model: Model, transform: TrsTransformF, location: ResourceLocation): Node {
    val primitives = model.geometry.mapNotNull {
        (it as? MeshGeometry)?.let { convertMesh(it, model, location) }
    }

    return Node(
        model.id.toInt(),
        mutableListOf(),
        transform,
        Mesh(primitives, floatArrayOf()),
        name = model.name.substringAfter("::")
    )
}

fun convertMesh(mesh: MeshGeometry, model: Model, location: ResourceLocation): Primitive {
    return Primitive(
        positions = mesh.vertices.toTypedArray(),
        normals = mesh.normals.toTypedArray(),
        texCoords = mesh.getTextureCoords(0).map { Vec2f(it.x, 1f-it.y) }.toTypedArray(),
        tangents = mesh.tangents.map { Vec4f(it.x, it.y, it.z, 1f) }.toTypedArray(),
        indices = mesh.indices.toIntArray(),
        material = model.materials[mesh.materials[0]].convert(
            location,
            mesh.colors.getOrNull(0)?.getOrNull(0) ?: Vec4f(1f, 1f, 1f, 1f)
        )
    )
}

fun Material.convert(model: ResourceLocation, color: Vec4f): InternalMaterial {
    var location = InternalMaterial.MISSING_TEXTURE
    textures["DiffuseColor"]?.media?.let { media ->
        location = model.withPath(model.path.substringBefore('.')+'/'+media.name.lowercase().filter(ResourceLocation::validPathChar)+".png")
        if (media.content.isNotEmpty()) {
            RenderSystem.recordRenderCall {
                try {
                    val texture = DynamicTexture(NativeImage.read(media.content))
                    Minecraft.getInstance().textureManager.register(location, texture)
                } catch (e: IOException) {
                    HollowCore.LOGGER.error("Invalid texture $location!")
                }
            }
        }
    }

    return InternalMaterial(
        color = Color(color.x, color.y, color.z, color.w),
        texture = location,
        // Судя по всему fbx такие параметры не поддерживает,
        // так что включим их по умолчанию, хоть это и хуже скажется на производительности
        blend = InternalMaterial.Blend.BLEND,
        doubleSided = true
    )
}

fun needsComplexTransformationChain(model: Model): Boolean {
    val props = model.props

    val zeroEpsilon = 1e-6f
    TransformationComp.entries.filter {
        it != Tc.Rotation && it != Tc.Scaling && it != Tc.Translation && it != Tc.GeometricScaling
                && it != Tc.GeometricRotation && it != Tc.GeometricTranslation
    }.forEach { comp -> props<Vec3f>(comp.nameProperty)?.let { if (it.sqrLength() > zeroEpsilon) return true } }
    return false
}

fun getRotationMatrix(mode: Model.RotOrder, rotation: Vec3f): Mat4f {
    val out = MutableMat4f()
    if (mode == Model.RotOrder.SphericXYZ) {
        HollowCore.LOGGER.error("Unsupported RotationMode: SphericXYZ")
        return out
    }

    val angleEpsilon = 1e-6f

    val isId = BooleanArray(3, { true })

    val temp = Array(3) { MutableMat4f() }
    if (abs(rotation.z) > angleEpsilon) {
        temp[2].rotate(rotation.z.deg, Vec3f.Z_AXIS)
        isId[2] = false
    }
    if (abs(rotation.y) > angleEpsilon) {
        temp[1].rotate(rotation.y.deg, Vec3f.Y_AXIS)
        isId[1] = false
    }
    if (abs(rotation.x) > angleEpsilon) {
        temp[0].rotate(rotation.x.deg, Vec3f.X_AXIS)
        isId[0] = false
    }

    val order = IntArray(3) { -1 }

    // note: rotation order is inverted since we're left multiplying as is usual in assimp
    when (mode) {
        Model.RotOrder.EulerXYZ -> {
            order[0] = 2
            order[1] = 1
            order[2] = 0
        }

        Model.RotOrder.EulerXZY -> {
            order[0] = 1
            order[1] = 2
            order[2] = 0
        }

        Model.RotOrder.EulerYZX -> {
            order[0] = 0
            order[1] = 2
            order[2] = 1
        }

        Model.RotOrder.EulerYXZ -> {
            order[0] = 2
            order[1] = 0
            order[2] = 1
        }

        Model.RotOrder.EulerZXY -> {
            order[0] = 1
            order[1] = 0
            order[2] = 2
        }

        Model.RotOrder.EulerZYX -> {
            order[0] = 0
            order[1] = 1
            order[2] = 2
        }

        else -> throw Exception()
    }

    assert(order[0] in 0..2)
    assert(order[1] in 0..2)
    assert(order[2] in 0..2)

    if (!isId[order[0]]) out *= temp[order[0]]
    if (!isId[order[1]]) out *= temp[order[1]]
    if (!isId[order[2]]) out *= temp[order[2]]

    return out
}


fun Document.convertAnimations() {
    val fps = globals?.timeMode ?: FileGlobalSettings.FrameRate._30
    val custom = globals?.customFrameRate ?: -1f
    val frameRate = frameRateToDouble(fps, custom.toDouble())
    animationStacks().forEach { convertAnimationStack(it) }
}

fun frameRateToDouble(fp: FileGlobalSettings.FrameRate, customFPSVal: Double = -1.0) = when (fp) {
    Fr.DEFAULT -> 1.0
    Fr._120 -> 120.0
    Fr._100 -> 100.0
    Fr._60 -> 60.0
    Fr._50 -> 50.0
    Fr._48 -> 48.0
    Fr._30, Fr._30_DROP -> 30.0
    Fr.NTSC_DROP_FRAME, Fr.NTSC_FULL_FRAME -> 29.9700262
    Fr.PAL -> 25.0
    Fr.CINEMA -> 24.0
    Fr._1000 -> 1000.0
    Fr.CINEMA_ND -> 23.976
    Fr.CUSTOM -> customFPSVal
}

fun convertAnimationStack(st: AnimationStack): Animation? {
    TODO()
}
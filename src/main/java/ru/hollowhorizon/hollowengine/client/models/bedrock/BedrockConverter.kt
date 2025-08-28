@file:OptIn(ExperimentalSerializationApi::class)

package ru.hollowhorizon.hollowengine.client.models.bedrock

import de.fabmax.kool.math.*
import de.fabmax.kool.scene.TrsTransformF
import kotlinx.serialization.ExperimentalSerializationApi
import net.minecraft.resources.ResourceLocation
import ru.hollowhorizon.hollowengine.client.models.internal.*
import ru.hollowhorizon.hollowengine.client.models.internal.manager.ModelLoader
import ru.hollowhorizon.hollowengine.client.utils.stream
import ru.hollowhorizon.hollowengine.common.utils.json.JsonFormat
import ru.hollowhorizon.hollowengine.common.utils.rl

object BedrockModelLoader : ModelLoader {

    override val supportedFormats = setOf("geo.json")

    override suspend fun load(location: ResourceLocation): AnimatedModel {
        val model = convert(JsonFormat.decodeFromStream<BedrockFile>(location.stream), location)

        val animationFile = location.withPath(location.path.substringBefore('.') + ".animation.json")
        val animations = JsonFormat.decodeFromStream<BedrockAnimationFile>(animationFile.stream)
        return AnimatedModel(model, BedrockAnimationConverter.convert(model, animations).associate { it.name to it })
    }

    fun convert(file: BedrockFile, location: ResourceLocation): Model {


        return Model(0, file.geometries.map { Scene(it.convertNodes(location)) }, setOf()).apply {
            isBlockBench = true
        }
    }

    fun BedrockFile.Geometry.convertNodes(location: ResourceLocation): List<Node> {
        val material = Material(
            description.color,
            if (description.texture.contains(':')) description.texture.rl
            else location.withPath(location.path.substringBeforeLast('/') + '/' + description.texture),
            blend = if (description.textureTranslucent) Material.Blend.BLEND else Material.Blend.OPAQUE,
            doubleSided = true
        )

        return bones.filter { it.parent == null }
            .map { convertNode(it, material).apply { transform.scale(1 / 16f); baseTransform.scale(1 / 16f) } }
    }

    fun BedrockFile.Geometry.convertNode(bone: BedrockFile.Bone, material: Material): Node {
        val transform = TrsTransformF()

        val parent = bones.find { it.name == bone.parent }?.pivot ?: Vec3f.ZERO
        val localPivot = (bone.pivot - parent) / if (bone.parent == null) 16f else 1f
        transform.translate(localPivot)
        transform.rotate(MutableQuatF().rotateByEulers(bone.rotation, EulerOrder.XYZ))

        val primitives = bone.cubes.map {
            val mesh = it.toMeshData(bone.pivot, description.textureWidth, description.textureHeight)

            Primitive(
                positions = mesh.vertices.toTypedArray(),
                normals = mesh.normals.toTypedArray(),
                texCoords = mesh.uvs.toTypedArray(),
                indices = mesh.indices.toIntArray(),
                material = material
            )
        }

        return Node(
            0,
            bones.filter { it.parent == bone.name }.map { convertNode(it, material) }.toMutableList(),
            transform,
            mesh = Mesh(primitives, floatArrayOf()),
            name = bone.name
        )
    }

    data class MeshData(
        val vertices: List<Vec3f>,
        val normals: List<Vec3f>,
        val uvs: List<Vec2f>,
        val indices: List<Int>,
    )

    fun BedrockFile.Cube.toMeshData(pivot: Vec3f, textureWidth: Int, textureHeight: Int): MeshData {
        val vertices = mutableListOf<Vec3f>()
        val normals = mutableListOf<Vec3f>()
        val uvsOut = mutableListOf<Vec2f>()
        val indices = mutableListOf<Int>()

        val (ox, oy, oz) = origin - pivot
        val (sx, sy, sz) = size
        val inf = inflate

        val x0 = ox - inf
        val y0 = oy - inf
        val z0 = oz - inf
        val x1 = ox + sx + inf
        val y1 = oy + sy + inf
        val z1 = oz + sz + inf

        val p = arrayOf(
            Vec3f(x0, y0, z0), Vec3f(x1, y0, z0), Vec3f(x1, y1, z0), Vec3f(x0, y1, z0), // front
            Vec3f(x0, y0, z1), Vec3f(x1, y0, z1), Vec3f(x1, y1, z1), Vec3f(x0, y1, z1)  // back
        )

        val faces = listOf(
            listOf(0, 1, 2, 3) to Vec3f(0f, 0f, -1f) to "north",
            listOf(5, 4, 7, 6) to Vec3f(0f, 0f, 1f) to "south",
            listOf(4, 0, 3, 7) to Vec3f(-1f, 0f, 0f) to "west",
            listOf(1, 5, 6, 2) to Vec3f(1f, 0f, 0f) to "east",
            listOf(3, 2, 6, 7) to Vec3f(0f, 1f, 0f) to "up",
            listOf(4, 5, 1, 0) to Vec3f(0f, -1f, 0f) to "down"
        )

        val uvMap: Map<String, BedrockFile.UvFace?> = when (uv) {
            is BedrockFile.Uvs.PerFace -> mapOf(
                "north" to uv.north, "south" to uv.south,
                "west" to uv.west, "east" to uv.east,
                "up" to uv.up, "down" to uv.down
            )

            is BedrockFile.Uvs.Box -> generateBoxUVs(uv.uv)
        }

        for ((faceWithNormal, name) in faces) {
            val (face, normal) = faceWithNormal
            val start = vertices.size
            val uvFace = uvMap[name]

            val uvCoords = if (uvFace != null) {
                val (u, v) = uvFace.uv
                val (w, h) = uvFace.size
                listOf(
                    Vec2f(u, v + h),
                    Vec2f(u + w, v + h),
                    Vec2f(u + w, v),
                    Vec2f(u, v)
                )
            } else {
                listOf(
                    Vec2f(0f, 1f),
                    Vec2f(1f, 1f),
                    Vec2f(1f, 0f),
                    Vec2f(0f, 0f)
                )
            }

            val indicesForFace = listOf(0, 1, 2, 2, 3, 0)

            for (i in 0..3) {
                vertices += p[face[i]]
                normals += normal
                uvsOut += if (mirror == true && (name == "west" || name == "east")) {
                    Vec2f(1f - uvCoords[i].x / textureWidth, uvCoords[i].y / textureHeight)
                } else uvCoords[i] / Vec2f(textureWidth.toFloat(), textureHeight.toFloat())
            }

            indices += indicesForFace.map { start + it }
        }

        return MeshData(vertices, normals, uvsOut, indices)
    }

    fun generateBoxUVs(boxUv: FloatArray): Map<String, BedrockFile.UvFace> {
        val (u, v) = boxUv
        val faceSize = 16f // или size.x / .y по логике

        return mapOf(
            "north" to BedrockFile.UvFace(floatArrayOf(u + faceSize, v + faceSize), floatArrayOf(faceSize, faceSize)),
            "south" to BedrockFile.UvFace(floatArrayOf(u, v + faceSize), floatArrayOf(faceSize, faceSize)),
            "west" to BedrockFile.UvFace(floatArrayOf(u, v), floatArrayOf(faceSize, faceSize)),
            "east" to BedrockFile.UvFace(floatArrayOf(u + faceSize * 2, v), floatArrayOf(faceSize, faceSize)),
            "up" to BedrockFile.UvFace(floatArrayOf(u + faceSize, v), floatArrayOf(faceSize, faceSize)),
            "down" to BedrockFile.UvFace(
                floatArrayOf(u + faceSize, v + faceSize * 2),
                floatArrayOf(faceSize, faceSize)
            ),
        )
    }
}
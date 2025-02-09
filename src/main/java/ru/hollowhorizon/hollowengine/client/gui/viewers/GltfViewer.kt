package ru.hollowhorizon.hollowengine.client.gui.viewers

import de.fabmax.kool.Assets
import de.fabmax.kool.hdriEnvironment
import de.fabmax.kool.loadImage2d
import de.fabmax.kool.math.*
import de.fabmax.kool.modules.gltf.GltfLoadConfig
import de.fabmax.kool.modules.gltf.GltfMaterialConfig
import de.fabmax.kool.modules.gltf.loadGltfModel
import de.fabmax.kool.modules.ksl.KslPbrShader
import de.fabmax.kool.pipeline.Texture2d
import de.fabmax.kool.pipeline.deferred.DeferredOutputShader
import de.fabmax.kool.pipeline.deferred.DeferredPipeline
import de.fabmax.kool.pipeline.deferred.DeferredPipelineConfig
import de.fabmax.kool.pipeline.deferred.deferredKslPbrShader
import de.fabmax.kool.pipeline.ibl.EnvironmentMap
import de.fabmax.kool.scene.*
import de.fabmax.kool.scene.geometry.MeshBuilder
import de.fabmax.kool.util.Time
import de.fabmax.kool.util.launchOnMainThread
import net.minecraft.util.Mth.cos
import net.minecraft.util.Mth.sin
import ru.hollowhorizon.hc.client.kool.KoolScreen
import kotlin.math.PI

class GltfViewer : KoolScreen({}) {
    init {
        scene.apply {
            launchOnMainThread {
                envMap = Assets.hdriEnvironment("hollowengine:textures/hdri/shanghai.png", 1f).getOrThrow()

                deferredPipeline = DeferredPipeline(scene, DeferredPipelineConfig().apply {
                    isWithAmbientOcclusion = true
                    isWithScreenSpaceReflections = true
                    baseReflectionStep = 0.02f
                    maxGlobalLights = 2
                    isWithVignette = true
                    useImageBasedLighting(envMap)
                })

                val currentModel = GltfModel(
                    "Player Model", "hollowengine:models/entity/player_model.gltf",
                    1f, Vec3f.ZERO, false, Vec3d(0.0, 0.0, 0.0), false, 1.5
                )

                orbitCamera {
                    setRotation(0f, -30f)
                    zoom = currentModel.zoom
                    translation.set(currentModel.lookAt)
                }

                addNode(Skybox.cube(envMap.reflectionMap, 1.5f))

                deferredPipeline.sceneContent.setupContentGroup(currentModel)

                // main scene only contains a quad used to draw the deferred shading output
                val contentGroupDeferred = Node().apply {
                    isFrustumChecked = false
                    val outputMesh = deferredPipeline.createDefaultOutputQuad()
                    (outputMesh.shader as? DeferredOutputShader)?.setupVignette(0f)
                    addNode(outputMesh)
                }
                addNode(contentGroupDeferred)
            }
        }
    }

    lateinit var envMap: EnvironmentMap
    lateinit var deferredPipeline: DeferredPipeline

    private suspend fun Node.setupContentGroup(currentModel: GltfModel) {
        transform.rotate((-60.0).deg, Vec3d.Y_AXIS)

        addTextureMesh(isNormalMapped = true) {
            generate {
                roundCylinder(4.1f, 0.2f)
            }

            fun KslPbrShader.Config.Builder.materialConfig() {
                color { textureColor(image("fabric_color")) }
                normalMapping { useNormalMap(image("fabric_normal")) }
                ao { textureProperty(image("fabric_ao")) }
                roughness { textureProperty(image("fabric_roughness")) }
            }

            shader = deferredKslPbrShader {
                materialConfig()
            }
        }

        addNode(currentModel.load())
    }

    private fun image(name: String) = Texture2d {
        Assets.loadImage2d("hollowengine:textures/materials/$name.jpg").getOrThrow()
    }

    private fun MeshBuilder.roundCylinder(radius: Float, height: Float) {
        val nCorner = 20
        val cornerR = height / 2
        val cornerPts = mutableListOf<Vec3f>()
        for (i in 0..nCorner) {
            val a = (PI / nCorner * i).toFloat()
            val x = sin(a) * cornerR + radius
            val y = cos(a) * cornerR - cornerR
            cornerPts += Vec3f(x, y, 0f)
        }

        val uvScale = 0.3f
        val nCyl = 100
        var firstI = 0
        for (i in 0..nCyl) {
            val a = (PI / nCyl * i * 2).toFloat()
            cornerPts.forEachIndexed { ci, cpt ->
                val uv = MutableVec2f(radius + ci.toFloat() / cornerPts.size * PI.toFloat() * cornerR, 0f)
                uv.mul(uvScale)
                uv.rotate(a.rad)
                val pt = cpt.rotate(a.rad, Vec3f.Y_AXIS, MutableVec3f())
                val iv = vertex(pt, Vec3f.ZERO, uv)
                if (i > 0 && ci > 0) {
                    geometry.addTriIndices(iv - 1, iv - cornerPts.size - 1, iv - cornerPts.size)
                    geometry.addTriIndices(iv, iv - 1, iv - cornerPts.size)
                }
                if (i == 0 && ci == 0) {
                    firstI = iv
                }
            }
        }
        val firstIBot = firstI + cornerPts.size - 1
        for (i in 2..nCyl) {
            geometry.addTriIndices(firstI, firstI + ((i - 1) * cornerPts.size), firstI + (i * cornerPts.size))
            geometry.addTriIndices(firstIBot, firstIBot + (i * cornerPts.size), firstIBot + ((i - 1) * cornerPts.size))
        }
        geometry.generateNormals()
    }

    private inner class GltfModel(
        val name: String,
        val assetPath: String,
        val scale: Float,
        val translation: Vec3f,
        val generateNormals: Boolean,
        val lookAt: Vec3d,
        val trackModel: Boolean,
        val zoom: Double,
        val normalizeBoneWeights: Boolean = false,
    ) {

        var deferredModel: Model? = null
        var isVisible: Boolean = false

        var animate: Model.(Float) -> Unit = { dt ->
            applyAnimation(dt)
        }

        override fun toString() = name

        suspend fun load(): Model {
            val materialCfg = GltfMaterialConfig(
                shadowMaps = deferredPipeline.shadowMaps,
                scrSpcAmbientOcclusionMap =
                deferredPipeline.aoPipeline?.aoMap,
                environmentMap = envMap,
                isDeferredShading = true
            )
            val modelCfg = GltfLoadConfig(
                generateNormals = generateNormals,
                materialConfig = materialCfg,
                loadAnimations = true,
                applyMorphTargets = true,
                applySkins = true,
                applyTransforms = true,
                mergeMeshesByMaterial = true
            )
            val model = Assets.loadGltfModel(assetPath, modelCfg).getOrThrow().apply {
                transform.translate(translation)
                transform.scale(scale)

                if (normalizeBoneWeights) {
                    meshes.values.forEach { mesh ->
                        mesh.geometry.forEach { v ->
                            v.weights.mul(1f / (v.weights.x + v.weights.y + v.weights.z + v.weights.w))
                        }
                    }
                }

                enableAnimation(0)
                onUpdate += {
                    isVisible = true
                    animate(Time.deltaT)
                }
            }
            deferredModel = model
            return model
        }
    }

    override fun onClose() {
        super.onClose()
        scene.release()
    }
}
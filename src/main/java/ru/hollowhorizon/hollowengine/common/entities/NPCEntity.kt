package ru.hollowhorizon.hollowengine.common.entities

import com.mojang.blaze3d.systems.RenderSystem
import de.javagl.jgltf.model.Optionals
import de.javagl.jgltf.model.TextureModel
import de.javagl.jgltf.model.image.PixelDatas
import de.javagl.jgltf.model.impl.DefaultGltfModel
import de.javagl.jgltf.model.impl.DefaultImageModel
import net.minecraft.entity.CreatureEntity
import net.minecraft.entity.EntityType
import net.minecraft.entity.ai.goal.SwimGoal
import net.minecraft.entity.player.PlayerEntity
import net.minecraft.util.ActionResultType
import net.minecraft.util.DamageSource
import net.minecraft.util.Hand
import net.minecraft.world.World
import net.minecraftforge.common.capabilities.Capability
import org.lwjgl.opengl.GL11
import org.lwjgl.opengl.GL12
import ru.hollowhorizon.hc.HollowCore
import ru.hollowhorizon.hc.client.gltf.GlTFModelManager
import ru.hollowhorizon.hc.client.gltf.IAnimatedEntity
import ru.hollowhorizon.hc.client.gltf.RenderedGltfModel
import ru.hollowhorizon.hc.client.gltf.animations.AnimationType
import ru.hollowhorizon.hc.client.utils.mcText
import ru.hollowhorizon.hc.common.capabilities.AnimatedEntityCapability
import ru.hollowhorizon.hc.common.capabilities.HollowCapabilityV2.Companion.get
import ru.hollowhorizon.hc.common.capabilities.ICapabilitySyncer
import ru.hollowhorizon.hc.common.capabilities.getCapability
import ru.hollowhorizon.hc.common.capabilities.syncEntity
import ru.hollowhorizon.hollowengine.common.capabilities.NPCEntityCapability
import ru.hollowhorizon.hollowengine.common.npcs.IHollowNPC
import ru.hollowhorizon.hollowengine.common.npcs.IconType
import ru.hollowhorizon.hollowengine.common.npcs.tasks.HollowNPCTask
import ru.hollowhorizon.hollowengine.common.registry.ModEntities

open class NPCEntity : CreatureEntity, IHollowNPC, IAnimatedEntity, ICapabilitySyncer {
    val interactionWaiter = Object()
    val goalQueue = ArrayList<HollowNPCTask>()
    val removeGoalQueue = ArrayList<HollowNPCTask>()

    constructor(level: World) : super(ModEntities.NPC_ENTITY, level)
    constructor(type: EntityType<NPCEntity>, world: World) : super(type, world)

    override fun mobInteract(pPlayer: PlayerEntity, pHand: Hand): ActionResultType {
        synchronized(interactionWaiter) { interactionWaiter.notifyAll() }
        return super.mobInteract(pPlayer, pHand)
    }

    fun setEntityIcon(type: IconType) {
        this.getCapability(
            get(
                NPCEntityCapability::class.java
            )
        ).ifPresent { cap: NPCEntityCapability ->
            cap.iconType = type
            cap.syncEntity(this)
        }
    }

    fun getEntityIcon(): IconType {
        return this.getCapability(
            get(
                NPCEntityCapability::class.java
            )
        )
            .orElseThrow { IllegalStateException("NPCEntity Capability not found!") }.iconType
    }

    override fun die(source: DamageSource) {
        super.die(source)
    }

    override fun registerGoals() {
        goalSelector.addGoal(0, SwimGoal(this)) //Если NPC решит утонить будет не кайф...
    }

    override fun isInvulnerable(): Boolean {
        return this.getCapability(
            get(
                NPCEntityCapability::class.java
            )
        ).orElseThrow { IllegalStateException("NPCEntity Capability not found!") }.settings.data.isUndead
    }

    override fun shouldDespawnInPeaceful(): Boolean {
        return false
    }

    //не думаю, что NPC можно деспавниться...
    override fun checkDespawn() {}
    override val npcEntity: NPCEntity
        get() = this

    override fun tick() {
        super.tick()

        //Без этого при добавлении тасков может вылететь ConcurrentModificationException
        if (goalQueue.size > 0) {
            goalQueue.forEach { task -> goalSelector.addGoal(0, task) }
            goalQueue.clear()
        }
        if (removeGoalQueue.size > 0) {
            removeGoalQueue.forEach { pTask -> goalSelector.removeGoal(pTask) }
            removeGoalQueue.clear()
        }
    }

    override fun onCapabilitySync(capability: Capability<*>) {
        if (capability == get<NPCEntityCapability>()) {
            val npcCapability = this.getCapability<NPCEntityCapability>()

            this.customName = npcCapability.settings.name.mcText
            this.isCustomNameVisible = true
        }

        if (level.isClientSide && capability == get<AnimatedEntityCapability>()) {
            val animCapability = this.getCapability<AnimatedEntityCapability>()

            updateModels(animCapability)
        }
    }

    fun updateModels(capability: AnimatedEntityCapability) {
        RenderSystem.recordRenderCall {
            model = GlTFModelManager.getOrCreate(capability.model)

            AnimationType.load(model!!.gltfModel, capability)

            val textures = capability.textures

            updateTextures(textures, model!!)
        }
    }

    private fun updateTextures(textures: HashMap<String, String>, model: RenderedGltfModel) {
        (model.gltfModel as? DefaultGltfModel)?.let { gltf ->

            val size = gltf.textureModels.size

            for (i in 0 until size) {
                val texture = gltf.getTextureModel(i)

                if (textures.contains(texture.name)) {
                    (texture.imageModel as? DefaultImageModel)?.imageData = GlTFModelManager.getInstance().getImageResource(textures[texture.name])
                    model.textureModelToGlTexture[texture] = null //Сбрасываем предыдущую текстуру
                }
            }
        }
    }

    override fun canPickUpLoot(): Boolean {
        return true
    }

    override var model: RenderedGltfModel? = null

}
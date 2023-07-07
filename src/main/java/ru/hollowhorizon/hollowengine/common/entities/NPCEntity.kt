package ru.hollowhorizon.hollowengine.common.entities

import com.mojang.blaze3d.systems.RenderSystem
import net.minecraft.entity.CreatureEntity
import net.minecraft.entity.EntityType
import net.minecraft.entity.ai.goal.SwimGoal
import net.minecraft.entity.player.PlayerEntity
import net.minecraft.util.ActionResultType
import net.minecraft.util.DamageSource
import net.minecraft.util.Hand
import net.minecraft.world.World
import net.minecraftforge.api.distmarker.Dist
import net.minecraftforge.api.distmarker.OnlyIn
import net.minecraftforge.common.capabilities.Capability
import org.lwjgl.opengl.GLCapabilities
import ru.hollowhorizon.hc.client.gltf.GlTFModelManager
import ru.hollowhorizon.hc.client.gltf.IAnimatedEntity
import ru.hollowhorizon.hc.client.gltf.RenderedGltfModel
import ru.hollowhorizon.hc.client.gltf.animation.AnimationTypes
import ru.hollowhorizon.hc.client.gltf.animation.GLTFAnimation
import ru.hollowhorizon.hc.client.gltf.animation.loadAnimations
import ru.hollowhorizon.hc.client.gltf.animations.AnimationManager
import ru.hollowhorizon.hc.client.utils.mcText
import ru.hollowhorizon.hc.common.capabilities.*
import ru.hollowhorizon.hc.common.capabilities.HollowCapabilityV2.Companion.get
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

    @OnlyIn(Dist.CLIENT)
    private fun tryAddAnimation(
        type: AnimationTypes,
        capability: AnimatedEntityCapability,
        animationList: List<GLTFAnimation>
    ) {
        when (type) {
            AnimationTypes.IDLE -> capability.animations[type] = animationList.find { it.name.contains("idle") }?.name
                ?: ""

            AnimationTypes.IDLE_SNEAKED -> capability.animations[type] =
                animationList.find { it.name.contains("idle") && it.name.contains("sneak") }?.name
                    ?: capability.animations[AnimationTypes.IDLE] ?: ""

            AnimationTypes.WALK -> capability.animations[type] = animationList
                .filter {
                    it.name.contains("walk") || it.name.contains("go") || it.name.contains("run") || it.name.contains("move")
                }.minByOrNull {
                    when {
                        it.name.contains("walk") -> 0
                        it.name.contains("go") -> 1
                        it.name.contains("run") -> 2
                        it.name.contains("move") -> 3
                        else -> 4
                    }
                }?.name ?: ""

            AnimationTypes.WALK_SNEAKED -> capability.animations[type] =
                animationList.find { it.name.contains("walk") && it.name.contains("sneak") }?.name
                    ?: capability.animations[AnimationTypes.WALK] ?: ""

            AnimationTypes.RUN -> capability.animations[type] = animationList.find { it.name.contains("run") }?.name
                ?: capability.animations[AnimationTypes.WALK] ?: ""

            AnimationTypes.SWIM -> capability.animations[type] = animationList.find { it.name.contains("swim") }?.name
                ?: capability.animations[AnimationTypes.WALK] ?: ""

            AnimationTypes.FALL -> capability.animations[type] = animationList.find { it.name.contains("fall") }?.name
                ?: capability.animations[AnimationTypes.IDLE] ?: ""

            AnimationTypes.FLY -> capability.animations[type] = animationList.find { it.name.contains("fly") }?.name
                ?: capability.animations[AnimationTypes.IDLE] ?: ""

            AnimationTypes.SIT -> capability.animations[type] = animationList.find { it.name.contains("sit") }?.name
                ?: capability.animations[AnimationTypes.IDLE] ?: ""

            AnimationTypes.SLEEP -> capability.animations[type] = animationList.find { it.name.contains("sleep") }?.name
                ?: capability.animations[AnimationTypes.SLEEP] ?: ""

            AnimationTypes.SWING -> capability.animations[type] =
                animationList.find { it.name.contains("attack") || it.name.contains("swing") }?.name ?: ""

            AnimationTypes.DEATH -> capability.animations[type] =
                animationList.find { it.name.contains("death") }?.name ?: ""

        }
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
        if (!level.isClientSide) {
            if (capability == get<NPCEntityCapability>()) { //При обновлении NPC на сервере обновляем данные о модели на клиенте
                val npcCapability = this.getCapability<NPCEntityCapability>()

                this.customName = npcCapability.settings.name.mcText

                val animCapability = this.getCapability<AnimatedEntityCapability>()

                val model = npcCapability.settings.model

                animCapability.model = model.modelPath
                animCapability.textures = model.textureOverrides

                animCapability.syncEntity(this)
            }
        } else {
            if (capability == get<AnimatedEntityCapability>()) {
                val animCapability = this.getCapability<AnimatedEntityCapability>()

                updateModels(animCapability)
            }
        }
    }

    fun updateModels(capability: AnimatedEntityCapability) {
        RenderSystem.recordRenderCall {
            renderedGltfModel = GlTFModelManager.getOrCreate(this, capability)
            animationList = renderedGltfModel!!.loadAnimations()
            animationManager = AnimationManager(renderedGltfModel!!)
            AnimationTypes.values().forEach { tryAddAnimation(it, capability, animationList) }
        }
    }

    override fun canPickUpLoot(): Boolean {
        return true
    }

    override var animationList: List<GLTFAnimation> = ArrayList()
    override var animationManager: AnimationManager? = null
    override var renderedGltfModel: RenderedGltfModel? = null

}

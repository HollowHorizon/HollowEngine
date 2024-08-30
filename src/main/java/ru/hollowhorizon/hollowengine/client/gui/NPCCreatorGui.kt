package ru.hollowhorizon.hollowengine.client.gui

import imgui.ImGui
import imgui.flag.ImGuiInputTextFlags
import imgui.type.ImBoolean
import imgui.type.ImFloat
import imgui.type.ImInt
import imgui.type.ImString
import kotlinx.serialization.Serializable
import net.minecraft.client.Minecraft
import net.minecraft.client.gui.screens.Screen
import net.minecraft.locale.Language
import net.minecraft.network.chat.Component
import net.minecraft.world.entity.player.Player
import ru.hollowhorizon.hc.client.imgui.DockingHelper
import ru.hollowhorizon.hc.client.imgui.Graphics
import ru.hollowhorizon.hc.client.imgui.ImGuiHandler
import ru.hollowhorizon.hc.client.models.internal.Transform
import ru.hollowhorizon.hc.client.models.internal.animations.AnimationType
import ru.hollowhorizon.hc.client.models.internal.manager.AnimatedEntityCapability
import ru.hollowhorizon.hc.client.models.internal.manager.GltfManager
import ru.hollowhorizon.hc.client.utils.*
//? if <=1.19.2
/*import ru.hollowhorizon.hc.client.utils.math.level*/
import ru.hollowhorizon.hc.common.network.HollowPacketV2
import ru.hollowhorizon.hc.common.network.HollowPacketV3
import ru.hollowhorizon.hollowengine.common.entities.NPCEntity
import ru.hollowhorizon.hollowengine.common.files.DirectoryManager
import ru.hollowhorizon.hollowengine.common.files.DirectoryManager.toReadablePath
import ru.hollowhorizon.hollowengine.common.npcs.HitboxMode
import ru.hollowhorizon.hollowengine.common.npcs.NPCCapability

class NPCCreatorGui(val npc: NPCEntity, private val npcId: Int) : ImGuiScreen() {
    val scripts = DirectoryManager.npcScripts.map { it.toReadablePath() }.toMutableList()
        .also { it.add(0, Language.getInstance().getOrDefault("npc_tool.empty")) }.toTypedArray()
    private val npcName = ImString().apply {
        if (npc.hasCustomName()) set(npc.customName?.string ?: "")
        else set(
            String(NPCCreatorGui::class.java.getResourceAsStream("/internal/npc.names")!!.readAllBytes())
                .split("\r\n", "\n").random()
        )
    }
    private val npcModel = ImString().apply {
        set(npc[AnimatedEntityCapability::class].model)
    }
    private val showName = ImBoolean().apply { set(npc.isCustomNameVisible) }
    private val switchHeadRot = ImBoolean().apply { set(npc[AnimatedEntityCapability::class].switchHeadRot) }
    private val invulnerable = ImBoolean().apply { set(npc.isInvulnerable) }
    private var model =
        GltfManager.getOrCreate(if (npcModel.get() != "%NO_MODEL%") npcModel.get().rl else "hollowengine:models/entity/player_model.gltf".rl)
    val script = ImInt(scripts.indexOf(npc[NPCCapability::class].script).coerceAtLeast(0))
    private val animations = HashMap<String, String>()
    private val textures = npc[AnimatedEntityCapability::class].textures.toMutableMap()
    private val hitboxWidth = ImFloat(npc.entityData[NPCEntity.sizeX])
    private val hitboxHeight = ImFloat(npc.entityData[NPCEntity.sizeY])
    private val hitboxMode = ImInt().apply { set(npc[NPCCapability::class].hitboxMode.ordinal) }
    private val showHitbox = ImBoolean().apply { set(false) }
    private val tX = floatArrayOf(0f)
    private val tY = floatArrayOf(0f)
    private val tZ = floatArrayOf(0f)
    private val rX = floatArrayOf(0f)
    private val rY = floatArrayOf(0f)
    private val rZ = floatArrayOf(0f)
    private val sX = floatArrayOf(1f)
    private val sY = floatArrayOf(1f)
    private val sZ = floatArrayOf(1f)

    override fun Graphics.draw() {

        npc.tickCount = Minecraft.getInstance().player?.tickCount ?: 0

        DockingHelper.splitHorizontally(
            {
                if (ImGui.beginTabBar("##tabs")) {
                    if (ImGui.beginTabItem("Основное")) {
                        drawGeneral()

                        val size = ImGui.calcTextSize("Создать   Отмена")
                        ImGui.setCursorPos(
                            ImGui.getWindowWidth() - size.x - ImGui.getStyle().windowPaddingX * 2,
                            ImGui.getWindowHeight() - size.y - ImGui.getStyle().windowPaddingY * 2
                        )
                        if (ImGui.button("Сохранить")) {
                            onClose()
                            NPCCreatorPacket(
                                npcId,
                                npcName.get(),
                                npcModel.get(),
                                showName.get(),
                                switchHeadRot.get(),
                                invulnerable.get(), hitboxWidth.get(), hitboxHeight.get(),
                                HitboxMode.entries[hitboxMode.get()],
                                animations.map { AnimationType.valueOf(it.key) to it.value }.toMap(),
                                textures.filter { it.value.isNotEmpty() },
                                tX[0], tY[0], tZ[0], rX[0], rY[0], rZ[0], sX[0], sY[0], sZ[0],
                                if (script.get() == 0) "%empty%" else scripts[script.get()]
                            ).send()
                        }
                        ImGui.sameLine()
                        if (ImGui.button("Отмена")) {
                            onClose()
                        }

                        ImGui.endTabItem()
                    }

                    if (ImGui.beginTabItem("Анимации")) {
                        drawAnimations()
                        ImGui.endTabItem()
                    }

                    if (ImGui.beginTabItem("Текстуры")) {
                        drawTextures()
                        ImGui.endTabItem()
                    }

                    if (ImGui.beginTabItem("Аттрибуты")) {
                        drawAttributes()
                        ImGui.endTabItem()
                    }

                    if (ImGui.beginTabItem("Дополнительное")) {
                        drawTransforms()
                        ImGui.endTabItem()
                    }
                    ImGui.endTabBar()
                }
            },
            {
                ImGui.checkbox("Показать хитбокс", showHitbox)

                val disp = Minecraft.getInstance().entityRenderDispatcher
                val last = disp.shouldRenderHitBoxes()
                disp.setRenderHitBoxes(showHitbox.get())
                val size = ImGui.getContentRegionAvail()
                entity(npc, size.x, size.y, border = true, scale = 0.8f)
                disp.setRenderHitBoxes(last)
            })

    }

    private fun drawGeneral() {
        ImGui.pushItemWidth(700f)
        ImGui.inputText("Имя персонажа", npcName)
        npcModel.set(npc[AnimatedEntityCapability::class].model)
        if (ImGui.inputText("Модель персонажа", npcModel)) {
            val npcModel = npcModel.get().rl
            if (npcModel.exists() && (npcModel.path.endsWith(".gltf") || npcModel.path.endsWith(".glb"))) {
                model = GltfManager.getOrCreate(npcModel)
                npc[AnimatedEntityCapability::class].model = npcModel.toString()
            }
        }
        ImGui.popItemWidth()

        if (ImGui.checkbox("Показывать имя", showName)) {
            npc.isCustomNameVisible = showName.get()
        }
        ImGui.checkbox("Инверсировать поворот головы", switchHeadRot)
        ImGui.checkbox("Бессмертный", invulnerable)

        ImGui.text("Размер хитбокса:")
        ImGui.pushItemWidth(360f)
        var hitbox = ImGui.inputFloat("Ширина", hitboxWidth, 0.1f, 0.5f, "%.2f")
        ImGui.sameLine()
        hitbox = hitbox or ImGui.inputFloat("Высота", hitboxHeight, 0.1f, 0.5f, "%.2f")
        ImGui.popItemWidth()

        if (hitbox) {
            npc.setDimensions(hitboxWidth.get() to hitboxHeight.get())
            npc.refreshDimensions()
        }

        ImGui.combo("Режим хитбокса", hitboxMode, arrayOf("Блокируемый", "Толкаемый", "Пустой"))

        ImGui.combo("Скрипт", script, scripts)
    }

    private fun drawAttributes() {
        ImGui.textWrapped("Понимаешь, редактор атрибутов у NPC - это, конечно, важная штука, но... - Говорит Халва, делая виноватое лицо и смотря в пол.")
        ImGui.separator()
        ImGui.textWrapped("Во-первых, Халва был очень занят другими, более критичными задачами. Загибает пальцы Оптимизация рендеринга, фиксы багов, работа над новыми фичами - все это отнимало уйму времени и сил. - Говорит Халва с серьезным лицом.")
        ImGui.separator()
        ImGui.textWrapped("Во-вторых, это не такая простая задача, как может показаться. Нужно продумать интерфейс, логику сохранения и загрузки, интеграцию с остальными системами... Вздыхает Халва не хотел делать что-то наспех и потом жалеть об этом.")
        ImGui.separator()
        ImGui.textWrapped("И наконец, *Халва делает невинное лицо*, может быть, Халва просто хотел оставить эту задачу для молодых и перспективных разработчиков, таких как ты? *Подмигивает.*\nЧтобы у них была возможность проявить себя и внести свой вклад в развитие проекта.")
        ImGui.separator()
        ImGui.textWrapped("*Халва разводит руками.* Вот такие дела, мой друг. Халва понимает, что редактор атрибутов - это важно и нужно, но... *Делает виноватое лицо.* Не всегда все получается так, как хочется. \n*Вздыхает.* Халва обещает, что вернется к этой задаче при первой же возможности... или хотя бы будет более убедительно оправдываться в следующий раз. *Ухмыляется* :)")
    }


    private fun drawAnimations() {
        val animationNames = model.animationPlayer.nameToAnimationMap.keys.toTypedArray()
        val animationTypes =
            model.animationPlayer.typeToAnimationMap.map { it.key.name to it.value.name }.toMap() + animations
        var animationToChange: Pair<String, String>? = null

        for (type in AnimationType.entries) {
            val anim = animationTypes[type.name] ?: ""
            val index = ImInt(animationNames.indexOf(anim))
            if (ImGui.combo(type.name.lowercase().capitalize() + " анимация", index, animationNames)) {
                animationToChange = type.name to animationNames[index.get()]
            }
        }

        animationToChange?.let {
            animations[it.first] = it.second
            npc[AnimatedEntityCapability::class].animations.put(AnimationType.valueOf(it.first), it.second)
        }
    }

    private fun drawTextures() {
        val textureNames = model.modelTree.materials.map { it.texture.path.toString() }

        for (texture in textureNames) {
            ImGui.text(texture)
            ImGui.sameLine()
            val text = ImString()
            text.set(textures.computeIfAbsent(texture) { "" })
            ImGui.pushID(texture)
            if (ImGui.inputText("", text, ImGuiInputTextFlags.NoUndoRedo)) {
                val new = text.get()
                textures[texture] = new
                if (new.isEmpty()) npc[AnimatedEntityCapability::class].textures.remove(texture)
                else npc[AnimatedEntityCapability::class].textures[texture] = new
            }
            ImGui.popID()
        }
    }

    private fun drawTransforms() {
        var changed = false

        ImGui.text("Перемещение")
        ImGui.separator()
        ImGui.pushItemWidth(120f)

        ImGui.pushID("T")
        changed = changed or ImGui.dragFloat("X", tX, 0.01f, -10f, 10f); ImGui.sameLine()
        changed = changed or ImGui.dragFloat("Y", tY, 0.01f, -10f, 10f); ImGui.sameLine()
        changed = changed or ImGui.dragFloat("Z", tZ, 0.01f, -10f, 10f)
        ImGui.popID()

        ImGui.text("Поворот")
        ImGui.separator()

        ImGui.pushID("R")
        changed = changed or ImGui.dragFloat("X", rX, 1f, -360f, 360f); ImGui.sameLine()
        changed = changed or ImGui.dragFloat("Y", rY, 1f, -360f, 360f); ImGui.sameLine()
        changed = changed or ImGui.dragFloat("Z", rZ, 1f, -360f, 360f)
        ImGui.popID()

        ImGui.text("Масштаб")
        ImGui.separator()

        ImGui.pushID("S")
        changed = changed or ImGui.dragFloat("X", sX, 0.01f, 0.001f, 10f); ImGui.sameLine()
        changed = changed or ImGui.dragFloat("Y", sY, 0.01f, 0.001f, 10f); ImGui.sameLine()
        changed = changed or ImGui.dragFloat("Z", sZ, 0.01f, 0.001f, 10f)
        ImGui.popID()

        ImGui.popItemWidth()

        if (changed) {
            npc[AnimatedEntityCapability::class].transform =
                Transform(tX[0], tY[0], tZ[0], rX[0], rY[0], rZ[0], sX[0], sY[0], sZ[0])
        }
    }

    override fun isPauseScreen() = false
}

@HollowPacketV2
@Serializable
class NPCCreatorPacket(
    private val id: Int,
    private val name: String,
    private val model: String,
    private val showName: Boolean,
    private val switchHeadRot: Boolean,
    private val invulnerable: Boolean,
    private val hitboxWidth: Float,
    private val hitboxHeight: Float,
    private val hitboxMode: HitboxMode,
    private val animations: Map<AnimationType, String>,
    private val textures: Map<String, String>,
    private val tX: Float, private val tY: Float, private val tZ: Float,
    private val rX: Float, private val rY: Float, private val rZ: Float,
    private val sX: Float, private val sY: Float, private val sZ: Float,
    val script: String,
) : HollowPacketV3<NPCCreatorPacket> {
    override fun handle(player: Player) {
        if (!player.hasPermissions(2)) {
            player.sendSystemMessage("У вас не достаточно прав для этого действия!".mcText)
            return
        }

        val entity = player.level().getEntity(id) as? NPCEntity

        if (entity == null) {
            player.sendSystemMessage("Ошибка, персонаж не был заспавнен!".mcText.colored(0xFF2222))
            return
        }

        entity[AnimatedEntityCapability::class].apply {
            model = this@NPCCreatorPacket.model
            animations.putAll(this@NPCCreatorPacket.animations)
            textures.putAll(this@NPCCreatorPacket.textures)
            transform = Transform(tX, tY, tZ, rX, rY, rZ, sX, sY, sZ)
            switchHeadRot = this@NPCCreatorPacket.switchHeadRot
        }
        entity[NPCCapability::class].hitboxMode = hitboxMode

        entity.isInvulnerable = invulnerable
        entity.isCustomNameVisible = showName && this@NPCCreatorPacket.name.isNotEmpty()
        entity.customName = name.mcText
        entity.setDimensions(hitboxWidth to hitboxHeight)
        entity.refreshDimensions()
        entity[NPCCapability::class].script = script
        entity.reloadScript()
    }


}
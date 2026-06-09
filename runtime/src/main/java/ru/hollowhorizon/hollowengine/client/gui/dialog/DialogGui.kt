package ru.hollowhorizon.hollowengine.client.gui.dialog

import de.fabmax.kool.math.Easing
import de.fabmax.kool.math.Vec2i
import de.fabmax.kool.math.clamp
import de.fabmax.kool.modules.ui2.*
import de.fabmax.kool.scene.Scene
import de.fabmax.kool.util.Color
import de.fabmax.kool.util.MsdfFont
import de.fabmax.kool.util.Time
import de.fabmax.kool.util.launchOnMainThread
import kotlinx.coroutines.delay
import net.minecraft.client.Minecraft
import net.minecraft.nbt.CompoundTag
import net.minecraft.util.Mth
import net.minecraft.world.entity.LivingEntity
import ru.hollowhorizon.hollowengine.client.gui.scripting.theme.IdeTheme
import ru.hollowhorizon.hollowengine.client.gui.scripting.tools.hoverable
import ru.hollowhorizon.hollowengine.client.kool.Entity
import ru.hollowhorizon.hollowengine.client.kool.KoolManager
import ru.hollowhorizon.hollowengine.client.kool.KoolScreen
import ru.hollowhorizon.hollowengine.client.kool.minecraft.Image
import ru.hollowhorizon.hollowengine.client.kool.scale
import ru.hollowhorizon.hollowengine.client.utils.math.Interpolation
import ru.hollowhorizon.hollowengine.common.npcs.dialogues.DialogChoice
import ru.hollowhorizon.hollowengine.common.npcs.dialogues.DialogueScene
import ru.hollowhorizon.hollowengine.common.npcs.dialogues.DialogueUpdateEvent

class DialogGui : KoolScreen() {
    var text = ""
        set(value) {
            textAnimator = FloatAnimator(value.split(wordCounter).size / 5f)
            textAnimator.start(1f)
            field = value
        }
    var character = ""
    var entities = ArrayList<LivingEntity>()
    val choices = mutableListOf<DialogChoice>()
    private var choiceId: Int = -1

    private var extras = CompoundTag()

    private val choiceShowAnimator = FloatAnimator(0.75f)
    private val choiceAnimator = FloatAnimator(1f, initial = 0f)
    private val rootChoiceAnimator = FloatAnimator(1f, initial = 0f)
    private val showAnimator = FloatAnimator(0.75f)
    private var textAnimator = FloatAnimator(2f)
    private val toggleAnimator = AnimatedFloatLoop()

    init {
        uiSize = Vec2i(500, 281)
    }

    override fun init() {
        super.init()
        showAnimator.start(1f)
        textAnimator.start(1f)
    }

    override fun Scene.setup() {
        addPanelSurface(IdeTheme.colors, IdeTheme.sizes.copy(normalText = MsdfFont(KoolManager.MONOCRAFT, 12f))) {
            val progress = showAnimator.updateUsing()
            modifier.backgroundColor(Color(0f, 0f, 0f, 0.6f * Interpolation.QUAD_OUT(progress))).layout(CellLayout)
            modifier.onHover {
                val (x, y) = it.pointer.delta

                Minecraft.getInstance().player?.let {
                    it.xRot += y / 1000f
                    it.yRot += x / 100f
                }
            }

            EntityOverlay(progress)
            DialogueChoices(progress * Interpolation.QUAD_OUT(choiceShowAnimator.updateUsing()))
            DialogueBox(progress)
        }
    }

    private fun UiScope.EntityOverlay(progress: Float) {
        Row(Grow.Std, Grow.Std) {
            modifier.padding(sizes.gap)
            entities.forEach { entity ->
                Entity({entity}) {
                    modifier.size(Grow.Std, Grow.Std)
                        .scale(0.85f)
                        .mouseRotation()
                        .margin(start = 100.dp * Interpolation.QUAD_OUT(1f - progress))
                        .tint(Color(1f, 1f, 1f, Interpolation.QUAD_IN(progress)))
                }
            }
        }
    }

    private fun UiScope.DialogueBox(progress: Float) {
        Column(Grow.Std, Grow.Std) {
            modifier.size(425.dp, FitContent)
                .margin(bottom = sizes.gap - 40.dp * Interpolation.QUINT_IN(1f - progress))
                .align(AlignmentX.Center, AlignmentY.Bottom)
                .zLayer(3)

            if(character.isNotBlank()) Box {
                modifier.size(425.dp, FitContent)
                    .margin(bottom = 1.dp)

                Image("hollowengine:textures/gui/dialogues/character_name.png") {
                    modifier.size(90.dp, 20.dp)
                        .tint(Color(1f, 1f, 1f, Interpolation.QUAD_IN(progress)))
                    Box(Grow.Std, Grow.Std) {
                        Text(character) {
                            modifier.zLayer(5).font(sizes.normalText.derive(10f))
                                .align(AlignmentX.Center, AlignmentY.Bottom)
                                .margin(bottom = 3.dp)
                                .textColor(Color(1f, 1f, 1f, Interpolation.QUAD_IN(progress)))
                        }
                    }
                }
            }

            Image("hollowengine:textures/gui/dialogues/dialogue_box.png") {
                modifier.size(425.dp, 62.dp)
                    .alignX(AlignmentX.Center)
                    .tint(Color(1f, 1f, 1f, Interpolation.QUAD_IN(progress)))

                Row(Grow.Std, Grow.Std) {
                    modifier.padding(start = 12.dp, end = 12.dp)
                    if(text.isEmpty()) return@Row
                    Text(text.substring(0, (text.lastIndex * textAnimator.updateUsing()).toInt() + 1)) {
                        modifier.zLayer(5).size(Grow.Std, Grow.Std)
                            .isWrapText(true)
                            .textColor(Color(1f, 1f, 1f, Interpolation.QUAD_IN(progress)))
                    }
                    if (!textAnimator.isActive && choices.isEmpty()) Image("hollowengine:textures/gui/dialogues/cursor.png") {
                        val isHovered by modifier.hoverable()
                        val factor by animateFloatAsState(if (isHovered) 1f else 0f, tween(easing = Easing.easeOutQuart))

                        val clickAnimator = remember { FloatAnimator(0.5f, initial = 0f) }

                        modifier.onClick {
                            clickAnimator.start(1f)
                            DialogueUpdateEvent(extras).send()
                        }

                        val size = 1.0f * (1f - Interpolation.QUAD_OUT(clickAnimator.updateUsing())) + 0.1f * Interpolation.QUAD_OUT(factor)
                        modifier.align(AlignmentX.Start, AlignmentY.Center)
                            .size(22.dp * size, 24.dp * size)
                            .tint(Color(1f, 1f, 1f, Interpolation.QUAD_IN(progress)).mulRgb(0.75f + 0.25f * factor))

                        modifier.imageProvider?.let { (it as FlatImageProvider).mirrorX() }

                        toggleAnimator.progress(Time.deltaT / if (isHovered) 5f else 1f)
                        var t = toggleAnimator.use()
                        t = if (t.toInt() % 2 == 0) Interpolation.SINE_IN_OUT(1f - t % 1f)
                        else Interpolation.SINE_IN_OUT(t % 1f)
                        modifier.margin(
                            end = (15f + 15f * t).dp
                        )
                    }
                    else Box(22.dp, 24.dp) {
                        modifier.align(AlignmentX.Start, AlignmentY.Center)
                    }
                }
            }
        }
    }

    private fun UiScope.DialogueChoices(progress: Float) {
        val state = rememberListState()
        choiceAnimator.update(Time.deltaT)
        rootChoiceAnimator.update(Time.deltaT)
        LazyColumn(
            Grow.Std,
            Grow.Std,
            state = state,
            containerModifier = { it.background(null).margin(bottom = 70.dp).align(AlignmentX.Center, AlignmentY.Top) },
            withHorizontalScrollbar = false,
            withVerticalScrollbar = false,
            isScrollByDrag = true
        ) {
            modifier.align(AlignmentX.Center, AlignmentY.Center)

            val choices = ArrayList<DialogChoice>()
            choices.add(DialogChoice.simple("%start%"))
            choices.addAll(this@DialogGui.choices)
            choices.add(DialogChoice.simple("%end%"))

            itemsIndexed(choices) { i, choice ->
                if (i == 0) {
                    Box {
                        modifier.height(Dp.fromPx(KoolManager.context.window.size.y / 2f) - 24.dp)
                    }
                    return@itemsIndexed
                }
                if (i == choices.lastIndex) {
                    Box {
                        modifier.height(Dp.fromPx(KoolManager.context.window.size.y / 3f) + 24.dp)
                    }
                    return@itemsIndexed
                }
                if (choiceId != -1) {
                    val alpha = if (i - 1 == choiceId) Interpolation.QUINT_IN(1f - rootChoiceAnimator.value)
                    else Interpolation.QUINT_IN(1f - choiceAnimator.value)

                    DialogueButton(choice, i - 1, alpha)
                } else {
                    DialogueButton(choice, i - 1, progress)
                }
            }
        }
    }

    private fun UiScope.DialogueButton(choice: DialogChoice, i: Int, progress: Float) {
        val box = uiNode
        Box {

            var scale by remember(1f)

            modifier.alignX(AlignmentX.Center)
                .margin(4.dp)
                .onMeasured {
                    val centerY = it.topPx + it.heightPx / 2f
                    val boxHeight = box.parent!!.clipBoundsPx.let { it.w - it.y }
                    val boxCenterY = box.parent!!.clipBoundsPx.y + boxHeight / 2f
                    val distanceFromCenter = ((centerY - boxCenterY) / boxHeight).coerceIn(-1f, 1f)

                    scale = (Mth.cos(distanceFromCenter * Mth.PI / 1.5f) + 1f) / 2f
                }
                .onClick {
                    choiceId = i
                    rootChoiceAnimator.set(0f)
                    choiceAnimator.start(1f)
                    launchOnMainThread {
                        delay(500L)
                        rootChoiceAnimator.start(1f)
                        delay(500)
                        extras.putInt("choiceId", choiceId)
                        DialogueUpdateEvent(extras).send()
                        choices.clear()
                    }
                }

            val isHovered by modifier.hoverable()
            val factor by animateFloatAsState(if (isHovered) 1f else 0f, tween(easing = Easing.easeOutQuart))

            val size = 1f + 0.1f * Interpolation.QUINT_OUT(factor)
            val transparency = 0.25f + 0.75f * Interpolation.QUINT_IN((factor + scale).clamp())
            DialogueType(choice, progress * transparency, size)
            DialogueHeader(choice.content, progress * transparency, factor, size)
            DialogueCursor(progress * transparency, factor, size)
        }
    }

    private fun UiScope.DialogueType(choice: DialogChoice, progress: Float, scale: Float) {
        Image("hollowengine:textures/gui/dialogues/invite.png") {
            modifier.size(32.dp * scale, 24.dp * scale)
                .tint(Color(1f, 1f, 1f, Interpolation.QUAD_IN(progress)))

            choice.apply {
                buildIcon(scale, progress)
            }
        }
    }

    private fun UiScope.DialogueHeader(text: String, progress: Float, hover: Float, scale: Float) {
        Image("hollowengine:textures/gui/dialogues/button.png") {
            modifier.size(205.dp * scale, 24.dp * scale).tint(Color(1f, 1f, 1f, Interpolation.QUAD_IN(progress)))
                .margin(start = (32.dp - 6.dp * hover) * scale, end = 22.dp * scale)

            Text(text) {
                modifier.zLayer(2)
                    .align(AlignmentX.Center, AlignmentY.Center)
                    .font(sizes.normalText.derive(9f * scale))
                    .textColor(Color(1f, 1f, 1f, Interpolation.QUAD_IN(progress)))
            }
        }
    }

    private fun UiScope.DialogueCursor(progress: Float, hover: Float, scale: Float) {
        if (hover == 0f) return
        Image("hollowengine:textures/gui/dialogues/cursor.png") {
            modifier.size(22.dp * scale, 24.dp * scale).tint(Color(1f, 1f, 1f, Interpolation.QUAD_IN(progress) * hover))
                .margin(start = (237.dp - 12.dp * hover) * scale)
        }
    }

    fun update(scene: DialogueScene) {
        if(text != scene.text) text = scene.text
        character = scene.character
        entities.clear()
        entities.addAll(scene.characters.filterIsInstance<LivingEntity>())
        if(scene.choices.isNotEmpty()) {
            choices.clear()
            choices.addAll(scene.choices)
            choiceShowAnimator.start(1f)
        }
        choiceId = -1
    }

    override fun isPauseScreen() = false

    companion object {
        private val wordCounter = "\\s+".toRegex()
    }
}

class AnimatedFloatLoop(initValue: Float = 0f) : MutableStateValue<Float>(initValue) {
    fun progress(deltaT: Float) {
        val newValue = value + deltaT
        set(newValue)
    }
}

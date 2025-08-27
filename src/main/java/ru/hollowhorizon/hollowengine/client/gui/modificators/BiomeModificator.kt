package ru.hollowhorizon.hollowengine.client.gui.modificators

import de.fabmax.kool.modules.ui2.*
import de.fabmax.kool.pipeline.ClearColorDontCare
import de.fabmax.kool.pipeline.ClearDepthDontCare
import de.fabmax.kool.scene.Scene
import de.fabmax.kool.util.Color
import de.fabmax.kool.util.MsdfFont
import ru.hollowhorizon.hollowengine.client.kool.KoolScreen
import ru.hollowhorizon.hollowengine.client.gui.scripting.HACK_FONT
import kotlin.math.roundToInt

class WorldColors(
    sky: Color,
    fog: Color,
    water: Color,
    waterFog: Color
) {
    private fun Color.toStates(): Triple<
            MutableStateValue<Float>,
            MutableStateValue<Float>,
            MutableStateValue<Float>
            > {
        val (h, s, v) = this.toHsv()
        return Triple(
            mutableStateOf(h),
            mutableStateOf(s),
            mutableStateOf(v)
        )
    }

    private val skyStates = sky.toStates()
    private val fogStates = fog.toStates()
    private val waterStates = water.toStates()
    private val waterFogStates = waterFog.toStates()

    val skyHueState: MutableStateValue<Float> get() = skyStates.first
    val skySatState: MutableStateValue<Float> get() = skyStates.second
    val skyValState: MutableStateValue<Float> get() = skyStates.third
    val skyHexState = mutableStateOf(sky.toHexString())
    val skyColor: Color.Hsv // для будущей реализации полноценной системы
        get() = Color.Hsv(
            skyHueState.value,
            skySatState.value,
            skyValState.value
        )

    val fogHueState: MutableStateValue<Float> get() = fogStates.first
    val fogSatState: MutableStateValue<Float> get() = fogStates.second
    val fogValState: MutableStateValue<Float> get() = fogStates.third
    val fogHexState = mutableStateOf(fog.toHexString())
    val fogColor: Color.Hsv
        get() = Color.Hsv(
            fogHueState.value,
            fogSatState.value,
            fogValState.value
        )

    val waterHueState: MutableStateValue<Float> get() = waterStates.first
    val waterSatState: MutableStateValue<Float> get() = waterStates.second
    val waterValState: MutableStateValue<Float> get() = waterStates.third
    val waterHexState = mutableStateOf(water.toHexString())
    val waterColor: Color.Hsv
        get() = Color.Hsv(
            waterHueState.value,
            waterSatState.value,
            waterValState.value
        )

    val waterFogHueState: MutableStateValue<Float> get() = waterFogStates.first
    val waterFogSatState: MutableStateValue<Float> get() = waterFogStates.second
    val waterFogValState: MutableStateValue<Float> get() = waterFogStates.third
    val waterFogHexState = mutableStateOf(waterFog.toHexString())
    val waterFogColor: Color.Hsv
        get() = Color.Hsv(
            waterFogHueState.value,
            waterFogSatState.value,
            waterFogValState.value
        )
}


private enum class WorldType(val buttonText: String) {
    OVERWORLD("Верхний мир"),
    NETHER("Нижний мир"),
    END("Эндер мир")
}

object BiomeModificator : KoolScreen() {
    val enable = mutableStateOf(false)
    val enableSkybox = mutableStateOf(false)

    private val openedPicker = mutableStateOf<String?>(null)
    private val currentWorld = mutableStateOf(WorldType.OVERWORLD)

    private val worldColors = mapOf(
        WorldType.OVERWORLD to WorldColors(
            sky = Color.fromHex("87CEEB"),
            fog = Color.fromHex("FFFFFF"),
            water = Color.fromHex("3F76E4"),
            waterFog = Color.fromHex("050533")
        ),
        WorldType.NETHER to WorldColors(
            sky = Color.fromHex("8C2919"),
            fog = Color.fromHex("4D1810"),
            water = Color.fromHex("9B1919"),
            waterFog = Color.fromHex("591A1A")
        ),
        WorldType.END to WorldColors(
            sky = Color.fromHex("1A1A2A"),
            fog = Color.fromHex("12121A"),
            water = Color.fromHex("522462"),
            waterFog = Color.fromHex("2E153A")
        )
    )

    private val activeColors: WorldColors get() = worldColors[currentWorld.value]!!

    val sunSize = mutableStateOf(30f)
    val moonSize = mutableStateOf(20f)

    override fun Scene.setup() {
        setupUiScene()
        clearColor = ClearColorDontCare
        clearDepth = ClearDepthDontCare
        addPanelSurface {
            modifier
                .align(AlignmentX.Center, AlignmentY.Center)
                .width(600.dp)
                .height(Grow(1f, max = 800.dp))
                .padding(vertical = sizes.largeGap)
            Text("Модификатор биомов") {
                modifier
                    .alignX(AlignmentX.Center)
                    .margin(bottom = sizes.gap)
                    .padding(horizontal = sizes.largeGap)
                    .font(MsdfFont(HACK_FONT, 24f))
            }
            ScrollArea(state = rememberScrollState()) {
                modifier
                    .width(Grow.Std)
                    .padding(horizontal = sizes.largeGap)
                    .padding(bottom = sizes.gap)

                Column(width = Grow.Std) {
                    Row(width = Grow.Std) {
                        modifier.margin(bottom = sizes.smallGap)
                        Checkbox(enable.use()) {
                            modifier.margin(start = sizes.gap).onToggle {
                                enable.set(!enable.value)
                            }
                        }
                        Text("Включить модификатор биомов") {
                            modifier.margin(start = sizes.gap)
                                .alignY(AlignmentY.Center).font(MsdfFont(HACK_FONT, 24f))
                        }
                    }
                    Row(width = Grow.Std) {
                        Checkbox(enableSkybox.use()) {
                            modifier.margin(start = sizes.gap).onToggle {
                                enableSkybox.set(!enableSkybox.value)
                            }
                        }
                        Text("Включить скайбокс") {
                            modifier.margin(start = sizes.gap)
                                .alignY(AlignmentY.Center).font(MsdfFont(HACK_FONT, 24f))
                        }
                    }

                    divider(horizontalMargin = Dp.ZERO, marginTop = sizes.largeGap, marginBottom = sizes.gap)

                    // Панель выбора мира
                    Row(width = Grow.Std) {
                        WorldType.entries.forEach { world ->
                            Button(world.buttonText) {
                                modifier
                                    .width(Grow.Std)
                                    .margin(horizontal = sizes.smallGap)
                                    .font(MsdfFont(HACK_FONT, 24f))
                                    .onClick { currentWorld.set(world) }
                                if (currentWorld.use() == world) {
                                    modifier.backgroundColor(colors.primary)
                                }
                            }
                        }
                    }

                    divider(horizontalMargin = Dp.ZERO, marginTop = sizes.gap, marginBottom = sizes.gap)

                    CollapsibleColorPicker(
                        pickerId = "sky",
                        label = "Цвет неба",
                        openedPicker = openedPicker,
                        hue = activeColors.skyHueState,
                        saturation = activeColors.skySatState,
                        value = activeColors.skyValState,
                        hexString = activeColors.skyHexState,
                    ) { color -> activeColors.skyHexState.set(color.toHexString()) }

                    CollapsibleColorPicker(
                        pickerId = "fog",
                        label = "Цвет тумана",
                        openedPicker = openedPicker,
                        hue = activeColors.fogHueState,
                        saturation = activeColors.fogSatState,
                        value = activeColors.fogValState,
                        hexString = activeColors.fogHexState,
                    ) { color -> activeColors.fogHexState.set(color.toHexString()) }

                    CollapsibleColorPicker(
                        pickerId = "water",
                        label = "Цвет воды",
                        openedPicker = openedPicker,
                        hue = activeColors.waterHueState,
                        saturation = activeColors.waterSatState,
                        value = activeColors.waterValState,
                        hexString = activeColors.waterHexState,
                    ) { color -> activeColors.waterHexState.set(color.toHexString()) }

                    CollapsibleColorPicker(
                        pickerId = "water_fog",
                        label = "Цвет тумана в воде",
                        openedPicker = openedPicker,
                        hue = activeColors.waterFogHueState,
                        saturation = activeColors.waterFogSatState,
                        value = activeColors.waterFogValState,
                        hexString = activeColors.waterFogHexState,
                    ) { color -> activeColors.waterFogHexState.set(color.toHexString()) }

                    divider(horizontalMargin = Dp.ZERO, marginTop = sizes.largeGap, marginBottom = sizes.gap)

                    Row(width = Grow.Std) {
                        modifier.margin(vertical = sizes.smallGap)
                        Text("Размер солнца:") {
                            modifier.width(Grow.MinFit).margin(start = sizes.gap)
                                .alignY(AlignmentY.Center).font(MsdfFont(HACK_FONT, 24f))
                        }
                        Slider(sunSize.use(), 0f, 500f) {
                            modifier.width(Grow.Std)
                                .alignY(AlignmentY.Center)
                                .onChange { sunSize.set(it) }
                        }
                        Text("${sunSize.use().roundToInt()}") {
                            modifier.width(40.dp)
                                .textAlignX(AlignmentX.End)
                                .margin(start = sizes.gap)
                                .alignY(AlignmentY.Center)
                                .font(MsdfFont(HACK_FONT, 24f))
                        }
                    }
                    Row(width = Grow.Std) {
                        modifier.margin(vertical = sizes.smallGap)
                        Text("Размер луны:") {
                            modifier.width(Grow.MinFit).margin(start = sizes.gap)
                                .alignY(AlignmentY.Center).font(MsdfFont(HACK_FONT, 24f))
                        }
                        Slider(moonSize.use(), 0f, 500f) {
                            modifier.width(Grow.Std)
                                .alignY(AlignmentY.Center)
                                .onChange {
                                    moonSize.set(it)
                                }
                        }
                        Text("${moonSize.use().roundToInt()}") {
                            modifier.width(40.dp)
                                .textAlignX(AlignmentX.End)
                                .margin(start = sizes.gap)
                                .alignY(AlignmentY.Center).font(MsdfFont(HACK_FONT, 24f))
                        }
                    }
                }
            }
        }
    }

    private fun UiScope.CollapsibleColorPicker(
        pickerId: String,
        label: String,
        openedPicker: MutableStateValue<String?>,
        hue: MutableStateValue<Float>,
        saturation: MutableStateValue<Float>,
        value: MutableStateValue<Float>,
        hexString: MutableStateValue<String>?,
        onChange: ((Color) -> Unit)?
    ) {
        val isOpen = openedPicker.use() == pickerId
        Button(label) {
            modifier
                .width(Grow.Std)
                .margin(top = sizes.gap)
                .font(MsdfFont(HACK_FONT, 24f))
                .onClick { openedPicker.set(if (isOpen) null else pickerId) }
            if (isOpen) {
                modifier.backgroundColor(colors.primary)
            }
        }
        if (isOpen) {
            ColorChooserH(hue, saturation, value, hexString = hexString) {
                onChange?.invoke(it)
            }
        }
    }
}
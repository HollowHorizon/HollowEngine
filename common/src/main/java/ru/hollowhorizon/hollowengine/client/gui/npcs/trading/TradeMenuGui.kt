package ru.hollowhorizon.hollowengine.client.gui.npcs.trading

import com.mojang.blaze3d.Blaze3D
import imgui.ImVec4
import imgui.flag.ImGuiMouseButton
import imgui.flag.ImGuiStyleVar
import imgui.flag.ImGuiWindowFlags
import imgui.internal.ImGui
import net.minecraft.client.Minecraft
import net.minecraft.client.gui.GuiGraphics
import net.minecraft.client.gui.screens.Screen
import net.minecraft.locale.Language
import net.minecraft.util.Mth
import net.minecraft.world.item.ItemStack
import ru.hollowhorizon.hc.client.imgui.ImGuiHandler
import ru.hollowhorizon.hc.client.imgui.ImGuiMethods
import ru.hollowhorizon.hc.client.imgui.LoadFontEvent
import ru.hollowhorizon.hc.client.imgui.addons.ContainerProvider
import ru.hollowhorizon.hc.client.imgui.addons.ImGuiInventory.slot
import ru.hollowhorizon.hc.client.imgui.addons.ItemProperties
import ru.hollowhorizon.hc.client.utils.get
import ru.hollowhorizon.hc.client.utils.mcText
import ru.hollowhorizon.hc.client.utils.rl
import ru.hollowhorizon.hc.client.utils.toTexture
import ru.hollowhorizon.hc.common.capabilities.containers.HollowContainer
import ru.hollowhorizon.hc.common.events.SubscribeEvent
import ru.hollowhorizon.hollowengine.common.entities.NPCEntity
import ru.hollowhorizon.hollowengine.common.npcs.NPCCapability
import ru.hollowhorizon.hollowengine.common.npcs.trades.AddTradePacket
import ru.hollowhorizon.hollowengine.common.npcs.trades.ClearContainerPacket
import ru.hollowhorizon.hollowengine.common.npcs.trades.RemoveTradePacket
import ru.hollowhorizon.hollowengine.common.npcs.trades.SelectTradePacket
import kotlin.math.min


class TradeMenuGui(val npc: NPCEntity, val editMode: Boolean = false) : Screen("".mcText) {
    private val GUI = "hollowengine:textures/gui/trades/trade_menu.png".rl
    private val TRADE_ICON = "hollowengine:textures/gui/trades/trade_icon.png".rl
    private var scale = 1f
    var page = (npc[NPCCapability::class].currentTrade / 9).coerceAtLeast(0)
    private var pageCount = 0
    var selectedTrade = npc[NPCCapability::class].currentTrade % 9

    override fun init() {
        super.init()

        val window = Minecraft.getInstance().window
        scale = min(window.width * 0.9f / 420f, window.height * 0.9f / 170f)
    }

    override fun render(guiGraphics: GuiGraphics, mouseX: Int, mouseY: Int, partialTick: Float) {
        renderBackground(guiGraphics, mouseX, mouseY, partialTick)
        ImGuiHandler.drawFrame {
            ImGui.pushStyleVar(ImGuiStyleVar.WindowPadding, 0f, 0f)
            centredWindow(
                "Trade Menu",
                args = ImGuiWindowFlags.NoMove or ImGuiWindowFlags.NoResize or ImGuiWindowFlags.NoTitleBar or ImGuiWindowFlags.AlwaysAutoResize or ImGuiWindowFlags.NoBackground
            ) {
                drawBackground()

                pushFontSize((scale*10f).toInt()) {
                    drawInventory()
                    drawTrades()
                    drawCurrentTrade()
                }

                ImGuiMethods.drawEntity()
            }
            ImGui.popStyleVar()
        }
    }

    private fun ImGuiMethods.drawCurrentTrade() {
        if (selectedTrade == -1) return
        val slots = npc[NPCCapability::class].tradeContainer
        val trade = npc[NPCCapability::class].trades[page * 9 + selectedTrade]

        val spacing = ImGui.getStyle().itemSpacingX
        ImGui.getStyle().setItemSpacing(1f * scale, ImGui.getStyle().itemSpacingY)

        ImGui.popStyleVar()

        ImGui.setCursorPos(125f * scale, 8f * scale)
        drawSlotTwo(slots, 0)

        ImGui.sameLine()
        ImGui.setCursorPosY(ImGui.getCursorPosY() + 14f * scale)
        image("hollowengine:textures/gui/trades/plus_icon.png".rl, 20f * scale, 18 * scale)
        ImGui.sameLine()
        ImGui.setCursorPos(imgui.ImGui.getCursorPosX() - 2f * scale, ImGui.getCursorPosY() - 14f * scale)

        drawSlotTwo(slots, 1)
        ImGui.sameLine()
        ImGui.setCursorPosY(ImGui.getCursorPosY() + 14f * scale)
        image("hollowengine:textures/gui/trades/plus_icon.png".rl, 20f * scale, 18 * scale)
        ImGui.sameLine()
        ImGui.setCursorPos(imgui.ImGui.getCursorPosX() - 2f * scale, ImGui.getCursorPosY() - 14f * scale)
        drawSlotTwo(slots, 2)
        ImGui.sameLine()
        ImGui.setCursorPosY(ImGui.getCursorPosY() + 14f * scale)
        image("hollowengine:textures/gui/trades/equal_icon.png".rl, 22f * scale, 18 * scale)
        ImGui.sameLine()
        ImGui.setCursorPos(imgui.ImGui.getCursorPosX() - 2f * scale, ImGui.getCursorPosY() - 14f * scale)

        val cursor = ImGui.getCursorPos()

        ImGui.image(TRADE_ICON.toTexture().id, 32f * scale, 41f * scale)

        ImGui.setCursorPos(cursor.x + 8f * scale, cursor.y + 12f * scale)

        val light = if (ItemStack.matches(trade.output, slots.getItem(6))) 1f else 0.35f

        slot(
            6, trade.output, 18f * scale,
            red = 1f, green = 1f, blue = 1f, alpha = light,
            slots
        )

        ImGui.pushStyleVar(ImGuiStyleVar.WindowPadding, 0f, 0f)

        ImGui.getStyle().setItemSpacing(spacing, ImGui.getStyle().itemSpacingY)

        ContainerProvider.previousContainer = slots
    }

    fun drawSlotOne() {
        ImGuiMethods.image("hollowengine:textures/gui/trades/trade_slot_1.png".rl, 26f * scale, 28f * scale)
    }

    fun ImGuiMethods.drawSlotTwo(slots: HollowContainer, index: Int) {
        val trade = npc[NPCCapability::class].trades[page * 9 + selectedTrade]

        val item1 = slots.getItem(index * 2)
        val item2 = slots.getItem(index * 2 + 1)
        val isValid1 = ItemStack.isSameItemSameComponents(
            trade.inputs[index * 2],
            item1
        ) && item1.count >= trade.inputs[index * 2].count
        val isValid2 = ItemStack.isSameItemSameComponents(
            trade.inputs[index * 2 + 1],
            item2
        ) && item2.count >= trade.inputs[index * 2 + 1].count

        val cursor = ImGui.getCursorPos()
        image("hollowengine:textures/gui/trades/trade_slot_2.png".rl, 26f * scale, 45f * scale)
        ImGui.setCursorPos(cursor.x + 6f * scale, cursor.y + 3f * scale)
        slot(
            index * 2,
            if (item1.isEmpty) trade.inputs[index * 2] else item1,
            16f * scale,
            red = 1f,
            green = if (isValid1 or item1.isEmpty) 1f else 0.1f,
            blue = if (isValid1 or item1.isEmpty) 1f else 0.1f,
            alpha = if (isValid1 or !item1.isEmpty) 1f else 0.25f + (Mth.sin(
                Blaze3D.getTime().toFloat() * Mth.PI * 2f / 2f
            ) + 1f) / 2f * 0.25f,
            slots
        )
        ImGui.setCursorPos(cursor.x + 6f * scale, cursor.y + 21f * scale)
        slot(
            index * 2 + 1,
            if (item2.isEmpty) trade.inputs[index * 2 + 1] else item2,
            16f * scale,
            red = 1f,
            green = if (isValid2 or item2.isEmpty) 1f else 0.1f,
            blue = if (isValid2 or item2.isEmpty) 1f else 0.1f,
            alpha = if (isValid2 or !item2.isEmpty) 1f else 0.35f + (Mth.sin(
                Blaze3D.getTime().toFloat() * Mth.PI * 2f / 2f
            ) + 1f) / 2f * 0.25f,
            slots
        )

        ImGui.setCursorPos(cursor.x, cursor.y)
        imgui.ImGui.dummy(26f * scale, 45f * scale)
    }

    private fun drawBackground() {
        ImGuiMethods.image(GUI, 420f * scale, 170f * scale)

        ImGuiMethods.pushFontSize((10 * scale).toInt()) {
            var text = "Инвентарь"
            var size = ImGui.calcTextSize(text)
            ImGui.setCursorPos(277.5f * scale - size.x / 2, 69.5f * scale - size.y / 2)
            textShadow(text)

            text = "Торговля"
            size = ImGui.calcTextSize(text)
            ImGui.setCursorPos(170.5f * scale - size.x / 2, 64.5f * scale - size.y / 2)
            textShadow(text)
        }
    }

    private fun ImGuiMethods.drawInventory() {
        val player = Minecraft.getInstance().player ?: return

        ImGui.popStyleVar()
        player.inventory.items.subList(9, 36).forEachIndexed { index, item ->
            ImGui.setCursorPos((149f + 18f * ((index) % 9)) * scale, (87f + 18f * ((index) / 9)) * scale)
            slot(9 + index, item, 16f * scale, container = player.inventory)
        }

        player.inventory.items.subList(0, 9).forEachIndexed { index, item ->
            ImGui.setCursorPos((149f + 18f * ((index) % 9)) * scale, (145f + 18f * ((index) / 9)) * scale)
            slot(index, item, 16f * scale, container = player.inventory)
        }
        ImGui.pushStyleVar(ImGuiStyleVar.WindowPadding, 0f, 0f)

        ContainerProvider.previousContainer = player.inventory
    }

    private fun ImGuiMethods.drawEntity() {
        ImGui.setCursorPos(331f * scale, 10f * scale)
        entity(npc, 78f * scale, 83f * scale, scale = 1.45f, offsetX = scale, offsetY = 50f * scale, rotation = true)
        ImGui.setCursorPos(331f * scale, 10f * scale)
        image("hollowengine:textures/gui/trades/entity_overlay.png".rl, 78f * scale, 83f * scale)
    }

    private fun ImGuiMethods.drawTrades() {
        val trades = npc[NPCCapability::class].trades
        pageCount = trades.size / 9 + 1 - (if (trades.size % 9 == 0 && trades.size > 0 && !editMode) 1 else 0)

        val maxSize = min((page + 1) * 9, trades.size)

        for (i in page * 9..maxSize) {
            ImGui.setCursorPos((6f + 37f * (i % 9 % 3)) * scale, (8f + 47f * (i % 9 / 3)) * scale)
            if (i < maxSize) drawTradeIcon(i % 9)
            else if ((i % 9 != 0 || page * 9 == maxSize) && editMode) drawAddIcon()
        }

        if (ImGui.isPopupOpen("delete_trade")) popup("delete_trade") {
            button(Language.getInstance().getOrDefault("npc_trade_editor.delete_trade")) {
                RemoveTradePacket(npc.id, page * 9 + selectedTrade).send()
                selectedTrade = -1
                SelectTradePacket(npc.id, -1).send()
                ImGui.closeCurrentPopup()
            }
        }

        ImGui.setCursorPos(8f * scale, 149f * scale)
        val windowPos = ImGui.getWindowPos()
        var hovered = ImGui.isMouseHoveringRect(
            windowPos.x + 8f * scale,
            windowPos.y + 149f * scale,
            windowPos.x + 8f * scale + 25f * scale,
            windowPos.y + 149f * scale + 15f * scale
        )
        var light = if (hovered && page > 0) 0.8f else 1f
        ImGui.image(
            "hollowengine:textures/gui/trades/left_button.png".rl.toTexture().id,
            25f * scale,
            15f * scale,
            0f,
            0f,
            1f,
            1f,
            light,
            light,
            light,
            1f
        )
        if (ImGui.isItemClicked()) {
            page = (page - 1).coerceAtLeast(0)
        }

        ImGui.setCursorPos(85f * scale, 149f * scale)
        hovered = ImGui.isMouseHoveringRect(
            windowPos.x + 85f * scale,
            windowPos.y + 149f * scale,
            windowPos.x + 85f * scale + 25f * scale,
            windowPos.y + 149f * scale + 15f * scale
        )
        light = if (hovered && page < pageCount - 1) 0.8f else 1f
        ImGui.image(
            "hollowengine:textures/gui/trades/right_button.png".rl.toTexture().id,
            25f * scale,
            15f * scale,
            0f,
            0f,
            1f,
            1f,
            light,
            light,
            light,
            1f
        )
        if (ImGui.isItemClicked()) {
            page = (page + 1).coerceAtMost(pageCount - 1)
        }

        ImGui.setCursorPos(39f * scale, 149f * scale)
        ImGui.image("hollowengine:textures/gui/trades/counter.png".rl.toTexture().id, 40f * scale, 15f * scale)
        pushFontSize((10 * scale).toInt()) {
            val text = "${page + 1} / $pageCount"
            val textSize = imgui.ImGui.calcTextSize(text)
            ImGui.setCursorPos(59f * scale - textSize.x / 2, 156.5f * scale - textSize.y / 2)

            textShadow(text)
        }
    }

    private fun drawAddIcon() {
        val cursor = ImGui.getCursorPos()

        ImGui.popStyleVar()

        if (ImGui.invisibleButton("##trade_add_icon", 32f * scale, 41f * scale)) {
            AddTradePacket(npc.id).send()
        }
        val color = if (!ImGui.isItemHovered()) ImVec4(0.8f, 0.8f, 0.8f, 1f)
        else ImVec4(1f, 1f, 1f, 1f)
        ImGui.setCursorPos(cursor.x, cursor.y - 2 * scale)

        ImGui.image(
            "hollowengine:textures/gui/trades/add_trade_icon.png".rl.toTexture().id,
            36f * scale,
            43f * scale,
            0f,
            0f,
            1f,
            1f,
            color.x,
            color.y,
            color.z,
            color.w
        )

        ImGui.pushStyleVar(ImGuiStyleVar.WindowPadding, 0f, 0f)
    }

    private fun ImGuiMethods.drawTradeIcon(i: Int) {
        val cursor = ImGui.getCursorPos()

        ImGui.popStyleVar()

        if (ImGui.invisibleButton("##trade_icon_$i", 32f * scale, 41f * scale)) {
            selectedTrade = i
            if (!editMode) SelectTradePacket(npc.id, page * 9 + selectedTrade).send()
        }
        if (ImGui.isItemClicked(ImGuiMouseButton.Right) && editMode) {
            selectedTrade = i
            ImGui.openPopup("delete_trade")
        }
        val color = if (!ImGui.isItemHovered() && selectedTrade != i) ImVec4(0.8f, 0.8f, 0.8f, 1f)
        else ImVec4(1f, 1f, 1f, 1f)
        ImGui.setCursorPos(cursor.x, cursor.y)

        ImGui.image(
            TRADE_ICON.toTexture().id, 32f * scale, 41f * scale, 0f, 0f, 1f, 1f, color.x, color.y, color.z, color.w
        )

        val trade = npc[NPCCapability::class].trades[page * 9 + i]

        val item = trade.output

        ImGui.setCursorPos(cursor.x + 8f * scale, cursor.y + 12f * scale)
        item(
            item,
            18f * scale,
            18f * scale,
            properties = ItemProperties().apply {
                disableResize = true
                red = color.x
                green = color.y
                blue = color.z
                alpha = color.w
            }
        )

        ImGui.pushStyleVar(ImGuiStyleVar.WindowPadding, 0f, 0f)
    }

    override fun onClose() {
        super.onClose()
        SelectTradePacket(npc.id, -1).send()
        if (editMode) ClearContainerPacket(npc.id).send()
    }

    override fun isPauseScreen() = false
}

@SubscribeEvent
fun loadFontSizesEvent(event: LoadFontEvent) {
    event.loadFont(10)
    event.loadFont(20)
    event.loadFont(40)
    event.loadFont(50)
    event.loadFont(70)
    event.loadFont(90)
    event.loadFont(100)
}
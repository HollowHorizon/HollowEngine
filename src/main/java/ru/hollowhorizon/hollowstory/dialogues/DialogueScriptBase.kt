package ru.hollowhorizon.hollowstory.dialogues

import net.minecraft.client.Minecraft
import net.minecraft.client.audio.SimpleSound
import net.minecraftforge.registries.ForgeRegistries
import ru.hollowhorizon.hc.HollowCore
import ru.hollowhorizon.hc.client.utils.toRL
import ru.hollowhorizon.hc.client.utils.toSTC
import ru.hollowhorizon.hollowstory.client.screen.DialogueScreen

open class DialogueScriptBase(val screen: DialogueScreen, val player: HDCharacter) {
    val scene = screen.scene

    init {

    }

    infix fun HDCharacter.play(sound: String): HDCharacter {
        return this
    }
    infix fun HDCharacter.say(text: String) {
        val characters = screen.scene.characters
        if (screen.scene.autoSwitch && !characters.contains(this)) {
            screen.scene add this
        }
        if (characters.contains(this) && this != screen.scene.currentCharacter) {
            screen.scene.currentCharacter = this
        }
        screen.textBox?.text = text
        screen.currentName = this.name
        waitClick()
    }

    fun say(character: String, text: String) {
        screen.textBox?.text = text
        screen.currentName = character.toSTC()
        waitClick()
    }

    fun play(sound: String) {
        HollowCore.LOGGER.info("Playing sound ${ForgeRegistries.SOUND_EVENTS.getValue(sound.toRL())}")
        Minecraft.getInstance().soundManager.play(SimpleSound.forUI(ForgeRegistries.SOUND_EVENTS.getValue(sound.toRL()), 1F, 1F))
    }

    fun setBackground(background: String?) {
        screen.background = background
    }

    fun HDCharacter.say(text: String, time: Float) {
        val characters = screen.scene.characters
        if (screen.scene.autoSwitch && !characters.contains(this)) {
            screen.scene add this
        }
        if (characters.contains(this) && this != screen.scene.currentCharacter) {
            screen.scene.currentCharacter = this
        }
        screen.textBox?.text = text
        screen.currentName = this.name
        delay(time)
    }

    fun wait(time: Float) {
        delay(time)
    }

    fun delay(time: Float) {
        Thread.sleep((time * 1000).toLong())
    }

    fun choice(vararg pairs: Pair<String, () -> Unit>) {
        screen.createChoice(pairs)
    }

    fun stop() {
        screen.shouldClose = true
    }

    private fun waitClick() {
        try {
            this.screen.waitClick()
        } catch (e: InterruptedException) {
            e.printStackTrace()
        }
    }
}
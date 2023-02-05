package ru.hollowhorizon.hollowstory.dialogues

import ru.hollowhorizon.hollowstory.client.screen.DialogueScreen

class HDScene(val screen: DialogueScreen) {
    val characters = ArrayList<HDCharacter>()
    var characterToAdd: HDCharacter? = null
    var characterToRemove: HDCharacter? = null
    var currentCharacter: HDCharacter? = null
    var autoSwitch = true

    infix fun add(character: HDCharacter) {
        characterToAdd = character
        characters.add(character)
        screen.waitAddAnim()
    }

    infix fun add(image: HDImage) {
        screen.addImage(image)
    }

    infix fun addLeft(character: HDCharacter) {
        characterToAdd = character
        characters.add(0, character)
        screen.waitAddAnim()
    }

    infix fun addRight(character: HDCharacter) {
        add(character)
    }

    infix fun remove(character: HDCharacter) {
        characterToRemove = character
        screen.waitRemoveAnim()
    }

    infix fun remove(image: HDImage) {
        screen.removeImage(image)
    }

    infix fun focus(character: HDCharacter) {
        currentCharacter = character
        screen.waitFocusAnim()
    }

    fun clear() {
        characters.clear()
    }
}
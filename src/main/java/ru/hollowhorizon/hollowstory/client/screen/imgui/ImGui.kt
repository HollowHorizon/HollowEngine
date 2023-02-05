package ru.hollowhorizon.hollowstory.client.screen.imgui

import imgui.ImGui
import imgui.type.ImBoolean
import imgui.type.ImInt

fun begin(name: String, task: () -> Unit) {
    if (ImGui.begin(name)) {
        task()
    }
    ImGui.end()
}

fun begin(name: String, pOpen: ImBoolean, task: () -> Unit) {
    if (ImGui.begin(name, pOpen)) {
        task()
    }
    ImGui.end()
}

fun begin(name: String, pOpen: ImBoolean, flags: Int, task: () -> Unit) {
    if (ImGui.begin(name, pOpen, flags)) {
        task()
    }
    ImGui.end()
}

fun button(label: String, task: () -> Unit) {
    if (ImGui.button(label)) {
        task()
    }
}

fun button(label: String, width: Float, height: Float, task: () -> Unit) {
    if (ImGui.button(label, width, height)) {
        task()
    }
}

fun checkbox(label: String, checkbox: ImBoolean, task: (Boolean) -> Unit) {
    val value = checkbox.get()
    ImGui.checkbox(label, checkbox)
    if (value != checkbox.get()) {
        task(!value)
    }
}

val checkBoxList = mutableListOf<ImBoolean>()
var checkBoxCounter = -1
fun checkbox(label: String, task: (Boolean) -> Unit) {
    val checkbox = if (checkBoxCounter == -1) {
        val cb = ImBoolean(false)
        checkBoxList.add(cb)
        cb
    } else {
        checkBoxList[checkBoxCounter++]
    }

    checkbox(label, checkbox, task)
}

fun slider(label: String, value: FloatArray, min: Float, max: Float, task: (Float) -> Unit) {
    val oldValue = value[0]
    ImGui.sliderFloat(label, value, min, max)
    if (oldValue != value[0]) {
        task(value[0])
    }
}

fun slider(label: String, value: IntArray, min: Int, max: Int, task: (Int) -> Unit) {
    val oldValue = value[0]
    ImGui.sliderInt(label, value, min, max)
    if (oldValue != value[0]) {
        task(value[0])
    }
}

//int slider using list
val sliderList = mutableListOf<IntArray>()
var sliderCounter = -1
fun slider(label: String, min: Int, max: Int, task: (Int) -> Unit) {
    val slider = if (sliderCounter == -1) {
        val s = IntArray(1).apply { this[0] = 0 }
        sliderList.add(s)
        s
    } else {
        sliderList[sliderCounter++]
    }

    slider(label, slider, min, max, task)
}

//float slider using list
val sliderFloatList = mutableListOf<FloatArray>()
var sliderFloatCounter = -1
fun slider(label: String, min: Float, max: Float, task: (Float) -> Unit) {
    val slider = if (sliderFloatCounter == -1) {
        val s = FloatArray(1).apply { this[0] = 0f }
        sliderFloatList.add(s)
        s
    } else {
        sliderFloatList[sliderFloatCounter++]
    }

    slider(label, slider, min, max, task)
}

fun sameLine(vararg data: () -> Unit) {
    data.forEachIndexed { index, task ->
        if (index != 0) {
            ImGui.sameLine()
        }
        task()
    }
}

fun combo(label: String, currentItem: ImInt, items: Array<String>, task: (String) -> Unit) {
    val oldValue = currentItem.get()
    ImGui.combo(label, currentItem, items)
    if (oldValue != currentItem.get()) {
        task(items[currentItem.get()])
    }
}

//int combo using list
val comboList = mutableListOf<ImInt>()
var comboCounter = -1
fun combo(label: String, items: Array<String>, task: (String) -> Unit) {
    val combo = if (comboCounter == -1) {
        val c = ImInt(0)
        comboList.add(c)
        c
    } else {
        comboList[comboCounter++]
    }

    combo(label, combo, items, task)
}

fun listBox(label: String, currentItem: ImInt, items: Array<String>, task: (String) -> Unit) {
    val oldValue = currentItem.get()
    ImGui.listBox(label, currentItem, items)
    if (oldValue != currentItem.get()) {
        task(items[currentItem.get()])
    }
}

//int listBox using list
val listBoxList = mutableListOf<ImInt>()
var listBoxCounter = -1
fun listBox(label: String, items: Array<String>, task: (String) -> Unit) {
    val listBox = if (listBoxCounter == -1) {
        val c = ImInt(0)
        listBoxList.add(c)
        c
    } else {
        listBoxList[listBoxCounter++]
    }

    listBox(label, listBox, items, task)
}

fun child(label: String, width: Float, height: Float, border: Boolean, task: () -> Unit) {
    if (ImGui.beginChild(label, width, height, border)) {
        task()
    }
    ImGui.endChild()
}

fun child(label: String, width: Float, height: Float, border: Boolean, flags: Int, task: () -> Unit) {
    if (ImGui.beginChild(label, width, height, border, flags)) {
        task()
    }
    ImGui.endChild()
}

fun child(label: String, task: () -> Unit) {
    if (ImGui.beginChild(label)) {
        task()
    }
    ImGui.endChild()
}

fun lockLists() {
    checkBoxCounter = 0
    sliderCounter = 0
    sliderFloatCounter = 0
    comboCounter = 0
    listBoxCounter = 0
}

fun unlockLists() {
    clearLists()

    checkBoxCounter = -1
    sliderCounter = -1
    sliderFloatCounter = -1
    comboCounter = -1
    listBoxCounter = -1
}

fun clearLists() {
    checkBoxList.clear()
    sliderList.clear()
    sliderFloatList.clear()
    comboList.clear()
    listBoxList.clear()
}
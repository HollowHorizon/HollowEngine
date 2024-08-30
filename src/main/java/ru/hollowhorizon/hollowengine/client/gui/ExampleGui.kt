package ru.hollowhorizon.hollowengine.client.gui

import imgui.ImGui
import imgui.flag.ImGuiWindowFlags
import ru.hollowhorizon.hc.client.imgui.Graphics
import kotlin.random.Random

class ExampleGui : ImGuiScreen() {
    val registry = Registry<State, Track>(State()).apply {
        values += Track().apply {
            label = "My Track"

            for (i in 1..5) {
                channels[EventType.entries.random()] = Channel().apply {
                    label = "Channel $i"

                    for (j in 1..5) {
                        events.add(Event().apply {
                            time = Random.nextInt(100)
                            length = Random.nextInt(5, 25)
                        })
                    }
                }
            }
        }

    }

    override fun Graphics.draw() {
        ImGui.begin("ExampleGui", ImGuiWindowFlags.NoTitleBar)
        Sequentity.draw(registry)
        ImGui.end()
    }
}
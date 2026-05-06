package ru.hollowhorizon.hollowengine.common.scripting.katari

import ru.hollowhorizon.hollowengine.common.geary.components.AnimationControllerLayerSpec
import ru.hollowhorizon.hollowengine.common.geary.components.AnimationPlayMode
import ru.hollowhorizon.hollowengine.common.geary.components.ClipAnimationLayerSpec
import ru.hollowhorizon.hollowengine.common.geary.components.ProceduralLayerSpec
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertIs

class KatariAnimatorBuilderTest {
    @Test
    fun `builds clip controller and procedural layers`() {
        val component = KatariAnimatorBuilder()
            .clip("wave", "hollowengine:wave", AnimationPlayMode.Once, fadeIn = 0.1f, fadeOut = 0.2f)
            .controller("locomotion", "idle")
            .state("locomotion", "idle", "hollowengine:idle")
            .state("locomotion", "walk", "hollowengine:walk")
            .transition("locomotion", "idle", "walk", "speed > 0", "4")
            .procedural("upper_body")
            .boneTransform("upper_body", "right_arm", rotation = vectorExpression("10", "0", "0"))
            .build()

        assertEquals(true, component.enabled)
        assertEquals(3, component.layers.size)

        val clip = assertIs<ClipAnimationLayerSpec>(component.layers[0])
        assertEquals("wave", clip.id)
        assertEquals("hollowengine:wave", clip.animation)
        assertEquals(0.1f, clip.fadeIn)
        assertEquals(0.2f, clip.fadeOut)

        val controller = assertIs<AnimationControllerLayerSpec>(component.layers[1])
        assertEquals("idle", controller.entryState)
        assertEquals(listOf("idle", "walk"), controller.states.map { it.id })
        assertEquals("speed > 0", controller.transitions.single().condition.source)

        val procedural = assertIs<ProceduralLayerSpec>(component.layers[2])
        assertEquals("right_arm", procedural.transforms.single().bone)
        assertEquals("10", procedural.transforms.single().rotation?.x?.source)
    }

    @Test
    fun `snapshot restores configured layers`() {
        val restored = KatariAnimatorBuilder(false)
            .clip("idle", "hollowengine:idle")
            .build()

        assertEquals(false, restored.enabled)
        assertEquals("idle", restored.layers.single().id)
    }
}

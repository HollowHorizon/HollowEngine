package ru.hollowhorizon.hollowengine.common.scripting.story.functions.npcs

import de.fabmax.kool.math.Vec3f
import de.fabmax.kool.math.deg
import de.fabmax.kool.scene.TrsTransformF
import net.minecraft.world.entity.Entity
import net.minecraft.world.phys.Vec2
import net.minecraft.world.phys.Vec3
import ru.hollowhorizon.hollowengine.client.models.internal.Transform
import ru.hollowhorizon.hollowengine.common.entities.NpcEntity
import ru.hollowhorizon.hollowengine.common.geary.api.set
import ru.hollowhorizon.hollowengine.common.geary.components.Model
import ru.hollowhorizon.hollowengine.common.geary.components.StandardPlayerAnimatorPreset
import ru.hollowhorizon.hollowengine.common.geary.components.TransformComponent
import ru.hollowhorizon.hollowengine.common.utils.currentServer
import ru.hollowhorizon.hollowengine.common.utils.isValidRL
import ru.hollowhorizon.hollowengine.common.utils.literal
import kotlin.contracts.ExperimentalContracts

/**
 * Создаёт и возвращает нового NPC (`NpcEntity`) в указанной позиции и с заданными параметрами внешнего вида, поведения и анимации.
 *
 * Эта функция используется для быстрой генерации NPC с полной кастомизацией: модель, текстуры, анимации, масштаб, имя, и прочее.
 * Она подходит как для статических, так и для интерактивных персонажей, включая анимированных мобов, актёров сцен и сюжетных героев.
 *
 * ### Пример использования:
 * ```
 * npc(
 *     pos = Vec3(10.0, 64.0, 10.0),
 *     name = "Guide",
 *     model = "my_mod:models/entity/guide.gltf"
 * )
 * ```
 *
 * @param pos Позиция размещения NPC в мире (в мировых координатах).
 * @param name Отображаемое имя NPC. Показывается над головой, если [showName] = `true`.
 * @param model Путь к 3D-модели в формате `.gltf`. Используется для рендеринга внешности NPC.
 * @param rotation Начальная ориентация NPC в виде углов (yaw, pitch), в радианах.
 * @param world Идентификатор мира, в котором должен появиться NPC. Например: `"minecraft:overworld"`.
 * @param size Пара `(ширина, высота)` хитбокса NPC, влияет на столкновения и взаимодействие.
 * @param attributes Дополнительные числовые параметры NPC (например, `"health"` → `20f`, `"speed"` → `0.3f`), определяют поведение.
 * @param textures Словарь текстур по именам слоёв. Например: `"skin"` → `"modid:textures/entity/custom_skin.png"`.
 * @param transform Локальные трансформации модели: смещение, поворот и масштаб по всем осям.
 * @param showName Показывать ли имя NPC над головой (аналогично `nameTag` у сущностей Minecraft).
 * @param inverseHeadRotation Если `true`, голова NPC будет поворачиваться в противоположную сторону (например, для камеры от 3-го лица).
 *
 * @return Созданный экземпляр [NpcEntity], добавленный в мир.
 */
@OptIn(ExperimentalContracts::class)
fun npc(
    pos: Vec3,
    name: String = "NPC",
    model: String = "hollowengine:models/entity/player_model.gltf",
    rotation: Vec2 = rotation(0, 0),
    world: String = "minecraft:overworld",
    size: Pair<Float, Float> = 0.6f to 1.8f,
    attributes: Map<String, Float> = emptyMap(),
    textures: Map<String, String> = emptyMap(),
    transform: Transform = Transform(),
    showName: Boolean = true,
    inverseHeadRotation: Boolean = false,
): NpcEntity {
    assert(model.isValidRL()) {
        "Non [a-z0-9/._-] character in path of location: $model"
    }

    val level = currentServer.getLevel(currentServer.levelKeys().find { it.location().toString() == world }
        ?: throw IllegalStateException("Dimension $world not found!"))
        ?: throw IllegalStateException("Dimension $world is not loaded!")

    return NpcEntity(level).apply {
        setPos(pos.x, pos.y, pos.z)
        moveTo(pos.x, pos.y, pos.z, rotation.x, rotation.y)

        set(Model(model))
        set(StandardPlayerAnimatorPreset.create())
        set(TransformComponent(TrsTransformF().rotate(180f.deg, Vec3f.Y_AXIS)))

        if (attributes.isNotEmpty()) {
            setAttributes(attributes)
        }

        refreshDimensions()

        isCustomNameVisible = showName && name.isNotEmpty()
        customName = name.literal

        level.addFreshEntity(this)
    }
}

fun NpcEntity.despawn() {
    this.remove(Entity.RemovalReason.DISCARDED)
}

fun pos(x: Int, y: Int, z: Int) = Vec3(x + 0.5, y.toDouble(), z + 0.5)
fun pos(x: Double, y: Double, z: Double) = Vec3(x, y, z)
fun rotation(x: Int, y: Int) = Vec2(x.toFloat(), y.toFloat())
fun rotation(x: Float, y: Float) = Vec2(x, y)
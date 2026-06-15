package ru.hollowhorizon.hollowengine.common.scripting.story.functions.npcs

import kotlinx.coroutines.delay
import kotlinx.coroutines.withContext
import net.minecraft.core.BlockPos
import net.minecraft.core.registries.BuiltInRegistries
import net.minecraft.nbt.CompoundTag
import net.minecraft.nbt.TagParser
import net.minecraft.util.Mth
import net.minecraft.world.InteractionHand
import net.minecraft.world.entity.Entity
import net.minecraft.world.entity.item.ItemEntity
import net.minecraft.world.item.ItemStack
import net.minecraft.world.level.ClipContext
import net.minecraft.world.phys.Vec3
import ru.hollowhorizon.hollowengine.common.coroutines.dispatcher
import ru.hollowhorizon.hollowengine.common.entities.NpcEntity
import ru.hollowhorizon.hollowengine.common.npcs.navigation.rotate
import ru.hollowhorizon.hollowengine.common.scripting.katari.binding.ScriptBinding
import ru.hollowhorizon.hollowengine.common.utils.currentServer
import ru.hollowhorizon.hollowengine.common.utils.literal
import ru.hollowhorizon.hollowengine.common.utils.rl
import kotlin.time.Duration.Companion.milliseconds

/**
 * Перемещает NPC к указанной сущности до тех пор, пока расстояние не станет меньше или равно заданному.
 *
 * @param entity Сущность, к которой нужно переместиться.
 * @param dist Минимальное расстояние до сущности, при достижении которого движение прекращается.
 * @param speed Скорость перемещения NPC.
 */
@ScriptBinding
suspend fun NpcEntity.move(entity: Entity, dist: Double = 1.5, speed: Double = 1.0) {
    while (distanceTo(entity) > dist) {
        withContext(currentServer.dispatcher) {
            navigation.moveTo(entity.x, entity.y, entity.z, 0, speed)
        }
        delay(50.milliseconds)
    }
}

/**
 * Перемещает NPC к указанной сущности с настройками по умолчанию. (Можно указать через пробел)
 *
 * @param mob Сущность, к которой нужно переместиться.
 */
// @ScriptBinding
suspend infix fun NpcEntity.move(mob: Entity): Unit = move(entity = mob)

/**
 * Перемещает NPC к указанной позиции до тех пор, пока расстояние не станет меньше или равно заданному.
 *
 * @param pos Позиция, к которой нужно переместиться.
 * @param dist Минимальное расстояние до позиции, при достижении которого движение прекращается.
 * @param speed Скорость перемещения NPC.
 */
@ScriptBinding
suspend fun NpcEntity.move(pos: Vec3, dist: Double = 1.5, speed: Double = 1.0) {
    while (distanceToSqr(pos) > dist * dist || !navigation.isDone) {
        withContext(currentServer.dispatcher) {
            navigation.moveTo(navigation.createPath(pos.x, pos.y, pos.z, 0), speed)
        }
        delay(50.milliseconds)
    }
}

/**
 * Перемещает NPC к указанной позиции с настройками по умолчанию. (Можно указать через пробел)
 *
 * @param position Позиция, к которой нужно переместиться.
 */
// @ScriptBinding
suspend infix fun NpcEntity.move(position: Vec3): Unit = move(pos = position)

/**
 * Заставляет NPC посмотреть на указанную позицию за 30 тиков.
 *
 * @param position Позиция, на которую нужно смотреть.
 */
@ScriptBinding
suspend infix fun NpcEntity.lookAt(position: Vec3) {
    withContext(currentServer.dispatcher) {
        rotate({ position }, 1500)
    }
}

/**
 * Заставляет NPC посмотреть на указанную сущность за 30 тиков.
 *
 * @param entity Сущность, на которую нужно смотреть.
 */
@ScriptBinding
suspend infix fun NpcEntity.lookAt(entity: Entity) {
    withContext(currentServer.dispatcher) {
        rotate({ entity.eyePosition }, 1500)
    }
}

/**
 * Заставляет NPC использовать блок по указанной позиции.
 * NPC переместится к блоку, посмотрит на него и выполнит действие использования.
 *
 * @param pos Позиция блока, который нужно использовать.
 */
@ScriptBinding
suspend infix fun NpcEntity.useBlock(pos: Vec3) {
    lookAt(pos)
    val hit = level().clip(ClipContext(pos, pos, ClipContext.Block.OUTLINE, ClipContext.Fluid.NONE, this))
    swing(InteractionHand.MAIN_HAND)
    val state = level().getBlockState(hit.blockPos)
    state.useItemOn(mainHandItem, level(), fakePlayer, InteractionHand.MAIN_HAND, hit)
}

/**
 * Заставляет NPC разрушить блок по указанной позиции.
 * NPC переместится к блоку, посмотрит на него и разрушит его.
 *
 * @param pos Позиция блока, который нужно разрушить.
 */
@ScriptBinding
suspend infix fun NpcEntity.destroyBlock(pos: Vec3) {
    move(pos)
    lookAt(pos)
    val manager = fakePlayer.gameMode

    manager.destroyBlock(BlockPos(pos.x.toInt(), pos.y.toInt(), pos.z.toInt()))
    swing(InteractionHand.MAIN_HAND)
}

/**
 * Заставляет NPC выбросить предмет из инвентаря.
 *
 * @param item Предмет, который нужно выбросить.
 */
@ScriptBinding
fun NpcEntity.dropItem(item: ItemStack) {
    val p = position()
    val entityStack = ItemEntity(level(), p.x, p.y + eyeHeight, p.z, item)
    entityStack.setDefaultPickUpDelay()
    val f8 = Mth.sin(xRot * Mth.PI / 180f)
    val f3 = Mth.sin(yHeadRot * Mth.PI / 180f)
    val f4 = Mth.cos(yHeadRot * Mth.PI / 180f)
    entityStack.setDeltaMovement(-f3 * 0.3, -f8 * 0.3 + 0.1, f4 * 0.3)
    level().addFreshEntity(entityStack)
}

/**
 * Приостанавливает выполнение корутины на указанное количество тиков.
 *
 * @param time Количество тиков для задержки.
 */
@ScriptBinding
suspend fun wait(time: Int) {
    delay((time * 50L).milliseconds)
}

/**
 * Создает ItemStack с указанным предметом, количеством и NBT-тегом.
 *
 * @param item Идентификатор предмета (например, "minecraft:apple").
 * @param count Количество предметов в стаке.
 * @param nbt Строковое представление NBT-тега.
 * @return ItemStack с заданными параметрами.
 */
@ScriptBinding
fun item(item: String, count: Int = 1, nbt: String) = item(item, count, TagParser.parseTag(nbt))

/**
 * Создает ItemStack с указанным предметом, количеством и NBT-тегом.
 *
 * @param item Идентификатор предмета (например, "minecraft:apple").
 * @param count Количество предметов в стаке.
 * @param nbt NBT-тег для предмета.
 * @return ItemStack с заданными параметрами.
 */
@ScriptBinding
fun item(item: String, count: Int = 1, nbt: CompoundTag? = null): ItemStack {
    assert(BuiltInRegistries.ITEM.containsKey(item.rl)) { "Item $item not found!" }
    assert(count > 0) { "Item must be non-zero!" }

    return ItemStack(
        BuiltInRegistries.ITEM.get(item.rl),
        count
    )
}

/**
 * Переменная-геттер, которая переводит секунды в тики.
 *
 * @param this Число которое необходимо преобразовать из тиков в секунды
 * @template
 * ```scene.kts
 * 10.sec // Переводит 10 секунд в тики (10 * 20 = 200)
 * ```
 */
@ScriptBinding
val Int.sec get() = this * 20

@ScriptBinding
val Double.sec get() = (this * 20).toInt()

/**
 * Заставляет NPC "сказать" текст, отправляя сообщение всем игрокам на сервере.
 *
 * @param text Текст сообщения.
 */
@ScriptBinding
infix fun NpcEntity.say(text: String) {
    server?.playerList?.players?.forEach {
        it.sendSystemMessage("[$name]: $text".literal)
    }
}





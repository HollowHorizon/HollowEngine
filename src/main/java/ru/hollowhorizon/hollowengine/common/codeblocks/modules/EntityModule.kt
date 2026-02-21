package ru.hollowhorizon.hollowengine.common.codeblocks.modules

import ru.hollowhorizon.hollowengine.common.codeblocks.BlockCategoryBuilder
import ru.hollowhorizon.hollowengine.common.codeblocks.BlockModule
import ru.hollowhorizon.hollowengine.common.codeblocks.blocks.entity.*
import ru.hollowhorizon.hollowengine.common.codeblocks.blocks.entity.attributes.EntityGetBaseSpeed
import ru.hollowhorizon.hollowengine.common.codeblocks.blocks.entity.attributes.EntitySetBaseSpeed

object EntityModule : BlockModule {
    override fun BlockCategoryBuilder.build() {
        category("Сущности", icons.NPCS) {
            // --- Свойства и Геттеры ---
            block("Получить координаты") { EntityGetPosition() }
            block("Получить имя") { EntityGetName() }
            block("Вектор взгляда") { EntityGetLookAngle() }
            block("Предмет в главной руке") { EntityGetMainHandItem() }
            block("Предмет в левой руке") { EntityGetOffHandItem() }
            block("Получить модификатор скорости") { EntityGetBaseSpeed() }

            // --- Проверки (Booleans) ---
            block("Жива?") { EntityIsAlive() }
            block("Агрессивна?") { EntityIsAggressive() }
            block("Можно атаковать?") { EntityIsAttackable() }
            block("Невидима?") { EntityIsInvisible() }
            block("Неуязвима?") { EntityIsInvulnerable() }

            // Проверки окружения
            block("Плавает?") { EntityIsSwimming() }
            block("Под водой?") { EntityIsUnderwater() }
            block("В огне?") { EntityIsOnFire() }
            block("В лаве?") { EntityIsInLava() }

            // Проверки позы
            block("Стоит?") { EntityIsStanding() }
            block("Крадется?") { EntityIsCrouching() }
            block("Сидит?") { EntityIsCrouching() }
            block("Бежит?") { EntityIsRunning() }
            block("Спит?") { EntityIsSleeping() }

            // --- Действия ---
            block("Нанести урон") { EntityHurtBlock() }
            block("Потушить") { EntityClearFire() }
            block("Удалить сущность") { RemoveEntityBlock() }
            block("Толкнуть") { PushEntityBlock() }
            block("Махнуть главной рукой") { SwingMainHandBlock() }
            block("Махнуть левой рукой") { SwingOffHandBlock() }
            block("Задать модификатор скорости") { EntitySetBaseSpeed() }

            // --- Старое ---
            block("Рейкастинг") { EntityPickBlock() }
            block("Угол между") { EntityAngleBlock() }
            block("Добавить эффект") { EntityAddEffectBlock() }
            block("Убрать эффект") { EntityRemoveEffectBlock() }
        }
    }
}
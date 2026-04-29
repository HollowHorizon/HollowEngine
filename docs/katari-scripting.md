# Katari-скриптинг в HollowEngine

## Введение

Katari — это нарративный скриптовый язык, встроенный в HollowEngine. Скрипты пишутся в файлах с расширением `.ktr` и располагаются в папке `hollowengine/scripts/`. Они позволяют создавать интерактивные сценарные последовательности: диалоги, кат-сцены, перемещение NPC, ожидание действий игрока и многое другое.

Скрипты выполняются на серверной стороне и поддерживают сохранение/восстановление состояния (сериализация).

---

## Основы синтаксиса

### Комментарии

```
// Однострочный комментарий
```

### Переменные

```
val name = "Alice"
val count = 42
val flag = true
val position = pos(10.0, 64.0, 100.0)
```

### Типы данных

| Тип | Описание | Пример |
|------|-----------|---------|
| `Text` | Строка | `"Hello"` |
| `Int` | Целое число | `42` |
| `Double` | Число с плавающей точкой | `3.14` |
| `Bool` | Булево значение | `true` / `false` |
| `Position` | Позиция в мире | `pos(10, 64, 100)` |
| `EntityRef` | Ссылка на сущность | `player` |
| `NpcRef` | Ссылка на NPC | `npc(pos, "Name")` |
| `PlayerRef` | Ссылка на игрока | `player` |
| `ChatMessage` | Сообщение чата | результат `waitChat()` |
| `AnimatorController` | Контроллер анимаций | `animatorController()` |
| `InputEvent` | Событие ввода | результат `waitKey(...)` |

### Enum-типы

Enum-значения передаются как `Тип.Значение`, не строкой:

| Тип | Где используется | Значения |
|------|------------------|----------|
| `HitboxMode` | `entity.setHitboxMode(...)` | `HitboxMode.PULLING`, `HitboxMode.EMPTY`, `HitboxMode.BLOCKING` |
| `AnimationPlayMode` | `entity.playAnimation(...)` | `AnimationPlayMode.Once`, `AnimationPlayMode.Loop`, `AnimationPlayMode.ClampForever`, `AnimationPlayMode.PingPong` |
| `KatariInputAction` | `player.waitKey(...)`, `player.waitClick(...)` | `KatariInputAction.Press`, `KatariInputAction.Release`, `KatariInputAction.Repeat`, `KatariInputAction.Scroll` |
| `KatariInputKind` | `InputEvent.kind` | `KatariInputKind.Key`, `KatariInputKind.MouseButton`, `KatariInputKind.MouseScroll` |

---

## Нативные конструкции языка Katari

Katari предоставляет встроенные нарративные конструкции: `checkpoint`, `jump` и `choose`. Они являются ключевыми словами языка (не функциями) и компилируются напрямую в байт-код.

### `checkpoint <метка>` — метка для перехода

Устанавливает точку возврата в скрипте. На `checkpoint` можно перепрыгнуть с помощью `jump`.

```
checkpoint start
say("Это начало!")

checkpoint after_wait
wait(20)
say("Прошла 1 секунда")
```

### `jump <метка>` — безусловный переход

Перемещает выполнение скрипта к указанной метке `checkpoint`. Может использоваться внутри блоков `choose` для создания циклов и ветвлений.

```
checkpoint loop_start
say("Это бесконечный цикл!")
wait(20)
jump loop_start
```

### `choose { ... }` — блок выбора

Создаёт меню выбора для игрока. Каждая запись содержит текст опции и действие, выполняемое при выборе. Синтаксис:

```
choose {
    "Текст опции 1" -> { действие }
    "Текст опции 2" -> { действие }
}
```

#### Условные опции

К опции можно добавить условие видимости с помощью `if`:

```
choose {
    "Показать секрет" if hasKey -> {
        say("У тебя есть ключ!")
    }
    "Обычный вариант" -> {
        say("Обычный ответ")
    }
}
```

#### Отключаемые опции

Можно сделать опцию видимой, но недоступной для выбора, с помощью `disableIf`. Текст для недоступной опции задаётся через `with`:

```
choose {
    "Атаковать" disableIf isFriendly with "Вы не можете атаковать дружелюбного NPC" -> {
        say("Вы атакуете!")
    }
    "Поговорить" -> {
        say("Мирный разговор")
    }
}
```

Также можно совмещать `disableIf` и `with` в одной строке через оператор `with`:

```
choose {
    "Открыть дверь" disableIf !hasKey with "Нужен ключ" -> {
        say("Дверь открыта!")
    }
}
```

#### Переходы внутри choose

Внутри действия опции можно использовать `jump` для перехода к метке:

```
checkpoint start

choose {
    "Пойти налево" -> jump left_path
    "Пойти направо" -> jump right_path
    "Выйти" -> {
        say("До свидания!")
    }
}

checkpoint left_path
say("Вы пошли налево")
jump start

checkpoint right_path
say("Вы пошли направо")
jump start
```

#### Как это работает

Блок `choose` компилируется в вызов встроенной функции `chooseIndexed`, которая через интерфейс `NarrativeHost` отправляет игроку список опций. Игрок выбирает опцию через команду `/hollowengine katari choose <runId> <optionId>`. После выбора выполняется соответствующее действие.

---

## Встроенные нарративные функции

Эти функции являются частью ядра Katari и работают через интерфейс `NarrativeHost`. В HollowEngine они реализованы через отправку сообщений в чат с кликабельными командами.

### `narrate(text)` — вывод текста

Отображает текст игроку. В HollowEngine текст отправляется в чат. Это эквивалент простой строки в скрипте:

```
narrate("Привет, мир!")
// или просто:
"Привет, мир!"
```

Любая строка, стоящая как отдельное выражение, автоматически вызывает `narrate`.

### `choose(...)` — выбор из вариантов

Принимает произвольное количество строк-опций. Возвращает текст выбранной опции. Все опции видны и доступны.

```
val choice = choose("Пойти налево", "Пойти направо", "Прямо")
say("Вы выбрали: " + choice)
```

### `chooseIndexed(...)` — выбор с индексом

То же, что `choose`, но возвращает индекс выбранной опции (начиная с 0) в виде строки. Используется внутри блока `choose { }`.

### `chooseExhaustible(...)` — исчерпываемый выбор

То же, что `choose`, но `null`-опции (недоступные варианты) не показываются игроку. Полезно для диалогов, где некоторые варианты уже были использованы.

```
val response = chooseExhaustible(
    "Расскажи историю",
    "Пока",
    null  // этот вариант не покажется
)
```

### `choiceOption(text, visible, enabled, disabledText)` — кастомная опция

Создаёт опцию с тонкой настройкой видимости и доступности. Используется внутри `choose { }` блоков.

Параметры:
- `text` — текст опции
- `visible` — видна ли опция (boolean)
- `enabled` — доступна ли для выбора (boolean)
- `disabledText` — текст, показываемый вместо `text`, если опция отключена (или `null`)

### `readLine(question)` — ввод текста

Запрашивает у игрока текстовый ввод. Возвращает введённую строку.

```
val name = readLine("Как тебя зовут?")
say("Приятно познакомиться, " + name + "!")
```

---

## Глобальные переменные

В скриптах доступны следующие глобальные переменные:

| Переменная | Тип | Описание |
|-----------|------|-------------|
| `player` | `PlayerRef` | Игрок, запустивший скрипт (или `null`) |
| `server` | `Server` | Текущий сервер |
| `level` | `Level` | Текущий уровень (измерение) |

---

## Базовые функции

### `say(text)` — отправка сообщения всем игрокам

```
say("Hello, world!")
```

### `pos(x, y, z)` — создание позиции

Именованные параметры: `x`, `y`, `z`.

```
val p = pos(10.0, 64.0, 100.0)
```

### `blockPos(x, y, z)` — создание позиции блока

Именованные параметры: `x`, `y`, `z`.

```
val p = blockPos(10, 64, 100)
```

### `item(item, count, nbt)` — создание предмета

Именованные параметры: `item` (обязательный), `count` (по умолч. `1`), `nbt` (по умолч. `null`).

```
val apple = item("minecraft:apple")
val sword = item("minecraft:diamond_sword", count = 1, nbt = "{display:{Name:'{\"text\":\"Blade\"}'}}")
```

### `number.sec` — перевод секунд в тики

```
wait(10.sec)   // 10 секунд
wait(1.5.sec)  // 1.5 секунды
```

### Свойства позиции

```
val p = pos(10.4, 64.0, 100.8)
p.blockX  // -> 10
p.blockY  // -> 64
p.blockZ  // -> 100
```

### `level(name)` — получение уровня по ID

```
val nether = level("minecraft:the_nether")
```

### `player(name)` — получение игрока по имени или UUID

```
val p = player("Steve")       // по имени
val p = player("uuid-here")   // по UUID
val p = player()              // текущий sourcePlayer
```

### `wait(ticks)` — ожидание в тиках (20 тиков = 1 секунда)

```
wait(20)   // ждём 1 секунду
wait(100)  // ждём 5 секунд
```

### `waitTime(timeOfDay)` — ожидание определённого времени суток (в тиках)

```
waitTime(0)     // ждём рассвета (0:00)
waitTime(6000)  // ждём полдня
waitTime(13000) // ждём заката
waitTime(18000) // ждём ночи
```

### `waitDay()` — ожидание наступления дня

```
waitDay()
```

### `waitNight()` — ожидание наступления ночи

```
waitNight()
```

### `command(text)` — выполнение консольной команды

```
command("give @a diamond 1")
```

### `runScript(path)` — запуск другого Katari-скрипта

```
val runId = runScript("dialogue_example.ktr")
```

---

## Работа с NPC

### Создание NPC

Именованные параметры: `pos` (обязательный), `name` (по умолч. `"NPC"`), `model` (по умолч. `"hollowengine:models/entity/player_model.gltf"`), `world` (по умолч. из позиции).

```
// npc(pos, name, model, world)
val npc = npc(
    pos = pos(10.0, 64.0, 100.0),
    name = "John",
    model = "hollowengine:models/entity/player_model.gltf",
    world = "minecraft:overworld"
)

// npc(pos, name) — с моделью по умолчанию
val npc = npc(pos(10.0, 64.0, 100.0), name = "John")

// npc(pos) — с именем и моделью по умолчанию
val npc = npc(pos(10.0, 64.0, 100.0))
```

### Свойства сущностей (EntityRef/NpcRef/PlayerRef)

Свойства читаются и записываются через точку, без скобок:

| Свойство | Тип | Чтение | Запись |
|---------|------|--------|--------|
| `entity.name` | `Text` | Имя сущности | Установка кастомного имени |
| `entity.uuid` | `Text` | UUID сущности | Изменение UUID |
| `entity.customName` | `Text?` | Кастомное имя (или `null`) | Установка/сброс кастомного имени |
| `entity.alive` | `Bool` | Жива ли сущность | — (только чтение) |
| `entity.invulnerable` | `Bool` | Неуязвима ли | Установка неуязвимости |
| `entity.sprinting` | `Bool` | Бежит ли | Установка бега |
| `entity.health` | `Double` | Текущее здоровье | Установка здоровья |
| `entity.position` | `Position` | Текущая позиция | — (только чтение) |
| `entity.dimension` | `Text` | ID измерения | — (только чтение) |
| `entity.mainHand` | `Text` | Предмет в основной руке | — (только чтение) |

Примеры:

```
// Чтение свойств
val name = npc.name
val isAlive = npc.alive
val pos = npc.position
val dim = npc.dimension
val hp = npc.health
val item = npc.mainHand
val isInvul = npc.invulnerable
val isRunning = npc.sprinting

// Запись свойств (присваивание)
npc.name = "New Name"
npc.customName = "Custom Name"
npc.health = 20.0
npc.invulnerable = true
npc.sprinting = true
npc.uuid = "00000000-0000-0000-0000-000000000000"
```

### Методы NPC/Entity

#### `entity.setHitboxMode(mode)` — режим хитбокса

Тип `mode`: `HitboxMode`.

```
npc.setHitboxMode(HitboxMode.PULLING)   // стандартный (толкает)
npc.setHitboxMode(HitboxMode.EMPTY)     // проходимый
npc.setHitboxMode(HitboxMode.BLOCKING)  // блокирующий
```

#### `npc.move(target, dist, speed)` — перемещение к цели

Именованные параметры: `entity` или `pos` (обязательный), `dist` (по умолч. `1.5`), `speed` (по умолч. `1.0`).

```
// К позиции
npc.move(pos = pos(100.0, 64.0, 200.0), dist = 0.5, speed = 1.0)

// К другому NPC или игроку
npc.move(entity = player, dist = 0.5, speed = 1.2)

// С параметрами по умолчанию
npc.move(player)
```

#### `npc.lookAt(target)` — поворот к цели

Аргументом может быть сущность или позиция.

```
npc.lookAt(player)
npc.lookAt(pos(0.0, 64.0, 0.0))
```

#### `entity.teleport(position)` — телепортация на позицию

```
npc.teleport(pos(0.0, 64.0, 0.0))
```

#### `entity.teleportTo(target)` — телепортация к сущности

```
npc.teleportTo(player)
```

#### `entity.remove()` / `entity.despawn()` — удаление NPC

```
npc.remove()
```

#### `entity.swing()` — взмах рукой

```
npc.swing()
```

#### `npc.useBlock(position)` — использование блока

```
npc.useBlock(pos(10.0, 64.0, 10.0))
```

#### `npc.destroyBlock(position)` — разрушение блока

```
npc.destroyBlock(pos(10.0, 64.0, 10.0))
```

#### `npc.dropItem(item)` — выброс предмета

```
npc.dropItem(item("minecraft:apple", count = 3))
```

#### `entity.heal(amount)` — лечение

```
npc.heal(5.0)
```

#### `entity.setHealth(value)` — установка здоровья

```
npc.setHealth(20.0)
```

#### `entity.setModel(model, controller)` — смена модели

```
npc.setModel("hollowengine:models/entity/player_model.gltf", "player_model.animation-controller.kts")
```

#### `entity.setTransform(x, y, z, scale)` — установка трансформации

```
npc.setTransform(0.0, 0.0, 0.0, 1.0)
```

#### `entity.playAnimation(animation, playMode, fadeIn, fadeOut)` — проигрывание анимации

Именованные параметры: `animation` (обязательный), `playMode` (по умолч. `AnimationPlayMode.Once`), `fadeIn` (по умолч. `0.33`), `fadeOut` (по умолч. `0.33`).

```
npc.playAnimation(animation = "walk", playMode = AnimationPlayMode.Loop, fadeIn = 0.33, fadeOut = 0.33)
npc.playAnimation(animation = "jump", playMode = AnimationPlayMode.Once, fadeIn = 0.1, fadeOut = 0.1)
npc.playAnimation("walk")  // playMode=AnimationPlayMode.Once, fadeIn=0.33, fadeOut=0.33
```

Режимы проигрывания: `AnimationPlayMode.Once`, `AnimationPlayMode.Loop`, `AnimationPlayMode.ClampForever`, `AnimationPlayMode.PingPong`.

#### `entity.stopAnimation(animation, fadeOut)` — остановка анимации

Именованные параметры: `animation` (обязательный), `fadeOut` (по умолч. `0.33`).

```
npc.stopAnimation(animation = "walk", fadeOut = 0.33)
npc.stopAnimation("walk")  // fadeOut=0.33
```

#### `entity.attack(target)` — атака цели

```
npc.attack(player)
npc.attack(null)  // сброс цели
```

#### `entity.say(text)` — речь от лица NPC

```
npc.say("Hello there!")
```

#### `entity.getAttribute(name)` — получение атрибута

```
val speed = npc.getAttribute("minecraft:generic.movement_speed")
```

#### `entity.setAttribute(name, value)` — установка атрибута

```
npc.setAttribute("minecraft:generic.max_health", 40.0)
```

#### `entity.stopNavigation()` — остановка навигации

```
npc.stopNavigation()
```

### Методы игрока (PlayerRef)

#### `player.give(item, count)` — выдача предмета

```
player.give("minecraft:diamond", 1)
player.give("minecraft:apple", 5)
```

#### `player.playSound(sound, volume, pitch)` — проигрывание звука игроку

```
player.playSound("minecraft:entity.experience_orb.pickup", 1.0, 1.0)
```

---

## Ожидание событий (триггеры)

### `entity.waitNpcInteract()` — ожидание взаимодействия с NPC

```
val who = npc.waitNpcInteract()
say("Player " + who.name + " interacted with the NPC!")
```

### `waitChat()` — ожидание сообщения в чате

```
val msg = waitChat()
val who = msg.player
val text = msg.text
say(who.name + " said: " + text)
```

### `player.waitZone(position, radius, leave)` — ожидание входа/выхода из зоны

Именованные параметры: `position` (обязательный), `radius` (по умолч. `1.0`), `leave` (по умолч. `false`).

```
// Ожидание входа в зону
val who = player.waitZone(position = pos(10.0, 64.0, 10.0), radius = 5.0, leave = false)

// Ожидание выхода из зоны
val who = player.waitZone(position = pos(10.0, 64.0, 10.0), radius = 5.0, leave = true)

// С параметрами по умолчанию
val who = player.waitZone(pos(10.0, 64.0, 10.0))
```

### Свойства ChatMessage

```
msg.player  // -> PlayerRef
msg.text    // -> Text
```

---

## Ввод с клавиатуры и мыши

### `player.waitKey(keyCode)` — ожидание нажатия клавиши

```
val input = player.waitKey(32)  // пробел (GLFW_KEY_SPACE)
```

### `player.waitKey(keyCode, action)` — ожидание действия с клавишей

Именованные параметры: `key` (обязательный), `action` (по умолч. `KatariInputAction.Press`).

```
val input = player.waitKey(key = 32, action = KatariInputAction.Press)    // нажатие
val input = player.waitKey(key = 32, action = KatariInputAction.Release)  // отпускание
val input = player.waitKey(key = 32, action = KatariInputAction.Repeat)   // повтор
val input = player.waitKey(32)  // action=KatariInputAction.Press
```

### `player.waitClick(button)` — ожидание клика мыши

```
val input = player.waitClick(0)  // левая кнопка
val input = player.waitClick(1)  // правая кнопка
```

### `player.waitClick(button, action)` — ожидание действия с кнопкой мыши

Именованные параметры: `button` (обязательный), `action` (по умолч. `KatariInputAction.Press`).

```
val input = player.waitClick(button = 0, action = KatariInputAction.Press)
val input = player.waitClick(button = 0, action = KatariInputAction.Release)
val input = player.waitClick(0)  // action=KatariInputAction.Press
```

### `player.waitScroll()` — ожидание прокрутки

```
val input = player.waitScroll()
```

### Свойства InputEvent

Все свойства — только для чтения (геттеры):

```
input.kind     // -> KatariInputKind.Key | KatariInputKind.MouseButton | KatariInputKind.MouseScroll
input.action   // -> KatariInputAction.Press | KatariInputAction.Release | KatariInputAction.Repeat | KatariInputAction.Scroll
input.key      // -> Int (код клавиши)
input.scanCode // -> Int (скан-код)
input.button   // -> Int (кнопка мыши)
input.modifiers // -> Int (модификаторы клавиатуры)
input.x        // -> Double
input.y        // -> Double
input.scrollX  // -> Double
input.scrollY  // -> Double
```

---

## Звуки

### `playSound(sound, position, volume, pitch)` — проигрывание звука в мире

Именованные параметры: `sound` (обязательный), `position` (обязательный), `volume` (по умолч. `1.0`), `pitch` (по умолч. `1.0`).

```
playSound(
    sound = "minecraft:entity.creeper.primed",
    position = pos(10.0, 64.0, 10.0),
    volume = 1.0,
    pitch = 1.0
)

// С параметрами по умолчанию
playSound("minecraft:entity.creeper.primed", pos(10.0, 64.0, 10.0))
```

### `player.playSound(sound, volume, pitch)` — проигрывание звука игроку

Именованные параметры: `sound` (обязательный), `volume` (по умолч. `1.0`), `pitch` (по умолч. `1.0`).

```
player.playSound(sound = "minecraft:entity.experience_orb.pickup", volume = 1.0, pitch = 1.0)
```

---

## Анимации (AnimatorController)

Методы `AnimatorController` описывают конфигурацию контроллера и пока используют строковые значения для `playMode` и `blendMode`. Enum `AnimationPlayMode` используется в runtime-вызове `entity.playAnimation(...)`.

### Создание контроллера

```
val anim = animatorController()              // включён (enabled = true)
val anim = animatorController(enabled = false)  // выключен
// или
val anim = animator()
```

### Свойство AnimatorController

```
anim.enabled = true   // включить аниматор
anim.enabled = false  // выключить аниматор
val isEnabled = anim.enabled  // проверить, включён ли
```

### Методы AnimatorController

#### `anim.clip(id, animation)` — добавление клипа (анимационного слоя)

```
anim.clip("walk", "walk_animation")
```

#### `anim.clip(id, animation, playMode, speed, weight, priority, blendMode, fadeIn, fadeOut)` — расширенный клип

```
anim.clip("run", "run_animation", "loop", "2", "1", 0, "override", 0.2, 0.2)
```

#### `anim.clip(id, animation, playMode, speed, weight, priority, blendMode, mask, fadeIn, fadeOut, referencePose, removeOnEnd)` — полный клип (именованные параметры)

```
anim.clip(
    id = "idle",
    animation = "idle_anim",
    playMode = "loop",
    speed = "1",
    weight = "1",
    priority = 0,
    blendMode = "override",
    mask = "head,arms",
    fadeIn = 0.1,
    fadeOut = 0.1,
    referencePose = "",
    removeOnEnd = true
)
```

#### `anim.controller(id, entryState)` — добавление контроллера состояний

```
anim.controller("main", "idle")
```

#### `anim.controller(id, entryState, weight, priority, blendMode, mask, fadeIn, fadeOut)` — расширенный контроллер (именованные параметры)

```
anim.controller(
    id = "main",
    entryState = "idle",
    weight = "1",
    priority = 0,
    blendMode = "override",
    mask = "",
    fadeIn = 0.1,
    fadeOut = 0.1
)
```

#### `anim.state(controllerId, stateId, animation, playMode)` — добавление состояния

```
anim.state("main", "idle", "idle_anim", "loop")
anim.state("main", "walk", "walk_anim", "loop")
```

#### `anim.state(controllerId, stateId, animation, playMode, speed, referencePose)` — расширенное состояние (именованные параметры)

```
anim.state(
    controllerId = "main",
    stateId = "run",
    animation = "run_anim",
    playMode = "loop",
    speed = "2",
    referencePose = ""
)
```

#### `anim.transition(controllerId, from, to, condition, duration)` — добавление перехода

```
anim.transition("main", "idle", "walk", "speed > 0.1", "0.2")
anim.transition("main", "", "idle", "speed <= 0.1", "0.3")  // из любого состояния
```

#### `anim.transition(controllerId, from, to, condition, duration, priority, exitTime)` — расширенный переход (именованные параметры)

```
anim.transition(
    controllerId = "main",
    from = "walk",
    to = "run",
    condition = "speed > 2",
    duration = "0.15",
    priority = 1,
    exitTime = 0.5
)
```

#### `anim.procedural(id)` — добавление процедурного слоя

```
anim.procedural("head_aim")
```

#### `anim.procedural(id, weight, priority, blendMode, mask, fadeIn, fadeOut)` — расширенный процедурный слой (именованные параметры)

```
anim.procedural(
    id = "head_aim",
    weight = "1",
    priority = 0,
    blendMode = "additive",
    mask = "head",
    fadeIn = 0.2,
    fadeOut = 0.2
)
```

#### `anim.boneTransform(layerId, bone, tx, ty, tz, rx, ry, rz, sx, sy, sz)` — трансформация кости

```
anim.boneTransform("head_aim", "head", "0", "0", "0", "sin(time)", "0", "0", "1", "1", "1")
```

#### `anim.removeLayer(id)` — удаление слоя

```
anim.removeLayer("walk")
```

#### `anim.clear()` — очистка всех слоёв

```
anim.clear()
```

### Применение аниматора к NPC

```
npc.setAnimator(anim)
```

### Режимы смешивания (blendMode)

| Значение | Описание |
|----------|----------|
| `"override"` / `"replace"` | Замещение |
| `"add"` / `"additive"` | Аддитивное смешивание |

### Маска костей (mask)

Список имён костей через запятую. Пустая строка = полная маска.

```
"head,arms,spine"
```

---

## Полные примеры скриптов

### Пример 1: Простой диалог с NPC

```
// Создаём NPC
val guard = npc(
    pos = pos(10.0, 64.0, 100.0),
    name = "Guard",
    model = "hollowengine:models/entity/player_model.gltf"
)
guard.invulnerable = true

// Ждём, пока игрок подойдёт
val who = guard.waitNpcInteract()
guard.lookAt(who)

// Диалог
guard.say("Hello, " + who.name + "!")
guard.say("Welcome to our village.")
wait(40)
guard.say("Be careful out there.")
```

### Пример 2: Ожидание времени и событие

```
say("Waiting for night...")
waitNight()
say("It's night time! Spawning monsters...")

val monster = npc(pos(0.0, 64.0, 0.0), name = "Zombie")
monster.attack(player)
```

### Пример 3: Анимация NPC

```
val npc = npc(pos(10.0, 64.0, 10.0), name = "Dancer")

val anim = animatorController()
anim.clip("dance", "dance_animation", "loop", "1", "1", 0, "override", 0.3, 0.3)
npc.setAnimator(anim)

wait(100)
npc.playAnimation(animation = "wave", playMode = AnimationPlayMode.Once, fadeIn = 0.2, fadeOut = 0.2)
```

### Пример 4: Ожидание ввода

```
say("Press SPACE to continue!")

val input = player.waitKey(32)  // GLFW_KEY_SPACE = 32
say("You pressed space! Continuing...")

say("Click left mouse button!")
val click = player.waitClick(0)
say("You clicked at (" + click.x + ", " + click.y + ")")
```

### Пример 5: Контроллер состояний анимации

```
val npc = npc(pos(0.0, 64.0, 0.0), name = "Patrol")

val anim = animatorController()
anim.controller("movement", "idle")
anim.state("movement", "idle", "idle_anim", "loop")
anim.state("movement", "walk", "walk_anim", "loop")
anim.state("movement", "run", "run_anim", "loop")
anim.transition("movement", "idle", "walk", "speed > 0.1", "0.2")
anim.transition("movement", "walk", "idle", "speed <= 0.1", "0.3")
anim.transition("movement", "walk", "run", "speed > 2", "0.15")
anim.transition("movement", "run", "walk", "speed <= 2", "0.2")
npc.setAnimator(anim)

npc.move(pos = pos(50.0, 64.0, 50.0), speed = 1.0, dist = 0.5)
wait(100)
npc.move(pos = pos(0.0, 64.0, 0.0), speed = 2.0, dist = 0.5)
```

### Пример 6: Диалог с выбором и циклическими переходами (checkpoint/jump/choose)

```
checkpoint start

val npc = npc(pos(10.0, 64.0, 100.0), name = "Mysterious Stranger")
npc.invulnerable = true

npc.say("Привет, путник! Чем могу помочь?")

checkpoint menu
choose {
    "Кто ты?" -> {
        npc.say("Я — странник, путешествующий между мирами.")
        jump menu
    }
    "Расскажи историю" -> {
        npc.say("Когда-то давно...")
        wait(40)
        npc.say("...этот мир был совсем другим.")
        jump menu
    }
    "Торговать" disableIf !hasCoin with "У тебя нет монет" -> {
        npc.say("Отличный выбор! Смотри, что у меня есть.")
        player.give("minecraft:diamond", 1)
        jump menu
    }
    "Пока" -> {
        npc.say("До встречи, путник!")
    }
}
```

### Пример 7: Использование narrate, choose и readLine

```
val name = readLine("Назови своё имя:")
narrate("Рад познакомиться, " + name + "!")

val choice = choose(
    "Расскажи о себе",
    "Покажи фокус",
    "Уйти"
)

if (choice == "Расскажи о себе") {
    narrate("Я — скриптовый NPC в мире HollowEngine!")
} else if (choice == "Покажи фокус") {
    narrate("Абракадабра! ✨")
} else {
    narrate("До свидания!")
}
```

### Пример 8: Зона входа и звук

```
val pos = pos(100.0, 64.0, 100.0)
say("Go to the marked area!")

val who = player.waitZone(position = pos, radius = 3.0, leave = false)
playSound(
    sound = "minecraft:block.note_block.chime",
    position = pos,
    volume = 1.0,
    pitch = 1.0
)
say("You entered the zone!")
```

---

## Команды управления скриптами

Все команды выполняются через `/hollowengine katari`:

| Команда | Описание |
|---------|----------|
| `/hollowengine katari run <path>` | Запуск скрипта |
| `/hollowengine katari stop <id или path>` | Остановка скрипта |
| `/hollowengine katari stop all` | Остановка всех скриптов |
| `/hollowengine katari list` | Список запущенных скриптов |
| `/hollowengine katari choose <runId> <optionId>` | Выбор опции в диалоге |

---

## Примечания

- Скрипты должны иметь расширение `.ktr`.
- Скрипты автоматически сохраняют состояние и могут быть восстановлены после перезапуска сервера.
- Если скрипт был изменён после сохранения, восстановление будет пропущено с ошибкой.
- Все функции `wait*` являются приостанавливаемыми (suspend) — скрипт ждёт, не блокируя сервер.
- Функции без префикса `wait*` выполняются мгновенно.
- Именованные параметры позволяют опускать необязательные аргументы со значениями по умолчанию.
- Свойства сущностей читаются и записываются через точку (например, `npc.name`, `npc.health = 20.0`), без вызова методов.

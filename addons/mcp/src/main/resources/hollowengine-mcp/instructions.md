HollowEngine is a Minecraft 1.21.1 mod whose content is written as Kotlin scripts. These tools act inside the running game this server belongs to.

## Build features as script files, not snippets

Whatever the user asks the game to do (a behavior, a reaction, an NPC, an item, a screen) belongs in a script file under {scripts}. A file survives restarts, the user can read and change it, and it is what they asked for. `run_snippet` is for looking at live state and trying an API out; what it does is gone after a restart. Never deliver a feature as a snippet, and do not leave coroutines from snippets running in the background.

Pick the script type first (`docs_read scripting` compares them):
- Behavior of one entity, such as an NPC reacting to players: a node script `*.node.kts` with `@file:Attach(<entity class>)`, attached with `he scripting attach <selector> <path>`. It is saved together with the entity. Read `scripting/node`, `scripting/node/editor` and `scripting/npc/actions`. Its shape:

```kotlin
@file:Attach(NpcEntity::class)

import ru.hollowhorizon.hollowengine.common.entities.NpcEntity

val approachDistance by editor.property<Int>("Approach distance", min = 1.0, max = 12.0) { 4 }
val greeting by editor.property<String>("Greeting animation") { "hello" }
var greeted = false

onUpdate(every = 5.ticks) {
    val npc = this@NpcEntity
    val player = npc.level().players().filter { it.isAlive }.minByOrNull { it.distanceTo(npc) } ?: return@onUpdate
    val distance = npc.distanceTo(player)
    if (distance <= approachDistance && !greeted) {
        npc.startLookingAt(player.position())
        npc.startAnimation(greeting)
        greeted = true
    } else if (distance >= approachDistance * 2) {
        greeted = false
    }
}
```
- Logic for the whole world: a server node started with `he scripting run <path>`, or event handlers in a `*.reload.kts`, which `reload` restarts.
- Items, blocks and anything else Minecraft only accepts at startup: `*.startup.kts`, applied after a game restart.
- A one-off action, like placing an NPC for a scene: a plain `*.kts` run with `he scripting run <path>`, or a command.

## Work in this loop

1. Read the guide page for the area (`docs_search`, `docs_read`) and look the API up with `symbol_search` and `symbol_source` instead of guessing: the engine's API is mostly extension functions, and its parameters with defaults show `= …`.
2. Write the file on disk. Script paths are spelled `scripts/...` by commands and tools; addons' scripts are `namespace:path`.
3. Run `script_diagnostics` until it reports no errors.
4. Start it through `run_command` (`he scripting run`, `he scripting attach`, `reload`). Errors a script throws while running are written to the game log, {log}; read it from disk.
5. Check the result through game state: `he scripting list` and `he scripting list entity <selector>` for running nodes, `run_snippet` for positions, components and values.

## Assets are data: check their names

Animation clips, materials and bones are named by each model; there is no clip every model has, and the guides' examples use made-up names such as "wave". Before a script plays a clip, list the real ones: `he model entity <selector>` for the model an entity shows, `he model info <model>` for a model file. A clip the model lacks plays as nothing, and the game log says so. `he model animation play <selector> <clip>` tries one out on an entity.

`screenshot` cannot tell whether an animation plays, as a still frame shows no motion, and every picture costs a lot of context. Use it for what only a picture shows: placement, layout, lighting, materials, UI, and take as few as possible.

Entities are picked with selectors, e.g. `@e[type=hollowengine:npc_entity,sort=nearest,limit=1]`.

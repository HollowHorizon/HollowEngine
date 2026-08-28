# HollowEngine addons

This directory contains dynamically loaded HollowEngine addons and the addon build convention.
Complete guides:

- [English addon development guide](../docs/en/addons.mdx)
- [English guide to integrating another mod](../docs/en/addons-mod-integration.mdx)
- [Руководство по разработке аддонов](../docs/ru/addons.mdx)
- [Интеграция HollowEngine с другим модом через аддон](../docs/ru/addons-mod-integration.mdx)

Every nested directory containing a `build.gradle.kts` is discovered by `settings.gradle.kts` and
added to the Gradle graph. Check the actual graph before building:

```shell
.\gradlew.bat projects
```

## Included examples

- `debug-command` demonstrates a common addon, a hot-reloadable command, and a packaged script.
- `ide-example` is a client-only API/ABI example. It adds:
  - **Tools → Open IDE Addon Example**;
  - an **IDE Addon Example** dock panel showing the currently focused file;
  - a read-only preview editor for `*.quest` files.
- `compiler` is a specialized addon with its own build because it packages the Kotlin compiler and
  IntelliJ analysis APIs.

The IDE example intentionally contains no gameplay system. It is a small, buildable reference and a
smoke test for addon-owned Compose UI and file type registrations.

## Minimal project

```text
addons/my-addon/
├── build.gradle.kts
└── src/main/
    ├── java/com/example/myaddon/MyAddon.kt
    └── resources/META-INF/plugin.properties
```

`build.gradle.kts`:

```kotlin
base {
    archivesName.set("MyAddon")
}
```

The standard convention applies Kotlin/JVM, serialization, the Compose compiler, Architectury Loom,
Minecraft mappings, and the addon packaging tasks. Compose Runtime is compile-only because
HollowEngine supplies it in the game.

`plugin.properties`:

```properties
id=my-addon
name=My Addon
version=${version}
entry=com.example.myaddon.MyAddon
environment=common
dependsOn=another-addon
```

Use `environment=client` if the entrypoint references Hollow IDE, HUD, Minecraft client, or Compose
types. `dependsOn` is a comma-separated list and controls both load order and classloader
visibility.

## Entrypoint and lifecycle

```kotlin
class MyAddon : HollowAddonEntrypoint {
    override suspend fun load(context: HollowAddonContext, scope: CoroutineScope) {
        // Register features and start coroutines in scope.
    }

    override suspend fun unload(context: HollowAddonContext) {
        // Only finalize state not owned by the lifecycle APIs.
    }
}
```

Each addon receives an isolated classloader, a dedicated coroutine `Job`, a Koin container, owned
host services, and owned extension/Minecraft APIs. A failed load rolls back partial registrations.
Disable, reload, and unload cancel the scope and remove owned state before the classloader closes.

Use `context.extensions.onUnload` for a third-party resource the runtime cannot track:

```kotlin
val executor = createExecutor()
context.extensions.onUnload(executor::close)
```

Cleanup runs in reverse registration order. A returned `HollowAddonRegistration` can also be closed
manually and is idempotent.

## API map

| API | Use |
| --- | --- |
| `context.extensions` | Typed contributions, qualified IDs, arbitrary cleanup |
| `context.minecraft.subscribe` | Explicit lifecycle-owned event listeners |
| `registerCommands` / `registerClientCommands` | Hot-reloadable Brigadier nodes |
| `registerPacket` | Runtime addon packet types with direction validation |
| `context.minecraft.dispatchers` | Guarded server/client thread execution |
| `context.hostServices` | Publish and find typed cross-addon services |
| `context.koin` / `koinModules` | Addon-local dependency injection |

Local extension IDs are qualified with the descriptor ID. For example:

```kotlin
val panelId = context.extensions.qualify("overview")
// my-addon:overview
```

The owner namespace cannot be forged, duplicate IDs in one extension point are rejected, and
contributions are ordered by descending priority followed by registration order.

### Minecraft example

```kotlin
context.minecraft.subscribe<TickEvent.Server> { event ->
    // Synchronous callback; do not block the server thread.
}

context.minecraft.registerCommands { dispatcher ->
    dispatcher.register(Commands.literal("my-addon").executes { 1 })
}

context.minecraft.dispatchers.executeServer {
    // Runs only if an active server and addon still exist.
}
```

Command registration replays the current dispatcher to hot-loaded addons. Its command nodes are
removed automatically on unload/reload.

Items, blocks, entities, and other frozen Minecraft registries are deliberately not runtime
extension points. They need an early startup phase and cannot support safe hot reload.

### Hollow IDE example

```kotlin
val panelId = context.extensions.qualify("overview")

context.extensions.registerIdePanel(
    HollowIdePanel(
        id = "overview",
        title = "My Addon",
        content = { ide -> Text("Focused: ${ide.focusedFile?.path ?: "none"}") },
    ),
)
context.extensions.registerIdeMenuItem(
    HollowIdeMenuItem(
        id = "open-overview",
        menu = HollowIdeMenu.TOOLS,
        label = "Open My Addon",
        run = { ide -> ide.openPanel(panelId) },
    ),
)
```

Panel titles and menu labels may be literal text or Minecraft translation keys. Translation is
resolved when the UI is rendered, not while the addon registry is initialized.

The IDE exposes file types/editors, panels, menu entries, file/project actions, languages, and
code-insight contributors. Built-in and addon contributions use the same registries; no central
`when` must be changed.

## Dependencies

```kotlin
dependencies {
    add("addonLibraries", "com.example:library:1.0.0")
    add("addonRuntimeLibraries", "com.example:runtime-only:1.0.0")
    add("addonBootstrapLibraries", "com.example:native-wrapper:1.0.0")
}
```

- `addonLibraries`: compile + runtime, stored in `hollowengine-addon-libs`.
- `addonRuntimeLibraries`: runtime only, stored in `hollowengine-addon-libs`.
- `addonBootstrapLibraries`: stable host classloader, stored in
  `hollowengine-addon-bootstrap`; installing or changing one requires restart.

Do not bundle Kotlin, coroutines, Koin, Log4j, LWJGL, Netty, JNA, OSHI, or other Minecraft-owned
native stacks. The build excludes host-provided libraries and rejects forbidden native artifacts.

## Build and install

```shell
# One standard addon
.\gradlew.bat :addons:my-addon:addonJar

# Every discovered addon, collected under build/addon-jars
.\gradlew.bat buildAddons
```

The standard `addonJar` is one universal artifact containing named and intermediary variants.
Copy the JAR without a classifier to the game's `hollowengine/addons/` directory; the runtime
selects the NeoForge or Fabric variant itself.

Runtime controls:

```text
/he addons list
/he addons enable <addon-id>
/he addons disable <addon-id>
/he addons reload <addon-id>
```

Disabled IDs are persisted in `hollowengine/addons/.disabled-addons`. An addon containing bootstrap
libraries reports `RESTART_REQUIRED`; an ordinary addon can be hot-loaded and replaced.

## Packaged scripts

Put addon scripts under `src/main/resources/scripts`. The build compiles and remaps them for both
variants, and the runtime exposes them under the addon ID namespace:

```text
/he scripting run my-addon:nodes/quest.node.kts
```

An import from another namespace requires that addon in `dependsOn`:

```kotlin
@file:Import("other-addon:shared.kts")
```

Build without script sources when required:

```shell
.\gradlew.bat buildAddons -Phollowengine.scripts.includeSources=false
```

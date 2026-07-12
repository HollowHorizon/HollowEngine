# HollowEngine addons

Every directory below `addons/` that contains a `build.gradle.kts` is discovered automatically as a Gradle subproject. Addon builds receive Minecraft, mappings, HollowEngine runtime, Kotlin, coroutines, and Koin on their compile classpath.

Build every addon with:

```shell
./gradlew buildAddons
```

The resulting platform artifacts are collected in `build/addon-jars/`:

- `*-fabric.jar` is remapped to Fabric's intermediary namespace.
- `*-neoforge.jar` uses the official namespace used by NeoForge.

Copy the artifact for the active loader to the game's `hollowengine/addons/` directory. The runtime watches this directory and reloads changed jars.

Runtime diagnostics and lifecycle controls are available to operators:

```text
/he addons list
/he addons enable <addon-id>
/he addons disable <addon-id>
/he addons reload <addon-id>
```

Disabled ids are persisted in `hollowengine/addons/.disabled-addons`. The `debug-command` example has no bootstrap libraries and can therefore be copied and loaded while Minecraft is running. It directly handles `RegisterCommandsEvent` and adds `/he addon-text <text>`.

Command addons use Brigadier directly from `@SubscribeEvent`. `RegisterCommandsEvent` replays the active dispatcher for a hot-loaded addon, and command nodes added by that scoped listener are removed automatically when the addon is disabled or reloaded. The video addon demonstrates the same mechanism with `/he video <local-path-or-url>`.

An addon's `build.gradle.kts` only needs its own settings and libraries. Dependencies added to `addonLibraries` are available during compilation, embedded as nested jars, and loaded in the addon's isolated classloader. Pure Java runtime-only libraries belong in `addonRuntimeLibraries`.

Libraries that load native code or keep process-global state belong in `addonBootstrapLibraries`. They are loaded into HollowEngine's stable runtime classloader before addon initialization. A newly copied or updated addon that contains bootstrap libraries is deliberately not hot-loaded: `HollowAddonManager.restartRequired` reports it, the log asks for a restart, and it becomes available on the next game launch.

```kotlin
base.archivesName.set("MyAddon")

dependencies {
    add("addonLibraries", "com.example:library:1.0.0")
    add("addonRuntimeLibraries", "com.example:pure-java-runtime-library:1.0.0")
    add("addonBootstrapLibraries", "com.example:native-library:1.0.0:windows-x86_64")
}
```

Do not bundle Minecraft-owned native stacks such as LWJGL, jemalloc, GLFW, OpenAL, OpenGL, STB, Vulkan, JNA, JInput, Netty, or OSHI. Addon builds reject them and the bootstrap validates external addon jars before loading. The game-provided versions must be used.

Declare the addon in `src/main/resources/META-INF/plugin.properties`:

```properties
id=my-addon
name=My Addon
version=${version}
entry=com.example.myaddon.MyAddon
dependsOn=another-addon
environment=common
```

Entrypoints receive a lifecycle `CoroutineScope`. Public `@SubscribeEvent` methods declared on the entrypoint, a Kotlin `object`, or as static/top-level functions are discovered automatically. HollowEngine registers them in that scope; cancelling the scope during unload removes all of them.

```kotlin
class MyAddon : HollowAddonEntrypoint {
    override suspend fun load(context: HollowAddonContext, scope: CoroutineScope) {
        // Start addon coroutines and publish services here.
    }

    @SubscribeEvent
    fun onServerTick(event: TickEvent.Server) {
        // Handle the event synchronously.
    }
}
```

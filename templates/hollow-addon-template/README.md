# Аддон HollowEngine внутри мода

Скопируйте папку `hollow-addon-template` в корень репозитория своего мода. В `build.gradle.kts` **модуля мода** после блока `plugins` добавьте:

```kotlin
apply(from = rootProject.file("hollow-addon-template/addon.gradle.kts"))
```

Скрипт добавляет единственный Kotlin-файл шаблона и его ресурсы в `main`, подключает лежащий в `libs/` dev JAR HE через `compileOnly` и проверяет производственный JAR. Имя мода берётся из `modId` или `mod_id` в Gradle, либо из `fabric.mod.json` / `neoforge.mods.toml`. Запускайте обычную задачу сборки мода (`assemble` или `build`): устанавливать нужно только полученный JAR мода. HE найдёт внутри него `META-INF/hollowengine/mod-addon.properties` и запустит entrypoint, если мод установлен.

Переименуйте пакет и `ExampleAddon`, затем обновите `entry` в `src/main/resources/META-INF/hollowengine/mod-addon.properties`. `id` — идентификатор аддона в HE, `hostModId` подставляется Gradle автоматически. Версия аддона совпадает с версией мода. В JAR мода должен сохраниться его обычный `fabric.mod.json` или `neoforge.mods.toml`.

Dev JAR в `libs/` создан задачей HE `developmentJar` для Minecraft 1.21.1 и Kotlin 2.4.0. При смене версии HE замените этот файл. Для Fabric нужны official Mojang mappings либо Parchment поверх них; NeoForge использует official mappings. HE должен быть установлен в игре как зависимость мода.

Такой аддон живёт вместе с модом и обновляется после перезапуска игры. Для независимого аддона, который устанавливают и обновляют отдельно, используется обычный формат HE 3 и его отдельный JAR.

<p align="center">
  <img src="https://raw.githubusercontent.com/0mods/HollowEngineDocumentation/refs/heads/master/static/img/headers/welcome2.webp"
       style="background-color: #110E11; padding: 0 20%; width: 100%; box-sizing: border-box;" alt="Logo">
  <h1 align="center">Hollow Engine</h1>
</p>

<p align="center">
  <a href="https://github.com/HollowHorizon/HollowEngine/commits/"><img src="https://img.shields.io/github/commit-activity/t/HollowHorizon/HollowEngine?style=for-the-badge&labelColor=a39774&color=ccac4b" alt="Commits"></a>
  <a href="https://github.com/HollowHorizon/HollowEngine/stargazers"><img src="https://img.shields.io/github/stars/HollowHorizon/HollowEngine.svg?style=for-the-badge&labelColor=a39774&color=ccac4b" alt="Stars"></a>
  <a href="https://github.com/HollowHorizon/HollowEngine/graphs/contributors"><img src="https://img.shields.io/github/contributors/HollowHorizon/HollowEngine.svg?style=for-the-badge&labelColor=a39774&color=ccac4b" alt="Contributors"></a>
  <a href="https://github.com/HollowHorizon/HollowEngine/releases"><img src="https://img.shields.io/github/downloads/HollowHorizon/HollowEngine/total?style=for-the-badge&labelColor=a39774&color=ccac4b" alt="Downloads"></a>
  <a href="https://discord.gg/qKpPhkwGCY"><img src="https://img.shields.io/discord/1081609215484887051?style=for-the-badge&label=Discord&logo=discord&logoColor=d9e0ee&labelColor=a39774&color=ccac4b" alt="Discord"></a>
  <img src="https://img.shields.io/codefactor/grade/github/HollowHorizon/HollowEngine?style=for-the-badge&labelColor=a39774&color=ccac4b" alt="CodeFactor">
</p>

---

## Introduction

HollowEngine is a content creation engine for Minecraft. It combines Kotlin scripting, a visual programming, and tools for building NPCs, events, gameplay systems, and interactive experiences.

## What It Provides

- In-game IDE with syntax highlighting and code analysis
- Kotlin script autocompletion and execution
- Block-based visual programming
- Runtime support for custom events, commands, entities, and levels
- NPC tooling with models, textures, animations, and interactions
- Scene and camera tooling for cinematic workflows
- Embedded asset, resource pack, and datapack loading

## Current Platform Support

- Minecraft `1.21.1`
- Fabric
- NeoForge
- Kotlin `2.3.0`

## Project Layout

- `runtime/`: main engine, UI, gameplay integration, tests
- `bridge/`: shared compatibility layer
- `bootstrap/`: shared bootstrap code and resources
- `bootstrap-fabric/`: Fabric bootstrap implementation
- `bootstrap-neoforge/`: NeoForge bootstrap implementation
- `compiler/`: scripting/compiler module

## Quick Start

1. Install the platform-specific HollowEngine jar for Fabric or NeoForge into your `mods/` folder.
2. If you use the compiler workflow, place `HollowEngineCompiler.jar` in the `hollowengine/` directory.
3. Start Minecraft.
4. Open the in-game editor and begin creating content.

## Build From Source

Use the Gradle wrapper from the repository root:

- `.\gradlew.bat buildAndCollect` - build enabled bootstrap platforms and collect outputs into `merged/`
- `.\gradlew.bat test` - run JVM tests
- `.\gradlew.bat :runtime:build` - build the runtime module
- `.\gradlew.bat :bridge:build` - build the bridge module
- `.\gradlew.bat :compiler:build` - build the compiler module
- `.\gradlew.bat :bootstrap:fabric:build` - build the Fabric bootstrap
- `.\gradlew.bat :bootstrap:neoforge:build` - build the NeoForge bootstrap
- `.\gradlew.bat projects` - inspect the current project graph

## Links

- [Documentation](https://0mods.team/) - Guides and API reference
- [Discord Community](https://discord.gg/qKpPhkwGCY) - Help and updates
- [Telegram Channel](https://t.me/hollowengine) - Devlogs and announcements
- [AI Wiki Assistant](https://deepwiki.com/HollowHorizon/HollowEngine) - Interactive help

## License

This project is licensed under the Creative Commons Attribution-NonCommercial-ShareAlike 4.0 International License.

## Other

<div align = center>
 <a href="https://www.star-history.com/#HollowHorizon/HollowEngine&Date">
  <picture>
    <source media="(prefers-color-scheme: dark)" srcset="https://api.star-history.com/svg?repos=HollowHorizon/HollowEngine&type=Date&theme=dark" />
    <source media="(prefers-color-scheme: light)" srcset="https://api.star-history.com/svg?repos=HollowHorizon/HollowEngine&type=Date" />
    <img alt="Star History Chart" src="https://api.star-history.com/svg?repos=HollowHorizon/HollowEngine&type=Date" />
  </picture>
 </a>
</div>
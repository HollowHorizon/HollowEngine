val minecraft_version: String by project
val mod_id: String by project
val enabledPlatforms: String by project

architectury {
    common(enabledPlatforms.split(","))
}

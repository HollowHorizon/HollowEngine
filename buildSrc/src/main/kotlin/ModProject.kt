class ModProject(
    val modId: String,
    val modName: String,
    val modVersion: String,
    val license: String,

    val description: String = "",
    val authors: List<String> = emptyList(),

    val entryPoints: Map<String, List<String>>,
    val dependencies: Map<String, String>,

    val username: String
)
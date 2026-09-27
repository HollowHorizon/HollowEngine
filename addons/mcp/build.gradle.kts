base {
    archivesName.set("HollowEngineMcp")
}

val mcpSdkVersion = "0.15.0"
val ktorVersion = "3.5.1"

dependencies {
    add("addonLibraries", "io.modelcontextprotocol:kotlin-sdk-server:$mcpSdkVersion")
    add("addonLibraries", "io.ktor:ktor-server-cio:$ktorVersion")
}

val docsDirectory = rootProject.layout.projectDirectory.dir("docs/en")
val docsPageList = tasks.register("docsPageList") {
    val pages = docsDirectory
    val output = layout.buildDirectory.file("generated/docs/pages.txt")
    inputs.dir(pages)
    outputs.file(output)
    doLast {
        val root = pages.asFile
        val paths = root.walkTopDown()
            .filter { it.isFile && it.extension == "mdx" }
            .map { it.relativeTo(root).invariantSeparatorsPath }
            .sorted()
        output.get().asFile.writeText(paths.joinToString("\n"))
    }
}

tasks.named<ProcessResources>("processResources") {
    from(docsDirectory) {
        into("hollowengine-mcp/docs")
        include("**/*.mdx")
    }
    from(docsPageList) {
        into("hollowengine-mcp/docs")
    }
}

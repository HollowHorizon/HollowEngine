val acousticVersion: String by rootProject
val serializationVersion: String by rootProject
val acousticApi = rootProject.files("libs/acoustic-0.2.0.jar")

base {
    archivesName.set("HollowEngineAcoustic")
}

dependencies {
    add("modCompileOnly", acousticApi)
    add("testImplementation", acousticApi)
    add("testImplementation", "org.jetbrains.kotlinx:kotlinx-serialization-core:$serializationVersion")
}

import org.jetbrains.kotlin.gradle.tasks.KotlinCompile
import java.time.ZonedDateTime
import java.time.format.DateTimeFormatter

plugins {
    id("com.modrinth.minotaur") version "2.+"
    id("net.minecraftforge.gradle") version "[6.0,6.2)"
    id("org.parchmentmc.librarian.forgegradle") version "1.+"
    id("org.spongepowered.mixin") version "0.7.38"
    kotlin("jvm") version "1.9.23"
    kotlin("plugin.serialization") version "1.9.23"
}

val mcVersion: String by project
val modVersion: String by project
val mappingsVersion: String by project
val hcVersion: String by project
val forgeVersion: String by project
val kffVersion: String by project
val ksffVersion: String by project

group = "ru.hollowhorizon"
version = "${mcVersion}-$modVersion"
project.setProperty("archivesBaseName", "hollowengine")

java {
    toolchain.languageVersion.set(JavaLanguageVersion.of(17))
}

tasks.withType<KotlinCompile> {
    kotlinOptions.jvmTarget = "17"
    kotlinOptions.freeCompilerArgs = listOf("-Xjvm-default=all", "-Xopt-in=kotlin.RequiresOptIn")
}

tasks.withType<JavaCompile> {
    options.encoding = "UTF-8"
}

minecraft {
    mappings("parchment", "${mappingsVersion}-$mcVersion")

    accessTransformer("src/main/resources/META-INF/accesstransformer.cfg")

    runs.create("client") {
        workingDirectory(project.file("run"))
        property("forge.logging.markers", "REGISTRIES") // eg: SCAN,REGISTRIES,REGISTRYDUMP
        property("forge.logging.console.level", "debug")
        //jvmArg("-XX:+AllowEnhancedClassRedefinition")
        arg("-mixin.config=hollowengine.mixins.json")
        mods.create("hollowengine") {
            source(the<JavaPluginExtension>().sourceSets.getByName("main"))
        }
    }

    runs.create("server") {
        workingDirectory(project.file("run"))
        property("forge.logging.markers", "REGISTRIES") // eg: SCAN,REGISTRIES,REGISTRYDUMP
        property("forge.logging.console.level", "debug")
        arg("-mixin.config=hollowengine.mixins.json")
        //jvmArg("-XX:+AllowEnhancedClassRedefinition")
        mods.create("hollowengine") {
            source(the<JavaPluginExtension>().sourceSets.getByName("main"))
        }
    }

}

repositories {
    mavenCentral()
    maven { url = uri("https://jitpack.io") }
    maven { url = uri("https://thedarkcolour.github.io/KotlinForForge/") }
    maven { url = uri("https://cursemaven.com") }
    maven { url = uri("https://maven.firstdarkdev.xyz/snapshots") }
    flatDir {
        dir("hc")
        dir("libs")
    }
}

mixin {
    add(sourceSets.main.get(), "hollowengine.refmap.json")
    config("hollowengine.mixins.json")
}

dependencies {
    minecraft("net.minecraftforge:forge:${mcVersion}-${forgeVersion}")
    annotationProcessor("org.spongepowered:mixin:0.8.5:processor")

    implementation(kotlin("script-runtime"))
    implementation(kotlin("scripting-jvm-host"))
    implementation(kotlin("scripting-jvm"))
    implementation("thedarkcolour:kotlinforforge:$kffVersion")
    implementation("com.github.SrRapero720.watermedia:build:2.0.28")
    implementation(fg.deobf("ru.hollowhorizon:kotlinscript:${ksffVersion}"))
    implementation(fg.deobf("ru.hollowhorizon:hc:${mcVersion}-${hcVersion}"))
    compileOnly(fg.deobf("curse.maven:ftb-teams-forge-404468:4611938"))
    implementation(fg.deobf("curse.maven:ftb-library-forge-404465:4661834"))
    implementation(fg.deobf("curse.maven:architectury-api-419699:4555749"))
    implementation(fg.deobf("curse.maven:jei-238222:4712866"))
    implementation(fg.deobf("curse.maven:wthit-forge-455982:4819215"))
    implementation(fg.deobf("curse.maven:badpackets-615134:4784364"))
    compileOnly(fg.deobf("curse.maven:embeddium-908741:4984830"))
    compileOnly(fg.deobf("curse.maven:oculus-581495:4763262"))
    implementation(fg.deobf("curse.maven:spark-361579:4505309"))
    implementation(fg.deobf("curse.maven:item-filters-309674:4553326"))
    compileOnly(fg.deobf("curse.maven:ftb-quests-forge-289412:5060506"))
}

fun Jar.createManifest() = manifest {
    attributes(
        "Automatic-Module-Name" to "hollowengine",
        "Specification-Title" to "HollowEngine",
        "Specification-Vendor" to "HollowHorizon",
        "Specification-Version" to "1", // We are version 1 of ourselves
        "Implementation-Title" to project.name,
        "Implementation-Version" to version,
        "Implementation-Vendor" to "HollowHorizon",
        "Implementation-Timestamp" to ZonedDateTime.now()
            .format(DateTimeFormatter.ofPattern("yyyy-MM-dd'T'HH:mm:ssZ")),
        "MixinConfigs" to "hollowengine.mixins.json"
    )
}

val jar = tasks.named<Jar>("jar") {
    archiveClassifier.set("")

    duplicatesStrategy = DuplicatesStrategy.EXCLUDE

    exclude(
        "LICENSE.txt", "META-INF/MANIFSET.MF", "META-INF/maven/**",
        "META-INF/*.RSA", "META-INF/*.SF", "META-INF/versions/**", "**/module-info.class"
    )

    createManifest()

    finalizedBy("reobfJar")
}
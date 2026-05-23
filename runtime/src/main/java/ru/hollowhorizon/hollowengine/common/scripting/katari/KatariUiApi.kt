package ru.hollowhorizon.hollowengine.common.scripting.katari

import com.sunnychung.lib.multiplatform.kotlite.katari.ValueRestoreContext
import com.sunnychung.lib.multiplatform.kotlite.katari.ValueSnapshot
import com.sunnychung.lib.multiplatform.kotlite.katari.NarrativeBindingsBuilder
import com.sunnychung.lib.multiplatform.kotlite.model.CustomFunctionParameter
import com.sunnychung.lib.multiplatform.kotlite.model.NarrativeHostValue
import com.sunnychung.lib.multiplatform.kotlite.model.NullValue
import com.sunnychung.lib.multiplatform.kotlite.model.BooleanValue
import com.sunnychung.lib.multiplatform.kotlite.model.DoubleValue
import com.sunnychung.lib.multiplatform.kotlite.model.FloatValue
import com.sunnychung.lib.multiplatform.kotlite.model.IntValue
import com.sunnychung.lib.multiplatform.kotlite.model.LongValue
import com.sunnychung.lib.multiplatform.kotlite.model.RuntimeValue
import com.sunnychung.lib.multiplatform.kotlite.model.ShortValue
import com.sunnychung.lib.multiplatform.kotlite.model.StringValue
import com.sunnychung.lib.multiplatform.kotlite.model.StructArrayValue
import com.sunnychung.lib.multiplatform.kotlite.model.StructValue
import com.sunnychung.lib.multiplatform.kotlite.model.STRUCT_VALUE_TYPE_ID
import com.sunnychung.lib.multiplatform.kotlite.model.XmlValue
import kotlinx.serialization.Serializable
import net.minecraft.nbt.ByteTag
import net.minecraft.resources.ResourceLocation
import net.minecraft.nbt.CompoundTag
import net.minecraft.nbt.DoubleTag
import net.minecraft.nbt.FloatTag
import net.minecraft.nbt.IntTag
import net.minecraft.nbt.ListTag
import net.minecraft.nbt.LongTag
import net.minecraft.nbt.ShortTag
import net.minecraft.nbt.StringTag
import net.minecraft.nbt.Tag
import net.minecraft.server.level.ServerPlayer
import net.minecraft.world.entity.player.Player
import ru.hollowhorizon.hollowengine.client.ui.scripting.CloseKatariUiScreenPacket
import ru.hollowhorizon.hollowengine.client.ui.scripting.HideKatariUiOverlayPacket
import ru.hollowhorizon.hollowengine.client.ui.scripting.KatariUiDisplayMode
import ru.hollowhorizon.hollowengine.client.ui.scripting.ShowKatariUiPacket
import ru.hollowhorizon.hollowengine.client.ui.xml.UiXmlTree
import ru.hollowhorizon.hollowengine.client.ui.xml.from
import ru.hollowhorizon.hollowengine.client.ui.xml.parseUiMarkup
import ru.hollowhorizon.hollowengine.common.files.DirectoryManager
import ru.hollowhorizon.hollowengine.common.events.factory.await
import ru.hollowhorizon.hollowengine.common.scripting.katari.binding.ScriptBinding
import ru.hollowhorizon.hollowengine.common.scripting.katari.binding.ScriptSnapshot
import ru.hollowhorizon.hollowengine.common.scripting.katari.binding.ScriptSnapshotFactory
import ru.hollowhorizon.hollowengine.common.scripting.katari.binding.ScriptType
import ru.hollowhorizon.hollowengine.common.scripting.katari.binding.KatariGeneratedBindingRuntime
import java.io.FileNotFoundException
import java.io.InputStreamReader
import java.nio.file.Files
import java.util.UUID

class KatariUiDocument(
    val id: String = UUID.randomUUID().toString(),
    root: UiXmlTree,
) {
    var root: UiXmlTree = root
        private set

    fun insertAt(target: String, child: UiXmlTree) {
        val cleanTarget = target.removePrefix(".").removePrefix("#")
        var inserted = false
        root = root.insertIntoFirst(cleanTarget, child) { inserted = true }
        require(inserted) { "UI target `$target` was not found" }
    }
}

@ScriptBinding("ui")
fun katariUi(path: String): KatariUiDocument {
    val source = readUiResourceText(ResourceLocation.parse(path))
    return KatariUiDocument(root = UiXmlTree.from(parseUiMarkup(source)))
}

@ScriptBinding("ui")
fun katariUi(xml: XmlValue): KatariUiDocument {
    return KatariUiDocument(root = UiXmlTree.from(xml))
}

@ScriptBinding
fun KatariUiDocument.insertAt(target: String, child: XmlValue): KatariUiDocument {
    insertAt(target, UiXmlTree.from(child))
    return this
}

@ScriptBinding
fun KatariUiDocument.openScreen(player: Player) {
    send(player, KatariUiDisplayMode.SCREEN, CompoundTag())
}

fun KatariUiDocument.openScreen(player: Player, variables: StructValue) {
    send(player, KatariUiDisplayMode.SCREEN, variables.toCompoundTag())
}

@ScriptBinding
fun KatariUiDocument.showScreen(player: Player) {
    openScreen(player)
}

fun KatariUiDocument.showScreen(player: Player, variables: StructValue) {
    openScreen(player, variables)
}

@ScriptBinding
fun KatariUiDocument.showOverlay(player: Player) {
    send(player, KatariUiDisplayMode.OVERLAY, CompoundTag())
}

fun KatariUiDocument.showOverlay(player: Player, variables: StructValue) {
    send(player, KatariUiDisplayMode.OVERLAY, variables.toCompoundTag())
}

@ScriptBinding
fun KatariUiDocument.closeScreen(player: Player) {
    val serverPlayer = player as? ServerPlayer ?: error("closeScreen requires a server player")
    CloseKatariUiScreenPacket(id).send(serverPlayer)
}

@ScriptBinding
fun KatariUiDocument.hideOverlay(player: Player) {
    val serverPlayer = player as? ServerPlayer ?: error("hideOverlay requires a server player")
    HideKatariUiOverlayPacket(id).send(serverPlayer)
}

@ScriptBinding
suspend fun KatariUiDocument.await(player: Player): CompoundTag {
    val playerId = player.uuid.toString()
    return KatariUiEvent.await { event ->
        event.uiId == id && event.player.uuid.toString() == playerId
    }.payload
}

fun NarrativeBindingsBuilder.registerKatariUiStructBindings() {
    fun screenFunction(name: String, mode: KatariUiDisplayMode) {
        immediateFunction(
            name = name,
            receiverType = "Ui",
            valueParameters = listOf(
                CustomFunctionParameter("player", "Player"),
                CustomFunctionParameter("variables", STRUCT_VALUE_TYPE_ID),
            ),
        ) { arguments, _ ->
            val ui = KatariGeneratedBindingRuntime.asHost<KatariUiDocument>(arguments[0], "Ui", "receiver")
            val player = KatariGeneratedBindingRuntime.asHost<Player>(arguments[1], "Player", "player")
            val variables = arguments[2] as? StructValue ?: error("$name variables expects StructValue")
            ui.send(player, mode, variables.toCompoundTag())
            NullValue
        }
    }
    screenFunction("openScreen", KatariUiDisplayMode.SCREEN)
    screenFunction("showScreen", KatariUiDisplayMode.SCREEN)
    screenFunction("showOverlay", KatariUiDisplayMode.OVERLAY)
}

@Serializable
@ScriptType("Ui")
data class KatariUiDocumentSnapshot(
    val id: String,
    val root: UiXmlTree,
) : ValueSnapshot(), ScriptSnapshot<KatariUiDocument> {
    override suspend fun restore(context: ValueRestoreContext): KatariUiDocument {
        return KatariUiDocument(id, root)
    }

    companion object : ScriptSnapshotFactory<KatariUiDocument, KatariUiDocumentSnapshot> {
        override fun capture(value: KatariUiDocument): KatariUiDocumentSnapshot {
            return KatariUiDocumentSnapshot(value.id, value.root)
        }
    }
}

private fun KatariUiDocument.send(player: Player, mode: KatariUiDisplayMode, variables: CompoundTag) {
    val serverPlayer = player as? ServerPlayer ?: error("$mode requires a server player")
    ShowKatariUiPacket(id, root, mode, variables).send(serverPlayer)
}

private fun UiXmlTree.insertIntoFirst(
    target: String,
    child: UiXmlTree,
    markInserted: () -> Unit,
): UiXmlTree {
    if (matchesTarget(target)) {
        markInserted()
        return copy(children = children + child)
    }
    var inserted = false
    val nextChildren = children.map { current ->
        if (inserted) {
            current
        } else {
            current.insertIntoFirst(target, child) {
                inserted = true
                markInserted()
            }
        }
    }
    return if (inserted) copy(children = nextChildren) else this
}

private fun UiXmlTree.matchesTarget(target: String): Boolean {
    if (name.equals(target, ignoreCase = true)) return true
    if (attributes["id"]?.removePrefix("#") == target) return true
    return tagAttributes().any { it == target }
}

private fun UiXmlTree.tagAttributes(): List<String> {
    return listOfNotNull(attributes["tag"], attributes["tags"], attributes["class"])
        .flatMap { it.split(Regex("\\s+")) }
        .map { it.removePrefix(".") }
        .filter { it.isNotBlank() }
}

private fun readUiResourceText(location: ResourceLocation): String {
    val local = DirectoryManager.HOLLOW_ENGINE.resolve("assets").resolve(location.namespace).resolve(location.path)
    if (Files.isRegularFile(local)) {
        return Files.newBufferedReader(local, Charsets.UTF_8).use { it.readText() }
    }
    val classpathPath = "assets/${location.namespace}/${location.path}"
    val stream = Thread.currentThread().contextClassLoader.getResourceAsStream(classpathPath)
        ?: KatariUiDocument::class.java.classLoader.getResourceAsStream(classpathPath)
        ?: throw FileNotFoundException("Resource $location not found")
    return stream.use { InputStreamReader(it, Charsets.UTF_8).use { reader -> reader.readText() } }
}

private fun StructValue.toCompoundTag(): CompoundTag {
    return CompoundTag().apply {
        fields.forEach { (key, value) -> put(key, value.toNbtTag()) }
    }
}

private fun RuntimeValue.toNbtTag(): Tag {
    return when (this) {
        is BooleanValue -> ByteTag.valueOf(value)
        is IntValue -> IntTag.valueOf(value)
        is LongValue -> LongTag.valueOf(value)
        is ShortValue -> ShortTag.valueOf(value)
        is DoubleValue -> DoubleTag.valueOf(value)
        is FloatValue -> FloatTag.valueOf(value)
        is StringValue -> StringTag.valueOf(value)
        is StructValue -> toCompoundTag()
        is StructArrayValue -> ListTag().also { list -> elements.forEach { list.add(it.toNbtTag()) } }
        else -> StringTag.valueOf(convertToString())
    }
}

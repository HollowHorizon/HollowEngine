package ru.hollowhorizon.hollowengine.common.scripting.katari

import com.sunnychung.lib.multiplatform.kotlite.katari.ValueRestoreContext
import com.sunnychung.lib.multiplatform.kotlite.katari.ValueSnapshot
import com.sunnychung.lib.multiplatform.kotlite.model.CustomFunctionParameter
import com.sunnychung.lib.multiplatform.kotlite.model.FunctionResponse
import com.sunnychung.lib.multiplatform.kotlite.model.NarrativeHostValue
import com.sunnychung.lib.multiplatform.kotlite.model.NarrativeCallContext
import com.sunnychung.lib.multiplatform.kotlite.model.NarrativeCallDispatchContext
import com.sunnychung.lib.multiplatform.kotlite.model.NarrativeCallResult
import com.sunnychung.lib.multiplatform.kotlite.model.NarrativeCallable
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
import com.sunnychung.lib.multiplatform.kotlite.model.SymbolTable
import com.sunnychung.lib.multiplatform.kotlite.model.TypeParameter
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
import ru.hollowhorizon.hollowengine.common.events.EventListener
import ru.hollowhorizon.hollowengine.client.ui.scripting.CloseKatariUiScreenPacket
import ru.hollowhorizon.hollowengine.client.ui.scripting.CloseKatariUiOverlayPacket
import ru.hollowhorizon.hollowengine.client.ui.scripting.KatariUiDisplayMode
import ru.hollowhorizon.hollowengine.client.ui.scripting.ShowKatariUiPacket
import ru.hollowhorizon.hollowengine.client.ui.scripting.UpdateKatariUiOverlayPacket
import ru.hollowhorizon.hollowengine.client.ui.xml.UiXmlTree
import ru.hollowhorizon.hollowengine.client.ui.xml.from
import ru.hollowhorizon.hollowengine.client.ui.xml.parseUiXml
import ru.hollowhorizon.hollowengine.common.files.DirectoryManager
import ru.hollowhorizon.hollowengine.common.events.factory.await
import ru.hollowhorizon.hollowengine.common.scripting.katari.binding.ScriptBinding
import ru.hollowhorizon.hollowengine.common.scripting.katari.binding.ScriptSnapshot
import ru.hollowhorizon.hollowengine.common.scripting.katari.binding.ScriptSnapshotFactory
import ru.hollowhorizon.hollowengine.common.scripting.katari.binding.ScriptType
import ru.hollowhorizon.hollowengine.common.scripting.katari.binding.KatariGeneratedBindingRuntime
import ru.hollowhorizon.hollowengine.common.scripting.katari.binding.GeneratedRuntimeValueResponse
import ru.hollowhorizon.hollowengine.common.scripting.katari.snapshots.PlayerSnapshot
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
        val cleanTarget = normalizeUiTarget(target)
        var inserted = false
        root = root.insertIntoFirst(cleanTarget, child) { inserted = true }
        require(inserted) { "UI target `$target` was not found" }
    }

    fun insertAt(target: String, child: UiXmlTree, attributes: Map<String, String>) {
        insertAt(target, child.withAttributes(attributes))
    }

    fun replaceAt(target: String, child: UiXmlTree) {
        val cleanTarget = normalizeUiTarget(target)
        var replaced = false
        root = root.replaceNodeFirst(cleanTarget, child) { replaced = true }
        require(replaced) { "UI target `$target` was not found" }
    }

    fun replaceAt(target: String, child: UiXmlTree, attributes: Map<String, String>) {
        replaceAt(target, child.withAttributes(attributes))
    }

    fun replaceChildrenAt(target: String, child: UiXmlTree) {
        replaceChildrenAt(target, listOf(child))
    }

    fun replaceChildrenAt(target: String, child: UiXmlTree, attributes: Map<String, String>) {
        replaceChildrenAt(target, child.withAttributes(attributes))
    }

    @Deprecated(
        message = "Use replaceChildrenAt when replacing target contents explicitly.",
        replaceWith = ReplaceWith("replaceChildrenAt(target, children)"),
    )
    fun replaceAt(target: String, children: List<UiXmlTree>) {
        replaceChildrenAt(target, children)
    }

    fun replaceChildrenAt(target: String, children: List<UiXmlTree>) {
        val cleanTarget = normalizeUiTarget(target)
        var replaced = false
        root = root.replaceChildrenFirst(cleanTarget, children) { replaced = true }
        require(replaced) { "UI target `$target` was not found" }
    }

    fun clear(target: String) {
        replaceChildrenAt(target, emptyList())
    }

    fun modify(target: String, attribute: String, value: String) {
        modify(target, mapOf(attribute to value))
    }

    fun modify(target: String, attributes: Map<String, String>) {
        val cleanTarget = normalizeUiTarget(target)
        var modified = false
        root = root.modifyFirst(cleanTarget, attributes) { modified = true }
        require(modified) { "UI target `$target` was not found" }
    }

    fun modifyAll(target: String, attribute: String, value: String): Int {
        return modifyAll(target, mapOf(attribute to value))
    }

    fun modifyAll(target: String, attributes: Map<String, String>): Int {
        val result = root.modifyAll(normalizeUiTarget(target), attributes)
        require(result.count > 0) { "UI target `$target` was not found" }
        root = result.root
        return result.count
    }

    fun removeAttribute(target: String, attribute: String) {
        val cleanTarget = normalizeUiTarget(target)
        var modified = false
        root = root.removeAttributeFirst(cleanTarget, attribute) { modified = true }
        require(modified) { "UI target `$target` was not found" }
    }
}

@ScriptBinding("ui")
fun katariUi(path: String): KatariUiDocument {
    val source = readUiResourceText(ResourceLocation.parse(path))
    return KatariUiDocument(root = parseUiXml(source, path))
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
fun KatariUiDocument.insertAt(target: String, child: XmlValue, attributes: Map<String, String>): KatariUiDocument {
    insertAt(target, UiXmlTree.from(child), attributes)
    return this
}

@ScriptBinding
fun KatariUiDocument.replaceAt(target: String, child: XmlValue): KatariUiDocument {
    replaceAt(target, UiXmlTree.from(child))
    return this
}

@ScriptBinding
fun KatariUiDocument.replaceAt(target: String, child: XmlValue, attributes: Map<String, String>): KatariUiDocument {
    replaceAt(target, UiXmlTree.from(child), attributes)
    return this
}

@ScriptBinding
fun KatariUiDocument.replaceChildrenAt(target: String, child: XmlValue): KatariUiDocument {
    replaceChildrenAt(target, UiXmlTree.from(child))
    return this
}

@ScriptBinding
fun KatariUiDocument.replaceChildrenAt(target: String, child: XmlValue, attributes: Map<String, String>): KatariUiDocument {
    replaceChildrenAt(target, UiXmlTree.from(child), attributes)
    return this
}

@ScriptBinding("setAt")
fun KatariUiDocument.setAt(target: String, child: XmlValue): KatariUiDocument {
    replaceAt(target, child)
    return this
}

@ScriptBinding
fun KatariUiDocument.clear(target: String): KatariUiDocument {
    clear(target)
    return this
}

@ScriptBinding
fun KatariUiDocument.modify(target: String, attribute: String, value: String): KatariUiDocument {
    modify(target, attribute, value)
    return this
}

@ScriptBinding
fun KatariUiDocument.modify(target: String, attributes: Map<String, String>): KatariUiDocument {
    modify(target, attributes)
    return this
}

@ScriptBinding
fun KatariUiDocument.modifyAll(target: String, attribute: String, value: String): Int {
    return modifyAll(target, attribute, value)
}

@ScriptBinding
fun KatariUiDocument.modifyAll(target: String, attributes: Map<String, String>): Int {
    return modifyAll(target, attributes)
}

@ScriptBinding
fun KatariUiDocument.removeAttribute(target: String, attribute: String): KatariUiDocument {
    removeAttribute(target, attribute)
    return this
}

@ScriptBinding
fun KatariUiDocument.openScreen(player: Player) {
    send(player, KatariUiDisplayMode.SCREEN, CompoundTag())
}

@ScriptBinding
fun KatariUiDocument.openScreen(players: List<Player>) {
    send(players, KatariUiDisplayMode.SCREEN, CompoundTag())
}

fun KatariUiDocument.openScreen(player: Player, variables: StructValue) {
    send(player, KatariUiDisplayMode.SCREEN, variables.toCompoundTag())
}

fun KatariUiDocument.openScreen(players: List<Player>, variables: StructValue) {
    send(players, KatariUiDisplayMode.SCREEN, variables.toCompoundTag())
}

@ScriptBinding
fun KatariUiDocument.showScreen(player: Player) {
    openScreen(player)
}

@ScriptBinding
fun KatariUiDocument.showScreen(players: List<Player>) {
    openScreen(players)
}

fun KatariUiDocument.showScreen(player: Player, variables: StructValue) {
    openScreen(player, variables)
}

fun KatariUiDocument.showScreen(players: List<Player>, variables: StructValue) {
    openScreen(players, variables)
}

@ScriptBinding
fun KatariUiDocument.showOverlay(player: Player) {
    showOverlay(player, CompoundTag())
}

@ScriptBinding
fun KatariUiDocument.showOverlay(players: List<Player>) {
    showOverlay(players, CompoundTag())
}

fun KatariUiDocument.showOverlay(player: Player, variables: StructValue) {
    showOverlay(player, variables.toCompoundTag())
}

fun KatariUiDocument.showOverlay(players: List<Player>, variables: StructValue) {
    showOverlay(players, variables.toCompoundTag())
}

@ScriptBinding
fun KatariUiDocument.closeScreen(player: Player) {
    val serverPlayer = player as? ServerPlayer ?: error("closeScreen requires a server player")
    CloseKatariUiScreenPacket(id).send(serverPlayer)
}

@ScriptBinding
fun KatariUiDocument.closeScreen(players: List<Player>) {
    players.forEach(::closeScreen)
}

@ScriptBinding
fun KatariUiDocument.hideOverlay(player: Player) {
    val serverPlayer = player as? ServerPlayer ?: error("hideOverlay requires a server player")
    CloseKatariUiOverlayPacket(id, root).send(serverPlayer)
}

@ScriptBinding
fun KatariUiDocument.hideOverlay(players: List<Player>) {
    players.forEach(::hideOverlay)
}

@ScriptBinding
fun KatariUiDocument.updateOverlay(player: Player) {
    val serverPlayer = player as? ServerPlayer ?: error("updateOverlay requires a server player")
    UpdateKatariUiOverlayPacket(id, root).send(serverPlayer)
}

@ScriptBinding
fun KatariUiDocument.updateOverlay(players: List<Player>) {
    players.forEach(::updateOverlay)
}

suspend fun KatariUiDocument.await(player: Player): CompoundTag {
    val playerId = player.uuid.toString()
    return KatariUiEvent.await { event ->
        event.uiId == id && event.player.uuid.toString() == playerId
    }.payload
}

suspend fun KatariUiDocument.await(): CompoundTag {
    return KatariUiEvent.await { event -> event.uiId == id }.payload
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

internal fun KatariUiDocument.send(player: Player, mode: KatariUiDisplayMode, variables: CompoundTag) {
    val serverPlayer = player as? ServerPlayer ?: error("$mode requires a server player")
    ShowKatariUiPacket(id, root, mode, variables).send(serverPlayer)
}

internal fun KatariUiDocument.send(players: Iterable<Player>, mode: KatariUiDisplayMode, variables: CompoundTag) {
    players.forEach { player -> send(player, mode, variables.copy()) }
}

private fun KatariUiDocument.showOverlay(player: Player, variables: CompoundTag) {
    send(player, KatariUiDisplayMode.OVERLAY, variables)
}

private fun KatariUiDocument.showOverlay(players: Iterable<Player>, variables: CompoundTag) {
    send(players, KatariUiDisplayMode.OVERLAY, variables)
}

internal data object KatariUiAwaitCallable : NarrativeCallable {
    override val id: String = "await"
    override val receiverType: String? = "Ui"
    override val returnType: String = STRUCT_VALUE_TYPE_ID
    override val typeParameters: List<TypeParameter> = emptyList()
    override val valueParameters: List<CustomFunctionParameter> = listOf(
        CustomFunctionParameter("player", "Player?", "null"),
    )

    override suspend fun startCall(arguments: List<RuntimeValue>, context: NarrativeCallContext): NarrativeCallResult {
        return NarrativeCallResult.Suspended
    }

    override suspend fun resumeCall(
        arguments: List<RuntimeValue>,
        response: FunctionResponse?,
        context: NarrativeCallContext,
    ): NarrativeCallResult {
        return when (response) {
            is GeneratedRuntimeValueResponse -> NarrativeCallResult.Returned(response.value)
            else -> NarrativeCallResult.Returned(StructValue(emptyMap(), context.symbolTable))
        }
    }

    override fun dispatch(
        arguments: List<RuntimeValue>,
        context: NarrativeCallDispatchContext,
        resume: (FunctionResponse?) -> Unit,
    ) {
        val ui = KatariGeneratedBindingRuntime.asHost<KatariUiDocument>(arguments[0], "Ui", "receiver")
        val playerId = arguments.getOrNull(1)
            ?.takeUnless { it == NullValue }
            ?.playerUuid("player")
            ?.toString()
        val listener = object : EventListener<KatariUiEvent> {
            override val priority: Int = 0

            override fun invoke(event: KatariUiEvent) {
                if (event.uiId != ui.id) return
                if (playerId != null && event.player.uuid.toString() != playerId) return
                KatariUiEvent.unregister(this)
                resume(GeneratedRuntimeValueResponse(event.payload.toStructValue(context.symbolTable)))
            }
        }
        KatariUiEvent.register(listener)
    }
}

private fun RuntimeValue.playerUuid(name: String): UUID {
    val host = this as? NarrativeHostValue ?: error("$name expects host value `Player`")
    if (host.typeId != "Player" && host.value !is Player) error("$name expects `Player`, got `${host.typeId}`")
    return when (val value = host.value) {
        is Player -> value.uuid
        is PlayerSnapshot -> value.uuid
        else -> error("$name has unexpected host value `$value`")
    }
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

internal fun StructValue.toCompoundTag(): CompoundTag {
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

private fun CompoundTag.toStructValue(symbolTable: SymbolTable): StructValue {
    return StructValue(
        fields = allKeys.associateWith { key -> get(key).toRuntimeValue(symbolTable) },
        symbolTable = symbolTable,
    )
}

private fun Tag?.toRuntimeValue(symbolTable: SymbolTable): RuntimeValue {
    return when (this) {
        null -> NullValue
        is ByteTag -> BooleanValue(asByte.toInt() != 0, symbolTable)
        is ShortTag -> IntValue(asShort.toInt(), symbolTable)
        is IntTag -> IntValue(asInt, symbolTable)
        is LongTag -> LongValue(asLong, symbolTable)
        is FloatTag -> FloatValue(asFloat, symbolTable)
        is DoubleTag -> DoubleValue(asDouble, symbolTable)
        is StringTag -> StringValue(asString, symbolTable)
        is CompoundTag -> toStructValue(symbolTable)
        is ListTag -> StructArrayValue(map { it.toRuntimeValue(symbolTable) }, symbolTable)
        else -> StringValue(asString, symbolTable)
    }
}

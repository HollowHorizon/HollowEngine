package ru.hollowhorizon.hollowengine.client.gui.scripting

import kotlinx.serialization.Serializable
import net.minecraft.client.Minecraft
import net.minecraft.client.gui.components.toasts.SystemToast
import net.minecraft.network.chat.Component
import net.minecraft.server.level.ServerPlayer
import net.minecraft.world.entity.player.Player
import org.apache.commons.io.FileUtils
import ru.hollowhorizon.hc.common.network.HollowPacket
import ru.hollowhorizon.hc.common.network.HollowPacketHandler
import ru.hollowhorizon.hc.common.utils.currentServer
import ru.hollowhorizon.hc.common.utils.literal
import ru.hollowhorizon.hc.common.utils.nbt.ForTextComponent
import ru.hollowhorizon.hollowengine.common.files.DirectoryManager.fromReadablePath
import ru.hollowhorizon.hollowengine.common.scripting.startScript
import ru.hollowhorizon.hollowengine.common.scripting.stopScript
import java.io.File

@HollowPacketHandler(HollowPacketHandler.Direction.TO_CLIENT)
@Serializable
class ToastPacket(val message: @Serializable(ForTextComponent::class) Component) : HollowPacket<ToastPacket> {
    override fun handle(player: Player) = player.sendToast(message)
}

@HollowPacketHandler(HollowPacketHandler.Direction.TO_SERVER)
@Serializable
class CopyFilePacket(val source: String, val dest: String, val deleteSource: Boolean = false) :
    HollowPacket<CreateFilePacket> {
    override fun handle(player: Player) {
        if (!player.hasPermissions(2)) {
            player.sendSystemMessage("У вас нет прав на перемещение файлов!".literal)
            return
        }

        val sourceFile = source.fromReadablePath()
        val destFile = dest.fromReadablePath()
        if (sourceFile == destFile) return
        copyWithUniqueName(sourceFile, destFile)
        if (deleteSource) {
            if (sourceFile.isDirectory) sourceFile.deleteRecursively()
            else sourceFile.delete()
        }
    }

    private fun copyWithUniqueName(source: File, targetDirectory: File) {
        require(source.exists()) { "Source file not exists!" }
        require(targetDirectory.isDirectory) { "Target directory is not folder!" }

        val (nameWithoutExtension, extension) = source.nameWithoutExtension to source.extension
        var target = File(targetDirectory, source.name)
        var counter = 1
        val ext = if (extension.isNotEmpty()) ".$extension" else ""

        // Генерация уникального имени
        while (target.exists()) {
            target = File(
                targetDirectory, if (source.isFile) {
                    "$nameWithoutExtension (${counter++})$ext"
                } else {
                    "${source.name} (${counter++})"
                }
            )
        }

        if (source.isFile) {
            // Копирование файла
            source.copyTo(target)
        } else {
            // Копирование папки рекурсивно
            source.copyRecursively(target)
        }
    }
}

fun Player.sendToast(message: Component) {
    if (this !is ServerPlayer) Minecraft.getInstance().toasts.addToast(
        SystemToast(
            SystemToast.SystemToastIds.PERIODIC_NOTIFICATION,
            "Уведомление".literal,
            message
        )
    )
    else ToastPacket(message).send(this)
}

@HollowPacketHandler(HollowPacketHandler.Direction.TO_SERVER)
@Serializable
class RequestFilePacket(val path: String) : HollowPacket<RequestFilePacket> {
    override fun handle(player: Player) {
        if (!player.hasPermissions(2)) {
            player.sendSystemMessage("У вас нет прав на чтение файлов!".literal)
            return
        }

        val extension = path.substringAfterLast('.')

        val file = path.fromReadablePath().readBytes()

        UpdateFilePacket(
            path,
            file,
            when (extension) {
                "png", "jpg", "jpeg" -> FileType.IMAGE
                "dialog" -> FileType.DIALOG
                else -> FileType.TEXT
            }
        ).send(player as ServerPlayer)

    }
}


@HollowPacketHandler(HollowPacketHandler.Direction.TO_SERVER)
@Serializable
class DeleteFilePacket(val path: String) : HollowPacket<DeleteFilePacket> {
    override fun handle(player: Player) {
        if (!player.hasPermissions(2)) {
            player.sendSystemMessage("У вас нет прав на удаление файлов!".literal)
            return
        }
        val file = path.fromReadablePath()
        if (!file.exists()) return

        if (file.isDirectory) FileUtils.deleteDirectory(file)
        else file.delete()
    }
}

@HollowPacketHandler(HollowPacketHandler.Direction.TO_SERVER)
@Serializable
class SaveFilePacket(val path: String, private val bytes: ByteArray) : HollowPacket<SaveFilePacket> {
    override fun handle(player: Player) {
        if (!player.hasPermissions(2)) {
            player.sendSystemMessage("You don't have permissions to save scripts!".literal)
            return
        }

        val file = path.fromReadablePath()
        if (!file.exists()) return

        file.writeBytes(bytes)
        UpdateFilePacket(path, bytes, FileType.TEXT)
            .send(*currentServer.playerList.players.filter { it != player }.toTypedArray())
    }
}

enum class FileType {
    TEXT, IMAGE, DIALOG
}

@HollowPacketHandler(HollowPacketHandler.Direction.TO_CLIENT)
@Serializable
class UpdateFilePacket(val path: String, private val bytes: ByteArray, val type: FileType) :
    HollowPacket<UpdateFilePacket> {
    override fun handle(player: Player) {
        IdeContent.openFile(path, bytes, type)
    }
}

@HollowPacketHandler(HollowPacketHandler.Direction.TO_SERVER)
@Serializable
class RenameFilePacket(val path: String, private val newName: String) : HollowPacket<RenameFilePacket> {
    override fun handle(player: Player) {
        if (player.hasPermissions(2)) {
            val file = path.fromReadablePath()
            if (file.exists()) {
                file.renameTo(file.parentFile.resolve(newName))
            }
        } else {
            player.sendSystemMessage("You don't have permissions to delete scripts!".literal)
        }
    }
}

@HollowPacketHandler(HollowPacketHandler.Direction.TO_SERVER)
@Serializable
class CreateFilePacket(val path: String) : HollowPacket<CreateFilePacket> {
    override fun handle(player: Player) {
        if (player.hasPermissions(2)) {
            val file = (if (path.startsWith('/')) path.substring(1) else path).fromReadablePath()
            if (!file.exists()) {
                if (!file.parentFile.exists()) file.parentFile.mkdirs()

                if (!file.name.contains(".")) file.mkdirs()
                else file.createNewFile()
            }
        } else {
            player.sendSystemMessage("You don't have permissions to create scripts!".literal)
        }
    }
}

@HollowPacketHandler(HollowPacketHandler.Direction.TO_SERVER)
@Serializable
class StartScriptPacket(val path: String) : HollowPacket<StartScriptPacket> {
    override fun handle(player: Player) {
        if (!player.hasPermissions(2)) {
            player.sendSystemMessage("You don't have permissions to start scripts!".literal)
            return
        } else {
            val file = path.fromReadablePath()

            startScript(file)
            CloseScreenPacket().send(player as ServerPlayer)
        }
    }
}

@HollowPacketHandler(HollowPacketHandler.Direction.TO_CLIENT)
@Serializable
class CloseScreenPacket : HollowPacket<CloseScreenPacket> {
    override fun handle(player: Player) {
        Minecraft.getInstance().screen?.onClose()
    }
}

@HollowPacketHandler(HollowPacketHandler.Direction.TO_SERVER)
@Serializable
class StopScriptPacket(val path: String) : HollowPacket<StopScriptPacket> {
    override fun handle(player: Player) {
        if (!player.hasPermissions(2)) {
            player.sendSystemMessage("You don't have permissions to start scripts!".literal)
            return
        } else {
            val file = path.fromReadablePath()

            stopScript(file)

            player.sendToast("Скрипт успешно остановлен.".literal)
        }
    }
}
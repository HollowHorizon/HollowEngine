package ru.hollowhorizon.hollowengine.common.project.kt


import kotlinx.serialization.Serializable
import net.minecraft.world.entity.player.Player
import java.util.concurrent.Executors
import org.eclipse.lsp4j.launch.LSPLauncher
import ru.hollowhorizon.hc.common.network.HollowPacket
import ru.hollowhorizon.hc.common.network.HollowPacketHandler
import ru.hollowhorizon.hollowengine.HollowEngine
import ru.hollowhorizon.hollowengine.common.project.kt.util.tcpStartServer
import ru.hollowhorizon.hollowengine.common.project.kt.util.tcpConnectToClient
import java.io.ByteArrayOutputStream
import java.io.InputStream
import java.io.OutputStream
import java.util.concurrent.LinkedBlockingQueue

class Args {
    var tcpServerPort: Int? = null
    var tcpClientPort: Int? = null
    var tcpClientHost: String = "localhost"
}

fun main(argv: Array<String>) {
    val args = Args()
    val (inStream, outStream) = args.tcpClientPort?.let {
        // Launch as TCP Client
        tcpConnectToClient(args.tcpClientHost, it)
    } ?: args.tcpServerPort?.let {
        // Launch as TCP Server
        tcpStartServer(it)
    } ?: Pair(System.`in`, System.out)

    val server = KotlinLanguageServer
    val threads = Executors.newSingleThreadExecutor { Thread(it, "client") }
    val launcher = LSPLauncher.createServerLauncher(server, ExitingInputStream(inStream), outStream, threads) { it }

    server.connect(launcher.remoteProxy)
    launcher.startListening()
}

fun startServer(port: Int) {
    val (inStream, outStream) = tcpStartServer(port=port)

    val server = KotlinLanguageServer
    val threads = Executors.newSingleThreadExecutor { Thread(it, "client") }
    val launcher = LSPLauncher.createServerLauncher(server, ExitingInputStream(inStream), outStream, threads) { it }

    server.connect(launcher.remoteProxy)
    launcher.startListening()
}

class ExitingInputStream(private val delegate: InputStream): InputStream() {
    override fun read(): Int = exitIfNegative { delegate.read() }

    override fun read(b: ByteArray): Int = exitIfNegative { delegate.read(b) }

    override fun read(b: ByteArray, off: Int, len: Int): Int = exitIfNegative { delegate.read(b, off, len) }

    private fun exitIfNegative(call: () -> Int): Int {
        val result = call()

        if (result < 0) {
            System.exit(0)
        }

        return result
    }
}

class LSPInputStream : InputStream() {
    private val queue = LinkedBlockingQueue<Int>()
    fun feed(data: ByteArray) {
        for (b in data) queue.put(b.toInt() and 0xFF)
    }
    override fun read(): Int = queue.take()
}

class LSPOutputStream(private val sender: (ByteArray) -> Unit) : OutputStream() {
    private val buffer = ByteArrayOutputStream()
    override fun write(b: Int) = buffer.write(b)
    override fun flush() {
        val bytes = buffer.toByteArray()
        buffer.reset()
        sender(bytes)
    }
}
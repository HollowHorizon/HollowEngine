package ru.hollowhorizon.hollowengine.common.project.kt


import java.util.concurrent.Executors
import org.eclipse.lsp4j.launch.LSPLauncher
import ru.hollowhorizon.hollowengine.common.project.kt.util.tcpStartServer
import ru.hollowhorizon.hollowengine.common.project.kt.util.tcpConnectToClient
import java.io.InputStream

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
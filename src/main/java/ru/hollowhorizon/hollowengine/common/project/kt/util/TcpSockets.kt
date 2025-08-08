package ru.hollowhorizon.hollowengine.common.project.kt.util

import ru.hollowhorizon.hollowengine.HollowEngine
import java.io.InputStream
import java.io.OutputStream
import java.net.ServerSocket
import java.net.Socket

/**
 * Starts a TCP server socket. Blocks until the first
 * client has connected, then returns a pair of IO streams.
 */
fun tcpStartServer(port: Int): Pair<InputStream, OutputStream> = ServerSocket(port)
    .also { HollowEngine.LOGGER.info("Waiting for client on port {}...", port) }
    .accept()
    .let { Pair(it.inputStream, it.outputStream) }

/**
 * Starts a TCP client socket and connects to the client at
 * the specified address, then returns a pair of IO streams.
 */
fun tcpConnectToClient(host: String = "localhost", port: Int): Pair<InputStream, OutputStream> =
    run { HollowEngine.LOGGER.info("Connecting to client at {}:{}...", host, port) }
        .let { Socket(host, port) }
        .let { Pair(it.inputStream, it.outputStream) }

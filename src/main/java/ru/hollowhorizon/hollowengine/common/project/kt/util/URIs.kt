package ru.hollowhorizon.hollowengine.common.project.kt.util

import java.io.File

fun parseFile(uri: String): File = File(uri)

fun describeURIs(uris: Collection<File>): String =
    if (uris.isEmpty()) "0 files"
    else if (uris.size > 5) "${uris.size} files"
    else uris.joinToString(", ", transform = ::describeURI)

fun describeURI(uri: String): String = describeURI(parseFile(uri))

fun describeURI(uri: File): String =
    uri.absolutePath.let {
        val (parent, fileName) = it.partitionAroundLast("/")
        ".../" + parent.substringAfterLast("/") + fileName
    }
package ru.hollowhorizon.hollowengine

import org.apache.logging.log4j.LogManager

val LOGGER = LogManager.getLogger(HollowCore::class.java)

fun logI(text: String) = LOGGER.info(text)
fun logW(text: String) = LOGGER.warn(text)
fun logE(text: String) = LOGGER.error(text)
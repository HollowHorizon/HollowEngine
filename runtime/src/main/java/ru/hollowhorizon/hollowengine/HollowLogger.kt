package ru.hollowhorizon.hollowengine

import org.apache.logging.log4j.LogManager

val LOGGER = LogManager.getLogger(HollowCore::class.java)

fun logI(text: Any) = LOGGER.info(text)
fun logW(text: Any) = LOGGER.warn(text)
fun logE(text: Any) = LOGGER.error(text)
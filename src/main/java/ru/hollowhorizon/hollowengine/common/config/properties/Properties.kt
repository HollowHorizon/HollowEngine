package ru.hollowhorizon.hollowengine.common.config.properties

import net.peanuuutz.tomlkt.TomlComment
import net.peanuuutz.tomlkt.TomlTable
import net.peanuuutz.tomlkt.annotated
import ru.hollowhorizon.hollowengine.HollowEngine

class Properties(val onChange: () -> Unit) {
    private val properties = mutableListOf<ConfigProperty<*>>()

    fun <T> add(value: T, validator: ((T) -> Boolean)? = null): ConfigProperty<T> {
        return ConfigProperty(value, onChange, validator).also { properties.add(it) }
    }

    fun serialize(): TomlTable {

        val table = properties.mapNotNull { property ->
            property.name?.let { name ->
                try {
                    val element = property.serialize()
                    if (element != null) {
                        return@mapNotNull name to element
                    }
                } catch (e: Exception) {
                    HollowEngine.LOGGER.warn("Failed to serialize property: $name", e)
                }
                return@mapNotNull null
            }
        }.let { TomlTable(*it.toTypedArray()) }

        val comments = properties.associate { property ->
            property.name to (property.comment?.let { comment ->
                listOf(TomlComment(comment))
            } ?: emptyList())
        }
        return table.annotated(comments)
    }

    fun deserialize(table: TomlTable) {
        properties.forEach { property ->
            property.name?.let { name ->
                try {
                    val element = table[name]
                    if (element != null) {
                        property.deserialize(element)
                    }
                } catch (e: Exception) {
                    HollowEngine.LOGGER.warn("Failed to deserialize property: $name", e)
                }
            }
        }
    }
}
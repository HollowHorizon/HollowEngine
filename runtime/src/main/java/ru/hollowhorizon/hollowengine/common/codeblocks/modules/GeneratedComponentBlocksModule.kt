package ru.hollowhorizon.hollowengine.common.codeblocks.modules

import ru.hollowhorizon.hollowengine.common.codeblocks.BlockCategoryBuilder
import ru.hollowhorizon.hollowengine.common.codeblocks.BlockModule
import ru.hollowhorizon.hollowengine.common.codeblocks.blocks.components.generated.*
import ru.hollowhorizon.hollowengine.common.geary.components.ComponentDescriptorRegistry
import ru.hollowhorizon.hollowengine.common.geary.components.ComponentSchemaRegistry
import ru.hollowhorizon.hollowengine.common.utils.rl

object GeneratedComponentBlocksModule : BlockModule {
    override fun BlockCategoryBuilder.build() {
        val descriptors = ComponentDescriptorRegistry
            .map { it.value }
            .filter { it.editable }
            .sortedBy { it.id.toString() }

        if (descriptors.isEmpty()) return

        category("Компоненты", icons.FILE_CODEBLOCKS) {
            category("Утилиты", icons.TYPES) {
                block("UUID сущности") { GetEntityUuidBlock() }
                block("Ссылка на сущность") { EntityReferenceFromEntityBlock() }
            }

            descriptors
                .groupBy { it.id.namespace }
                .toSortedMap()
                .forEach { (namespace, namespaceDescriptors) ->
                    category(namespace, icons.FILE_CODEBLOCKS) {
                        namespaceDescriptors.forEach { descriptor ->
                            val schema = ComponentSchemaRegistry.descriptorSchema(descriptor.id) ?: return@forEach
                            category(schema.displayName, schema.icon.rl) {
                                block("Создать ${schema.displayName}") { CreateComponentBlock(schema.key) }
                                block("Установить ${schema.displayName}") { SetComponentBlock(descriptor.id.toString()) }
                                block("Убрать ${schema.displayName}") { RemoveComponentBlock(descriptor.id.toString()) }
                                block("Есть ${schema.displayName}") { HasComponentBlock(descriptor.id.toString()) }

                                schema.fields.forEach { field ->
                                    block("Получить ${field.displayName}") {
                                        GetComponentFieldBlock(descriptor.id.toString(), field.name)
                                    }
                                }
                            }
                        }

                        val nestedSchemaKeys = namespaceDescriptors
                            .mapNotNull { ComponentSchemaRegistry.descriptorSchema(it.id) }
                            .flatMap { schema ->
                                schema.fields.flatMap { field ->
                                    listOfNotNull(field.nestedSchemaKey, field.listElementSchemaKey)
                                }
                            }
                            .distinct()
                            .mapNotNull(ComponentSchemaRegistry::schema)
                            .sortedBy { it.displayName }

                        if (nestedSchemaKeys.isNotEmpty()) {
                            category("Типы", icons.TYPES) {
                                nestedSchemaKeys.forEach { nested ->
                                    block("Создать ${nested.displayName}") { CreateComponentBlock(nested.key) }
                                }
                            }
                        }
                    }
                }
        }
    }
}

package ru.hollowhorizon.hollowengine.client.models.fbx

open class Property

class TypedProperty<T>(var value: T) : Property()

/**
 *  Represents a property table as can be found in the newer FBX files (Properties60, Properties70)
 */
class PropertyTable(val element: Element? = null, val templateProps: PropertyTable? = null) {

    val lazyProps = HashMap<String, Element>()
    val props = HashMap<String, Property>()

    init {
        element?.let {
            val scope = it.scope
            for (e in scope.elements) {
                val key = e.key
                for (value in e.value) {
                    if (key != "P") {
                        domWarning("expected only P elements in property table", value)
                        continue
                    }
                    val name = value.peekPropertyName
                    if (name.isEmpty()) {
                        domWarning("could not read property name", value)
                        continue
                    }

                    if (lazyProps.contains(name)) {
                        domWarning("duplicate property name, will hide previous value: $name", value)
                        continue
                    }
                    lazyProps[name] = value
                }
            }
        }
    }

    /** PropertyGet */
    operator fun <T> invoke(name: String, defaultValue: T) = (get(name) as? TypedProperty<T>)?.value ?: defaultValue

    /** PropertyGet */
    operator fun <T> invoke(name: String, useTemplate: Boolean = false): T? {
        var prop = get(name)
        if (null == prop) {
            if (!useTemplate || null == templateProps) return null
            prop = templateProps[name]
            if (null == prop) return null
        }
        // strong typing, no need to be lenient
        return (prop as? TypedProperty<T>)?.value
    }

    operator fun get(name: String): Property? {

        var it = props[name]
        if (it == null) {
            // hasn't been parsed yet?
            val lit = lazyProps[name]
            if (lit != null) {
                it = lit.readTypedProperty()!!  // = assert
                props[name] = it
            }
            if (it == null)
            // check property template
                return templateProps?.get(name)
        }
        return it
    }

    fun getUnparsedProperties(): MutableMap<String, Property> {

        val result = mutableMapOf<String, Property>()

        // Loop through all the lazy properties (which is all the properties)
        for (entry in lazyProps) {

            // Skip parsed properties
            if (props.contains(entry.key)) continue

            // Read the entry's value.
            val prop = entry.value.readTypedProperty() ?: continue  // Element could not be read. Skip it.

            // Add to result
            result[entry.key] = prop
        }
        return result
    }
}
package ru.hollowhorizon.hc.client.models.fbx

import ru.hollowhorizon.hc.HollowCore

/* DOM/Parse error reporting - does not return */
fun domError(message: String, token: Token): Nothing = throw Exception(Util.addTokenText("FBX-DOM", message, token))

fun domError(message: String, element: Element? = null): Nothing {
    element?.let { domError(message, element.keyToken) }
    throw Exception("FBX-DOM $message")
}

// does return
fun domWarning(message: String, token: Token) = HollowCore.LOGGER.warn(Util.addTokenText("FBX-DOM", message, token))

fun domWarning(message: String, element: Element? = null) {
    element?.let {
        domWarning(message, element.keyToken)
        return
    }
    HollowCore.LOGGER.warn("FBX-DOM: $message")
}

/** fetch a property table and the corresponding property template */
fun getPropertyTable(doc: Document, templateName: String, element: Element, sc: Scope, noWarn: Boolean = false): PropertyTable {

    val properties70 = sc["Properties70"]
    val templateProps = doc.templates[templateName].takeIf { templateName.isNotEmpty() }

    return if (properties70 == null) {
        if (!noWarn) domWarning("property table (Properties70) not found", element)
        templateProps ?: PropertyTable()
    } else PropertyTable(properties70, templateProps)
}

fun <T> processSimpleConnection(con: Connection, isObjectPropertyConn: Boolean, name: String, element: Element,
                                propNameOut: Array<String>? = null): T? {
    if (isObjectPropertyConn && !con.prop.isEmpty()) {
        domWarning("expected incoming $name link to be an object-object connection, ignoring", element)
        return null
    } else if (!isObjectPropertyConn && con.prop.isNotEmpty()) {
        domWarning("expected incoming $name link to be an object-property connection, ignoring", element)
        return null
    }

    if (isObjectPropertyConn && propNameOut != null)
    /*  note: this is ok, the return value of PropertyValue() is guaranteed to remain valid and unchanged as long as
        the document exists.         */
        propNameOut[0] = con.prop

    val ob = con.sourceObject
    if (ob == null) {
        domWarning("failed to read source object for incoming $name link, ignoring", element)
        return null
    }

    return ob as T
}
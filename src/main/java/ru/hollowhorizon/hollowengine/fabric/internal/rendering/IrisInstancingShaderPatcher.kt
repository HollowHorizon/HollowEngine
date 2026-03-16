package ru.hollowhorizon.hollowengine.fabric.internal.rendering

object IrisInstancingShaderPatcher {
    private val versionPattern = Regex("#version\\s+(\\d+)([^\\n]*)")
    private val extensionPattern = Regex("(?m)^\\s*#extension[^\\n]*$")

    fun patch(source: String): String {
        val match = versionPattern.find(source) ?: return source
        val version = match.groupValues[1].toIntOrNull() ?: return source
        val attributeQualifier = if (version >= 130) "in" else "attribute"

        val header = buildString {
            appendLine()
            appendLine("$attributeQualifier vec3 Position;")
            appendLine("$attributeQualifier vec2 UV0;")
            appendLine("$attributeQualifier vec3 Normal;")
            appendLine("$attributeQualifier vec4 _he_InstanceModelView0;")
            appendLine("$attributeQualifier vec4 _he_InstanceModelView1;")
            appendLine("$attributeQualifier vec4 _he_InstanceModelView2;")
            appendLine("$attributeQualifier vec4 _he_InstanceModelView3;")
            appendLine("$attributeQualifier vec3 _he_InstanceNormal0;")
            appendLine("$attributeQualifier vec3 _he_InstanceNormal1;")
            appendLine("$attributeQualifier vec3 _he_InstanceNormal2;")
            appendLine("mat4 _he_ModelViewMat() {")
            appendLine("    return mat4(_he_InstanceModelView0, _he_InstanceModelView1, _he_InstanceModelView2, _he_InstanceModelView3);")
            appendLine("}")
            appendLine("mat4 _he_ModelViewProjectionMat() {")
            appendLine("    return gl_ProjectionMatrix * _he_ModelViewMat();")
            appendLine("}")
            appendLine("mat3 _he_NormalMat() {")
            appendLine("    return mat3(_he_InstanceNormal0, _he_InstanceNormal1, _he_InstanceNormal2);")
            appendLine("}")
            appendLine("vec4 _he_Vertex() {")
            appendLine("    return vec4(Position, 1.0);")
            appendLine("}")
        }

        val insertionIndex =
            extensionPattern.findAll(source).lastOrNull()?.range?.last?.plus(1) ?: (match.range.last + 1)
        var patched = source.substring(0, insertionIndex) + header + source.substring(insertionIndex)

        patched = patched.replace("ftransform()", "(_he_ModelViewProjectionMat() * _he_Vertex())")
        patched = replaceToken(patched, "gl_ModelViewProjectionMatrixInverse", "inverse(_he_ModelViewProjectionMat())")
        patched = replaceToken(patched, "gl_ModelViewProjectionMatrix", "_he_ModelViewProjectionMat()")
        patched = replaceToken(patched, "gl_ModelViewMatrixInverseTranspose", "transpose(inverse(_he_ModelViewMat()))")
        patched = replaceToken(patched, "gl_ModelViewMatrixInverse", "inverse(_he_ModelViewMat())")
        patched = replaceToken(patched, "gl_ModelViewMatrix", "_he_ModelViewMat()")
        patched = replaceToken(patched, "gl_NormalMatrix", "_he_NormalMat()")
        patched = replaceToken(patched, "gl_Vertex", "_he_Vertex()")
        patched = replaceToken(patched, "gl_Normal", "Normal")
        patched = replaceToken(patched, "gl_MultiTexCoord0", "vec4(UV0, 0.0, 1.0)")

        return patched
    }

    private fun replaceToken(source: String, token: String, replacement: String): String {
        return Regex("(?<![A-Za-z0-9_])${Regex.escape(token)}(?![A-Za-z0-9_])").replace(source, replacement)
    }
}

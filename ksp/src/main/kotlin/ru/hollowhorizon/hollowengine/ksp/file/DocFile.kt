package ru.hollowhorizon.hollowengine.ksp.file

import com.google.devtools.ksp.symbol.*
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import ru.hollowhorizon.hollowengine.ksp.DocPage

@Serializable
@SerialName("File")
class DocFile(val title: String, val description: String) {
    val declarations = mutableListOf<DocDeclaration>()

    companion object {
        fun fromFile(file: KSFile, page: DocPage) = DocFile(page.title, page.description).apply {
            file.declarations.forEach {
                declarations.add(DocDeclaration.fromDeclaration(it))
            }
        }
    }
}

@Serializable
@SerialName("Declaration")
sealed class DocDeclaration {
    @SerialName("doc_text")
    var docText = mutableListOf<String>()

    @Serializable
    @SerialName("Class")
    class DocClass(val name: String, val pkg: String) : DocDeclaration() {
        val declarations = mutableListOf<DocDeclaration>()

        companion object {
            fun fromClass(declaration: KSClassDeclaration): DocClass {
                return DocClass(declaration.simpleName.asString(), declaration.packageName.asString()).apply {
                    declaration.declarations.forEach {
                        declarations.add(fromDeclaration(it))
                    }
                }
            }
        }
    }

    @Serializable
    @SerialName("Method")
    class DocMethod(
        val name: String,
        @SerialName("extension_receiver")
        val extReceiver: DocType? = null,
        val valueParameters: List<DocParameter>,
        val returnType: DocType
    ) :
        DocDeclaration() {
        companion object {
            fun fromDeclaration(declaration: KSFunctionDeclaration): DocMethod {
                val returnType =
                    DocType(declaration.returnType?.resolve()?.declaration?.simpleName?.asString() ?: "kotlin.Unit")
                val valueParameters =
                    declaration.parameters.map {
                        DocParameter(
                            it.name?.asString() ?: "",
                            DocType(it.type.resolve().declaration.simpleName.asString())
                        )
                    }
                val extReceiver = declaration.extensionReceiver?.let {
                    DocType(it.resolve().declaration.simpleName.asString())
                }
                return DocMethod(declaration.simpleName.asString(), extReceiver, valueParameters, returnType)
            }
        }
    }

    @Serializable
    @SerialName("Field")
    class DocField(val name: String, val extReceiver: DocType?, val returnType: DocType) : DocDeclaration() {
        companion object {
            fun fromDeclaration(declaration: KSPropertyDeclaration): DocField {
                return DocField(
                    declaration.simpleName.asString(),
                    declaration.extensionReceiver?.let { DocType(it.resolve().declaration.simpleName.asString()) },
                    DocType(declaration.type.resolve().declaration.simpleName.asString())
                )
            }
        }
    }

    companion object {
        fun fromDeclaration(declaration: KSDeclaration): DocDeclaration {
            return when (declaration) {
                is KSClassDeclaration -> DocClass.fromClass(declaration)
                is KSFunctionDeclaration -> DocMethod.fromDeclaration(declaration)
                is KSPropertyDeclaration -> DocField.fromDeclaration(declaration)
                else -> error("unknown declaration")
            }.apply {
                declaration.docString?.trimIndent()?.lines()?.let {
                    docText.addAll(it)
                }
            }
        }
    }
}


@Serializable
class DocParameter(val name: String, val type: DocType)

@Serializable
class DocType(val type: String)
package ru.hollowhorizon.hollowengine.common.scripting.ide

sealed interface CompletionItem {
    val typedPrefix: String
    val show: String
    val insert: String
    val tag: CompletionItemTag
    val moveCaret: Int
    val name: String

    data class Declaration(
        override val show: String,
        override val insert: String,
        override val name: String,
        override val tag: CompletionItemTag,
        override val moveCaret: Int = 0,
        val import: Boolean = false,
        val fqName: String?,
        val tail: String?,
        val middle: String?, override val typedPrefix: String,
    ) : CompletionItem

    data class Keyword(
        override val show: String,
        override val insert: String = show,
        override val name: String,
        override val moveCaret: Int = 0, override val typedPrefix: String,
    ) : CompletionItem {
        override val tag: CompletionItemTag
            get() = CompletionItemTag.KEYWORD
    }
}

class KeywordItemBuilder {
    lateinit var textToShow: String
    lateinit var textToInsert: String
    var moveCaret: Int = 0
    lateinit var name: String
    var typedPrefix: String = ""

    fun with(completionItem: CompletionItem.Keyword) {
        textToShow = completionItem.show
        textToInsert = completionItem.insert
        moveCaret = completionItem.moveCaret
        name = completionItem.name
    }

    fun withSpace() {
        textToInsert += " "
    }

    fun build() = CompletionItem.Keyword(textToShow, textToInsert, name, moveCaret, typedPrefix)
}

class DeclarationCompletionItemBuilder {
    lateinit var show: String
    var insert: String? = null
    lateinit var tag: CompletionItemTag
    var moveCaret: Int = 0
    var import: Boolean = false
    var fqName: String? = null
    var name: String? = null
    var tail: String? = null
    var middle: String? = null
    var typedPrefix: String = ""

    fun withImport() {
        import = true
    }

    fun with(completionItem: CompletionItem.Declaration) {
        show = completionItem.show
        insert = completionItem.insert
        tag = completionItem.tag
        moveCaret = completionItem.moveCaret
        import = completionItem.import
        fqName = completionItem.fqName
        name = completionItem.name
        tail = completionItem.tail
        middle = completionItem.middle
    }

    fun build() =
        CompletionItem.Declaration(
            show = show,
            insert = insert ?: show,
            name = name ?: fqName?.substringAfterLast(".") ?: error("No name provided"),
            tag = tag,
            moveCaret = moveCaret,
            import = import,
            fqName = fqName,
            tail = tail,
            middle = middle,
            typedPrefix = typedPrefix
        )
}

fun declarationCompletionItem(block: DeclarationCompletionItemBuilder.() -> Unit): CompletionItem.Declaration {
    val builder = DeclarationCompletionItemBuilder().apply(block)
    return builder.build()
}

fun keywordCompletionItem(keyword: String, block: KeywordItemBuilder.() -> Unit = {}): CompletionItem.Keyword {
    return keywordCompletionItem {
        textToShow = keyword
        textToInsert = keyword
        name = keyword
        apply(block)
    }
}

fun keywordCompletionItem(block: KeywordItemBuilder.() -> Unit = {}): CompletionItem.Keyword {
    val builder = KeywordItemBuilder().apply { apply(block) }
    return builder.build()
}

enum class CompletionItemTag(val text: String) {
    FUNCTION("ⓕ"),
    PROPERTY("ⓟ"),
    CLASS("ⓒ"),
    LOCAL_VARIABLE("ⓥ"),
    KEYWORD("ⓚ"),
}
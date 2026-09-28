package ru.hollowhorizon.hollowengine.addons.mcp.docs

/**
 * The engine's English guides, shipped inside the addon. A page is addressed by its path without the
 * extension ("scripting/node/states"); an `index` page also answers to its folder ("scripting/node").
 */
class DocsLibrary(val pages: List<DocsPage>) {
    private val byId = pages.associateBy(DocsPage::id)

    fun page(id: String): DocsPage? {
        val normalized = id.trim().removePrefix("/").removePrefix(SITE_PREFIX).removeSuffix(".mdx").trimEnd('/')
        return byId[normalized] ?: byId["$normalized/index"] ?: byId[normalized.removeSuffix("/index")]
    }

    /** Sections holding every word of [query], best first: a word in a heading weighs more than in text. */
    fun search(query: String, limit: Int): List<DocsHit> {
        val words = query.lowercase().split(WORD_SEPARATORS).filter(String::isNotBlank).distinct()
        if (words.isEmpty()) return emptyList()
        return pages.asSequence()
            .flatMap { page -> page.sections.asSequence().map { section -> page to section } }
            .mapNotNull { (page, section) ->
                val heading = (page.title + " " + section.heading).lowercase()
                val body = section.text.lowercase()
                if (words.any { it !in heading && it !in body }) return@mapNotNull null
                val score = words.sumOf { word -> (if (word in heading) HEADING_WEIGHT else 0) + body.occurrences(word).coerceAtMost(MAX_COUNTED) }
                DocsHit(page, section, score, section.text.lines().firstOrNull { line -> words.any { it in line.lowercase() } }?.trim().orEmpty())
            }
            .sortedByDescending(DocsHit::score)
            .take(limit)
            .toList()
    }

    companion object {
        /** How the pages link to each other on the site; stripped so a link reads as a page id. */
        const val SITE_PREFIX = "docs/hollowengine/"

        private val WORD_SEPARATORS = Regex("[^\\p{L}\\p{N}_.]+")
        private const val HEADING_WEIGHT = 10
        private const val MAX_COUNTED = 5

        private fun String.occurrences(word: String): Int {
            var count = 0
            var from = indexOf(word)
            while (from >= 0) {
                count++
                from = indexOf(word, from + word.length)
            }
            return count
        }

        /** Reads the pages listed in `pages.txt` next to them in the addon's resources. */
        fun load(classLoader: ClassLoader, root: String): DocsLibrary {
            val listing = classLoader.getResourceAsStream("$root/pages.txt")?.bufferedReader()?.use { it.readLines() }.orEmpty()
            val pages = listing.filter(String::isNotBlank).mapNotNull { path ->
                val text = classLoader.getResourceAsStream("$root/$path")?.bufferedReader()?.use { it.readText() }
                    ?: return@mapNotNull null
                DocsPage.parse(path.removeSuffix(".mdx"), text)
            }
            return DocsLibrary(pages)
        }
    }
}

class DocsHit(val page: DocsPage, val section: DocsSection, val score: Int, val excerpt: String)

/** One `#`-heading and the text under it, up to the next heading of any level. */
class DocsSection(val heading: String, val anchor: String, val level: Int, val text: String)

class DocsPage(val id: String, val title: String, val order: Int, val sections: List<DocsSection>) {
    val text: String get() = sections.joinToString("\n\n") { section -> section.render() }

    /** [anchor]'s section together with the deeper sections nested under it. */
    fun section(anchor: String): String? {
        val start = sections.indexOfFirst { it.anchor == anchor.removePrefix("#") }
        if (start < 0) return null
        val level = sections[start].level
        val end = (start + 1 until sections.size).firstOrNull { sections[it].level <= level } ?: sections.size
        return sections.subList(start, end).joinToString("\n\n") { it.render() }
    }

    private fun DocsSection.render(): String =
        if (level == 0) text else "${"#".repeat(level)} $heading\n$text"

    companion object {
        private val HEADING = Regex("^(#{1,6})\\s+(.+?)\\s*#*$")
        private val IMAGE = Regex("^!\\[[^]]*]\\([^)]*\\)\\s*$")
        private val SITE_LINK = Regex("]\\(/${Regex.escape(DocsLibrary.SITE_PREFIX)}")

        fun parse(id: String, source: String): DocsPage {
            val (frontMatter, body) = splitFrontMatter(source.replace("\r\n", "\n"))
            val sections = ArrayList<DocsSection>()
            var heading = ""
            var level = 0
            val text = StringBuilder()
            var inCode = false

            fun flush() {
                val content = text.toString().trim()
                if (level > 0 || content.isNotEmpty()) sections += DocsSection(heading, anchorOf(heading), level, content)
                text.clear()
            }

            for (line in body.lines()) {
                if (line.trimStart().startsWith("```")) inCode = !inCode
                val match = if (inCode) null else HEADING.matchEntire(line)
                when {
                    match != null -> {
                        flush()
                        level = match.groupValues[1].length
                        heading = match.groupValues[2]
                    }
                    !inCode && (IMAGE.matches(line) || line.startsWith("import ") || line.startsWith("export ")) -> Unit
                    else -> text.appendLine(line.replace(SITE_LINK, "]("))
                }
            }
            flush()

            val title = frontMatter["title"] ?: sections.firstOrNull { it.level == 1 }?.heading ?: id
            return DocsPage(id, title, frontMatter["order"]?.toIntOrNull() ?: Int.MAX_VALUE, sections)
        }

        private fun splitFrontMatter(source: String): Pair<Map<String, String>, String> {
            if (!source.startsWith("---\n")) return emptyMap<String, String>() to source
            val end = source.indexOf("\n---", startIndex = 4)
            if (end < 0) return emptyMap<String, String>() to source
            val values = source.substring(4, end).lines().mapNotNull { line ->
                val key = line.substringBefore(':', "").trim()
                if (key.isEmpty()) null else key to line.substringAfter(':').trim().trim('"', '\'')
            }.toMap()
            return values to source.substring(end + 4).trimStart('\n')
        }

        /** The anchor a heading gets on the site: lowercase, spaces to dashes, punctuation dropped. */
        fun anchorOf(heading: String): String =
            heading.lowercase().replace(Regex("[^\\p{L}\\p{N} _-]"), "").trim().replace(Regex("\\s+"), "-")
    }
}

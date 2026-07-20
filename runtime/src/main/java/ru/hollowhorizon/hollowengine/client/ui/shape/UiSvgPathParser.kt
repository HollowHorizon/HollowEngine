package ru.hollowhorizon.hollowengine.client.ui.shape

object SvgPathParser {
    fun parse(source: String): UiPath = Parser(source).parse()

    private class Parser(private val source: String) {
        private val builder = UiPathBuilder()
        private var index = 0
        private var command: Char? = null
        private var current = UiPathPoint(0f, 0f)
        private var subPathStart = UiPathPoint(0f, 0f)

        fun parse(): UiPath {
            while (true) {
                skipSeparators()
                if (index >= source.length) break
                val next = source[index]
                if (next.isSvgCommand()) {
                    command = next
                    index++
                } else if (command == null) {
                    error("Expected SVG path command")
                }
                parseCommand(command ?: error("Expected SVG path command"))
            }
            return builder.build()
        }

        private fun parseCommand(command: Char) {
            when (command) {
                'M', 'm' -> parseMove(command.isRelative)
                'L', 'l' -> repeatPairs { x, y -> lineTo(command.point(x, y)) }
                'H', 'h' -> repeatNumbers { x -> lineTo(command.horizontal(x)) }
                'V', 'v' -> repeatNumbers { y -> lineTo(command.vertical(y)) }
                'C', 'c' -> repeatCubic(command.isRelative)
                'S', 's' -> repeatSmoothCubic(command.isRelative)
                'Q', 'q' -> repeatQuadratic(command.isRelative)
                'T', 't' -> repeatSmoothQuadratic(command.isRelative)
                'A', 'a' -> repeatArc(command.isRelative)
                'Z', 'z' -> close()
                else -> error("Unsupported SVG path command '$command'")
            }
        }

        private fun parseMove(relative: Boolean) {
            val first = point(readNumber(), readNumber(), relative)
            moveTo(first)
            while (hasNumber()) {
                lineTo(point(readNumber(), readNumber(), relative))
            }
            command = if (relative) 'l' else 'L'
        }

        private fun repeatPairs(block: (Float, Float) -> Unit) {
            var parsed = false
            while (hasNumber()) {
                block(readNumber(), readNumber())
                parsed = true
            }
            require(parsed) { "Expected coordinate pair at SVG path offset $index" }
        }

        private fun repeatNumbers(block: (Float) -> Unit) {
            var parsed = false
            while (hasNumber()) {
                block(readNumber())
                parsed = true
            }
            require(parsed) { "Expected number at SVG path offset $index" }
        }

        private fun repeatCubic(relative: Boolean) {
            var parsed = false
            while (hasNumber()) {
                val control1 = point(readNumber(), readNumber(), relative)
                val control2 = point(readNumber(), readNumber(), relative)
                val target = point(readNumber(), readNumber(), relative)
                builder.curveTo(control1.x, control1.y, control2.x, control2.y, target.x, target.y)
                current = target
                parsed = true
            }
            require(parsed) { "Expected cubic curve at SVG path offset $index" }
        }

        private fun repeatSmoothCubic(relative: Boolean) {
            var parsed = false
            while (hasNumber()) {
                val control2 = point(readNumber(), readNumber(), relative)
                val target = point(readNumber(), readNumber(), relative)
                builder.smoothCurveTo(control2.x, control2.y, target.x, target.y)
                current = target
                parsed = true
            }
            require(parsed) { "Expected smooth cubic curve at SVG path offset $index" }
        }

        private fun repeatQuadratic(relative: Boolean) {
            var parsed = false
            while (hasNumber()) {
                val control = point(readNumber(), readNumber(), relative)
                val target = point(readNumber(), readNumber(), relative)
                builder.quadraticBezierTo(control.x, control.y, target.x, target.y)
                current = target
                parsed = true
            }
            require(parsed) { "Expected quadratic curve at SVG path offset $index" }
        }

        private fun repeatSmoothQuadratic(relative: Boolean) {
            var parsed = false
            while (hasNumber()) {
                val target = point(readNumber(), readNumber(), relative)
                builder.smoothQuadraticBezierTo(target.x, target.y)
                current = target
                parsed = true
            }
            require(parsed) { "Expected smooth quadratic curve at SVG path offset $index" }
        }

        private fun repeatArc(relative: Boolean) {
            var parsed = false
            while (hasNumber()) {
                val radiusX = readNumber()
                val radiusY = readNumber()
                val rotation = readNumber()
                val largeArc = readFlag()
                val sweep = readFlag()
                val target = point(readNumber(), readNumber(), relative)
                builder.ellipticalArcTo(radiusX, radiusY, rotation, largeArc, sweep, target.x, target.y)
                current = target
                parsed = true
            }
            require(parsed) { "Expected elliptical arc at SVG path offset $index" }
        }

        private fun moveTo(point: UiPathPoint) {
            builder.moveTo(point.x, point.y)
            current = point
            subPathStart = point
        }

        private fun lineTo(point: UiPathPoint) {
            builder.lineTo(point.x, point.y)
            current = point
        }

        private fun close() {
            builder.close()
            current = subPathStart
            command = null
        }

        private fun Char.point(x: Float, y: Float): UiPathPoint = point(x, y, isRelative)

        private fun Char.horizontal(x: Float): UiPathPoint {
            return if (isRelative) UiPathPoint(current.x + x, current.y) else UiPathPoint(x, current.y)
        }

        private fun Char.vertical(y: Float): UiPathPoint {
            return if (isRelative) UiPathPoint(current.x, current.y + y) else UiPathPoint(current.x, y)
        }

        private fun point(x: Float, y: Float, relative: Boolean): UiPathPoint {
            return if (relative) UiPathPoint(current.x + x, current.y + y) else UiPathPoint(x, y)
        }

        private fun hasNumber(): Boolean {
            skipSeparators()
            val char = source.getOrNull(index) ?: return false
            return char == '+' || char == '-' || char == '.' || char.isDigit()
        }

        private fun readNumber(): Float {
            skipSeparators()
            val start = index
            if (source.getOrNull(index) == '+' || source.getOrNull(index) == '-') index++
            var hasDigits = readDigits()
            if (source.getOrNull(index) == '.') {
                index++
                hasDigits = readDigits() || hasDigits
            }
            require(hasDigits) { "Expected number at SVG path offset $start" }
            val exponentStart = index
            if (source.getOrNull(index) == 'e' || source.getOrNull(index) == 'E') {
                index++
                if (source.getOrNull(index) == '+' || source.getOrNull(index) == '-') index++
                if (!readDigits()) index = exponentStart
            }
            return source.substring(start, index).toFloat()
        }

        private fun readDigits(): Boolean {
            val start = index
            while (source.getOrNull(index)?.isDigit() == true) index++
            return index > start
        }

        private fun readFlag(): Boolean {
            skipSeparators()
            return when (source.getOrNull(index)) {
                '0' -> false.also { index++ }
                '1' -> true.also { index++ }
                else -> error("Expected arc flag at SVG path offset $index")
            }
        }

        private fun skipSeparators() {
            while (index < source.length && (source[index].isWhitespace() || source[index] == ',')) index++
        }

        private fun error(message: String): Nothing = throw IllegalArgumentException("$message in '$source'")
    }
}

private val Char.isRelative: Boolean get() = isLowerCase()

private fun Char.isSvgCommand(): Boolean = this in "MmLlHhVvCcSsQqTtAaZz"

/*
 * MIT License
 *
 * Copyright (c) 2024 HollowHorizon
 *
 * Permission is hereby granted, free of charge, to any person obtaining a copy
 * of this software and associated documentation files (the "Software"), to deal
 * in the Software without restriction, including without limitation the rights
 * to use, copy, modify, merge, publish, distribute, sublicense, and/or sell
 * copies of the Software, and to permit persons to whom the Software is
 * furnished to do so, subject to the following conditions:
 *
 * The above copyright notice and this permission notice shall be included in all
 * copies or substantial portions of the Software.
 *
 * THE SOFTWARE IS PROVIDED "AS IS", WITHOUT WARRANTY OF ANY KIND, EXPRESS OR
 * IMPLIED, INCLUDING BUT NOT LIMITED TO THE WARRANTIES OF MERCHANTABILITY,
 * FITNESS FOR A PARTICULAR PURPOSE AND NONINFRINGEMENT. IN NO EVENT SHALL THE
 * AUTHORS OR COPYRIGHT HOLDERS BE LIABLE FOR ANY CLAIM, DAMAGES OR OTHER
 * LIABILITY, WHETHER IN AN ACTION OF CONTRACT, TORT OR OTHERWISE, ARISING FROM,
 * OUT OF OR IN CONNECTION WITH THE SOFTWARE OR THE USE OR OTHER DEALINGS IN THE
 * SOFTWARE.
 */

package ru.hollowhorizon.hollowengine.common.util

class EditorIniFile {
    val properties = HashMap<String, Property>()

    class Property(val name: String, val x: Int, val y: Int) {
        override fun toString(): String {
            return "$name=$x,$y"
        }
    }

    companion object {
        fun read(data: String): EditorIniFile {
            var name = ""
            val file = EditorIniFile()
            for (line in data.split("\r\n", "\n")) {
                if (line.startsWith("[")) {
                    name = line.substringAfter("[").substringBefore("]")
                    continue
                }
                if (name.isNotEmpty()) {
                    val property = line.split("=")
                    val x = property[1].split(",")[0].toInt()
                    val y = property[1].split(",")[1].toInt()
                    file.properties[name] = Property(property[0], x, y)
                    name = ""
                }
            }
            return file
        }
    }

    override fun toString(): String {
        val sb = StringBuilder()
        for ((key, value) in properties) {
            sb.append('[').append(key).append(']').append('\n')
            sb.append(value.toString()).append('\n')
        }
        return sb.toString()
    }
}
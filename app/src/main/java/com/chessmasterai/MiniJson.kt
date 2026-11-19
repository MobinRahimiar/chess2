package com.chessmasterai

/**
 * Small strict JSON reader/writer (RFC 8259 subset: objects, arrays, strings with escapes,
 * numbers, booleans, null). Pure Kotlin so network/cache parsing is unit-testable on the JVM
 * (android.jar's org.json is only a stub in local unit tests).
 * Objects -> Map<String, Any?>, arrays -> List<Any?>, numbers -> Long or Double.
 */
object MiniJson {
    class JsonException(message: String) : IllegalArgumentException(message)

    fun parse(text: String): Any? {
        val p = Parser(text)
        p.skipWs()
        val value = p.value(0)
        p.skipWs()
        if (!p.atEnd()) throw JsonException("Unexpected trailing characters at ${p.pos}")
        return value
    }

    fun quote(s: String): String = buildString {
        append('"')
        for (ch in s) when {
            ch == '"' -> append("\\\"")
            ch == '\\' -> append("\\\\")
            ch == '\n' -> append("\\n")
            ch == '\r' -> append("\\r")
            ch == '\t' -> append("\\t")
            ch < ' ' -> append(String.format("\\u%04x", ch.code))
            else -> append(ch)
        }
        append('"')
    }

    private class Parser(val s: String) {
        var pos = 0
        fun atEnd() = pos >= s.length
        fun skipWs() { while (pos < s.length && s[pos] in " \t\r\n") pos++ }
        private fun fail(msg: String): Nothing = throw JsonException("$msg at $pos")

        fun value(depth: Int): Any? {
            if (depth > 64) fail("Nesting too deep")
            if (atEnd()) fail("Unexpected end")
            return when (val c = s[pos]) {
                '{' -> obj(depth)
                '[' -> arr(depth)
                '"' -> str()
                't' -> lit("true", true)
                'f' -> lit("false", false)
                'n' -> lit("null", null)
                else -> if (c == '-' || c in '0'..'9') num() else fail("Unexpected '$c'")
            }
        }
        private fun lit(word: String, v: Any?): Any? {
            if (!s.startsWith(word, pos)) fail("Invalid literal")
            pos += word.length; return v
        }
        private fun num(): Any {
            val start = pos
            if (s[pos] == '-') pos++
            while (pos < s.length && (s[pos] in '0'..'9' || s[pos] in ".eE+-")) pos++
            val t = s.substring(start, pos)
            return t.toLongOrNull() ?: t.toDoubleOrNull() ?: fail("Invalid number '$t'")
        }
        private fun str(): String {
            pos++ // opening quote
            val sb = StringBuilder()
            while (true) {
                if (atEnd()) fail("Unterminated string")
                val c = s[pos++]
                when (c) {
                    '"' -> return sb.toString()
                    '\\' -> {
                        if (atEnd()) fail("Bad escape")
                        when (val e = s[pos++]) {
                            '"' -> sb.append('"'); '\\' -> sb.append('\\'); '/' -> sb.append('/')
                            'b' -> sb.append('\b'); 'f' -> sb.append('\u000c'); 'n' -> sb.append('\n')
                            'r' -> sb.append('\r'); 't' -> sb.append('\t')
                            'u' -> {
                                if (pos + 4 > s.length) fail("Bad unicode escape")
                                sb.append(s.substring(pos, pos + 4).toIntOrNull(16)?.toChar() ?: fail("Bad unicode escape"))
                                pos += 4
                            }
                            else -> fail("Bad escape '$e'")
                        }
                    }
                    else -> sb.append(c)
                }
            }
        }
        private fun arr(depth: Int): List<Any?> {
            pos++
            val out = mutableListOf<Any?>()
            skipWs()
            if (!atEnd() && s[pos] == ']') { pos++; return out }
            while (true) {
                skipWs(); out += value(depth + 1); skipWs()
                if (atEnd()) fail("Unterminated array")
                when (s[pos++]) { ',' -> continue; ']' -> return out; else -> fail("Expected ',' or ']'") }
            }
        }
        private fun obj(depth: Int): Map<String, Any?> {
            pos++
            val out = linkedMapOf<String, Any?>()
            skipWs()
            if (!atEnd() && s[pos] == '}') { pos++; return out }
            while (true) {
                skipWs()
                if (atEnd() || s[pos] != '"') fail("Expected string key")
                val key = str(); skipWs()
                if (atEnd() || s[pos++] != ':') fail("Expected ':'")
                skipWs(); out[key] = value(depth + 1); skipWs()
                if (atEnd()) fail("Unterminated object")
                when (s[pos++]) { ',' -> continue; '}' -> return out; else -> fail("Expected ',' or '}'") }
            }
        }
    }
}

internal fun Any?.asObj(): Map<String, Any?>? = (this as? Map<*, *>)?.entries?.associate { it.key.toString() to it.value }
internal fun Any?.asList(): List<Any?>? = this as? List<Any?>
internal fun Any?.asLong(): Long = (this as? Number)?.toLong() ?: 0L
internal fun Any?.asText(): String = (this as? String).orEmpty()

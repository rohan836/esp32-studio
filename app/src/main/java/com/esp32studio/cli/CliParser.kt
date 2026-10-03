package com.esp32studio.cli

data class CliCommand(
    val name: String,
    val arguments: List<String>
)

object CliParser {
    private val aliases = mapOf(
        "?" to "help",
        "lsusb" to "devices",
        "detect" to "devices",
        "build" to "build",
        "flash" to "flash",
        "run" to "run",
        "monitor" to "monitor"
    )

    fun parse(input: String): CliCommand? {
        val tokens = tokenize(input.trim())
        if (tokens.isEmpty()) return null

        val raw = tokens.first()
        val name = when {
            raw == "esp" && tokens.size > 1 -> tokens[1]
            raw == "esp" -> "help"
            else -> raw
        }
        val args = when {
            raw == "esp" -> tokens.drop(2)
            else -> tokens.drop(1)
        }
        return CliCommand(aliases[name] ?: name, args)
    }

    private fun tokenize(input: String): List<String> {
        val result = mutableListOf<String>()
        val current = StringBuilder()
        var quote: Char? = null
        var escaped = false

        for (character in input) {
            when {
                escaped -> {
                    current.append(character)
                    escaped = false
                }
                character == '\\' && quote != '\'' -> escaped = true
                quote != null && character == quote -> quote = null
                quote == null && (character == '\'' || character == '"') -> quote = character
                quote == null && character.isWhitespace() -> {
                    if (current.isNotEmpty()) {
                        result += current.toString()
                        current.clear()
                    }
                }
                else -> current.append(character)
            }
        }

        if (escaped) current.append('\\')
        if (current.isNotEmpty()) result += current.toString()
        return result
    }
}

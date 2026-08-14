package com.example.ui.components

import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.sp

fun parseMarkdown(text: String): AnnotatedString {
    return buildAnnotatedString {
        val lines = text.split("\n")
        lines.forEachIndexed { index, line ->
            var currentLine = line
            var isHeader = false
            var headerLevel = 0
            
            // Check headers
            if (currentLine.startsWith("#")) {
                val level = currentLine.takeWhile { it == '#' }.length
                if (level in 1..6 && currentLine.drop(level).startsWith(" ")) {
                    isHeader = true
                    headerLevel = level
                    currentLine = currentLine.drop(level + 1)
                }
            }

            // Apply Header styling
            if (isHeader) {
                val size = when (headerLevel) {
                    1 -> 20.sp
                    2 -> 18.sp
                    3 -> 16.sp
                    else -> 14.sp
                }
                pushStyle(SpanStyle(fontWeight = FontWeight.Bold, fontSize = size))
                append(parseInlineStyles(currentLine))
                pop()
            } else {
                // Regular line: Check if it's a bullet point
                val trimmed = currentLine.trim()
                if (trimmed.startsWith("- ") || trimmed.startsWith("* ")) {
                    val indent = "  •  "
                    val content = trimmed.substring(2)
                    append(indent)
                    append(parseInlineStyles(content))
                } else if (trimmed.startsWith("1. ") || trimmed.startsWith("2. ") || trimmed.startsWith("3. ") ||
                    trimmed.startsWith("4. ") || trimmed.startsWith("5. ") || trimmed.startsWith("6. ") ||
                    trimmed.startsWith("7. ") || trimmed.startsWith("8. ") || trimmed.startsWith("9. ")
                ) {
                    val num = trimmed.takeWhile { it != ' ' }
                    val content = trimmed.substring(num.length + 1)
                    append("  $num ")
                    append(parseInlineStyles(content))
                } else {
                    append(parseInlineStyles(currentLine))
                }
            }

            if (index < lines.lastIndex) {
                append("\n")
            }
        }
    }
}

private fun parseInlineStyles(text: String): AnnotatedString {
    return buildAnnotatedString {
        var i = 0
        while (i < text.length) {
            if (text.startsWith("**", i)) {
                val end = text.indexOf("**", i + 2)
                if (end != -1) {
                    pushStyle(SpanStyle(fontWeight = FontWeight.Bold))
                    append(text.substring(i + 2, end))
                    pop()
                    i = end + 2
                } else {
                    append("**")
                    i += 2
                }
            } else if (text.startsWith("*", i)) {
                val end = text.indexOf("*", i + 1)
                if (end != -1) {
                    pushStyle(SpanStyle(fontStyle = FontStyle.Italic))
                    append(text.substring(i + 1, end))
                    pop()
                    i = end + 1
                } else {
                    append("*")
                    i += 1
                }
            } else if (text.startsWith("`", i)) {
                val end = text.indexOf("`", i + 1)
                if (end != -1) {
                    pushStyle(SpanStyle(fontFamily = FontFamily.Monospace, fontSize = 11.sp))
                    append(text.substring(i + 1, end))
                    pop()
                    i = end + 1
                } else {
                    append("`")
                    i += 1
                }
            } else {
                append(text[i].toString())
                i++
            }
        }
    }
}

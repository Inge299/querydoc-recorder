package com.querydoc.lecturerecorder.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.ClickableText
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextDecoration
import androidx.compose.ui.text.withStyle
import androidx.compose.ui.unit.dp

@Composable
fun ConspectMarkdown(markdown: String, onCitationClick: ((Int) -> Unit)? = null) {
    val lines = markdown.lines()
    Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
        var index = 0
        while (index < lines.size) {
            val line = lines[index].trimEnd()
            val trimmed = line.trim()
            if (trimmed.startsWith("$$")) {
                val formula = StringBuilder()
                val first = trimmed.removePrefix("$$")
                if (first.endsWith("$$")) {
                    formula.append(first.removeSuffix("$$"))
                    index += 1
                } else {
                    formula.append(first)
                    index += 1
                    while (index < lines.size) {
                        val next = lines[index].trim()
                        if (next.contains("$$")) {
                            formula.append(' ').append(next.substringBefore("$$"))
                            index += 1
                            break
                        }
                        formula.append(' ').append(next)
                        index += 1
                    }
                }
                FormulaBlock(formula.toString())
                continue
            }
            when {
                line.startsWith("# ") -> Text(
                    text = line.removePrefix("# ").trim(),
                    style = MaterialTheme.typography.titleLarge,
                    fontWeight = FontWeight.Bold,
                )
                line.startsWith("## ") -> Text(
                    text = line.removePrefix("## ").trim(),
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.SemiBold,
                    modifier = Modifier.padding(top = 8.dp),
                )
                line.startsWith("- ") || line.startsWith("* ") -> Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(6.dp),
                ) {
                    Text("•", style = MaterialTheme.typography.bodyMedium, color = Color(0xFF4338CA))
                    CitationText(
                        text = line.drop(2).trim(),
                        onCitationClick = onCitationClick,
                        modifier = Modifier.weight(1f),
                    )
                }
                line.isBlank() -> Spacer(Modifier.height(4.dp))
                else -> CitationText(
                    text = line,
                    onCitationClick = onCitationClick,
                    modifier = Modifier.fillMaxWidth(),
                )
            }
            index += 1
        }
    }
}

@Composable
private fun FormulaBlock(formula: String) {
    Box(
        modifier = Modifier
            .fillMaxWidth()
            .background(Color(0xFFF1F5F9), RoundedCornerShape(8.dp))
            .padding(horizontal = 10.dp, vertical = 8.dp),
    ) {
        Text(
            text = latexToReadable(formula),
            style = MaterialTheme.typography.bodyMedium.copy(fontFamily = FontFamily.Monospace),
            color = Color(0xFF1E293B),
        )
    }
}

@Composable
private fun CitationText(
    text: String,
    onCitationClick: ((Int) -> Unit)?,
    modifier: Modifier = Modifier,
) {
    val annotated = inlineFormatted(text)
    if (onCitationClick == null || annotated.getStringAnnotations("citation", 0, annotated.length).isEmpty()) {
        Text(text = annotated, style = MaterialTheme.typography.bodyMedium, modifier = modifier)
        return
    }
    ClickableText(
        text = annotated,
        style = MaterialTheme.typography.bodyMedium,
        modifier = modifier,
        onClick = { offset ->
            annotated.getStringAnnotations("citation", offset, offset).firstOrNull()?.let { annotation ->
                annotation.item.toIntOrNull()?.let(onCitationClick)
            }
        },
    )
}

private fun inlineFormatted(text: String): AnnotatedString = buildAnnotatedString {
    val parts = text.split("**")
    parts.forEachIndexed { index, part ->
        if (index % 2 == 1) {
            withStyle(SpanStyle(fontWeight = FontWeight.Bold)) { appendWithCitations(part) }
        } else {
            appendWithCitations(part)
        }
    }
}

private fun AnnotatedString.Builder.appendWithCitations(text: String) {
    val math = Regex("""\$([^$]+)\$""")
    var cursor = 0
    math.findAll(text).forEach { match ->
        if (match.range.first > cursor) appendWithPlainCitations(text.substring(cursor, match.range.first))
        withStyle(
            SpanStyle(
                fontFamily = FontFamily.Monospace,
                background = Color(0xFFF1F5F9),
                color = Color(0xFF1E293B),
            ),
        ) {
            append(latexToReadable(match.groupValues[1]))
        }
        cursor = match.range.last + 1
    }
    if (cursor < text.length) appendWithPlainCitations(text.substring(cursor))
}

private fun AnnotatedString.Builder.appendWithPlainCitations(text: String) {
    val regex = Regex("""\[(\d+)]""")
    var cursor = 0
    regex.findAll(text).forEach { match ->
        if (match.range.first > cursor) append(text.substring(cursor, match.range.first))
        val start = length
        val label = match.value
        pushStyle(
            SpanStyle(
                color = Color(0xFF4338CA),
                textDecoration = TextDecoration.Underline,
                fontWeight = FontWeight.Medium,
            ),
        )
        append(label)
        pop()
        addStringAnnotation("citation", match.groupValues[1], start, length)
        cursor = match.range.last + 1
    }
    if (cursor < text.length) append(text.substring(cursor))
}

private val greekCommands = mapOf(
    "alpha" to "α", "beta" to "β", "gamma" to "γ", "delta" to "δ", "epsilon" to "ε",
    "theta" to "θ", "lambda" to "λ", "mu" to "μ", "pi" to "π", "rho" to "ρ",
    "sigma" to "σ", "tau" to "τ", "phi" to "φ", "omega" to "ω", "Delta" to "Δ",
    "Omega" to "Ω", "Sigma" to "Σ", "infty" to "∞", "sum" to "Σ", "int" to "∫",
    "cdot" to "·", "times" to "×", "le" to "≤", "ge" to "≥", "neq" to "≠",
    "pm" to "±", "approx" to "≈", "to" to "→", "rightarrow" to "→",
)

private val superscripts = mapOf(
    '0' to '⁰', '1' to '¹', '2' to '²', '3' to '³', '4' to '⁴',
    '5' to '⁵', '6' to '⁶', '7' to '⁷', '8' to '⁸', '9' to '⁹',
    '+' to '⁺', '-' to '⁻', 'n' to 'ⁿ', 'i' to 'ⁱ', 'x' to 'ˣ',
)

private val subscripts = mapOf(
    '0' to '₀', '1' to '₁', '2' to '₂', '3' to '₃', '4' to '₄',
    '5' to '₅', '6' to '₆', '7' to '₇', '8' to '₈', '9' to '₉',
    '+' to '₊', '-' to '₋', 'i' to 'ᵢ', 'j' to 'ⱼ', 'n' to 'ₙ', 'k' to 'ₖ',
)

private fun latexToReadable(raw: String): String {
    var value = raw.trim()
    value = value.replace(Regex("""\\text\{([^{}]*)\}"""), "$1")
    value = value.replace(Regex("""\\frac\{([^{}]*)\}\{([^{}]*)\}"""), "($1)/($2)")
    value = value.replace(Regex("""\\sqrt\{([^{}]*)\}"""), "√($1)")
    value = value.replace(Regex("""\\(left|right|,|;|!|quad|qquad)"""), "")
    greekCommands.forEach { (command, symbol) -> value = value.replace("\\" + command, symbol) }
    value = value.replace("{", "").replace("}", "")
    value = value.replace("\\", "")
    value = renderScripts(value, '^', superscripts)
    value = renderScripts(value, '_', subscripts)
    return value.replace(Regex("""\s+"""), " ").trim()
}

private fun renderScripts(value: String, marker: Char, map: Map<Char, Char>): String {
    val result = StringBuilder()
    var index = 0
    while (index < value.length) {
        val ch = value[index]
        if (ch != marker || index + 1 >= value.length) {
            result.append(ch)
            index += 1
            continue
        }
        val next = value[index + 1]
        val mapped = map[next]
        if (mapped != null) {
            result.append(mapped)
            index += 2
        } else {
            result.append(ch)
            index += 1
        }
    }
    return result.toString()
}

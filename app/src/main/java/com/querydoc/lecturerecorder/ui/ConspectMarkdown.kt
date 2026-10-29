package com.querydoc.lecturerecorder.ui

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.text.ClickableText
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextDecoration
import androidx.compose.ui.text.withStyle
import androidx.compose.ui.unit.dp

@Composable
fun ConspectMarkdown(markdown: String, onCitationClick: ((Int) -> Unit)? = null) {
    Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
        markdown.lines().forEach { rawLine ->
            val line = rawLine.trimEnd()
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
        }
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
    val regex = Regex("\\[(\\d+)]")
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

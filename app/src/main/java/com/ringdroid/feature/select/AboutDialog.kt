package com.ringdroid.feature.select

import android.content.pm.PackageManager
import android.text.Spanned
import android.text.style.URLSpan
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalUriHandler
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.TextLayoutResult
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextDecoration
import androidx.core.text.HtmlCompat
import com.ringdroid.R

@Composable
fun AboutDialog(onDismiss: () -> Unit) {
    val context = LocalContext.current
    val textColor = MaterialTheme.colorScheme.onSurface
    val versionUnknown = stringResource(R.string.version_unknown)

    val versionName =
        remember(versionUnknown) {
            try {
                context.packageManager.getPackageInfo(context.packageName, 0).versionName
            } catch (e: PackageManager.NameNotFoundException) {
                versionUnknown
            }
        }

    // Compose-aware string (updates on locale change)
    val htmlText = stringResource(R.string.about_text_html, versionName ?: "")

    // Convert HTML → Spanned only when text changes
    val message =
        remember(htmlText) { HtmlCompat.fromHtml(htmlText, HtmlCompat.FROM_HTML_MODE_LEGACY) }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(text = stringResource(R.string.about_title)) },
        text = {
            val uriHandler = LocalUriHandler.current
            val annotatedText =
                remember(message) { message.toAnnotatedString(textColor = textColor) }
            var layoutResult by remember { mutableStateOf<TextLayoutResult?>(null) }
            Text(
                text = annotatedText,
                style = MaterialTheme.typography.bodyMedium.copy(color = textColor),
                modifier =
                    Modifier.pointerInput(annotatedText) {
                        detectTapGestures { tapOffset ->
                            layoutResult?.let { result ->
                                val position = result.getOffsetForPosition(tapOffset)
                                annotatedText
                                    .getStringAnnotations("URL", position, position)
                                    .firstOrNull()
                                    ?.let { uriHandler.openUri(it.item) }
                            }
                        }
                    },
                onTextLayout = { layoutResult = it },
            )
        },
        confirmButton = {
            TextButton(onClick = onDismiss) {
                Text(text = stringResource(R.string.alert_ok_button))
            }
        },
    )
}

private fun CharSequence.toAnnotatedString(textColor: Color): AnnotatedString {
    if (this !is Spanned) {
        return AnnotatedString(this.toString())
    }
    val spanned = this
    return buildAnnotatedString {
        append(spanned.toString())
        spanned.getSpans(0, spanned.length, URLSpan::class.java).forEach { span ->
            val start = spanned.getSpanStart(span)
            val end = spanned.getSpanEnd(span)
            addStyle(
                SpanStyle(
                    color = textColor,
                    textDecoration = TextDecoration.Underline,
                    fontWeight = FontWeight.Medium,
                ),
                start,
                end,
            )
            addStringAnnotation("URL", span.url, start, end)
        }
    }
}

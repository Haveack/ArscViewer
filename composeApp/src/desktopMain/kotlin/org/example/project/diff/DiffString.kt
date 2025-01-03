package org.example.project.diff

import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.withStyle
import org.example.project.Size

fun Size.toDiffString(): String {
    return if (bytes > 0) {
        "+$this"
    } else {
        toString()
    }
}

fun Size.toAnnotatedDiffStringWithStyle(
    increaseStyle: SpanStyle = SpanStyle(color = Color.Magenta),
    decreaseStyle: SpanStyle = SpanStyle(color = Color.Red),
    unchangedStyle: SpanStyle = SpanStyle(color = Color.Gray),
): AnnotatedString {
    val size = this
    return buildAnnotatedString {
        when {
            bytes > 0L -> {
                withStyle(increaseStyle) {
                    append("+$size")
                }
            }
            bytes == 0L -> {
                withStyle(unchangedStyle) {
                    append(size.toString())
                }
            }
            else -> {
                withStyle(decreaseStyle) {
                    append(size.toString())
                }
            }
        }
    }
}

fun Size.toAnnotatedDiffString(
    increaseColor: Color = Color.Magenta,
    decreaseColor: Color =  Color.Red,
    unchangedColor: Color = Color.Gray
): AnnotatedString {
    return toAnnotatedDiffStringWithStyle(
        increaseStyle = SpanStyle(color = increaseColor),
        decreaseStyle = SpanStyle(color = decreaseColor),
        unchangedStyle = SpanStyle(color = unchangedColor)
    )
}

fun Int.toDiffString(): String {
    return if (this > 0) {
        "+$this"
    } else {
        toString()
    }
}

fun Int.toAnnotatedDiffStringWithStyle(
    increaseStyle: SpanStyle = SpanStyle(color = Color.Magenta),
    decreaseStyle: SpanStyle = SpanStyle(color = Color.Red),
    unchangedStyle: SpanStyle = SpanStyle(color = Color.Gray),
): AnnotatedString {
    val value = this
    return buildAnnotatedString {
        when {
            value > 0 -> {
                withStyle(increaseStyle) {
                    append("+$value")
                }
            }
            value == 0 -> {
                withStyle(unchangedStyle) {
                    append(value.toString())
                }
            }
            else -> {
                withStyle(decreaseStyle) {
                    append(value.toString())
                }
            }
        }
    }
}

fun Int.toAnnotatedDiffString(
    increaseColor: Color = Color.Magenta,
    decreaseColor: Color = Color.Red,
    unchangedColor: Color = Color.Gray,
): AnnotatedString {
    return toAnnotatedDiffStringWithStyle(
        increaseStyle = SpanStyle(color = increaseColor),
        decreaseStyle = SpanStyle(color = decreaseColor),
        unchangedStyle = SpanStyle(color = unchangedColor)
    )
}


package com.contextswitcher.android.terminal

import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.font.FontWeight

/// Parses `tmux capture-pane -e` output (SGR/ANSI color escapes) into an
/// [AnnotatedString] for a monospace, dark-background terminal view. Only
/// `ESC [ ... m` (SGR) sequences are interpreted — 16-color (30-37/40-47,
/// 90-97/100-107), 256-color (`38;5;n`/`48;5;n`), 24-bit (`38;2;r;g;b`/
/// `48;2;r;g;b`) and bold (`1`); every other escape (cursor movement, other
/// CSI finals, OSC) is stripped rather than rendered, since a static
/// snapshot has no cursor to move.
// [impl->dsn~android-terminal-snapshot~3]
object SgrParser {

    private val ESCAPE = Regex("\\[([0-9;]*)([A-Za-z])")

    private val ANSI_16 = arrayOf(
        Color(0xFF000000.toInt()), Color(0xFFCD0000.toInt()), Color(0xFF00CD00.toInt()),
        Color(0xFFCDCD00.toInt()), Color(0xFF0000EE.toInt()), Color(0xFFCD00CD.toInt()),
        Color(0xFF00CDCD.toInt()), Color(0xFFE5E5E5.toInt()),
    )
    private val ANSI_16_BRIGHT = arrayOf(
        Color(0xFF7F7F7F.toInt()), Color(0xFFFF0000.toInt()), Color(0xFF00FF00.toInt()),
        Color(0xFFFFFF00.toInt()), Color(0xFF5C5CFF.toInt()), Color(0xFFFF00FF.toInt()),
        Color(0xFF00FFFF.toInt()), Color(0xFFFFFFFF.toInt()),
    )

    private data class Pen(val fg: Color? = null, val bg: Color? = null, val bold: Boolean = false) {
        fun toSpanStyle(): SpanStyle = SpanStyle(
            color = fg ?: Color.Unspecified,
            background = bg ?: Color.Unspecified,
            fontWeight = if (bold) FontWeight.Bold else FontWeight.Normal,
        )
    }

    /// Renders `text` (raw `capture-pane -e` output) as an [AnnotatedString]:
    /// plain runs keep the caller's default style, styled runs carry a
    /// [SpanStyle] built from the SGR parameters seen so far.
    fun parse(text: String): AnnotatedString {
        val builder = AnnotatedString.Builder()
        var pen = Pen()
        var index = 0
        var spanStart = 0

        fun flush(end: Int) {
            if (end > spanStart) {
                val style = pen.toSpanStyle()
                if (style.color != Color.Unspecified || style.background != Color.Unspecified
                    || style.fontWeight == FontWeight.Bold) {
                    builder.addStyle(style, spanStart, end)
                }
            }
        }

        while (index < text.length) {
            val match = ESCAPE.find(text, index)
            if (match == null) {
                builder.append(text.substring(index))
                flush(builder.length)
                break
            }
            builder.append(text.substring(index, match.range.first))
            flush(builder.length)
            spanStart = builder.length
            if (match.groupValues[2] == "m") {
                pen = applySgr(pen, match.groupValues[1])
            }
            index = match.range.last + 1
        }
        return builder.toAnnotatedString()
    }

    private fun applySgr(current: Pen, params: String): Pen {
        val codes = if (params.isEmpty()) listOf(0) else params.split(";").map { it.toIntOrNull() ?: 0 }
        var pen = current
        var i = 0
        while (i < codes.size) {
            when (val code = codes[i]) {
                0 -> pen = Pen()
                1 -> pen = pen.copy(bold = true)
                22 -> pen = pen.copy(bold = false)
                39 -> pen = pen.copy(fg = null)
                49 -> pen = pen.copy(bg = null)
                in 30..37 -> pen = pen.copy(fg = ANSI_16[code - 30])
                in 90..97 -> pen = pen.copy(fg = ANSI_16_BRIGHT[code - 90])
                in 40..47 -> pen = pen.copy(bg = ANSI_16[code - 40])
                in 100..107 -> pen = pen.copy(bg = ANSI_16_BRIGHT[code - 100])
                38, 48 -> {
                    val (color, consumed) = extendedColor(codes, i + 1)
                    if (color != null) {
                        pen = if (code == 38) pen.copy(fg = color) else pen.copy(bg = color)
                    }
                    i += consumed
                }
                else -> {}
            }
            i++
        }
        return pen
    }

    /// Parses the `5;n` (256-color) or `2;r;g;b` (24-bit) tail following a
    /// `38`/`48` code, starting at `from`. Returns the color (or null if the
    /// tail is malformed/truncated) and how many extra codes were consumed.
    private fun extendedColor(codes: List<Int>, from: Int): Pair<Color?, Int> {
        if (from >= codes.size) {
            return null to 0
        }
        return when (codes[from]) {
            5 -> if (from + 1 < codes.size) color256(codes[from + 1]) to 2 else null to 1
            2 -> if (from + 3 < codes.size) {
                Color(codes[from + 1].coerceIn(0, 255), codes[from + 2].coerceIn(0, 255),
                    codes[from + 3].coerceIn(0, 255)) to 4
            } else {
                null to (codes.size - from)
            }
            else -> null to 1
        }
    }

    private fun color256(n: Int): Color {
        if (n < 16) {
            return if (n < 8) ANSI_16[n] else ANSI_16_BRIGHT[n - 8]
        }
        if (n in 16..231) {
            val i = n - 16
            val r = i / 36
            val g = (i % 36) / 6
            val b = i % 6
            fun level(v: Int) = if (v == 0) 0 else 55 + v * 40
            return Color(level(r), level(g), level(b))
        }
        val gray = 8 + (n - 232) * 10
        return Color(gray.coerceIn(0, 255), gray.coerceIn(0, 255), gray.coerceIn(0, 255))
    }
}

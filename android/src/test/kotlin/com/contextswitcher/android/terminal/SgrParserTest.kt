package com.contextswitcher.android.terminal

import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.Test

private const val ESC = ""

// [utest->dsn~android-terminal-snapshot~3]
class SgrParserTest {

    @Test
    fun `plain text carries no spans`() {
        val result = SgrParser.parse("hello world")
        assertThat(result.text).isEqualTo("hello world")
        assertThat(result.spanStyles).isEmpty()
    }

    @Test
    fun `16-color foreground applies to the following text`() {
        val result = SgrParser.parse("$ESC[31mred$ESC[0m plain")
        assertThat(result.text).isEqualTo("red plain")
        assertThat(result.spanStyles).hasSize(1)
        val span = result.spanStyles.single()
        assertThat(span.item.color).isEqualTo(Color(0xFFCD0000.toInt()))
        assertThat(span.start).isEqualTo(0)
        assertThat(span.end).isEqualTo(3)
    }

    @Test
    fun `bright background and bold combine`() {
        val result = SgrParser.parse("$ESC[1;102mbold-on-bright-green$ESC[0m")
        val span = result.spanStyles.single()
        assertThat(span.item.fontWeight).isEqualTo(FontWeight.Bold)
        assertThat(span.item.background).isEqualTo(Color(0xFF00FF00.toInt()))
    }

    @Test
    fun `256-color palette index resolves to the standard cube color`() {
        // 38;5;196 is the 256-color "bright red" cube entry.
        val result = SgrParser.parse("$ESC[38;5;196mred256$ESC[0m")
        val span = result.spanStyles.single()
        assertThat(span.item.color).isEqualTo(Color(255, 0, 0))
    }

    @Test
    fun `256-color grayscale ramp resolves`() {
        val result = SgrParser.parse("$ESC[38;5;232mdarkest$ESC[0m")
        val span = result.spanStyles.single()
        assertThat(span.item.color).isEqualTo(Color(8, 8, 8))
    }

    @Test
    fun `24-bit truecolor applies exact rgb`() {
        val result = SgrParser.parse("$ESC[38;2;10;20;30mtruecolor$ESC[0m")
        val span = result.spanStyles.single()
        assertThat(span.item.color).isEqualTo(Color(10, 20, 30))
    }

    @Test
    fun `reset code 0 clears color and bold`() {
        val result = SgrParser.parse("$ESC[1;31mred-bold$ESC[0mplain")
        assertThat(result.text).isEqualTo("red-boldplain")
        assertThat(result.spanStyles).hasSize(1)
        assertThat(result.spanStyles.single().end).isEqualTo(8)
    }

    @Test
    fun `non-SGR CSI sequences are stripped but do not affect color`() {
        // cursor-position ("H") is not a color code; it must vanish from the
        // text without leaving a span or throwing.
        val result = SgrParser.parse("${ESC}[2;5Hmoved")
        assertThat(result.text).isEqualTo("moved")
        assertThat(result.spanStyles).isEmpty()
    }

    @Test
    fun `default fg code 39 clears foreground only`() {
        val result = SgrParser.parse("$ESC[41;31mred-on-red$ESC[39mstill-red-bg")
        assertThat(result.text).isEqualTo("red-on-redstill-red-bg")
        assertThat(result.spanStyles).hasSize(2)
        assertThat(result.spanStyles[1].item.background).isEqualTo(Color(0xFFCD0000.toInt()))
        assertThat(result.spanStyles[1].item.color).isEqualTo(Color.Unspecified)
    }

    @Test
    fun `empty input produces empty result`() {
        val result = SgrParser.parse("")
        assertThat(result.text).isEmpty()
        assertThat(result.spanStyles).isEmpty()
    }
}

package warlockfe.warlock3.telnet.ansi

/**
 * Splits the text a MUD sends into runs of one style each, by reading the ANSI escape sequences
 * in it and taking them out.
 *
 * Only SGR (`ESC [ ... m`), the "select graphic rendition" sequence that sets colours and
 * attributes, has any effect. Everything else a terminal would understand - cursor movement,
 * erasing, the OSC title-setting sequences - is recognised well enough to be dropped whole, since
 * none of it means anything in a scrolling text window and leaving the bytes in would show as
 * garbage.
 *
 * A sequence can be cut by the end of a socket read, so the parser keeps its place between calls;
 * the style in force carries over too, as it does on a terminal.
 */
class AnsiParser {
    private enum class State {
        TEXT,
        ESCAPE,

        // ESC [ seen: parameters and intermediates until a final byte.
        CSI,

        // ESC ] seen: an operating system command, up to BEL or ESC \.
        OSC,
        OSC_ESCAPE,

        // ESC followed by an intermediate byte (0x20-0x2F): a sequence such as `ESC ( B`, up to a
        // final byte.
        INTERMEDIATE,
    }

    private var state = State.TEXT
    private val parameters = StringBuilder()
    private val text = StringBuilder()
    private var style = AnsiStyle.Default

    /** The style the next text will be in, as the sequences so far have left it. */
    val currentStyle: AnsiStyle
        get() = style

    fun parse(input: String): List<AnsiSpan> {
        val spans = ArrayList<AnsiSpan>()
        for (c in input) {
            feed(c, spans)
        }
        flushText(spans)
        return spans
    }

    private fun feed(
        c: Char,
        spans: MutableList<AnsiSpan>,
    ) {
        when (state) {
            State.TEXT -> {
                when {
                    c == ESC -> {
                        state = State.ESCAPE
                    }

                    c == '\n' || c == '\r' || c == '\t' || (c.code >= 0x20 && c.code != DEL) -> {
                        text.append(c)
                    }

                    // Every other control character: BEL, backspace, and the like. Nothing to
                    // show for them.
                    else -> {}
                }
            }

            State.ESCAPE -> {
                when {
                    c == '[' -> {
                        parameters.clear()
                        state = State.CSI
                    }

                    c == ']' -> {
                        state = State.OSC
                    }

                    c.code in 0x20..0x2F -> {
                        state = State.INTERMEDIATE
                    }

                    // A two-character sequence (ESC and one more), or something that is not an
                    // escape sequence at all. Dropping both is the reading that shows the least
                    // garbage either way.
                    else -> {
                        state = State.TEXT
                    }
                }
            }

            State.CSI -> {
                when (c.code) {
                    in 0x30..0x3F -> {
                        parameters.append(c)
                    }

                    in 0x20..0x2F -> {
                        // An intermediate byte; a sequence with one is never SGR.
                        parameters.clear()
                        parameters.append(NOT_SGR)
                    }

                    in 0x40..0x7E -> {
                        if (c == 'm') {
                            flushText(spans)
                            style = applySgr(style, parameters.toString())
                        }
                        state = State.TEXT
                    }

                    // Not a valid CSI byte: the sequence was cut short. Read this character
                    // afresh as text.
                    else -> {
                        state = State.TEXT
                        feed(c, spans)
                    }
                }
            }

            State.OSC -> {
                when (c) {
                    BEL -> {
                        state = State.TEXT
                    }

                    ESC -> {
                        state = State.OSC_ESCAPE
                    }

                    else -> {}
                }
            }

            State.OSC_ESCAPE -> {
                when (c) {
                    '\\' -> {
                        state = State.TEXT
                    }

                    // Not the string terminator, so the OSC was never finished. Read this as a
                    // new escape sequence instead of swallowing text until a BEL that may never
                    // come.
                    else -> {
                        state = State.ESCAPE
                        feed(c, spans)
                    }
                }
            }

            State.INTERMEDIATE -> {
                when (c.code) {
                    in 0x20..0x2F -> {}

                    else -> {
                        state = State.TEXT
                    }
                }
            }
        }
    }

    private fun flushText(spans: MutableList<AnsiSpan>) {
        if (text.isEmpty()) return
        spans += AnsiSpan(text.toString(), style)
        text.clear()
    }

    private companion object {
        const val ESC = '\u001B'
        const val BEL = '\u0007'
        const val DEL = 0x7F

        // A parameter string no SGR sequence can have, marking a CSI sequence with an intermediate
        // byte so it is ignored even when its final byte is 'm'.
        const val NOT_SGR = "!"
    }
}

/**
 * The style [sgr] leaves in force, starting from [current]. [sgr] is the parameter string between
 * `ESC [` and `m`: numbers separated by `;` (or `:`, which some servers use for the sub-parameters
 * of the extended colours). An empty string, like an explicit `0`, is a reset.
 */
internal fun applySgr(
    current: AnsiStyle,
    sgr: String,
): AnsiStyle {
    if (sgr.isEmpty()) return AnsiStyle.Default
    // An empty parameter is 0, as in `ESC[;1m`. Anything that is not a number (a private-mode
    // `?`, say) makes this something other than SGR, and nothing changes.
    val params =
        sgr.split(';', ':').map { if (it.isEmpty()) 0 else it.toIntOrNull() ?: return current }
    var style = current
    var i = 0
    while (i < params.size) {
        when (val p = params[i]) {
            0 -> {
                style = AnsiStyle.Default
            }

            1 -> {
                style = style.copy(bold = true)
            }

            2 -> {
                style = style.copy(faint = true)
            }

            3 -> {
                style = style.copy(italic = true)
            }

            4 -> {
                style = style.copy(underline = true)
            }

            7 -> {
                style = style.copy(inverse = true)
            }

            9 -> {
                style = style.copy(strikethrough = true)
            }

            21, 22 -> {
                style = style.copy(bold = false, faint = false)
            }

            23 -> {
                style = style.copy(italic = false)
            }

            24 -> {
                style = style.copy(underline = false)
            }

            27 -> {
                style = style.copy(inverse = false)
            }

            29 -> {
                style = style.copy(strikethrough = false)
            }

            in 30..37 -> {
                style = style.copy(foreground = AnsiColor.Indexed(p - 30))
            }

            38 -> {
                val (color, consumed) = extendedColor(params, i + 1)
                if (color != null) style = style.copy(foreground = color)
                i += consumed
            }

            39 -> {
                style = style.copy(foreground = null)
            }

            in 40..47 -> {
                style = style.copy(background = AnsiColor.Indexed(p - 40))
            }

            48 -> {
                val (color, consumed) = extendedColor(params, i + 1)
                if (color != null) style = style.copy(background = color)
                i += consumed
            }

            49 -> {
                style = style.copy(background = null)
            }

            in 90..97 -> {
                style = style.copy(foreground = AnsiColor.Indexed(p - 90 + 8))
            }

            in 100..107 -> {
                style = style.copy(background = AnsiColor.Indexed(p - 100 + 8))
            }

            // Blink, fonts, and the rest: nothing to do.
            else -> {}
        }
        i++
    }
    return style
}

/**
 * The colour named by the sub-parameters of a 38 or 48 starting at [from] - `5;n` for indexed,
 * `2;r;g;b` for RGB - and how many parameters that took. Null and zero for anything else.
 */
private fun extendedColor(
    params: List<Int>,
    from: Int,
): Pair<AnsiColor?, Int> {
    if (from >= params.size) return null to 0
    return when (params[from]) {
        5 -> {
            val index = params.getOrNull(from + 1)
            if (index != null && index in 0..255) AnsiColor.Indexed(index) to 2 else null to 1
        }

        2 -> {
            val r = params.getOrNull(from + 1)
            val g = params.getOrNull(from + 2)
            val b = params.getOrNull(from + 3)
            if (r != null && g != null && b != null && r in 0..255 && g in 0..255 && b in 0..255) {
                AnsiColor.Rgb(r, g, b) to 4
            } else {
                null to 1
            }
        }

        else -> {
            null to 0
        }
    }
}

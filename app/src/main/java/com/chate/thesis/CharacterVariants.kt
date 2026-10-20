package com.chate.thesis

import android.content.Context
import android.text.Spannable
import android.text.SpannableStringBuilder
import android.text.style.ForegroundColorSpan
import android.text.style.RelativeSizeSpan
import android.text.style.SuperscriptSpan
import androidx.core.content.ContextCompat

object CharacterVariants {

    private val letterVariants = mapOf(
        "q" to listOf("1", "q"),
        "w" to listOf("2", "w"),
        "e" to listOf("3", "e", "é", "è", "ê", "ë", "ē", "ė", "ę"),
        "r" to listOf("4", "r"),
        "t" to listOf("5", "t", "þ"),
        "y" to listOf("6", "y", "ÿ"),
        "u" to listOf("7", "u", "ú", "ù", "û", "ü", "ū"),
        "i" to listOf("8", "i", "í", "ì", "î", "ï", "ī", "į"),
        "o" to listOf("9", "o", "ó", "ò", "ô", "ö", "õ", "œ", "ō", "ø"),
        "p" to listOf("0", "p"),
        "a" to listOf("á", "a", "à", "â", "ä", "æ", "ã", "å", "ā"),
        "s" to listOf("s", "ß", "ś", "š", "$"),
        "d" to listOf("d", "ð", "ď"),
        "f" to listOf("f"),
        "g" to listOf("g", "ğ"),
        "h" to listOf("h"),
        "j" to listOf("j"),
        "k" to listOf("k"),
        "l" to listOf("l", "ł", "£"),
        "z" to listOf("z", "ž", "ź", "ż"),
        "x" to listOf("x"),
        "c" to listOf("ç", "c", "ć", "č"),
        "v" to listOf("v"),
        "b" to listOf("b", "ß"),
        "n" to listOf("ñ", "n", "ń"),
        "m" to listOf("m"),
        "." to listOf(".", "…", "!", "?", ",", "-", ":", ";", "/", "@", "'"),
        "," to listOf(",", ";", ":", "!", "?", "'"),
        "?" to listOf("?", "¿", "‽"),
        "!" to listOf("!", "¡"),
        "-" to listOf("-", "—", "–", "·", "_"),
        "$" to listOf("$", "€", "£", "¥", "₩", "₹", "¢", "₽"),
        "%" to listOf("%", "‰"),
        "&" to listOf("&", "§"),
        "\"" to listOf("\"", "“", "”", "«", "»", "„"),
        "'" to listOf("'", "‘", "’", "`"),
        "/" to listOf("/", "\\"),
        "\\" to listOf("\\", "/"),
        "(" to listOf("(", "[", "{", "<"),
        ")" to listOf(")", "]", "}", ">"),
        "[" to listOf("[", "{", "<"),
        "]" to listOf("]", "}", ">"),
        "=" to listOf("=", "≠", "≈", "±"),
        "+" to listOf("+", "±"),
        "*" to listOf("*", "★", "†", "‡"),
        ";" to listOf(";", ":"),
        ":" to listOf(":", ";"),
        "1" to listOf("1", "¹", "½", "⅓", "¼"),
        "2" to listOf("2", "²", "⅔"),
        "3" to listOf("3", "³", "¾", "⅜"),
        "4" to listOf("4", "⁴"),
        "5" to listOf("5", "⅝"),
        "6" to listOf("6"),
        "7" to listOf("7", "⅞"),
        "8" to listOf("8"),
        "9" to listOf("9"),
        "0" to listOf("0", "°", "∅")
    )

    private val alphaKeyHints = mapOf(
        "q" to "1", "w" to "2", "e" to "3", "r" to "4", "t" to "5",
        "y" to "6", "u" to "7", "i" to "8", "o" to "9", "p" to "0",
        "a" to "@", "s" to "#", "d" to "$", "f" to "%", "g" to "&",
        "h" to "-", "j" to "+", "k" to "(", "l" to ")",
        "z" to "*", "x" to "\"", "c" to "'", "v" to ":", "b" to ";",
        "n" to "!", "m" to "?",
        "," to ";", "." to "…"
    )

    private val symbolKeyHints = mapOf(
        "1" to "¹", "2" to "²", "3" to "³", "4" to "⁴", "5" to "⅝",
        "7" to "⅞", "0" to "°", "$" to "€", "%" to "‰", "-" to "—",
        "." to "…", "," to ";"
    )

    fun getVariants(keyChar: String, isShifted: Boolean): List<String> {
        val lowerKey = keyChar.lowercase()
        val list = letterVariants[lowerKey] ?: return emptyList()

        if (!isShifted) return list

        return list.map { variant ->
            if (variant.length == 1 && variant[0].isLetter()) {
                variant.uppercase()
            } else {
                variant
            }
        }
    }

    fun hasVariants(keyChar: String): Boolean {
        val list = letterVariants[keyChar.lowercase()]
        return list != null && list.size > 1
    }

    fun getKeyHint(keyChar: String, isSymbols: Boolean): String? {
        val lower = keyChar.lowercase()
        return if (isSymbols) {
            symbolKeyHints[lower]
        } else {
            alphaKeyHints[lower]
        }
    }

    fun formatKeyLabel(baseChar: String, isSymbols: Boolean, showHint: Boolean, context: Context): CharSequence {
        if (!showHint) return baseChar

        val hint = getKeyHint(baseChar, isSymbols) ?: return baseChar
        val ssb = SpannableStringBuilder(baseChar)
        val start = ssb.length
        ssb.append(hint)
        val end = ssb.length

        val hintColor = ContextCompat.getColor(context, R.color.key_text_hint)
        ssb.setSpan(RelativeSizeSpan(0.42f), start, end, Spannable.SPAN_EXCLUSIVE_EXCLUSIVE)
        ssb.setSpan(SuperscriptSpan(), start, end, Spannable.SPAN_EXCLUSIVE_EXCLUSIVE)
        ssb.setSpan(ForegroundColorSpan(hintColor), start, end, Spannable.SPAN_EXCLUSIVE_EXCLUSIVE)

        return ssb
    }
}

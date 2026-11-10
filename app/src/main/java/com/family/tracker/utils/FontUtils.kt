package com.family.tracker.utils

import android.content.Context
import android.widget.TextView
import androidx.core.content.res.ResourcesCompat
import com.family.tracker.R

object FontUtils {

    // Urdu-exclusive characters
    private val urduSpecificChars = charArrayOf('ٹ', 'ڈ', 'ڑ', 'ں', 'ھ', 'ے', 'ۓ', 'ۂ')
    // Arabic-exclusive characters (not commonly used in Urdu)
    private val arabicSpecificChars = charArrayOf('ة', 'ى', 'ك')

    fun applyUrdu(context: Context, textView: TextView) {
        try {
            val typeface = ResourcesCompat.getFont(context, R.font.urdu_nastaliq)
            textView.typeface = typeface
        } catch (e: Exception) {
            e.printStackTrace()
        }
    }

    fun applyArabic(context: Context, textView: TextView) {
        try {
            val typeface = ResourcesCompat.getFont(context, R.font.arabic_naskh)
            textView.typeface = typeface
        } catch (e: Exception) {
            e.printStackTrace()
        }
    }

    /**
     * Automatically detects if the text is Urdu, Arabic, or Default and applies the correct font.
     */
    fun applySmartFont(context: Context, textView: TextView, text: String) {
        if (text.isBlank()) return

        var hasUrdu = false
        var hasArabicScript = false

        for (char in text) {
            if (urduSpecificChars.contains(char)) {
                hasUrdu = true
                break
            }
            // Check if the character belongs to the Arabic Unicode block (0600–06FF)
            if (char.code in 0x0600..0x06FF) {
                hasArabicScript = true
            }
        }

        if (hasUrdu) {
            applyUrdu(context, textView)
        } else if (hasArabicScript) {
            // If it has Arabic script but no Urdu-specific characters, default to Arabic font
            applyArabic(context, textView)
        }
        // If it's English or unsupported, it leaves the default system font intact
    }
}
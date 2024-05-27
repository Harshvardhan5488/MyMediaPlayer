package com.harsh.taptargetview

import android.content.Context
import android.util.TypedValue


internal object UiUtil {
    /** Returns the given pixel value in dp  */
    fun dp(context: Context, `val`: Int): Int {
        return TypedValue.applyDimension(
            TypedValue.COMPLEX_UNIT_DIP, `val`.toFloat(), context.resources.displayMetrics).toInt()
    }

    /** Returns the given pixel value in sp  */
    fun sp(context: Context, `val`: Int): Int {
        return TypedValue.applyDimension(
            TypedValue.COMPLEX_UNIT_SP, `val`.toFloat(), context.resources.displayMetrics).toInt()
    }

    /** Returns the value of the desired theme integer attribute, or -1 if not found  */
    fun themeIntAttr(context: Context, attr: String?): Int {
        val theme = context.theme ?: return -1
        val value = TypedValue()
        val id = context.resources.getIdentifier(attr, "attr", context.packageName)
        if (id == 0) {
            // Not found
            return -1
        }
        theme.resolveAttribute(id, value, true)
        return value.data
    }

    /** Modifies the alpha value of the given ARGB color  */
    fun setAlpha(argb: Int, alpha: Float): Int {
        var alpha = alpha
        if (alpha > 1.0f) {
            alpha = 1.0f
        } else if (alpha <= 0.0f) {
            alpha = 0.0f
        }
        return ((argb ushr 24) * alpha).toInt() shl 24 or (argb and 0x00FFFFFF)
    }
}


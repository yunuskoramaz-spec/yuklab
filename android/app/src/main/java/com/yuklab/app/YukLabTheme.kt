package com.yuklab.app

import android.content.Context
import android.graphics.Color
import android.graphics.Typeface
import android.graphics.drawable.GradientDrawable
import android.view.Gravity
import android.view.View
import android.widget.EditText
import android.widget.LinearLayout
import android.widget.TextView

object YukLabTheme {
    val TURQUOISE: Int = Color.parseColor("#00B8BD")
    val TURQUOISE_DARK: Int = Color.parseColor("#007E82")
    val TURQUOISE_SOFT: Int = Color.parseColor("#E6F8F8")
    val ANTHRACITE: Int = Color.parseColor("#171B1E")
    val ANTHRACITE_DEEP: Int = Color.parseColor("#0E1214")
    val BACKGROUND: Int = Color.parseColor("#F4F6F7")
    val SURFACE: Int = Color.WHITE
    val SURFACE_ALT: Int = Color.parseColor("#EEF2F3")
    val TEXT: Int = ANTHRACITE
    val MUTED: Int = Color.parseColor("#6B7478")
    val BORDER: Int = Color.parseColor("#D9E0E3")
    val SUCCESS: Int = Color.parseColor("#16895B")
    val SUCCESS_SOFT: Int = Color.parseColor("#EAF8F2")
    val WARNING: Int = Color.parseColor("#B96F10")
    val ERROR: Int = Color.parseColor("#D53A45")
    val ERROR_SOFT: Int = Color.parseColor("#FDECEF")

    fun Context.dp(value: Int): Int = (value * resources.displayMetrics.density).toInt()

    fun shape(
        context: Context,
        color: Int,
        radiusDp: Float = 18f,
        strokeColor: Int? = null,
        strokeWidthDp: Int = 1
    ): GradientDrawable = GradientDrawable().apply {
        setColor(color)
        cornerRadius = context.dp(radiusDp.toInt()).toFloat()
        if (strokeColor != null) setStroke(context.dp(strokeWidthDp), strokeColor)
    }

    fun gradient(context: Context, start: Int = ANTHRACITE, end: Int = ANTHRACITE_DEEP): GradientDrawable =
        GradientDrawable(GradientDrawable.Orientation.TL_BR, intArrayOf(start, end)).apply {
            cornerRadius = context.dp(24).toFloat()
        }

    fun text(context: Context, value: String, sizeSp: Float = 15f, color: Int = TEXT, bold: Boolean = false): TextView =
        TextView(context).apply {
            text = value
            textSize = sizeSp
            setTextColor(color)
            if (bold) setTypeface(typeface, Typeface.BOLD)
            includeFontPadding = false
            importantForAccessibility = View.IMPORTANT_FOR_ACCESSIBILITY_YES
        }

    fun button(context: Context, label: String, primary: Boolean = true, onClick: () -> Unit): TextView =
        TextView(context).apply {
            text = label
            textSize = 15f
            gravity = Gravity.CENTER
            minHeight = context.dp(48)
            setTypeface(typeface, Typeface.BOLD)
            setPadding(context.dp(16), context.dp(14), context.dp(16), context.dp(14))
            setTextColor(ANTHRACITE)
            background = shape(context, if (primary) TURQUOISE else SURFACE, 16f, if (primary) TURQUOISE else BORDER)
            isClickable = true
            isFocusable = true
            contentDescription = label
            setOnClickListener { onClick() }
        }

    fun dangerButton(context: Context, label: String, onClick: () -> Unit): TextView =
        button(context, label, false, onClick).apply {
            setTextColor(ERROR)
            background = shape(context, SURFACE, 16f, ERROR)
        }

    fun input(context: Context, hintText: String, password: Boolean = false, singleLine: Boolean = true): EditText =
        EditText(context).apply {
            hint = hintText
            textSize = 15f
            setTextColor(TEXT)
            setHintTextColor(Color.parseColor("#929CA0"))
            setPadding(context.dp(14), context.dp(12), context.dp(14), context.dp(12))
            background = shape(context, SURFACE, 14f, BORDER)
            isSingleLine = singleLine
            minHeight = context.dp(48)
            if (!singleLine) minLines = 4
            if (password) inputType = android.text.InputType.TYPE_CLASS_TEXT or android.text.InputType.TYPE_TEXT_VARIATION_PASSWORD
        }

    fun card(context: Context, dark: Boolean = false): LinearLayout = LinearLayout(context).apply {
        orientation = LinearLayout.VERTICAL
        setPadding(context.dp(18), context.dp(18), context.dp(18), context.dp(18))
        background = if (dark) gradient(context) else shape(context, SURFACE, 20f, BORDER)
        elevation = context.dp(if (dark) 3 else 1).toFloat()
    }

    fun statusCard(context: Context, success: Boolean, title: String, subtitle: String): LinearLayout =
        card(context).apply {
            background = shape(context, if (success) SUCCESS_SOFT else ERROR_SOFT, 18f, if (success) Color.parseColor("#BEE8D4") else Color.parseColor("#F3C8CE"))
            addView(text(context, title, 17f, if (success) SUCCESS else ERROR, true))
            addView(text(context, subtitle, 13f, MUTED))
        }

    fun divider(context: Context): View = View(context).apply { setBackgroundColor(BORDER) }
}

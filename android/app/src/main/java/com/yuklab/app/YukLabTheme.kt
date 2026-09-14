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
    val TURQUOISE_DARK: Int = Color.parseColor("#008D92")
    val ANTHRACITE: Int = Color.parseColor("#1D252C")
    val ANTHRACITE_DEEP: Int = Color.parseColor("#11171C")
    val BACKGROUND: Int = Color.parseColor("#F4F7F8")
    val SURFACE: Int = Color.WHITE
    val SURFACE_ALT: Int = Color.parseColor("#EAF0F1")
    val TEXT: Int = Color.parseColor("#172126")
    val MUTED: Int = Color.parseColor("#68777E")
    val BORDER: Int = Color.parseColor("#DCE5E7")
    val SUCCESS: Int = Color.parseColor("#1B9C68")
    val WARNING: Int = Color.parseColor("#D78419")

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

    fun text(
        context: Context,
        value: String,
        sizeSp: Float = 15f,
        color: Int = TEXT,
        bold: Boolean = false
    ): TextView = TextView(context).apply {
        text = value
        textSize = sizeSp
        setTextColor(color)
        if (bold) setTypeface(typeface, Typeface.BOLD)
        includeFontPadding = false
    }

    fun button(
        context: Context,
        label: String,
        primary: Boolean = true,
        onClick: () -> Unit
    ): TextView = TextView(context).apply {
        text = label
        textSize = 15f
        gravity = Gravity.CENTER
        setTypeface(typeface, Typeface.BOLD)
        setPadding(context.dp(16), context.dp(14), context.dp(16), context.dp(14))
        setTextColor(if (primary) Color.WHITE else ANTHRACITE)
        background = shape(
            context,
            if (primary) TURQUOISE else SURFACE,
            16f,
            if (primary) TURQUOISE else BORDER
        )
        isClickable = true
        isFocusable = true
        setOnClickListener { onClick() }
    }

    fun input(context: Context, hintText: String, password: Boolean = false): EditText = EditText(context).apply {
        hint = hintText
        textSize = 15f
        setTextColor(TEXT)
        setHintTextColor(Color.parseColor("#94A0A6"))
        setPadding(context.dp(14), context.dp(12), context.dp(14), context.dp(12))
        background = shape(context, SURFACE, 14f, BORDER)
        isSingleLine = true
        if (password) inputType = android.text.InputType.TYPE_CLASS_TEXT or android.text.InputType.TYPE_TEXT_VARIATION_PASSWORD
    }

    fun card(context: Context, dark: Boolean = false): LinearLayout = LinearLayout(context).apply {
        orientation = LinearLayout.VERTICAL
        setPadding(context.dp(18), context.dp(18), context.dp(18), context.dp(18))
        background = if (dark) gradient(context) else shape(context, SURFACE, 20f, BORDER)
        elevation = context.dp(2).toFloat()
    }

    fun divider(context: Context): View = View(context).apply {
        setBackgroundColor(BORDER)
    }
}

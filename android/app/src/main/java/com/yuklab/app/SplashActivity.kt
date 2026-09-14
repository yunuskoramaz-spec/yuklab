package com.yuklab.app

import android.app.Activity
import android.content.Intent
import android.graphics.Color
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.view.Gravity
import android.widget.FrameLayout
import android.widget.ImageView

class SplashActivity : Activity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        window.statusBarColor = Color.WHITE
        window.navigationBarColor = Color.WHITE
        if (android.os.Build.VERSION.SDK_INT >= 23) {
            window.decorView.systemUiVisibility = android.view.View.SYSTEM_UI_FLAG_LIGHT_STATUS_BAR
        }

        val root = FrameLayout(this).apply { setBackgroundColor(Color.WHITE) }
        val logo = ImageView(this).apply {
            setImageResource(R.drawable.logo_full)
            scaleType = ImageView.ScaleType.CENTER_INSIDE
            adjustViewBounds = true
            contentDescription = "YükLab Global Smart Logistics Network"
            setPadding(dp(28), dp(28), dp(28), dp(28))
        }
        root.addView(
            logo,
            FrameLayout.LayoutParams(FrameLayout.LayoutParams.MATCH_PARENT, dp(360)).apply {
                gravity = Gravity.CENTER
                leftMargin = dp(20)
                rightMargin = dp(20)
            },
        )
        setContentView(root)

        Handler(Looper.getMainLooper()).postDelayed({
            startActivity(Intent(this, MainActivity::class.java))
            finish()
        }, 650)
    }

    private fun dp(value: Int) = (value * resources.displayMetrics.density).toInt()
}

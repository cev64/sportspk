package com.personal.sportspk

import android.app.Activity
import android.os.Bundle
import android.view.Gravity
import android.widget.TextView

/** The app is just the widget; this screen only explains how to add it and kicks a refresh. */
class MainActivity : Activity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        RefreshWorker.schedule(this)
        RefreshWorker.scheduleSoon(this)
        setContentView(TextView(this).apply {
            text = getString(R.string.add_widget_hint)
            textSize = 18f
            gravity = Gravity.CENTER
            val pad = (32 * resources.displayMetrics.density).toInt()
            setPadding(pad, pad, pad, pad)
        })
    }
}

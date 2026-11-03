package com.vinstall.alwiz.ui

import android.annotation.SuppressLint
import android.content.Context
import android.util.AttributeSet
import android.view.View
import androidx.appcompat.widget.AppCompatButton
import com.google.android.material.button.MaterialButton
import com.google.android.material.theme.MaterialComponentsViewInflater

@SuppressLint("RestrictedApi")
class MarqueeViewInflater : MaterialComponentsViewInflater() {

    override fun createButton(context: Context, attrs: AttributeSet): AppCompatButton =
        super.createButton(context, attrs).also { it.enableMarquee() }

    override fun createView(context: Context, name: String, attrs: AttributeSet): View? = when (name) {
        "com.google.android.material.button.MaterialButton" ->
            MaterialButton(context, attrs).also { it.enableMarquee() }

        else -> super.createView(context, name, attrs)
    }
}

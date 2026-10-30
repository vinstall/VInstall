package com.vinstall.alwiz.appmanager

import android.content.Context
import android.graphics.drawable.Drawable
import android.util.LruCache
import android.widget.ImageView
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

class AppIconLoader(context: Context, private val scope: CoroutineScope) {

    private val packageManager = context.applicationContext.packageManager
    private val cache = LruCache<String, Drawable.ConstantState>(160)

    fun load(packageName: String, target: ImageView) {
        target.tag = packageName
        val cached = cache.get(packageName)
        if (cached != null) {
            target.setImageDrawable(cached.newDrawable(target.resources))
            return
        }
        target.setImageResource(android.R.drawable.sym_def_app_icon)
        scope.launch {
            val drawable = withContext(Dispatchers.IO) {
                if (target.tag != packageName) {
                    null
                } else {
                    try {
                        packageManager.getApplicationIcon(packageName)
                    } catch (_: Exception) {
                        null
                    }
                }
            } ?: return@launch
            drawable.constantState?.let { cache.put(packageName, it) }
            if (target.tag == packageName) target.setImageDrawable(drawable)
        }
    }
}

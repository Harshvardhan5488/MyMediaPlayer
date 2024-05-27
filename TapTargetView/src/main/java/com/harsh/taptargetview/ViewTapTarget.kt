package com.harsh.taptargetview

import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Rect
import android.graphics.drawable.BitmapDrawable
import android.view.View


internal open class ViewTapTarget(view: View?, title: CharSequence?, description: CharSequence?): TapTarget(title, description) {
    val view: View

    init {
        requireNotNull(view) { "Given null view to target" }
        this.view = view
    }

    override fun onReady(runnable: Runnable) {
        ViewUtil.onLaidOut(view) { // Cache bounds
            val location = IntArray(2)
            view.getLocationOnScreen(location)
            bounds = Rect(location[0], location[1],
                location[0] + view.width, location[1] + view.height)
            if (icon == null && view.width > 0 && view.height > 0) {
                val viewBitmap = Bitmap.createBitmap(view.width, view.height, Bitmap.Config.ARGB_8888)
                val canvas = Canvas(viewBitmap)
                view.draw(canvas)
                icon = BitmapDrawable(view.context.resources, viewBitmap)
                (icon as BitmapDrawable).setBounds(0, 0, (icon as BitmapDrawable).intrinsicWidth, (icon as BitmapDrawable).intrinsicHeight)
            }
            runnable.run()
        }
    }
}


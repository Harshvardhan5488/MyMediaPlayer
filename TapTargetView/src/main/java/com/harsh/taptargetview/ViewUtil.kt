package com.harsh.taptargetview

import android.os.Build
import android.view.View
import android.view.ViewManager
import android.view.ViewTreeObserver
import android.view.ViewTreeObserver.OnGlobalLayoutListener
import androidx.core.view.ViewCompat


internal object ViewUtil {
    /** Returns whether or not the view has been laid out  */
    private fun isLaidOut(view: View): Boolean {
        return ViewCompat.isLaidOut(view) && view.width > 0 && view.height > 0
    }

    /** Executes the given [Runnable] when the view is laid out  */
    fun onLaidOut(view: View, runnable: Runnable) {
        if (isLaidOut(view)) {
            runnable.run()
            return
        }
        val observer = view.getViewTreeObserver()
        observer.addOnGlobalLayoutListener(object: OnGlobalLayoutListener {
            override fun onGlobalLayout() {
                val trueObserver: ViewTreeObserver
                trueObserver = if (observer.isAlive) {
                    observer
                } else {
                    view.getViewTreeObserver()
                }
                removeOnGlobalLayoutListener(trueObserver, this)
                runnable.run()
            }
        })
    }

    @Suppress("deprecation")
    fun removeOnGlobalLayoutListener(observer: ViewTreeObserver,
        listener: OnGlobalLayoutListener?) {
        if (Build.VERSION.SDK_INT >= 16) {
            observer.removeOnGlobalLayoutListener(listener)
        } else {
            observer.removeGlobalOnLayoutListener(listener)
        }
    }

    fun removeView(parent: ViewManager?, child: View?) {
        if (parent == null || child == null) {
            return
        }
        try {
            parent.removeView(child)
        } catch (ignored: Exception) {
            // This catch exists for modified versions of Android that have a buggy ViewGroup
            // implementation. See b.android.com/77639, #121 and #49
        }
    }
}


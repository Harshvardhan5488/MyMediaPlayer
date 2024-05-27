package com.harsh.taptargetview

import android.annotation.TargetApi
import android.graphics.drawable.Drawable
import android.os.Build
import android.text.TextUtils
import android.view.View
import android.view.ViewGroup
import android.widget.ImageButton
import android.widget.ImageView
import androidx.annotation.IdRes
import androidx.appcompat.widget.Toolbar
import com.harsh.taptargetview.ReflectUtil.getPrivateField
import java.util.Stack


internal class ToolbarTapTarget: ViewTapTarget {
    constructor(toolbar: Toolbar, @IdRes menuItemId: Int,
        title: CharSequence?, description: CharSequence?): super(toolbar.findViewById<View>(menuItemId), title, description)

    constructor(toolbar: android.widget.Toolbar, @IdRes menuItemId: Int,
        title: CharSequence?, description: CharSequence?): super(toolbar.findViewById<View>(menuItemId), title, description)

    constructor(toolbar: Toolbar, findNavView: Boolean,
        title: CharSequence?, description: CharSequence?): super(if (findNavView) findNavView(toolbar) else findOverflowView(toolbar), title, description)

    constructor(toolbar: android.widget.Toolbar, findNavView: Boolean,
        title: CharSequence?, description: CharSequence?): super(if (findNavView) findNavView(toolbar) else findOverflowView(toolbar), title, description)

    private interface ToolbarProxy {
        var navigationContentDescription: CharSequence?

        fun findViewsWithText(out: ArrayList<View>?, toFind: CharSequence?, flags: Int)
        val navigationIcon: Drawable?
        val overflowIcon: Drawable?
        val childCount: Int

        fun getChildAt(position: Int): View
        fun internalToolbar(): Any
    }

    private class SupportToolbarProxy internal constructor(private val toolbar: Toolbar): ToolbarProxy {

        override var navigationContentDescription: CharSequence?
            get() = toolbar.navigationContentDescription
            set(description) {
                toolbar.setNavigationContentDescription(description)
            }

        override fun findViewsWithText(out: ArrayList<View>?, toFind: CharSequence?, flags: Int) {
            toolbar.findViewsWithText(out, toFind, flags)
        }

        override val navigationIcon: Drawable?
            get() = toolbar.navigationIcon
        override val overflowIcon: Drawable?
            get() = toolbar.getOverflowIcon()
        override val childCount: Int
            get() = toolbar.childCount

        override fun getChildAt(position: Int): View {
            return toolbar.getChildAt(position)
        }

        override fun internalToolbar(): Any {
            return toolbar
        }
    }


    private class StandardToolbarProxy internal constructor(private val toolbar: android.widget.Toolbar): ToolbarProxy {

        override var navigationContentDescription: CharSequence?
            get() = toolbar.navigationContentDescription
            set(description) {
                toolbar.setNavigationContentDescription(description)
            }

        override fun findViewsWithText(out: ArrayList<View>?, toFind: CharSequence?, flags: Int) {
            toolbar.findViewsWithText(out, toFind, flags)
        }

        override val navigationIcon: Drawable?
            get() = toolbar.navigationIcon
        override val overflowIcon: Drawable?
            get() = if (Build.VERSION.SDK_INT >= 23) {
                toolbar.getOverflowIcon()
            } else null
        override val childCount: Int
            get() = toolbar.childCount

        override fun getChildAt(position: Int): View {
            return toolbar.getChildAt(position)
        }

        override fun internalToolbar(): Any {
            return toolbar
        }
    }

    companion object {
        private fun proxyOf(instance: Any?): ToolbarProxy {
            requireNotNull(instance) { "Given null instance" }
            if (instance is Toolbar) {
                return SupportToolbarProxy(instance)
            } else if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.LOLLIPOP
                && instance is android.widget.Toolbar) {
                return StandardToolbarProxy(instance)
            }
            throw IllegalStateException("Couldn't provide proper toolbar proxy instance")
        }

        private fun findNavView(instance: Any): View {
            val toolbar = proxyOf(instance)

            // First we try to find the view via its content description
            val currentDescription = toolbar.navigationContentDescription!!
            val hadContentDescription = !TextUtils.isEmpty(currentDescription)
            val sentinel = if (hadContentDescription) currentDescription else "taptarget-findme"
            toolbar.navigationContentDescription = sentinel
            val possibleViews = ArrayList<View>(1)
            toolbar.findViewsWithText(possibleViews, sentinel, View.FIND_VIEWS_WITH_CONTENT_DESCRIPTION)
            if (!hadContentDescription) {
                toolbar.navigationContentDescription = null
            }
            if (possibleViews.size > 0) {
                return possibleViews[0]
            }

            // If that doesn't work, we try to grab it via matching its drawable
            val navigationIcon = toolbar.navigationIcon ?: throw IllegalStateException("Toolbar does not have a navigation view set!")
            val size = toolbar.childCount
            for (i in 0 until size) {
                val child = toolbar.getChildAt(i)
                if (child is ImageButton) {
                    val childDrawable = child.getDrawable()
                    if (childDrawable === navigationIcon) {
                        return child
                    }
                }
            }
            throw IllegalStateException("Could not find navigation view for Toolbar!")
        }

        private fun findOverflowView(instance: Any): View {
            val toolbar = proxyOf(instance)

            // First we try to find the overflow menu view via drawable matching
            val overflowDrawable = toolbar.overflowIcon
            if (overflowDrawable != null) {
                val parents = Stack<ViewGroup>()
                parents.push(toolbar.internalToolbar() as ViewGroup)
                while (!parents.empty()) {
                    val parent = parents.pop()
                    val size = parent.childCount
                    for (i in 0 until size) {
                        val child = parent.getChildAt(i)
                        if (child is ViewGroup) {
                            parents.push(child)
                            continue
                        }
                        if (child is ImageView) {
                            val childDrawable = child.getDrawable()
                            if (childDrawable === overflowDrawable) {
                                return child
                            }
                        }
                    }
                }
            }

            // If that doesn't work, we fall-back to our last resort solution: Reflection
            // Toolbars contain an "ActionMenuView" which in turn contains an "ActionMenuPresenter".
            // The "ActionMenuPresenter" then holds a reference to an "OverflowMenuButton" which is the
            // desired target
            return try {
                val actionMenuView = getPrivateField(toolbar.internalToolbar(), "mMenuView")
                val actionMenuPresenter = getPrivateField(actionMenuView, "mPresenter")
                getPrivateField(actionMenuPresenter, "mOverflowButton") as View
            } catch (e: NoSuchFieldException) {
                throw IllegalStateException("Could not find overflow view for Toolbar!", e)
            } catch (e: IllegalAccessException) {
                throw IllegalStateException("Unable to access overflow view for Toolbar!", e)
            }
        }
    }
}
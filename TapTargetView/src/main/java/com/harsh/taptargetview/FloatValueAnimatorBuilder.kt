package com.harsh.taptargetview

import android.animation.Animator
import android.animation.AnimatorListenerAdapter
import android.animation.TimeInterpolator
import android.animation.ValueAnimator

/**
 * A small wrapper around [ValueAnimator] to provide a builder-like interface
 */
class FloatValueAnimatorBuilder(reverse: Boolean = false) {
    private var animator: ValueAnimator? = null
    var endListener: EndListener? = null

    interface UpdateListener {
        fun onUpdate(lerpTime: Float)
    }

    interface EndListener {
        fun onEnd()
    }

    init {
        if (reverse) {
            animator = ValueAnimator.ofFloat(1.0f, 0.0f)
        } else {
            animator = ValueAnimator.ofFloat(0.0f, 1.0f)
        }
    }

    fun delayBy(millis: Long): FloatValueAnimatorBuilder {
        animator!!.setStartDelay(millis)
        return this
    }

    fun duration(millis: Long): FloatValueAnimatorBuilder {
        animator!!.setDuration(millis)
        return this
    }

    fun interpolator(lerper: TimeInterpolator?): FloatValueAnimatorBuilder {
        animator!!.interpolator = lerper
        return this
    }

    fun repeat(times: Int): FloatValueAnimatorBuilder {
        animator!!.repeatCount = times
        return this
    }

    fun onUpdate(listener: Any): FloatValueAnimatorBuilder {
        animator!!.addUpdateListener { animation -> listener.onUpdate(animation.getAnimatedValue() as Float) }
        return this
    }

    fun onEnd(listener: EndListener?): FloatValueAnimatorBuilder {
        endListener = listener
        return this
    }

    fun build(): ValueAnimator? {
        if (endListener != null) {
            animator!!.addListener(object: AnimatorListenerAdapter() {
                override fun onAnimationEnd(animation: Animator) {
                    endListener!!.onEnd()
                }
            })
        }
        return animator
    }
}


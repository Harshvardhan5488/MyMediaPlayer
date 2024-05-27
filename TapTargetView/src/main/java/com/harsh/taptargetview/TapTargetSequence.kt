package com.harsh.taptargetview

import android.app.Activity
import android.app.Dialog
import androidx.annotation.UiThread
import java.util.Collections
import java.util.LinkedList
import java.util.Queue

/**
 * Displays a sequence of [TapTargetView]s.
 *
 *
 * Internally, a FIFO queue is held to dictate which [TapTarget] will be shown.
 */
class TapTargetSequence {
    private val activity: Activity?
    private val dialog: Dialog?
    private val targets: Queue<TapTarget?>
    private var active = false
    private var currentView: TapTargetView? = null
    var listener: Listener? = null
    var considerOuterCircleCanceled = false
    var continueOnCancel = false

    interface Listener {
        /** Called when there are no more tap targets to display  */
        fun onSequenceFinish()

        /**
         * Called when moving onto the next tap target.
         * @param lastTarget The last displayed target
         * @param targetClicked Whether the last displayed target was clicked (this will always be true
         * unless you have set [.continueOnCancel] and the user
         * clicks outside of the target
         */
        fun onSequenceStep(lastTarget: TapTarget?, targetClicked: Boolean)

        /**
         * Called when the user taps outside of the current target, the target is cancelable, and
         * [.continueOnCancel] is not set.
         * @param lastTarget The last displayed target
         */
        fun onSequenceCanceled(lastTarget: TapTarget?)
    }

    constructor(activity: Activity?) {
        requireNotNull(activity) { "Activity is null" }
        this.activity = activity
        dialog = null
        targets = LinkedList()
    }

    constructor(dialog: Dialog?) {
        requireNotNull(dialog) { "Given null Dialog" }
        this.dialog = dialog
        activity = null
        targets = LinkedList()
    }

    /** Adds the given targets, in order, to the pending queue of [TapTarget]s  */
    fun targets(targets: List<TapTarget?>?): TapTargetSequence {
        this.targets.addAll(targets!!)
        return this
    }

    /** Adds the given targets, in order, to the pending queue of [TapTarget]s  */
    fun targets(vararg targets: TapTarget?): TapTargetSequence {
        Collections.addAll(this.targets, *targets)
        return this
    }

    /** Adds the given target to the pending queue of [TapTarget]s  */
    fun target(target: TapTarget?): TapTargetSequence {
        targets.add(target)
        return this
    }

    /** Whether or not to continue the sequence when a [TapTarget] is canceled  */
    fun continueOnCancel(status: Boolean): TapTargetSequence {
        continueOnCancel = status
        return this
    }

    /** Whether or not to consider taps on the outer circle as a cancellation  */
    fun considerOuterCircleCanceled(status: Boolean): TapTargetSequence {
        considerOuterCircleCanceled = status
        return this
    }

    /** Specify the listener for this sequence  */
    fun listener(listener: Listener?): TapTargetSequence {
        this.listener = listener
        return this
    }

    /** Immediately starts the sequence and displays the first target from the queue  */
    @UiThread
    fun start() {
        if (targets.isEmpty() || active) {
            return
        }
        active = true
        showNext()
    }

    /** Immediately starts the sequence from the given targetId's position in the queue  */
    fun startWith(targetId: Int) {
        if (active) {
            return
        }
        while (targets.peek() != null && targets.peek()!!.id() != targetId) {
            targets.poll()
        }
        val peekedTarget = targets.peek()
        check(!(peekedTarget == null || peekedTarget.id() != targetId)) { "Given target $targetId not in sequence" }
        start()
    }

    /** Immediately starts the sequence at the specified zero-based index in the queue  */
    fun startAt(index: Int) {
        if (active) {
            return
        }
        require(!(index < 0 || index >= targets.size)) { "Given invalid index $index" }
        val expectedSize = targets.size - index
        while (targets.peek() != null && targets.size != expectedSize) {
            targets.poll()
        }
        check(targets.size == expectedSize) { "Given index $index not in sequence" }
        start()
    }

    /**
     * Cancels the sequence, if the current target is cancelable.
     * When the sequence is canceled, the current target is dismissed and the remaining targets are
     * removed from the sequence.
     * @return whether the sequence was canceled or not
     */
    @UiThread
    fun cancel(): Boolean {
        if (!active || currentView == null || !currentView!!.cancelable) {
            return false
        }
        currentView!!.dismiss(false)
        active = false
        targets.clear()
        if (listener != null) {
            listener!!.onSequenceCanceled(currentView!!.target)
        }
        return true
    }

    fun showNext() {
        try {
            val tapTarget = targets.remove()
            currentView = if (activity != null) {
                TapTargetView.showFor(activity, tapTarget, tapTargetListener)
            } else {
                TapTargetView.showFor(dialog, tapTarget, tapTargetListener)
            }
        } catch (e: NoSuchElementException) {
            currentView = null
            // No more targets
            if (listener != null) {
                listener!!.onSequenceFinish()
            }
        }
    }

    private val tapTargetListener: TapTargetView.Listener = object: TapTargetView.Listener() {
        override fun onTargetClick(view: TapTargetView) {
            super.onTargetClick(view)
            if (listener != null) {
                listener!!.onSequenceStep(view.target, true)
            }
            showNext()
        }

        override fun onOuterCircleClick(view: TapTargetView) {
            if (considerOuterCircleCanceled) {
                onTargetCancel(view)
            }
        }

        override fun onTargetCancel(view: TapTargetView) {
            super.onTargetCancel(view)
            if (continueOnCancel) {
                if (listener != null) {
                    listener!!.onSequenceStep(view.target, false)
                }
                showNext()
            } else {
                if (listener != null) {
                    listener!!.onSequenceCanceled(view.target)
                }
            }
        }
    }
}


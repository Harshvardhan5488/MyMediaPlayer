package com.harsh.taptargetview

import android.animation.ValueAnimator
import android.annotation.SuppressLint
import android.annotation.TargetApi
import android.app.Activity
import android.app.Dialog
import android.content.Context
import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Outline
import android.graphics.Paint
import android.graphics.Path
import android.graphics.PixelFormat
import android.graphics.PorterDuff
import android.graphics.PorterDuffColorFilter
import android.graphics.PorterDuffXfermode
import android.graphics.Rect
import android.graphics.Region
import android.graphics.Typeface
import android.os.Build
import android.text.DynamicLayout
import android.text.Layout
import android.text.SpannableStringBuilder
import android.text.StaticLayout
import android.text.TextPaint
import android.util.DisplayMetrics
import android.view.Gravity
import android.view.KeyEvent
import android.view.MotionEvent
import android.view.View
import android.view.View.OnClickListener
import android.view.View.OnLongClickListener
import android.view.ViewGroup
import android.view.ViewManager
import android.view.ViewOutlineProvider
import android.view.ViewTreeObserver.OnGlobalLayoutListener
import android.view.WindowManager
import android.view.animation.AccelerateDecelerateInterpolator
import kotlin.math.max
import kotlin.math.min
import kotlin.math.pow
import kotlin.math.sqrt

/**
 * TapTargetView implements a feature discovery paradigm following Google's Material Design
 * guidelines.
 *
 *
 * This class should not be instantiated directly. Instead, please use the
 * [.showFor] static factory method instead.
 *
 *
 * More information can be found here:
 * https://material.google.com/growth-communications/feature-discovery.html#feature-discovery-design
 */
@SuppressLint("ViewConstructor")
class TapTargetView(context: Context,
    parent: ViewManager,
    boundingParent: ViewGroup?,
    target: TapTarget?,
    userListener: Listener?): View(context) {
    private var isDismissed = false
    private var isDismissing = false
    private var isInteractable = true
    val TARGET_PADDING: Int
    val TARGET_RADIUS: Int
    val TARGET_PULSE_RADIUS: Int
    val TEXT_PADDING: Int
    val TEXT_SPACING: Int
    val TEXT_MAX_WIDTH: Int
    val TEXT_POSITIONING_BIAS: Int
    val CIRCLE_PADDING: Int
    val GUTTER_DIM: Int
    val SHADOW_DIM: Int
    val SHADOW_JITTER_DIM: Int
    val boundingParent: ViewGroup?
    val parent: ViewManager
    val target: TapTarget
    val targetBounds: Rect
    val titlePaint: TextPaint
    val descriptionPaint: TextPaint
    val outerCirclePaint: Paint
    val outerCircleShadowPaint: Paint
    val targetCirclePaint: Paint
    val targetCirclePulsePaint: Paint
    var title: CharSequence
    var titleLayout: StaticLayout? = null
    var description: CharSequence?
    var descriptionLayout: StaticLayout? = null
    var isDark = false
    var debug = false
    var shouldTintTarget = false
    var shouldDrawShadow = false
    var cancelable = false
    var visible = false

    // Debug related variables
    var debugStringBuilder: SpannableStringBuilder? = null
    var debugLayout: DynamicLayout? = null
    var debugTextPaint: TextPaint? = null
    var debugPaint: Paint? = null

    // Drawing properties
    var drawingBounds: Rect = Rect()
    var textBounds: Rect? = null
    var outerCirclePath: Path = Path()
    var outerCircleRadius = 0f
    var calculatedOuterCircleRadius = 0
    var outerCircleCenter: IntArray? = null
    var outerCircleAlpha = 0
    var targetCirclePulseRadius = 0f
    var targetCirclePulseAlpha = 0
    var targetCircleRadius = 0f
    var targetCircleAlpha = 0
    var textAlpha = 0
    var dimColor = 0
    var lastTouchX = 0f
    var lastTouchY = 0f
    var topBoundary = 0
    var bottomBoundary = 0
    var tintedTarget: Bitmap? = null
    var listener: Listener?
    var outlineProvider: ViewOutlineProvider? = null

    class Listener {
        /** Signals that the user has clicked inside of the target  */
        fun onTargetClick(view: TapTargetView) {
            view.dismiss(true)
        }

        /** Signals that the user has long clicked inside of the target  */
        fun onTargetLongClick(view: TapTargetView) {
            onTargetClick(view)
        }

        /** If cancelable, signals that the user has clicked outside of the outer circle  */
        fun onTargetCancel(view: TapTargetView) {
            view.dismiss(false)
        }

        /** Signals that the user clicked on the outer circle portion of the tap target  */
        fun onOuterCircleClick(view: TapTargetView?) {
            // no-op as default
        }

        /**
         * Signals that the tap target has been dismissed
         * @param userInitiated Whether the user caused this action
         */
        fun onTargetDismissed(view: TapTargetView?, userInitiated: Boolean) {}
    }

    val expandContractUpdateListener: FloatValueAnimatorBuilder.UpdateListener = object: FloatValueAnimatorBuilder.UpdateListener {
        override fun onUpdate(lerpTime: Float) {
            val newOuterCircleRadius = calculatedOuterCircleRadius * lerpTime
            val expanding = newOuterCircleRadius > outerCircleRadius
            if (!expanding) {
                // When contracting we need to invalidate the old drawing bounds. Otherwise
                // you will see artifacts as the circle gets smaller
                calculateDrawingBounds()
            }
            val targetAlpha = target!!.outerCircleAlpha * 255
            outerCircleRadius = newOuterCircleRadius
            outerCircleAlpha = min(targetAlpha.toDouble(), (lerpTime * 1.5f * targetAlpha).toDouble()).toInt()
            outerCirclePath.reset()
            outerCirclePath.addCircle(outerCircleCenter!![0].toFloat(), outerCircleCenter!![1].toFloat(), outerCircleRadius, Path.Direction.CW)
            targetCircleAlpha = min(255.0, (lerpTime * 1.5f * 255.0f).toDouble()).toInt()
            if (expanding) {
                targetCircleRadius = (TARGET_RADIUS * min(1.0, (lerpTime * 1.5f).toDouble())).toFloat()
            } else {
                targetCircleRadius = TARGET_RADIUS * lerpTime
                targetCirclePulseRadius *= lerpTime
            }
            textAlpha = (delayedLerp(lerpTime, 0.7f) * 255).toInt()
            if (expanding) {
                calculateDrawingBounds()
            }
            invalidateViewAndOutline(drawingBounds)
        }
    }
    val expandAnimation = FloatValueAnimatorBuilder()
            .duration(250)
            .delayBy(250)
            .interpolator(AccelerateDecelerateInterpolator())
            .onUpdate(object: FloatValueAnimatorBuilder.UpdateListener {
                override fun onUpdate(lerpTime: Float) {
                    expandContractUpdateListener.onUpdate(lerpTime)
                }
            })
            .onEnd(object: FloatValueAnimatorBuilder.EndListener {
                override fun onEnd() {
                    pulseAnimation!!.start()
                    isInteractable = true
                }
            })
            .build()
    val pulseAnimation = FloatValueAnimatorBuilder()
            .duration(1000)
            .repeat(ValueAnimator.INFINITE)
            .interpolator(AccelerateDecelerateInterpolator())
            .onUpdate(object: FloatValueAnimatorBuilder.UpdateListener() {
                override fun onUpdate(lerpTime: Float) {
                    val pulseLerp = delayedLerp(lerpTime, 0.5f)
                    targetCirclePulseRadius = (1.0f + pulseLerp) * TARGET_RADIUS
                    targetCirclePulseAlpha = ((1.0f - pulseLerp) * 255).toInt()
                    targetCircleRadius = TARGET_RADIUS + halfwayLerp(lerpTime) * TARGET_PULSE_RADIUS
                    if (outerCircleRadius != calculatedOuterCircleRadius.toFloat()) {
                        outerCircleRadius = calculatedOuterCircleRadius.toFloat()
                    }
                    calculateDrawingBounds()
                    invalidateViewAndOutline(drawingBounds)
                }
            })
            .build()
    val dismissAnimation = FloatValueAnimatorBuilder(true)
            .duration(250)
            .interpolator(AccelerateDecelerateInterpolator())
            .onUpdate(object: FloatValueAnimatorBuilder.UpdateListener() {
                override fun onUpdate(lerpTime: Float) {
                    expandContractUpdateListener.onUpdate(lerpTime)
                }
            })
            .onEnd(object: FloatValueAnimatorBuilder.EndListener() {
                override fun onEnd() {
                    finishDismiss(true)
                }
            })
            .build()
    private val dismissConfirmAnimation = FloatValueAnimatorBuilder()
            .duration(250)
            .interpolator(AccelerateDecelerateInterpolator())
            .onUpdate(object: FloatValueAnimatorBuilder.UpdateListener() {
                override fun onUpdate(lerpTime: Float) {
                    val spedUpLerp = min(1.0, (lerpTime * 2.0f).toDouble()).toFloat()
                    outerCircleRadius = calculatedOuterCircleRadius * (1.0f + spedUpLerp * 0.2f)
                    outerCircleAlpha = ((1.0f - spedUpLerp) * target!!.outerCircleAlpha * 255.0f).toInt()
                    outerCirclePath.reset()
                    outerCirclePath.addCircle(outerCircleCenter!![0].toFloat(), outerCircleCenter!![1].toFloat(), outerCircleRadius, Path.Direction.CW)
                    targetCircleRadius = (1.0f - lerpTime) * TARGET_RADIUS
                    targetCircleAlpha = ((1.0f - lerpTime) * 255.0f).toInt()
                    targetCirclePulseRadius = (1.0f + lerpTime) * TARGET_RADIUS
                    targetCirclePulseAlpha = ((1.0f - lerpTime) * targetCirclePulseAlpha).toInt()
                    textAlpha = ((1.0f - spedUpLerp) * 255.0f).toInt()
                    calculateDrawingBounds()
                    invalidateViewAndOutline(drawingBounds)
                }
            })
            .onEnd(object: FloatValueAnimatorBuilder.EndListener {
                override fun onEnd() {
                    finishDismiss(true)
                }
            })
            .build()
    private val animators = arrayOf(expandAnimation, pulseAnimation, dismissConfirmAnimation, dismissAnimation)
    private val globalLayoutListener: OnGlobalLayoutListener

    /**
     * This constructor should only be used directly for very specific use cases not covered by
     * the static factory methods.
     *
     * @param context The host context
     * @param parent The parent that this TapTargetView will become a child of. This parent should
     * allow the largest possible area for this view to utilize
     * @param boundingParent Optional. Will be used to calculate boundaries if needed. For example,
     * if your view is added to the decor view of your Window, then you want
     * to adjust for system ui like the navigation bar or status bar, and so
     * you would pass in the content view (which doesn't include system ui)
     * here.
     * @param target The [TapTarget] to target
     * @param userListener Optional. The [Listener] instance for this view
     */
    init {
        requireNotNull(target) { "Target cannot be null" }
        this.target = target
        this.parent = parent
        this.boundingParent = boundingParent
        listener = userListener ?: Listener()
        title = target.title
        description = target.description
        TARGET_PADDING = UiUtil.dp(context, 20)
        CIRCLE_PADDING = UiUtil.dp(context, 40)
        TARGET_RADIUS = UiUtil.dp(context, target.targetRadius)
        TEXT_PADDING = UiUtil.dp(context, 40)
        TEXT_SPACING = UiUtil.dp(context, 8)
        TEXT_MAX_WIDTH = UiUtil.dp(context, 360)
        TEXT_POSITIONING_BIAS = UiUtil.dp(context, 20)
        GUTTER_DIM = UiUtil.dp(context, 88)
        SHADOW_DIM = UiUtil.dp(context, 8)
        SHADOW_JITTER_DIM = UiUtil.dp(context, 1)
        TARGET_PULSE_RADIUS = (0.1f * TARGET_RADIUS).toInt()
        outerCirclePath = Path()
        targetBounds = Rect()
        drawingBounds = Rect()
        titlePaint = TextPaint()
        titlePaint.textSize = target.titleTextSizePx(context).toFloat()
        titlePaint.setTypeface(Typeface.create("sans-serif-medium", Typeface.NORMAL))
        titlePaint.isAntiAlias = true
        descriptionPaint = TextPaint()
        descriptionPaint.textSize = target.descriptionTextSizePx(context).toFloat()
        descriptionPaint.setTypeface(Typeface.create(Typeface.SANS_SERIF, Typeface.NORMAL))
        descriptionPaint.isAntiAlias = true
        descriptionPaint.setAlpha((0.54f * 255.0f).toInt())
        outerCirclePaint = Paint()
        outerCirclePaint.isAntiAlias = true
        outerCirclePaint.setAlpha((target.outerCircleAlpha * 255.0f).toInt())
        outerCircleShadowPaint = Paint()
        outerCircleShadowPaint.isAntiAlias = true
        outerCircleShadowPaint.setAlpha(50)
        outerCircleShadowPaint.style = Paint.Style.STROKE
        outerCircleShadowPaint.strokeWidth = SHADOW_JITTER_DIM.toFloat()
        outerCircleShadowPaint.setColor(Color.BLACK)
        targetCirclePaint = Paint()
        targetCirclePaint.isAntiAlias = true
        targetCirclePulsePaint = Paint()
        targetCirclePulsePaint.isAntiAlias = true
        applyTargetOptions(context)
        val hasKitkat = Build.VERSION.SDK_INT >= Build.VERSION_CODES.KITKAT
        val translucentStatusBar: Boolean
        val translucentNavigationBar: Boolean
        val layoutNoLimits: Boolean
        if (context is Activity) {
            val flags = context.window.attributes.flags
            translucentStatusBar = hasKitkat && flags and WindowManager.LayoutParams.FLAG_TRANSLUCENT_STATUS != 0
            translucentNavigationBar = hasKitkat && flags and WindowManager.LayoutParams.FLAG_TRANSLUCENT_NAVIGATION != 0
            layoutNoLimits = flags and WindowManager.LayoutParams.FLAG_LAYOUT_NO_LIMITS != 0
        } else {
            translucentStatusBar = false
            translucentNavigationBar = false
            layoutNoLimits = false
        }
        globalLayoutListener = OnGlobalLayoutListener {
            if (isDismissing) {
                return@OnGlobalLayoutListener
            }
            updateTextLayouts()
            target.onReady(Runnable {
                val offset = IntArray(2)
                targetBounds.set(target.bounds())
                getLocationOnScreen(offset)
                targetBounds.offset(-offset[0], -offset[1])
                if (boundingParent != null) {
                    val windowManager = context.getSystemService(Context.WINDOW_SERVICE) as WindowManager
                    val displayMetrics = DisplayMetrics()
                    windowManager.defaultDisplay.getMetrics(displayMetrics)
                    val rect = Rect()
                    boundingParent.getWindowVisibleDisplayFrame(rect)
                    val parentLocation = IntArray(2)
                    boundingParent.getLocationInWindow(parentLocation)
                    if (translucentStatusBar) {
                        rect.top = parentLocation[1]
                    }
                    if (translucentNavigationBar) {
                        rect.bottom = parentLocation[1] + boundingParent.height
                    }

                    // We bound the boundaries to be within the screen's coordinates to
                    // handle the case where the flag FLAG_LAYOUT_NO_LIMITS is set
                    if (layoutNoLimits) {
                        topBoundary = max(0.0, rect.top.toDouble()).toInt()
                        bottomBoundary = min(rect.bottom.toDouble(), displayMetrics.heightPixels.toDouble()).toInt()
                    } else {
                        topBoundary = rect.top
                        bottomBoundary = rect.bottom
                    }
                }
                drawTintedTarget()
                requestFocus()
                calculateDimensions()
                startExpandAnimation()
            })
        }
        getViewTreeObserver().addOnGlobalLayoutListener(globalLayoutListener)
        setFocusableInTouchMode(true)
        isClickable = true
        setOnClickListener(OnClickListener {
            if (listener == null || outerCircleCenter == null || !isInteractable) return@OnClickListener
            val clickedInTarget = distance(targetBounds.centerX(), targetBounds.centerY(), lastTouchX.toInt(), lastTouchY.toInt()) <= targetCircleRadius
            val distanceToOuterCircleCenter = distance(outerCircleCenter!![0], outerCircleCenter!![1], lastTouchX.toInt(), lastTouchY.toInt())
            val clickedInsideOfOuterCircle = distanceToOuterCircleCenter <= outerCircleRadius
            if (clickedInTarget) {
                isInteractable = false
                listener!!.onTargetClick(this@TapTargetView)
            } else if (clickedInsideOfOuterCircle) {
                listener!!.onOuterCircleClick(this@TapTargetView)
            } else if (cancelable) {
                isInteractable = false
                listener!!.onTargetCancel(this@TapTargetView)
            }
        })
        setOnLongClickListener(OnLongClickListener {
            if (listener == null) return@OnLongClickListener false
            if (targetBounds.contains(lastTouchX.toInt(), lastTouchY.toInt())) {
                listener!!.onTargetLongClick(this@TapTargetView)
                return@OnLongClickListener true
            }
            false
        })
    }

    private fun startExpandAnimation() {
        if (!visible) {
            isInteractable = false
            expandAnimation!!.start()
            visible = true
        }
    }

    protected fun applyTargetOptions(context: Context) {
        shouldTintTarget = !target.transparentTarget && target.tintTarget
        shouldDrawShadow = target.drawShadow
        cancelable = target.cancelable

        // We can't clip out portions of a view outline, so if the user specified a transparent
        // target, we need to fallback to drawing a jittered shadow approximation
        if (shouldDrawShadow && Build.VERSION.SDK_INT >= 21 && !target.transparentTarget) {
            outlineProvider = object: ViewOutlineProvider() {
                @TargetApi(Build.VERSION_CODES.LOLLIPOP)
                override fun getOutline(view: View, outline: Outline) {
                    if (outerCircleCenter == null) return
                    outline.setOval((outerCircleCenter!![0] - outerCircleRadius).toInt(), (outerCircleCenter!![1] - outerCircleRadius).toInt(), (outerCircleCenter!![0] + outerCircleRadius).toInt(), (outerCircleCenter!![1] + outerCircleRadius).toInt())
                    outline.alpha = outerCircleAlpha / 255.0f
                    if (Build.VERSION.SDK_INT >= 22) {
                        outline.offset(0, SHADOW_DIM)
                    }
                }
            }
            setOutlineProvider(outlineProvider)
            elevation = SHADOW_DIM.toFloat()
        }
        if (shouldDrawShadow && outlineProvider == null && Build.VERSION.SDK_INT < 18) {
            setLayerType(LAYER_TYPE_SOFTWARE, null)
        } else {
            setLayerType(LAYER_TYPE_HARDWARE, null)
        }
        val theme = context.theme
        isDark = UiUtil.themeIntAttr(context, "isLightTheme") === 0
        val outerCircleColor = target.outerCircleColorInt(context)
        if (outerCircleColor != null) {
            outerCirclePaint.setColor(outerCircleColor)
        } else if (theme != null) {
            outerCirclePaint.setColor(UiUtil.themeIntAttr(context, "colorPrimary"))
        } else {
            outerCirclePaint.setColor(Color.WHITE)
        }
        val targetCircleColor = target.targetCircleColorInt(context)
        if (targetCircleColor != null) {
            targetCirclePaint.setColor(targetCircleColor)
        } else {
            targetCirclePaint.setColor(if (isDark) Color.BLACK else Color.WHITE)
        }
        if (target.transparentTarget) {
            targetCirclePaint.setXfermode(PorterDuffXfermode(PorterDuff.Mode.CLEAR))
        }
        targetCirclePulsePaint.setColor(targetCirclePaint.color)
        val targetDimColor = target.dimColorInt(context)
        dimColor = if (targetDimColor != null) {
            UiUtil.setAlpha(targetDimColor, 0.3f)
        } else {
            -1
        }
        val titleTextColor = target.titleTextColorInt(context)
        if (titleTextColor != null) {
            titlePaint.setColor(titleTextColor)
        } else {
            titlePaint.setColor(if (isDark) Color.BLACK else Color.WHITE)
        }
        val descriptionTextColor = target.descriptionTextColorInt(context)
        if (descriptionTextColor != null) {
            descriptionPaint.setColor(descriptionTextColor)
        } else {
            descriptionPaint.setColor(titlePaint.color)
        }
        if (target.titleTypeface != null) {
            titlePaint.setTypeface(target.titleTypeface)
        }
        if (target.descriptionTypeface != null) {
            descriptionPaint.setTypeface(target.descriptionTypeface)
        }
    }

    override fun onDetachedFromWindow() {
        super.onDetachedFromWindow()
        onDismiss(false)
    }

    fun onDismiss(userInitiated: Boolean) {
        if (isDismissed) return
        isDismissing = false
        isDismissed = true
        for (animator in animators) {
            animator!!.cancel()
            animator.removeAllUpdateListeners()
        }
        ViewUtil.removeOnGlobalLayoutListener(getViewTreeObserver(), globalLayoutListener)
        visible = false
        if (listener != null) {
            listener!!.onTargetDismissed(this, userInitiated)
        }
    }

    override fun onDraw(c: Canvas) {
        if (isDismissed || outerCircleCenter == null) return
        if (topBoundary > 0 && bottomBoundary > 0) {
            c.clipRect(0, topBoundary, width, bottomBoundary)
        }
        if (dimColor != -1) {
            c.drawColor(dimColor)
        }
        var saveCount: Int
        outerCirclePaint.setAlpha(outerCircleAlpha)
        if (shouldDrawShadow && outlineProvider == null) {
            saveCount = c.save()
            run {
                c.clipPath(outerCirclePath, Region.Op.DIFFERENCE)
                drawJitteredShadow(c)
            }
            c.restoreToCount(saveCount)
        }
        c.drawCircle(outerCircleCenter!![0].toFloat(), outerCircleCenter!![1].toFloat(), outerCircleRadius, outerCirclePaint)
        targetCirclePaint.setAlpha(targetCircleAlpha)
        if (targetCirclePulseAlpha > 0) {
            targetCirclePulsePaint.setAlpha(targetCirclePulseAlpha)
            c.drawCircle(targetBounds.centerX().toFloat(), targetBounds.centerY().toFloat(),
                targetCirclePulseRadius, targetCirclePulsePaint)
        }
        c.drawCircle(targetBounds.centerX().toFloat(), targetBounds.centerY().toFloat(),
            targetCircleRadius, targetCirclePaint)
        saveCount = c.save()
        run {
            c.translate(textBounds!!.left.toFloat(), textBounds!!.top.toFloat())
            titlePaint.setAlpha(textAlpha)
            if (titleLayout != null) {
                titleLayout!!.draw(c)
            }
            if (descriptionLayout != null && titleLayout != null) {
                c.translate(0f, (titleLayout!!.height + TEXT_SPACING).toFloat())
                descriptionPaint.setAlpha((target.descriptionTextAlpha * textAlpha).toInt())
                descriptionLayout!!.draw(c)
            }
        }
        c.restoreToCount(saveCount)
        saveCount = c.save()
        run {
            if (tintedTarget != null) {
                c.translate((targetBounds.centerX() - tintedTarget!!.getWidth() / 2).toFloat(),
                    (
                            targetBounds.centerY() - tintedTarget!!.getHeight() / 2).toFloat())
                c.drawBitmap(tintedTarget!!, 0f, 0f, targetCirclePaint)
            } else if (target.icon != null) {
                c.translate((targetBounds.centerX() - target.icon!!.getBounds().width() / 2).toFloat(),
                    (targetBounds.centerY() - target.icon!!.getBounds().height() / 2).toFloat())
                target.icon!!.alpha = targetCirclePaint.alpha
                target.icon!!.draw(c)
            }
        }
        c.restoreToCount(saveCount)
        if (debug) {
            drawDebugInformation(c)
        }
    }

    override fun onTouchEvent(e: MotionEvent): Boolean {
        lastTouchX = e.x
        lastTouchY = e.y
        return super.onTouchEvent(e)
    }

    override fun onKeyDown(keyCode: Int, event: KeyEvent): Boolean {
        if (isVisible() && cancelable && keyCode == KeyEvent.KEYCODE_BACK) {
            event.startTracking()
            return true
        }
        return false
    }

    override fun onKeyUp(keyCode: Int, event: KeyEvent): Boolean {
        if (isVisible() && isInteractable && cancelable && keyCode == KeyEvent.KEYCODE_BACK && event.isTracking && !event.isCanceled) {
            isInteractable = false
            if (listener != null) {
                listener!!.onTargetCancel(this)
            } else {
                Listener().onTargetCancel(this)
            }
            return true
        }
        return false
    }

    /**
     * Dismiss this view
     * @param tappedTarget If the user tapped the target or not
     * (results in different dismiss animations)
     */
    fun dismiss(tappedTarget: Boolean) {
        isDismissing = true
        pulseAnimation!!.cancel()
        expandAnimation!!.cancel()
        if (!visible || outerCircleCenter == null) {
            finishDismiss(tappedTarget)
            return
        }
        if (tappedTarget) {
            dismissConfirmAnimation!!.start()
        } else {
            dismissAnimation!!.start()
        }
    }

    private fun finishDismiss(userInitiated: Boolean) {
        onDismiss(userInitiated)
        ViewUtil.removeView(parent, this@TapTargetView)
    }

    /** Specify whether to draw a wireframe around the view, useful for debugging  */
    fun setDrawDebug(status: Boolean) {
        if (debug != status) {
            debug = status
            postInvalidate()
        }
    }

    /** Returns whether this view is visible or not  */
    fun isVisible(): Boolean {
        return !isDismissed && visible
    }

    fun drawJitteredShadow(c: Canvas) {
        val baseAlpha = 0.20f * outerCircleAlpha
        outerCircleShadowPaint.style = Paint.Style.FILL_AND_STROKE
        outerCircleShadowPaint.setAlpha(baseAlpha.toInt())
        c.drawCircle(outerCircleCenter!![0].toFloat(), (outerCircleCenter!![1] + SHADOW_DIM).toFloat(), outerCircleRadius, outerCircleShadowPaint)
        outerCircleShadowPaint.style = Paint.Style.STROKE
        val numJitters = 7
        for (i in numJitters - 1 downTo 1) {
            outerCircleShadowPaint.setAlpha((i / numJitters.toFloat() * baseAlpha).toInt())
            c.drawCircle(outerCircleCenter!![0].toFloat(), (outerCircleCenter!![1] + SHADOW_DIM).toFloat(),
                outerCircleRadius + (numJitters - i) * SHADOW_JITTER_DIM, outerCircleShadowPaint)
        }
    }

    fun drawDebugInformation(c: Canvas) {
        if (debugPaint == null) {
            debugPaint = Paint()
            debugPaint!!.setARGB(255, 255, 0, 0)
            debugPaint!!.style = Paint.Style.STROKE
            debugPaint!!.strokeWidth = UiUtil.dp(context, 1).toFloat()
        }
        if (debugTextPaint == null) {
            debugTextPaint = TextPaint()
            debugTextPaint!!.setColor(-0x10000)
            debugTextPaint!!.textSize = UiUtil.sp(context, 16).toFloat()
        }

        // Draw wireframe
        debugPaint!!.style = Paint.Style.STROKE
        c.drawRect(textBounds!!, debugPaint!!)
        c.drawRect(targetBounds, debugPaint!!)
        c.drawCircle(outerCircleCenter!![0].toFloat(), outerCircleCenter!![1].toFloat(), 10f, debugPaint!!)
        c.drawCircle(outerCircleCenter!![0].toFloat(), outerCircleCenter!![1].toFloat(), (calculatedOuterCircleRadius - CIRCLE_PADDING).toFloat(), debugPaint!!)
        c.drawCircle(targetBounds.centerX().toFloat(), targetBounds.centerY().toFloat(), (TARGET_RADIUS + TARGET_PADDING).toFloat(), debugPaint!!)

        // Draw positions and dimensions
        debugPaint!!.style = Paint.Style.FILL
        val debugText = """Text bounds: ${textBounds!!.toShortString()}
Target bounds: ${targetBounds.toShortString()}
Center: ${outerCircleCenter!![0]} ${outerCircleCenter!![1]}
View size: $width $height
Target bounds: ${targetBounds.toShortString()}"""
        if (debugStringBuilder == null) {
            debugStringBuilder = SpannableStringBuilder(debugText)
        } else {
            debugStringBuilder!!.clear()
            debugStringBuilder!!.append(debugText)
        }
        if (debugLayout == null) {
            debugLayout = DynamicLayout(debugText, debugTextPaint!!, width, Layout.Alignment.ALIGN_NORMAL, 1.0f, 0.0f, false)
        }
        val saveCount = c.save()
        run {
            debugPaint!!.setARGB(220, 0, 0, 0)
            c.translate(0.0f, topBoundary.toFloat())
            c.drawRect(0.0f, 0.0f, debugLayout!!.width.toFloat(), debugLayout!!.height.toFloat(), debugPaint!!)
            debugPaint!!.setARGB(255, 255, 0, 0)
            debugLayout!!.draw(c)
        }
        c.restoreToCount(saveCount)
    }

    fun drawTintedTarget() {
        val icon = target.icon
        if (!shouldTintTarget || icon == null) {
            tintedTarget = null
            return
        }
        if (tintedTarget != null) return
        tintedTarget = Bitmap.createBitmap(icon.intrinsicWidth, icon.intrinsicHeight,
            Bitmap.Config.ARGB_8888)
        val canvas = Canvas(tintedTarget!!)
        icon.colorFilter = PorterDuffColorFilter(
            outerCirclePaint.color, PorterDuff.Mode.SRC_ATOP)
        icon.draw(canvas)
        icon.colorFilter = null
    }

    fun updateTextLayouts() {
        val textWidth = (min(width.toDouble(), TEXT_MAX_WIDTH.toDouble()) - TEXT_PADDING * 2).toInt()
        if (textWidth <= 0) {
            return
        }
        titleLayout = StaticLayout(title, titlePaint, textWidth,
            Layout.Alignment.ALIGN_NORMAL, 1.0f, 0.0f, false)
        descriptionLayout = if (description != null) {
            StaticLayout(description, descriptionPaint, textWidth,
                Layout.Alignment.ALIGN_NORMAL, 1.0f, 0.0f, false)
        } else {
            null
        }
    }

    fun halfwayLerp(lerp: Float): Float {
        return if (lerp < 0.5f) {
            lerp / 0.5f
        } else (1.0f - lerp) / 0.5f
    }

    fun delayedLerp(lerp: Float, threshold: Float): Float {
        return if (lerp < threshold) {
            0.0f
        } else (lerp - threshold) / (1.0f - threshold)
    }

    fun calculateDimensions() {
        textBounds = getTextBounds()
        outerCircleCenter = outerCircleCenterPoint
        calculatedOuterCircleRadius = getOuterCircleRadius(outerCircleCenter!![0], outerCircleCenter!![1], textBounds, targetBounds)
    }

    fun calculateDrawingBounds() {
        if (outerCircleCenter == null) {
            // Called dismiss before we got a chance to display the tap target
            // So we have no center -> cant determine the drawing bounds
            return
        }
        drawingBounds.left = max(0.0, (outerCircleCenter!![0] - outerCircleRadius).toDouble()).toInt()
        drawingBounds.top = min(0.0, (outerCircleCenter!![1] - outerCircleRadius).toDouble()).toInt()
        drawingBounds.right = min(width.toDouble(),
            (
                    outerCircleCenter!![0] + outerCircleRadius + CIRCLE_PADDING).toDouble()).toInt()
        drawingBounds.bottom = min(height.toDouble(),
            (
                    outerCircleCenter!![1] + outerCircleRadius + CIRCLE_PADDING).toDouble()).toInt()
    }

    fun getOuterCircleRadius(centerX: Int, centerY: Int, textBounds: Rect?, targetBounds: Rect): Int {
        val targetCenterX = targetBounds.centerX()
        val targetCenterY = targetBounds.centerY()
        val expandedRadius = (1.1f * TARGET_RADIUS).toInt()
        val expandedBounds = Rect(targetCenterX, targetCenterY, targetCenterX, targetCenterY)
        expandedBounds.inset(-expandedRadius, -expandedRadius)
        val textRadius = maxDistanceToPoints(centerX, centerY, textBounds)
        val targetRadius = maxDistanceToPoints(centerX, centerY, expandedBounds)
        return (max(textRadius.toDouble(), targetRadius.toDouble()) + CIRCLE_PADDING).toInt()
    }

    fun getTextBounds(): Rect {
        val totalTextHeight = totalTextHeight
        val totalTextWidth = totalTextWidth
        val possibleTop = targetBounds.centerY() - TARGET_RADIUS - TARGET_PADDING - totalTextHeight
        val top: Int
        top = if (possibleTop > topBoundary) {
            possibleTop
        } else {
            targetBounds.centerY() + TARGET_RADIUS + TARGET_PADDING
        }
        val relativeCenterDistance = width / 2 - targetBounds.centerX()
        val bias = if (relativeCenterDistance < 0) -TEXT_POSITIONING_BIAS else TEXT_POSITIONING_BIAS
        val left = max(TEXT_PADDING.toDouble(), (targetBounds.centerX() - bias - totalTextWidth).toDouble()).toInt()
        val right = min((width - TEXT_PADDING).toDouble(), (left + totalTextWidth).toDouble()).toInt()
        return Rect(left, top, right, top + totalTextHeight)
    }

    val outerCircleCenterPoint: IntArray
        get() {
            if (inGutter(targetBounds.centerY())) {
                return intArrayOf(targetBounds.centerX(), targetBounds.centerY())
            }
            val targetRadius = (max(targetBounds.width().toDouble(), targetBounds.height().toDouble()) / 2 + TARGET_PADDING).toInt()
            val totalTextHeight = totalTextHeight
            val onTop = targetBounds.centerY() - TARGET_RADIUS - TARGET_PADDING - totalTextHeight > 0
            val left = min(textBounds!!.left.toDouble(), (targetBounds.left - targetRadius).toDouble()).toInt()
            val right = max(textBounds!!.right.toDouble(), (targetBounds.right + targetRadius).toDouble()).toInt()
            val titleHeight = if (titleLayout == null) 0 else titleLayout!!.height
            val centerY = if (onTop) targetBounds.centerY() - TARGET_RADIUS - TARGET_PADDING - totalTextHeight + titleHeight else targetBounds.centerY() + TARGET_RADIUS + TARGET_PADDING + titleHeight
            return intArrayOf((left + right) / 2, centerY)
        }
    val totalTextHeight: Int
        get() {
            if (titleLayout == null) {
                return 0
            }
            return if (descriptionLayout == null) {
                titleLayout!!.height + TEXT_SPACING
            } else titleLayout!!.height + descriptionLayout!!.height + TEXT_SPACING
        }
    val totalTextWidth: Int
        get() {
            if (titleLayout == null) {
                return 0
            }
            return if (descriptionLayout == null) {
                titleLayout!!.width
            } else max(titleLayout!!.width.toDouble(), descriptionLayout!!.width.toDouble()).toInt()
        }

    fun inGutter(y: Int): Boolean {
        return if (bottomBoundary > 0) {
            y < GUTTER_DIM || y > bottomBoundary - GUTTER_DIM
        } else {
            y < GUTTER_DIM || y > height - GUTTER_DIM
        }
    }

    fun maxDistanceToPoints(x1: Int, y1: Int, bounds: Rect?): Int {
        val tl = distance(x1, y1, bounds!!.left, bounds.top)
        val tr = distance(x1, y1, bounds.right, bounds.top)
        val bl = distance(x1, y1, bounds.left, bounds.bottom)
        val br = distance(x1, y1, bounds.right, bounds.bottom)
        return max(tl, max(tr, max(bl, br))).toInt()
    }

    fun distance(x1: Int, y1: Int, x2: Int, y2: Int): Double {
        return sqrt((x2 - x1).toDouble().pow(2.0) + (y2 - y1).toDouble().pow(2.0))
    }

    fun invalidateViewAndOutline(bounds: Rect?) {
        invalidate(bounds)
        if (outlineProvider != null && Build.VERSION.SDK_INT >= 21) {
            invalidateOutline()
        }
    }

    companion object {
        fun showFor(activity: Activity?, target: TapTarget?): TapTargetView {
            return showFor(activity, target, null)
        }

        fun showFor(activity: Activity?, target: TapTarget?, listener: Listener?): TapTargetView {
            requireNotNull(activity) { "Activity is null" }
            val decor = activity.window.decorView as ViewGroup
            val layoutParams = ViewGroup.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.MATCH_PARENT)
            val content = decor.findViewById<View>(android.R.id.content) as ViewGroup
            val tapTargetView = TapTargetView(activity, decor, content, target, listener)
            decor.addView(tapTargetView, layoutParams)
            return tapTargetView
        }

        fun showFor(dialog: Dialog?, target: TapTarget?): TapTargetView {
            return showFor(dialog, target, null)
        }

        fun showFor(dialog: Dialog?, target: TapTarget?, listener: Listener?): TapTargetView {
            requireNotNull(dialog) { "Dialog is null" }
            val context = dialog.context
            val windowManager = context.getSystemService(Context.WINDOW_SERVICE) as WindowManager
            val params = WindowManager.LayoutParams()
            params.type = WindowManager.LayoutParams.TYPE_APPLICATION
            params.format = PixelFormat.RGBA_8888
            params.flags = 0
            params.gravity = Gravity.START or Gravity.TOP
            params.x = 0
            params.y = 0
            params.width = WindowManager.LayoutParams.MATCH_PARENT
            params.height = WindowManager.LayoutParams.MATCH_PARENT
            val tapTargetView = TapTargetView(context, windowManager, null, target, listener)
            windowManager.addView(tapTargetView, params)
            return tapTargetView
        }
    }
}


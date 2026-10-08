package com.liufy.thermaldisplay

import android.content.Context
import android.graphics.drawable.Drawable
import android.view.GestureDetector
import android.view.MotionEvent
import android.view.View
import android.widget.FrameLayout
import android.widget.ImageView
import kotlin.math.abs
import kotlin.math.hypot

/**
 * V21 media viewport.
 *
 * The drawable is never transformed with ImageView.imageMatrix.  Instead the
 * ImageView itself is scaled/translated.  This keeps touch transforms separate
 * from AnimatedImageDrawable refreshes and from thermal drawable replacement.
 * The entire rectangular media viewport is the touch target.
 */
class ZoomableMediaViewport(
    context: Context,
    private val fitFraction: Float,
    private val maxUserScale: Float
) : FrameLayout(context) {

    private val image = ImageView(context).apply {
        scaleType = ImageView.ScaleType.FIT_CENTER
        isClickable = false
        isFocusable = false
        pivotX = 0f
        pivotY = 0f
        setLayerType(View.LAYER_TYPE_HARDWARE, null)
    }

    private val gestureDetector = GestureDetector(context, object : GestureDetector.SimpleOnGestureListener() {
        override fun onDown(e: MotionEvent): Boolean = true
        override fun onDoubleTap(e: MotionEvent): Boolean {
            resetTransform()
            return true
        }
    })

    private var userScale = 1f
    private var tx = 0f
    private var ty = 0f
    private var lastX = 0f
    private var lastY = 0f
    private var lastSpan = 0f
    private var pinching = false

    private var maxPointersSeen = 0
    private var lastPointerCount = 0
    private var lastInputSource = 0

    init {
        clipChildren = true
        clipToPadding = true
        isClickable = true
        isFocusable = true
        isMotionEventSplittingEnabled = false
        addView(image, LayoutParams(LayoutParams.MATCH_PARENT, LayoutParams.MATCH_PARENT))
    }

    /** Always intercept touches inside this media rectangle. */
    override fun onInterceptTouchEvent(ev: MotionEvent): Boolean = true

    fun setImageDrawable(drawable: Drawable?) {
        image.setImageDrawable(drawable)
        if (drawable != null && (width > 0 && height > 0)) {
            post { if (image.scaleX == 1f && userScale == 1f) resetTransform() }
        }
    }

    /** Replacing a thermal frame must not change the current zoom/pan. */
    fun setImageDrawablePreserveTransform(drawable: Drawable?) {
        image.setImageDrawable(drawable)
        image.scaleX = fitFraction * userScale
        image.scaleY = fitFraction * userScale
        image.translationX = tx
        image.translationY = ty
        invalidate()
    }

    fun resetTransform() {
        if (width <= 0 || height <= 0) return
        userScale = 1f
        tx = (1f - fitFraction) * width * 0.5f
        ty = (1f - fitFraction) * height * 0.5f
        image.pivotX = 0f
        image.pivotY = 0f
        image.scaleX = fitFraction
        image.scaleY = fitFraction
        image.translationX = tx
        image.translationY = ty
        lastSpan = 0f
        pinching = false
        invalidate()
    }

    override fun onSizeChanged(w: Int, h: Int, oldw: Int, oldh: Int) {
        super.onSizeChanged(w, h, oldw, oldh)
        if (w > 0 && h > 0 && (oldw == 0 || oldh == 0)) post { resetTransform() }
    }

    override fun onTouchEvent(event: MotionEvent): Boolean {
        parent?.requestDisallowInterceptTouchEvent(true)
        gestureDetector.onTouchEvent(event)

        lastPointerCount = event.pointerCount
        maxPointersSeen = maxOf(maxPointersSeen, event.pointerCount)
        lastInputSource = event.source

        when (event.actionMasked) {
            MotionEvent.ACTION_DOWN -> {
                pinching = false
                lastSpan = 0f
                lastX = event.x
                lastY = event.y
                return true
            }

            MotionEvent.ACTION_POINTER_DOWN -> {
                if (event.pointerCount >= 2) {
                    pinching = true
                    lastSpan = span(event)
                }
                return true
            }

            MotionEvent.ACTION_MOVE -> {
                if (event.pointerCount >= 2) {
                    val currentSpan = span(event)
                    if (!pinching || lastSpan <= 0f) {
                        pinching = true
                        lastSpan = currentSpan
                        return true
                    }

                    if (currentSpan > 0f) {
                        val rawFactor = currentSpan / lastSpan
                        val requested = (userScale * rawFactor).coerceIn(0.72f, maxUserScale)
                        val factor = requested / userScale
                        if (factor.isFinite() && factor > 0f && abs(factor - 1f) > 0.0001f) {
                            val focusX = (event.getX(0) + event.getX(1)) * 0.5f
                            val focusY = (event.getY(0) + event.getY(1)) * 0.5f

                            // Keep the content point under the pinch centre stationary.
                            tx = focusX - (focusX - tx) * factor
                            ty = focusY - (focusY - ty) * factor
                            userScale = requested
                            applyTransform()
                        }
                        lastSpan = currentSpan
                    }
                    return true
                }

                if (event.pointerCount == 1) {
                    val x = event.x
                    val y = event.y
                    if (!pinching && userScale > 1.001f) {
                        tx += x - lastX
                        ty += y - lastY
                        applyTransform()
                    }
                    lastX = x
                    lastY = y
                }
                return true
            }

            MotionEvent.ACTION_POINTER_UP -> {
                pinching = false
                lastSpan = 0f
                val lifted = event.actionIndex
                val remain = if (lifted == 0 && event.pointerCount > 1) 1 else 0
                if (remain < event.pointerCount) {
                    lastX = event.getX(remain)
                    lastY = event.getY(remain)
                }
                return true
            }

            MotionEvent.ACTION_UP -> {
                pinching = false
                lastSpan = 0f
                parent?.requestDisallowInterceptTouchEvent(false)
                performClick()
                return true
            }

            MotionEvent.ACTION_CANCEL -> {
                pinching = false
                lastSpan = 0f
                parent?.requestDisallowInterceptTouchEvent(false)
                return true
            }
        }
        return true
    }

    override fun performClick(): Boolean {
        super.performClick()
        return true
    }

    private fun applyTransform() {
        val s = fitFraction * userScale
        image.scaleX = s
        image.scaleY = s
        image.translationX = tx
        image.translationY = ty
        invalidate()
    }

    private fun span(event: MotionEvent): Float {
        if (event.pointerCount < 2) return 0f
        return hypot(event.getX(1) - event.getX(0), event.getY(1) - event.getY(0))
    }

    fun getMaxPointersSeen(): Int = maxPointersSeen
    fun getLastPointerCount(): Int = lastPointerCount
    fun getLastInputSource(): Int = lastInputSource
    fun getUserScale(): Float = userScale
}

package com.example.myastapp

import android.content.Context
import android.graphics.*
import android.util.AttributeSet
import android.view.MotionEvent
import android.view.View

class ZoneOverlayView @JvmOverloads constructor(
    context: Context, attrs: AttributeSet? = null, defStyleAttr: Int = 0
) : View(context, attrs, defStyleAttr) {

    private var centerX = 500f
    private var centerY = 500f
    private var radius = 100f
    private var isVisible = true
    private var lastX = 0f
    private var lastY = 0f

    private val paint = Paint().apply {
        color = Color.GREEN
        style = Paint.Style.STROKE
        strokeWidth = 3f
        isAntiAlias = true
        pathEffect = DashPathEffect(floatArrayOf(10f, 10f), 0f)
    }

    interface OnCircleChangedListener {
        fun onCircleChanged(centerX: Float, centerY: Float, radius: Float)
    }

    var changeListener: OnCircleChangedListener? = null

    override fun onDraw(canvas: Canvas) {
        super.onDraw(canvas)
        if (isVisible) {
            canvas.drawCircle(centerX, centerY, radius, paint)
        }
    }

    override fun onTouchEvent(event: MotionEvent): Boolean {
        if (!isEnabled) return false

        when (event.action) {
            MotionEvent.ACTION_DOWN -> {
                // 1. Check if the circle was hidden (meaning we just saved a result)
                if (!isVisible) {
                    // Teleport the ring to the new disc you just touched
                    centerX = event.x
                    centerY = event.y
                    isVisible = true // Make it visible at the new spot
                }

                // 2. Store current touch for smooth dragging
                lastX = event.x
                lastY = event.y

                invalidate()
                changeListener?.onCircleChanged(centerX, centerY, radius)
            }
            MotionEvent.ACTION_MOVE -> {
                // 3. Your original perfect dragging logic
                val dx = event.x - lastX
                val dy = event.y - lastY

                centerX += dx
                centerY += dy

                lastX = event.x
                lastY = event.y

                invalidate()
                changeListener?.onCircleChanged(centerX, centerY, radius)
            }
        }
        return true
    }
    fun updateRadius(newRadius: Float) {
        this.radius = newRadius
        invalidate()
        changeListener?.onCircleChanged(centerX, centerY, radius)
    }

    fun hideCircle() {
        isVisible = false
        invalidate() // This tells Android to redraw the screen (hiding the ring)
    }
    fun showCircle() { isVisible = true; invalidate() }
    fun getCurrentRadius(): Float = radius
    fun getCenterPoint(): PointF = PointF(centerX, centerY)
}
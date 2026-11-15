package com.noorazan.app.ui

import android.content.Context
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.graphics.Path
import android.util.AttributeSet
import android.view.View
import kotlin.math.cos
import kotlin.math.min
import kotlin.math.sin

class QiblaCompassView @JvmOverloads constructor(
    context: Context,
    attrs: AttributeSet? = null,
    defStyleAttr: Int = 0
) : View(context, attrs, defStyleAttr) {

    private var azimuthDegrees: Float = 0f
    private var qiblaDegrees: Float = 0f

    /** When false the Kaaba arrow is hidden and the view acts as a plain compass. */
    var showQibla: Boolean = true
        set(value) {
            field = value
            invalidate()
        }

    private val outerRingPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = Color.parseColor("#1B5E20")
        style = Paint.Style.STROKE
        strokeWidth = 8f
    }

    private val innerFillPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = Color.parseColor("#F4F8F5")
        style = Paint.Style.FILL
    }

    private val tickPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = Color.parseColor("#78909C")
        strokeWidth = 3f
    }

    private val cardinalPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = Color.parseColor("#1E293B")
        textSize = 42f
        textAlign = Paint.Align.CENTER
        isFakeBoldText = true
    }

    private val northPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = Color.parseColor("#C62828")
        textSize = 44f
        textAlign = Paint.Align.CENTER
        isFakeBoldText = true
    }

    private val qiblaPointerPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = Color.parseColor("#D4AF37")
        style = Paint.Style.FILL
    }

    private val qiblaGlowPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = Color.parseColor("#2E7D32")
        style = Paint.Style.FILL
    }

    private val centerDotPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = Color.parseColor("#1B5E20")
        style = Paint.Style.FILL
    }

    fun updateAngles(azimuth: Float, qiblaBearing: Float) {
        this.azimuthDegrees = azimuth
        this.qiblaDegrees = qiblaBearing
        invalidate()
    }

    override fun onDraw(canvas: Canvas) {
        super.onDraw(canvas)

        val cx = width / 2f
        val cy = height / 2f
        val radius = min(cx, cy) - 24f
        if (radius <= 0f) return

        canvas.drawCircle(cx, cy, radius, innerFillPaint)
        canvas.drawCircle(cx, cy, radius, outerRingPaint)

        canvas.save()
        canvas.rotate(-azimuthDegrees, cx, cy)

        for (deg in 0 until 360 step 15) {
            val rad = Math.toRadians(deg.toDouble())
            val innerR = if (deg % 90 == 0) radius - 34f else if (deg % 45 == 0) radius - 24f else radius - 14f
            val startX = cx + (innerR * sin(rad)).toFloat()
            val startY = cy - (innerR * cos(rad)).toFloat()
            val endX = cx + ((radius - 6f) * sin(rad)).toFloat()
            val endY = cy - ((radius - 6f) * cos(rad)).toFloat()
            canvas.drawLine(startX, startY, endX, endY, tickPaint)
        }

        val labelRadius = radius - 64f
        canvas.drawText("N", cx, cy - labelRadius + 14f, northPaint)
        canvas.drawText("E", cx + labelRadius, cy + 14f, cardinalPaint)
        canvas.drawText("S", cx, cy + labelRadius + 14f, cardinalPaint)
        canvas.drawText("W", cx - labelRadius, cy + 14f, cardinalPaint)

        if (showQibla) {
        canvas.save()
        canvas.rotate(qiblaDegrees, cx, cy)

        val arrowPath = Path().apply {
            moveTo(cx, cy - radius + 22f)
            lineTo(cx - 24f, cy - radius + 82f)
            lineTo(cx - 8f, cy - radius + 76f)
            lineTo(cx - 8f, cy + 35f)
            lineTo(cx + 8f, cy + 35f)
            lineTo(cx + 8f, cy - radius + 76f)
            lineTo(cx + 24f, cy - radius + 82f)
            close()
        }
        canvas.drawPath(arrowPath, qiblaGlowPaint)

        val kaabaTop = cy - radius + 92f
        canvas.drawRect(cx - 18f, kaabaTop, cx + 18f, kaabaTop + 36f, qiblaPointerPaint)

        canvas.restore()
        }
        canvas.restore()

        val topNotch = Path().apply {
            moveTo(cx, 6f)
            lineTo(cx - 14f, 26f)
            lineTo(cx + 14f, 26f)
            close()
        }
        canvas.drawPath(topNotch, northPaint)

        canvas.drawCircle(cx, cy, 14f, centerDotPaint)
    }
}

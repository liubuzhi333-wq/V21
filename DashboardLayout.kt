package com.liufy.thermaldisplay

import android.content.Context
import android.graphics.*
import android.graphics.drawable.GradientDrawable
import android.util.AttributeSet
import android.util.TypedValue
import android.view.MotionEvent
import android.view.Gravity
import android.view.View
import android.view.ViewGroup
import android.widget.TextView
import kotlin.math.min

class DashboardLayout @JvmOverloads constructor(
    context: Context,
    attrs: AttributeSet? = null
) : ViewGroup(context, attrs) {

    companion object {
        const val DESIGN_W = 1920f
        const val DESIGN_H = 1080f
        private const val LEFT_X = 52f
        private const val RIGHT_X = 1368f
        private const val COL_Y = 145f
        private const val PANEL_W = 500f
        private const val TOP_H = 690f
        private const val BOTTOM_Y = 870f
        private const val BOTTOM_H = 165f
    }

    data class Spec(val x: Float, val y: Float, val w: Float, val h: Float, val textPx: Float = 12f)

    private val specs = LinkedHashMap<View, Spec>()
    private var stageScale = 1f
    private var offX = 0f
    private var offY = 0f

    val modelView = ZoomableMediaViewport(context, 0.94f, 3.5f)
    val thermalView = ZoomableMediaViewport(context, 0.90f, 3.0f)
    val resetButton = DashboardButton(context, "重置")
    val playButton = DashboardButton(context, "暂停")
    val thermalButtons = arrayOf(
        DashboardButton(context, "正面"),
        DashboardButton(context, "右侧"),
        DashboardButton(context, "背面"),
        DashboardButton(context, "左侧")
    )

    var onHeaderTap: (() -> Unit)? = null

    private val fillPanel = Paint(Paint.ANTI_ALIAS_FLAG).apply { color = Color.argb(122, 65, 76, 84) }
    private val linePaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = Color.argb(158, 165, 215, 235)
        style = Paint.Style.STROKE
        strokeWidth = 1f
    }
    private val frameLinePaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = Color.argb(66, 143, 220, 255)
        style = Paint.Style.STROKE
        strokeWidth = 1f
    }
    private val textPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = Color.WHITE
        typeface = Typeface.create("sans-serif", Typeface.BOLD)
    }
    private val normalPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = Color.WHITE
        typeface = Typeface.create("sans-serif", Typeface.NORMAL)
    }
    private val cyan = Color.rgb(143, 220, 255)
    private val subText = Color.rgb(223, 245, 255)

    init {
        setWillNotDraw(false)
        isClickable = true
        // Keep a multi-finger gesture together on the same media child.
        // This is important for some USB/industrial touch controllers.
        isMotionEventSplittingEnabled = false

        addDesignChild(modelView, Spec(LEFT_X + 16f, COL_Y + 68f, 468f, 568f))
        addDesignChild(thermalView, Spec(RIGHT_X + 16f, COL_Y + 68f, 468f, 568f))

        addDesignChild(resetButton, Spec(LEFT_X + 16f, COL_Y + TOP_H - 46f, 72f, 32f, 12f))
        addDesignChild(playButton, Spec(LEFT_X + 96f, COL_Y + TOP_H - 46f, 72f, 32f, 12f))

        val viewX = RIGHT_X + 16f
        val viewY = COL_Y + TOP_H - 46f
        val gap = 7f
        val bw = (468f - gap * 3f) / 4f
        thermalButtons.forEachIndexed { i, b ->
            addDesignChild(b, Spec(viewX + i * (bw + gap), viewY, bw, 34f, 12f))
        }
        thermalButtons[0].setActive(true)
    }

    private fun addDesignChild(view: View, spec: Spec) {
        specs[view] = spec
        addView(view)
    }

    fun updateThermalActive(index: Int) {
        thermalButtons.forEachIndexed { i, b -> b.setActive(i == index) }
    }

    fun getDesignScale(): Float = stageScale
    fun getStageOffsetX(): Float = offX
    fun getStageOffsetY(): Float = offY

    override fun onMeasure(widthMeasureSpec: Int, heightMeasureSpec: Int) {
        val w = MeasureSpec.getSize(widthMeasureSpec)
        val h = MeasureSpec.getSize(heightMeasureSpec)
        setMeasuredDimension(w, h)
        stageScale = min(w / DESIGN_W, h / DESIGN_H)
        offX = (w - DESIGN_W * stageScale) / 2f
        offY = (h - DESIGN_H * stageScale) / 2f

        specs.forEach { (v, s) ->
            val cw = (s.w * stageScale).toInt().coerceAtLeast(1)
            val ch = (s.h * stageScale).toInt().coerceAtLeast(1)
            if (v is TextView) {
                v.setTextSize(TypedValue.COMPLEX_UNIT_PX, s.textPx * stageScale)
            }
            v.measure(MeasureSpec.makeMeasureSpec(cw, MeasureSpec.EXACTLY), MeasureSpec.makeMeasureSpec(ch, MeasureSpec.EXACTLY))
        }
    }

    override fun onLayout(changed: Boolean, l: Int, t: Int, r: Int, b: Int) {
        specs.forEach { (v, s) ->
            val x = (offX + s.x * stageScale).toInt()
            val y = (offY + s.y * stageScale).toInt()
            v.layout(x, y, x + v.measuredWidth, y + v.measuredHeight)
        }
    }

    override fun onDraw(canvas: Canvas) {
        super.onDraw(canvas)
        canvas.save()
        canvas.translate(offX, offY)
        canvas.scale(stageScale, stageScale)

        val bg = LinearGradient(0f, 0f, 0f, DESIGN_H,
            intArrayOf(Color.rgb(123,137,146), Color.rgb(105,119,128), Color.rgb(89,104,114)),
            floatArrayOf(0f, .48f, 1f), Shader.TileMode.CLAMP)
        val bgPaint = Paint().apply { shader = bg }
        canvas.drawRect(0f, 0f, DESIGN_W, DESIGN_H, bgPaint)
        canvas.drawRoundRect(14f, 14f, DESIGN_W - 14f, DESIGN_H - 14f, 16f, 16f, frameLinePaint)

        // Header
        drawTextTop(canvas, "36.5度智能温度调节西服", 52f, 28f, 38f, true, Color.WHITE)
        drawSpacedText(canvas, "36.5 SMART TEMPERATURE-REGULATING SUIT", 52f, 78f, 14f, 4f, subText)
        drawTextRightTop(canvas, "科技 / 舒适 / 专业", 1868f, 34f, 14f, true, Color.WHITE)
        drawTextRightTop(canvas, "TECHNOLOGY / COMFORT / PROFESSIONAL", 1868f, 59f, 9f, false, Color.rgb(215,230,238))

        drawColumn(canvas, LEFT_X, true)
        drawColumn(canvas, RIGHT_X, false)
        canvas.restore()
    }

    private fun drawColumn(canvas: Canvas, x: Float, left: Boolean) {
        drawPanel(canvas, x, COL_Y, PANEL_W, TOP_H)
        drawPanel(canvas, x, BOTTOM_Y, PANEL_W, BOTTOM_H)

        if (left) {
            drawTextTop(canvas, "3D动态展示", x + 16f, COL_Y + 16f, 25f, true, Color.WHITE)
            drawSpacedText(canvas, "360° ROTATION DISPLAY", x + 16f, COL_Y + 48f, 11f, 2f, cyan)

            drawTextTop(canvas, "36.5度恒温科技", x + 16f, BOTTOM_Y + 14f, 18f, true, Color.WHITE)
            drawSpacedText(canvas, "36.5 THERMAL TECHNOLOGY", x + 16f, BOTTOM_Y + 38f, 8f, 1.5f, cyan)
            drawTechCards(canvas, x + 16f, BOTTOM_Y + 63f, 468f, 88f)
        } else {
            drawTextTop(canvas, "热力分析", x + 16f, COL_Y + 16f, 25f, true, Color.WHITE)
            drawSpacedText(canvas, "THERMAL ANALYSIS", x + 16f, COL_Y + 48f, 11f, 2f, cyan)

            drawTextTop(canvas, "温度分布", x + 16f, BOTTOM_Y + 14f, 18f, true, Color.WHITE)
            drawSpacedText(canvas, "THERMAL SCALE", x + 16f, BOTTOM_Y + 38f, 8f, 1.5f, cyan)
            drawThermalScale(canvas, x + 16f, BOTTOM_Y + 72f, 468f)
        }
    }

    private fun drawPanel(canvas: Canvas, x: Float, y: Float, w: Float, h: Float) {
        val r = RectF(x, y, x + w, y + h)
        canvas.drawRoundRect(r, 15f, 15f, fillPanel)
        canvas.drawRoundRect(r, 15f, 15f, linePaint)
    }

    private fun drawTechCards(canvas: Canvas, x: Float, y: Float, w: Float, h: Float) {
        val gap = 7f
        val cw = (w - 2f * gap) / 3f
        val data = arrayOf(
            arrayOf("36.5°", "智能调温", "SMART THERMAL", "REGULATION"),
            arrayOf("5×", "快速排湿", "MOISTURE", "TRANSFER"),
            arrayOf("CARBON", "天然活性碳", "NATURAL ACTIVATED", "CARBON")
        )
        for (i in 0..2) {
            val cx = x + i * (cw + gap)
            val rr = RectF(cx, y, cx + cw, y + h)
            val p = Paint(Paint.ANTI_ALIAS_FLAG).apply { color = Color.argb(100,58,70,78) }
            canvas.drawRoundRect(rr, 10f, 10f, p)
            val border = Paint(Paint.ANTI_ALIAS_FLAG).apply { color=Color.argb(90,143,220,255); style=Paint.Style.STROKE; strokeWidth=1f }
            canvas.drawRoundRect(rr,10f,10f,border)
            drawTextTop(canvas, data[i][0], cx + 10f, y + 10f, if(i==2) 13f else 16f, true, Color.WHITE)
            drawTextTop(canvas, data[i][1], cx + 10f, y + 36f, 12f, true, Color.WHITE)
            drawTextTop(canvas, data[i][2], cx + 10f, y + 57f, 6f, false, cyan)
            drawTextTop(canvas, data[i][3], cx + 10f, y + 68f, 6f, false, cyan)
        }
    }

    private fun drawThermalScale(canvas: Canvas, x: Float, y: Float, w: Float) {
        val colors = intArrayOf(
            Color.rgb(255,79,60), Color.rgb(255,156,75), Color.rgb(255,226,118),
            Color.rgb(146,234,181), Color.rgb(75,205,244), Color.rgb(53,101,255)
        )
        val pos = floatArrayOf(0f,.23f,.43f,.64f,.82f,1f)
        val bar = RectF(x, y, x+w, y+18f)
        val gp = Paint(Paint.ANTI_ALIAS_FLAG).apply { shader = LinearGradient(x,y,x+w,y,colors,pos,Shader.TileMode.CLAMP) }
        canvas.drawRoundRect(bar, 9f, 9f, gp)
        val dx = x + w * .52f
        val dotP = Paint(Paint.ANTI_ALIAS_FLAG).apply { color=Color.WHITE; style=Paint.Style.FILL }
        canvas.drawCircle(dx,y+9f,8f,dotP)
        val ring = Paint(Paint.ANTI_ALIAS_FLAG).apply { color=Color.rgb(184,244,255); style=Paint.Style.STROKE; strokeWidth=3f }
        canvas.drawCircle(dx,y+9f,6.5f,ring)

        val labels = arrayOf(
            "高温" to "High Temp",
            "体表热区" to "Skin Heat Zone",
            "36.5°舒适区" to "36.5° Comfort Zone",
            "温感均衡" to "Thermal Balance",
            "低温" to "Low Temp"
        )
        val cell = w/5f
        labels.forEachIndexed { i, pair ->
            val cx = x + cell*i + cell/2f
            drawTextCenterTop(canvas,pair.first,cx,y+30f,9f,true,Color.WHITE)
            drawTextCenterTop(canvas,pair.second,cx,y+45f,5.5f,false,Color.rgb(219,232,238))
        }
    }

    private fun preparePaint(size: Float, bold: Boolean, color: Int): Paint {
        val p = if (bold) textPaint else normalPaint
        p.textSize = size
        p.color = color
        p.typeface = Typeface.create("sans-serif", if (bold) Typeface.BOLD else Typeface.NORMAL)
        p.textAlign = Paint.Align.LEFT
        return p
    }

    private fun drawTextTop(canvas: Canvas, text: String, x: Float, top: Float, size: Float, bold: Boolean, color: Int) {
        val p = preparePaint(size,bold,color)
        val fm = p.fontMetrics
        canvas.drawText(text,x,top - fm.top,p)
    }

    private fun drawTextRightTop(canvas: Canvas, text: String, right: Float, top: Float, size: Float, bold: Boolean, color: Int) {
        val p = preparePaint(size,bold,color)
        p.textAlign = Paint.Align.RIGHT
        val fm=p.fontMetrics
        canvas.drawText(text,right,top-fm.top,p)
        p.textAlign=Paint.Align.LEFT
    }

    private fun drawTextCenterTop(canvas: Canvas, text: String, center: Float, top: Float, size: Float, bold: Boolean, color: Int) {
        val p=preparePaint(size,bold,color)
        p.textAlign=Paint.Align.CENTER
        val fm=p.fontMetrics
        canvas.drawText(text,center,top-fm.top,p)
        p.textAlign=Paint.Align.LEFT
    }

    private fun drawSpacedText(canvas: Canvas, text: String, x: Float, top: Float, size: Float, spacing: Float, color: Int) {
        val p=preparePaint(size,false,color)
        val fm=p.fontMetrics
        var cx=x
        val base=top-fm.top
        text.forEach { ch ->
            val s=ch.toString()
            canvas.drawText(s,cx,base,p)
            cx += p.measureText(s)+spacing
        }
    }

    override fun onTouchEvent(event: MotionEvent): Boolean {
        if (event.actionMasked == MotionEvent.ACTION_UP) {
            val dx=(event.x-offX)/stageScale
            val dy=(event.y-offY)/stageScale
            if (dx in 30f..920f && dy in 10f..125f) {
                onHeaderTap?.invoke()
            }
        }
        return true
    }
}

class DashboardButton(context: Context, text: String) : TextView(context) {
    private var active = false
    init {
        this.text = text
        gravity = Gravity.CENTER
        setTextColor(Color.WHITE)
        typeface = Typeface.create("sans-serif", Typeface.BOLD)
        isClickable = true
        isFocusable = true
        updateBg()
    }

    fun setActive(value: Boolean) {
        active=value
        updateBg()
    }

    private fun updateBg() {
        background = GradientDrawable().apply {
            cornerRadius = 7f
            setColor(if(active) Color.argb(145,52,143,195) else Color.argb(200,48,60,68))
            setStroke(1, if(active) Color.rgb(188,238,255) else Color.argb(128,143,220,255))
        }
    }
}

package ink.x2.record.weiget

import android.content.Context
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.util.AttributeSet
import android.view.View

class WaveformView(
    context: Context,
    attrs: AttributeSet? = null
): View(context, attrs) {
    private val paint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = Color.RED
    }
    val amplitudes = FloatArray(200)
    private val maxBars = 200
    fun addAmplitude(value: Float) {
        System.arraycopy(
            amplitudes,
            1,
            amplitudes,
            0,
            amplitudes.size - 1
        )
        amplitudes[amplitudes.lastIndex] = value.coerceIn(0f,1f)
        invalidate()
    }
    override fun onDraw(canvas: Canvas) {
        super.onDraw(canvas)
        val barSpacing = width.toFloat() / maxBars
        val barWidth = barSpacing * 0.6f
        val centerY = height / 2f
        amplitudes.forEachIndexed { index, amplitude ->
            val x = index * barSpacing
            val barHeight = maxOf(
                4f,
                amplitude * height * 0.8f
            )
            val top = centerY - barHeight / 2
            val bottom = centerY + barHeight / 2
            canvas.drawRoundRect(
                x,
                top,
                x + barWidth,
                bottom,
                barWidth / 2,
                barWidth / 2,
                paint
            )
        }


    }

}
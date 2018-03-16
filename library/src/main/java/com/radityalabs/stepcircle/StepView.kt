package com.radityalabs.stepcircle

import android.content.Context
import android.graphics.Canvas
import android.graphics.Paint
import android.graphics.Rect
import android.graphics.RectF
import android.util.AttributeSet
import android.view.View
import kotlin.properties.Delegates

interface StepCircle {
    fun setStepCounts(count: Int)
}

class StepView @JvmOverloads constructor(context: Context,
                                         attributeSet: AttributeSet? = null,
                                         defStyle: Int = 0) :
        View(context, attributeSet, defStyle),
        StepCircle {

    private var centerX = 40f

    private var resource = context.resources

    private var stepCount by Delegates.notNull<Int>()
    private var currentStep by Delegates.notNull<Int>()

    private var selectedCirclePaint by Delegates.notNull<Paint>()
    private var unSelectedCirclePaint by Delegates.notNull<Paint>()
    private var selectedTextPaint by Delegates.notNull<Paint>()
    private var unSelectedTextPaint by Delegates.notNull<Paint>()


    init {
        initCirclePaint()
        initTextPaint()
        calculate()
    }

    private val stepCenterY: Float
        get() = measuredHeight.toFloat()

    override fun setStepCounts(count: Int) {
        this.stepCount = count
        this.currentStep = 1
    }

    private val stepAreaRect = Rect()
    private val stepAreaRectF = RectF()

    override fun onDraw(canvas: Canvas) {
        super.onDraw(canvas)
        for (i in 0 until currentStep) {
            canvas.drawCircle(centerX, 100f, 60f, selectedCirclePaint)

            stepAreaRect.set((centerX - 60f).toInt(), (100f - 60f).toInt(),
                    (centerX + 60f).toInt(), (100f + 60f).toInt())
            stepAreaRectF.set(stepAreaRect)

            canvas.drawText((i + 1).toString(), centerX, 100f, selectedTextPaint)
            centerX += (60f + 120f)
        }

        for (i in currentStep until stepCount) {
            canvas.drawCircle(centerX, 100f, 60f, unSelectedCirclePaint)
            canvas.drawText((i + 1).toString(), centerX, 100f, unSelectedTextPaint)
            centerX += (60f + 120f)
        }
    }

    private fun initCirclePaint() {
        selectedCirclePaint = Paint().apply {
            strokeWidth = 4f
            style = Paint.Style.FILL
            color = resources.getColor(R.color.lifeGreen)
            isAntiAlias = true
        }

        unSelectedCirclePaint = Paint(selectedCirclePaint).apply {
            style = Paint.Style.FILL
            color = resources.getColor(R.color.lifeGrey)
        }
    }

    private fun initTextPaint() {
        selectedTextPaint = Paint().apply {
            style = Paint.Style.FILL
            color = resources.getColor(R.color.lifeWhite)
            textSize = resource.getDimension(R.dimen.stpi_default_text_medium_size)
            isAntiAlias = true
        }

        unSelectedTextPaint = Paint(selectedTextPaint).apply {
            color = resources.getColor(R.color.lifeGreen)
        }
    }

    private fun calculate() {
        compute()
        invalidate()
    }

    private fun compute() {

    }
}
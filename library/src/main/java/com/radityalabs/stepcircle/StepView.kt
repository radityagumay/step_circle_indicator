package com.radityalabs.stepcircle

import android.animation.AnimatorSet
import android.animation.ObjectAnimator
import android.content.Context
import android.graphics.*
import android.os.Build
import android.support.annotation.UiThread
import android.support.v4.content.ContextCompat
import android.util.AttributeSet
import android.util.Log
import android.util.TypedValue
import android.view.View
import android.view.animation.DecelerateInterpolator
import java.util.*
import kotlin.properties.Delegates

interface StepCircle {
    fun setCircleCount(count: Int)
}

class StepView @JvmOverloads constructor(context: Context,
                                         attributeSet: AttributeSet,
                                         defStyle: Int = 0) : View(context, attributeSet, defStyle), StepCircle {
    companion object {
        private val TAG = StepView::class.java.simpleName

        private const val DEFAULT_ANIM_DURATION = 200
        private const val EXPAND_MARK = 1.3f
    }

    private val resource = context.resources
    private val defaultPrimaryColor = getPrimaryColor()
    private val defaultCircleColor = ContextCompat.getColor(context, R.color.lifeWhite)
    private val defaultCircleRadius = resources.getDimension(R.dimen.stpi_default_circle_radius)
    private val defaultCircleStrokeWidth = resources.getDimension(R.dimen.stpi_default_circle_stroke_width)

    private val defaultIndicatorColor = defaultPrimaryColor
    private val defaultIndicatorRadius = resources.getDimension(R.dimen.stpi_default_indicator_radius)

    private val defaultLineStrokeWidth = resources.getDimension(R.dimen.stpi_default_line_stroke_width)
    private val defaultLineMargin = resources.getDimension(R.dimen.stpi_default_line_margin)
    private val defaultLineColor = ContextCompat.getColor(context, R.color.lifeGrey)

    private val defaultLineDoneColor = defaultPrimaryColor

    private var lineSubset = arrayListOf<Path>()
    private val linePathList = arrayListOf<Path>()
    private var stepsClickAreas = mutableListOf<RectF>()

    private var stepCount by Delegates.notNull<Int>()
    private var currentStep by Delegates.notNull<Int>()
    private var previousStep by Delegates.notNull<Int>()
    private var animDuration by Delegates.notNull<Int>()

    private var indicator by Delegates.notNull<FloatArray>()
    private var circleRadius by Delegates.notNull<Float>()
    private var indicatorRadius by Delegates.notNull<Float>()
    private var animIndicatorRadius by Delegates.notNull<Float>()
    private var lineMargin by Delegates.notNull<Float>()
    private var lineLength by Delegates.notNull<Float>()
    private var animProgress by Delegates.notNull<Float>()

    private var animatorSet: AnimatorSet? = null
    private var lineAnimator: ObjectAnimator? = null
    private var indicatorAnimator: ObjectAnimator? = null

    private lateinit var textPaint: Paint
    private lateinit var circlePaint: Paint
    private lateinit var indicatorPaint: Paint
    private lateinit var linePaint: Paint
    private lateinit var lineFinishPaint: Paint
    private lateinit var lineFinishAnimate: Paint

    private lateinit var stepsCirclePaintList: MutableList<Paint>
    private lateinit var stepsIndicatorPaintList: MutableList<Paint>

    override fun setCircleCount(count: Int) {
        this.stepCount = count
        this.currentStep = 0
        this.previousStep = 0

        initCirclePaint()
        initIndicatorPaint()
        initTextPaint()
        initStepIndicator()
        initLinePaint()
        initDependecies()

        compute()
        invalidate()
    }

    override fun onSizeChanged(w: Int, h: Int, oldw: Int, oldh: Int) {
        compute()
    }

    override fun onDraw(canvas: Canvas?) {
        super.onDraw(canvas)
        val centerY = getStepCenterY()

        val inAnimation = animatorSet != null && animatorSet!!.isRunning
        val inLineAnimation = lineAnimator != null && lineAnimator!!.isRunning
        val inIndicatorAnimation = indicatorAnimator != null && indicatorAnimator!!.isRunning

        val drawToNext = previousStep == currentStep - 1
        val drawFromNext = previousStep == currentStep + 1

        for (i in indicator.indices) {
            val indicator = indicator[i]

            // We draw the "done" check if previous step, or if we are going back (if going back, animated value will reduce radius to 0)
            val drawCheck = i < currentStep || drawFromNext && i == currentStep

            // Draw back circle
            canvas!!.drawCircle(indicator, centerY, circleRadius, getStepCirclePaint(i))

            // Show the current step indicator as bullet
            // If current step, or coming back from next step and still animating
            if (i == currentStep && !drawFromNext || i == previousStep && drawFromNext && inAnimation) {
                // Draw animated indicator
                canvas.drawCircle(indicator, centerY, animIndicatorRadius, getStepIndicatorPaint(i))
            }

            // Draw lines
            if (i < linePathList.size) {
                if (i >= currentStep) {
                    canvas.drawPath(linePathList[i], linePaint)
                    if (i == currentStep && drawFromNext && (inLineAnimation || inIndicatorAnimation)) {
                        // Coming back from n+1
                        canvas.drawPath(linePathList[i], lineFinishAnimate)
                    }
                } else {
                    if (i == currentStep - 1 && drawToNext && inLineAnimation) {
                        // Going to n+1
                        canvas.drawPath(linePathList[i], linePaint)
                        canvas.drawPath(linePathList[i], lineFinishAnimate)
                    } else {
                        canvas.drawPath(linePathList[i], lineFinishPaint)
                    }
                }
            }
        }
    }

    @UiThread
    private fun setCurrentSteps(position: Int) {
        if (position < 0 || position > stepCount) {
            throw IllegalArgumentException("Invalid step value $position")
        }

        this.previousStep = this.currentStep
        this.currentStep = position

        animatorSet?.let { it.cancel() }

        animatorSet = null
        lineAnimator = null
        indicatorAnimator = null

        if (currentStep == previousStep + 1) {
            // for next animation
            animatorSet = AnimatorSet()

            // draw line from n (current) to n + 1
            lineAnimator = ObjectAnimator.ofFloat(this, "animProgress", 1.0f, 0.0f)

            // pop current indicator
            animIndicatorRadius = 0f

            indicatorAnimator = ObjectAnimator.ofFloat(this, "animIndicatorRadius", 0f,
                    indicatorRadius * 1.4f, indicatorRadius)

            animatorSet!!.play(lineAnimator).before(indicatorAnimator)
        }

        animatorSet?.let {
            lineAnimator?.let { line ->
                // Max 500 ms for the animation
                line.duration = Math.min(500, animDuration).toLong()
                line.interpolator = DecelerateInterpolator()
                // Other animations will run 2 times faster that line animation
                indicatorAnimator!!.duration = line.getDuration() / 2
                animatorSet!!.start()
            }
        }
        invalidate()
    }

    private fun initCirclePaint() {
        circlePaint = Paint().apply {
            strokeWidth = 4f
            style = Paint.Style.STROKE
            isAntiAlias = true
        }
    }

    private fun initIndicatorPaint() {
        indicatorPaint = Paint().apply {
            style = Paint.Style.FILL
            color = resource.getColor(R.color.lifeWhite)
            isAntiAlias = true
        }
    }

    private fun initTextPaint() {
        textPaint = Paint(indicatorPaint).apply {
            textSize = resource.getDimension(R.dimen.stpi_default_text_size)
        }
    }

    private fun initStepIndicator() {
        stepsCirclePaintList = ArrayList(stepCount)

        for (i in 0 until stepCount) {
            val circlePaint = Paint(this.circlePaint)
            circlePaint.color = resource.getColor(R.color.lifeGreen)
            stepsCirclePaintList.add(circlePaint)
        }

        stepsIndicatorPaintList = ArrayList(stepCount)

        for (i in 0 until stepCount) {
            val indicatorPaint = Paint(this.indicatorPaint)
            indicatorPaint.color = resource.getColor(R.color.lifeGreen)
            stepsIndicatorPaintList.add(indicatorPaint)
        }
    }

    private fun initLinePaint() {
        linePaint = Paint().apply {
            strokeWidth = 4f
            strokeCap = Paint.Cap.ROUND
            style = Paint.Style.STROKE
            color = resource.getColor(R.color.lifeGrey)
            isAntiAlias = true
        }

        lineFinishPaint = Paint(linePaint).apply {
            color = resource.getColor(R.color.lifeGreen)
        }

        lineFinishAnimate = Paint(lineFinishPaint)
    }

    private fun getStepIndicatorPaint(stepPosition: Int): Paint {
        return getPaint(stepPosition, stepsIndicatorPaintList, indicatorPaint)
    }

    private fun getRandomColor(): Int {
        val rnd = Random()
        return Color.argb(255, rnd.nextInt(256), rnd.nextInt(256), rnd.nextInt(256))
    }

    private fun initDependecies() {
        // better use from attr, so we can override it within xml
        circleRadius = defaultCircleRadius
        indicatorRadius = defaultIndicatorRadius
        animIndicatorRadius = indicatorRadius

        lineMargin = resource.getDimension(R.dimen.stpi_default_line_margin)
        animDuration = DEFAULT_ANIM_DURATION
    }

    private fun compute() {
        indicator = floatArrayOf(stepCount.toFloat())
        lineSubset.clear()

        val startX = (circleRadius * EXPAND_MARK) + (circlePaint.strokeWidth / 2f)
        val divider = (measuredWidth - startX * 2f) / (stepCount - 1)
        lineLength = divider - (circleRadius * 2f + circlePaint.strokeWidth) - (lineMargin * 2)

        for (i in 0 until indicator.size) {
            indicator[i] = startX + divider * i
        }

        for (i in 0 until indicator.size - 1) {
            val position = ((indicator[i] + indicator[i + 1]) / 2) - lineLength / 2
            val linePath = Path()
            val lineY = getStepCenterY()
            linePath.moveTo(position, lineY)
            linePath.lineTo(position + lineLength, lineY)
            linePathList.add(linePath)
        }

        computeStepsClickAreas()
    }

    private fun getStepCirclePaint(stepPosition: Int): Paint {
        return getPaint(stepPosition, stepsCirclePaintList, circlePaint)
    }

    private fun isStepValid(stepPos: Int): Boolean {
        if (stepPos < 0 || stepPos > stepCount - 1) {
            throw IllegalArgumentException("Invalid step position. " + stepPos + " is not a valid position! it " +
                    "should be between 0 and stepCount(" + stepCount + ")")
        }
        return true
    }

    private fun getPaint(stepPosition: Int, sourceList: List<Paint>?, defaultPaint: Paint?): Paint {
        isStepValid(stepPosition) // it will throw an error if not valid

        var paint: Paint? = null
        if (null != sourceList && !sourceList.isEmpty()) {
            try {
                paint = sourceList[stepPosition]
            } catch (e: IndexOutOfBoundsException) {
                // We use an random color as this usually should not happen, maybe in edit mode
                Log.d(TAG, "getPaint: could not find the specific step paint to use! Try to use default instead!")
            }

        }

        if (null == paint && null != defaultPaint) {
            // Try to use the default
            paint = defaultPaint
        }

        if (null == paint) {
            Log.d(TAG, "getPaint: could not use default paint for the specific step! Using random Paint instead!")
            // If we reached this point, not even the default is setup, rely on some random color
            paint = getRandomPaint()
        }

        return paint
    }

    private fun getRandomPaint(): Paint {
        val paint = Paint(indicatorPaint)
        paint.color = getRandomColor()
        return paint
    }

    private fun getStepCenterY(): Float {
        return (measuredHeight.toFloat() - getBottomIndicatorHeight().toFloat() - getMaxLabelHeight()) / 2f
    }

    // return 0 for a moment
    private fun getBottomIndicatorHeight(): Int {
        return 0
    }

    // return 0 for a moment
    private fun getMaxLabelHeight(): Float {
        return 0f
    }

    private fun computeStepsClickAreas() {
        stepsClickAreas = ArrayList(stepCount)

        // Compute the clicked area for each step
        for (indicator in indicator) {
            // Get the indicator position
            // Calculate the bounds for the step
            val left = indicator - circleRadius * 2
            val right = indicator + circleRadius * 2
            val top = getStepCenterY() - circleRadius * 2
            val bottom = getStepCenterY() + circleRadius + getBottomIndicatorHeight().toFloat()

            // Store the click area for the step
            val area = RectF(left, top, right, bottom)
            stepsClickAreas.add(area)
        }
    }

    private fun getPrimaryColor(): Int {
        var color = resource.getIdentifier("colorPrimary", "attr", context.packageName)
        when {
            color != 0 -> {
                val t = TypedValue()
                context.theme.resolveAttribute(color, t, true)
                color = t.data
            }
            Build.VERSION.SDK_INT >= Build.VERSION_CODES.LOLLIPOP -> {
                val t = context.obtainStyledAttributes(intArrayOf(android.R.attr.colorPrimary))
                color = t.getColor(0, ContextCompat.getColor(context, R.color.lifeGreen))
                t.recycle()
            }
            else -> {
                val t = context.obtainStyledAttributes(intArrayOf(R.attr.colorPrimary))
                color = t.getColor(0, ContextCompat.getColor(context, R.color.lifeGreen))
                t.recycle()
            }
        }
        return color
    }
}
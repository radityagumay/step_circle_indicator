package com.radityalabs.stepcircle

import android.animation.AnimatorSet
import android.animation.ObjectAnimator
import android.annotation.TargetApi
import android.content.Context
import android.graphics.*
import android.os.Build
import android.os.Parcel
import android.os.Parcelable
import android.support.annotation.UiThread
import android.support.v4.content.ContextCompat
import android.text.Layout
import android.text.StaticLayout
import android.text.TextPaint
import android.util.AttributeSet
import android.util.Log
import android.util.TypedValue
import android.view.GestureDetector
import android.view.MotionEvent
import android.view.View
import android.view.animation.DecelerateInterpolator
import java.util.*

class StepperIndicatorView : View {
    companion object {
        private val TAG = "StepperIndicatorView"
        private const val DEFAULT_ANIMATION_DURATION = 200
        private const val EXPAND_MARK = 1.3f
        private const val STEP_INVALID = -1
    }

    private var circlePaint: Paint? = null
    private var stepsCirclePaintList: MutableList<Paint>? = null
    private var circleRadius: Float = 0.toFloat()

    private var showStepTextNumber: Boolean = false
    private var stepTextNumberPaint: Paint? = null

    private var stepsTextNumberPaintList: MutableList<Paint>? = null

    private var indicatorPaint: Paint? = null
    private var stepsIndicatorPaintList: MutableList<Paint>? = null
    private var linePaint: Paint? = null
    private var lineDonePaint: Paint? = null
    private var lineDoneAnimatedPaint: Paint? = null

    private val linePathList = ArrayList<Path>()
    private var animProgress: Float = 0.toFloat()
    private var animIndicatorRadius: Float = 0.toFloat()
    private var animCheckRadius: Float = 0.toFloat()
    private var useBottomIndicator: Boolean = false
    private var bottomIndicatorMarginTop = 0f
    private var bottomIndicatorWidth = 0f

    private var bottomIndicatorHeight = 0f
    private var useBottomIndicatorWithStepColors: Boolean = false
    private var lineLength: Float = 0.toFloat()

    private var checkRadius: Float = 0.toFloat()
    private var indicatorRadius: Float = 0.toFloat()
    private var lineMargin: Float = 0.toFloat()
    private var animDuration: Int = 0

    private val onStepClickListeners = ArrayList<OnStepClickListener>(0)
    private var stepsClickAreas: MutableList<RectF>? = null

    private var gestureDetector: GestureDetector? = null
    private var stepCount: Int = 0
    private var currentStep: Int = 0
    private var previousStep: Int = 0

    private var indicators: FloatArray? = null
    private val stepAreaRect = Rect()
    private val stepAreaRectF = RectF()

    private var showDoneIcon: Boolean = false

    private var labelPaint: TextPaint? = null
    private var labels: Array<CharSequence>? = null
    private var showLabels: Boolean = false
    private var labelMarginTop: Float = 0.toFloat()
    private var labelSize: Float = 0.toFloat()
    private var labelLayouts = mutableListOf<StaticLayout>()
    private var maxLabelHeight: Float = 0.toFloat()

    private var animatorSet: AnimatorSet? = null
    private var lineAnimator: ObjectAnimator? = null
    private var indicatorAnimator: ObjectAnimator? = null
    private var checkAnimator: ObjectAnimator? = null

    private val gestureListener = object : GestureDetector.SimpleOnGestureListener() {
        override fun onSingleTapConfirmed(e: MotionEvent): Boolean {
            var clickedStep = STEP_INVALID
            for (i in stepsClickAreas!!.indices) {
                if (stepsClickAreas!![i].contains(e.x, e.y)) {
                    clickedStep = i
                    break
                }
            }
            if (clickedStep != STEP_INVALID) {
                setCurrentStep(clickedStep)
                for (listener in onStepClickListeners) {
                    listener.onStepClicked(clickedStep)
                }
            }
            return super.onSingleTapConfirmed(e)
        }
    }

    private val randomPaint: Paint
        get() {
            val paint = Paint(indicatorPaint)
            paint.color = randomColor
            return paint
        }

    private val randomColor: Int
        get() {
            val rnd = Random()
            return Color.argb(255, rnd.nextInt(256), rnd.nextInt(256), rnd.nextInt(256))
        }

    private val stepCenterY: Float
        get() = (measuredHeight.toFloat() - getBottomIndicatorHeight().toFloat() - getMaxLabelHeight()) / 2f

    @JvmOverloads constructor(context: Context, attrs: AttributeSet? = null, defStyleAttr: Int = 0) : super(context, attrs, defStyleAttr) {
        init(context, attrs, defStyleAttr)
    }

    @TargetApi(Build.VERSION_CODES.LOLLIPOP)
    constructor(context: Context, attrs: AttributeSet, defStyleAttr: Int, defStyleRes: Int) : super(context, attrs, defStyleAttr, defStyleRes) {
        init(context, attrs, defStyleAttr)
    }

    private fun init(context: Context, attrs: AttributeSet?, defStyleAttr: Int) {
        val resources = resources
        val defaultPrimaryColor = getPrimaryColor(context)
        val defaultCircleRadius = resources.getDimension(R.dimen.circle_size)
        val defaultIndicatorRadius = resources.getDimension(R.dimen.stpi_default_indicator_radius)
        val defaultLineMargin = resources.getDimension(R.dimen.stpi_default_line_margin)

        val a = context.obtainStyledAttributes(attrs, R.styleable.StepperIndicatorView, defStyleAttr, 0)

        circlePaint = Paint()
        circlePaint!!.strokeWidth = 4f //a.getDimension(R.styleable.StepperIndicatorView_stpi_circleStrokeWidth, defaultCircleStrokeWidth)
        circlePaint!!.style = Paint.Style.STROKE
        circlePaint!!.color = resources.getColor(R.color.lifeGreen) //a.getColor(R.styleable.StepperIndicatorView_stpi_circleColor, defaultCircleColor)
        circlePaint!!.isAntiAlias = true

        setStepCount(a.getInteger(R.styleable.StepperIndicatorView_stpi_stepCount, 2))

        val stepsCircleColorsResId = a.getResourceId(R.styleable.StepperIndicatorView_stpi_stepsCircleColors, 0)
        if (stepsCircleColorsResId != 0) {
            stepsCirclePaintList = ArrayList(stepCount)
            for (i in 0 until stepCount) {
                val circlePaint = Paint(this.circlePaint)
                circlePaint.color = resources.getColor(R.color.lifeGreen)
                stepsCirclePaintList!!.add(circlePaint)
            }
        }

        indicatorPaint = Paint(circlePaint)
        indicatorPaint!!.style = Paint.Style.FILL
        indicatorPaint!!.color = resources.getColor(R.color.lifeGreen)
        indicatorPaint!!.isAntiAlias = true

        stepTextNumberPaint = Paint(indicatorPaint)
        stepTextNumberPaint!!.textSize = getResources().getDimension(R.dimen.stpi_default_text_size)

        showStepTextNumber = a.getBoolean(R.styleable.StepperIndicatorView_stpi_showStepNumberInstead, false)

        val stepsIndicatorColorsResId = a.getResourceId(R.styleable.StepperIndicatorView_stpi_stepsIndicatorColors, 0)
        if (stepsIndicatorColorsResId != 0) {
            stepsIndicatorPaintList = ArrayList(stepCount)
            if (showStepTextNumber) {
                stepsTextNumberPaintList = ArrayList(stepCount)
            }

            for (i in 0 until stepCount) {
                val indicatorPaint = Paint(this.indicatorPaint)

                val textNumberPaint = if (showStepTextNumber) Paint(stepTextNumberPaint) else null
                if (isInEditMode) {
                    indicatorPaint.color = randomColor // random color
                    if (null != textNumberPaint) {
                        textNumberPaint.color = indicatorPaint.color
                    }
                } else {
                    val colorResValues = context.resources.obtainTypedArray(stepsIndicatorColorsResId)

                    if (stepCount > colorResValues.length()) {
                        throw IllegalArgumentException(
                                "Invalid number of colors for the indicators. Please provide a list " + "of colors with as many items as the number of steps required!")
                    }

                    indicatorPaint.color = colorResValues.getColor(i, 0) // specific color
                    if (null != textNumberPaint) {
                        textNumberPaint.color = indicatorPaint.color
                    }
                    // No need for the array anymore, recycle it
                    colorResValues.recycle()
                }

                stepsIndicatorPaintList!!.add(indicatorPaint)
                if (showStepTextNumber && null != textNumberPaint) {
                    stepsTextNumberPaintList!!.add(textNumberPaint)
                }
            }
        }

        linePaint = Paint()
        linePaint!!.strokeWidth = a.getDimension(R.styleable.StepperIndicatorView_stpi_lineStrokeWidth, 4f)
        linePaint!!.strokeCap = Paint.Cap.ROUND
        linePaint!!.style = Paint.Style.STROKE
        linePaint!!.color = resources.getColor(R.color.lifeGrey)
        linePaint!!.isAntiAlias = true

        lineDonePaint = Paint(linePaint)
        lineDonePaint!!.color = a.getColor(R.styleable.StepperIndicatorView_stpi_lineDoneColor, defaultPrimaryColor)

        lineDoneAnimatedPaint = Paint(lineDonePaint)

        circleRadius = a.getDimension(R.styleable.StepperIndicatorView_stpi_circleRadius, defaultCircleRadius)
        checkRadius = circleRadius + circlePaint!!.strokeWidth / 2f
        indicatorRadius = a.getDimension(R.styleable.StepperIndicatorView_stpi_indicatorRadius, defaultIndicatorRadius)
        animIndicatorRadius = indicatorRadius
        animCheckRadius = checkRadius
        lineMargin = a.getDimension(R.styleable.StepperIndicatorView_stpi_lineMargin, defaultLineMargin)

        animDuration = a.getInteger(R.styleable.StepperIndicatorView_stpi_animDuration, DEFAULT_ANIMATION_DURATION)
        showDoneIcon = a.getBoolean(R.styleable.StepperIndicatorView_stpi_showDoneIcon, true)

        // Labels Configuration
        labelPaint = TextPaint(Paint.ANTI_ALIAS_FLAG)
        labelPaint!!.textAlign = Paint.Align.CENTER

        val defaultLabelSize = resources.getDimension(R.dimen.stpi_default_label_size)
        labelSize = a.getDimension(R.styleable.StepperIndicatorView_stpi_labelSize, defaultLabelSize)
        labelPaint!!.textSize = labelSize

        val defaultLabelMarginTop = resources.getDimension(R.dimen.stpi_default_label_margin_top)
        labelMarginTop = a.getDimension(R.styleable.StepperIndicatorView_stpi_labelMarginTop, defaultLabelMarginTop)

        showLabels(a.getBoolean(R.styleable.StepperIndicatorView_stpi_showLabels, false))
        setLabels(a.getTextArray(R.styleable.StepperIndicatorView_stpi_labels))

        if (a.hasValue(R.styleable.StepperIndicatorView_stpi_labelColor)) {
            setLabelColor(a.getColor(R.styleable.StepperIndicatorView_stpi_labelColor, 0))
        } else {
            setLabelColor(getTextColorSecondary(getContext()))
        }

        if (isInEditMode && showLabels && labels == null) {
            labels = arrayOf("First", "Second", "Third", "Fourth", "Fifth")
        }

        if (!a.hasValue(R.styleable.StepperIndicatorView_stpi_stepCount) && labels != null) {
            setStepCount(labels!!.size)
        }

        a.recycle()

        // Display at least 1 cleared step for preview in XML editor
        if (isInEditMode) {
            currentStep = Math.max(Math.ceil((stepCount / 2f).toDouble()).toInt(), 1)
        }

        // Initialize the gesture detector, setup with our custom gesture listener
        gestureDetector = GestureDetector(getContext(), gestureListener)
    }

    override fun onTouchEvent(event: MotionEvent): Boolean {
        gestureDetector!!.onTouchEvent(event)
        return true
    }

    override fun onSizeChanged(w: Int, h: Int, oldw: Int, oldh: Int) {
        compute()
    }

    override fun onDraw(canvas: Canvas) {
        val centerY = stepCenterY
        val inAnimation = animatorSet != null && animatorSet!!.isRunning
        val inLineAnimation = lineAnimator != null && lineAnimator!!.isRunning
        val inIndicatorAnimation = indicatorAnimator != null && indicatorAnimator!!.isRunning
        val inCheckAnimation = checkAnimator != null && checkAnimator!!.isRunning

        val drawToNext = previousStep == currentStep - 1
        val drawFromNext = previousStep == currentStep + 1

        for (i in indicators!!.indices) {
            val indicator = indicators!![i]
            val drawCheck = i < currentStep || drawFromNext && i == currentStep
            canvas.drawCircle(indicator, centerY, circleRadius, getStepCirclePaint(i))
            val stepLabel = (i + 1).toString()

            stepAreaRect.set((indicator - circleRadius).toInt(), (centerY - circleRadius).toInt(),
                    (indicator + circleRadius).toInt(), (centerY + circleRadius).toInt())
            stepAreaRectF.set(stepAreaRect)

            // draw counter
            val stepTextNumberPaint = getStepTextNumberPaint(i)
            stepAreaRectF.right = stepTextNumberPaint.measureText(stepLabel, 0, stepLabel.length)
            stepAreaRectF.bottom = stepTextNumberPaint.descent() - stepTextNumberPaint.ascent()
            stepAreaRectF.left += (stepAreaRect.width() - stepAreaRectF.right) / 2.0f
            stepAreaRectF.top += (stepAreaRect.height() - stepAreaRectF.bottom) / 2.0f
            canvas.drawText(stepLabel, stepAreaRectF.left, stepAreaRectF.top - stepTextNumberPaint.ascent(), stepTextNumberPaint)

            if (i == currentStep && !drawFromNext || i == previousStep && drawFromNext && inAnimation) {
                canvas.drawCircle(indicator, centerY, animIndicatorRadius, getStepIndicatorPaint(i))
            }

            if (drawCheck) {
                var radius = checkRadius
                if (i == previousStep && drawToNext || i == currentStep && drawFromNext) radius = animCheckRadius
                canvas.drawCircle(indicator, centerY, radius, getStepIndicatorPaint(i))
            }

            if (i < linePathList.size) {
                if (i >= currentStep) {
                    canvas.drawPath(linePathList[i], linePaint!!)
                    if (i == currentStep && drawFromNext && (inLineAnimation || inIndicatorAnimation)) {
                        // Coming back from n+1
                        canvas.drawPath(linePathList[i], lineDoneAnimatedPaint!!)
                    }
                } else {
                    if (i == currentStep - 1 && drawToNext && inLineAnimation) {
                        // Going to n+1
                        canvas.drawPath(linePathList[i], linePaint!!)
                        canvas.drawPath(linePathList[i], lineDoneAnimatedPaint!!)
                    } else {
                        canvas.drawPath(linePathList[i], lineDonePaint!!)
                    }
                }
            }
        }
    }

    private fun compute() {
        if (null == circlePaint) {
            throw IllegalArgumentException("circlePaint is invalid! Make sure you setup the field circlePaint " + "before calling compute() method!")
        }

        indicators = FloatArray(stepCount)
        linePathList.clear()

        var startX = circleRadius * EXPAND_MARK + circlePaint!!.strokeWidth / 2f
        if (useBottomIndicator) {
            startX = bottomIndicatorWidth / 2f
        }
        if (showLabels) {
            // gridWidth is the width of the grid assigned for the step indicator
            val gridWidth = measuredWidth / stepCount
            startX = gridWidth / 2f
        }

        // Compute position of indicators and line length
        val divider = (measuredWidth - startX * 2f) / (stepCount - 1)
        lineLength = divider - (circleRadius * 2f + circlePaint!!.strokeWidth) - lineMargin * 2

        // Compute position of circles and lines once
        for (i in indicators!!.indices) {
            indicators!![i] = startX + divider * i
        }
        for (i in 0 until indicators!!.size - 1) {
            val position = (indicators!![i] + indicators!![i + 1]) / 2 - lineLength / 2
            val linePath = Path()
            val lineY = stepCenterY
            linePath.moveTo(position, lineY)
            linePath.lineTo(position + lineLength, lineY)
            linePathList.add(linePath)
        }

        computeStepsClickAreas() // update the position of the steps click area also
    }

    fun computeStepsClickAreas() {
        if (stepCount == STEP_INVALID) {
            throw IllegalArgumentException("stepCount wasn't setup yet. Make sure you call setStepCount() " + "before computing the steps click area!")
        }

        if (null == indicators) {
            throw IllegalArgumentException("indicators wasn't setup yet. Make sure the indicators are " +
                    "initialized and setup correctly before trying to compute the click " +
                    "area for each step!")
        }

        stepsClickAreas = ArrayList(stepCount)
        for (indicator in indicators!!) {
            val left = indicator - circleRadius * 2
            val right = indicator + circleRadius * 2
            val top = stepCenterY - circleRadius * 2
            val bottom = stepCenterY + circleRadius
            val area = RectF(left, top, right, bottom)
            stepsClickAreas!!.add(area)
        }
    }

    private fun getBottomIndicatorHeight(): Int {
        return if (useBottomIndicator) {
            (bottomIndicatorHeight + bottomIndicatorMarginTop).toInt()
        } else {
            0
        }
    }

    private fun getMaxLabelHeight(): Float {
        return if (showLabels) maxLabelHeight + labelMarginTop else 0f
    }

    private fun calculateMaxLabelHeight(measuredWidth: Int) {
        if (!showLabels) return
        val twoDp = context.resources.getDimensionPixelSize(R.dimen.stpi_two_dp)
        val gridWidth = measuredWidth / stepCount - twoDp

        if (gridWidth <= 0) return
        maxLabelHeight = 0f
    }

    private fun getStepIndicatorPaint(stepPosition: Int): Paint {
        return getPaint(stepPosition, stepsIndicatorPaintList, indicatorPaint)
    }

    private fun getStepTextNumberPaint(stepPosition: Int): Paint {
        return getPaint(stepPosition, stepsTextNumberPaintList, stepTextNumberPaint)
    }

    private fun getStepCirclePaint(stepPosition: Int): Paint {
        return getPaint(stepPosition, stepsCirclePaintList, circlePaint)
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
            paint = randomPaint
        }

        return paint
    }

    private fun isStepValid(stepPos: Int): Boolean {
        if (stepPos < 0 || stepPos > stepCount - 1) {
            throw IllegalArgumentException("Invalid step position. " + stepPos + " is not a valid position! it " +
                    "should be between 0 and stepCount(" + stepCount + ")")
        }

        return true
    }

    override fun onMeasure(widthMeasureSpec: Int, heightMeasureSpec: Int) {
        val widthMode = View.MeasureSpec.getMode(widthMeasureSpec)
        val widthSize = View.MeasureSpec.getSize(widthMeasureSpec)

        val width = if (widthMode == View.MeasureSpec.EXACTLY) widthSize else suggestedMinimumWidth

        calculateMaxLabelHeight(width)

        // Compute the necessary height for the widget
        val desiredHeight = Math.ceil(
                (circleRadius * EXPAND_MARK * 2f +
                        circlePaint!!.strokeWidth +
                        getBottomIndicatorHeight().toFloat() +
                        getMaxLabelHeight()).toDouble()
        ).toInt()

        val heightMode = View.MeasureSpec.getMode(heightMeasureSpec)
        val heightSize = View.MeasureSpec.getSize(heightMeasureSpec)
        val height = if (heightMode == View.MeasureSpec.EXACTLY) heightSize else desiredHeight

        setMeasuredDimension(width, height)
    }

    fun getStepCount(): Int {
        return stepCount
    }

    fun setStepCount(stepCount: Int) {
        if (stepCount < 2) {
            throw IllegalArgumentException("stepCount must be >= 2")
        }

        this.stepCount = stepCount
        currentStep = 0
        compute()
        invalidate()
    }

    fun getCurrentStep(): Int {
        return currentStep
    }

    @UiThread
    fun setCurrentStep(currentStep: Int) {
        if (currentStep < 0 || currentStep > stepCount) {
            throw IllegalArgumentException("Invalid step value $currentStep")
        }

        previousStep = this.currentStep
        this.currentStep = currentStep

        // Cancel any running animations
        if (animatorSet != null) {
            animatorSet!!.cancel()
        }

        animatorSet = null
        lineAnimator = null
        indicatorAnimator = null

        // TODO: 05/08/16 handle cases where steps are skipped - need to animate all of them

        if (currentStep == previousStep + 1) {
            // Going to next step
            animatorSet = AnimatorSet()

            // First, draw line to new
            lineAnimator = ObjectAnimator.ofFloat(this, "animProgress", 1.0f, 0.0f)

            // Same time, pop check mark
            checkAnimator = ObjectAnimator.ofFloat(this, "animCheckRadius", indicatorRadius,
                    checkRadius * EXPAND_MARK, checkRadius)

            // Finally, pop current step indicator
            animIndicatorRadius = 0f
            indicatorAnimator = ObjectAnimator.ofFloat(this, "animIndicatorRadius", 0f,
                    indicatorRadius * 1.4f, indicatorRadius)

            animatorSet!!.play(lineAnimator).with(checkAnimator).before(indicatorAnimator)
        } else if (currentStep == previousStep - 1) {
            // Going back to previous step
            animatorSet = AnimatorSet()

            // First, pop out current step indicator
            indicatorAnimator = ObjectAnimator
                    .ofFloat(this, "animIndicatorRadius", indicatorRadius, 0f)

            // Then delete line
            animProgress = 1.0f
            lineDoneAnimatedPaint!!.pathEffect = null
            lineAnimator = ObjectAnimator.ofFloat(this, "animProgress", 0.0f, 1.0f)

            // Finally, pop out check mark to display step indicator
            animCheckRadius = checkRadius
            checkAnimator = ObjectAnimator
                    .ofFloat(this, "animCheckRadius", checkRadius, indicatorRadius)

            animatorSet!!.playSequentially(indicatorAnimator, lineAnimator, checkAnimator)
        }

        if (animatorSet != null) {
            // Max 500 ms for the animation
            lineAnimator!!.duration = Math.min(500, animDuration).toLong()
            lineAnimator!!.interpolator = DecelerateInterpolator()
            // Other animations will run 2 times faster that line animation
            indicatorAnimator!!.duration = lineAnimator!!.duration / 2
            checkAnimator!!.duration = lineAnimator!!.duration / 2

            animatorSet!!.start()
        }

        invalidate()
    }

    fun setAnimProgress(animProgress: Float) {
        this.animProgress = animProgress
        lineDoneAnimatedPaint!!.pathEffect = createPathEffect(lineLength, animProgress, 0.0f)
        invalidate()
    }

    fun setAnimIndicatorRadius(animIndicatorRadius: Float) {
        this.animIndicatorRadius = animIndicatorRadius
        invalidate()
    }

    fun setAnimCheckRadius(animCheckRadius: Float) {
        this.animCheckRadius = animCheckRadius
        invalidate()
    }

    fun setLabels(labelsArray: Array<CharSequence>?) {
        if (labelsArray == null) {
            labels = null
            return
        }
        if (stepCount > labelsArray.size) {
            throw IllegalArgumentException("")
        }
        labels = labelsArray
        showLabels(true)
    }

    fun setLabelColor(color: Int) {
        labelPaint!!.color = color
        requestLayout()
        invalidate()
    }

    fun showLabels(show: Boolean) {
        showLabels = show
        requestLayout()
        invalidate()
    }

    fun addOnStepClickListener(listener: OnStepClickListener) {
        onStepClickListeners.add(listener)
    }

    fun removeOnStepClickListener(listener: OnStepClickListener) {
        onStepClickListeners.remove(listener)
    }

    fun clearOnStepClickListeners() {
        onStepClickListeners.clear()
    }

    public override fun onRestoreInstanceState(state: Parcelable) {
        val savedState = state as SavedState
        super.onRestoreInstanceState(savedState.superState)
        currentStep = savedState.mCurrentStep
        requestLayout()
    }

    override fun onSaveInstanceState(): Parcelable? {
        val superState = super.onSaveInstanceState()
        val savedState = SavedState(superState)
        savedState.mCurrentStep = currentStep
        return savedState
    }

    private fun getPrimaryColor(context: Context): Int {
        var color = context.resources.getIdentifier("colorPrimary", "attr", context.packageName)
        when {
            color != 0 -> {
                val t = TypedValue()
                context.theme.resolveAttribute(color, t, true)
                color = t.data
            }
            Build.VERSION.SDK_INT >= Build.VERSION_CODES.LOLLIPOP -> {
                val t = context.obtainStyledAttributes(intArrayOf(android.R.attr.colorPrimary))
                color = t.getColor(0, ContextCompat.getColor(context, R.color.stpi_default_primary_color))
                t.recycle()
            }
            else -> {
                val t = context.obtainStyledAttributes(intArrayOf(R.attr.colorPrimary))
                color = t.getColor(0, ContextCompat.getColor(context, R.color.stpi_default_primary_color))
                t.recycle()
            }
        }

        return color
    }

    private fun getTextColorSecondary(context: Context): Int {
        val t = context.obtainStyledAttributes(intArrayOf(android.R.attr.textColorSecondary))
        val color = t.getColor(0, ContextCompat.getColor(context, R.color.stpi_default_text_color))
        t.recycle()
        return color
    }

    private fun createPathEffect(pathLength: Float, phase: Float, offset: Float): PathEffect {
        return DashPathEffect(floatArrayOf(pathLength, pathLength), Math.max(phase * pathLength, offset))
    }

    private fun drawLayout(layout: Layout, x: Float, y: Float,
                           canvas: Canvas, paint: TextPaint?) {
        canvas.save()
        canvas.translate(x, y)
        layout.draw(canvas)
        canvas.restore()
    }

    private class SavedState : View.BaseSavedState {
        companion object {
            val CREATOR: Parcelable.Creator<SavedState> = object : Parcelable.Creator<SavedState> {
                override fun createFromParcel(`in`: Parcel): SavedState {
                    return SavedState(`in`)
                }

                override fun newArray(size: Int): Array<SavedState?> {
                    return arrayOfNulls(size)
                }
            }
        }

        var mCurrentStep: Int = 0

        constructor(superState: Parcelable) : super(superState)

        private constructor(`in`: Parcel) : super(`in`) {
            mCurrentStep = `in`.readInt()
        }

        override fun writeToParcel(dest: Parcel, flags: Int) {
            super.writeToParcel(dest, flags)
            dest.writeInt(mCurrentStep)
        }
    }

    interface OnStepClickListener {
        fun onStepClicked(step: Int)
    }
}

package com.github.donglua.layoutx2c.demo

import android.content.Context
import android.graphics.Color
import android.util.AttributeSet
import android.view.View

class SyntheticAttrsPriceView @JvmOverloads constructor(
    context: Context,
    attrs: AttributeSet? = null,
    defStyleAttr: Int = 0
) : View(context, attrs, defStyleAttr) {

    val constructorPriceColor: Int
    val constructorPriceFormat: String?

    var appliedPriceColor: Int = Color.BLACK
        private set
    var appliedPriceFormat: String? = null
        private set

    init {
        val typedArray = context.obtainStyledAttributes(attrs, R.styleable.SyntheticAttrsPriceView)
        try {
            constructorPriceColor = typedArray.getColor(
                R.styleable.SyntheticAttrsPriceView_priceColor,
                Color.BLACK
            )
            constructorPriceFormat = typedArray.getString(R.styleable.SyntheticAttrsPriceView_priceFormat)
        } finally {
            typedArray.recycle()
        }
        appliedPriceColor = constructorPriceColor
        appliedPriceFormat = constructorPriceFormat
    }

    fun setPriceColor(value: Int) {
        appliedPriceColor = value
    }

    fun setPriceFormat(value: String) {
        appliedPriceFormat = value
    }
}

package com.github.donglua.layoutx2c.codegen

import com.github.donglua.layoutx2c.analyzer.AnalyzedNode
import com.github.donglua.layoutx2c.parser.isEditText
import com.github.donglua.layoutx2c.parser.isImageView
import com.github.donglua.layoutx2c.parser.isLinearLayout
import com.github.donglua.layoutx2c.parser.isScrollView
import com.github.donglua.layoutx2c.parser.isTextLikeView
import com.squareup.kotlinpoet.ClassName
import com.squareup.kotlinpoet.CodeBlock

interface AttrEmitter {
    fun emit(builder: CodeBlock.Builder, node: AnalyzedNode)
}

class DefaultAttrEmitter : AttrEmitter {

    override fun emit(builder: CodeBlock.Builder, node: AnalyzedNode) {
        val attrs = node.node.attributes

        attrs["android:id"]?.let { idValue ->
            val idName = idValue.removePrefix("@+id/").removePrefix("@id/")
            builder.addStatement("id = R.id.%L", idName)
        }

        attrs["android:orientation"]?.takeIf { node.node.isLinearLayout() }?.let { value ->
            val orientation = if (value == "horizontal") "HORIZONTAL" else "VERTICAL"
            builder.addStatement(
                "orientation = %T.%L",
                ClassName("android.widget", "LinearLayout"),
                orientation
            )
        }

        attrs["android:visibility"]?.let { value ->
            val visibility = when (value) {
                "gone" -> "GONE"
                "invisible" -> "INVISIBLE"
                else -> "VISIBLE"
            }
            builder.addStatement(
                "visibility = %T.%L",
                ClassName("android.view", "View"),
                visibility
            )
        }

        attrs["android:background"]?.let { value ->
            emitBackground(builder, value)
        }

        if (node.node.isTextLikeView()) {
            emitTextAttrs(builder, attrs)
        }

        if (node.node.isEditText()) {
            emitEditTextAttrs(builder, attrs)
        }

        if (node.node.isImageView()) {
            emitImageAttrs(builder, attrs)
        }

        emitCommonStateAttrs(builder, attrs)
        emitPadding(builder, attrs)

        attrs["android:gravity"]?.takeIf { node.node.isLinearLayout() || node.node.isTextLikeView() }?.let { value ->
            builder.addStatement("gravity = %L", gravityToCode(value))
        }

        attrs["android:fillViewport"]?.takeIf { node.node.isScrollView() && it == "true" }?.let {
            builder.addStatement("isFillViewport = true")
        }
    }

    private fun emitTextAttrs(builder: CodeBlock.Builder, attrs: Map<String, String>) {
        attrs["android:text"]?.let { value ->
            if (value.startsWith("@string/")) {
                val resName = value.removePrefix("@string/")
                builder.addStatement("text = context.getString(R.string.%L)", resName)
            } else {
                builder.addStatement("text = %S", value)
            }
        }

        attrs["android:textColor"]?.let { value ->
            emitColorAssignment(builder, value, "setTextColor")
        }

        attrs["android:textSize"]?.let { value ->
            builder.addStatement("setTextSize(%T.COMPLEX_UNIT_PX, %L)", ClassName("android.util", "TypedValue"), dimensionToPxFloatCode(value))
        }

        attrs["android:textStyle"]?.let { value ->
            builder.addStatement("setTypeface(typeface, %L)", textStyleToCode(value))
        }
    }

    private fun emitEditTextAttrs(builder: CodeBlock.Builder, attrs: Map<String, String>) {
        attrs["android:hint"]?.let { value ->
            if (value.startsWith("@string/")) {
                val resName = value.removePrefix("@string/")
                builder.addStatement("hint = context.getString(R.string.%L)", resName)
            } else {
                builder.addStatement("hint = %S", value)
            }
        }

        attrs["android:inputType"]?.let { value ->
            builder.addStatement("inputType = %L", inputTypeToCode(value))
        }
    }

    private fun emitCommonStateAttrs(builder: CodeBlock.Builder, attrs: Map<String, String>) {
        attrs["android:enabled"]?.let { value ->
            builder.addStatement("isEnabled = %L", value == "true")
        }

        attrs["android:clickable"]?.let { value ->
            builder.addStatement("isClickable = %L", value == "true")
        }

        attrs["android:focusable"]?.let { value ->
            builder.addStatement("isFocusable = %L", value == "true")
        }

        attrs["android:elevation"]?.let { value ->
            builder.addStatement("elevation = %L", dimensionToPxFloatCode(value))
        }

        attrs["android:minWidth"]?.let { value ->
            builder.addStatement("minimumWidth = %L", dimensionToCode(value))
        }

        attrs["android:minHeight"]?.let { value ->
            builder.addStatement("minimumHeight = %L", dimensionToCode(value))
        }
    }

    private fun emitPadding(builder: CodeBlock.Builder, attrs: Map<String, String>) {
        val left = attrs["android:paddingLeft"] ?: attrs["android:paddingStart"] ?: attrs["android:padding"]
        val top = attrs["android:paddingTop"] ?: attrs["android:padding"]
        val right = attrs["android:paddingRight"] ?: attrs["android:paddingEnd"] ?: attrs["android:padding"]
        val bottom = attrs["android:paddingBottom"] ?: attrs["android:padding"]

        if (left != null || top != null || right != null || bottom != null) {
            builder.addStatement(
                "setPadding(%L, %L, %L, %L)",
                dimensionToCode(left ?: "0dp"),
                dimensionToCode(top ?: "0dp"),
                dimensionToCode(right ?: "0dp"),
                dimensionToCode(bottom ?: "0dp")
            )
        }
    }

    private fun emitImageAttrs(builder: CodeBlock.Builder, attrs: Map<String, String>) {
        attrs["android:src"]?.let { value ->
            if (value.startsWith("@drawable/")) {
                val resName = value.removePrefix("@drawable/")
                builder.addStatement("setImageResource(R.drawable.%L)", resName)
            }
        }

        attrs["android:scaleType"]?.let { value ->
            ImageScaleTypes.enumName(value)?.let { scaleType ->
                builder.addStatement("scaleType = %T.ScaleType.%L", ClassName("android.widget", "ImageView"), scaleType)
            }
        }

        (attrs["app:tint"] ?: attrs["android:tint"])?.let { value ->
            if (value.startsWith("@color/")) {
                val resName = value.removePrefix("@color/")
                builder.addStatement(
                    "imageTintList = %T.getColorStateList(context, R.color.%L)",
                    ClassName("androidx.core.content", "ContextCompat"),
                    resName
                )
            }
        }
    }

    private fun emitBackground(builder: CodeBlock.Builder, value: String) {
        when {
            value.startsWith("@drawable/") -> {
                val resName = value.removePrefix("@drawable/")
                builder.addStatement("setBackgroundResource(R.drawable.%L)", resName)
            }
            value.startsWith("@color/") -> {
                val resName = value.removePrefix("@color/")
                builder.addStatement("setBackgroundColor(%T.getColor(context, R.color.%L))", ClassName("androidx.core.content", "ContextCompat"), resName)
            }
            value.startsWith("#") -> {
                builder.addStatement("setBackgroundColor(%T.parseColor(%S))", ClassName("android.graphics", "Color"), value)
            }
        }
    }

    private fun emitColorAssignment(builder: CodeBlock.Builder, value: String, methodName: String) {
        when {
            value.startsWith("@color/") -> {
                val resName = value.removePrefix("@color/")
                builder.addStatement("%L(%T.getColor(context, R.color.%L))", methodName, ClassName("androidx.core.content", "ContextCompat"), resName)
            }
            value.startsWith("#") -> {
                builder.addStatement("%L(%T.parseColor(%S))", methodName, ClassName("android.graphics", "Color"), value)
            }
        }
    }

    private fun textStyleToCode(value: String): String {
        val styles = value.split("|").map { it.trim() }.toSet()
        return when {
            "bold" in styles && "italic" in styles -> "android.graphics.Typeface.BOLD_ITALIC"
            "bold" in styles -> "android.graphics.Typeface.BOLD"
            "italic" in styles -> "android.graphics.Typeface.ITALIC"
            else -> "android.graphics.Typeface.NORMAL"
        }
    }

    private fun inputTypeToCode(value: String): String {
        val parts = value.split("|").map { it.trim() }
        return parts.joinToString(" or ") { part ->
            when (part) {
                "none" -> "android.text.InputType.TYPE_NULL"
                "text" -> "android.text.InputType.TYPE_CLASS_TEXT"
                "textCapCharacters" -> "android.text.InputType.TYPE_CLASS_TEXT or android.text.InputType.TYPE_TEXT_FLAG_CAP_CHARACTERS"
                "textCapWords" -> "android.text.InputType.TYPE_CLASS_TEXT or android.text.InputType.TYPE_TEXT_FLAG_CAP_WORDS"
                "textCapSentences" -> "android.text.InputType.TYPE_CLASS_TEXT or android.text.InputType.TYPE_TEXT_FLAG_CAP_SENTENCES"
                "textAutoCorrect" -> "android.text.InputType.TYPE_CLASS_TEXT or android.text.InputType.TYPE_TEXT_FLAG_AUTO_CORRECT"
                "textAutoComplete" -> "android.text.InputType.TYPE_CLASS_TEXT or android.text.InputType.TYPE_TEXT_FLAG_AUTO_COMPLETE"
                "textMultiLine" -> "android.text.InputType.TYPE_CLASS_TEXT or android.text.InputType.TYPE_TEXT_FLAG_MULTI_LINE"
                "textNoSuggestions" -> "android.text.InputType.TYPE_CLASS_TEXT or android.text.InputType.TYPE_TEXT_FLAG_NO_SUGGESTIONS"
                "textEmailAddress" -> "android.text.InputType.TYPE_CLASS_TEXT or android.text.InputType.TYPE_TEXT_VARIATION_EMAIL_ADDRESS"
                "textEmailSubject" -> "android.text.InputType.TYPE_CLASS_TEXT or android.text.InputType.TYPE_TEXT_VARIATION_EMAIL_SUBJECT"
                "textUri" -> "android.text.InputType.TYPE_CLASS_TEXT or android.text.InputType.TYPE_TEXT_VARIATION_URI"
                "textPersonName" -> "android.text.InputType.TYPE_CLASS_TEXT or android.text.InputType.TYPE_TEXT_VARIATION_PERSON_NAME"
                "textPassword" -> "android.text.InputType.TYPE_CLASS_TEXT or android.text.InputType.TYPE_TEXT_VARIATION_PASSWORD"
                "textVisiblePassword" -> "android.text.InputType.TYPE_CLASS_TEXT or android.text.InputType.TYPE_TEXT_VARIATION_VISIBLE_PASSWORD"
                "textWebEditText" -> "android.text.InputType.TYPE_CLASS_TEXT or android.text.InputType.TYPE_TEXT_VARIATION_WEB_EDIT_TEXT"
                "textFilter" -> "android.text.InputType.TYPE_CLASS_TEXT or android.text.InputType.TYPE_TEXT_VARIATION_FILTER"
                "textPostalAddress" -> "android.text.InputType.TYPE_CLASS_TEXT or android.text.InputType.TYPE_TEXT_VARIATION_POSTAL_ADDRESS"
                "number" -> "android.text.InputType.TYPE_CLASS_NUMBER"
                "numberSigned" -> "android.text.InputType.TYPE_CLASS_NUMBER or android.text.InputType.TYPE_NUMBER_FLAG_SIGNED"
                "numberDecimal" -> "android.text.InputType.TYPE_CLASS_NUMBER or android.text.InputType.TYPE_NUMBER_FLAG_DECIMAL"
                "numberPassword" -> "android.text.InputType.TYPE_CLASS_NUMBER or android.text.InputType.TYPE_NUMBER_VARIATION_PASSWORD"
                "phone" -> "android.text.InputType.TYPE_CLASS_PHONE"
                "datetime" -> "android.text.InputType.TYPE_CLASS_DATETIME"
                "date" -> "android.text.InputType.TYPE_CLASS_DATETIME or android.text.InputType.TYPE_DATETIME_VARIATION_DATE"
                "time" -> "android.text.InputType.TYPE_CLASS_DATETIME or android.text.InputType.TYPE_DATETIME_VARIATION_TIME"
                else -> "android.text.InputType.TYPE_NULL"
            }
        }
    }
}

package com.github.donglua.layoutx2c.registry

import com.github.donglua.layoutx2c.analyzer.AnalyzedNode
import com.github.donglua.layoutx2c.codegen.ImageScaleTypes
import com.github.donglua.layoutx2c.codegen.dimensionToCode
import com.github.donglua.layoutx2c.codegen.dimensionToPixelSizeFloatCode
import com.github.donglua.layoutx2c.codegen.dimensionToPxFloatCode
import com.github.donglua.layoutx2c.codegen.gravityToCode
import com.github.donglua.layoutx2c.parser.LayoutNode
import com.github.donglua.layoutx2c.parser.isButton
import com.github.donglua.layoutx2c.parser.isCompoundButton
import com.github.donglua.layoutx2c.parser.isEditText
import com.github.donglua.layoutx2c.parser.isFrameLayout
import com.github.donglua.layoutx2c.parser.isImageView
import com.github.donglua.layoutx2c.parser.isLinearLayout
import com.github.donglua.layoutx2c.parser.isRecyclerView
import com.github.donglua.layoutx2c.parser.isScrollView
import com.github.donglua.layoutx2c.parser.isTextLikeView
import com.github.donglua.layoutx2c.resources.PermissiveResourceReferenceResolver
import com.github.donglua.layoutx2c.resources.ResourceReferenceResolver
import com.github.donglua.layoutx2c.resources.parseResourceReference
import com.github.donglua.layoutx2c.resources.referenceCode
import com.squareup.kotlinpoet.ClassName
import com.squareup.kotlinpoet.CodeBlock

interface ViewAnalysisRegistry {
    val forceFallbackAttributes: Set<String>

    fun viewHandlerFor(tagName: String): ViewHandler?
    fun isKnownLayoutAttribute(attrName: String): Boolean
    fun isSupportedAttribute(node: LayoutNode, parentTagName: String?, attrName: String): Boolean
    fun hasUnsupportedAttributeValue(node: LayoutNode, parentTagName: String?): Boolean
    fun hasUnsupportedLayoutAttributeValue(node: LayoutNode, parentTagName: String?): Boolean = false
    fun hasInvalidRelativeLayoutParamForNode(node: LayoutNode, parentTagName: String?): Boolean
    fun hasInvalidConstraintLayoutParamForNode(node: LayoutNode, parentTagName: String?): Boolean
}

interface ViewEmitRegistry {
    fun viewHandlerFor(tagName: String): ViewHandler?
    fun canEmitAttribute(node: AnalyzedNode, attrName: String): Boolean
    fun emitAttributes(builder: CodeBlock.Builder, node: AnalyzedNode)
}

data class ViewHandler(
    val tagNames: Set<String>,
    val viewClass: ClassName
)

private interface AttributeHandler {
    val names: Set<String>

    fun supports(node: LayoutNode, parentTagName: String?, attrName: String): Boolean = true

    fun supportsValue(node: LayoutNode, parentTagName: String?, attrName: String, value: String): Boolean = true

    fun canEmit(node: AnalyzedNode, attrName: String): Boolean = true

    fun shouldEmit(node: AnalyzedNode): Boolean = false

    fun emit(builder: CodeBlock.Builder, node: AnalyzedNode)

    fun hasAnyAttribute(attrs: Map<String, String>, supportedAttributes: Set<String>): Boolean {
        return names.any { attrName -> attrName in supportedAttributes && attrName in attrs }
    }
}

open class ResourceAwareViewRegistry(
    private val rPackageName: String = "",
    private val resourceResolver: ResourceReferenceResolver = PermissiveResourceReferenceResolver
) : ViewAnalysisRegistry, ViewEmitRegistry {
    override val forceFallbackAttributes = setOf(
        "style",
        "android:theme"
    )

    private val viewHandlers = listOf(
        ViewHandler(
            setOf("LinearLayout", "android.widget.LinearLayout"),
            ClassName("android.widget", "LinearLayout")
        ),
        ViewHandler(
            setOf("FrameLayout", "android.widget.FrameLayout"),
            ClassName("android.widget", "FrameLayout")
        ),
        ViewHandler(
            setOf("RelativeLayout", "android.widget.RelativeLayout"),
            ClassName("android.widget", "RelativeLayout")
        ),
        ViewHandler(
            setOf("androidx.recyclerview.widget.RecyclerView"),
            ClassName("androidx.recyclerview.widget", "RecyclerView")
        ),
        ViewHandler(
            setOf("androidx.constraintlayout.widget.ConstraintLayout"),
            ClassName("androidx.constraintlayout.widget", "ConstraintLayout")
        ),
        ViewHandler(
            setOf("ScrollView", "android.widget.ScrollView"),
            ClassName("android.widget", "ScrollView")
        ),
        ViewHandler(
            setOf("HorizontalScrollView", "android.widget.HorizontalScrollView"),
            ClassName("android.widget", "HorizontalScrollView")
        ),
        ViewHandler(
            setOf("TextView", "android.widget.TextView"),
            ClassName("androidx.appcompat.widget", "AppCompatTextView")
        ),
        ViewHandler(
            setOf("Button", "android.widget.Button", "androidx.appcompat.widget.AppCompatButton"),
            ClassName("androidx.appcompat.widget", "AppCompatButton")
        ),
        ViewHandler(
            setOf("EditText", "android.widget.EditText", "androidx.appcompat.widget.AppCompatEditText"),
            ClassName("androidx.appcompat.widget", "AppCompatEditText")
        ),
        ViewHandler(
            setOf("CheckBox", "android.widget.CheckBox"),
            ClassName("android.widget", "CheckBox")
        ),
        ViewHandler(
            setOf("androidx.appcompat.widget.AppCompatCheckBox"),
            ClassName("androidx.appcompat.widget", "AppCompatCheckBox")
        ),
        ViewHandler(
            setOf("Switch", "android.widget.Switch"),
            ClassName("android.widget", "Switch")
        ),
        ViewHandler(
            setOf("androidx.appcompat.widget.SwitchCompat"),
            ClassName("androidx.appcompat.widget", "SwitchCompat")
        ),
        ViewHandler(
            setOf("RadioButton", "android.widget.RadioButton", "androidx.appcompat.widget.AppCompatRadioButton"),
            ClassName("androidx.appcompat.widget", "AppCompatRadioButton")
        ),
        ViewHandler(
            setOf("ToggleButton", "android.widget.ToggleButton"),
            ClassName("android.widget", "ToggleButton")
        ),
        ViewHandler(
            setOf("ImageView", "android.widget.ImageView", "androidx.appcompat.widget.AppCompatImageView"),
            ClassName("androidx.appcompat.widget", "AppCompatImageView")
        ),
        ViewHandler(
            setOf("View", "android.view.View"),
            ClassName("android.view", "View")
        ),
        ViewHandler(
            setOf("ViewStub", "android.view.ViewStub"),
            ClassName("android.view", "ViewStub")
        ),
        ViewHandler(
            setOf("Guideline", "androidx.constraintlayout.widget.Guideline"),
            ClassName("androidx.constraintlayout.widget", "Guideline")
        )
    )

    private val tagToHandler: Map<String, ViewHandler> =
        viewHandlers.flatMap { handler -> handler.tagNames.map { tagName -> tagName to handler } }.toMap()

    private val attributeHandlers = listOf(
        IdAttributeHandler,
        GuidelineAttributeHandler(),
        OrientationAttributeHandler,
        VisibilityAttributeHandler,
        BackgroundAttributeHandler(),
        TextAttributeHandler(),
        TextColorAttributeHandler(),
        TextSizeAttributeHandler(),
        TextStyleAttributeHandler,
        TextPresentationAttributeHandler(),
        HintAttributeHandler(),
        InputTypeAttributeHandler,
        ImageSourceAttributeHandler(),
        ImageScaleTypeAttributeHandler,
        ImageTintAttributeHandler(),
        CheckedAttributeHandler,
        CommonStateAttributeHandler(),
        CommonViewPresentationAttributeHandler(),
        PaddingAttributeHandler(),
        GravityAttributeHandler,
        FillViewportAttributeHandler,
        RecyclerViewLayoutManagerAttributeHandler,
        ViewStubAttributeHandler()
    )

    private val attributeHandlersByName: Map<String, List<AttributeHandler>> =
        attributeHandlers
            .flatMap { handler -> handler.names.map { attrName -> attrName to handler } }
            .groupBy({ it.first }, { it.second })

    private val layoutAttributeNames = setOf(
        "android:layout_width",
        "android:layout_height",
        "android:layout_margin",
        "android:layout_marginLeft",
        "android:layout_marginRight",
        "android:layout_marginTop",
        "android:layout_marginBottom",
        "android:layout_marginStart",
        "android:layout_marginEnd",
        "android:layout_weight",
        "android:layout_gravity"
    ) + relativeLayoutRuleAttributes + ConstraintLayoutRules.supportedAttributes

    override fun viewHandlerFor(tagName: String): ViewHandler? {
        return tagToHandler[tagName]
    }

    override fun isKnownLayoutAttribute(attrName: String): Boolean {
        return attrName in layoutAttributeNames
    }

    override fun isSupportedAttribute(node: LayoutNode, parentTagName: String?, attrName: String): Boolean {
        if (attrName in layoutAttributeNames) {
            if (attrName in relativeLayoutRuleAttributes && !isRelativeLayoutTag(parentTagName)) {
                return false
            }
            if (attrName in ConstraintLayoutRules.supportedAttributes &&
                !ConstraintLayoutRules.parentIsConstraintLayout(parentTagName)
            ) {
                return false
            }
            return true
        }

        return attributeHandlersByName[attrName]
            ?.any { handler -> handler.supports(node, parentTagName, attrName) }
            ?: false
    }

    override fun hasUnsupportedAttributeValue(node: LayoutNode, parentTagName: String?): Boolean {
        for ((attrName, value) in node.attributes) {
            val supportingHandlers = attributeHandlersByName[attrName]
                ?.filter { handler -> handler.supports(node, parentTagName, attrName) }
                ?: continue
            if (supportingHandlers.isNotEmpty() &&
                supportingHandlers.none { handler -> handler.supportsValue(node, parentTagName, attrName, value) }) {
                return true
            }
        }
        return false
    }

    override fun hasUnsupportedLayoutAttributeValue(node: LayoutNode, parentTagName: String?): Boolean {
        for ((attrName, value) in node.attributes) {
            if (attrName !in layoutAttributeNames) continue
            if (attrName in relativeLayoutRuleAttributes || attrName in ConstraintLayoutRules.supportedAttributes) {
                continue
            }
            if (value.startsWith("@dimen/") && !supportsResourceReference(value)) {
                return true
            }
        }
        return false
    }

    override fun hasInvalidRelativeLayoutParamForNode(node: LayoutNode, parentTagName: String?): Boolean {
        for ((attrName, value) in node.attributes) {
            if (attrName in relativeLayoutRuleAttributes && hasUnsupportedRelativeLayoutRuleValue(attrName, value, parentTagName)) {
                return true
            }
        }
        return false
    }

    override fun hasInvalidConstraintLayoutParamForNode(node: LayoutNode, parentTagName: String?): Boolean {
        if (!ConstraintLayoutRules.parentIsConstraintLayout(parentTagName)) return false
        for ((attrName, value) in node.attributes) {
            if (attrName in ConstraintLayoutRules.anchorAttributes && !ConstraintLayoutRules.isSupportedAnchorValue(value)) {
                return true
            }
            if (attrName in ConstraintLayoutRules.biasAttributes && !ConstraintLayoutRules.isSupportedBiasValue(value)) {
                return true
            }
        }
        return false
    }

    override fun canEmitAttribute(node: AnalyzedNode, attrName: String): Boolean {
        return attributeHandlersByName[attrName]
            ?.any { handler ->
                handler.supports(node.node, node.parentTagName, attrName) && handler.canEmit(node, attrName)
            }
            ?: false
    }

    override fun emitAttributes(builder: CodeBlock.Builder, node: AnalyzedNode) {
        for (handler in attributeHandlers) {
            if (handler.shouldEmit(node) || handler.hasAnyAttribute(node.node.attributes, node.supportedAttributes)) {
                handler.emit(builder, node)
            }
        }
    }

    private fun supportsResourceReference(value: String): Boolean {
        val reference = parseResourceReference(value) ?: return true
        return resourceResolver.resolve(reference.type, reference.name) != null
    }

    private fun resourceCode(type: String, name: String): String? {
        return resourceResolver.referenceCode(type, name, rPackageName)
    }

    private fun hasUnsupportedRelativeLayoutRuleValue(
        attrName: String,
        value: String,
        parentTagName: String?
    ): Boolean {
        return when (attrName) {
            in relativeLayoutIdRuleAttributes ->
                !isRelativeLayoutTag(parentTagName) || !isSupportedIdReference(value)
            in relativeLayoutBooleanRuleAttributes ->
                !isRelativeLayoutTag(parentTagName) || !isSupportedBoolean(value)
            else -> false
        }
    }

    private object IdAttributeHandler : AttributeHandler {
        override val names = setOf("android:id")

        override fun emit(builder: CodeBlock.Builder, node: AnalyzedNode) {
            node.node.attributes["android:id"]?.let { idValue ->
                val idName = idValue.removePrefix("@+id/").removePrefix("@id/")
                builder.addStatement("id = R.id.%L", idName)
            }
        }
    }

    private object OrientationAttributeHandler : AttributeHandler {
        override val names = setOf("android:orientation")

        override fun supports(node: LayoutNode, parentTagName: String?, attrName: String): Boolean {
            return node.isLinearLayout()
        }

        override fun emit(builder: CodeBlock.Builder, node: AnalyzedNode) {
            node.node.attributes["android:orientation"]?.takeIf { node.node.isLinearLayout() }?.let { value ->
                val orientation = if (value == "horizontal") "HORIZONTAL" else "VERTICAL"
                builder.addStatement("orientation = %T.%L", ClassName("android.widget", "LinearLayout"), orientation)
            }
        }
    }

    private object VisibilityAttributeHandler : AttributeHandler {
        override val names = setOf("android:visibility")

        override fun emit(builder: CodeBlock.Builder, node: AnalyzedNode) {
            node.node.attributes["android:visibility"]?.let { value ->
                val visibility = when (value) {
                    "gone" -> "GONE"
                    "invisible" -> "INVISIBLE"
                    else -> "VISIBLE"
                }
                builder.addStatement("visibility = %T.%L", ClassName("android.view", "View"), visibility)
            }
        }
    }

    private inner class BackgroundAttributeHandler : AttributeHandler {
        override val names = setOf("android:background")

        override fun supportsValue(node: LayoutNode, parentTagName: String?, attrName: String, value: String): Boolean {
            return isSupportedBackground(value) && supportsResourceReference(value)
        }

        override fun emit(builder: CodeBlock.Builder, node: AnalyzedNode) {
            node.node.attributes["android:background"]?.let { value ->
                when {
                    value.startsWith("@drawable/") -> {
                        val resName = value.removePrefix("@drawable/")
                        resourceCode("drawable", resName)?.let { resCode ->
                            builder.addStatement("setBackgroundResource(%L)", resCode)
                        }
                    }
                    value.startsWith("@color/") -> {
                        val resName = value.removePrefix("@color/")
                        resourceCode("color", resName)?.let { resCode ->
                            builder.addStatement(
                                "setBackgroundColor(%T.getColor(context, %L))",
                                ClassName("androidx.core.content", "ContextCompat"),
                                resCode
                            )
                        }
                    }
                    value.startsWith("#") -> {
                        builder.addStatement("setBackgroundColor(%T.parseColor(%S))", ClassName("android.graphics", "Color"), value)
                    }
                }
            }
        }
    }

    private inner class TextAttributeHandler : AttributeHandler {
        override val names = setOf("android:text")

        override fun supportsValue(node: LayoutNode, parentTagName: String?, attrName: String, value: String): Boolean {
            return supportsResourceReference(value)
        }

        override fun supports(node: LayoutNode, parentTagName: String?, attrName: String): Boolean {
            return node.isTextLikeView()
        }

        override fun emit(builder: CodeBlock.Builder, node: AnalyzedNode) {
            node.node.attributes["android:text"]?.let { value ->
                if (value.startsWith("@string/")) {
                    val resName = value.removePrefix("@string/")
                    resourceCode("string", resName)?.let { resCode ->
                        builder.addStatement("text = context.getString(%L)", resCode)
                    }
                } else {
                    builder.addStatement("text = %S", value)
                }
            }
        }
    }

    private inner class TextColorAttributeHandler : AttributeHandler {
        override val names = setOf("android:textColor")

        override fun supports(node: LayoutNode, parentTagName: String?, attrName: String): Boolean {
            return node.isTextLikeView()
        }

        override fun supportsValue(node: LayoutNode, parentTagName: String?, attrName: String, value: String): Boolean {
            return isSupportedColor(value) && supportsResourceReference(value)
        }

        override fun emit(builder: CodeBlock.Builder, node: AnalyzedNode) {
            node.node.attributes["android:textColor"]?.let { value ->
                emitColorAssignment(builder, value, "setTextColor")
            }
        }

        private fun emitColorAssignment(builder: CodeBlock.Builder, value: String, methodName: String) {
            when {
                value.startsWith("@color/") -> {
                    val resName = value.removePrefix("@color/")
                    resourceCode("color", resName)?.let { resCode ->
                        builder.addStatement(
                            "%L(%T.getColor(context, %L))",
                            methodName,
                            ClassName("androidx.core.content", "ContextCompat"),
                            resCode
                        )
                    }
                }
                value.startsWith("#") -> {
                    builder.addStatement(
                        "%L(%T.parseColor(%S))",
                        methodName,
                        ClassName("android.graphics", "Color"),
                        value
                    )
                }
            }
        }
    }

    private inner class TextSizeAttributeHandler : AttributeHandler {
        override val names = setOf("android:textSize")

        override fun supports(node: LayoutNode, parentTagName: String?, attrName: String): Boolean {
            return node.isTextLikeView()
        }

        override fun supportsValue(node: LayoutNode, parentTagName: String?, attrName: String, value: String): Boolean {
            return isSupportedDimension(value) && supportsResourceReference(value)
        }

        override fun emit(builder: CodeBlock.Builder, node: AnalyzedNode) {
            node.node.attributes["android:textSize"]?.let { value ->
                builder.addStatement(
                    "setTextSize(%T.COMPLEX_UNIT_PX, %L)",
                    ClassName("android.util", "TypedValue"),
                    dimensionToPixelSizeFloatCode(value, resourceResolver, rPackageName)
                )
            }
        }
    }

    private object TextStyleAttributeHandler : AttributeHandler {
        override val names = setOf("android:textStyle")

        override fun supports(node: LayoutNode, parentTagName: String?, attrName: String): Boolean {
            return node.isTextLikeView()
        }

        override fun supportsValue(node: LayoutNode, parentTagName: String?, attrName: String, value: String): Boolean {
            return isSupportedTextStyle(value)
        }

        override fun emit(builder: CodeBlock.Builder, node: AnalyzedNode) {
            node.node.attributes["android:textStyle"]?.let { value ->
                builder.addStatement("setTypeface(typeface, %L)", textStyleToCode(value))
            }
        }
    }

    private inner class TextPresentationAttributeHandler : AttributeHandler {
        override val names = setOf(
            "android:textAllCaps",
            "android:singleLine",
            "android:ellipsize",
            "android:maxLines",
            "android:minLines",
            "android:lines",
            "android:includeFontPadding",
            "android:lineSpacingExtra",
            "android:lineSpacingMultiplier",
            "android:fontFamily",
            "android:textIsSelectable",
            "android:scrollHorizontally"
        )

        override fun supports(node: LayoutNode, parentTagName: String?, attrName: String): Boolean {
            return node.isTextLikeView()
        }

        override fun supportsValue(node: LayoutNode, parentTagName: String?, attrName: String, value: String): Boolean {
            return when (attrName) {
                "android:textAllCaps",
                "android:singleLine",
                "android:includeFontPadding",
                "android:textIsSelectable",
                "android:scrollHorizontally" -> isSupportedBoolean(value)
                "android:ellipsize" -> ellipsizeToCode(value) != null
                "android:maxLines",
                "android:minLines",
                "android:lines" -> isSupportedNonNegativeInt(value)
                "android:lineSpacingExtra" -> isSupportedDimension(value) && supportsResourceReference(value)
                "android:lineSpacingMultiplier" -> value.toFloatOrNull() != null
                "android:fontFamily" -> isSupportedFontFamily(value)
                else -> false
            }
        }

        override fun emit(builder: CodeBlock.Builder, node: AnalyzedNode) {
            val attrs = node.node.attributes
            attrs["android:textAllCaps"]?.let { value ->
                builder.addStatement("setAllCaps(%L)", value == "true")
            }
            attrs["android:singleLine"]?.let { value ->
                builder.addStatement("setSingleLine(%L)", value == "true")
            }
            attrs["android:ellipsize"]?.let { value ->
                ellipsizeToCode(value)?.let { code ->
                    builder.addStatement("ellipsize = %T.TruncateAt.%L", ClassName("android.text", "TextUtils"), code)
                }
            }
            attrs["android:maxLines"]?.let { value ->
                builder.addStatement("maxLines = %L", value)
            }
            attrs["android:minLines"]?.let { value ->
                builder.addStatement("minLines = %L", value)
            }
            attrs["android:lines"]?.let { value ->
                builder.addStatement("setLines(%L)", value)
            }
            attrs["android:includeFontPadding"]?.let { value ->
                builder.addStatement("setIncludeFontPadding(%L)", value == "true")
            }
            val lineSpacingExtra = attrs["android:lineSpacingExtra"]
            val lineSpacingMultiplier = attrs["android:lineSpacingMultiplier"]
            if (lineSpacingExtra != null || lineSpacingMultiplier != null) {
                builder.addStatement(
                    "setLineSpacing(%L, %Lf)",
                    dimensionToPxFloatCode(lineSpacingExtra ?: "0", resourceResolver, rPackageName),
                    lineSpacingMultiplier?.toFloatOrNull() ?: 1.0f
                )
            }
            attrs["android:fontFamily"]?.let { value ->
                builder.addStatement(
                    "typeface = %T.create(%S, typeface?.style ?: %T.NORMAL)",
                    ClassName("android.graphics", "Typeface"),
                    value,
                    ClassName("android.graphics", "Typeface")
                )
            }
            attrs["android:textIsSelectable"]?.let { value ->
                builder.addStatement("setTextIsSelectable(%L)", value == "true")
            }
            attrs["android:scrollHorizontally"]?.let { value ->
                builder.addStatement("setHorizontallyScrolling(%L)", value == "true")
            }
        }
    }

    private inner class HintAttributeHandler : AttributeHandler {
        override val names = setOf("android:hint")

        override fun supportsValue(node: LayoutNode, parentTagName: String?, attrName: String, value: String): Boolean {
            return supportsResourceReference(value)
        }

        override fun supports(node: LayoutNode, parentTagName: String?, attrName: String): Boolean {
            return node.isEditText()
        }

        override fun emit(builder: CodeBlock.Builder, node: AnalyzedNode) {
            node.node.attributes["android:hint"]?.let { value ->
                if (value.startsWith("@string/")) {
                    val resName = value.removePrefix("@string/")
                    resourceCode("string", resName)?.let { resCode ->
                        builder.addStatement("hint = context.getString(%L)", resCode)
                    }
                } else {
                    builder.addStatement("hint = %S", value)
                }
            }
        }
    }

    private object InputTypeAttributeHandler : AttributeHandler {
        override val names = setOf("android:inputType")

        override fun supports(node: LayoutNode, parentTagName: String?, attrName: String): Boolean {
            return node.isEditText()
        }

        override fun supportsValue(node: LayoutNode, parentTagName: String?, attrName: String, value: String): Boolean {
            return isSupportedInputType(value)
        }

        override fun emit(builder: CodeBlock.Builder, node: AnalyzedNode) {
            node.node.attributes["android:inputType"]?.let { value ->
                builder.addStatement("inputType = %L", inputTypeToCode(value))
            }
        }
    }

    private inner class ImageSourceAttributeHandler : AttributeHandler {
        override val names = setOf("android:src")

        override fun supports(node: LayoutNode, parentTagName: String?, attrName: String): Boolean {
            return node.isImageView()
        }

        override fun supportsValue(node: LayoutNode, parentTagName: String?, attrName: String, value: String): Boolean {
            return !value.startsWith("@drawable/") || supportsResourceReference(value)
        }

        override fun emit(builder: CodeBlock.Builder, node: AnalyzedNode) {
            node.node.attributes["android:src"]?.let { value ->
                if (value.startsWith("@drawable/")) {
                    val resName = value.removePrefix("@drawable/")
                    resourceCode("drawable", resName)?.let { resCode ->
                        builder.addStatement("setImageResource(%L)", resCode)
                    }
                }
            }
        }
    }

    private object ImageScaleTypeAttributeHandler : AttributeHandler {
        override val names = setOf("android:scaleType")

        override fun supports(node: LayoutNode, parentTagName: String?, attrName: String): Boolean {
            return node.isImageView()
        }

        override fun supportsValue(node: LayoutNode, parentTagName: String?, attrName: String, value: String): Boolean {
            return ImageScaleTypes.supports(value)
        }

        override fun emit(builder: CodeBlock.Builder, node: AnalyzedNode) {
            node.node.attributes["android:scaleType"]?.let { value ->
                ImageScaleTypes.enumName(value)?.let { scaleType ->
                    builder.addStatement("scaleType = %T.ScaleType.%L", ClassName("android.widget", "ImageView"), scaleType)
                }
            }
        }
    }

    private inner class ImageTintAttributeHandler : AttributeHandler {
        override val names = setOf("android:tint", "app:tint")

        override fun supports(node: LayoutNode, parentTagName: String?, attrName: String): Boolean {
            return node.isImageView()
        }

        override fun supportsValue(node: LayoutNode, parentTagName: String?, attrName: String, value: String): Boolean {
            return !value.startsWith("@color/") || supportsResourceReference(value)
        }

        override fun emit(builder: CodeBlock.Builder, node: AnalyzedNode) {
            (node.node.attributes["app:tint"] ?: node.node.attributes["android:tint"])?.let { value ->
                if (value.startsWith("@color/")) {
                    val resName = value.removePrefix("@color/")
                    resourceCode("color", resName)?.let { resCode ->
                        builder.addStatement(
                            "imageTintList = %T.getColorStateList(context, %L)",
                            ClassName("androidx.core.content", "ContextCompat"),
                            resCode
                        )
                    }
                }
            }
        }
    }

    private object CheckedAttributeHandler : AttributeHandler {
        override val names = setOf("android:checked")

        override fun supports(node: LayoutNode, parentTagName: String?, attrName: String): Boolean {
            return node.isCompoundButton()
        }

        override fun supportsValue(node: LayoutNode, parentTagName: String?, attrName: String, value: String): Boolean {
            return isSupportedBoolean(value) || isSimpleDataBindingExpression(value)
        }

        override fun emit(builder: CodeBlock.Builder, node: AnalyzedNode) {
            node.node.attributes["android:checked"]
                ?.takeIf { node.node.isCompoundButton() && isSupportedBoolean(it) }
                ?.let { value ->
                    builder.addStatement("isChecked = %L", value == "true")
                }
        }
    }

    private inner class CommonStateAttributeHandler : AttributeHandler {
        override val names = setOf(
            "android:enabled",
            "android:clickable",
            "android:focusable",
            "android:elevation",
            "android:minWidth",
            "android:minHeight"
        )

        override fun supportsValue(node: LayoutNode, parentTagName: String?, attrName: String, value: String): Boolean {
            return when (attrName) {
                "android:enabled",
                "android:clickable",
                "android:focusable" -> isSupportedBoolean(value)
                "android:elevation",
                "android:minWidth",
                "android:minHeight" -> isSupportedDimension(value) && supportsResourceReference(value)
                else -> true
            }
        }

        override fun shouldEmit(node: AnalyzedNode): Boolean {
            return node.node.isButton()
        }

        override fun emit(builder: CodeBlock.Builder, node: AnalyzedNode) {
            val attrs = node.node.attributes
            if (node.node.isButton()) {
                if ("android:clickable" !in attrs) {
                    builder.addStatement("isClickable = true")
                }
                if ("android:focusable" !in attrs) {
                    builder.addStatement("isFocusable = true")
                }
            }
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
                builder.addStatement("elevation = %L", dimensionToPxFloatCode(value, resourceResolver, rPackageName))
            }
            attrs["android:minWidth"]?.let { value ->
                builder.addStatement("minimumWidth = %L", dimensionToCode(value, resourceResolver, rPackageName))
            }
            attrs["android:minHeight"]?.let { value ->
                builder.addStatement("minimumHeight = %L", dimensionToCode(value, resourceResolver, rPackageName))
            }
        }
    }

    private inner class CommonViewPresentationAttributeHandler : AttributeHandler {
        override val names = setOf(
            "android:alpha",
            "android:contentDescription",
            "android:tag",
            "android:backgroundTint",
            "android:foreground",
            "android:foregroundGravity",
            "android:importantForAccessibility",
            "android:overScrollMode",
            "android:scrollbars"
        )

        override fun supports(node: LayoutNode, parentTagName: String?, attrName: String): Boolean {
            return when (attrName) {
                "android:foreground", "android:foregroundGravity" -> node.isFrameLayout()
                else -> true
            }
        }

        override fun supportsValue(node: LayoutNode, parentTagName: String?, attrName: String, value: String): Boolean {
            return when (attrName) {
                "android:alpha" -> value.toFloatOrNull() != null
                "android:contentDescription" -> isSupportedLiteralOrStringReference(value) && supportsResourceReference(value)
                "android:tag" -> isSupportedLiteralOrStringReference(value) && supportsResourceReference(value)
                "android:backgroundTint" -> isSupportedColor(value) && supportsResourceReference(value)
                "android:foreground" -> isSupportedDrawableReference(value) && supportsResourceReference(value)
                "android:foregroundGravity" -> true
                "android:importantForAccessibility" -> importantForAccessibilityToCode(value) != null
                "android:overScrollMode" -> overScrollModeToCode(value) != null
                "android:scrollbars" -> scrollbarsToCode(value) != null
                else -> false
            }
        }

        override fun emit(builder: CodeBlock.Builder, node: AnalyzedNode) {
            val attrs = node.node.attributes
            attrs["android:alpha"]?.let { value ->
                builder.addStatement("alpha = %Lf", value.toFloat())
            }
            attrs["android:contentDescription"]?.let { value ->
                emitCharSequenceAssignment(builder, "contentDescription", value)
            }
            attrs["android:tag"]?.let { value ->
                emitAnyAssignment(builder, "tag", value)
            }
            attrs["android:backgroundTint"]?.let { value ->
                emitColorStateListAssignment(builder, "backgroundTintList", value)
            }
            attrs["android:foreground"]?.takeIf { node.node.isFrameLayout() }?.let { value ->
                emitDrawableAssignment(builder, "foreground", value)
            }
            attrs["android:foregroundGravity"]?.takeIf { node.node.isFrameLayout() }?.let { value ->
                builder.addStatement("foregroundGravity = %L", gravityToCode(value))
            }
            attrs["android:importantForAccessibility"]?.let { value ->
                importantForAccessibilityToCode(value)?.let { code ->
                    builder.addStatement("importantForAccessibility = %T.%L", ClassName("android.view", "View"), code)
                }
            }
            attrs["android:overScrollMode"]?.let { value ->
                overScrollModeToCode(value)?.let { code ->
                    builder.addStatement("overScrollMode = %T.%L", ClassName("android.view", "View"), code)
                }
            }
            attrs["android:scrollbars"]?.let { value ->
                scrollbarsToCode(value)?.let { (horizontal, vertical) ->
                    builder.addStatement("isHorizontalScrollBarEnabled = %L", horizontal)
                    builder.addStatement("isVerticalScrollBarEnabled = %L", vertical)
                }
            }
        }

        private fun emitCharSequenceAssignment(builder: CodeBlock.Builder, propertyName: String, value: String) {
            if (value.startsWith("@string/")) {
                val resName = value.removePrefix("@string/")
                resourceCode("string", resName)?.let { resCode ->
                    builder.addStatement("%L = context.getString(%L)", propertyName, resCode)
                }
            } else {
                builder.addStatement("%L = %S", propertyName, value)
            }
        }

        private fun emitAnyAssignment(builder: CodeBlock.Builder, propertyName: String, value: String) {
            if (value.startsWith("@string/")) {
                val resName = value.removePrefix("@string/")
                resourceCode("string", resName)?.let { resCode ->
                    builder.addStatement("%L = context.getString(%L)", propertyName, resCode)
                }
            } else {
                builder.addStatement("%L = %S", propertyName, value)
            }
        }

        private fun emitColorStateListAssignment(builder: CodeBlock.Builder, propertyName: String, value: String) {
            when {
                value.startsWith("@color/") -> {
                    val resName = value.removePrefix("@color/")
                    resourceCode("color", resName)?.let { resCode ->
                        builder.addStatement(
                            "%L = %T.getColorStateList(context, %L)",
                            propertyName,
                            ClassName("androidx.core.content", "ContextCompat"),
                            resCode
                        )
                    }
                }
                value.startsWith("#") -> {
                    builder.addStatement(
                        "%L = %T.valueOf(%T.parseColor(%S))",
                        propertyName,
                        ClassName("android.content.res", "ColorStateList"),
                        ClassName("android.graphics", "Color"),
                        value
                    )
                }
            }
        }

        private fun emitDrawableAssignment(builder: CodeBlock.Builder, propertyName: String, value: String) {
            if (value.startsWith("@drawable/")) {
                val resName = value.removePrefix("@drawable/")
                resourceCode("drawable", resName)?.let { resCode ->
                    builder.addStatement(
                        "%L = %T.getDrawable(context, %L)",
                        propertyName,
                        ClassName("androidx.core.content", "ContextCompat"),
                        resCode
                    )
                }
            }
        }
    }

    private inner class PaddingAttributeHandler : AttributeHandler {
        override val names = setOf(
            "android:padding",
            "android:paddingLeft",
            "android:paddingRight",
            "android:paddingTop",
            "android:paddingBottom",
            "android:paddingStart",
            "android:paddingEnd"
        )

        override fun supportsValue(node: LayoutNode, parentTagName: String?, attrName: String, value: String): Boolean {
            return isSupportedDimension(value) && supportsResourceReference(value)
        }

        override fun emit(builder: CodeBlock.Builder, node: AnalyzedNode) {
            val attrs = node.node.attributes
            val left = attrs["android:paddingLeft"] ?: attrs["android:paddingStart"] ?: attrs["android:padding"]
            val top = attrs["android:paddingTop"] ?: attrs["android:padding"]
            val right = attrs["android:paddingRight"] ?: attrs["android:paddingEnd"] ?: attrs["android:padding"]
            val bottom = attrs["android:paddingBottom"] ?: attrs["android:padding"]

            if (left != null || top != null || right != null || bottom != null) {
                builder.addStatement(
                    "setPadding(%L, %L, %L, %L)",
                    dimensionToCode(left ?: "0dp", resourceResolver, rPackageName),
                    dimensionToCode(top ?: "0dp", resourceResolver, rPackageName),
                    dimensionToCode(right ?: "0dp", resourceResolver, rPackageName),
                    dimensionToCode(bottom ?: "0dp", resourceResolver, rPackageName)
                )
            }
        }
    }

    private object GravityAttributeHandler : AttributeHandler {
        override val names = setOf("android:gravity")

        override fun supports(node: LayoutNode, parentTagName: String?, attrName: String): Boolean {
            return node.isLinearLayout() || node.isTextLikeView()
        }

        override fun emit(builder: CodeBlock.Builder, node: AnalyzedNode) {
            node.node.attributes["android:gravity"]
                ?.takeIf { node.node.isLinearLayout() || node.node.isTextLikeView() }
                ?.let { value ->
                    builder.addStatement("gravity = %L", gravityToCode(value))
                }
        }
    }

    private object FillViewportAttributeHandler : AttributeHandler {
        override val names = setOf("android:fillViewport")

        override fun supports(node: LayoutNode, parentTagName: String?, attrName: String): Boolean {
            return node.isScrollView()
        }

        override fun supportsValue(node: LayoutNode, parentTagName: String?, attrName: String, value: String): Boolean {
            return isSupportedBoolean(value)
        }

        override fun emit(builder: CodeBlock.Builder, node: AnalyzedNode) {
            node.node.attributes["android:fillViewport"]?.takeIf { node.node.isScrollView() && it == "true" }?.let {
                builder.addStatement("isFillViewport = true")
            }
        }
    }

    private object RecyclerViewLayoutManagerAttributeHandler : AttributeHandler {
        override val names = setOf("app:layoutManager")

        override fun supports(node: LayoutNode, parentTagName: String?, attrName: String): Boolean {
            return node.isRecyclerView()
        }

        override fun canEmit(node: AnalyzedNode, attrName: String): Boolean {
            return false
        }

        override fun emit(builder: CodeBlock.Builder, node: AnalyzedNode) = Unit
    }

    private inner class ViewStubAttributeHandler : AttributeHandler {
        override val names = setOf("android:layout", "android:inflatedId")

        override fun supports(node: LayoutNode, parentTagName: String?, attrName: String): Boolean {
            return node.tagName == "ViewStub" || node.tagName == "android.view.ViewStub"
        }

        override fun supportsValue(node: LayoutNode, parentTagName: String?, attrName: String, value: String): Boolean {
            return attrName != "android:layout" || !value.startsWith("@layout/") || supportsResourceReference(value)
        }

        override fun emit(builder: CodeBlock.Builder, node: AnalyzedNode) {
            node.node.attributes["android:layout"]?.let { value ->
                if (value.startsWith("@layout/")) {
                    val resName = value.removePrefix("@layout/")
                    resourceCode("layout", resName)?.let { resCode ->
                        builder.addStatement("layoutResource = %L", resCode)
                    }
                }
            }
            node.node.attributes["android:inflatedId"]?.let { value ->
                if (value.startsWith("@+id/") || value.startsWith("@id/")) {
                    val idName = value.substringAfter("/")
                    builder.addStatement("inflatedId = R.id.%L", idName)
                }
            }
        }
    }

    private inner class GuidelineAttributeHandler : AttributeHandler {
        override val names = setOf(
            "android:orientation",
            "app:layout_constraintGuide_begin",
            "app:layout_constraintGuide_end",
            "app:layout_constraintGuide_percent"
        )

        override fun supports(node: LayoutNode, parentTagName: String?, attrName: String): Boolean {
            return ConstraintLayoutRules.isGuideline(node.tagName) &&
                ConstraintLayoutRules.parentIsConstraintLayout(parentTagName)
        }

        override fun supportsValue(node: LayoutNode, parentTagName: String?, attrName: String, value: String): Boolean {
            return when (attrName) {
                "android:orientation" -> ConstraintLayoutRules.isSupportedGuidelineOrientation(value)
                "app:layout_constraintGuide_percent" -> ConstraintLayoutRules.isSupportedGuidelinePercent(value)
                "app:layout_constraintGuide_begin", "app:layout_constraintGuide_end" -> {
                    // Should be a dimension resource or dp value
                    value.startsWith("@dimen/") || value.endsWith("dp") || value.endsWith("px")
                }
                else -> false
            }
        }

        override fun emit(builder: CodeBlock.Builder, node: AnalyzedNode) {
            // Orientation
            node.node.attributes["android:orientation"]?.let { value ->
                val orientationConstant = when (value) {
                    "vertical" -> "ConstraintLayout.LayoutParams.VERTICAL"
                    "horizontal" -> "ConstraintLayout.LayoutParams.HORIZONTAL"
                    else -> return@let
                }
                builder.addStatement("orientation = %L", orientationConstant)
            }

            // Guide begin
            node.node.attributes["app:layout_constraintGuide_begin"]?.let { value ->
                val dimensionCode = dimensionToCode(value, resourceResolver, rPackageName)
                builder.addStatement("setGuidelineBegin(%L)", dimensionCode)
            }

            // Guide end
            node.node.attributes["app:layout_constraintGuide_end"]?.let { value ->
                val dimensionCode = dimensionToCode(value, resourceResolver, rPackageName)
                builder.addStatement("setGuidelineEnd(%L)", dimensionCode)
            }

            // Guide percent
            node.node.attributes["app:layout_constraintGuide_percent"]?.let { value ->
                value.toFloatOrNull()?.let { percent ->
                    builder.addStatement("setGuidelinePercent(%Lf)", percent)
                }
            }
        }
    }
}

object DefaultViewRegistry : ResourceAwareViewRegistry()

private val relativeLayoutIdRuleAttributes = setOf(
    "android:layout_above",
    "android:layout_below",
    "android:layout_toStartOf",
    "android:layout_toEndOf",
    "android:layout_toLeftOf",
    "android:layout_toRightOf",
    "android:layout_alignStart",
    "android:layout_alignEnd",
    "android:layout_alignLeft",
    "android:layout_alignRight",
    "android:layout_alignTop",
    "android:layout_alignBottom"
)

private val relativeLayoutBooleanRuleAttributes = setOf(
    "android:layout_alignParentStart",
    "android:layout_alignParentEnd",
    "android:layout_alignParentLeft",
    "android:layout_alignParentRight",
    "android:layout_alignParentTop",
    "android:layout_alignParentBottom",
    "android:layout_centerInParent",
    "android:layout_centerHorizontal",
    "android:layout_centerVertical"
)

private val relativeLayoutRuleAttributes = relativeLayoutIdRuleAttributes + relativeLayoutBooleanRuleAttributes

private val supportedInputTypes = setOf(
    "none",
    "text",
    "textCapCharacters",
    "textCapWords",
    "textCapSentences",
    "textAutoCorrect",
    "textAutoComplete",
    "textMultiLine",
    "textNoSuggestions",
    "textEmailAddress",
    "textEmailSubject",
    "textUri",
    "textPersonName",
    "textPassword",
    "textVisiblePassword",
    "textWebEditText",
    "textFilter",
    "textPostalAddress",
    "number",
    "numberSigned",
    "numberDecimal",
    "numberPassword",
    "phone",
    "datetime",
    "date",
    "time"
)

private val inputTypeClassOrVariationParts = setOf(
    "text",
    "textEmailAddress",
    "textEmailSubject",
    "textUri",
    "textPersonName",
    "textPassword",
    "textVisiblePassword",
    "textWebEditText",
    "textFilter",
    "textPostalAddress",
    "number",
    "numberPassword",
    "phone",
    "datetime",
    "date",
    "time"
)

private fun isRelativeLayoutTag(tagName: String?): Boolean {
    return tagName == "RelativeLayout" || tagName == "android.widget.RelativeLayout"
}

private fun isSupportedIdReference(value: String): Boolean {
    return value.startsWith("@id/") || value.startsWith("@+id/")
}

private fun isSupportedDimension(value: String): Boolean {
    return value.startsWith("@dimen/") ||
        value.endsWith("dp") ||
        value.endsWith("sp") ||
        value.endsWith("px") ||
        value == "0"
}

private fun isSupportedColor(value: String): Boolean {
    return value.startsWith("@color/") || value.startsWith("#")
}

private fun isSupportedBackground(value: String): Boolean {
    return value.startsWith("@drawable/") || isSupportedColor(value)
}

private fun isSupportedDrawableReference(value: String): Boolean {
    return value.startsWith("@drawable/")
}

private fun isSupportedLiteralOrStringReference(value: String): Boolean {
    return !value.startsWith("?") &&
        (value.startsWith("@string/") || !value.startsWith("@"))
}

private fun isSupportedTextStyle(value: String): Boolean {
    return value.split("|").map { it.trim() }.all { it in setOf("normal", "bold", "italic") }
}

private fun isSupportedBoolean(value: String): Boolean {
    return value == "true" || value == "false"
}

private fun isSupportedNonNegativeInt(value: String): Boolean {
    return value.toIntOrNull()?.let { it >= 0 } == true
}

private fun isSupportedFontFamily(value: String): Boolean {
    return value.isNotBlank() &&
        !value.startsWith("@") &&
        !value.startsWith("?") &&
        !value.contains("/")
}

private fun ellipsizeToCode(value: String): String? {
    return when (value) {
        "start" -> "START"
        "middle" -> "MIDDLE"
        "end" -> "END"
        "marquee" -> "MARQUEE"
        "none" -> null
        else -> null
    }
}

private fun importantForAccessibilityToCode(value: String): String? {
    return when (value) {
        "auto" -> "IMPORTANT_FOR_ACCESSIBILITY_AUTO"
        "yes" -> "IMPORTANT_FOR_ACCESSIBILITY_YES"
        "no" -> "IMPORTANT_FOR_ACCESSIBILITY_NO"
        "noHideDescendants" -> "IMPORTANT_FOR_ACCESSIBILITY_NO_HIDE_DESCENDANTS"
        else -> null
    }
}

private fun overScrollModeToCode(value: String): String? {
    return when (value) {
        "always" -> "OVER_SCROLL_ALWAYS"
        "ifContentScrolls" -> "OVER_SCROLL_IF_CONTENT_SCROLLS"
        "never" -> "OVER_SCROLL_NEVER"
        else -> null
    }
}

private fun scrollbarsToCode(value: String): Pair<Boolean, Boolean>? {
    val parts = value.split("|").map { it.trim() }.filter { it.isNotEmpty() }.toSet()
    if (parts.isEmpty() || parts.any { it !in setOf("none", "horizontal", "vertical") }) return null
    if ("none" in parts && parts.size > 1) return null
    return Pair("horizontal" in parts, "vertical" in parts)
}

private fun isSimpleDataBindingExpression(value: String): Boolean {
    val expr = when {
        value.startsWith("@={") && value.endsWith("}") ->
            value.substring(3, value.length - 1).trim()
        value.startsWith("@{") && value.endsWith("}") ->
            value.substring(2, value.length - 1).trim()
        else -> return false
    }
    return expr.matches(Regex("""[a-zA-Z_][a-zA-Z0-9_]*(\.[a-zA-Z_][a-zA-Z0-9_]*)*"""))
}

private fun isSupportedInputType(value: String): Boolean {
    val parts = value.split("|").map { it.trim() }
    if (parts.any { it !in supportedInputTypes }) return false
    if ("none" in parts) return parts.size == 1

    val classOrVariationCount = parts.count { it in inputTypeClassOrVariationParts }
    return classOrVariationCount <= 1
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

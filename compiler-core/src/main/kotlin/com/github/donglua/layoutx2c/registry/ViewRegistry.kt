package com.github.donglua.layoutx2c.registry

import com.github.donglua.layoutx2c.analyzer.AnalyzedNode
import com.github.donglua.layoutx2c.codegen.ImageScaleTypes
import com.github.donglua.layoutx2c.codegen.dimensionToCode
import com.github.donglua.layoutx2c.codegen.dimensionToPixelSizeFloatCode
import com.github.donglua.layoutx2c.codegen.dimensionToPxFloatCode
import com.github.donglua.layoutx2c.codegen.gravityToCode
import com.github.donglua.layoutx2c.parser.LayoutNode
import com.github.donglua.layoutx2c.parser.isButton
import com.github.donglua.layoutx2c.parser.isEditText
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
        )
    )

    private val tagToHandler: Map<String, ViewHandler> =
        viewHandlers.flatMap { handler -> handler.tagNames.map { tagName -> tagName to handler } }.toMap()

    private val attributeHandlers = listOf(
        IdAttributeHandler,
        OrientationAttributeHandler,
        VisibilityAttributeHandler,
        BackgroundAttributeHandler(),
        TextAttributeHandler(),
        TextColorAttributeHandler(),
        TextSizeAttributeHandler(),
        TextStyleAttributeHandler,
        HintAttributeHandler(),
        InputTypeAttributeHandler,
        ImageSourceAttributeHandler(),
        ImageScaleTypeAttributeHandler,
        ImageTintAttributeHandler(),
        CommonStateAttributeHandler(),
        PaddingAttributeHandler(),
        GravityAttributeHandler,
        FillViewportAttributeHandler,
        RecyclerViewLayoutManagerAttributeHandler,
        ViewStubAttributeHandler()
    )

    private val attributeHandlerByName: Map<String, AttributeHandler> =
        attributeHandlers.flatMap { handler -> handler.names.map { attrName -> attrName to handler } }.toMap()

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

        val handler = attributeHandlerByName[attrName] ?: return false
        return handler.supports(node, parentTagName, attrName)
    }

    override fun hasUnsupportedAttributeValue(node: LayoutNode, parentTagName: String?): Boolean {
        for ((attrName, value) in node.attributes) {
            val handler = attributeHandlerByName[attrName] ?: continue
            if (handler.supports(node, parentTagName, attrName) &&
                !handler.supportsValue(node, parentTagName, attrName, value)
            ) {
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
        val handler = attributeHandlerByName[attrName] ?: return false
        return handler.canEmit(node, attrName)
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

private fun isSupportedTextStyle(value: String): Boolean {
    return value.split("|").map { it.trim() }.all { it in setOf("normal", "bold", "italic") }
}

private fun isSupportedBoolean(value: String): Boolean {
    return value == "true" || value == "false"
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

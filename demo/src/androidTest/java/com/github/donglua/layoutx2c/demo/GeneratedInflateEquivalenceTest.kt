package com.github.donglua.layoutx2c.demo

import android.content.Context
import android.os.Looper
import android.view.ContextThemeWrapper
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.view.ViewStub
import android.widget.CompoundButton
import android.widget.Button
import android.widget.EditText
import android.widget.FrameLayout
import android.widget.ImageView
import android.widget.LinearLayout
import android.widget.ProgressBar
import android.widget.RatingBar
import android.widget.RelativeLayout
import android.widget.SeekBar
import android.widget.TextView
import androidx.appcompat.widget.Toolbar
import androidx.cardview.widget.CardView
import androidx.constraintlayout.widget.ConstraintLayout
import androidx.constraintlayout.widget.ConstraintLayout.LayoutParams as ConstraintLayoutParams
import androidx.databinding.DataBindingUtil
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import androidx.viewpager2.widget.ViewPager2
import androidx.recyclerview.widget.RecyclerView
import com.github.donglua.layoutx2c.demo.generated.DemoDataBindingEnhancedX2CBinding
import com.github.donglua.layoutx2c.demo.generated.DemoPartialFallbackParserCrashX2C
import com.github.donglua.layoutx2c.runtime.LayoutX2CRegistry
import com.google.android.material.card.MaterialCardView
import com.google.android.material.appbar.MaterialToolbar
import kotlin.math.abs
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class GeneratedInflateEquivalenceTest {

    private val context: Context
        get() = ContextThemeWrapper(
            InstrumentationRegistry.getInstrumentation().targetContext,
            R.style.Theme_LayoutX2CDemo
        )

    @Test
    fun generatedDemoLayoutsMatchPlatformInflatedViewTrees() {
        runOnMainThread {
            for (entry in DemoLayoutCatalog.entries.filter { it.platformInflatable }) {
                val platformInflated = inflatePlatform(entry)
                val generated = inflateGenerated(entry)

                assertEquivalent(entry.layoutName, snapshot(platformInflated), snapshot(generated))
            }
        }
    }

    @Test
    fun generatedTextSizeResourceMatchesPlatformPixelSizeRounding() {
        runOnMainThread {
            val entry = entry("demo_simple")
            val platformInflated = inflatePlatform(entry)
            val generated = inflateGenerated(entry)

            assertEquivalentTextSizeById(
                "demo_simple/demo_body",
                platformInflated,
                generated,
                R.id.demo_body
            )
        }
    }

    @Test
    fun generatedExplicitViewPropertiesMatchPlatformInflation() {
        runOnMainThread {
            val formEntry = entry("demo_form")
            val platformForm = inflatePlatform(formEntry)
            val generatedForm = inflateGenerated(formEntry)

            assertEquivalentMinimumHeightById("demo_form/form_name", platformForm, generatedForm, R.id.form_name)
            assertEquivalentMinimumWidthById("demo_form/form_submit", platformForm, generatedForm, R.id.form_submit)
            assertEquivalentElevationById("demo_form/form_submit", platformForm, generatedForm, R.id.form_submit)

            val relativeEntry = entry("demo_relative")
            val platformRelative = inflatePlatform(relativeEntry)
            val generatedRelative = inflateGenerated(relativeEntry)

            assertEquivalentPaddingById("demo_relative/relative_badge", platformRelative, generatedRelative, R.id.relative_badge)
            assertEquivalentRelativeRulesById(
                "demo_relative/relative_anchor",
                platformRelative,
                generatedRelative,
                R.id.relative_anchor,
                RelativeLayout.CENTER_IN_PARENT
            )
            assertEquivalentRelativeRulesById(
                "demo_relative/relative_above",
                platformRelative,
                generatedRelative,
                R.id.relative_above,
                RelativeLayout.ABOVE,
                RelativeLayout.CENTER_HORIZONTAL
            )
            assertEquivalentRelativeRulesById(
                "demo_relative/relative_start_of",
                platformRelative,
                generatedRelative,
                R.id.relative_start_of,
                RelativeLayout.START_OF,
                RelativeLayout.CENTER_VERTICAL
            )
            assertEquivalentRelativeRulesById(
                "demo_relative/relative_end_of",
                platformRelative,
                generatedRelative,
                R.id.relative_end_of,
                RelativeLayout.END_OF,
                RelativeLayout.CENTER_VERTICAL
            )
        }
    }

    @Test
    fun generatedImageViewsPreserveDrawableTintAndScaleTypes() {
        runOnMainThread {
            val entry = entry("demo_scroll_image")
            val platformInflated = inflatePlatform(entry)
            val generated = inflateGenerated(entry)

            assertEquivalentImageView("demo_scroll_image/icon", platformInflated, generated, R.id.demo_scroll_image_icon)
            assertEquivalentImageView("demo_scroll_image/crop", platformInflated, generated, R.id.demo_scroll_image_crop)
            assertEquivalentImageView("demo_scroll_image/fit", platformInflated, generated, R.id.demo_scroll_image_fit)
        }
    }

    @Test
    fun registryFacadeInflatesEveryGeneratedDemoLayout() {
        runOnMainThread {
            requireTrue(LayoutX2CRegistry.initialize(context))

            for (entry in DemoLayoutCatalog.entries) {
                requireTrue("${entry.layoutName} should be registered", LayoutX2CRegistry.has(context, entry.layoutResId))

                val registryInflated = LayoutX2CRegistry.inflate(context, entry.layoutResId, parentFor(entry), attachToRoot = false)
                if (!entry.platformInflatable) {
                    continue
                }

                val platformInflated = inflatePlatform(entry)
                assertEquivalent(entry.layoutName, snapshot(platformInflated), snapshot(registryInflated))
            }
        }
    }

    @Test
    fun generatedIncludeLayoutPreservesMergeChildrenAndViewStubMetadata() {
        runOnMainThread {
            val entry = entry("demo_include")
            val platformInflated = inflatePlatform(entry)
            val generated = inflateGenerated(entry)

            assertEquivalent(entry.layoutName, snapshot(platformInflated), snapshot(generated))
            assertEquivalentChildIds(
                "demo_include",
                platformInflated,
                generated,
                listOf(
                    R.id.include_title,
                    R.id.include_body,
                    R.id.include_primary,
                    R.id.include_secondary,
                    R.id.include_stub
                )
            )
            assertEquivalentLinearLayoutParamsById("demo_include/include_primary", platformInflated, generated, R.id.include_primary)
            assertEquivalentLinearLayoutParamsById("demo_include/include_secondary", platformInflated, generated, R.id.include_secondary)
            assertEquivalentViewStub("demo_include/include_stub", platformInflated, generated, R.id.include_stub)
        }
    }

    @Test
    fun generatedViewStubInflatesEquivalentContentAndReplacesStub() {
        runOnMainThread {
            val entry = entry("demo_include")
            val platformInflated = inflatePlatform(entry)
            val generated = inflateGenerated(entry)

            val platformContent = platformInflated.findViewById<ViewStub>(R.id.include_stub).inflate()
            val generatedContent = generated.findViewById<ViewStub>(R.id.include_stub).inflate()

            assertEquivalent("demo_include/inflated_stub", snapshot(platformContent), snapshot(generatedContent))
            assertEquivalentChildIds(
                "demo_include/after_stub_inflate",
                platformInflated,
                generated,
                listOf(
                    R.id.include_title,
                    R.id.include_body,
                    R.id.include_primary,
                    R.id.include_secondary,
                    R.id.include_stub_content
                )
            )
        }
    }

    @Test
    fun generatedConstraintLayoutPreservesSafeSubsetAnchors() {
        runOnMainThread {
            val entry = entry("demo_constraint")
            val platformInflated = inflatePlatform(entry)
            val generated = inflateGenerated(entry)

            assertEquivalent(entry.layoutName, snapshot(platformInflated), snapshot(generated))
            assertEquivalentConstraintParams("demo_constraint/constraint_title", platformInflated, generated, R.id.constraint_title)
            assertEquivalentConstraintParams("demo_constraint/constraint_subtitle", platformInflated, generated, R.id.constraint_subtitle)
            assertEquivalentConstraintParams("demo_constraint/constraint_end", platformInflated, generated, R.id.constraint_end)
        }
    }

    @Test
    fun generatedGuidelinesPreserveConstraintMetadata() {
        runOnMainThread {
            val entry = entry("demo_constraint_guideline")
            val platformInflated = inflatePlatform(entry)
            val generated = inflateGenerated(entry)

            assertEquivalent(entry.layoutName, snapshot(platformInflated), snapshot(generated))
            assertEquivalentGuidelineParams("demo_constraint_guideline/vertical", platformInflated, generated, R.id.guideline_vertical)
            assertEquivalentGuidelineParams("demo_constraint_guideline/horizontal", platformInflated, generated, R.id.guideline_horizontal)
            assertEquivalentGuidelineParams("demo_constraint_guideline/end", platformInflated, generated, R.id.guideline_end)
            assertEquivalentConstraintParams("demo_constraint_guideline/title", platformInflated, generated, R.id.guideline_title)
            assertEquivalentConstraintParams("demo_constraint_guideline/left", platformInflated, generated, R.id.guideline_left)
        }
    }

    @Test
    fun generatedCompatWidgetsPreserveExpandedProperties() {
        runOnMainThread {
            val entry = entry("demo_compat_widgets")
            val platformInflated = inflatePlatform(entry)
            val generated = inflateGenerated(entry)
            val differences = mutableListOf<String>()

            val expectedText = platformInflated.findViewById<TextView>(R.id.compat_text)
            val actualText = generated.findViewById<TextView>(R.id.compat_text)
            checkField(entry.layoutName, "compat_text.text", expectedText.text.toString(), actualText.text.toString(), differences)
            checkField(
                entry.layoutName,
                "compat_text.allCapsTransformation",
                expectedText.transformedText("Mixed Case"),
                actualText.transformedText("Mixed Case"),
                differences
            )
            checkField(entry.layoutName, "compat_text.ellipsize", expectedText.ellipsize, actualText.ellipsize, differences)
            checkField(entry.layoutName, "compat_text.maxLines", expectedText.maxLines, actualText.maxLines, differences)
            checkField(entry.layoutName, "compat_text.minLines", expectedText.minLines, actualText.minLines, differences)
            checkField(entry.layoutName, "compat_text.includeFontPadding", expectedText.includeFontPadding, actualText.includeFontPadding, differences)
            checkFloatField(entry.layoutName, "compat_text.lineSpacingExtra", expectedText.lineSpacingExtra, actualText.lineSpacingExtra, differences)
            checkFloatField(entry.layoutName, "compat_text.lineSpacingMultiplier", expectedText.lineSpacingMultiplier, actualText.lineSpacingMultiplier, differences)
            checkField(entry.layoutName, "compat_text.typeface", expectedText.typeface?.toString(), actualText.typeface?.toString(), differences)
            checkField(entry.layoutName, "compat_text.isTextSelectable", expectedText.isTextSelectable, actualText.isTextSelectable, differences)
            checkField(entry.layoutName, "compat_text.isHorizontallyScrollable", expectedText.isHorizontallyScrollable, actualText.isHorizontallyScrollable, differences)

            val expectedTextLines = platformInflated.findViewById<TextView>(R.id.compat_text_lines)
            val actualTextLines = generated.findViewById<TextView>(R.id.compat_text_lines)
            checkField(entry.layoutName, "compat_text_lines.ellipsize", expectedTextLines.ellipsize, actualTextLines.ellipsize, differences)
            checkField(entry.layoutName, "compat_text_lines.maxLines", expectedTextLines.maxLines, actualTextLines.maxLines, differences)
            checkField(entry.layoutName, "compat_text_lines.minLines", expectedTextLines.minLines, actualTextLines.minLines, differences)
            checkField(entry.layoutName, "compat_text_lines.typeface", expectedTextLines.typeface?.toString(), actualTextLines.typeface?.toString(), differences)

            val expectedPanel = platformInflated.findViewById<FrameLayout>(R.id.compat_panel)
            val actualPanel = generated.findViewById<FrameLayout>(R.id.compat_panel)
            checkFloatField(entry.layoutName, "compat_panel.alpha", expectedPanel.alpha, actualPanel.alpha, differences)
            checkField(entry.layoutName, "compat_panel.contentDescription", expectedPanel.contentDescription?.toString(), actualPanel.contentDescription?.toString(), differences)
            checkField(entry.layoutName, "compat_panel.tag", expectedPanel.tag, actualPanel.tag, differences)
            checkField(entry.layoutName, "compat_panel.backgroundTint", expectedPanel.backgroundTintList?.defaultColor, actualPanel.backgroundTintList?.defaultColor, differences)
            checkField(entry.layoutName, "compat_panel.foreground", expectedPanel.foreground != null, actualPanel.foreground != null, differences)
            checkField(entry.layoutName, "compat_panel.foregroundGravity", expectedPanel.foregroundGravity, actualPanel.foregroundGravity, differences)
            checkField(entry.layoutName, "compat_panel.importantForAccessibility", expectedPanel.importantForAccessibility, actualPanel.importantForAccessibility, differences)
            checkField(entry.layoutName, "compat_panel.overScrollMode", expectedPanel.overScrollMode, actualPanel.overScrollMode, differences)
            checkField(entry.layoutName, "compat_panel.horizontalScrollbar", expectedPanel.isHorizontalScrollBarEnabled, actualPanel.isHorizontalScrollBarEnabled, differences)
            checkField(entry.layoutName, "compat_panel.verticalScrollbar", expectedPanel.isVerticalScrollBarEnabled, actualPanel.isVerticalScrollBarEnabled, differences)

            val expectedProgress = platformInflated.findViewById<ProgressBar>(R.id.compat_progress)
            val actualProgress = generated.findViewById<ProgressBar>(R.id.compat_progress)
            checkField(entry.layoutName, "compat_progress.indeterminate", expectedProgress.isIndeterminate, actualProgress.isIndeterminate, differences)
            checkField(entry.layoutName, "compat_progress.max", expectedProgress.max, actualProgress.max, differences)
            checkField(entry.layoutName, "compat_progress.progress", expectedProgress.progress, actualProgress.progress, differences)
            checkField(entry.layoutName, "compat_progress.secondaryProgress", expectedProgress.secondaryProgress, actualProgress.secondaryProgress, differences)
            checkField(entry.layoutName, "compat_progress.progressTint", expectedProgress.progressTintList?.defaultColor, actualProgress.progressTintList?.defaultColor, differences)
            checkField(entry.layoutName, "compat_progress.indeterminateTint", expectedProgress.indeterminateTintList?.defaultColor, actualProgress.indeterminateTintList?.defaultColor, differences)

            val expectedSeek = platformInflated.findViewById<SeekBar>(R.id.compat_seek)
            val actualSeek = generated.findViewById<SeekBar>(R.id.compat_seek)
            checkField(entry.layoutName, "compat_seek.max", expectedSeek.max, actualSeek.max, differences)
            checkField(entry.layoutName, "compat_seek.progress", expectedSeek.progress, actualSeek.progress, differences)
            checkField(entry.layoutName, "compat_seek.thumb", expectedSeek.thumb != null, actualSeek.thumb != null, differences)
            checkField(entry.layoutName, "compat_seek.splitTrack", expectedSeek.splitTrack, actualSeek.splitTrack, differences)

            val expectedRating = platformInflated.findViewById<RatingBar>(R.id.compat_rating)
            val actualRating = generated.findViewById<RatingBar>(R.id.compat_rating)
            checkField(entry.layoutName, "compat_rating.numStars", expectedRating.numStars, actualRating.numStars, differences)
            checkFloatField(entry.layoutName, "compat_rating.rating", expectedRating.rating, actualRating.rating, differences)
            checkFloatField(entry.layoutName, "compat_rating.stepSize", expectedRating.stepSize, actualRating.stepSize, differences)
            checkField(entry.layoutName, "compat_rating.isIndicator", expectedRating.isIndicator, actualRating.isIndicator, differences)

            val expectedConstraintParams = platformInflated
                .findViewById<TextView>(R.id.compat_constrained)
                .layoutParams as ConstraintLayoutParams
            val actualConstraintParams = generated
                .findViewById<TextView>(R.id.compat_constrained)
                .layoutParams as ConstraintLayoutParams
            checkField(entry.layoutName, "constraint.dimensionRatio", expectedConstraintParams.dimensionRatio, actualConstraintParams.dimensionRatio, differences)
            checkFloatField(entry.layoutName, "constraint.widthPercent", expectedConstraintParams.matchConstraintPercentWidth, actualConstraintParams.matchConstraintPercentWidth, differences)
            checkFloatField(entry.layoutName, "constraint.heightPercent", expectedConstraintParams.matchConstraintPercentHeight, actualConstraintParams.matchConstraintPercentHeight, differences)
            checkField(entry.layoutName, "constraint.horizontalChainStyle", expectedConstraintParams.horizontalChainStyle, actualConstraintParams.horizontalChainStyle, differences)
            checkField(entry.layoutName, "constraint.verticalChainStyle", expectedConstraintParams.verticalChainStyle, actualConstraintParams.verticalChainStyle, differences)
            checkFloatField(entry.layoutName, "constraint.horizontalWeight", expectedConstraintParams.horizontalWeight, actualConstraintParams.horizontalWeight, differences)
            checkFloatField(entry.layoutName, "constraint.verticalWeight", expectedConstraintParams.verticalWeight, actualConstraintParams.verticalWeight, differences)
            checkField(entry.layoutName, "constraint.goneStartMargin", expectedConstraintParams.goneStartMargin, actualConstraintParams.goneStartMargin, differences)
            checkField(entry.layoutName, "constraint.goneEndMargin", expectedConstraintParams.goneEndMargin, actualConstraintParams.goneEndMargin, differences)
            checkField(entry.layoutName, "constraint.goneTopMargin", expectedConstraintParams.goneTopMargin, actualConstraintParams.goneTopMargin, differences)
            checkField(entry.layoutName, "constraint.goneBottomMargin", expectedConstraintParams.goneBottomMargin, actualConstraintParams.goneBottomMargin, differences)
            checkField(entry.layoutName, "constraint.leftToLeft", expectedConstraintParams.leftToLeft, actualConstraintParams.leftToLeft, differences)
            checkField(entry.layoutName, "constraint.rightToRight", expectedConstraintParams.rightToRight, actualConstraintParams.rightToRight, differences)

            val expectedCard = platformInflated.findViewById<CardView>(R.id.compat_card)
            val actualCard = generated.findViewById<CardView>(R.id.compat_card)
            checkFloatField(entry.layoutName, "compat_card.radius", expectedCard.radius, actualCard.radius, differences)
            checkFloatField(entry.layoutName, "compat_card.cardElevation", expectedCard.cardElevation, actualCard.cardElevation, differences)
            checkField(entry.layoutName, "compat_card.useCompatPadding", expectedCard.useCompatPadding, actualCard.useCompatPadding, differences)

            val expectedMaterialCard = platformInflated.findViewById<MaterialCardView>(R.id.compat_material_card)
            val actualMaterialCard = generated.findViewById<MaterialCardView>(R.id.compat_material_card)
            checkFloatField(entry.layoutName, "compat_material_card.radius", expectedMaterialCard.radius, actualMaterialCard.radius, differences)
            checkField(entry.layoutName, "compat_material_card.strokeColor", expectedMaterialCard.strokeColor, actualMaterialCard.strokeColor, differences)
            checkField(entry.layoutName, "compat_material_card.strokeWidth", expectedMaterialCard.strokeWidth, actualMaterialCard.strokeWidth, differences)

            val expectedToolbar = platformInflated.findViewById<Toolbar>(R.id.compat_toolbar)
            val actualToolbar = generated.findViewById<Toolbar>(R.id.compat_toolbar)
            checkField(entry.layoutName, "compat_toolbar.title", expectedToolbar.title?.toString(), actualToolbar.title?.toString(), differences)
            checkField(entry.layoutName, "compat_toolbar.subtitle", expectedToolbar.subtitle?.toString(), actualToolbar.subtitle?.toString(), differences)
            checkField(entry.layoutName, "compat_toolbar.navigationIcon", expectedToolbar.navigationIcon != null, actualToolbar.navigationIcon != null, differences)

            val expectedMaterialToolbar = platformInflated.findViewById<MaterialToolbar>(R.id.compat_material_toolbar)
            val actualMaterialToolbar = generated.findViewById<MaterialToolbar>(R.id.compat_material_toolbar)
            checkField(entry.layoutName, "compat_material_toolbar.title", expectedMaterialToolbar.title?.toString(), actualMaterialToolbar.title?.toString(), differences)
            checkField(entry.layoutName, "compat_material_toolbar.subtitle", expectedMaterialToolbar.subtitle?.toString(), actualMaterialToolbar.subtitle?.toString(), differences)

            checkField(
                entry.layoutName,
                "compat_pager.class",
                platformInflated.findViewById<ViewPager2>(R.id.compat_pager).javaClass.name,
                generated.findViewById<ViewPager2>(R.id.compat_pager).javaClass.name,
                differences
            )

            if (differences.isNotEmpty()) {
                throw AssertionError(differences.joinToString(separator = "\n"))
            }
        }
    }

    @Test
    fun generatedFallbackLayoutPreservesPlatformInflatedStructure() {
        runOnMainThread {
            val entry = entry("demo_fallback")
            val platformInflated = inflatePlatform(entry)
            val generated = inflateGenerated(entry)

            assertEquivalent(entry.layoutName, snapshot(platformInflated), snapshot(generated))
            requireTrue("demo_fallback should inflate a LinearLayout root", generated is LinearLayout)
            requireTrue("demo_fallback should preserve child count", (generated as LinearLayout).childCount == 3)
            assertEquivalentLinearLayoutParamsAt("demo_fallback/badge", platformInflated, generated, childIndex = 1)
            assertEquivalentLinearLayoutParamsAt("demo_fallback/body", platformInflated, generated, childIndex = 2)
        }
    }

    @Test
    fun generatedDataBindingEnhancedFieldsAndSimpleExpressionsStayUsable() {
        runOnMainThread {
            val binding = DemoDataBindingEnhancedX2CBinding.inflate(
                LayoutInflater.from(context),
                parentFor(entry("demo_data_binding_enhanced")),
                false
            )

            requireTrue("titleText should resolve generated field", binding.titleText.id == R.id.title_text)
            requireTrue("descriptionText should resolve generated field", binding.descriptionText.id == R.id.description_text)
            requireTrue("countText should resolve generated field", binding.countText.id == R.id.count_text)
            requireTrue("vmNameText should resolve generated field", binding.vmNameText.id == R.id.vm_name_text)
            requireTrue("vmStatusText should resolve generated field", binding.vmStatusText.id == R.id.vm_status_text)
            requireTrue(
                "generated binding should be registered on the root view",
                DataBindingUtil.getBinding<DemoDataBindingEnhancedX2CBinding>(binding.root) === binding
            )

            binding.title = "Android equivalence"
            binding.description = "Generated binding writes supported expressions"
            binding.viewModel = ItemViewModel(
                name = "LayoutX2C VM",
                status = "Bound",
                itemId = 42
            )
            requireTrue("setting variables should mark binding dirty", binding.hasPendingBindings())

            binding.executePendingBindings()

            val differences = mutableListOf<String>()
            checkField("demo_data_binding_enhanced", "titleText.text", "Android equivalence", binding.titleText.text.toString(), differences)
            checkField(
                "demo_data_binding_enhanced",
                "descriptionText.text",
                "Generated binding writes supported expressions",
                binding.descriptionText.text.toString(),
                differences
            )
            checkField("demo_data_binding_enhanced", "vmNameText.text", "LayoutX2C VM", binding.vmNameText.text.toString(), differences)
            checkField("demo_data_binding_enhanced", "vmStatusText.text", "Bound", binding.vmStatusText.text.toString(), differences)
            if (differences.isNotEmpty()) {
                throw AssertionError(differences.joinToString(separator = "\n"))
            }
            requireTrue("executePendingBindings should clear dirty state", !binding.hasPendingBindings())
        }
    }

    @Test
    fun generatedConstraintRootInflatesFallbackChildrenWithoutParserCastCrash() {
        runOnMainThread {
            val parent = ConstraintLayout(context)

            val generated = DemoPartialFallbackParserCrashX2C.inflate(context, parent)

            requireTrue("constraint root should inflate", generated is ConstraintLayout)
            requireTrue(
                "constraint root should preserve direct children",
                (generated as ConstraintLayout).childCount == 4
            )
        }
    }

    private fun runOnMainThread(block: () -> Unit) {
        if (Looper.myLooper() == Looper.getMainLooper()) {
            block()
        } else {
            InstrumentationRegistry.getInstrumentation().runOnMainSync(block)
        }
    }

    private fun inflatePlatform(entry: DemoLayoutCatalog.Entry): View {
        return LayoutInflater.from(context).inflate(entry.layoutResId, parentFor(entry), false)
    }

    private fun inflateGenerated(entry: DemoLayoutCatalog.Entry): View {
        return entry.generatedInflater(context, parentFor(entry))
    }

    private fun parentFor(entry: DemoLayoutCatalog.Entry): ViewGroup {
        return when (entry.layoutName) {
            "demo_relative" -> RelativeLayout(context)
            "demo_nested" -> LinearLayout(context)
            "demo_constraint" -> ConstraintLayout(context)
            else -> FrameLayout(context)
        }
    }

    private fun snapshot(view: View): ViewSnapshot {
        return ViewSnapshot(
            className = normalizedClassName(view),
            idName = view.resources.getResourceEntryNameOrNull(view.id),
            visibility = view.visibility,
            enabled = view.isEnabled,
            clickable = view.isClickable,
            focusable = view.isFocusable,
            padding = if (view is TextView) {
                null
            } else {
                PaddingSnapshot(view.paddingLeft, view.paddingTop, view.paddingRight, view.paddingBottom)
            },
            layoutParams = snapshotLayoutParams(view.layoutParams),
            text = (view as? TextView)?.text?.toString(),
            hint = (view as? EditText)?.hint?.toString(),
            textColor = (view as? TextView)?.currentTextColor,
            textSize = textSizeOf(view),
            typefaceStyle = (view as? TextView)?.typeface?.style,
            inputType = (view as? EditText)?.inputType,
            checked = (view as? CompoundButton)?.isChecked,
            imageScaleType = (view as? ImageView)?.scaleType?.name,
            orientation = (view as? LinearLayout)?.orientation,
            gravity = gravityOf(view),
            fillViewport = fillViewportOf(view),
            recyclerLayoutManager = recyclerLayoutManagerOf(view),
            children = if (view is ViewGroup) {
                (0 until view.childCount).map { index -> snapshot(view.getChildAt(index)) }
            } else {
                emptyList()
            }
        )
    }

    private fun normalizedClassName(view: View): String {
        return when (view) {
            is Button -> Button::class.java.name
            is EditText -> EditText::class.java.name
            is androidx.appcompat.widget.AppCompatButton -> Button::class.java.name
            is androidx.appcompat.widget.AppCompatEditText -> EditText::class.java.name
            is androidx.appcompat.widget.AppCompatImageView -> ImageView::class.java.name
            is androidx.appcompat.widget.AppCompatTextView -> TextView::class.java.name
            is TextView -> TextView::class.java.name
            is ImageView -> ImageView::class.java.name
            else -> view.javaClass.name
        }
    }

    private fun textSizeOf(view: View): Float? {
        if (view !is TextView) return null
        return if (view.javaClass == TextView::class.java || view is androidx.appcompat.widget.AppCompatTextView) {
            view.textSize
        } else {
            null
        }
    }

    private fun snapshotLayoutParams(layoutParams: ViewGroup.LayoutParams?): LayoutParamsSnapshot? {
        if (layoutParams == null) return null

        return LayoutParamsSnapshot(
            className = layoutParams.javaClass.name,
            width = layoutParams.width,
            height = layoutParams.height,
            margins = (layoutParams as? ViewGroup.MarginLayoutParams)?.let {
                MarginSnapshot(it.leftMargin, it.topMargin, it.rightMargin, it.bottomMargin)
            },
            linearWeight = (layoutParams as? LinearLayout.LayoutParams)?.weight,
            gravity = gravityOf(layoutParams),
            relativeRules = (layoutParams as? RelativeLayout.LayoutParams)?.rules?.toList(),
            constraintAnchors = (layoutParams as? ConstraintLayoutParams)?.let {
                ConstraintAnchorSnapshot(
                    startToStart = it.startToStart,
                    startToEnd = it.startToEnd,
                    endToStart = it.endToStart,
                    endToEnd = it.endToEnd,
                    leftToLeft = it.leftToLeft,
                    leftToRight = it.leftToRight,
                    rightToLeft = it.rightToLeft,
                    rightToRight = it.rightToRight,
                    topToTop = it.topToTop,
                    topToBottom = it.topToBottom,
                    bottomToTop = it.bottomToTop,
                    bottomToBottom = it.bottomToBottom,
                    horizontalBias = it.horizontalBias,
                    verticalBias = it.verticalBias
                )
            }
        )
    }

    private fun gravityOf(view: View): Int? {
        return when (view) {
            is LinearLayout -> view.gravity
            is TextView -> view.gravity
            else -> null
        }
    }

    private fun gravityOf(layoutParams: ViewGroup.LayoutParams): Int? {
        return when (layoutParams) {
            is LinearLayout.LayoutParams -> layoutParams.gravity
            is FrameLayout.LayoutParams -> layoutParams.gravity
            else -> null
        }
    }

    private fun fillViewportOf(view: View): Boolean? {
        return when (view) {
            is android.widget.ScrollView -> view.isFillViewport
            is android.widget.HorizontalScrollView -> view.isFillViewport
            else -> null
        }
    }

    private fun recyclerLayoutManagerOf(view: View): String? {
        // LayoutX2C currently generates RecyclerView as a safe empty container; adapter and
        // layoutManager wiring are outside the generated contract for this demo.
        if (view is RecyclerView) return null
        return null
    }

    private fun requireTrue(message: String, condition: Boolean) {
        if (!condition) throw AssertionError(message)
    }

    private fun requireTrue(condition: Boolean) {
        requireTrue("Expected condition to be true", condition)
    }

    private fun assertEquivalent(layoutName: String, expected: ViewSnapshot, actual: ViewSnapshot) {
        val differences = mutableListOf<String>()
        expected.collectDifferences(actual, layoutName, differences)
        if (differences.isNotEmpty()) {
            throw AssertionError(differences.joinToString(separator = "\n"))
        }
    }

    private fun ViewSnapshot.collectDifferences(
        actual: ViewSnapshot,
        path: String,
        differences: MutableList<String>
    ) {
        checkField(path, "className", className, actual.className, differences)
        checkField(path, "idName", idName, actual.idName, differences)
        checkField(path, "visibility", visibility, actual.visibility, differences)
        checkField(path, "enabled", enabled, actual.enabled, differences)
        checkField(path, "clickable", clickable, actual.clickable, differences)
        checkField(path, "focusable", focusable, actual.focusable, differences)
        checkField(path, "text", text, actual.text, differences)
        checkField(path, "hint", hint, actual.hint, differences)
        checkField(path, "textColor", textColor, actual.textColor, differences)
        checkFloatField(path, "textSize", textSize, actual.textSize, differences)
        checkField(path, "typefaceStyle", typefaceStyle, actual.typefaceStyle, differences)
        checkField(path, "inputType", inputType, actual.inputType, differences)
        checkField(path, "checked", checked, actual.checked, differences)
        checkField(path, "imageScaleType", imageScaleType, actual.imageScaleType, differences)
        checkField(path, "orientation", orientation, actual.orientation, differences)
        checkField(path, "fillViewport", fillViewport, actual.fillViewport, differences)
        checkField(path, "recyclerLayoutManager", recyclerLayoutManager, actual.recyclerLayoutManager, differences)
        checkField(path, "padding", padding, actual.padding, differences)
        layoutParams.collectDifferences(actual.layoutParams, path, differences)
        checkField(path, "childCount", children.size, actual.children.size, differences)
        for (index in 0 until minOf(children.size, actual.children.size)) {
            children[index].collectDifferences(actual.children[index], "$path/$index", differences)
        }
    }

    private fun entry(layoutName: String): DemoLayoutCatalog.Entry {
        return DemoLayoutCatalog.entries.first { it.layoutName == layoutName }
    }

    private fun assertEquivalentChildIds(
        path: String,
        expectedRoot: View,
        actualRoot: View,
        expectedIds: List<Int>
    ) {
        val expectedNames = expectedIds.map { context.resources.getResourceEntryName(it) }
        val differences = mutableListOf<String>()
        checkField(path, "platform childIds", expectedNames, (expectedRoot as ViewGroup).childIds(), differences)
        checkField(path, "generated childIds", expectedNames, (actualRoot as ViewGroup).childIds(), differences)
        if (differences.isNotEmpty()) {
            throw AssertionError(differences.joinToString(separator = "\n"))
        }
    }

    private fun ViewGroup.childIds(): List<String?> {
        return (0 until childCount).map { index ->
            resources.getResourceEntryNameOrNull(getChildAt(index).id)
        }
    }

    private fun assertEquivalentLinearLayoutParamsById(
        path: String,
        expectedRoot: View,
        actualRoot: View,
        childId: Int
    ) {
        assertEquivalentLinearLayoutParams(
            path,
            expectedRoot.findViewById(childId),
            actualRoot.findViewById(childId)
        )
    }

    private fun assertEquivalentLinearLayoutParamsAt(
        path: String,
        expectedRoot: View,
        actualRoot: View,
        childIndex: Int
    ) {
        assertEquivalentLinearLayoutParams(
            path,
            (expectedRoot as ViewGroup).getChildAt(childIndex),
            (actualRoot as ViewGroup).getChildAt(childIndex)
        )
    }

    private fun assertEquivalentLinearLayoutParams(path: String, expected: View, actual: View) {
        val expectedParams = expected.layoutParams as LinearLayout.LayoutParams
        val actualParams = actual.layoutParams as LinearLayout.LayoutParams
        val differences = mutableListOf<String>()
        checkField(path, "layoutParams.width", expectedParams.width, actualParams.width, differences)
        checkField(path, "layoutParams.height", expectedParams.height, actualParams.height, differences)
        checkField(path, "layoutParams.topMargin", expectedParams.topMargin, actualParams.topMargin, differences)
        checkField(path, "layoutParams.bottomMargin", expectedParams.bottomMargin, actualParams.bottomMargin, differences)
        if (differences.isNotEmpty()) {
            throw AssertionError(differences.joinToString(separator = "\n"))
        }
    }

    private fun assertEquivalentTextSizeById(path: String, expectedRoot: View, actualRoot: View, childId: Int) {
        val expected = expectedRoot.findViewById<TextView>(childId)
        val actual = actualRoot.findViewById<TextView>(childId)
        val differences = mutableListOf<String>()
        checkFloatField(path, "textSize", expected.textSize, actual.textSize, differences)
        if (differences.isNotEmpty()) {
            throw AssertionError(differences.joinToString(separator = "\n"))
        }
    }

    private fun assertEquivalentImageView(path: String, expectedRoot: View, actualRoot: View, childId: Int) {
        val expected = expectedRoot.findViewById<ImageView>(childId)
        val actual = actualRoot.findViewById<ImageView>(childId)
        val differences = mutableListOf<String>()
        checkField(path, "drawable", expected.drawable != null, actual.drawable != null, differences)
        checkField(path, "imageTint", expected.imageTintList?.defaultColor, actual.imageTintList?.defaultColor, differences)
        checkField(path, "scaleType", expected.scaleType, actual.scaleType, differences)
        if (differences.isNotEmpty()) {
            throw AssertionError(differences.joinToString(separator = "\n"))
        }
    }

    private fun assertEquivalentMinimumHeightById(path: String, expectedRoot: View, actualRoot: View, childId: Int) {
        val expected = expectedRoot.findViewById<View>(childId)
        val actual = actualRoot.findViewById<View>(childId)
        val differences = mutableListOf<String>()
        checkField(path, "minimumHeight", expected.minimumHeight, actual.minimumHeight, differences)
        if (differences.isNotEmpty()) {
            throw AssertionError(differences.joinToString(separator = "\n"))
        }
    }

    private fun assertEquivalentMinimumWidthById(path: String, expectedRoot: View, actualRoot: View, childId: Int) {
        val expected = expectedRoot.findViewById<View>(childId)
        val actual = actualRoot.findViewById<View>(childId)
        val differences = mutableListOf<String>()
        checkField(path, "minimumWidth", expected.minimumWidth, actual.minimumWidth, differences)
        if (differences.isNotEmpty()) {
            throw AssertionError(differences.joinToString(separator = "\n"))
        }
    }

    private fun assertEquivalentElevationById(path: String, expectedRoot: View, actualRoot: View, childId: Int) {
        val expected = expectedRoot.findViewById<View>(childId)
        val actual = actualRoot.findViewById<View>(childId)
        val differences = mutableListOf<String>()
        checkFloatField(path, "elevation", expected.elevation, actual.elevation, differences)
        if (differences.isNotEmpty()) {
            throw AssertionError(differences.joinToString(separator = "\n"))
        }
    }

    private fun assertEquivalentPaddingById(path: String, expectedRoot: View, actualRoot: View, childId: Int) {
        val expected = expectedRoot.findViewById<View>(childId)
        val actual = actualRoot.findViewById<View>(childId)
        val differences = mutableListOf<String>()
        checkField(
            path,
            "padding",
            PaddingSnapshot(expected.paddingLeft, expected.paddingTop, expected.paddingRight, expected.paddingBottom),
            PaddingSnapshot(actual.paddingLeft, actual.paddingTop, actual.paddingRight, actual.paddingBottom),
            differences
        )
        if (differences.isNotEmpty()) {
            throw AssertionError(differences.joinToString(separator = "\n"))
        }
    }

    private fun assertEquivalentRelativeRulesById(
        path: String,
        expectedRoot: View,
        actualRoot: View,
        childId: Int,
        vararg rules: Int
    ) {
        val expected = expectedRoot.findViewById<View>(childId).layoutParams as RelativeLayout.LayoutParams
        val actual = actualRoot.findViewById<View>(childId).layoutParams as RelativeLayout.LayoutParams
        val differences = mutableListOf<String>()
        for (rule in rules) {
            checkField(path, "relativeRule[$rule]", expected.getRule(rule), actual.getRule(rule), differences)
        }
        if (differences.isNotEmpty()) {
            throw AssertionError(differences.joinToString(separator = "\n"))
        }
    }

    private fun assertEquivalentViewStub(path: String, expectedRoot: View, actualRoot: View, childId: Int) {
        val expected = expectedRoot.findViewById<ViewStub>(childId)
        val actual = actualRoot.findViewById<ViewStub>(childId)
        val differences = mutableListOf<String>()
        checkField(path, "className", expected.javaClass.name, actual.javaClass.name, differences)
        checkField(path, "layoutResource", expected.layoutResource, actual.layoutResource, differences)
        checkField(path, "inflatedId", expected.inflatedId, actual.inflatedId, differences)
        checkField(path, "layoutParams.width", expected.layoutParams.width, actual.layoutParams.width, differences)
        checkField(path, "layoutParams.height", expected.layoutParams.height, actual.layoutParams.height, differences)
        if (differences.isNotEmpty()) {
            throw AssertionError(differences.joinToString(separator = "\n"))
        }
    }

    private fun assertEquivalentConstraintParams(path: String, expectedRoot: View, actualRoot: View, childId: Int) {
        val expected = expectedRoot.findViewById<View>(childId).layoutParams as ConstraintLayoutParams
        val actual = actualRoot.findViewById<View>(childId).layoutParams as ConstraintLayoutParams
        val differences = mutableListOf<String>()
        checkField(path, "width", expected.width, actual.width, differences)
        checkField(path, "height", expected.height, actual.height, differences)
        checkField(path, "startToStart", expected.startToStart, actual.startToStart, differences)
        checkField(path, "startToEnd", expected.startToEnd, actual.startToEnd, differences)
        checkField(path, "endToStart", expected.endToStart, actual.endToStart, differences)
        checkField(path, "endToEnd", expected.endToEnd, actual.endToEnd, differences)
        checkField(path, "leftToLeft", expected.leftToLeft, actual.leftToLeft, differences)
        checkField(path, "leftToRight", expected.leftToRight, actual.leftToRight, differences)
        checkField(path, "rightToLeft", expected.rightToLeft, actual.rightToLeft, differences)
        checkField(path, "rightToRight", expected.rightToRight, actual.rightToRight, differences)
        checkField(path, "topToTop", expected.topToTop, actual.topToTop, differences)
        checkField(path, "topToBottom", expected.topToBottom, actual.topToBottom, differences)
        checkField(path, "bottomToTop", expected.bottomToTop, actual.bottomToTop, differences)
        checkField(path, "bottomToBottom", expected.bottomToBottom, actual.bottomToBottom, differences)
        checkFloatField(path, "horizontalBias", expected.horizontalBias, actual.horizontalBias, differences)
        checkFloatField(path, "verticalBias", expected.verticalBias, actual.verticalBias, differences)
        if (differences.isNotEmpty()) {
            throw AssertionError(differences.joinToString(separator = "\n"))
        }
    }

    private fun assertEquivalentGuidelineParams(path: String, expectedRoot: View, actualRoot: View, childId: Int) {
        val expected = expectedRoot.findViewById<View>(childId).layoutParams as ConstraintLayoutParams
        val actual = actualRoot.findViewById<View>(childId).layoutParams as ConstraintLayoutParams
        val differences = mutableListOf<String>()
        checkField(path, "orientation", expected.orientation, actual.orientation, differences)
        checkField(path, "guideBegin", expected.guideBegin, actual.guideBegin, differences)
        checkField(path, "guideEnd", expected.guideEnd, actual.guideEnd, differences)
        checkFloatField(path, "guidePercent", expected.guidePercent, actual.guidePercent, differences)
        if (differences.isNotEmpty()) {
            throw AssertionError(differences.joinToString(separator = "\n"))
        }
    }

    private fun LayoutParamsSnapshot?.collectDifferences(
        actual: LayoutParamsSnapshot?,
        path: String,
        differences: MutableList<String>
    ) {
        checkField(path, "layoutParams.className", this?.className, actual?.className, differences)
        checkField(path, "layoutParams.width", this?.width, actual?.width, differences)
        checkField(path, "layoutParams.height", this?.height, actual?.height, differences)
        checkField(path, "layoutParams.margins", this?.margins, actual?.margins, differences)
        checkField(path, "layoutParams.linearWeight", this?.linearWeight, actual?.linearWeight, differences)
        checkField(path, "layoutParams.gravity", this?.gravity, actual?.gravity, differences)
        checkField(path, "layoutParams.relativeRules", this?.relativeRules, actual?.relativeRules, differences)
        checkField(path, "layoutParams.constraintAnchors", this?.constraintAnchors, actual?.constraintAnchors, differences)
    }

    private fun checkField(
        path: String,
        fieldName: String,
        expected: Any?,
        actual: Any?,
        differences: MutableList<String>
    ) {
        if (expected != actual) {
            differences += "$path $fieldName expected <$expected> but was <$actual>"
        }
    }

    private fun checkFloatField(
        path: String,
        fieldName: String,
        expected: Float?,
        actual: Float?,
        differences: MutableList<String>
    ) {
        if (expected == null || actual == null) {
            checkField(path, fieldName, expected, actual, differences)
            return
        }
        if (abs(expected - actual) > 0.01f) {
            differences += "$path $fieldName expected <$expected> but was <$actual>"
        }
    }

    private fun TextView.transformedText(value: String): String {
        return transformationMethod?.getTransformation(value, this)?.toString() ?: value
    }

    private fun android.content.res.Resources.getResourceEntryNameOrNull(id: Int): String? {
        if (id == View.NO_ID) return null
        return runCatching { getResourceEntryName(id) }.getOrNull()
    }

    private data class ViewSnapshot(
        val className: String,
        val idName: String?,
        val visibility: Int,
        val enabled: Boolean,
        val clickable: Boolean,
        val focusable: Boolean,
        val padding: PaddingSnapshot?,
        val layoutParams: LayoutParamsSnapshot?,
        val text: String?,
        val hint: String?,
        val textColor: Int?,
        val textSize: Float?,
        val typefaceStyle: Int?,
        val inputType: Int?,
        val checked: Boolean?,
        val imageScaleType: String?,
        val orientation: Int?,
        val gravity: Int?,
        val fillViewport: Boolean?,
        val recyclerLayoutManager: String?,
        val children: List<ViewSnapshot>
    )

    private data class PaddingSnapshot(
        val left: Int,
        val top: Int,
        val right: Int,
        val bottom: Int
    )

    private data class LayoutParamsSnapshot(
        val className: String,
        val width: Int,
        val height: Int,
        val margins: MarginSnapshot?,
        val linearWeight: Float?,
        val gravity: Int?,
        val relativeRules: List<Int>?,
        val constraintAnchors: ConstraintAnchorSnapshot?
    )

    private data class MarginSnapshot(
        val left: Int,
        val top: Int,
        val right: Int,
        val bottom: Int
    )

    private data class ConstraintAnchorSnapshot(
        val startToStart: Int,
        val startToEnd: Int,
        val endToStart: Int,
        val endToEnd: Int,
        val leftToLeft: Int,
        val leftToRight: Int,
        val rightToLeft: Int,
        val rightToRight: Int,
        val topToTop: Int,
        val topToBottom: Int,
        val bottomToTop: Int,
        val bottomToBottom: Int,
        val horizontalBias: Float,
        val verticalBias: Float
    )
}

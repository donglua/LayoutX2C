package com.github.donglua.layoutx2c.registry

import com.github.donglua.layoutx2c.analyzer.LayoutAnalyzer
import com.github.donglua.layoutx2c.codegen.LayoutCodeGenerator
import com.github.donglua.layoutx2c.parser.XmlLayoutParser
import com.google.common.truth.Truth.assertThat
import org.junit.Test

class ViewRegistryCoverageTest {

    private val parser = XmlLayoutParser()

    @Test
    fun `custom view attributes emit every supported value kind`() {
        val registry = ResourceAwareViewRegistry(
            rPackageName = "com.example",
            customViews = listOf(
                CustomViewDescriptor(
                    viewClassName = "com.example.widget.FancyView",
                    attributes = listOf(
                        CustomViewAttribute("app:title-text", CustomViewAttributeKind.STRING),
                        CustomViewAttribute("app:label", CustomViewAttributeKind.STRING),
                        CustomViewAttribute("app:visible", CustomViewAttributeKind.BOOLEAN),
                        CustomViewAttribute("app:count", CustomViewAttributeKind.INT),
                        CustomViewAttribute("app:ratio", CustomViewAttributeKind.FLOAT),
                        CustomViewAttribute("app:gap", CustomViewAttributeKind.DIMENSION),
                        CustomViewAttribute("app:hexColor", CustomViewAttributeKind.COLOR),
                        CustomViewAttribute("app:refColor", CustomViewAttributeKind.COLOR),
                        CustomViewAttribute("app:hexTint", CustomViewAttributeKind.COLOR_STATE_LIST),
                        CustomViewAttribute("app:refTint", CustomViewAttributeKind.COLOR_STATE_LIST),
                        CustomViewAttribute("app:icon", CustomViewAttributeKind.DRAWABLE_REF),
                        CustomViewAttribute("app:anyRef", CustomViewAttributeKind.RESOURCE_REF)
                    )
                )
            )
        )
        val xml = """
            <com.example.widget.FancyView xmlns:android="http://schemas.android.com/apk/res/android"
                xmlns:app="http://schemas.android.com/apk/res-auto"
                android:layout_width="wrap_content"
                android:layout_height="wrap_content"
                app:title-text="@string/title"
                app:label="literal"
                app:visible="true"
                app:count="7"
                app:ratio="1.5"
                app:gap="8dp"
                app:hexColor="#123456"
                app:refColor="@color/accent"
                app:hexTint="#654321"
                app:refTint="@color/selector"
                app:icon="@drawable/ic_demo"
                app:anyRef="@layout/other" />
        """.trimIndent()

        val generated = generate(xml, "custom_attr_kinds", registry)

        assertThat(generated).contains("setTitleText(context.getString(R.string.title))")
        assertThat(generated).contains("setLabel(\"literal\")")
        assertThat(generated).contains("setVisible(true)")
        assertThat(generated).contains("setCount(7)")
        assertThat(generated).contains("setRatio(1.5f)")
        assertThat(generated).contains("setGap((8f * density + 0.5f).toInt())")
        assertThat(generated).contains("setHexColor(Color.parseColor(\"#123456\"))")
        assertThat(generated).contains("setRefColor(ContextCompat.getColor(context, R.color.accent))")
        assertThat(generated).contains("setHexTint(ColorStateList.valueOf(Color.parseColor(\"#654321\")))")
        assertThat(generated).contains("setRefTint(ContextCompat.getColorStateList(context, R.color.selector))")
        assertThat(generated).contains("setIcon(ContextCompat.getDrawable(context, R.drawable.ic_demo))")
        assertThat(generated).contains("setAnyRef(R.layout.other)")
    }

    @Test
    fun `text and common presentation enums emit all supported alternatives`() {
        val xml = """
            <LinearLayout xmlns:android="http://schemas.android.com/apk/res/android"
                android:layout_width="match_parent"
                android:layout_height="match_parent"
                android:orientation="vertical">
                <TextView
                    android:layout_width="wrap_content"
                    android:layout_height="wrap_content"
                    android:ellipsize="start"
                    android:textStyle="italic" />
                <TextView
                    android:layout_width="wrap_content"
                    android:layout_height="wrap_content"
                    android:ellipsize="end"
                    android:textStyle="normal" />
                <TextView
                    android:layout_width="wrap_content"
                    android:layout_height="wrap_content"
                    android:ellipsize="marquee" />
                <View
                    android:layout_width="1dp"
                    android:layout_height="1dp"
                    android:importantForAccessibility="auto"
                    android:overScrollMode="always"
                    android:scrollbars="none" />
                <View
                    android:layout_width="1dp"
                    android:layout_height="1dp"
                    android:importantForAccessibility="no"
                    android:overScrollMode="ifContentScrolls"
                    android:scrollbars="horizontal" />
                <View
                    android:layout_width="1dp"
                    android:layout_height="1dp"
                    android:importantForAccessibility="noHideDescendants"
                    android:scrollbars="horizontal|vertical" />
            </LinearLayout>
        """.trimIndent()

        val generated = generate(xml, "presentation_enum_attrs")

        assertThat(generated).contains("ellipsize = TextUtils.TruncateAt.START")
        assertThat(generated).contains("ellipsize = TextUtils.TruncateAt.END")
        assertThat(generated).contains("ellipsize = TextUtils.TruncateAt.MARQUEE")
        assertThat(generated).contains("setTypeface(typeface, android.graphics.Typeface.ITALIC)")
        assertThat(generated).contains("setTypeface(typeface, android.graphics.Typeface.NORMAL)")
        assertThat(generated).contains("importantForAccessibility = View.IMPORTANT_FOR_ACCESSIBILITY_AUTO")
        assertThat(generated).contains("importantForAccessibility = View.IMPORTANT_FOR_ACCESSIBILITY_NO")
        assertThat(generated).contains("importantForAccessibility = View.IMPORTANT_FOR_ACCESSIBILITY_NO_HIDE_DESCENDANTS")
        assertThat(generated).contains("overScrollMode = View.OVER_SCROLL_ALWAYS")
        assertThat(generated).contains("overScrollMode = View.OVER_SCROLL_IF_CONTENT_SCROLLS")
        assertThat(generated).contains("isHorizontalScrollBarEnabled = true")
        assertThat(generated).contains("isVerticalScrollBarEnabled = true")
    }

    @Test
    fun `edit text input and ime option enums emit remaining constants`() {
        val xml = """
            <LinearLayout xmlns:android="http://schemas.android.com/apk/res/android"
                android:layout_width="match_parent"
                android:layout_height="match_parent"
                android:orientation="vertical">
                <EditText android:layout_width="match_parent" android:layout_height="wrap_content" android:inputType="none" />
                <EditText android:layout_width="match_parent" android:layout_height="wrap_content" android:inputType="textCapCharacters" />
                <EditText android:layout_width="match_parent" android:layout_height="wrap_content" android:inputType="textCapSentences" />
                <EditText android:layout_width="match_parent" android:layout_height="wrap_content" android:inputType="textAutoCorrect" />
                <EditText android:layout_width="match_parent" android:layout_height="wrap_content" android:inputType="textAutoComplete" />
                <EditText android:layout_width="match_parent" android:layout_height="wrap_content" android:inputType="textMultiLine" />
                <EditText android:layout_width="match_parent" android:layout_height="wrap_content" android:inputType="textNoSuggestions" />
                <EditText android:layout_width="match_parent" android:layout_height="wrap_content" android:inputType="textEmailSubject" />
                <EditText android:layout_width="match_parent" android:layout_height="wrap_content" android:inputType="textUri" />
                <EditText android:layout_width="match_parent" android:layout_height="wrap_content" android:inputType="textPassword" />
                <EditText android:layout_width="match_parent" android:layout_height="wrap_content" android:inputType="textVisiblePassword" />
                <EditText android:layout_width="match_parent" android:layout_height="wrap_content" android:inputType="textWebEditText" />
                <EditText android:layout_width="match_parent" android:layout_height="wrap_content" android:inputType="textFilter" />
                <EditText android:layout_width="match_parent" android:layout_height="wrap_content" android:inputType="textPostalAddress" />
                <EditText android:layout_width="match_parent" android:layout_height="wrap_content" android:inputType="number" />
                <EditText android:layout_width="match_parent" android:layout_height="wrap_content" android:inputType="numberSigned" />
                <EditText android:layout_width="match_parent" android:layout_height="wrap_content" android:inputType="numberPassword" />
                <EditText android:layout_width="match_parent" android:layout_height="wrap_content" android:inputType="phone" />
                <EditText android:layout_width="match_parent" android:layout_height="wrap_content" android:inputType="datetime" />
                <EditText android:layout_width="match_parent" android:layout_height="wrap_content" android:inputType="date" />
                <EditText android:layout_width="match_parent" android:layout_height="wrap_content" android:inputType="time" />
                <EditText
                    android:layout_width="match_parent"
                    android:layout_height="wrap_content"
                    android:imeOptions="actionUnspecified|actionNone|actionGo|actionSearch|actionSend|actionNext|actionPrevious|flagNoFullscreen|flagNavigatePrevious|flagNavigateNext|flagNoAccessoryAction|flagNoEnterAction|flagForceAscii|flagNoPersonalizedLearning" />
            </LinearLayout>
        """.trimIndent()

        val generated = generate(xml, "edit_text_remaining_enums")

        assertThat(generated).contains("inputType = android.text.InputType.TYPE_NULL")
        assertThat(generated).contains("TYPE_TEXT_FLAG_CAP_CHARACTERS")
        assertThat(generated).contains("TYPE_TEXT_FLAG_CAP_SENTENCES")
        assertThat(generated).contains("TYPE_TEXT_FLAG_AUTO_CORRECT")
        assertThat(generated).contains("TYPE_TEXT_FLAG_AUTO_COMPLETE")
        assertThat(generated).contains("TYPE_TEXT_FLAG_MULTI_LINE")
        assertThat(generated).contains("TYPE_TEXT_FLAG_NO_SUGGESTIONS")
        assertThat(generated).contains("TYPE_TEXT_VARIATION_EMAIL_SUBJECT")
        assertThat(generated).contains("TYPE_TEXT_VARIATION_URI")
        assertThat(generated).contains("TYPE_TEXT_VARIATION_PASSWORD")
        assertThat(generated).contains("TYPE_TEXT_VARIATION_VISIBLE_PASSWORD")
        assertThat(generated).contains("TYPE_TEXT_VARIATION_WEB_EDIT_TEXT")
        assertThat(generated).contains("TYPE_TEXT_VARIATION_FILTER")
        assertThat(generated).contains("TYPE_TEXT_VARIATION_POSTAL_ADDRESS")
        assertThat(generated).contains("TYPE_CLASS_NUMBER")
        assertThat(generated).contains("TYPE_NUMBER_FLAG_SIGNED")
        assertThat(generated).contains("TYPE_NUMBER_VARIATION_PASSWORD")
        assertThat(generated).contains("TYPE_CLASS_PHONE")
        assertThat(generated).contains("TYPE_CLASS_DATETIME")
        assertThat(generated).contains("TYPE_DATETIME_VARIATION_DATE")
        assertThat(generated).contains("TYPE_DATETIME_VARIATION_TIME")
        assertThat(generated).contains("IME_ACTION_UNSPECIFIED")
        assertThat(generated).contains("IME_ACTION_NONE")
        assertThat(generated).contains("IME_ACTION_GO")
        assertThat(generated).contains("IME_ACTION_SEARCH")
        assertThat(generated).contains("IME_ACTION_SEND")
        assertThat(generated).contains("IME_ACTION_NEXT")
        assertThat(generated).contains("IME_ACTION_PREVIOUS")
        assertThat(generated).contains("IME_FLAG_NO_FULLSCREEN")
        assertThat(generated).contains("IME_FLAG_NAVIGATE_PREVIOUS")
        assertThat(generated).contains("IME_FLAG_NAVIGATE_NEXT")
        assertThat(generated).contains("IME_FLAG_NO_ACCESSORY_ACTION")
        assertThat(generated).contains("IME_FLAG_NO_ENTER_ACTION")
        assertThat(generated).contains("IME_FLAG_FORCE_ASCII")
        assertThat(generated).contains("IME_FLAG_NO_PERSONALIZED_LEARNING")
    }

    private fun generate(
        xml: String,
        layoutName: String,
        registry: ResourceAwareViewRegistry = DefaultViewRegistry
    ): String {
        val analyzed = LayoutAnalyzer(registry).analyze(parser.parse(xml, layoutName).root)
        return LayoutCodeGenerator(
            packageName = "com.example.generated",
            rPackageName = "com.example",
            viewRegistry = registry
        ).generate(analyzed, layoutName, "R.layout.$layoutName").toString()
    }
}

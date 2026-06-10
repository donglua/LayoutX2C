package com.github.donglua.layoutx2c.parser

import com.google.common.truth.Truth.assertThat
import org.junit.Test

class LayoutNodeTypesTest {

    @Test
    fun `classifies framework and appcompat node tags`() {
        assertThat(node("android.widget.LinearLayout").isLinearLayout()).isTrue()
        assertThat(node("android.widget.FrameLayout").isFrameLayout()).isTrue()
        assertThat(node("android.widget.RelativeLayout").isRelativeLayout()).isTrue()
        assertThat(node("android.widget.TextView").isTextView()).isTrue()
        assertThat(node("TextView").isTextLikeView()).isTrue()
        assertThat(node("android.widget.Button").isButton()).isTrue()
        assertThat(node("androidx.appcompat.widget.AppCompatButton").isButton()).isTrue()
        assertThat(node("Button").isTextLikeView()).isTrue()
        assertThat(node("android.widget.EditText").isEditText()).isTrue()
        assertThat(node("androidx.appcompat.widget.AppCompatEditText").isEditText()).isTrue()
        assertThat(node("EditText").isTextLikeView()).isTrue()
        assertThat(node("android.widget.CompoundButton").isCompoundButton()).isTrue()
        assertThat(node("androidx.appcompat.widget.AppCompatCheckBox").isCompoundButton()).isTrue()
        assertThat(node("CheckBox").isTextLikeView()).isTrue()
        assertThat(node("android.widget.Switch").isCompoundButton()).isTrue()
        assertThat(node("androidx.appcompat.widget.SwitchCompat").isCompoundButton()).isTrue()
        assertThat(node("android.widget.RadioButton").isCompoundButton()).isTrue()
        assertThat(node("androidx.appcompat.widget.AppCompatRadioButton").isCompoundButton()).isTrue()
        assertThat(node("android.widget.ToggleButton").isCompoundButton()).isTrue()
        assertThat(node("android.widget.ImageView").isImageView()).isTrue()
        assertThat(node("androidx.appcompat.widget.AppCompatImageView").isImageView()).isTrue()
        assertThat(node("android.widget.ScrollView").isScrollView()).isTrue()
        assertThat(node("HorizontalScrollView").isScrollView()).isTrue()
        assertThat(node("android.widget.HorizontalScrollView").isScrollView()).isTrue()
        assertThat(node("android.widget.ProgressBar").isProgressBar()).isTrue()
        assertThat(node("android.widget.SeekBar").isSeekBar()).isTrue()
        assertThat(node("android.widget.RatingBar").isRatingBar()).isTrue()
    }

    @Test
    fun `classifies material and androidx node tags`() {
        assertThat(node("androidx.recyclerview.widget.RecyclerView").isRecyclerView()).isTrue()
        assertThat(node("androidx.cardview.widget.CardView").isCardView()).isTrue()
        assertThat(node("com.google.android.material.card.MaterialCardView").isCardView()).isTrue()
        assertThat(node("com.google.android.material.card.MaterialCardView").isMaterialCardView()).isTrue()
        assertThat(node("androidx.appcompat.widget.Toolbar").isToolbar()).isTrue()
        assertThat(node("com.google.android.material.appbar.MaterialToolbar").isToolbar()).isTrue()
        assertThat(node("androidx.constraintlayout.widget.ConstraintLayout").isConstraintLayout()).isTrue()
    }

    @Test
    fun `does not classify unrelated tags`() {
        val custom = node("com.example.CustomView")

        assertThat(custom.isLinearLayout()).isFalse()
        assertThat(custom.isFrameLayout()).isFalse()
        assertThat(custom.isRelativeLayout()).isFalse()
        assertThat(custom.isTextView()).isFalse()
        assertThat(custom.isButton()).isFalse()
        assertThat(custom.isEditText()).isFalse()
        assertThat(custom.isTextLikeView()).isFalse()
        assertThat(custom.isCompoundButton()).isFalse()
        assertThat(custom.isImageView()).isFalse()
        assertThat(custom.isScrollView()).isFalse()
        assertThat(custom.isRecyclerView()).isFalse()
        assertThat(custom.isProgressBar()).isFalse()
        assertThat(custom.isSeekBar()).isFalse()
        assertThat(custom.isRatingBar()).isFalse()
        assertThat(custom.isCardView()).isFalse()
        assertThat(custom.isMaterialCardView()).isFalse()
        assertThat(custom.isToolbar()).isFalse()
        assertThat(custom.isConstraintLayout()).isFalse()
    }

    private fun node(tagName: String): LayoutNode {
        return LayoutNode(tagName = tagName, attributes = emptyMap(), children = emptyList())
    }
}

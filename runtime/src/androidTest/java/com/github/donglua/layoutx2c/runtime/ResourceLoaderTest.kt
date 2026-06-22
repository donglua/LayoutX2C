package com.github.donglua.layoutx2c.runtime

import android.content.Context
import android.content.res.ColorStateList
import android.graphics.Color
import android.graphics.drawable.ColorDrawable
import android.graphics.drawable.Drawable
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import org.junit.After
import org.junit.Assert.*
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class ResourceLoaderTest {

    private lateinit var context: Context

    @Before
    fun setup() {
        context = InstrumentationRegistry.getInstrumentation().targetContext
        // 重置为默认加载器
        ResourceLoaderRegistry.reset()
    }

    @After
    fun tearDown() {
        ResourceLoaderRegistry.reset()
    }

    @Test
    fun testDefaultResourceLoader() {
        // 默认使用 Android 原生 API
        val color = ResourceCompat.getColor(context, android.R.color.black)
        assertEquals(Color.BLACK, color)
    }

    @Test
    fun testCustomResourceLoader() {
        // 自定义加载器：所有颜色返回红色
        ResourceLoaderRegistry.setResourceLoader(object : ResourceLoader {
            override fun getColor(context: Context, colorRes: Int): Int {
                return Color.RED
            }

            override fun getColorStateList(context: Context, colorRes: Int): ColorStateList? {
                return ColorStateList.valueOf(Color.RED)
            }

            override fun getDrawable(context: Context, drawableRes: Int): Drawable? {
                return ColorDrawable(Color.RED)
            }

            override fun getDimension(context: Context, dimenRes: Int): Float {
                return 100f
            }

            override fun getString(context: Context, stringRes: Int): String {
                return "Custom String"
            }
        })

        // 验证自定义加载器生效
        val color = ResourceCompat.getColor(context, android.R.color.black)
        assertEquals(Color.RED, color)

        val colorStateList = ResourceCompat.getColorStateList(context, android.R.color.black)
        assertNotNull(colorStateList)
        assertEquals(Color.RED, colorStateList?.defaultColor)

        val drawable = ResourceCompat.getDrawable(context, android.R.drawable.ic_menu_camera)
        assertTrue(drawable is ColorDrawable)
        assertEquals(Color.RED, (drawable as ColorDrawable).color)

        val string = ResourceCompat.getString(context, android.R.string.ok)
        assertEquals("Custom String", string)
    }

    @Test
    fun testSwitchResourceLoader() {
        // 第一个加载器：返回红色
        ResourceLoaderRegistry.setResourceLoader(object : ResourceLoader {
            override fun getColor(context: Context, colorRes: Int) = Color.RED
            override fun getColorStateList(context: Context, colorRes: Int) = null
            override fun getDrawable(context: Context, drawableRes: Int) = null
            override fun getDimension(context: Context, dimenRes: Int) = 0f
            override fun getString(context: Context, stringRes: Int) = ""
        })

        assertEquals(Color.RED, ResourceCompat.getColor(context, android.R.color.black))

        // 切换到第二个加载器：返回蓝色
        ResourceLoaderRegistry.setResourceLoader(object : ResourceLoader {
            override fun getColor(context: Context, colorRes: Int) = Color.BLUE
            override fun getColorStateList(context: Context, colorRes: Int) = null
            override fun getDrawable(context: Context, drawableRes: Int) = null
            override fun getDimension(context: Context, dimenRes: Int) = 0f
            override fun getString(context: Context, stringRes: Int) = ""
        })

        assertEquals(Color.BLUE, ResourceCompat.getColor(context, android.R.color.black))
    }

    @Test
    fun testResetToDefault() {
        // 设置自定义加载器
        ResourceLoaderRegistry.setResourceLoader(object : ResourceLoader {
            override fun getColor(context: Context, colorRes: Int) = Color.RED
            override fun getColorStateList(context: Context, colorRes: Int) = null
            override fun getDrawable(context: Context, drawableRes: Int) = null
            override fun getDimension(context: Context, dimenRes: Int) = 0f
            override fun getString(context: Context, stringRes: Int) = ""
        })

        assertEquals(Color.RED, ResourceCompat.getColor(context, android.R.color.black))

        // 重置为默认
        ResourceLoaderRegistry.reset()

        val color = ResourceCompat.getColor(context, android.R.color.black)
        assertEquals(Color.BLACK, color)
    }
}

package com.github.donglua.layoutx2c.ksp

import com.github.donglua.layoutx2c.resources.ResourceReference
import com.github.donglua.layoutx2c.resources.ResourceSymbolTable
import com.google.common.truth.Truth.assertThat
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder

class LayoutX2CDigestStoreTest {

    @get:Rule
    val tempDir = TemporaryFolder()

    @Test
    fun `layout digest changes when layout xml changes`() {
        val projectDir = tempDir.newFolder("layoutx2c-digest")
        val resDir = projectDir.resolve("src/main/res")
        val layoutFile = resDir.resolve("layout/demo.xml")
        layoutFile.parentFile.mkdirs()
        layoutFile.writeText("<LinearLayout android:id=\"@+id/root\" />")

        val first = LayoutX2CDigestCalculator.layoutDigest(
            layoutFile = layoutFile,
            resDir = resDir,
            packageName = "com.example.generated",
            rPackageName = "com.example"
        )

        layoutFile.writeText("<FrameLayout android:id=\"@+id/root\" />")

        val second = LayoutX2CDigestCalculator.layoutDigest(
            layoutFile = layoutFile,
            resDir = resDir,
            packageName = "com.example.generated",
            rPackageName = "com.example"
        )

        assertThat(second).isNotEqualTo(first)
    }

    @Test
    fun `layout digest includes layout relative path`() {
        val projectDir = tempDir.newFolder("layoutx2c-path-digest")
        val resDir = projectDir.resolve("src/main/res")
        val defaultLayout = resDir.resolve("layout/demo.xml")
        val landscapeLayout = resDir.resolve("layout-land/demo.xml")
        defaultLayout.parentFile.mkdirs()
        landscapeLayout.parentFile.mkdirs()
        defaultLayout.writeText("<TextView android:id=\"@+id/title\" />")
        landscapeLayout.writeText("<TextView android:id=\"@+id/title\" />")

        val first = LayoutX2CDigestCalculator.layoutDigest(
            layoutFile = defaultLayout,
            resDir = resDir,
            packageName = "com.example.generated",
            rPackageName = "com.example"
        )

        val second = LayoutX2CDigestCalculator.layoutDigest(
            layoutFile = landscapeLayout,
            resDir = resDir,
            packageName = "com.example.generated",
            rPackageName = "com.example"
        )

        assertThat(second).isNotEqualTo(first)
    }

    @Test
    fun `layout digest changes when values resource changes`() {
        val projectDir = tempDir.newFolder("layoutx2c-values-digest")
        val resDir = projectDir.resolve("src/main/res")
        val layoutFile = resDir.resolve("layout/demo.xml")
        val valuesFile = resDir.resolve("values/colors.xml")
        layoutFile.parentFile.mkdirs()
        valuesFile.parentFile.mkdirs()
        layoutFile.writeText("<TextView android:textColor=\"@color/title\" />")
        valuesFile.writeText("<resources><color name=\"title\">#111111</color></resources>")

        val first = LayoutX2CDigestCalculator.layoutDigest(
            layoutFile = layoutFile,
            resDir = resDir,
            packageName = "com.example.generated",
            rPackageName = "com.example"
        )

        valuesFile.writeText("<resources><color name=\"title\">#222222</color></resources>")

        val second = LayoutX2CDigestCalculator.layoutDigest(
            layoutFile = layoutFile,
            resDir = resDir,
            packageName = "com.example.generated",
            rPackageName = "com.example"
        )

        assertThat(second).isNotEqualTo(first)
    }

    @Test
    fun `layout digest changes when resource owner changes`() {
        val projectDir = tempDir.newFolder("layoutx2c-owner-digest")
        val resDir = projectDir.resolve("src/main/res")
        val layoutFile = resDir.resolve("layout/demo.xml")
        layoutFile.parentFile.mkdirs()
        layoutFile.writeText("<TextView android:textColor=\"@color/base_divider\" />")
        val reference = ResourceReference("color", "base_divider")

        val first = LayoutX2CDigestCalculator.layoutDigest(
            layoutFile = layoutFile,
            resDir = resDir,
            packageName = "com.example.generated",
            rPackageName = "com.example",
            resourceSymbolsKey = ResourceSymbolTable(
                references = setOf(reference),
                owners = mapOf(reference to "com.example")
            ).stableKey()
        )

        val second = LayoutX2CDigestCalculator.layoutDigest(
            layoutFile = layoutFile,
            resDir = resDir,
            packageName = "com.example.generated",
            rPackageName = "com.example",
            resourceSymbolsKey = ResourceSymbolTable(
                references = setOf(reference),
                owners = mapOf(reference to "com.example.base")
            ).stableKey()
        )

        assertThat(second).isNotEqualTo(first)
    }

    @Test
    fun `layout digest ignores unrelated values resource changes`() {
        val projectDir = tempDir.newFolder("layoutx2c-unrelated-values-digest")
        val resDir = projectDir.resolve("src/main/res")
        val layoutFile = resDir.resolve("layout/demo.xml")
        val valuesFile = resDir.resolve("values/strings.xml")
        layoutFile.parentFile.mkdirs()
        valuesFile.parentFile.mkdirs()
        layoutFile.writeText("<TextView android:text=\"@string/title\" />")
        valuesFile.writeText(
            """
            <resources>
                <string name="title">Title</string>
                <string name="unused">Before</string>
            </resources>
            """.trimIndent()
        )

        val first = LayoutX2CDigestCalculator.layoutDigest(
            layoutFile = layoutFile,
            resDir = resDir,
            packageName = "com.example.generated",
            rPackageName = "com.example"
        )

        valuesFile.writeText(
            """
            <resources>
                <string name="title">Title</string>
                <string name="unused">After</string>
            </resources>
            """.trimIndent()
        )

        val second = LayoutX2CDigestCalculator.layoutDigest(
            layoutFile = layoutFile,
            resDir = resDir,
            packageName = "com.example.generated",
            rPackageName = "com.example"
        )

        assertThat(second).isEqualTo(first)
    }

    @Test
    fun `layout digest changes when referenced nested value resource changes`() {
        val projectDir = tempDir.newFolder("layoutx2c-nested-values-digest")
        val resDir = projectDir.resolve("src/main/res")
        val layoutFile = resDir.resolve("layout/demo.xml")
        val valuesFile = resDir.resolve("values/strings.xml")
        layoutFile.parentFile.mkdirs()
        valuesFile.parentFile.mkdirs()
        layoutFile.writeText("<TextView android:text=\"@string/title\" />")
        valuesFile.writeText(
            """
            <resources>
                <string name="title">@string/app_name</string>
                <string name="app_name">Before</string>
            </resources>
            """.trimIndent()
        )

        val first = LayoutX2CDigestCalculator.layoutDigest(
            layoutFile = layoutFile,
            resDir = resDir,
            packageName = "com.example.generated",
            rPackageName = "com.example"
        )

        valuesFile.writeText(
            """
            <resources>
                <string name="title">@string/app_name</string>
                <string name="app_name">After</string>
            </resources>
            """.trimIndent()
        )

        val second = LayoutX2CDigestCalculator.layoutDigest(
            layoutFile = layoutFile,
            resDir = resDir,
            packageName = "com.example.generated",
            rPackageName = "com.example"
        )

        assertThat(second).isNotEqualTo(first)
    }

    @Test
    fun `layout digest changes when referenced drawable file changes`() {
        val projectDir = tempDir.newFolder("layoutx2c-drawable-digest")
        val resDir = projectDir.resolve("src/main/res")
        val layoutFile = resDir.resolve("layout/demo.xml")
        val drawableFile = resDir.resolve("drawable/logo.png")
        layoutFile.parentFile.mkdirs()
        drawableFile.parentFile.mkdirs()
        layoutFile.writeText("<ImageView android:src=\"@drawable/logo\" />")
        drawableFile.writeBytes(byteArrayOf(0x00, 0x7f, 0x50, 0x4e, 0x47, 0x01, 0x02))

        val first = LayoutX2CDigestCalculator.layoutDigest(
            layoutFile = layoutFile,
            resDir = resDir,
            packageName = "com.example.generated",
            rPackageName = "com.example"
        )

        drawableFile.writeBytes(byteArrayOf(0x00, 0x7f, 0x50, 0x4e, 0x47, 0x03, 0x04))

        val second = LayoutX2CDigestCalculator.layoutDigest(
            layoutFile = layoutFile,
            resDir = resDir,
            packageName = "com.example.generated",
            rPackageName = "com.example"
        )

        assertThat(second).isNotEqualTo(first)
    }

    @Test
    fun `layout digest changes when missing resource becomes available`() {
        val projectDir = tempDir.newFolder("layoutx2c-missing-resource-digest")
        val resDir = projectDir.resolve("src/main/res")
        val layoutFile = resDir.resolve("layout/demo.xml")
        val valuesFile = resDir.resolve("values/strings.xml")
        layoutFile.parentFile.mkdirs()
        layoutFile.writeText("<TextView android:text=\"@string/later_title\" />")

        val first = LayoutX2CDigestCalculator.layoutDigest(
            layoutFile = layoutFile,
            resDir = resDir,
            packageName = "com.example.generated",
            rPackageName = "com.example"
        )

        valuesFile.parentFile.mkdirs()
        valuesFile.writeText("<resources><string name=\"later_title\">Now present</string></resources>")

        val second = LayoutX2CDigestCalculator.layoutDigest(
            layoutFile = layoutFile,
            resDir = resDir,
            packageName = "com.example.generated",
            rPackageName = "com.example"
        )

        assertThat(second).isNotEqualTo(first)
    }

    @Test
    fun `layout digest changes when included layout changes`() {
        val projectDir = tempDir.newFolder("layoutx2c-include-digest")
        val resDir = projectDir.resolve("src/main/res")
        val layoutDir = resDir.resolve("layout")
        val layoutFile = layoutDir.resolve("host.xml")
        val includedFile = layoutDir.resolve("common_button.xml")
        layoutDir.mkdirs()
        layoutFile.writeText(
            """
            <LinearLayout xmlns:android="http://schemas.android.com/apk/res/android">
                <include layout="@layout/common_button" />
            </LinearLayout>
            """.trimIndent()
        )
        includedFile.writeText("<Button android:text=\"Before\" />")

        val first = LayoutX2CDigestCalculator.layoutDigest(
            layoutFile = layoutFile,
            resDir = resDir,
            packageName = "com.example.generated",
            rPackageName = "com.example"
        )

        includedFile.writeText("<Button android:text=\"After\" />")

        val second = LayoutX2CDigestCalculator.layoutDigest(
            layoutFile = layoutFile,
            resDir = resDir,
            packageName = "com.example.generated",
            rPackageName = "com.example"
        )

        assertThat(second).isNotEqualTo(first)
    }

    @Test
    fun `layout digest changes when missing include becomes available`() {
        val projectDir = tempDir.newFolder("layoutx2c-missing-include-digest")
        val resDir = projectDir.resolve("src/main/res")
        val layoutDir = resDir.resolve("layout")
        val layoutFile = layoutDir.resolve("host.xml")
        val includedFile = layoutDir.resolve("later_panel.xml")
        layoutDir.mkdirs()
        layoutFile.writeText(
            """
            <FrameLayout xmlns:android="http://schemas.android.com/apk/res/android">
                <include layout="@layout/later_panel" />
            </FrameLayout>
            """.trimIndent()
        )

        val first = LayoutX2CDigestCalculator.layoutDigest(
            layoutFile = layoutFile,
            resDir = resDir,
            packageName = "com.example.generated",
            rPackageName = "com.example"
        )

        includedFile.writeText("<TextView android:text=\"Now present\" />")

        val second = LayoutX2CDigestCalculator.layoutDigest(
            layoutFile = layoutFile,
            resDir = resDir,
            packageName = "com.example.generated",
            rPackageName = "com.example"
        )

        assertThat(second).isNotEqualTo(first)
    }

    @Test
    fun `layout digest tolerates malformed included layout`() {
        val projectDir = tempDir.newFolder("layoutx2c-malformed-include-digest")
        val resDir = projectDir.resolve("src/main/res")
        val layoutDir = resDir.resolve("layout")
        val layoutFile = layoutDir.resolve("host.xml")
        val includedFile = layoutDir.resolve("broken_panel.xml")
        layoutDir.mkdirs()
        layoutFile.writeText(
            """
            <FrameLayout xmlns:android="http://schemas.android.com/apk/res/android">
                <include layout="@layout/broken_panel" />
            </FrameLayout>
            """.trimIndent()
        )
        includedFile.writeText("<LinearLayout>")

        val digest = LayoutX2CDigestCalculator.layoutDigest(
            layoutFile = layoutFile,
            resDir = resDir,
            packageName = "com.example.generated",
            rPackageName = "com.example"
        )

        assertThat(digest).isNotEmpty()
    }

    @Test
    fun `layout digest schema is bumped for binding facade generation`() {
        val projectDir = tempDir.newFolder("layoutx2c-schema-digest")
        val resDir = projectDir.resolve("src/main/res")
        val layoutFile = resDir.resolve("layout/demo.xml")
        layoutFile.parentFile.mkdirs()
        layoutFile.writeText("<LinearLayout android:id=\"@+id/root\" />")

        val digest = LayoutX2CDigestCalculator.layoutDigest(
            layoutFile = layoutFile,
            resDir = resDir,
            packageName = "com.example.generated",
            rPackageName = "com.example"
        )

        assertThat(digest).isNotEmpty()
        assertThat(digest).isNotEqualTo(
            "91b2c11d8e6f5d98b00be1f5e18ab1a3c41d18d5808d73f4037beacfb050bb75"
        )
    }

    @Test
    fun `layout digest invalidates caches from older codegen compatibility keys`() {
        val projectDir = tempDir.newFolder("layoutx2c-codegen-compatibility-digest")
        val resDir = projectDir.resolve("src/main/res")
        val layoutFile = resDir.resolve("layout/demo.xml")
        layoutFile.parentFile.mkdirs()
        layoutFile.writeText("<LinearLayout android:id=\"@+id/root\" />")

        val digest = LayoutX2CDigestCalculator.layoutDigest(
            layoutFile = layoutFile,
            resDir = resDir,
            packageName = "com.example.generated",
            rPackageName = "com.example"
        )

        assertThat(digest).isNotEqualTo(
            "3ffb5db5cd705bbef3d8bd5281a9bdffff098fc49791dc9f7b9d051117a27042"
        )
    }

    @Test
    fun `cache compatibility key is bumped for file resource hash semantics`() {
        assertThat(LayoutX2CDigestCalculator.cacheCompatibilityKey("test"))
            .isEqualTo("schema=v9|processor=test")
    }

    @Test
    fun `manifest reports unchanged only for persisted digest`() {
        val manifestFile = tempDir.newFolder("layoutx2c-manifest")
            .resolve("manifest.properties")

        LayoutX2CDigestStore(manifestFile).apply {
            record("demo", "abc")
            save()
        }

        val store = LayoutX2CDigestStore(manifestFile)

        assertThat(store.isUnchanged("demo", "abc")).isTrue()
        assertThat(store.isUnchanged("demo", "def")).isFalse()
        assertThat(store.isUnchanged("other", "abc")).isFalse()
    }

    @Test
    fun `save removes stale generated cache directories`() {
        val manifestFile = tempDir.newFolder("layoutx2c-cache-cleanup")
            .resolve("layoutx2c-digests.properties")
        val store = LayoutX2CDigestStore(manifestFile)
        val stale = store.cachedFile("demo", "old", "Layout_Demo", "kt")
        val current = store.cachedFile("demo", "new", "Layout_Demo", "kt")
        val removedLayout = store.cachedFile("removed", "old", "Layout_Removed", "kt")
        stale.parentFile.mkdirs()
        current.parentFile.mkdirs()
        removedLayout.parentFile.mkdirs()
        stale.writeText("stale")
        current.writeText("current")
        removedLayout.writeText("removed")

        store.record("demo", "new")
        store.save()

        assertThat(stale.parentFile.exists()).isFalse()
        assertThat(current.parentFile.exists()).isTrue()
        assertThat(removedLayout.parentFile.parentFile.exists()).isFalse()
    }
}

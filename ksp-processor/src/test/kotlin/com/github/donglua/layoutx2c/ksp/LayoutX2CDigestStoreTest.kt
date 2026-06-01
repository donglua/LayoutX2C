package com.github.donglua.layoutx2c.ksp

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

package com.github.donglua.layoutx2c.resources

import com.google.common.truth.Truth.assertThat
import java.util.jar.JarEntry
import java.util.jar.JarOutputStream
import javax.tools.ToolProvider
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder

class ResourceSymbolTableTest {

    @get:Rule
    val tempDir = TemporaryFolder()

    @Test
    fun `scans value and file based resources from res directory`() {
        val resDir = tempDir.newFolder("res")
        val valuesDir = resDir.resolve("values").apply { mkdirs() }
        valuesDir.resolve("colors.xml").writeText(
            """
            <resources>
                <color name="divider">@color/black</color>
                <dimen name="gap">8dp</dimen>
                <string name="title">Title</string>
                <style name="AppTheme" />
                <item name="item_color" type="color">#fff</item>
            </resources>
            """.trimIndent()
        )
        resDir.resolve("drawable").apply { mkdirs() }
            .resolve("panel.xml").writeText("<shape />")
        resDir.resolve("color-night").apply { mkdirs() }
            .resolve("chip.xml").writeText("<selector />")

        val table = ResourceSymbolTable.fromResDir(resDir)

        assertThat(table.references).containsAtLeast(
            ResourceReference("color", "divider"),
            ResourceReference("dimen", "gap"),
            ResourceReference("string", "title"),
            ResourceReference("style", "AppTheme"),
            ResourceReference("color", "item_color"),
            ResourceReference("drawable", "panel"),
            ResourceReference("color", "chip")
        )
    }

    @Test
    fun `scans resources from AGP R text symbol file`() {
        val symbolFile = tempDir.newFile("R.txt").apply {
            writeText(
                """
                int color dependency_divider 0x7f060001
                int dimen main_tab_height 0x7f070001
                int drawable panel_bg 0x7f080001
                int[] styleable Ignored { 0x7f010001 }
                int styleable Ignored_android_text 0
                """.trimIndent()
            )
        }

        val table = ResourceSymbolTable.fromSymbolFile(symbolFile)

        assertThat(table.references).containsAtLeast(
            ResourceReference("color", "dependency_divider"),
            ResourceReference("dimen", "main_tab_height"),
            ResourceReference("drawable", "panel_bg")
        )
        assertThat(table.references).doesNotContain(ResourceReference("styleable", "Ignored"))
    }

    @Test
    fun `scans resources and owner from AGP package aware symbol file`() {
        val symbolFile = tempDir.newFile("package-aware-r.txt").apply {
            writeText(
                """
                com.example.base
                color base_divider
                drawable base_panel
                com.example.feature
                string feature_title
                """.trimIndent()
            )
        }

        val table = ResourceSymbolTable.fromSymbolFile(symbolFile)

        assertThat(table.references).containsAtLeast(
            ResourceReference("color", "base_divider"),
            ResourceReference("drawable", "base_panel"),
            ResourceReference("string", "feature_title")
        )
        assertThat(table.owners[ResourceReference("color", "base_divider")])
            .isEqualTo("com.example.base")
        assertThat(table.owners[ResourceReference("string", "feature_title")])
            .isEqualTo("com.example.feature")
    }

    @Test
    fun `scans resource owners from compiled R class jar`() {
        val jarFile = compileRJar(
            packageName = "com.example.base",
            rBody = """
                public static final class color {
                    public static int base_divider = 0;
                }
                public static final class dimen {
                    public static int base_gap = 0;
                }
            """.trimIndent()
        )

        val table = ResourceSymbolTable.fromRClassJar(jarFile)

        assertThat(table.references).containsAtLeast(
            ResourceReference("color", "base_divider"),
            ResourceReference("dimen", "base_gap")
        )
        assertThat(table.owners[ResourceReference("color", "base_divider")])
            .isEqualTo("com.example.base")
    }

    @Test
    fun `stable key includes owner package names`() {
        val reference = ResourceReference("color", "base_divider")
        val appOwned = ResourceSymbolTable(
            references = setOf(reference),
            owners = mapOf(reference to "com.example.app")
        )
        val baseOwned = ResourceSymbolTable(
            references = setOf(reference),
            owners = mapOf(reference to "com.example.base")
        )

        assertThat(baseOwned.stableKey()).isNotEqualTo(appOwned.stableKey())
    }

    @Test
    fun `parses and formats resource reference edge cases`() {
        assertThat(parseResourceReference("plain")).isNull()
        assertThat(parseResourceReference("@+id/title")).isNull()
        assertThat(parseResourceReference("@android:color/white")).isNull()
        assertThat(parseResourceReference("@color")).isNull()
        assertThat(parseResourceReference("@/missing_type")).isNull()
        assertThat(parseResourceReference("@color/")).isNull()
        assertThat(parseResourceReference("@color/title"))
            .isEqualTo(ResourceReference("color", "title"))

        val localResolver = StaticResourceReferenceResolver(
            owners = mapOf(ResourceReference("color", "title") to "com.example.app"),
            currentPackageName = "com.example.app"
        )
        val externalResolver = StaticResourceReferenceResolver(
            owners = mapOf(ResourceReference("color", "title") to "com.example.base"),
            currentPackageName = "com.example.app"
        )

        assertThat(localResolver.referenceCode("color", "title", "com.example.app"))
            .isEqualTo("R.color.title")
        assertThat(externalResolver.referenceCode("color", "title", "com.example.app"))
            .isEqualTo("com.example.base.R.color.title")
        assertThat(localResolver.referenceCode("color", "missing", "com.example.app")).isNull()
    }

    private fun compileRJar(packageName: String, rBody: String): java.io.File {
        val srcDir = tempDir.newFolder("r-src")
        val classesDir = tempDir.newFolder("r-classes")
        val packageDir = srcDir.resolve(packageName.replace('.', '/')).apply { mkdirs() }
        val sourceFile = packageDir.resolve("R.java")
        sourceFile.writeText(
            """
            package $packageName;
            public final class R {
                $rBody
            }
            """.trimIndent()
        )

        val compiler = ToolProvider.getSystemJavaCompiler()
            ?: error("JDK compiler is required for this test")
        val result = compiler.run(null, null, null, "-d", classesDir.path, sourceFile.path)
        assertThat(result).isEqualTo(0)

        val jarFile = tempDir.newFile("r.jar")
        JarOutputStream(jarFile.outputStream()).use { jar ->
            classesDir.walkTopDown()
                .filter { it.isFile && it.extension == "class" }
                .forEach { file ->
                    val entryName = file.relativeTo(classesDir).invariantSeparatorsPath
                    jar.putNextEntry(JarEntry(entryName))
                    file.inputStream().use { it.copyTo(jar) }
                    jar.closeEntry()
                }
        }
        return jarFile
    }
}

private val java.io.File.invariantSeparatorsPath: String
    get() = path.replace(java.io.File.separatorChar, '/')

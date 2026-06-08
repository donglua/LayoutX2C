package com.github.donglua.layoutx2c.plugin

import com.google.common.truth.Truth.assertThat
import org.gradle.api.GradleException
import org.gradle.testfixtures.ProjectBuilder
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder

class LayoutX2CReportTaskTest {

    @get:Rule
    val tempDir = TemporaryFolder()

    @Test
    fun `generate writes aggregated json and html report from sorted distinct report files`() {
        val reportsDir = tempDir.newFolder("reports")
        val outputDir = tempDir.newFolder("output")
        reportFile(
            reportsDir,
            "02_duplicate_report.json",
            layoutReport(
                layout = "settings",
                bindingFacade = "SettingsBinding",
                nodes = listOf(node("TextView", "[0]", "FULL"))
            )
        )
        reportFile(
            reportsDir,
            "03_duplicate_report.json",
            layoutReport(
                layout = "settings",
                bindingFacade = "DuplicateShouldBeIgnored",
                nodes = listOf(node("TextView", "[9]", "FALLBACK", "IGNORED"))
            )
        )
        reportFile(
            reportsDir,
            "01_home_report.json",
            layoutReport(
                layout = "home",
                bindingFacade = "HomeBinding",
                nodes = listOf(
                    node("LinearLayout", "[]", "PARTIAL"),
                    node("TextView", "[0]", "FALLBACK", "DATA_BINDING_WRAPPER", listOf("android:text"))
                )
            )
        )
        reportsDir.resolve("ignored.json").writeText("{}")
        reportsDir.resolve("nested_report.json").mkdir()

        val task = reportTask(outputDir) {
            reportFiles.from(reportsDir.listFiles())
        }

        task.generate()

        val json = outputDir.resolve("index.json").readText()
        assertThat(json).contains("\"totalLayouts\": 2")
        assertThat(json).contains("\"FULL\": 1")
        assertThat(json).contains("\"PARTIAL\": 1")
        assertThat(json).contains("\"FALLBACK\": 1")
        assertThat(json).contains("\"reason\": \"DATA_BINDING_WRAPPER\"")
        assertThat(json).contains("\"layout\": \"home\"")
        assertThat(json).contains("\"layout\": \"settings\"")
        assertThat(json).doesNotContain("DuplicateShouldBeIgnored")
        assertThat(json.indexOf("\"layout\": \"home\"")).isLessThan(json.indexOf("\"layout\": \"settings\""))

        val html = outputDir.resolve("index.html").readText()
        assertThat(html).contains("LayoutX2C Report")
        assertThat(html).contains("Total layouts: 2")
        assertThat(html).contains("DATA_BINDING_WRAPPER: 1")
        assertThat(html).contains("[0] DATA_BINDING_WRAPPER attrs=android:text")
    }

    @Test
    fun `generate escapes json strings and html text`() {
        val reportsDir = tempDir.newFolder("escaped-reports")
        val outputDir = tempDir.newFolder("escaped-output")
        reportFile(
            reportsDir,
            "escaped_report.json",
            layoutReport(
                layout = "quote\"slash\\html<layout>",
                bindingFacade = "Facade<&>\"",
                nodes = listOf(
                    node(
                        tag = "Text<View>",
                        path = "[0]\"",
                        support = "FALLBACK",
                        reason = "reason<&>\"",
                        attrs = listOf("app:value\"")
                    )
                )
            )
        )

        reportTask(outputDir) {
            reportFiles.from(reportsDir.listFiles())
        }.generate()

        val json = outputDir.resolve("index.json").readText()
        assertThat(json).contains("quote\\\"slash\\\\html<layout>")
        assertThat(json).contains("Facade<&>\\\"")
        assertThat(json).contains("reason<&>\\\"")
        assertThat(json).contains("app:value\\\"")

        val html = outputDir.resolve("index.html").readText()
        assertThat(html).contains("quote&quot;slash\\html&lt;layout&gt;")
        assertThat(html).contains("Facade&lt;&amp;&gt;&quot;")
        assertThat(html).contains("[0]&quot; reason&lt;&amp;&gt;&quot; attrs=app:value&quot;")
    }

    @Test
    fun `generate fails when fallback count exceeds configured maximum`() {
        val reportsDir = tempDir.newFolder("max-policy-reports")
        val outputDir = tempDir.newFolder("max-policy-output")
        reportFile(
            reportsDir,
            "fallback_report.json",
            layoutReport(
                layout = "fallback",
                bindingFacade = "FallbackBinding",
                nodes = listOf(node("TextView", "[0]", "FALLBACK", "UNSUPPORTED_ATTR"))
            )
        )

        val task = reportTask(outputDir) {
            maxFallbackLayouts.set(0)
            reportFiles.from(reportsDir.listFiles())
        }

        val error = assertThrowsGradleException {
            task.generate()
        }

        assertThat(error).hasMessageThat().contains("fallback layouts: 1 > 0")
        assertThat(outputDir.resolve("index.json").isFile).isTrue()
        assertThat(outputDir.resolve("index.html").isFile).isTrue()
    }

    @Test
    fun `generate fails when fallback reasons match configured deny list`() {
        val reportsDir = tempDir.newFolder("reason-policy-reports")
        val outputDir = tempDir.newFolder("reason-policy-output")
        reportFile(
            reportsDir,
            "fallback_report.json",
            layoutReport(
                layout = "fallback",
                bindingFacade = "FallbackBinding",
                nodes = listOf(
                    node("TextView", "[0]", "FALLBACK", "Z_REASON"),
                    node("ImageView", "[1]", "FALLBACK", "A_REASON")
                )
            )
        )

        val task = reportTask(outputDir) {
            failOnFallbackReasons.addAll("A_REASON", "Z_REASON")
            reportFiles.from(reportsDir.listFiles())
        }

        val error = assertThrowsGradleException {
            task.generate()
        }

        assertThat(error).hasMessageThat().contains("fallback reasons: A_REASON, Z_REASON")
    }

    @Test
    fun `generate handles empty report inputs`() {
        val outputDir = tempDir.newFolder("empty-output")

        reportTask(outputDir).generate()

        val json = outputDir.resolve("index.json").readText()
        assertThat(json).contains("\"totalLayouts\": 0")
        assertThat(json).contains("\"topFallbackReasons\": [")

        val html = outputDir.resolve("index.html").readText()
        assertThat(html).contains("Total layouts: 0")
        assertThat(html).contains("<ul></ul>")
    }

    private fun reportTask(
        outputDir: java.io.File,
        configure: LayoutX2CReportTask.() -> Unit = {}
    ): LayoutX2CReportTask {
        val project = ProjectBuilder.builder()
            .withProjectDir(tempDir.newFolder("project"))
            .build()
        return project.tasks.register("layoutX2CReport", LayoutX2CReportTask::class.java).get().apply {
            this.outputDir.set(outputDir)
            warnOnFallback.set(false)
            maxFallbackLayouts.set(Int.MAX_VALUE)
            failOnFallbackReasons.set(emptyList())
            configure()
        }
    }

    private fun reportFile(dir: java.io.File, name: String, content: String): java.io.File {
        return dir.resolve(name).apply {
            writeText(content)
        }
    }

    private fun layoutReport(
        layout: String,
        bindingFacade: String,
        nodes: List<String>
    ): String {
        return """
            {
              "layout": "${layout.escapeInputJson()}",
              "bindingFacade": "${bindingFacade.escapeInputJson()}",
              "nodes": [
                ${nodes.joinToString(",\n")}
              ]
            }
        """.trimIndent()
    }

    private fun node(
        tag: String,
        path: String,
        support: String,
        reason: String? = null,
        attrs: List<String> = emptyList()
    ): String {
        val reasonField = reason?.let { """, "reason": "${it.escapeInputJson()}"""" }.orEmpty()
        val attrsJson = attrs.joinToString(", ") { """"${it.escapeInputJson()}"""" }
        return """{ "tag": "${tag.escapeInputJson()}", "path": "${path.escapeInputJson()}", "support": "$support"$reasonField, "unsupportedAttrs": [$attrsJson] }"""
    }

    private fun String.escapeInputJson(): String {
        return replace("\\", "\\\\").replace("\"", "\\\"")
    }

    private fun assertThrowsGradleException(block: () -> Unit): GradleException {
        return try {
            block()
            throw AssertionError("Expected GradleException")
        } catch (error: GradleException) {
            error
        }
    }
}

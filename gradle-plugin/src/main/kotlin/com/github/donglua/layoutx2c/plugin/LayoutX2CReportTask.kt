package com.github.donglua.layoutx2c.plugin

import groovy.json.JsonSlurper
import org.gradle.api.DefaultTask
import org.gradle.api.GradleException
import org.gradle.api.file.ConfigurableFileCollection
import org.gradle.api.file.DirectoryProperty
import org.gradle.api.provider.ListProperty
import org.gradle.api.provider.Property
import org.gradle.api.tasks.Input
import org.gradle.api.tasks.InputFiles
import org.gradle.api.tasks.OutputDirectory
import org.gradle.api.tasks.PathSensitive
import org.gradle.api.tasks.PathSensitivity
import org.gradle.api.tasks.TaskAction

abstract class LayoutX2CReportTask : DefaultTask() {

    @get:InputFiles
    @get:PathSensitive(PathSensitivity.RELATIVE)
    abstract val reportFiles: ConfigurableFileCollection

    @get:OutputDirectory
    abstract val outputDir: DirectoryProperty

    @get:Input
    abstract val warnOnFallback: Property<Boolean>

    @get:Input
    abstract val maxFallbackLayouts: Property<Int>

    @get:Input
    abstract val failOnFallbackReasons: ListProperty<String>

    @TaskAction
    fun generate() {
        val layouts = reportFiles.files
            .filter { it.isFile && it.name.endsWith("_report.json") }
            .sortedBy { it.invariantSeparatorsPath }
            .map { LayoutReport.from(it.readText()) }
            .distinctBy { it.name }

        val summary = ProjectReportSummary.from(layouts)
        val dir = outputDir.get().asFile
        dir.mkdirs()
        dir.resolve("index.json").writeText(renderJson(summary, layouts))
        dir.resolve("index.html").writeText(renderHtml(summary, layouts))
        enforcePolicy(summary)
    }

    private fun enforcePolicy(summary: ProjectReportSummary) {
        val fallbackLayouts = summary.layoutCounts["FALLBACK"] ?: 0
        if (warnOnFallback.get() && fallbackLayouts > 0) {
            logger.warn("LayoutX2C report found $fallbackLayouts fallback layout(s).")
        }

        val failures = mutableListOf<String>()
        val maxFallback = maxFallbackLayouts.get()
        if (fallbackLayouts > maxFallback) {
            failures += "fallback layouts: $fallbackLayouts > $maxFallback"
        }

        val disallowedReasons = failOnFallbackReasons.get().toSet()
        val matchedReasons = summary.topFallbackReasons.keys
            .filter { it in disallowedReasons }
            .sorted()
        if (matchedReasons.isNotEmpty()) {
            failures += "fallback reasons: ${matchedReasons.joinToString(", ")}"
        }

        if (failures.isNotEmpty()) {
            throw GradleException("LayoutX2C report policy failed: ${failures.joinToString("; ")}")
        }
    }

    private fun renderJson(summary: ProjectReportSummary, layouts: List<LayoutReport>): String {
        return buildString {
            appendLine("{")
            appendLine("  \"summary\": {")
            appendLine("    \"totalLayouts\": ${summary.totalLayouts},")
            appendLine("    \"layouts\": {")
            appendLine("      \"FULL\": ${summary.layoutCounts["FULL"] ?: 0},")
            appendLine("      \"PARTIAL\": ${summary.layoutCounts["PARTIAL"] ?: 0},")
            appendLine("      \"FALLBACK\": ${summary.layoutCounts["FALLBACK"] ?: 0}")
            appendLine("    },")
            appendLine("    \"nodes\": {")
            appendLine("      \"FULL\": ${summary.nodeCounts["FULL"] ?: 0},")
            appendLine("      \"PARTIAL\": ${summary.nodeCounts["PARTIAL"] ?: 0},")
            appendLine("      \"FALLBACK\": ${summary.nodeCounts["FALLBACK"] ?: 0}")
            appendLine("    },")
            appendLine("    \"topFallbackReasons\": [")
            summary.topFallbackReasons.entries.forEachIndexed { index, entry ->
                append("      { \"reason\": \"${entry.key.escapeJson()}\", \"count\": ${entry.value} }")
                if (index < summary.topFallbackReasons.size - 1) append(",")
                appendLine()
            }
            appendLine("    ]")
            appendLine("  },")
            appendLine("  \"layouts\": [")
            layouts.forEachIndexed { index, layout ->
                appendLine("    {")
                appendLine("      \"layout\": \"${layout.name.escapeJson()}\",")
                appendLine("      \"support\": \"${layout.support}\",")
                appendLine("      \"bindingFacade\": \"${layout.bindingFacade.escapeJson()}\",")
                appendLine("      \"nodes\": {")
                appendLine("        \"FULL\": ${layout.nodeCounts["FULL"] ?: 0},")
                appendLine("        \"PARTIAL\": ${layout.nodeCounts["PARTIAL"] ?: 0},")
                appendLine("        \"FALLBACK\": ${layout.nodeCounts["FALLBACK"] ?: 0}")
                appendLine("      },")
                appendLine("      \"fallbackReasons\": [${layout.fallbackReasons.joinToString(", ") { "\"${it.escapeJson()}\"" }}],")
                appendLine("      \"fallbackNodes\": [")
                layout.fallbackNodes.forEachIndexed { nodeIndex, node ->
                    appendLine("        {")
                    appendLine("          \"tag\": \"${node.tagName.escapeJson()}\",")
                    appendLine("          \"path\": \"${node.path.escapeJson()}\",")
                    appendLine("          \"reason\": \"${node.reason.orEmpty().escapeJson()}\",")
                    appendLine("          \"unsupportedAttrs\": [${node.unsupportedAttrs.joinToString(", ") { "\"${it.escapeJson()}\"" }}]")
                    append("        }")
                    if (nodeIndex < layout.fallbackNodes.size - 1) append(",")
                    appendLine()
                }
                appendLine("      ]")
                append("    }")
                if (index < layouts.size - 1) append(",")
                appendLine()
            }
            appendLine("  ]")
            appendLine("}")
        }
    }

    private fun renderHtml(summary: ProjectReportSummary, layouts: List<LayoutReport>): String {
        val rows = layouts.joinToString("\n") { layout ->
            val fallbackNodes = layout.fallbackNodes.joinToString("<br>") { node ->
                val attrs = if (node.unsupportedAttrs.isEmpty()) "" else " attrs=${node.unsupportedAttrs.joinToString("|")}"
                "${node.path} ${node.reason.orEmpty()}$attrs".trim().escapeHtml()
            }
            """
            <tr>
              <td>${layout.name.escapeHtml()}</td>
              <td>${layout.support}</td>
              <td>${layout.bindingFacade.escapeHtml()}</td>
              <td>${layout.nodeCounts["FULL"] ?: 0}</td>
              <td>${layout.nodeCounts["PARTIAL"] ?: 0}</td>
              <td>${layout.nodeCounts["FALLBACK"] ?: 0}</td>
              <td>$fallbackNodes</td>
            </tr>
            """.trimIndent()
        }
        val reasons = summary.topFallbackReasons.entries.joinToString("\n") {
            "<li>${it.key.escapeHtml()}: ${it.value}</li>"
        }
        return """
            <!doctype html>
            <html>
            <head>
              <meta charset="utf-8">
              <title>LayoutX2C Report</title>
              <style>
                body { font-family: -apple-system, BlinkMacSystemFont, "Segoe UI", sans-serif; margin: 24px; }
                table { border-collapse: collapse; width: 100%; }
                th, td { border: 1px solid #ddd; padding: 8px; text-align: left; }
                th { background: #f6f8fa; }
              </style>
            </head>
            <body>
              <h1>LayoutX2C Report</h1>
              <p>Total layouts: ${summary.totalLayouts}</p>
              <h2>Top fallback reasons</h2>
              <ul>$reasons</ul>
              <h2>Layouts</h2>
              <table>
                <thead>
                  <tr>
                    <th>Layout</th>
                    <th>Support</th>
                    <th>Binding facade</th>
                    <th>FULL nodes</th>
                    <th>PARTIAL nodes</th>
                    <th>FALLBACK nodes</th>
                    <th>Fallback nodes</th>
                  </tr>
                </thead>
                <tbody>
                  $rows
                </tbody>
              </table>
            </body>
            </html>
        """.trimIndent()
    }

    private data class LayoutReport(
        val name: String,
        val bindingFacade: String,
        val nodes: List<LayoutNodeReport>
    ) {
        val nodeSupports: List<String> = nodes.map { it.support }

        val fallbackNodes: List<LayoutNodeReport> = nodes.filter { it.support == "FALLBACK" }

        val fallbackReasons: List<String> = fallbackNodes.mapNotNull { it.reason }

        val support: String = when {
            nodeSupports.any { it == "FALLBACK" } -> "FALLBACK"
            nodeSupports.any { it == "PARTIAL" } -> "PARTIAL"
            else -> "FULL"
        }

        val nodeCounts: Map<String, Int> = nodeSupports.groupingBy { it }.eachCount()

        companion object {
            fun from(json: String): LayoutReport {
                val root = JsonSlurper().parseText(json) as Map<*, *>
                val nodes = (root["nodes"] as? List<*>).orEmpty().mapNotNull { rawNode ->
                    val node = rawNode as? Map<*, *> ?: return@mapNotNull null
                    LayoutNodeReport(
                        tagName = node["tag"]?.toString().orEmpty(),
                        path = node["path"]?.toString().orEmpty(),
                        support = node["support"]?.toString().orEmpty(),
                        reason = node["reason"] as? String,
                        unsupportedAttrs = (node["unsupportedAttrs"] as? List<*>)
                            .orEmpty()
                            .map { it.toString() }
                    )
                }
                return LayoutReport(
                    name = root["layout"]?.toString().orEmpty(),
                    bindingFacade = root["bindingFacade"]?.toString().orEmpty(),
                    nodes = nodes
                )
            }
        }
    }

    private data class LayoutNodeReport(
        val tagName: String,
        val path: String,
        val support: String,
        val reason: String?,
        val unsupportedAttrs: List<String>
    )

    private data class ProjectReportSummary(
        val totalLayouts: Int,
        val layoutCounts: Map<String, Int>,
        val nodeCounts: Map<String, Int>,
        val topFallbackReasons: Map<String, Int>
    ) {
        companion object {
            fun from(layouts: List<LayoutReport>): ProjectReportSummary {
                return ProjectReportSummary(
                    totalLayouts = layouts.size,
                    layoutCounts = layouts.groupingBy { it.support }.eachCount(),
                    nodeCounts = layouts.flatMap { it.nodeSupports }.groupingBy { it }.eachCount(),
                    topFallbackReasons = layouts
                        .flatMap { it.fallbackReasons }
                        .groupingBy { it }
                        .eachCount()
                        .toList()
                        .sortedByDescending { it.second }
                        .toMap()
                )
            }
        }
    }

    private fun String.escapeJson(): String {
        return replace("\\", "\\\\").replace("\"", "\\\"")
    }

    private fun String.escapeHtml(): String {
        return replace("&", "&amp;")
            .replace("<", "&lt;")
            .replace(">", "&gt;")
            .replace("\"", "&quot;")
    }
}

package com.github.donglua.layoutx2c.parser

import java.io.File

sealed class IncludeResult {
    data class Success(val tree: LayoutTree) : IncludeResult()
    object NotFound : IncludeResult()
    object CircularReference : IncludeResult()
    object DepthExceeded : IncludeResult()
    data class ParseError(val exception: Exception) : IncludeResult()
}

/**
 * Recursively resolves `<include>` layout references.
 *
 * @param layoutDir The `res/layout` directory containing layout XML files.
 * @param maxDepth Maximum recursion depth to prevent runaway resolution (default 10).
 */
open class IncludeResolver(
    private val layoutDir: File,
    private val maxDepth: Int = 10
) {

    /**
     * Resolves an include reference to a [LayoutTree].
     *
     * @param layoutRef The layout resource name (e.g. "toolbar_common").
     * @param visitedLayouts Layouts already visited in the current resolution chain (for cycle detection).
     * @param depth Current recursion depth.
     * @return The [IncludeResult] indicating success or specific failure reason.
     */
    open fun resolveInclude(
        layoutRef: String,
        visitedLayouts: Set<String> = emptySet(),
        depth: Int = 0
    ): IncludeResult {
        // Depth guard
        if (depth >= maxDepth) return IncludeResult.DepthExceeded

        // Circular reference guard
        if (layoutRef in visitedLayouts) return IncludeResult.CircularReference

        // Locate the XML file
        val xmlFile = File(layoutDir, "$layoutRef.xml")
        if (!xmlFile.exists() || !xmlFile.isFile) return IncludeResult.NotFound

        // Create a child resolver that carries forward visited state
        val currentDepth = depth
        val childVisited = visitedLayouts + layoutRef
        val childResolver = object : IncludeResolver(layoutDir, maxDepth) {
            override fun resolveInclude(
                layoutRef: String,
                visitedLayouts: Set<String>,
                depth: Int
            ): IncludeResult {
                // Delegate to parent's resolveInclude with accumulated state
                return this@IncludeResolver.resolveInclude(layoutRef, childVisited, currentDepth + 1)
            }
        }

        val childParser = XmlLayoutParser(includeResolver = childResolver)

        return try {
            IncludeResult.Success(childParser.parse(xmlFile))
        } catch (e: Exception) {
            IncludeResult.ParseError(e)
        }
    }
}

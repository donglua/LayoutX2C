package com.github.donglua.layoutx2c.ksp

import java.io.File

internal fun File.layoutResourceDirs(): List<File> {
    return listFiles()
        ?.filter { it.isDirectory && (it.name == "layout" || it.name.startsWith("layout-")) }
        ?.sortedWith(compareBy<File> { if (it.name == "layout") 0 else 1 }.thenBy { it.name })
        ?: emptyList()
}

internal fun File.resolveLayoutFiles(layoutName: String): List<File> {
    return layoutResourceDirs()
        .map { it.resolve("$layoutName.xml") }
        .filter { it.isFile }
}

internal fun File.primaryLayoutFile(layoutName: String): File? {
    return resolveLayoutFiles(layoutName).firstOrNull()
}

internal fun File.layoutNamesWithPrefix(prefix: String): Set<String> {
    return layoutResourceDirs()
        .flatMap { layoutDir ->
            layoutDir.listFiles()
                ?.filter { it.isFile && it.extension == "xml" && it.nameWithoutExtension.startsWith(prefix) }
                ?.map { it.nameWithoutExtension }
                ?: emptyList()
        }
        .toSortedSet()
}

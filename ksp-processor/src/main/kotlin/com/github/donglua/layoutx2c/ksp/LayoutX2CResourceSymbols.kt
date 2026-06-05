package com.github.donglua.layoutx2c.ksp

import com.github.donglua.layoutx2c.resources.ResourceSymbolTable
import java.io.File

internal object LayoutX2CResourceSymbols {

    fun resolve(
        resDir: File,
        explicitSymbolFiles: List<File>
    ): ResourceSymbolTable {
        val sourceSymbols = ResourceSymbolTable.fromResDir(resDir)
        val projectDir = AgpResourceSymbolLocator.inferProjectDir(resDir)
        val inferredSymbolFiles = projectDir
            ?.let(AgpResourceSymbolLocator::findSymbolFiles)
            ?: emptyList()
        val inferredRClassJars = projectDir
            ?.let(AgpResourceSymbolLocator::findRClassJars)
            ?: emptyList()

        val textSymbols = ResourceSymbolTable.fromSymbolFiles(
            explicitSymbolFiles + inferredSymbolFiles
        )
        val rClassSymbols = ResourceSymbolTable.fromRClassJars(inferredRClassJars)

        return sourceSymbols + textSymbols + rClassSymbols
    }
}

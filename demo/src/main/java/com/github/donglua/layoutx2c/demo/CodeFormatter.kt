package com.github.donglua.layoutx2c.demo

object CodeFormatter {

    data class FormattedCode(
        val lineNumbers: String,
        val code: String,
        val summary: String
    )

    fun withLineNumbers(source: String): FormattedCode {
        val lineCount = source.split('\n').size
        val lineNumbers = (1..lineCount).joinToString(separator = "\n")
        val summary = "$lineCount ${if (lineCount == 1) "line" else "lines"}"
        return FormattedCode(
            lineNumbers = lineNumbers,
            code = source,
            summary = summary
        )
    }
}

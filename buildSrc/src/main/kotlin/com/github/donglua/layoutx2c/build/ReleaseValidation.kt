package com.github.donglua.layoutx2c.build

import java.io.File

private val releaseVersionPattern = Regex(
    "(?:0|[1-9][0-9]*)\\.(?:0|[1-9][0-9]*)\\.(?:0|[1-9][0-9]*)" +
        "(?:-[0-9A-Za-z-]+(?:\\.[0-9A-Za-z-]+)*)?"
)
private val markdownLinkPattern = Regex("""\[[^]]*]\((<[^>]+>|[^)\r\n]+)\)""")
private val externalLinkPattern = Regex("^(?:https?://|mailto:)", RegexOption.IGNORE_CASE)

fun validateReleaseVersion(version: String): String? {
    val containsSnapshot = version.substringAfter('-', "")
        .split('.')
        .any { it.equals("SNAPSHOT", ignoreCase = true) }
    return if (releaseVersionPattern.matches(version) && !containsSnapshot) {
        null
    } else {
        "Release version must be stable SemVer or a non-SNAPSHOT prerelease: $version"
    }
}

fun findBrokenLocalLinks(markdown: String, document: File, repoRoot: File): List<String> {
    val canonicalRoot = repoRoot.canonicalFile.toPath()
    val documentDirectory = document.canonicalFile.parentFile ?: repoRoot.canonicalFile

    return markdownLinkPattern.findAll(markdown)
        .map { match -> match.groupValues[1].trim() }
        .map { destination ->
            if (destination.startsWith('<') && destination.endsWith('>')) {
                destination.substring(1, destination.length - 1)
            } else {
                destination
            }
        }
        .filterNot { destination ->
            destination.startsWith('#') || externalLinkPattern.containsMatchIn(destination)
        }
        .map { destination -> destination.substringBefore('#') }
        .filter { it.isNotBlank() }
        .filter { destination ->
            val normalized = destination.replace('\\', '/')
            val target = documentDirectory.resolve(normalized).canonicalFile
            !target.toPath().startsWith(canonicalRoot) || !target.exists()
        }
        .distinct()
        .toList()
}

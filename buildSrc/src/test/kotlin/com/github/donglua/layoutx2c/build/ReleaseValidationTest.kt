package com.github.donglua.layoutx2c.build

import com.google.common.truth.Truth.assertThat
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder

class ReleaseValidationTest {

    @get:Rule
    val tempDir = TemporaryFolder()

    @Test
    fun `release version accepts stable and prerelease semver`() {
        assertThat(validateReleaseVersion("1.4.1")).isNull()
        assertThat(validateReleaseVersion("1.4.1-rc.1")).isNull()
        assertThat(validateReleaseVersion("2.0.0-beta.2")).isNull()
    }

    @Test
    fun `release version rejects snapshot and malformed values`() {
        assertThat(validateReleaseVersion("1.4.1-SNAPSHOT")).contains("stable SemVer")
        assertThat(validateReleaseVersion("1.4")).contains("stable SemVer")
        assertThat(validateReleaseVersion("v1.4.1")).contains("stable SemVer")
    }

    @Test
    fun `markdown validator ignores external links and anchors`() {
        val repoRoot = tempDir.newFolder("repo")
        val document = repoRoot.resolve("README.md").apply { writeText("") }
        repoRoot.resolve("docs").mkdirs()
        repoRoot.resolve("docs/RELEASE.md").writeText("release")
        val markdown = """
            [local](docs/RELEASE.md)
            [web](https://example.com)
            [mail](mailto:maintainer@example.com)
            [anchor](#section)
        """.trimIndent()

        assertThat(findBrokenLocalLinks(markdown, document, repoRoot)).isEmpty()
    }

    @Test
    fun `markdown validator resolves document relative links and strips anchors`() {
        val repoRoot = tempDir.newFolder("repo")
        val docs = repoRoot.resolve("docs").apply { mkdirs() }
        val document = docs.resolve("RELEASE.md").apply { writeText("") }
        docs.resolve("ROADMAP.md").writeText("roadmap")

        assertThat(findBrokenLocalLinks("[roadmap](ROADMAP.md#next)", document, repoRoot)).isEmpty()
    }

    @Test
    fun `markdown validator accepts angle bracket paths containing spaces`() {
        val repoRoot = tempDir.newFolder("repo")
        val document = repoRoot.resolve("README.md").apply { writeText("") }
        repoRoot.resolve("docs").mkdirs()
        repoRoot.resolve("docs/release notes.md").writeText("notes")

        assertThat(findBrokenLocalLinks("[notes](<docs/release notes.md>)", document, repoRoot)).isEmpty()
    }

    @Test
    fun `markdown validator normalizes windows separators`() {
        val repoRoot = tempDir.newFolder("repo")
        val document = repoRoot.resolve("README.md").apply { writeText("") }
        repoRoot.resolve("docs").mkdirs()
        repoRoot.resolve("docs/RELEASE.md").writeText("release")

        assertThat(findBrokenLocalLinks("[release](docs\\RELEASE.md)", document, repoRoot)).isEmpty()
    }

    @Test
    fun `markdown validator reports missing links relative to release document`() {
        val repoRoot = tempDir.newFolder("repo")
        val docs = repoRoot.resolve("docs").apply { mkdirs() }
        val document = docs.resolve("RELEASE.md").apply { writeText("") }

        assertThat(findBrokenLocalLinks("[missing](missing.md)", document, repoRoot))
            .containsExactly("missing.md")
    }

    @Test
    fun `markdown validator rejects paths outside repository`() {
        val repoRoot = tempDir.newFolder("repo")
        val document = repoRoot.resolve("README.md").apply { writeText("") }
        tempDir.root.resolve("outside.md").writeText("outside")

        assertThat(findBrokenLocalLinks("[outside](../outside.md)", document, repoRoot))
            .containsExactly("../outside.md")
    }
}

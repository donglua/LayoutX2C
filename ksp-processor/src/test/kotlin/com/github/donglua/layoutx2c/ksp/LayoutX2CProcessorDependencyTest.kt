package com.github.donglua.layoutx2c.ksp

import com.google.common.truth.Truth.assertThat
import com.google.devtools.ksp.processing.Dependencies
import com.google.devtools.ksp.symbol.FileLocation
import com.google.devtools.ksp.symbol.KSAnnotation
import com.google.devtools.ksp.symbol.KSDeclaration
import com.google.devtools.ksp.symbol.KSFile
import com.google.devtools.ksp.symbol.KSName
import com.google.devtools.ksp.symbol.KSNode
import com.google.devtools.ksp.symbol.KSVisitor
import com.google.devtools.ksp.symbol.Origin
import org.junit.Test

class LayoutX2CProcessorDependencyTest {

    @Test
    fun `layout outputs depend on the source files that request generation`() {
        val source = TestKSFile("LayoutX2CConfig.kt", "/tmp/LayoutX2CConfig.kt")
        val dependency = LayoutX2CDependencyFactory.layout(listOf(source))

        assertThat(dependency.aggregating).isFalse()
        assertThat(dependency.originatingFiles).containsExactly(source)
    }

    @Test
    fun `registry output is aggregating across generated layouts`() {
        val source = TestKSFile("LayoutX2CConfig.kt", "/tmp/LayoutX2CConfig.kt")
        val dependency = LayoutX2CDependencyFactory.registry(listOf(source))

        assertThat(dependency.aggregating).isTrue()
        assertThat(dependency.originatingFiles).containsExactly(source)
    }

    @Test
    fun `outputs without source files use all files fallback`() {
        val dependency = LayoutX2CDependencyFactory.layout(emptyList())

        assertThat(dependency).isSameInstanceAs(Dependencies.ALL_FILES)
    }
}

private class TestKSFile(
    override val fileName: String,
    override val filePath: String
) : KSFile {
    override val packageName: KSName = TestKSName("")
    override val annotations: Sequence<KSAnnotation> = emptySequence()
    override val declarations: Sequence<KSDeclaration> = emptySequence()
    override val origin: Origin = Origin.KOTLIN
    override val location = FileLocation(filePath, 1)
    override val parent: KSNode? = null

    override fun <D, R> accept(visitor: KSVisitor<D, R>, data: D): R = visitor.visitFile(this, data)
}

private class TestKSName(private val value: String) : KSName {
    override fun asString(): String = value
    override fun getQualifier(): String = value.substringBeforeLast('.', "")
    override fun getShortName(): String = value.substringAfterLast('.')
}

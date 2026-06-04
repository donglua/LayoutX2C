# Custom View Whitelist Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Let LayoutX2C generate safe, explicitly whitelisted custom View classes and their typed attributes when those declarations live beside the existing layout config annotations.

**Architecture:** Keep the feature opt-in and local to the existing config object. Add public runtime annotations to describe custom View descriptors, teach the KSP processor to extract those descriptors from the same annotated source file, and extend the registry layer so analyzer/codegen can treat declared Views as known only when they were explicitly listed. Unknown custom Views and undeclared attributes continue to fallback.

**Tech Stack:** Kotlin, KSP, KotlinPoet, JUnit 4, Truth, AndroidX Core, existing `ResourceAwareViewRegistry`.

---

## File Structure

- Create `runtime/src/main/java/com/github/donglua/layoutx2c/runtime/annotation/FastCustomViews.kt`: public container annotation for the whitelist entries.
- Create `runtime/src/main/java/com/github/donglua/layoutx2c/runtime/annotation/FastCustomView.kt`: single custom View descriptor annotation.
- Create `runtime/src/main/java/com/github/donglua/layoutx2c/runtime/annotation/FastCustomViewAttr.kt`: typed attribute descriptor annotation.
- Create `runtime/src/main/java/com/github/donglua/layoutx2c/runtime/annotation/FastCustomViewAttrKind.kt`: enum describing emitted value kinds.
- Modify `runtime/src/test/java/com/github/donglua/layoutx2c/runtime/PackagePathConsistencyTest.kt`: include the new runtime API files.
- Create `ksp-processor/src/main/kotlin/com/github/donglua/layoutx2c/ksp/CustomViewConfigParser.kt`: parse `@FastCustomViews` from source files into an internal model.
- Modify `ksp-processor/src/main/kotlin/com/github/donglua/layoutx2c/ksp/LayoutX2CProcessor.kt`: collect custom View descriptors and pass them to the registry.
- Create `ksp-processor/src/test/kotlin/com/github/donglua/layoutx2c/ksp/CustomViewConfigParserTest.kt`: prove the KSP-side parser extracts descriptors correctly.
- Modify `compiler-core/src/main/kotlin/com/github/donglua/layoutx2c/registry/ViewRegistry.kt`: accept custom View descriptors and emit typed attribute handlers.
- Create `compiler-core/src/main/kotlin/com/github/donglua/layoutx2c/registry/CustomViewDescriptors.kt`: shared internal model for custom View descriptors.
- Modify `compiler-core/src/test/kotlin/com/github/donglua/layoutx2c/registry/DefaultViewRegistryTest.kt`: verify custom Views are recognized and unsupported values still fallback.
- Modify `ksp-processor/src/test/kotlin/com/github/donglua/layoutx2c/ksp/LayoutX2CGenerationFixtureTest.kt`: verify end-to-end generated output for a whitelisted custom View.
- Modify `README.md` and `docs/ROADMAP.md`: document that whitelisted custom Views are a supported opt-in path.

## Task 1: Public Custom View Annotations

**Files:**
- Create: `runtime/src/main/java/com/github/donglua/layoutx2c/runtime/annotation/FastCustomViews.kt`
- Create: `runtime/src/main/java/com/github/donglua/layoutx2c/runtime/annotation/FastCustomView.kt`
- Create: `runtime/src/main/java/com/github/donglua/layoutx2c/runtime/annotation/FastCustomViewAttr.kt`
- Create: `runtime/src/main/java/com/github/donglua/layoutx2c/runtime/annotation/FastCustomViewAttrKind.kt`
- Modify: `runtime/src/test/java/com/github/donglua/layoutx2c/runtime/PackagePathConsistencyTest.kt`
- Test: `./gradlew :runtime:testDebugUnitTest --tests com.github.donglua.layoutx2c.runtime.PackagePathConsistencyTest`

- [ ] **Step 1: Write the failing API coverage test**

Add assertions that the runtime package now includes the new public API files:

```java
assertThat(packagePaths).contains("com/github/donglua/layoutx2c/runtime/annotation/FastCustomViews.kt");
assertThat(packagePaths).contains("com/github/donglua/layoutx2c/runtime/annotation/FastCustomView.kt");
assertThat(packagePaths).contains("com/github/donglua/layoutx2c/runtime/annotation/FastCustomViewAttr.kt");
assertThat(packagePaths).contains("com/github/donglua/layoutx2c/runtime/annotation/FastCustomViewAttrKind.kt");
```

Also add a compile-time usage snippet in the KSP parser test fixture:

```kotlin
import com.github.donglua.layoutx2c.runtime.annotation.FastCustomViews
import com.github.donglua.layoutx2c.runtime.annotation.FastCustomView
import com.github.donglua.layoutx2c.runtime.annotation.FastCustomViewAttr
import com.github.donglua.layoutx2c.runtime.annotation.FastCustomViewAttrKind

@FastCustomViews(
    FastCustomView(
        viewClass = PriceView::class,
        attrs = [
            FastCustomViewAttr(name = "app:priceColor", kind = FastCustomViewAttrKind.COLOR)
        ]
    )
)
object LayoutX2CConfig
```

- [ ] **Step 2: Run the test and verify it fails**

Run:

```bash
./gradlew :runtime:testDebugUnitTest --tests com.github.donglua.layoutx2c.runtime.PackagePathConsistencyTest
```

Expected: compilation fails because the new annotation files do not exist yet.

- [ ] **Step 3: Add the annotation files**

Implement the four files as binary-retained public annotations:

```kotlin
@MustBeDocumented
@Retention(AnnotationRetention.BINARY)
@Target(AnnotationTarget.CLASS)
annotation class FastCustomViews(vararg val value: FastCustomView)
```

```kotlin
@MustBeDocumented
@Retention(AnnotationRetention.BINARY)
@Target(AnnotationTarget.ANNOTATION_CLASS, AnnotationTarget.CLASS)
annotation class FastCustomView(
    val viewClass: KClass<*>,
    val attrs: Array<FastCustomViewAttr> = []
)
```

```kotlin
@MustBeDocumented
@Retention(AnnotationRetention.BINARY)
@Target(AnnotationTarget.ANNOTATION_CLASS, AnnotationTarget.CLASS)
annotation class FastCustomViewAttr(
    val name: String,
    val kind: FastCustomViewAttrKind
)
```

```kotlin
enum class FastCustomViewAttrKind {
    STRING,
    BOOLEAN,
    INT,
    FLOAT,
    DIMENSION,
    COLOR,
    COLOR_STATE_LIST,
    DRAWABLE_REF,
    RESOURCE_REF
}
```

- [ ] **Step 4: Run the runtime test and verify it passes**

Run:

```bash
./gradlew :runtime:testDebugUnitTest --tests com.github.donglua.layoutx2c.runtime.PackagePathConsistencyTest
```

Expected: PASS.

- [ ] **Step 5: Commit the runtime API layer**

```bash
git add runtime/src/main/java/com/github/donglua/layoutx2c/runtime/annotation/FastCustomViews.kt \
  runtime/src/main/java/com/github/donglua/layoutx2c/runtime/annotation/FastCustomView.kt \
  runtime/src/main/java/com/github/donglua/layoutx2c/runtime/annotation/FastCustomViewAttr.kt \
  runtime/src/main/java/com/github/donglua/layoutx2c/runtime/annotation/FastCustomViewAttrKind.kt \
  runtime/src/test/java/com/github/donglua/layoutx2c/runtime/PackagePathConsistencyTest.kt
git commit -m "feat(runtime): add custom view whitelist annotations"
```

## Task 2: Parse Custom View Descriptors in KSP

**Files:**
- Create: `ksp-processor/src/main/kotlin/com/github/donglua/layoutx2c/ksp/CustomViewConfigParser.kt`
- Modify: `ksp-processor/src/main/kotlin/com/github/donglua/layoutx2c/ksp/LayoutX2CProcessor.kt`
- Create: `ksp-processor/src/test/kotlin/com/github/donglua/layoutx2c/ksp/CustomViewConfigParserTest.kt`
- Test: `./gradlew :ksp-processor:test --tests com.github.donglua.layoutx2c.ksp.CustomViewConfigParserTest`

- [ ] **Step 1: Write the failing parser test**

Add a parser test that feeds a config source containing the new annotations and expects the parser to extract one descriptor:

```kotlin
@Test
fun `extracts custom view descriptors from config object`() {
    val source = """
        package com.example

        import com.github.donglua.layoutx2c.runtime.annotation.FastCustomViews
        import com.github.donglua.layoutx2c.runtime.annotation.FastCustomView
        import com.github.donglua.layoutx2c.runtime.annotation.FastCustomViewAttr
        import com.github.donglua.layoutx2c.runtime.annotation.FastCustomViewAttrKind

        @FastCustomViews(
            FastCustomView(
                viewClass = com.example.widget.PriceView::class,
                attrs = [
                    FastCustomViewAttr(name = "app:priceColor", kind = FastCustomViewAttrKind.COLOR),
                    FastCustomViewAttr(name = "app:label", kind = FastCustomViewAttrKind.STRING)
                ]
            )
        )
        object LayoutX2CConfig
    """.trimIndent()

    val descriptors = CustomViewConfigParser.extractCustomViews(source)

    assertThat(descriptors).hasSize(1)
    assertThat(descriptors.single().viewClassName).isEqualTo("com.example.widget.PriceView")
    assertThat(descriptors.single().attributes.map { it.name }).containsExactly(
        "app:priceColor",
        "app:label"
    )
}
```

- [ ] **Step 2: Run the test and verify it fails**

Run:

```bash
./gradlew :ksp-processor:test --tests com.github.donglua.layoutx2c.ksp.CustomViewConfigParserTest
```

Expected: FAIL because `CustomViewConfigParser` does not exist yet.

- [ ] **Step 3: Implement the parser and wire it into the processor**

Create an internal model in `ksp-processor` or `compiler-core`:

```kotlin
data class CustomViewDescriptor(
    val viewClassName: String,
    val attributes: List<CustomViewAttribute>
)

data class CustomViewAttribute(
    val name: String,
    val kind: FastCustomViewAttrKind
)
```

Then implement `CustomViewConfigParser.extractCustomViews(source: String)` to:

- find `@FastCustomViews`
- extract each nested `FastCustomView`
- read `viewClass = ...::class`
- read each `FastCustomViewAttr(name = ..., kind = ...)`
- ignore malformed entries with a warning instead of throwing

Modify `LayoutX2CProcessor.process()` so it:

- still extracts layout names as before
- also collects custom View descriptors from the same source files
- passes the descriptors into the registry constructor

- [ ] **Step 4: Run the parser test and the processor test suite**

Run:

```bash
./gradlew :ksp-processor:test --tests com.github.donglua.layoutx2c.ksp.CustomViewConfigParserTest
./gradlew :ksp-processor:test
```

Expected: PASS.

- [ ] **Step 5: Commit the processor parsing layer**

```bash
git add ksp-processor/src/main/kotlin/com/github/donglua/layoutx2c/ksp/CustomViewConfigParser.kt \
  ksp-processor/src/main/kotlin/com/github/donglua/layoutx2c/ksp/LayoutX2CProcessor.kt \
  ksp-processor/src/test/kotlin/com/github/donglua/layoutx2c/ksp/CustomViewConfigParserTest.kt
git commit -m "feat(ksp): parse custom view whitelist annotations"
```

## Task 3: Extend Registry and Code Generation

**Files:**
- Create: `compiler-core/src/main/kotlin/com/github/donglua/layoutx2c/registry/CustomViewDescriptors.kt`
- Modify: `compiler-core/src/main/kotlin/com/github/donglua/layoutx2c/registry/ViewRegistry.kt`
- Modify: `compiler-core/src/test/kotlin/com/github/donglua/layoutx2c/registry/DefaultViewRegistryTest.kt`
- Modify: `ksp-processor/src/test/kotlin/com/github/donglua/layoutx2c/ksp/LayoutX2CGenerationFixtureTest.kt`
- Test:
  - `./gradlew :compiler-core:test --tests com.github.donglua.layoutx2c.registry.DefaultViewRegistryTest`
  - `./gradlew :ksp-processor:test --tests com.github.donglua.layoutx2c.ksp.LayoutX2CGenerationFixtureTest`

- [ ] **Step 1: Add failing registry tests**

Add a registry test that constructs a custom View descriptor and verifies recognition:

```kotlin
@Test
fun `custom view is recognized only when explicitly whitelisted`() {
    val registry = ResourceAwareViewRegistry(
        rPackageName = "com.example",
        customViews = listOf(
            CustomViewDescriptor(
                viewClassName = "com.example.widget.PriceView",
                attributes = listOf(
                    CustomViewAttribute("app:priceColor", FastCustomViewAttrKind.COLOR)
                )
            )
        )
    )

    assertThat(registry.viewHandlerFor("com.example.widget.PriceView")).isNotNull()
    assertThat(registry.viewHandlerFor("com.example.widget.OtherView")).isNull()
}
```

Add a codegen fixture test that expects a generated setter call for a whitelisted color attr:

```kotlin
assertThat(generated).contains("setPriceColor(ContextCompat.getColor(context, R.color.red))")
```

- [ ] **Step 2: Run the tests and verify they fail**

Run:

```bash
./gradlew :compiler-core:test --tests com.github.donglua.layoutx2c.registry.DefaultViewRegistryTest
./gradlew :ksp-processor:test --tests com.github.donglua.layoutx2c.ksp.LayoutX2CGenerationFixtureTest
```

Expected: FAIL because `ResourceAwareViewRegistry` does not yet accept custom views and codegen does not emit the custom setter path.

- [ ] **Step 3: Add the registry model and emission support**

Implement the shared model in `CustomViewDescriptors.kt` and extend `ResourceAwareViewRegistry` so it:

- accepts `customViews: List<CustomViewDescriptor> = emptyList()` in the constructor
- creates a handler for each whitelisted custom View class name
- treats `viewClassName` and the simple class name as valid tags
- emits typed attributes based on `FastCustomViewAttrKind`

For the first release, use these emission rules:

```kotlin
COLOR -> ContextCompat.getColor(context, R.color.red)
COLOR_STATE_LIST -> ContextCompat.getColorStateList(context, R.color.red)
DRAWABLE_REF -> ContextCompat.getDrawable(context, R.drawable.icon)
DIMENSION -> dimensionToCode(...)
STRING -> "literal"
BOOLEAN -> true / false
INT -> 123
FLOAT -> 12.5f
RESOURCE_REF -> R.type.name
```

If a value does not match the declared kind, mark the attr unsupported and keep the current fallback semantics.

- [ ] **Step 4: Run the registry and codegen tests**

Run:

```bash
./gradlew :compiler-core:test --tests com.github.donglua.layoutx2c.registry.DefaultViewRegistryTest
./gradlew :ksp-processor:test --tests com.github.donglua.layoutx2c.ksp.LayoutX2CGenerationFixtureTest
```

Expected: PASS.

- [ ] **Step 5: Commit registry and codegen support**

```bash
git add compiler-core/src/main/kotlin/com/github/donglua/layoutx2c/registry/CustomViewDescriptors.kt \
  compiler-core/src/main/kotlin/com/github/donglua/layoutx2c/registry/ViewRegistry.kt \
  compiler-core/src/test/kotlin/com/github/donglua/layoutx2c/registry/DefaultViewRegistryTest.kt \
  ksp-processor/src/test/kotlin/com/github/donglua/layoutx2c/ksp/LayoutX2CGenerationFixtureTest.kt
git commit -m "feat(core): emit whitelisted custom views"
```

## Task 4: Documentation and Roadmap Sync

**Files:**
- Modify: `README.md`
- Modify: `docs/ROADMAP.md`

- [ ] **Step 1: Add failing docs assertions**

Update the README snippet and roadmap bullets to mention the new opt-in custom View whitelist.

Minimal README addition:

```md
## Custom views

You can whitelist app-specific View classes beside `@FastLayoutConfig` with
`@FastCustomViews`. LayoutX2C will generate whitelisted Views and typed attrs,
and will continue to fallback for anything not declared safe.
```

- [ ] **Step 2: Verify the docs diff is scoped**

Run:

```bash
git diff -- README.md docs/ROADMAP.md
```

Expected: only the custom View whitelist wording changes.

- [ ] **Step 3: Run the full test suite**

Run:

```bash
./gradlew test
```

Expected: PASS.

- [ ] **Step 4: Commit docs sync**

```bash
git add README.md docs/ROADMAP.md
git commit -m "docs: document custom view whitelist support"
```

## Self-Review

1. **Spec coverage:** This plan covers runtime annotations, KSP parsing, registry/codegen support, and user-facing docs. There is a task for every layer touched by the feature.
2. **Placeholder scan:** No `TBD`, `TODO`, or vague fill-ins remain in the plan text.
3. **Type consistency:** The API names are used consistently across runtime annotations, KSP parsing, and registry descriptors: `FastCustomViews`, `FastCustomView`, `FastCustomViewAttr`, `FastCustomViewAttrKind`, `CustomViewDescriptor`, and `CustomViewAttribute`.


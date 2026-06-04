# Custom View Whitelist Design

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Let LayoutX2C safely generate a user-declared whitelist of custom View classes and a small, explicit set of their attributes when those declarations live beside the existing layout config annotations.

**Architecture:** Keep the feature opt-in and local to the existing config object. Add runtime annotations that describe custom View classes and supported attribute kinds, teach the KSP processor to read them from the same source file as `@FastLayoutConfig`, and extend the registry layer so analyzer/codegen can treat those Views as known only when they were explicitly declared. Unknown custom Views and undeclared attributes continue to fallback.

**Tech Stack:** Kotlin, KSP, KotlinPoet, JUnit 4, Truth, AndroidX Core, existing `ResourceAwareViewRegistry`.

---

## Scope

This feature adds a whitelist for app-specific Views such as `PriceView` or `BadgeView`.
The whitelist is declared on the same config object that already carries `@FastLayoutConfig`
or `@FastLayouts` / `@FastLayoutPattern` usage. The first release supports only explicit,
compile-time known attributes. It does not try to discover arbitrary Kotlin properties, and
it does not change runtime inflation behavior for undeclared Views.

## Proposed API

### Runtime annotations

Add two new public annotations under `runtime/src/main/java/com/github/donglua/layoutx2c/runtime/annotation/`:

```kotlin
@MustBeDocumented
@Retention(AnnotationRetention.BINARY)
@Target(AnnotationTarget.CLASS)
annotation class FastCustomViews(
    vararg val value: FastCustomView
)

@MustBeDocumented
@Retention(AnnotationRetention.BINARY)
@Target(AnnotationTarget.ANNOTATION_CLASS, AnnotationTarget.CLASS)
annotation class FastCustomView(
    val viewClass: KClass<*>,
    val attrs: Array<FastCustomViewAttr> = []
)

@MustBeDocumented
@Retention(AnnotationRetention.BINARY)
@Target(AnnotationTarget.ANNOTATION_CLASS, AnnotationTarget.CLASS)
annotation class FastCustomViewAttr(
    val name: String,
    val kind: FastCustomViewAttrKind
)

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

### Example usage

```kotlin
@FastLayoutConfig
@FastCustomViews(
    FastCustomView(
        viewClass = PriceView::class,
        attrs = [
            FastCustomViewAttr(name = "app:priceColor", kind = FastCustomViewAttrKind.COLOR),
            FastCustomViewAttr(name = "app:label", kind = FastCustomViewAttrKind.STRING)
        ]
    )
)
object LayoutX2CConfig {
    val layouts = intArrayOf(
        R.layout.activity_main,
        R.layout.item_price
    )
}
```

In this model, `app:priceColor` becomes a generated setter call that resolves
`@color/red` with the existing resource-aware code path, for example:

```kotlin
priceView.setPriceColor(ContextCompat.getColor(context, R.color.red))
```

## Architecture

### 1. KSP input parsing

`LayoutX2CProcessor` keeps reading layout names from `@FastLayoutConfig`,
`@FastLayouts`, and `@FastLayoutPattern`. It also scans the same annotated source
files for `@FastCustomViews` and extracts a compact internal model:

- the custom View class name
- each attribute name
- the attribute kind

Invalid entries are ignored with warnings. A custom View class that cannot be resolved,
is not a `View`, or duplicates an existing declaration does not stop code generation.

### 2. Registry extension

`ResourceAwareViewRegistry` gets an optional `customViews` input. For each declared
custom View it adds a view handler and a tiny set of attribute handlers derived from
the descriptor. The registry should treat the custom View as known only when the tag
name matches the declared class name or fully qualified name.

Attribute emission follows the same pattern as built-in handlers:

- `STRING` -> quoted string or string resource lookup
- `BOOLEAN` -> literal boolean
- `INT` / `FLOAT` -> numeric literal
- `DIMENSION` -> existing dimension code path
- `COLOR` -> `ContextCompat.getColor(...)` for `@color/...` and `Color.parseColor(...)` for literals
- `COLOR_STATE_LIST` -> `ContextCompat.getColorStateList(...)` or `ColorStateList.valueOf(...)`
- `DRAWABLE_REF` -> drawable resource lookup
- `RESOURCE_REF` -> direct resource reference using the existing resolver

If an attribute value cannot be expressed safely for the declared kind, that attribute
is marked unsupported and the node falls back conservatively.

### 3. Analyzer and codegen behavior

`LayoutAnalyzerV2` and `LayoutCodeGenerator` do not get a special path. They continue
using the registry contract. That keeps the change localized and preserves the current
fallback semantics:

- declared custom View + supported attrs -> generated
- declared custom View + unsupported attr -> partial or fallback, depending on the node
- undeclared custom View -> current fallback behavior

## Error Handling

- Unknown annotation shapes are skipped with a warning.
- A custom View class that is not resolvable or not a `View` is ignored.
- Duplicate custom View declarations are merged only when identical; conflicting
  declarations are warned and the first valid one wins.
- Unsupported attribute kinds or malformed attribute names are ignored rather than
  aborting processing.
- No runtime reflection is added. All safety decisions stay compile-time only.

## Testing

Add focused tests in three layers:

1. **Runtime annotation/API shape**
   - Ensure the new annotations live in the public runtime annotation package.
   - Keep package path consistency checks green.

2. **KSP parsing**
   - Parse a config object that combines `@FastLayoutConfig` and `@FastCustomViews`.
   - Verify the processor extracts custom View descriptors alongside layout names.

3. **Registry and codegen**
   - Verify a declared custom View tag is classified as known.
   - Verify an `app:priceColor` attribute with `COLOR` kind emits a color setter call.
   - Verify an undeclared custom View still falls back.

## Non-Goals

- No Gradle DSL for custom Views.
- No reflection-based discovery of app widgets.
- No auto-inference of arbitrary setter signatures.
- No theme / `?attr/` semantic compilation.
- No attempt to make every custom widget fully supported by default.

## Open Questions Resolved

- The whitelist lives beside the existing config object, not in a separate plugin DSL.
- Attributes are explicit and typed, so `app:priceColor` has a clear generated meaning.
- The first version is intentionally conservative and preserves fallback for everything
  outside the whitelist.


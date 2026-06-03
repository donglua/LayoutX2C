# LayoutX2C 1.0 Migration Guide

LayoutX2C 1.0 freezes the conservative public runtime contract. The project
continues to prefer fallback over unsafe generation: unsupported layouts,
attributes, styles, themes, DataBinding semantics, and custom runtime behavior
remain on the platform inflater path.

## Stable Public API

Runtime APIs marked with `@PublicApi` are part of the 1.0 compatibility
contract:

- `LayoutFactory`
- `LayoutX2CRegistry`
- `LayoutX2CFactory2`
- `FallbackInflater`
- `FastLayoutConfig`
- `FastLayouts`
- `FastLayoutPattern`

Generated code may depend on these APIs. Application code may use
`LayoutX2CRegistry` directly for explicit generated inflate, or use the Gradle
plugin and generated facades for the usual path.

## Experimental API

APIs marked with `@ExperimentalApi` are available for early adopters but may
change in a minor release. The 1.0 release does not require application code to
use any experimental runtime API.

## Incremental Cache Behavior

The KSP digest cache now tracks resource references per layout instead of
hashing every `res/values/*.xml` file for every layout. A layout digest includes:

- The layout XML itself.
- Recursively included layouts and `ViewStub` layout references.
- Referenced `@string`, `@dimen`, `@color`, `@drawable`, `@mipmap`, and
  `@style` resources.
- Nested references from resolved values resources.
- All qualifier variants for a referenced values resource.
- Stable unresolved markers for missing or unresolvable references.

Changing an unrelated values resource should no longer invalidate an unrelated
layout factory. Changing a referenced resource still invalidates that layout.

## Compatibility Contract

1.0 targets:

- AGP 8.4+ and 9.x.
- KSP 2.x.
- Android API 21-35.
- Coexistence with ViewBinding and DataBinding.

LayoutX2C does not replace the Android resource resolver, theme engine,
DataBinding runtime, or platform inflater. Unsupported semantics fallback to
the platform path.

## Breaking Changes After 1.0

Breaking changes to `@PublicApi` require:

- A migration note in this guide or a successor migration document.
- A release note naming the old and new behavior.
- A compatibility fallback when feasible.

Internal implementation classes and generated-code internals can change without
that process.

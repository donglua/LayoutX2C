# ConstraintLayout Safe Subset Design

Date: 2026-05-28

## Goal

Add first-class `androidx.constraintlayout.widget.ConstraintLayout` support for the common safe subset without adding a feature flag. LayoutX2C should generate code only when the ConstraintLayout semantics are simple and directly equivalent to platform inflation. Any unsupported or ambiguous ConstraintLayout feature must fall back through the existing fallback path.

## Non-Goals

- No Gradle DSL flag or experimental opt-in.
- No chains, guidelines, barriers, Flow, Group, Placeholder, layer helpers, dimension ratio, percent dimensions, circle constraints, constrained width/height, gone margins, baseline constraints, or constraint sets.
- No partial ConstraintLayout generation when a complex ConstraintLayout-only feature appears in the same subtree.
- No broad refactor of analyzer/codegen architecture beyond the small boundary needed for this feature.

## Supported Scope

Support `androidx.constraintlayout.widget.ConstraintLayout` as a ViewGroup when every child uses only supported View types and supported attributes.

Supported child constraint attributes:

- `app:layout_constraintStart_toStartOf`
- `app:layout_constraintStart_toEndOf`
- `app:layout_constraintEnd_toStartOf`
- `app:layout_constraintEnd_toEndOf`
- `app:layout_constraintTop_toTopOf`
- `app:layout_constraintTop_toBottomOf`
- `app:layout_constraintBottom_toTopOf`
- `app:layout_constraintBottom_toBottomOf`
- `app:layout_constraintHorizontal_bias`
- `app:layout_constraintVertical_bias`

Supported anchor values are `parent`, `@id/name`, and `@+id/name`. Bias values must be float literals accepted by Kotlin `toFloatOrNull()`.

`android:layout_width="0dp"` and `android:layout_height="0dp"` are allowed under a ConstraintLayout parent and emit `ConstraintLayout.LayoutParams.MATCH_CONSTRAINT`.

## Fallback Rules

The analyzer should mark the whole ConstraintLayout subtree as fallback when any node in that subtree contains an unsupported ConstraintLayout-only attribute or unsupported constraint value. This is stricter than normal partial support because wrong constraint params can silently produce a badly positioned UI.

Fallback triggers include:

- Any `app:layout_constraint*` attribute outside the supported list.
- Any supported anchor attribute whose value is not `parent`, `@id/name`, or `@+id/name`.
- Any bias attribute whose value is not a float literal.
- Any ConstraintLayout helper tag or complex feature tag, including `Guideline`, `Barrier`, `Flow`, `Group`, and `Placeholder`.
- Any currently unsupported View type or existing force-fallback condition such as `style`, theme refs, or data binding expressions.

## Architecture

Keep the implementation close to existing LayoutX2C patterns.

1. Add `LayoutNode.isConstraintLayout()` in `parser/LayoutNodeTypes.kt`.
2. Add a `ViewHandler` for `androidx.constraintlayout.widget.ConstraintLayout` in `DefaultViewRegistry`.
3. Add a compact ConstraintLayout rule helper inside compiler-core, preferably near registry/codegen boundaries rather than as a large new abstraction. It should expose:
   - supported constraint attribute names
   - unsupported complex constraint attribute detection
   - anchor value parsing
   - bias value parsing
4. Extend `DefaultViewRegistry` so supported constraint layout params are known layout attributes only for children whose parent is ConstraintLayout.
5. Extend `LayoutAnalyzer` with a ConstraintLayout invalid-param pass similar to the existing RelativeLayout validation. If an invalid ConstraintLayout param exists anywhere in a ConstraintLayout subtree, mark that subtree fallback.
6. Extend `DefaultLayoutParamsEmitter` so children of ConstraintLayout receive `ConstraintLayout.LayoutParams`.

This avoids a new feature flag and avoids scattering raw string checks across analyzer and emitter.

## Code Generation

For a child whose parent is ConstraintLayout:

- Emit `ConstraintLayout.LayoutParams(width, height)`.
- Map `match_parent` and `wrap_content` to normal `ViewGroup.LayoutParams` constants.
- Map `0dp` to `ConstraintLayout.LayoutParams.MATCH_CONSTRAINT`.
- Emit margins through the existing margin logic.
- Emit anchors after layout params are assigned:
  - `startToStart`
  - `startToEnd`
  - `endToStart`
  - `endToEnd`
  - `topToTop`
  - `topToBottom`
  - `bottomToTop`
  - `bottomToBottom`
- Map `parent` to `ConstraintLayout.LayoutParams.PARENT_ID`.
- Map `@id/name` and `@+id/name` to `R.id.name`.
- Emit `horizontalBias` and `verticalBias` only when present.

Root layout params should also understand a non-null ConstraintLayout parent in `emitRoot`, so generated roots can attach correctly when inflated into a ConstraintLayout.

## Demo And Dependencies

Add a `demo_constraint.xml` layout that uses:

- ConstraintLayout root.
- At least three child views.
- Parent anchors.
- Sibling anchors.
- One horizontal or vertical `0dp` match-constraint dimension.
- Horizontal and vertical bias.

Add the demo entry to `DemoLayoutCatalog`, `MainActivity`, benchmark, and code viewer surfaces using the existing catalog-driven flow.

If the demo module does not already depend on `androidx.constraintlayout:constraintlayout`, add it there. Compiler-core should keep using KotlinPoet class names and should not add a runtime dependency to the pure JVM compiler module.

## Testing

Unit tests:

- Analyzer marks a safe ConstraintLayout subtree as `FULL`.
- Analyzer falls back for unsupported constraint attributes such as chain style, dimension ratio, percent, circle, guideline, barrier, and gone margins.
- Analyzer falls back when supported anchors use unsupported values.
- Codegen emits `ConstraintLayout.LayoutParams`, parent id mapping, sibling id mapping, match-constraint dimensions, margins, and bias.
- Codegen does not emit ConstraintLayout params for non-ConstraintLayout parents.

Instrumentation tests:

- Extend `GeneratedInflateEquivalenceTest` snapshots to include ConstraintLayout params:
  - layout params class
  - width and height
  - margins
  - all supported constraint fields
  - horizontal and vertical bias
- Add the new demo layout to platform-vs-generated equivalence coverage.
- Keep registry facade coverage for the generated demo layout.

Regression tests:

- Existing RelativeLayout, ScrollView, RecyclerView, DataBinding wrapper, fallback subtree, and root fallback tests must continue to pass.

## Acceptance Criteria

- Safe ConstraintLayout layouts generate Kotlin code by default without a Gradle flag.
- Unsupported ConstraintLayout features fallback instead of generating partial or incorrect constraint params.
- Generated demo ConstraintLayout view tree matches platform inflation in instrumentation tests.
- Compiler-core unit tests cover both supported generation and unsupported fallback.
- `docs/ROADMAP.md` can later be updated to mark the safe subset as implemented, but this design does not require changing roadmap text before implementation.

## Risks

- ConstraintLayout has many interacting params; the first version must prefer fallback over partial generation.
- Snapshot equivalence can miss actual solved positions. The first acceptance gate compares layout params, not pixel positions. A later device screenshot/layout-position test can be added if real-world regressions appear.
- `0dp` has different meaning outside ConstraintLayout. The emitter must scope match-constraint behavior strictly to ConstraintLayout parents.
- Some XML layouts use `app:` namespace aliases differently. The parser stores prefixed names as seen in XML, so first support targets the common `app:` prefix used by Android tooling.

## Implementation Order

1. Add parser tag helper and registry support for ConstraintLayout root.
2. Add constraint attribute/value validation and fallback tests.
3. Add ConstraintLayout layout param emission and codegen tests.
4. Add demo layout, catalog entry, and dependency if needed.
5. Extend instrumentation snapshot and equivalence test coverage.
6. Run focused compiler-core tests, demo unit tests, and Android instrumentation where the local environment permits.

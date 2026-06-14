# LayoutX2C 1.1.0

LayoutX2C 1.1.0 expands the safe generated path after the 1.0 release. The focus is broader
attribute coverage, stronger DataBinding compatibility, and better release/reporting confidence
without changing the conservative fallback contract.

## Added

- Support for common transform attributes such as rotation, scale, translation, pivot, elevation,
  and clip-related view values when they can be generated safely.
- Support for common `EditText` attributes including input behavior, hint/text handling, max length,
  IME options, select-all-on-focus, and related text-like values.
- Support for additional view state attributes such as selected, activated, duplicate parent state,
  accessibility importance, scrollbars, and over-scroll mode.
- Support for resolving supported theme drawable attributes where the value can stay equivalent to
  platform inflation semantics.

## Fixed

- Handles malformed DataBinding include structures more defensively instead of generating invalid
  binding facade code.
- Tracks qualified layout resources so resource variants participate in digest and regeneration
  decisions.
- Preserves ConstraintLayout Guideline layout params in generated code and demo equivalence coverage.
- Avoids emitting unsupported attribute handlers for values that should remain on the fallback path.
- Binds DataBinding root fields and include roots by the correct root view id.

## Verification and Tooling

- Added Kover and Codecov reporting to CI.
- Expanded generated-vs-inflated demo equivalence coverage for TextView, ImageView, RelativeLayout,
  EditText, view state, and Guideline cases.
- Added coverage for fallback child path traversal, dimension helpers, registry value branches,
  BindingAdapter config generation, unsupported BindingAdapter expressions, plugin defaults, provider
  wiring, and report task behavior.
- Documented fallback performance constraints and rejected runtime partial-inflate approaches.

## Compatibility Notes

- This release keeps the 1.0 public API and conservative fallback model.
- Dynamic theme semantics, complex DataBinding expressions, Observable/LiveData lifecycle tracking,
  unsupported BindingAdapters, and unsupported custom View attributes still remain on the native
  DataBinding or fallback path.

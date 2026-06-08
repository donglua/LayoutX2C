# FallbackInflater 性能优化记录

## 背景

`FallbackInflater` 的目标是语义安全：当 LayoutX2C 不能等价生成某个布局或子树时，回到平台
`LayoutInflater`。当前 child fallback 采用 full-tree extraction：

```kotlin
val fullTree = inflater.inflate(layoutId, parent, false)
val child = findChildByPath(fullTree, childPath, layoutName)
(child.parent as? ViewGroup)?.removeView(child)
return child
```

这会创建整棵 View tree，再摘取目标节点。大型 layout 中，fallback 子树可能比直接使用原生
inflate 更慢。该成本是语义正确性换来的结果，而不是实现遗漏。

## 已拒绝方案

### Direct parser inflate

不再尝试把 seek 后的 `XmlPullParser` 直接传给
`LayoutInflater.inflate(XmlPullParser, root, attachToRoot)`。

主要原因：

- `LayoutInflater` 会调用 `advanceToRootNode()`，该方法会继续推进 parser，目标节点可能被跳过。
- wrapper parser 会让 `Xml.asAttributeSet(parser)` 返回 `XmlPullAttributes`，后续平台属性解析会把
  `AttributeSet` 强转为 `XmlBlock.Parser` 并崩溃。
- 反射调用 `createViewFromTag()` 或 `rInflateChildren()` 需要复刻平台 inflate 语义，包括
  `Factory2`、theme、style、`LayoutParams`、`merge`、`include`、`requestFocus`、`tag` 和
  `onFinishInflate()` 调用时机，维护成本过高。
- 手写 inflater 等同于重新实现 Android 属性解析系统，不适合作为 LayoutX2C 主线能力。

相关废弃记录见 [FALLBACK_PARTIAL_INFLATE.md](./FALLBACK_PARTIAL_INFLATE.md)。

### Native hook

Native hook `LayoutInflater` 或 `advanceToRootNode()` 不进入主线。

风险：

- 依赖平台内部实现和设备 ROM 行为。
- 可能影响应用审核、稳定性和调试。
- Android 版本升级后失效概率高。
- 与 LayoutX2C 的「保守、可发布、可诊断」目标冲突。

该方向只适合作为外部技术研究，不在仓库文档中提供实现指南。

### View 池复用

View 池可以减少重复 inflate 成本，但暂不作为默认方案。

主要风险：

- View 可能绑定 `Context`、theme、listener、adapter、animation、transient state 或 parent。
- 复用前需要完整状态清理，遗漏会引入难以定位的 UI bug。
- 与原生 inflate 的「每次创建新 View」语义不同。

如后续研究该方向，必须先定义严格的可复用 View 子集，并用真实 Android runtime 测试覆盖状态清理。

## 当前可用优化

### 批量摘取

同一个 layout 中有多个 sibling fallback 时，优先共享一次 full-tree inflate，再摘取多个目标节点。

```kotlin
val children = FallbackInflater.inflateChildren(
    context = context,
    layoutId = layoutId,
    childPaths = childPaths,
    parent = parent
)
```

该策略已经进入当前实现方向，适合减少重复创建整棵树的成本。

### Registry 负缓存

对不支持或无法生成的布局结果使用缓存，减少重复分析和查找成本。该策略不能消除 View 创建成本，
但能降低重复 fallback 判定开销。

### 编译期扩大支持面

根本优化是减少进入 fallback 的节点数量：

- 扩展 `DefaultViewRegistry` 和 View / 属性 handler。
- 对明确可验证的 `ViewGroup` 生成局部子树代码。
- 对自定义 View 使用显式白名单和 typed 属性声明。
- 对复杂、动态或无法静态保证的语义继续 fallback。

## 可研究方向

### 异步预热

异步预热可以改变 View 创建时机，而不是改变 inflate 语义：

```kotlin
FallbackPreheater.preheat(context, layoutId, childPath)
val view = FallbackInflater.inflateChild(context, layoutId, childPath, parent)
```

该方向的价值在于把 full-tree inflate 从关键路径前移。它仍然需要保持原生 inflate 语义，并需要处理：

- 预热 View 的生命周期和 `Context` 绑定。
- parent、`LayoutParams` 和 attach 语义。
- 超时策略，避免主线程等待预热结果过久。
- 内存占用和缓存淘汰。
- 预热结果未命中时回到同步 fallback。

在没有真实设备 benchmark 和状态安全测试前，不建议默认启用。

### 编译期最小化 fallback

对于部分可生成的子树，后续可以研究更细的分析边界：

- 支持的节点继续生成代码。
- 只有无法等价生成的最小子树进入 fallback。
- 对 `LayoutParams` 和父子关系保持原生等价性测试。

该方向比 runtime hack 更符合 LayoutX2C 的长期设计，但需要足够的 analyzer 和 generated vs inflated
测试支撑。

## 验证要求

任何 fallback 性能优化都必须先证明语义不变：

- generated vs inflated 等价性测试覆盖目标布局。
- `LayoutParams`、id、visibility、theme/style 相关属性保持一致。
- `merge`、`include`、`fragment`、`ViewStub` 等特殊标签不被 partial inflate 破坏。
- DataBinding `<layout>` root 和 `childPath` 定位不回退。
- Android runtime 或真实设备验证不出现 `XmlPullAttributes` 到 `XmlBlock.Parser` 的强转崩溃。
- 性能结论必须来自 demo benchmark 或真实设备数据，不能只依赖理论估算。

## 结论

Fallback 的性能优化优先级如下：

1. 扩大编译期支持面，减少 fallback。
2. 对同一 layout 的多个 fallback 子树使用批量摘取。
3. 研究编译期最小化 fallback。
4. 谨慎评估异步预热。
5. 不采用 direct parser inflate、Native hook 或默认 View 池复用。

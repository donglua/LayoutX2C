# FallbackInflater partial inflate 废弃方案记录

## 状态

该方案已废弃。

`FallbackInflater` 不再通过 seek 后的 `XmlPullParser` 或 replay wrapper 调用
`LayoutInflater.inflate(XmlPullParser, root, attachToRoot)`。fallback 子树统一采用
full-tree extraction：先 inflate 原始 layout，再按 `childPath` 找到目标节点并从父节点
detach。

当前行为与 [ROADMAP.md](./ROADMAP.md) 保持一致：

- root fallback：直接 inflate 原始 layout。
- child fallback：完整 inflate 原始 layout，再按 `childPath` 摘取目标节点。
- batched child fallback：同一个原始 layout 只完整 inflate 一次，再摘取多个目标节点。

## 原目标

旧方案试图减少 child fallback 成本：

```kotlin
val fullTree = inflater.inflate(layoutId, parent, false)
val child = findChildByPath(fullTree, childPath, layoutName)
(child.parent as? ViewGroup)?.removeView(child)
return child
```

对于大型 layout，这会创建整棵 View tree。旧方案希望先用
`Resources.getLayout(layoutId)` 定位到目标 `START_TAG`，再只 inflate 目标子树。

## 废弃原因

Android framework 的属性解析依赖原始 `XmlBlock.Parser`。`LayoutInflater` 入口会先执行：

```java
final AttributeSet attrs = Xml.asAttributeSet(parser);
advanceToRootNode(parser);
```

如果传入的是普通 wrapper，`Xml.asAttributeSet(parser)` 会返回 `XmlPullAttributes`。
后续 `Resources` / `ThemeImpl` 解析属性时会把 `AttributeSet` 强转成
`XmlBlock.Parser`：

```java
final XmlBlock.Parser parser = (XmlBlock.Parser) set;
```

这会导致类似以下崩溃：

```text
android.util.XmlPullAttributes cannot be cast to android.content.res.XmlBlock$Parser
```

因此，`ReplayCurrentStartTagXmlPullParser` 这类 wrapper 不能安全传给
`LayoutInflater.inflate(XmlPullParser, ...)`。问题不只是白名单不够保守，而是 wrapper
无法保留 framework 需要的具体 parser 类型。

## 已拒绝的做法

以下做法不再作为实现方向：

- `ReplayCurrentStartTagXmlPullParser`
- `SAFE_PARTIAL_INFLATE_TAGS`
- 按 tag 白名单启用 partial inflate
- 按属性组合启用 partial inflate
- 让 `FallbackChildPlan.partialInflateAllowed` 重新打开 parser replay 路径

保留 `FallbackChildPlan` 的字段仅用于 API 兼容和生成代码结构稳定。runtime 不应读取该字段来启用
partial inflate。

## 仍然有效的设计约束

`childPath` 合约继续有效：

- 空 path 表示真实 View root。
- DataBinding `<layout>` 会先跳过 `<data>`，进入真实 View root。
- path 中每一项表示当前节点的直接子节点 index。
- index 只统计直接子 `START_TAG`，不会把孙节点算入当前层。

示例：

```xml
<LinearLayout>
  <TextView />
  <FrameLayout>
    <TextView />
  </FrameLayout>
</LinearLayout>
```

- `intArrayOf()` 指向 `LinearLayout`。
- `intArrayOf(0)` 指向第一个 `TextView`。
- `intArrayOf(1)` 指向 `FrameLayout`。
- `intArrayOf(1, 0)` 指向 `FrameLayout` 内部的 `TextView`。

特殊标签继续依赖原生 inflate 语义，不能作为独立子树 partial inflate：

- `merge`
- `include`
- `fragment`

## 当前实现边界

当前 runtime 选择语义安全优先：

```kotlin
val fullTree = inflater.inflate(layoutId, parent, false)
val child = findChildByPath(fullTree, childPath, layoutName)
(child.parent as? ViewGroup)?.removeView(child)
return child
```

多 sibling fallback 会共享一次 full-tree inflate，避免为同一个 layout 重复创建整棵树。

## 后续可研究方向

如需继续优化 child fallback 性能，应避开 `LayoutInflater.inflate(XmlPullParser, ...)` 的
wrapper 方案。可研究方向包括：

- 编译期直接生成更多 View 和 LayoutParams，减少进入 fallback 的子树数量。
- 针对明确支持的 ViewGroup 生成局部子树代码，而不是把局部 XML 交回 `LayoutInflater`。
- 在 runtime full-tree extraction 之外增加缓存或批量摘取策略，但必须避免复用已绑定上下文、
  parent、listener 或 transient state 的 View 实例。
- 研究 AOSP/开源 inflater 是否提供可保留 `XmlBlock.Parser` 语义的子树入口；没有明确证据前不落地。

## 验证要求

相关改动至少需要覆盖：

- codegen 生成的 `FallbackChildPlan(..., false)`。
- runtime 源码不再包含 parser replay wrapper。
- DataBinding wrapper 下的 `childPath` 定位。
- 多 fallback sibling 只触发一次 full-tree extraction。
- 真实设备或 Android runtime 测试中不再出现 `XmlPullAttributes` 到 `XmlBlock.Parser` 的 cast 崩溃。

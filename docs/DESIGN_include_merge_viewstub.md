# include / merge / ViewStub 支持设计文档

## 概述

当前 LayoutX2C 对 `<include>`、`<merge>`、`<ViewStub>` 三个特殊标签采用整棵 layout fallback 的保守策略。这三个标签是 Android XML 布局中的高频组合模式，支持它们能显著降低真实项目的 fallback 率。

本文档明确这三个标签的语义、代码生成策略、与现有 fallback 子树定位的交互，以及验收口径。

---

## 1. 语义分析

### 1.1 `<include>` 标签

**XML 语法：**
```xml
<LinearLayout ...>
  <include layout="@layout/header" />
  <include layout="@layout/content" android:layout_weight="1" />
</LinearLayout>
```

**运行时语义：**
- `layout="@layout/xxx"` 指向被 include 的 layout 文件
- include 标签本身不创建 View，而是将被 include layout 的根 View 直接插入到 include 标签的位置
- include 标签上的属性（如 `layout_weight`、`layout_height` 等）作为 LayoutParams 应用到被 include layout 的根 View
- 被 include layout 的根 View 的 LayoutParams 属性被 include 标签上的属性**覆盖**

**关键约束：**
- 被 include layout 的根 View 必须与 include 标签的父 ViewGroup 兼容（如 include 在 LinearLayout 中，被 include 的根必须能接受 LinearLayout.LayoutParams）
- 不支持 include 循环引用（A include B, B include A）
- include 标签上的属性只能是 LayoutParams 相关属性，不能是 View 属性

### 1.2 `<merge>` 标签

**XML 语法：**
```xml
<!-- header.xml -->
<merge xmlns:android="http://schemas.android.com/apk/res/android">
  <TextView ... />
  <Button ... />
</merge>

<!-- activity_main.xml -->
<LinearLayout ...>
  <include layout="@layout/header" />  <!-- merge 的两个子 View 直接注入到 LinearLayout -->
</LinearLayout>
```

**运行时语义：**
- `<merge>` 是虚拟容器，不创建 View
- merge 的所有子 View 直接注入到 merge 标签的父 ViewGroup
- merge 标签本身不能有属性（除了 xmlns）
- merge 通常与 include 配合使用，避免多余的 ViewGroup 嵌套

**关键约束：**
- merge 只能作为 layout 文件的根标签
- merge 的子 View 必须与其父 ViewGroup 兼容
- merge 不能单独出现在非根位置

### 1.3 `<ViewStub>` 标签

**XML 语法：**
```xml
<LinearLayout ...>
  <ViewStub
    android:id="@+id/stub_import"
    android:inflatedId="@+id/panel_import"
    android:layout="@layout/progress"
    android:layout_width="match_parent"
    android:layout_height="wrap_content" />
</LinearLayout>
```

**运行时语义：**
- ViewStub 是一个轻量级占位符，初始不 inflate 被引用的 layout
- 调用 `stub.inflate()` 或 `stub.setVisibility(View.VISIBLE)` 时才触发 inflate
- `android:layout` 指向被延迟 inflate 的 layout 文件
- `android:inflatedId` 指定被 inflate 后的根 View 的 id（覆盖原 layout 中的 id）
- ViewStub 本身在 inflate 后被移除

**关键约束：**
- ViewStub 必须有 `android:layout` 属性
- ViewStub 的 LayoutParams 属性应用到被 inflate 的根 View
- 不支持 ViewStub 的 `android:layoutInflater` 自定义属性

---

## 2. 当前处理现状

当前代码已经具备第一版 include / merge / ViewStub 支持：
- `IncludeResolver` 会递归解析 `@layout/...`，并通过深度和 visited 集合避免循环 include。
- `XmlLayoutParser` 会给 include / merge / ViewStub 标注 `LayoutNodeType`，并在 resolver 可用时把 include 展开为被引用 layout 的根节点。
- `LayoutAnalyzerV2` 会在 `AnalyzedNode` 上记录 `includedLayoutRef`、`isMerge`、`isViewStub`，并按 FULL / PARTIAL / FALLBACK 语义传播支持度。
- `LayoutCodeGenerator` 会为普通 include 调用对应 layout factory，为 merge 根或 include+merge 内联子节点，为 ViewStub 生成 `layoutResource` / `inflatedId` 等属性。

### 2.1 支持度口径

- `FULL`：当前节点和所有已分析后代都由生成代码完整覆盖。
- `PARTIAL`：当前节点仍由生成代码创建，但自身存在未支持属性，或至少一个已分析后代为 `PARTIAL` / `FALLBACK`。
- `FALLBACK`：当前节点整棵子树交给 `LayoutInflater` fallback。

---

## 3. 设计方案

### 3.1 Parser 层扩展

#### 3.1.1 新增 `IncludeReference` 和 `MergeNode` 类型

```kotlin
// LayoutTree.kt 扩展
sealed class LayoutNodeType {
    object Regular : LayoutNodeType()
    data class Include(val layoutRef: String) : LayoutNodeType()  // @layout/xxx
    object Merge : LayoutNodeType()
    data class ViewStub(val layoutRef: String) : LayoutNodeType()  // @layout/xxx
}

data class LayoutNode(
    val tagName: String,
    val attributes: Map<String, String>,
    val children: List<LayoutNode>,
    val indexInParent: Int = 0,
    val nodeType: LayoutNodeType = LayoutNodeType.Regular
)
```

#### 3.1.2 Include 递归解析

新增 `IncludeResolver` 类：

```kotlin
class IncludeResolver(
    private val layoutDir: File,  // res/layout 目录
    private val maxDepth: Int = 10  // 防止循环引用
) {
    fun resolveInclude(layoutRef: String, depth: Int = 0): LayoutTree? {
        if (depth > maxDepth) {
            logger.warn("Include depth exceeded: $layoutRef")
            return null
        }
        
        val layoutFile = layoutDir.resolve("${layoutRef.substringAfterLast("/")}.xml")
        if (!layoutFile.exists()) {
            logger.warn("Include layout not found: $layoutRef")
            return null
        }
        
        // 检测循环引用
        if (visitedLayouts.contains(layoutFile.absolutePath)) {
            logger.warn("Circular include detected: $layoutRef")
            return null
        }
        
        visitedLayouts.add(layoutFile.absolutePath)
        return parser.parse(layoutFile)
    }
}
```

#### 3.1.3 Parser 改造

```kotlin
class XmlLayoutParser(
    private val includeResolver: IncludeResolver? = null
) {
    private fun parseElement(element: Element, indexInParent: Int): LayoutNode {
        val tagName = element.tagName
        
        return when (tagName) {
            "include" -> {
                val layoutRef = element.getAttribute("layout")
                if (layoutRef.isEmpty()) {
                    logger.error("include tag missing layout attribute")
                    // fallback: 作为普通节点处理
                    parseRegularElement(element, indexInParent)
                } else {
                    val includedTree = includeResolver?.resolveInclude(layoutRef)
                    if (includedTree != null) {
                        // 返回被 include 的根节点，但标记为 Include 类型
                        includedTree.root.copy(
                            nodeType = LayoutNodeType.Include(layoutRef),
                            indexInParent = indexInParent,
                            // include 标签上的属性作为 LayoutParams 覆盖
                            attributes = mergeIncludeAttributes(
                                includedTree.root.attributes,
                                element.attributes
                            )
                        )
                    } else {
                        // 无法解析 include，作为普通节点处理
                        parseRegularElement(element, indexInParent)
                    }
                }
            }
            "merge" -> {
                // merge 的子节点直接注入到父级
                val children = parseChildren(element)
                LayoutNode(
                    tagName = "merge",
                    attributes = emptyMap(),
                    children = children,
                    indexInParent = indexInParent,
                    nodeType = LayoutNodeType.Merge
                )
            }
            "ViewStub" -> {
                val layoutRef = element.getAttribute("android:layout")
                parseRegularElement(element, indexInParent).copy(
                    nodeType = if (layoutRef.isNotEmpty()) {
                        LayoutNodeType.ViewStub(layoutRef)
                    } else {
                        LayoutNodeType.Regular
                    }
                )
            }
            else -> parseRegularElement(element, indexInParent)
        }
    }
    
    private fun mergeIncludeAttributes(
        baseAttrs: Map<String, String>,
        includeAttrs: Map<String, String>
    ): Map<String, String> {
        // include 标签上的 LayoutParams 属性覆盖被 include 根节点的属性
        val layoutParamAttrs = includeAttrs.filterKeys { key ->
            key.startsWith("android:layout_")
        }
        return baseAttrs + layoutParamAttrs
    }
}
```

### 3.2 Analyzer 层扩展

#### 3.2.1 Include 节点分析

```kotlin
class LayoutAnalyzerV2(
    private val viewRegistry: ViewRegistry
) {
    fun analyze(tree: LayoutTree): AnalyzedNode {
        return analyzeNode(tree.root, parentTagName = null)
    }
    
    private fun analyzeNode(
        node: LayoutNode,
        parentTagName: String?
    ): AnalyzedNode {
        return when (node.nodeType) {
            is LayoutNodeType.Include -> analyzeInclude(node, parentTagName)
            is LayoutNodeType.Merge -> analyzeMerge(node, parentTagName)
            is LayoutNodeType.ViewStub -> analyzeViewStub(node, parentTagName)
            else -> analyzeRegularNode(node, parentTagName)
        }
    }
    
    private fun analyzeInclude(
        node: LayoutNode,
        parentTagName: String?
    ): AnalyzedNode {
        // include 节点本身不创建 View，其支持度取决于：
        // 1. 被 include 的根 View 是否支持
        // 2. include 标签上的 LayoutParams 属性是否支持
        
        val rootViewHandler = viewRegistry.getHandler(node.tagName)
        if (rootViewHandler == null) {
            return AnalyzedNode(
                node = node,
                supportLevel = SupportLevel.FALLBACK,
                supportedAttributes = emptySet(),
                unsupportedAttributes = node.attributes.keys,
                children = emptyList(),
                indexInParent = node.indexInParent,
                parentTagName = parentTagName
            )
        }
        
        // 检查 LayoutParams 属性
        val layoutParamAttrs = node.attributes.filterKeys { it.startsWith("android:layout_") }
        val (supported, unsupported) = checkLayoutParamSupport(
            layoutParamAttrs,
            parentTagName
        )
        
        // 递归分析被 include 的子树
        val analyzedChildren = node.children.map { child ->
            analyzeNode(child, node.tagName)
        }
        
        val supportLevel = when {
            unsupported.isNotEmpty() -> SupportLevel.PARTIAL
            analyzedChildren.any { it.supportLevel == SupportLevel.FALLBACK } -> SupportLevel.PARTIAL
            else -> SupportLevel.FULL
        }
        
        return AnalyzedNode(
            node = node,
            supportLevel = supportLevel,
            supportedAttributes = supported,
            unsupportedAttributes = unsupported,
            children = analyzedChildren,
            indexInParent = node.indexInParent,
            parentTagName = parentTagName
        )
    }
    
    private fun analyzeMerge(
        node: LayoutNode,
        parentTagName: String?
    ): AnalyzedNode {
        // merge 不创建 View，其子节点直接注入到父级
        // 支持度取决于子节点是否都支持
        
        val analyzedChildren = node.children.map { child ->
            analyzeNode(child, parentTagName)  // 注意：父级是 merge 的父级，不是 merge 本身
        }
        
        val supportLevel = when {
            analyzedChildren.isEmpty() -> SupportLevel.FULL
            analyzedChildren.all { it.supportLevel == SupportLevel.FULL } -> SupportLevel.FULL
            analyzedChildren.all { it.supportLevel in listOf(SupportLevel.FULL, SupportLevel.PARTIAL) } -> SupportLevel.PARTIAL
            else -> SupportLevel.FALLBACK
        }
        
        return AnalyzedNode(
            node = node,
            supportLevel = supportLevel,
            supportedAttributes = emptySet(),
            unsupportedAttributes = emptySet(),
            children = analyzedChildren,
            indexInParent = node.indexInParent,
            parentTagName = parentTagName
        )
    }
    
    private fun analyzeViewStub(
        node: LayoutNode,
        parentTagName: String?
    ): AnalyzedNode {
        // ViewStub 本身是一个 View，需要检查其属性支持度
        // 被延迟 inflate 的 layout 不在编译期分析（因为不知道何时 inflate）
        
        val handler = viewRegistry.getHandler("ViewStub")
        if (handler == null) {
            return AnalyzedNode(
                node = node,
                supportLevel = SupportLevel.FALLBACK,
                supportedAttributes = emptySet(),
                unsupportedAttributes = node.attributes.keys,
                children = emptyList(),
                indexInParent = node.indexInParent,
                parentTagName = parentTagName
            )
        }
        
        val (supported, unsupported) = handler.checkAttributeSupport(node.attributes)
        
        val supportLevel = if (unsupported.isEmpty()) SupportLevel.FULL else SupportLevel.PARTIAL
        
        return AnalyzedNode(
            node = node,
            supportLevel = supportLevel,
            supportedAttributes = supported,
            unsupportedAttributes = unsupported,
            children = emptyList(),  // ViewStub 没有编译期子节点
            indexInParent = node.indexInParent,
            parentTagName = parentTagName
        )
    }
}
```

### 3.3 CodeGen 层扩展

#### 3.3.1 Include 代码生成

```kotlin
class LayoutCodeGenerator {
    fun generateViewCreation(analyzedNode: AnalyzedNode): CodeBlock {
        return when (analyzedNode.node.nodeType) {
            is LayoutNodeType.Include -> generateIncludeInflation(analyzedNode)
            is LayoutNodeType.Merge -> generateMergeChildren(analyzedNode)
            is LayoutNodeType.ViewStub -> generateViewStubCreation(analyzedNode)
            else -> generateRegularViewCreation(analyzedNode)
        }
    }
    
    private fun generateIncludeInflation(analyzedNode: AnalyzedNode): CodeBlock {
        // include 节点本身不创建 View，而是 inflate 被 include 的 layout
        // 注意：如果被 include 的 layout 是 <merge>，生成的代码调用将不会返回单一 View
        // (或 factory.inflate 签名为无返回值)，并且直接附加到 parent。
        // 以下是普通 include (非 merge) 的生成示例：
        
        val includedLayoutName = analyzedNode.node.nodeType.let {
            (it as LayoutNodeType.Include).layoutRef.substringAfterLast("/")
        }
        
        val factoryName = "${includedLayoutName.toCamelCase()}X2CFactory"
        
        val code = CodeBlock.builder()
            .addStatement(
                "val %L = %L.inflate(parent, false)",
                analyzedNode.node.attributes["android:id"]?.substringAfterLast("/") ?: "view",
                factoryName
            )
        
        // 应用 include 标签上的 LayoutParams 属性
        val layoutParamAttrs = analyzedNode.node.attributes.filterKeys { it.startsWith("android:layout_") }
        if (layoutParamAttrs.isNotEmpty()) {
            code.add(generateLayoutParamsAssignment(
                viewName = "view",
                attributes = layoutParamAttrs,
                parentTagName = analyzedNode.parentTagName
            ))
        }
        
        return code.build()
    }
    
    private fun generateMergeChildren(analyzedNode: AnalyzedNode): CodeBlock {
        // merge 不创建 View，直接生成其子节点的创建代码
        val code = CodeBlock.builder()
        for (child in analyzedNode.children) {
            code.add(generateViewCreation(child))
        }
        return code.build()
    }
    
    private fun generateViewStubCreation(analyzedNode: AnalyzedNode): CodeBlock {
        // ViewStub 是一个普通 View，但需要特殊处理 android:layout 属性
        val code = CodeBlock.builder()
        
        val viewName = analyzedNode.node.attributes["android:id"]?.substringAfterLast("/") ?: "stub"
        code.addStatement(
            "val %L = %T(context)",
            viewName,
            ClassName("android.view", "ViewStub")
        )
        
        // 设置 android:layout 属性
        val layoutRef = analyzedNode.node.nodeType.let {
            (it as LayoutNodeType.ViewStub).layoutRef
        }
        val layoutId = "R.layout.${layoutRef.substringAfterLast("/")}"
        code.addStatement("%L.setLayoutResource(%L)", viewName, layoutId)
        
        // 设置其他属性
        val otherAttrs = analyzedNode.node.attributes.filterKeys { 
            !it.startsWith("android:layout") && it != "android:id"
        }
        code.add(generateAttributeAssignment(viewName, otherAttrs, "ViewStub"))
        
        // 添加到父级
        code.addStatement(
            "parent.addView(%L)",
            viewName
        )
        
        return code.build()
    }
}
```

#### 3.3.2 Include 工厂类生成

对于每个被 include 的 layout，需要生成对应的工厂类：

```kotlin
// 被 include 的 layout: header.xml
// 生成的工厂类: HeaderX2CFactory.kt

object HeaderX2CFactory {
    // 普通 View 根节点的签名
    fun inflate(parent: ViewGroup?, attachToParent: Boolean): View {
        val context = parent?.context ?: throw IllegalArgumentException("parent cannot be null")
        val root = LinearLayout(context)
        // ... 生成 header.xml 的 View 创建代码
        if (attachToParent && parent != null) {
            parent.addView(root)
        }
        return root
    }
    
    // 如果 header.xml 是 <merge> 布局，签名和实现会有所不同，例如：
    // fun inflate(parent: ViewGroup) {
    //     val context = parent.context
    //     // ... 直接将子节点添加到 parent
    //     // 无返回值，且强制 attachToParent = true
    // }
}
```

---

## 4. 与现有 Fallback 子树定位的交互

### 4.1 Fallback 子树定位

当前 fallback 子树定位使用 `XmlPullParser` seek + partial inflate，需要精确的 child path。

### 4.2 Include/Merge/ViewStub 的影响

- **Include**：如果被 include 的 layout 中有 FALLBACK 节点，include 节点标记为 PARTIAL，fallback 子树定位需要跨越 include 边界
- **Merge**：merge 的子节点直接注入，fallback 子树定位不需要特殊处理
- **ViewStub**：ViewStub 本身可能是 FALLBACK，但被延迟 inflate 的 layout 不在编译期处理

### 4.3 改进方案

扩展 `AnalyzedNode` 以支持跨 include 边界的 fallback 定位：

```kotlin
data class AnalyzedNode(
    // ... 现有字段
    /** Include 引用的被 include layout 文件名 */
    val includedLayoutFile: String? = null,
    /** 是否是 merge 节点 */
    val isMerge: Boolean = false,
    /** 是否是 ViewStub 节点 */
    val isViewStub: Boolean = false
)
```

Fallback 子树定位时，如果遇到 include 节点，需要：
1. 记录 include 的 layout 文件名
2. 在被 include layout 的工厂类中定位 fallback 子树
3. 生成跨越 include 边界的 fallback 调用

---

## 5. 验收口径

### 5.1 功能验收

- [ ] Parser 正确识别 include/merge/ViewStub 标签
- [ ] Include 递归解析被 include layout，检测循环引用
- [ ] Analyzer 正确分析 include/merge/ViewStub 的支持度
- [ ] CodeGen 为 include 生成工厂类和 inflate 调用
- [ ] CodeGen 为 merge 生成子节点创建代码
- [ ] CodeGen 为 ViewStub 生成创建和 setLayoutResource 调用
- [ ] Include 标签上的 LayoutParams 属性正确应用到被 include 根 View
- [ ] Merge 的子节点正确注入到父级
- [ ] ViewStub 的 android:inflatedId 属性正确处理

### 5.2 测试覆盖

**单元测试：**
- `IncludeResolverTest`：include 递归解析、循环检测
- `XmlLayoutParserIncludeTest`：include/merge/ViewStub 标签解析
- `LayoutAnalyzerIncludeTest`：include/merge/ViewStub 支持度分析
- `LayoutCodeGeneratorIncludeTest`：include/merge/ViewStub 代码生成

**集成测试：**
- `IncludeMergeViewStubIntegrationTest`：端到端 inflate 等价性验证
  - 简单 include
  - 嵌套 include
  - Include + merge 组合
  - Include 标签上的 LayoutParams 属性
  - ViewStub 创建和 setLayoutResource

**Demo 样例：**
- `demo_include.xml`：简单 include 示例
- `demo_include_nested.xml`：嵌套 include 示例
- `demo_merge.xml`：merge 标签示例
- `demo_viewstub.xml`：ViewStub 示例

### 5.3 性能基准

- Include 递归解析的时间开销（相对于 fallback 整棵 layout）
- 生成代码的大小（相对于 fallback 调用）

### 5.4 文档

- 更新 README 说明 include/merge/ViewStub 支持范围
- 更新 ROADMAP 标记 v0.4.0 P0 完成

---

## 6. 实现步骤

### Phase 1: Parser 层（1-2 天）
1. 定义 `LayoutNodeType` 枚举
2. 实现 `IncludeResolver` 类
3. 改造 `XmlLayoutParser` 识别 include/merge/ViewStub
4. 编写 Parser 单元测试

### Phase 2: Analyzer 层（1-2 天）
1. 实现 `analyzeInclude` / `analyzeMerge` / `analyzeViewStub` 方法
2. 扩展 `AnalyzedNode` 字段
3. 编写 Analyzer 单元测试

### Phase 3: CodeGen 层（2-3 天）
1. 实现 `generateIncludeInflation` / `generateMergeChildren` / `generateViewStubCreation`
2. 实现 Include 工厂类生成
3. 编写 CodeGen 单元测试

### Phase 4: 集成测试与 Demo（1-2 天）
1. 编写集成测试
2. 添加 Demo 样例
3. 验证 generated vs inflated 等价性

### Phase 5: 文档与发布（0.5 天）
1. 更新 README 和 ROADMAP
2. 清理 worktree 分支

---

## 7. 风险与缓解

| 风险 | 影响 | 缓解 |
|------|------|------|
| Include 循环引用导致无限递归 | 高 | 引入 maxDepth 限制和 visitedLayouts 集合 |
| Include 跨越 include 边界的 fallback 定位复杂 | 中 | 先支持 FULL/PARTIAL include，FALLBACK include 暂时整棵 fallback |
| Merge 子节点的 LayoutParams 语义不清 | 中 | 明确 merge 子节点继承父级 LayoutParams 类型 |
| ViewStub 的 android:inflatedId 处理 | 低 | 在 generated factory 中处理 inflatedId 替换 |

当前阶段仍需显式跟踪：
- 跨 include 边界的 fallback 子树定位仍需要端到端验证。
- include 指向 merge 时，include 标签自身的 `android:id` 和 `layout_*` 属性应继续保持 Android 原生语义，不应用到 merge 虚拟根。
- ViewStub 引用 layout 的运行时 inflate 等价性仍需要 demo 或 androidTest 覆盖。

---

## 8. 后续优化（v0.5+）

- Include 的增量编译：被 include layout 改动时只重新生成对应工厂类
- Merge 的编译期展开：将 merge 子节点直接内联到父级，避免工厂类调用
- ViewStub 的延迟生成：为 ViewStub 引用的 layout 生成独立工厂类，支持运行时 inflate
- Include 的条件编译：支持 `android:layout` 的资源限定符（如 `@layout/header_land`）

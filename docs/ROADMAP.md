# LayoutX2C Roadmap

> 编译期 XML Layout -> Kotlin 代码生成。核心策略是「能等价生成的生成，不能等价生成的 fallback」。生成错误比 fallback 更危险，因此路线图优先围绕语义正确性、可诊断性和可发布性展开。

---

## Current

当前主干已经具备一条可用的 XML -> AST -> Analyze -> CodeGen -> Runtime inflate 管道。

**核心管道**

- XML 解析器输出 `LayoutTree` AST，并保留节点路径、DataBinding root metadata 和特殊节点语义。
- 支持度分析器按 FULL / PARTIAL / FALLBACK 三级给出节点级结果。
- KotlinPoet 代码生成已拆分为 `ViewEmitter` / `AttrEmitter` / `LayoutParamsEmitter`。
- View / 属性支持面已迁移到 `DefaultViewRegistry` 和注册式 `ViewHandler` / `AttributeHandler`。
- Runtime 提供 `LayoutFactory`、`FallbackInflater` 和 Registry 自动注册。
- 生成代码提供 direct X2C inflate facade，demo 可直接走生成入口做 benchmark。

**已支持的布局和 View**

- 容器：`LinearLayout`、`FrameLayout`、`RelativeLayout`、`ScrollView`、`HorizontalScrollView`。
- 常用 View：`TextView`、`Button`、`EditText`、`ImageView`、`View`。
- AndroidX：`RecyclerView` 作为容器创建；`ConstraintLayout` 已有安全子集支持。
- 特殊标签：`include`、`merge`、`ViewStub` 已进入编译期支持路径。
- DataBinding `<layout>` root 会透明解包到真实 View root，并生成继承 `ViewDataBinding` 的
  `{Name}X2CBinding`。

**已支持的关键属性**

- 通用：尺寸、padding、margin、gravity、visibility、id、background。
- Text-like：text、textColor、textSize、textStyle、hint、inputType。
- ImageView：src、scaleType、tint。
- ScrollView：fillViewport。
- RelativeLayout 常见规则。
- ConstraintLayout 安全子集：普通 start/end/top/bottom 约束、`0dp` match constraint、horizontal/vertical bias。

**DataBinding 子集**

- `{Name}X2CBinding` 继承 `androidx.databinding.ViewDataBinding`，并提供 `inflate()`、`bind()`、
  `root` 和按 `android:id` 生成的字段。
- `<data>` 变量会生成类型化 `@Bindable` 属性，setter 会维护 dirty flag、触发
  `notifyPropertyChanged()` 和 `requestRebind()`。
- `setVariable(variableId, value)`、`invalidateAll()`、`hasPendingBindings()`、`onFieldChange()` 和
  `executeBindings()` 已按 LayoutX2C 支持子集生成。
- 简单 `@{variable}` / `@{variable.property}` 表达式通过 `executePendingBindings()` 写回 View；
  白名单 `@={}` 绑定生成反向监听器。
- 生成代码不直接依赖 DataBinding AP 阶段输出的 `BR` 类；运行时会解析宿主 `BR` ID，并保留本地
  fallback ID 以保证 KSP Kotlin 编译顺序稳定。

**Fallback 和增量**

- fallback 子树定位支持完整 child path，并对非法路径输出可诊断错误。
- 普通 fallback 子树优先走 `XmlPullParser` seek + partial inflate；无法安全局部生成的特殊节点保留整棵 layout inflate 兼容路径。
- Gradle 插件已声明 layout / values XML 输入，并传入 `layoutx2c.cacheDir`。
- KSP 侧已落地保守 digest cache：digest 未变时可恢复 per-layout factory、facade 和 report；digest
  已纳入 include / ViewStub layout 引用图，values XML 仍作为 coarse input。
- Registry 仍是 aggregating 输出，但已通过内容 digest/cache 避免 Registry 内容未变时重复走完整生成路径。
- Gradle 插件已提供 `layoutX2CReport`，可输出项目级 JSON / HTML 汇总，并支持 CI fallback policy。

**当前限制**

- `fragment`、无法解析的 include、循环 include、超出递归深度限制的 include，以及无法等价生成的特殊语义仍会保守 fallback。
- `LayoutDigest` 还没有精确 style / dimen / color / string / drawable 引用图；values XML 仍作为 coarse input。
- 复杂 DataBinding 表达式、BindingAdapter、Observable / LiveData 自动订阅和 lifecycle 观察者语义仍交给原生 DataBinding。
- Android 端到端 generated vs inflated 等价性覆盖还需要系统补齐。

---

## Next

短期只推进会直接提高可用性和发布可信度的工作。

### 1. Android 等价性测试补齐

目标：用真实 Android inflate 结果约束高风险语义，避免代码生成只在字符串测试层面正确。

- 覆盖 include、nested include、include + merge、ViewStub、ConstraintLayout 安全子集。
- 覆盖 fallback 子树和父级 LayoutParams 保留语义。
- 对比 generated vs inflated 的 View tree、id、visibility、LayoutParams 和关键属性。

验收口径：

- 每类特殊语义都有最小端到端 Android 测试。
- 失败信息能指出 layout、节点路径和不一致属性。

### 2. README 和 Demo 同步

目标：让用户看到的支持范围与实际 Roadmap 保持一致。

- README 更新 include / merge / ViewStub / ConstraintLayout 安全子集支持范围。
- Demo 补齐对应 XML 样例和 benchmark 入口。
- 明确 DataBinding binding 子类只实现 LayoutX2C 可静态保证的 `ViewDataBinding` 子集。

验收口径：

- README 的支持范围、限制和 Roadmap 一致。
- Demo 能覆盖主要成功路径和 fallback 路径。

### 3. 精确资源引用图

目标：减少无关 values 资源改动导致的保守重跑，同时保持 fallback 正确性。

- 从 coarse values XML 输入推进到 style / dimen / color / string / drawable 引用图。
- style / theme 引用继续默认保守 fallback，只把 digest 依赖做精确。
- 保持 Registry aggregating 输出，不提前承诺 KSP isolating processor。

验收口径：

- 修改被引用资源会触发相关 layout 重新生成。
- 修改无关资源不触发无关 factory 重生成。
- 无法解析或动态 theme 语义仍保守 fallback，并在 report 中可诊断。

---

## Later

这些方向有价值，但不应阻塞短期可用性。

**Style / Theme 部分支持**

- 内联 `styles.xml` 中可静态解析的已知 style 属性。
- `?attr/` 默认继续 fallback。
- `?attr/` 编译期替换只作为显式 opt-in，并要求用户提供 theme 映射。

**自定义 View 白名单**

- 用户声明哪些自定义 View 可以安全生成。
- 白名单 View 必须有 `(Context)` 或 `(Context, AttributeSet)` 公开构造函数。
- 用户通过 DSL 声明支持属性子集；未声明属性遇到即 PARTIAL / fallback。
- 编译期静态检查构造函数签名，不满足则 warning 并拒绝生成。

**ViewStub 延迟生成**

- 为 ViewStub 引用的 layout 生成独立 factory。
- 运行时 ViewStub inflate 可选择走生成 factory。
- 必须保持原生 ViewStub 替换、inflatedId 和 LayoutParams 语义。

**ConstraintLayout 扩展**

- 逐项引入 chains、guidelines、barriers、dimension ratio、percent 等能力。
- 默认保守 fallback；每项能力必须有 generated vs inflated 等价性测试。
- 复杂 ConstraintLayout 不作为 v1.0 前置条件。

**Lint / IDE 辅助**

- Lint 检测可避免的 fallback，并给出可操作建议。
- IDE gutter icon、跳转生成代码和 quick-fix 作为 post-1.0 候选。
- 只有当 HTML/JSON 报告无法覆盖主要排查需求时再投入 IDE 插件。

**其他候选**

- Java 代码生成后端，用于兼容纯 Java 项目。
- 更精确的资源引用图，减少无关 values 改动带来的重跑。
- Runtime debug 模式输出 generated vs inflated 差异报告。

---

## 1.0

v1.0 的目标是稳定 API，发布到 Maven Central，并能被生产项目保守接入。

**API 稳定**

- `LayoutFactory`、Registry 和注解接口冻结。
- Public API 使用 `@PublicApi` / `@ExperimentalApi` 标注。
- breaking change 必须有迁移指南。

**兼容性矩阵**

- 覆盖 AGP 8.4+ / 9.x。
- 覆盖 KSP 2.x。
- 覆盖 Android API 21-35。
- 与 ViewBinding / DataBinding 共存，不干扰原生生成流程。

**发布工程**

- Maven Central 发布 runtime、compiler-core、ksp-processor、gradle-plugin。
- Gradle Plugin Portal 发布插件。
- CI 自动化 staging -> release。
- 自动生成 ProGuard / R8 规则。

**性能和文档**

- 公开 benchmark 数据：inflate 时间、内存占用、编译时间开销。
- README 和文档站点说明测试设备、方法论和限制。
- 示例项目覆盖 generated inflate、fallback、DataBinding binding 子类和报告输出。

---

## Non-goals

- 替代 Jetpack Compose：LayoutX2C 服务于仍在使用 XML 布局的项目。
- 运行时动态 layout 加载：这是编译期工具，不做运行时 DSL。
- 100% 覆盖率：渐进式策略的核心就是接受 fallback 的存在。
- 默认把运行时 Theme 语义编译成常量：这类能力只能显式 opt-in。
- 完整替代 DataBinding runtime：LayoutX2C 只实现可静态保证的 `ViewDataBinding` 子集；复杂表达式、
  BindingAdapter、Observable / LiveData 自动订阅和 lifecycle 观察者语义仍由原生 DataBinding 负责。

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
- 常用 View：`TextView`、`Button`、`EditText`、`CheckBox`、`Switch`、`RadioButton`、
  `ToggleButton`、`ImageView`、`ProgressBar`、`SeekBar`、`RatingBar`、`Spinner`、
  `Space`、`View`。
- AndroidX / Material：`RecyclerView` 和 `ViewPager2` 作为容器创建；`ConstraintLayout`
  已有扩展安全子集支持；`CardView`、`MaterialCardView`、`Toolbar`、`MaterialToolbar`
  已支持基础属性。
- 自定义 View：用户可以通过 `@FastCustomViews` 在现有配置对象旁声明白名单 View 和 typed
  属性；KSP 会解析白名单 View 的父类链，复用已验证安全的父类属性语义。声明范围外的自定义
  View 或 typed 自定义属性继续保守 fallback。
- 特殊标签：`include`、`merge`、`ViewStub` 已进入编译期支持路径。
- DataBinding `<layout>` root 会透明解包到真实 View root，并生成继承 `ViewDataBinding` 的
  `{Name}X2CBinding`。

**已支持的关键属性**

- 通用：尺寸、padding、margin、gravity、visibility、id、background、alpha、
  contentDescription、tag、backgroundTint、foreground、importantForAccessibility、
  overScrollMode、scrollbars。
- Text-like：text、textColor、textSize、textStyle、hint、inputType、textAllCaps、
  singleLine、ellipsize、line count、fontFamily、lineSpacing、selectable / horizontal scroll。
- ImageView：src、scaleType、tint。
- Progress / selection widgets：checked、progress、tint、thumb、rating 等基础属性。
- Card / Toolbar：corner radius、elevation、stroke、title、subtitle、navigationIcon。
- ScrollView：fillViewport。
- RelativeLayout 常见规则。
- ConstraintLayout 安全子集：普通 start/end/top/bottom 约束、`0dp` match constraint、
  horizontal/vertical bias、Guideline、dimension ratio、percent、chain style / weight、
  gone margin。

**DataBinding 子集**

- `{Name}X2CBinding` 继承原生 DataBinding AP 生成的 `{Name}Binding`，并提供 `inflate()`、
  `bind()`、`root` 和按 `android:id` 生成的字段。
- `<data>` 变量会生成类型化 `@Bindable` 属性，setter 会维护 dirty flag、触发
  `notifyPropertyChanged()` 和 `requestRebind()`。
- `setVariable(variableId, value)`、`invalidateAll()`、`hasPendingBindings()`、`onFieldChange()` 和
  `executeBindings()` 已按 LayoutX2C 支持子集生成。
- 简单 `@{variable}` / `@{variable.property}` 表达式通过 `executePendingBindings()` 写回 View；
  白名单 `@={}` 绑定生成反向监听器。
- DataBinding include 字段保持原生 binding 类型；可生成的子 layout 会以 X2C binding 子类实例
  绑定，并传播 include 变量、contained binding、`lifecycleOwner` 和 pending binding 状态。
- 生成代码不直接依赖 DataBinding AP 阶段输出的 `BR` 类；运行时会解析宿主 `BR` ID，并保留本地
  fallback ID 以保证 KSP Kotlin 编译顺序稳定。

**Fallback 和增量**

- fallback 子树定位支持完整 child path，并对非法路径输出可诊断错误。
- fallback 子树通过原始 layout 完整 inflate 后按 child path 摘取目标节点；`LayoutInflater` 的属性解析依赖平台 `XmlBlock.Parser`，不能安全地从 seek 后的普通 `XmlPullParser` 做 partial inflate。
- Gradle 插件已声明 layout / values XML 输入，并传入 `layoutx2c.cacheDir`。
- KSP 侧已落地保守 digest cache：digest 未变时可恢复 per-layout factory、facade 和 report；digest
  已纳入 include / ViewStub layout 引用图，以及 layout 直接或递归引用的 string / dimen /
  color / drawable / mipmap / style 资源图。values qualifier 变体会一起纳入 digest，未解析资源会以稳定 marker 记录。
- Registry 仍是 aggregating 输出，但已通过内容 digest/cache 避免 Registry 内容未变时重复走完整生成路径。
- Gradle 插件已提供 `layoutX2CReport`，可输出项目级 JSON / HTML 汇总，并支持 CI fallback policy。

**1.0 仓库就绪状态**

- Runtime-facing API 已使用 `@PublicApi` / `@ExperimentalApi` 标注，1.0 迁移边界见
  `docs/MIGRATION_1_0.md`。
- 版本号已准备为 `1.0.0-rc.1`，Maven Central / Gradle Plugin Portal 发布配置和 release workflow
  已就绪；真实发布仍需要 tag、Maven Central credentials、signing key，以及可选 Gradle Plugin Portal secrets。
- `runtime` consumer ProGuard / R8 rules 已覆盖 generated factory 和 app-package generated registry。
- benchmark 方法、release 步骤和本地验证命令分别记录在 `docs/BENCHMARKS.md` 和 `docs/RELEASE.md`。

**当前限制**

- `fragment`、无法解析的 include、循环 include、超出递归深度限制的 include，以及无法等价生成的特殊语义仍会保守 fallback。
- 自定义 View 只支持显式白名单、可解析父类链上的安全平台属性，以及显式 typed 属性；未声明属性、无法转换的值和未声明 View 仍会 fallback。
- 精确资源图只用于 digest/cache invalidation，不把 style / theme 语义编译成常量；`?attr/` 和动态 theme 仍保守 fallback。
- 复杂 DataBinding 表达式、BindingAdapter、Observable / LiveData 自动订阅和 lifecycle 观察者语义仍交给原生 DataBinding。
- connected Android generated vs inflated 等价性测试依赖设备或 CI 环境运行。

---

## Next

**仓库侧 1.0 工作已全部完成**。下一步是执行外部发布。

### ✅ 已完成的 1.0 准备工作

以下任务已在仓库中完成：

1. **Android 等价性测试补齐** ✅
   - 已覆盖 include、nested include、include + merge、ViewStub、ConstraintLayout 安全子集
   - 已覆盖 fallback 子树和父级 LayoutParams 保留语义
   - 已对比 generated vs inflated 的 View tree、id、visibility、LayoutParams 和关键属性

2. **README 和 Demo 同步** ✅
   - README 已更新完整支持范围
   - Demo 已补齐所有 XML 样例和 benchmark 入口
   - 已明确 DataBinding binding 子类的支持边界

3. **精确资源引用图** ✅
   - 已实现 layout → style/dimen/color/string/drawable 精确依赖追踪
   - 修改被引用资源会触发相关 layout 重新生成
   - 修改无关资源不触发无关 factory 重生成

4. **API 标注和文档** ✅
   - `@PublicApi` / `@ExperimentalApi` 已完成标注
   - `docs/MIGRATION_1_0.md`、`docs/BENCHMARKS.md`、`docs/RELEASE.md` 已完成
   - 版本号已设为 `1.0.0-rc.1`

### 🚀 待执行：1.0 外部发布

**唯一剩余任务**：执行真实外部发布（需要维护者权限）

按照 `docs/RELEASE.md` 流程：

1. **配置 GitHub Secrets**（一次性配置）
   - `MAVEN_CENTRAL_USERNAME` / `MAVEN_CENTRAL_PASSWORD`
   - `SIGNING_IN_MEMORY_KEY` / `SIGNING_IN_MEMORY_KEY_PASSWORD`
   - `GRADLE_PUBLISH_KEY` / `GRADLE_PUBLISH_SECRET`（可选）

2. **打 tag 触发发布**
   ```bash
   git tag -a 1.0.0-rc.1 -m "Release 1.0.0-rc.1"
   git push origin 1.0.0-rc.1
   ```

3. **验证发布结果**
   - Maven Central: `io.github.donglua.layoutx2c:runtime:1.0.0-rc.1`
   - Gradle Plugin Portal: `io.github.donglua.layoutx2c`（如已配置）

4. **发布 GitHub Release**
   - 附上 changelog 和 migration guide 链接
   - 说明性能基准和兼容性矩阵

---

## Later

这些方向有价值，但不应阻塞短期可用性。

**Style / Theme 部分支持**

- 内联 `styles.xml` 中可静态解析的已知 style 属性。
- `?attr/` 默认继续 fallback。
- `?attr/` 编译期替换只作为显式 opt-in，并要求用户提供 theme 映射。

**ViewStub 延迟生成**

- 为 ViewStub 引用的 layout 生成独立 factory。
- 运行时 ViewStub inflate 可选择走生成 factory。
- 必须保持原生 ViewStub 替换、inflatedId 和 LayoutParams 语义。

**ConstraintLayout helpers**

- 逐项引入 Barrier、Group、Placeholder、Flow、Layer 等 helper 语义。
- 默认保守 fallback；每项能力必须有 generated vs inflated 等价性测试。
- 复杂 helper 不作为当前发布前置条件。

**Lint / IDE 辅助**

- Lint 检测可避免的 fallback，并给出可操作建议。
- IDE gutter icon、跳转生成代码和 quick-fix 作为 post-1.0 候选。
- 只有当 HTML/JSON 报告无法覆盖主要排查需求时再投入 IDE 插件。

**其他候选**

- Java 代码生成后端，用于兼容纯 Java 项目。
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

- Maven Central 发布 runtime、compiler-core、ksp-processor、gradle-plugin 的配置已就绪。
- Gradle Plugin Portal 发布插件的配置已就绪，缺少 portal secrets 时 workflow 会显式跳过。
- CI 自动化 staging -> release 已通过 tag workflow 表达。
- ProGuard / R8 consumer rules 已覆盖 generated factory 和 generated registry。

**性能和文档**

- benchmark 方法论已公开，真实数据应随 release notes 或文档站点按设备记录补充。
- README 和 docs 说明测试设备、方法论和限制。
- 示例项目覆盖 generated inflate、fallback、DataBinding binding 子类和报告输出。

---

## Non-goals

- 替代 Jetpack Compose：LayoutX2C 服务于仍在使用 XML 布局的项目。
- 运行时动态 layout 加载：这是编译期工具，不做运行时 DSL。
- 100% 覆盖率：渐进式策略的核心就是接受 fallback 的存在。
- 默认把运行时 Theme 语义编译成常量：这类能力只能显式 opt-in。
- 完整替代 DataBinding runtime：LayoutX2C 只实现可静态保证的 `ViewDataBinding` 子集；复杂表达式、
  BindingAdapter、Observable / LiveData 自动订阅和 lifecycle 观察者语义仍由原生 DataBinding 负责。

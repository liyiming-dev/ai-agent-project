# 图片异步收集改造 —— 分阶段落地计划

> 配套文档: [image-slot-async-collection.md](image-slot-async-collection.md)
> 目的: 把整套设计拆成 8 个**单独可合并、单独可观测、单独可回滚**的阶段,
> 每个阶段都有一个肉眼或日志可见的"验收信号",任何一阶段没过就不进入下一阶段。

## 拆分原则

1. **一次只动一种生成类型 / 一条链路**: HTML、MULTI_FILE、Vue 各自独立上线,出问题只影响一种类型。
2. **每阶段必须有可视化验收**: 浏览器看到、日志看到、DB 看到,三选其一,绝不允许"看测试通过就上"。
3. **二轮 injection 是兜底,不到最后一步不关**: 早期阶段允许"启发式 + 二轮 injection"双跑,以防新链路漏图。
4. **占位符兜底必须先落地**: 任何阶段保存的最终代码都不能含残留 `__IMG_SLOT_*__`。
5. **每阶段独立回滚**: revert 单个 PR 不影响其他阶段。

## 阶段总览

| 阶段 | 主题 | 影响范围 | 可视化验收 | 风险 |
| --- | --- | --- | --- | --- |
| 0 | 占位符兜底安全网 | 全链路保存口子 | 注入 fixture 占位符必被 scrub | 极低 |
| 1 | 底座 + HTML 端到端 | HTML | 浏览器打开 HTML 页,Hero 真实图 | 低 |
| 2 | MULTI_FILE 接入 | MULTI_FILE | HTML+CSS 都有真实图 | 低 |
| 3 | Vue 首轮 prompt 带槽位(不替换) | Vue 首轮 prompt | 流文本中出现 `__IMG_SLOT_*__` | 低 |
| 4 | Vue 落盘后 src/ 扫描替换 | Vue 工程目录 | Vue 项目预览有真实图 | 中 |
| 5 | 关闭 Vue 二轮 injection | Vue 生成耗时 | 生成时间从 2-5min 降到首轮时间 | 中 |
| 6 | LLM planner 退役 / 短等改增强 | HTML/MULTI 首 token | 首 token 等待 < 50ms | 低 |
| 7 | 多轮 slot 持久化 | DB / 启发式输入 | 第二轮 DB 中复用 slotId | 中 |
| 8 | 同步路径收口 + 指标完善 | 全链路 | 日志面板有 bindRate | 低 |

---

## 阶段 0 — 占位符兜底安全网

### 目标

在引入任何"会产出 `__IMG_SLOT_*__` 的代码"之前,**先把"保存前最后一道兜底"装好**:无论后续哪一阶段出现 bug 让占位符泄露,这道网都能把它替换为可用的兜底 URL,杜绝生产页面破图。

由于此阶段上线时还没有任何源会产出占位符,**对真实用户行为零变化**;但它给后续 1-7 阶段提供了一个统一的安全网,任何阶段出 bug 都不会让用户看到 `__IMG_SLOT_xxx__` 字符串。

### 改动范围

- 新增 `PlaceholderFallbackScrubber`(纯工具类):
  - `scrub(String content) -> String`: 用统一正则匹配 `__IMG_SLOT_([a-z0-9_]+)__`,替换为 picsum 兜底 URL(可基于 slotId 哈希取稳定图,保证多文件引用同一 slotId 时是同一张图)。
  - `scrubFile(File file)`: 读取 → scrub → 回写,对二进制/超大文件跳过。
  - `scrubDirectory(File root, Set<String> extensions)`: 递归扫描指定后缀,逐个 scrub。
  - 命中数返回值,便于上层打 `placeholderLeakCount` 日志。
- 新增 `VueProjectPathResolver`(从已有 Vue 工具链中提取/复用 `appId → 工程根目录` 的逻辑;Phase 4 会再用一次,这里先抽出来)。
- 接入点:
  - **HTML / MULTI_FILE**: 在 `processCodeStreamWithInjection.doOnComplete` 中,`pickFinalCode` 之后、`CodeParserExecutor.executeParser` 之前,对最终代码字符串调用 `scrub`。
  - **Vue**: 在 `processVueStreamWithInjection` 末尾,首轮+二轮流全部完成后,通过 `VueProjectPathResolver` 拿到工程根目录,对 `<root>/src/` 递归 `scrubDirectory`(后缀 `.vue/.js/.ts/.css/.html`)。
  - **同步路径** `generateAndSaveCode`: 在保存前对结果模型的字符串字段调用 `scrub`(虽然同步路径暂时没占位符,先把口子留好)。
- 日志:每次 scrub 调用打印 `placeholderLeakCount=N`,正常情况都应是 0。

### 不动什么

- 不动任何 prompt;模型不会产出占位符,scrubber 不会命中。
- 不动 `ImageCollectionOrchestrator`、不动启发式 planner(还没引入)。
- 不动二轮 injection。

### 验收步骤

由于线上还没有任何源会产出 `__IMG_SLOT_*__`,真实流量看不到差异,验收靠 **fixture 注入**:

1. **单测**: 给 scrubber 喂以下输入,断言每一种都被替换:
   - `<img src="__IMG_SLOT_hero_main_1__">`
   - `<img src='__IMG_SLOT_hero_main_1__'>`
   - `:src="__IMG_SLOT_hero_main_1__"`
   - `background-image: url("__IMG_SLOT_hero_main_1__")` / `url('...')` / `url(...)`
   - 同一 slotId 在多处出现,scrub 后是同一张兜底图。
   - 替换后字符串不再含 `__IMG_SLOT_`。
2. **HTML 集成测试**: 用一个 mock 的 `AiCodeGeneratorService` 让流式输出强制返回一段含 `__IMG_SLOT_xxx__` 的 HTML,跑完整链路,断言保存的文件中无残留。
3. **Vue 集成测试**: 先在工程目录下手工放一个含占位符的 `.vue` 文件,触发一次 Vue 流(可以是空 prompt 或 mock 流,关键是触发末尾 `doOnComplete`),断言文件中无残留。
4. **生产灰度**: 上线后看日志 `placeholderLeakCount`,**应当持续为 0**(没有源会产出占位符)。如果出现非 0,说明有未知源在产生占位符,要立刻排查。

### 单测清单

- `PlaceholderFallbackScrubberTest`: 8 种引号/语法变体替换。
- `PlaceholderFallbackScrubberTest`: 同 slotId 多处稳定为同一兜底图。
- `PlaceholderFallbackScrubberTest`: 大文件 / 二进制跳过。
- `VueProjectPathResolverTest`: 给定 appId 返回正确工程根目录。
- `AiCodeGeneratorFacadeTest`: HTML/MULTI/Vue 三条链路都注入 scrubber 调用(用 mock 验证调用)。

### 回滚

revert 此 PR;由于本阶段对真实流量零作用,回滚也零影响。

### 退出标准

- 所有单测、集成测试通过。
- 生产灰度 1-2 天,`placeholderLeakCount=0` 始终成立。
- 这是后续阶段的硬前置:阶段 1 起任何阶段都默认依赖这道网。

---

## 阶段 1 — 底座 + HTML 端到端打通

### 目标

让一次 HTML 生成请求,**首轮代码包含 `__IMG_SLOT_*__` 占位符,保存后被替换为真实 URL,浏览器打开能看到真实图片**。

### 改动范围

- 新增 `ImageSlotPlan` / `ImageSlot` / `SlotPlanSource` 模型类。
- `ImageResource` 增加 `slotId` 字段。
- 新增 `HeuristicSlotPlanner.buildHeuristicPlan(ctx)`(只支持 HTML 首轮,先用最简单分桶: hero_main_1 + feature_1)。
- 新增 `ImageSlotBinder.bind(code, plan, images)`,用统一正则覆盖:
  - `src="…"` / `src='…'` / `src={`…`}` / `:src="…"`
  - `url(…)` / `url('…')` / `url("…")`
  - 命中真实素材的 slotId 替换为真实 URL,补全 alt;
  - 没命中真实素材的 slotId **不在这里兜底**,直接交给阶段 0 已经装好的 `PlaceholderFallbackScrubber` 在保存前那一道网处理(职责单一,binder 不做兜底,scrubber 不做绑定)。
- `ImageCollectionOrchestrator` 新增 `collectImagesByPlanAsync(plan)`,各 tool 返回的 `ImageResource` 带回 `slotId`。
- `AiCodeGeneratorFacade.generateAndSaveCodeStream` 的 HTML 分支:
  - 首轮 prompt 加 `buildPromptWithImageSlots(userMessage, plan)`。
  - `doOnComplete` 中,**先 binder 替换 → 再走阶段 0 scrubber → 最后保存**(scrubber 已在阶段 0 接好,这里不需要新增挂钩)。
  - **二轮 injection 保留**(此阶段不动)。

### 不动什么

- MULTI_FILE 走老路径(`enhancePromptWithImages` + 二轮 injection)。
- Vue 走老路径。
- 同步路径 `generateAndSaveCode` 走老路径。
- 占位符规则 prompt 写得"克制",不强求模型;依赖正则兜底保障最终页面无泄露。

### 验收步骤

1. 启动应用,创建 HTML 类型应用,prompt = `做一个咖啡店首页`。
2. 流式输出文本中至少出现一次 `__IMG_SLOT_hero_main_1__`(看前端日志或浏览器网络面板)。
3. 保存目录下的 `index.html`:
   - 不含 `__IMG_SLOT_*__`(grep 检查)。
   - `<img src="…">` 中至少一处是真实 http(s) URL。
4. 浏览器打开 `index.html`,Hero 区域看到一张真实图(可以是兜底 picsum,只要不是 broken image)。
5. 后端日志可见 `bindRate=…` 和 `placeholderLeakCount=0`。

### 单测

- `ImageSlotBinder` 三种引号变体替换。
- `ImageSlotBinder` 残留兜底替换。
- `HeuristicSlotPlanner` 给定 prompt 返回非空 plan。

### 回滚

revert 此 PR;HTML 重新走老链路。

### 退出标准

- 连续 5 次手工生成,bindRate ≥ 50%,placeholderLeakCount 一直为 0。

---

## 阶段 2 — MULTI_FILE 接入

### 目标

MULTI_FILE 类型的 `index.html` + `style.css` 都按槽位机制生成 + 替换。

### 改动范围

- `AiCodeGeneratorFacade.generateAndSaveCodeStream` 的 MULTI_FILE 分支:
  - 复用阶段 1 的启发式 plan + prompt 增强 + binder。
  - binder 对 `MultiFileCodeResult.htmlCode` / `cssCode` / `jsCode` 三块分别替换。
- `HeuristicSlotPlanner` 增加"是否包含 CSS"的判定(对 MULTI_FILE 默认强制覆盖 CSS 替换分支)。
- 移除 MULTI_FILE 链路里的 `enhancePromptWithImages` 调用。

### 不动什么

- HTML 不动。Vue 不动。

### 验收步骤

1. 创建 MULTI_FILE 应用,prompt = `做一个产品落地页,带 hero 和 3 张特性卡片`。
2. 保存目录下的 `index.html`、`style.css`:
   - 都不含 `__IMG_SLOT_*__`。
   - HTML 中至少有 1 处真实 `src`,CSS 中至少有 1 处真实 `url(...)`。
3. 浏览器打开 index.html,看到 Hero 大图 + 卡片缩略图(允许部分是兜底)。
4. 后端日志 bindRate ≥ 50%,placeholderLeakCount=0。

### 回滚

revert 此 PR;MULTI_FILE 重新走老链路。

### 退出标准

- 同阶段 1。

---

## 阶段 3 — Vue 首轮 prompt 带槽位(暂不做工程目录扫描)

### 目标

让 Vue 首轮模型在生成时**已经知道有图片位**,首轮代码中出现 `__IMG_SLOT_*__`;
此阶段**不替换工程目录**,只观察模型遵从率,确保即使不替换页面也不崩。

### 改动范围

- `HeuristicSlotPlanner` 增加 Vue 分支:默认 1 个 `hero_main_1`,可选 `feature_1`。
- `AiCodeGeneratorFacade.generateAndSaveCodeStream` 的 VUE_PROJECT 分支:
  - 首轮 prompt 加 ≤ 800 字的槽位提示。
  - **保留二轮 injection**(此阶段二轮仍然跑,确保兜底)。
  - 不做工程目录扫描替换。
- 二轮 injection 走老路径不动,但要在二轮 prompt 末尾追加一句"如果代码中存在 `__IMG_SLOT_*__`,请用对应分组的真实素材 URL 替换"。

### 不动什么

- Vue 工程目录文件不被后端扫描。
- HTML/MULTI_FILE 不动。

### 验收步骤

1. 创建 Vue 应用,prompt = `做一个咖啡店官网`。
2. 抓首轮流式文本(JsonMessageStreamHandler 日志或前端 console),搜索 `__IMG_SLOT_`,至少出现 1 次。
3. 二轮 injection 完成后,工程目录下 `.vue` 文件大概率不含 `__IMG_SLOT_*__`(因为模型在二轮替换了)。
4. 浏览器预览 Vue 项目,Hero 显示真实图。
5. 日志统计:首轮代码片段中 `__IMG_SLOT_*__` 出现次数 / 槽位数 = "Vue 模型遵从率",目标 ≥ 50%。

### 回滚

revert 此 PR;Vue 首轮恢复裸 userMessage。

### 退出标准

- 连续 5 次 Vue 生成,模型遵从率 ≥ 50%。
- 二轮 injection 后无残留占位符泄露(grep `.vue` 文件)。

---

## 阶段 4 — Vue 落盘后 src/ 扫描替换

### 目标

Vue 工程目录下的 `__IMG_SLOT_*__` 在**首轮流结束后由后端确定性替换**为真实 URL;
为阶段 5 关闭二轮 injection 做准备。

### 改动范围

- `VueProjectPathResolver` 在阶段 0 已抽出,本阶段直接复用。
- 新增 `VueSlotBinder`:
  - 递归扫 `<root>/src/**/*.{vue,js,ts,css}`。
  - 复用阶段 1 的 `ImageSlotBinder` 同一套正则,把命中真实素材的 slotId 替换为真实 URL。
  - **不做兜底**,残留 `__IMG_SLOT_*__` 仍交给阶段 0 的 `PlaceholderFallbackScrubber` 在 binder 之后兜底。
  - 不抛异常,失败只写日志。
- `processVueStreamWithInjection` 修改:
  - 把 `Mono.fromCallable(waitImages)` 拼接到首轮流末尾。
  - 拿到 images 后**先 `VueSlotBinder.bind`(命中替换)→ 再 scrubber(残留兜底)**;两步顺序绝不能反。
  - **此阶段二轮 injection 仍保留**,但只在 `bindRate < 50%` 时触发(灰度阶段)。

### 不动什么

- HTML/MULTI_FILE 不动。
- 二轮 injection 默认仍开启(下个阶段才关)。
- 不调模型、不进工具链。

### 验收步骤

1. 创建 Vue 应用,prompt = `做一个咖啡店官网`。
2. 工具调用流结束后,在工程目录运行 `grep -r "__IMG_SLOT_" src/`,**应当 0 命中**。
3. 浏览器预览 Vue 项目,Hero 显示真实图。
4. 日志可见 `vueBindRate=…`、`vueScannedFiles=…`、`vueHitFiles=…`。
5. 即使图片收集 future 超时,工程目录也不能残留 `__IMG_SLOT_*__`(必被兜底)。

### 回滚

revert 此 PR;Vue 又回到"靠二轮 injection 补图"。

### 退出标准

- 连续 5 次 Vue 生成,vueBindRate ≥ 70%,grep 残留 0 命中。

---

## 阶段 5 — 关闭 Vue 二轮 injection

### 目标

Vue 生成时长从 2-5min 降到首轮时间(只跑一次工具循环),验证阶段 4 的扫描替换稳定后再永久关闭。

### 改动范围

- 新增配置 `enableImageInjectionSecondRound`,默认 `false`。
- `processVueStreamWithInjection`:
  - 默认不再触发二轮 injection 流。
  - 通过开关可临时打开作为兜底。
- 监控 `vueBindRate` < 50% 时打 warn 日志(暂不自动重试)。

### 不动什么

- HTML/MULTI_FILE 不动。

### 验收步骤

1. 同 prompt,在阶段 4 完成后测一遍生成耗时(假设 X 分钟)。
2. 上线本阶段后,同 prompt 重测,耗时应 ≤ X / 2(典型 2-5min → 1-2min)。
3. 浏览器预览页面,Hero 与卡片仍能显示真实图(允许少量兜底)。
4. 日志:`enableImageInjectionSecondRound=false` 且 vueBindRate ≥ 70%。

### 回滚

把配置改回 `true` 即可恢复二轮 injection,不需要 revert 代码。

### 退出标准

- 连续 5 次 Vue 生成,耗时下降明显,绑定率 ≥ 70%,无用户报"图片消失"。

---

## 阶段 6 — LLM planner 退役 / 改增量增强

### 目标

HTML/MULTI_FILE 链路里的 `imageCollectionPlanService.planImageCollection`(reasoner 模型)从默认链路移除,**首 token 等待没有 LLM 规划开销**。

### 改动范围

- 新增配置 `enableLlmSlotPlanEnhance`,默认 `false`。
- `ImageCollectionOrchestrator`:
  - 默认链路:启发式 plan → 直接派任务。
  - 配置打开时:启发式 plan + LLM(改用小模型/haiku)增量增强,1.5s 超时,**仅追加/细化 query,不覆盖启发式 slotId**。
- 删除/废弃 `enhancePromptWithImages` 旧拼接路径(同步路径在阶段 8 收口)。

### 不动什么

- 启发式 planner 行为不变。
- Vue 链路不动(本来就不调 LLM planner)。

### 验收步骤

1. HTML 应用首 token 等待:阶段 1 上线时若有 LLM 等待,本阶段后应消失,前后对比日志中 `firstTokenLatencyMs` 减少 ≥ 1500ms。
2. 启发式 plan 仍正常派任务,绑定率维持阶段 1 水平。
3. 配置开关切到 `true` 测试增量增强:LLM 返回的 task 必须挂在已有 slotId 上,日志中 slotId 集合不变,只是 query 更精准。

### 回滚

把配置改回 `true` 即可恢复 reasoner planner。

### 退出标准

- HTML 首 token 时间下降明确;绑定率不下降。

---

## 阶段 7 — 多轮 slot 持久化

### 目标

第二轮"加联系页"等增量需求,**复用第一轮的 slotId**,减少重复搜图,新增 slot 仅围绕新增页面。

### 改动范围

- 新增 `app_image_slot` 表(字段见设计文档第八节),配套迁移脚本放到 `sql/` 目录。
- 新增 `AppImageSlotMapper` / `AppImageSlotService`。
- `HeuristicSlotPlanner.buildHeuristicPlan(ctx)`:
  - 先读 `app_image_slot` 表,把已有 slot 加入 plan。
  - 仅为本轮新增页面创建新 slotId。
- 绑定成功后回写 `last_url` + `last_used_at`。
- 第二轮派任务前,优先用 `last_url` 直接命中,不再调搜图工具。

### 不动什么

- 单轮(初次)请求行为不变。

### 验收步骤

1. 创建 Vue 应用,prompt = `做一个咖啡店官网`,生成完毕。
2. 查 DB:`SELECT * FROM app_image_slot WHERE app_id=?`,应有 `hero_main_1`、`feature_1` 等记录,且 `last_url` 非空。
3. 同应用下第二轮 prompt = `加一个联系我们页面`。
4. 生成完毕后查 DB:hero/feature 的 `last_used_at` 不更新或更新为复用,新增 `contact_hero_1` 记录。
5. 日志:第二轮搜图调用次数显著少于第一轮(因为命中 last_url 复用)。
6. 浏览器预览:首页 Hero 用的是与第一轮相同的图(URL 一致)。

### 回滚

- 配置开关 `enableSlotPersistence`,默认开启,出问题改 `false` 关闭读写,行为退化为阶段 4/5。
- DB 表保留,不需要回滚迁移。

### 退出标准

- 第二轮"加页面"场景下,旧页面图片 URL 不变;新页面有自己的新 slotId。

---

## 阶段 8 — 同步路径收口 + 指标完善

### 目标

`generateAndSaveCode`(非流式)接入槽位机制,消除两套图片处理路径;
完善 `bindRate` / `placeholderLeakCount` / `slotPlanSize` 指标埋点,落到日志或现有监控面板。

### 改动范围

- `generateAndSaveCode` 改用启发式 plan + 同步等待真实素材 + binder 替换,移除 `enhancePromptWithImages`。
- 在 facade 各分支统一打点 `bindRate` / `placeholderLeakCount` / `slotPlanSize` / `firstTokenLatencyMs` / `vueBindRate`。
- 指标日志关键字标准化,便于 grep 或后续接入监控。

### 验收步骤

1. 同步接口 `generateAndSaveCode` 的 HTML/MULTI_FILE 路径走通,生成的代码同样无残留占位符。
2. 日志面板 / kibana / grep 能看到所有阶段的关键指标,字段命名统一。
3. 一次失败的图片收集场景下,`placeholderLeakCount=0` 仍然成立。

### 回滚

revert 此 PR;指标埋点是增量的,回滚不影响业务。

### 退出标准

- 同步路径有人手测一遍,所有指标可见。

---

## 跨阶段约束

- 每个阶段独立提交一个 PR,描述里必须列出"验收步骤"和"通过标准";没过验收不进入下一阶段。
- `placeholderLeakCount` 是**所有阶段的硬约束**:任何一阶段上线后,生产保存的最终代码不允许残留 `__IMG_SLOT_*__`。
- 灰度期建议先在内部账号灰度跑 1-2 天,再放给真实用户。
- 出现绑定率断崖式下跌(< 30%),立即回滚到上一阶段,不要"先继续后修"。

## 推荐排期

| 阶段 | 估时(开发 + 验收) |
| --- | --- |
| 0 | 0.5-1 天 |
| 1 | 1-2 天 |
| 2 | 0.5 天 |
| 3 | 0.5-1 天 |
| 4 | 1 天(VueProjectPathResolver 已在阶段 0 完成) |
| 5 | 0.5 天(主要等观察) |
| 6 | 0.5 天 |
| 7 | 1-2 天 |
| 8 | 0.5-1 天 |

合计 5.5-9.5 天人力,可分两周排期。

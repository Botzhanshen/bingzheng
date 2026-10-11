# 《病证》模组 · 开发进度与决策记录

> **本文件是唯一事实来源。** 任何人（包括 AI）在中断后重新接手，先读这里。
> 每次做出决定或完成一个阶段，都要回来更新它。

最后更新：**内核切片定稿**（v0.1.0-core-slice）。二轮实机复验 3/3 通过；两条 GUI 美术问题与一条食用组件架构备忘已记入「十、后续迭代 TODO」，按用户要求暂不修改。

---

## 一、已锁定的版本矩阵（用户确认，不得擅自更改）

| 组件 | 版本 |
|---|---|
| Gradle | 9.7.1 |
| Fabric Loom | 1.10.5 |
| Minecraft | 1.20.1 |
| Fabric Loader | 0.18.4 |
| Yarn mappings | 1.20.1+build.10 |
| Fabric API | 0.92.12+1.20.1 |
| 编译目标 | `options.release = 17`，class 版本适配 Java 17 |
| 验收启动器 | PCL2 2.12.1（`D:\PCL 正式版 2.12.1\`），仅用于**成品 jar 的真机验收** |

**为什么 Loom 是 1.10.5 而不是官方的 1.18.4：** Loom 1.18.4 要求 JVM ≥ 25，本机只有 JDK 17。
实测各版本最低 JVM：1.7.4/1.8.13/1.9.2 → 8；**1.10.5 → 17**；1.11.8–1.17.21 → 21；1.18.4 → 25。
1.10.5 是 Java 17 上能用的最新稳定版。

**命名空间：`bingzhengmod`**（不是 `bingzheng`）。所有方块、物品、GUI、状态效果、Attachment 一律走它。

---

## 二、用户定下的 8 条硬约束（第三方模组兼容）

1. **禁止覆盖/改写原版玩家的数据域、原版状态效果、原版饥饿/生命/体温属性。**
   病证、营卫、情志、脏腑、体耗神耗全部用模组自定义 NBT / **Attachment 附属数据**存储。
2. 自定义状态效果一律用 `bingzhengmod:` 命名空间，绝不复用原版 ID，不改原版效果属性。
3. **事件监听全部用后置追加模式**：只做追加计算，不取消、不篡改其它模组的伤害/更新/tick。
   非必要不调 `event.cancel()`；若必须拦截，写详细注释。
4. 方块、物品、GUI 全部带独立命名空间 `bingzhengmod:`，**绝不注册无命名空间全局 ID**。
5. Mixin 优先 `@Inject`，少用 `@Overwrite`/`@Redirect`；能用事件/API 就不改写原版。
   迫不得已覆写必须写注释说明原因。
6. **修合台 5×5 配方系统独立**，自成一套模组内部配方注册表，不碰原版 Vanilla 配方体系。
7. `fabric.mod.json` 元信息完善：合理的 `depends`（含 fabric-api）、版本、联系人；加 `suggests` 段，不强制绑定任何第三方 mod。
8. **不把数值硬编码进代码**，便于后续开放配置文件；垂直切片阶段允许硬编码桂枝汤，
   但必须标 `//TODO 后续迁移配置文件`。

---

## 三、两个文档缺口的处理（用户已裁定）

### 1. 「62 味核心中药材」矛盾
文档口头约定 62 味，但从附录二 111 个方剂反推出 **331 个药材词条**（含炮制变体）。
**裁定：分两层，炮制变体不算新增基础药材。**
- 【原生基础药材 62 味】：自然界采集/生成/种植获得的未炮制原材料
- 【炮制药材产物】：由 62 味原生药材经修合台/药锅炮制转换而来，不计入 62 基数

**当前做法：** 建占位文件 `assets/bingzhengmod/data/herbs_temp.json`，把全部药材录入，
字段含 `is_raw:boolean` 与 `base_herb_id`（炮制产物指向其原生药材 id）。
**先不校验、不阻塞开发**，完整 62 味官方清单后续由人补全。

### 2. 状态效果「表和」
文档原文对桂枝汤的功效描述是「解肌发表、调和营卫」；「表和」一词**文档全文不存在**。
**裁定：**
- 标识符 `bingzhengmod:harmonize_exterior`
- 中文显示名 **表气调和**
- 行为：缓慢恢复**模组自定义的营卫值**；**绝不改原版饥饿/生命**；作为测试闭环用，数值后续可调。

---

## 四、已确认的技术结论（都经实测，不是推测）

- **Attachment API 支持持久化。** `javap` 实测 Fabric API 0.92.12+1.20.1 自带的
  `fabric-data-attachment-api-v1-1.0.2` 有 `AttachmentRegistry.createPersistent(Identifier, Codec)`
  与 `AttachmentType.isPersistent()/persistenceCodec()`。
  → 营卫值存持久化 Attachment，**完全不碰原版 NBT**，约束 1 可 100% 兑现。
- **垂直切片可以做到零 Mixin。** 状态效果走 `StatusEffect` 注册、玩家数据走 Attachment、
  GUI 走 `fabric-screen-handler-api-v1`、配方走自有注册表、物品方块走注册表 API。
  → 约束 5 天然满足。
- **配方判定用「无序多重集」。** 文档表格只给每味药的「格数」+「每格数量」，没有坐标；
  且多数方剂合计恰好 25 格（麻黄汤 10+6+5+4=25），位置规则在数学上无法成立。
  判定 = 每种药材占几格 × 每格几枚。桂枝汤 = 桂枝6/芍药6/生姜5/大枣5/炙甘草3，合计 25 格。
- **炮制走修合台自有配方注册表**（如 甘草×1 + 蜂蜜×1 → 炙甘草×1），不碰原版配方，符合约束 6。

### 垂直切片药材范围（用户选定）
- 原生：桂枝、芍药、生姜、大枣、甘草、蜂蜜
- 炮制：**炙甘草**（甘草+蜂蜜）、**制附子**（生附子经浸泡/辅料煮制/晾干三步简化）
- 有毒演示：**生附子 → 制附子**，验证「生用有毒、炮制去毒」链路

---

## 五、开发环境踩坑记录（沙箱相关，别重复踩）

| 现象 | 原因 | 对策 |
|---|---|---|
| Gradle 完全起不来：`CreatePipe error=5 拒绝访问` | 沙箱禁止子进程创建匿名管道，Gradle 必须 fork 守护进程并读其 greeting | 用**一次** `danger-full-access` 放宽把守护进程拉起；之后构建复用守护进程（走 localhost socket），不再需要批准 |
| 守护进程闲置 3 小时会退出 | Gradle 默认行为 | 届时下次构建需重新申请 `danger-full-access`；备用绕行：`inheritIO`（实测允许） |
| PowerShell / curl 一律 HTTPS 失败 `SEC_E_NO_CREDENTIALS` | 沙箱下 Windows 原生 TLS(Schannel) 不可用 | **用 Java 下载**（JSSE 正常）：`.tools/Download.java`、`.tools/Probe.java`、`.tools/LoomCheck.java` |
| Gradle wrapper 下载 10 秒超时 | wrapper 默认 `networkTimeout=10000`，且 services.gradle.org 会 307 跳 github.com 后超时 | 超时调到 120000 + retries=3；`distributionUrl` 指向**腾讯云镜像** `mirrors.cloud.tencent.com/gradle/` |
| PowerShell 中文正则全失效 | PS 5.1 把**无 BOM 的 UTF-8 脚本**按 GBK 解析 | 中文文本解析改用 **TypeScript + `node:fs`**，不要用 PS 脚本 |
| 读文档只拿到一半 | `read` 工具对大文件有截断 | 大文件用 `node:fs.readFileSync` |
| 日志中文显示乱码 | JVM 写入的是 **GBK**（系统代码页），我按 UTF-8 读所以花屏 | **读日志要用 GBK 解码**；**不要**给 runClient 加 `-Dfile.encoding=UTF-8`，那会让用户自己的控制台花屏 |
| 看不见游戏进程、无法枚举窗口 | 沙箱 PowerShell 跑在非交互会话，看不到其它安全上下文的进程 | 用 `run/logs/latest.log` 作为游戏状态的事实来源；停止游戏靠中断 Gradle 客户端 |
| `runClient` 永远停着不退出 | **全新 `run/` 目录首次启动会先弹 `AccessibilityOnboardingScreen`（辅助功能引导页）等玩家点「继续」**，无人值守永远等不到 | ① `run/options.txt` 写 `onboardAccessibility:false` 直接跳过；② 冒烟钩子把引导页也视为「已就绪」 |
| `loom { runs { client { vmArg ... } } }` 不生效 | Loom 的 run 任务在配置期之后才最终确定 jvmArgs | 改用 `tasks.configureEach { if (name == "runClient") doFirst { jvmArgs(...) } }` |
| **GUI 背景贴图被异常放大** | `DrawContext.drawTexture(tex,x,y,u,v,w,h)` 这个 7 参重载**硬编码按 256×256 采样**；贴图实际是 176×222 时，只取左上 68.75%×86.7% 再拉伸铺满 | **GUI 贴图一律生成 256×256**（内容占左上 176×222），符合 MC 惯例 |
| 客户端类编译时报「程序包 net.minecraft.client.* 不存在」 | `splitEnvironmentSourceSets()` 下客户端代码**必须**放 `src/client/java`，放 `src/main/java` 拿不到客户端专用 MC 类 | 客户端代码一律进 `src/client/java` |
| 编译报「需要 class、interface、enum 或 record」+ 大量「非法字符」 | Javadoc 里写了 `{@code data/*/recipes}`，其中的 **`*/` 提前闭合了注释块** | 注释里禁止出现 `*/` 序列 |
| PowerShell 里 `\| Select-String` 接原生命令报 Access is denied | PowerShell 管道对原生命令会用匿名管道 | 原生命令输出**重定向到文件**再用 `node:fs` 读，不要接 PowerShell 管道 |

---

## 六、进度看板

### 已完成
- [x] 通读设定书 v1.04（75454 字 / 6310 行），产出纯文本 `.docx_extract/doc2.txt` 与章节索引
- [x] 反推药材清单（111 方剂 → 331 词条）
- [x] 工程骨架：`settings.gradle` / `build.gradle` / `gradle.properties` / wrapper / `fabric.mod.json` / 主类 + 客户端类
- [x] **`gradlew build` 通过**，产出 `build/libs/bingzhengmod-0.1.0.jar`
- [x] **`gradlew runClient` 通关**：Minecraft 1.20.1 + Fabric Loader 0.18.4 + fabric-api 0.92.12+1.20.1
      正常启动到主菜单，`病证` 的 main/client 两个入口都执行，无 crash-report
- [x] 旧 IDEA 模板 `src/Main.java` 移至 `.tools/legacy/`（未删除）

- [x] **开发期冒烟钩子**：`gradlew runClient -PbzSmoketest=15` → 到主菜单 15 秒后自动退出 → `BUILD SUCCESSFUL` / exit 0。
      **已验证无人值守可跑**（全程约 30 秒）。也支持环境变量 `BINGZHENGMOD_SMOKETEST`。
      这是以后每轮验证的标准手段。

### 已完成（垂直切片 · 阶段 1–2）
- [x] **阶段 1：`herbs_temp.json` 占位数据**
      路径 `src/main/resources/assets/bingzhengmod/data/herbs_temp.json`
      从 109 个方剂行反抽出 **320 条**（285 原生 / 35 炮制），字段 `id / name / is_raw / base_herb_id / process / formula_count`。
      英文 id 由 `pinyin-pro` 生成（多音字已手工校正：白术 bai_zhu、厚朴 hou_po、黄柏 huang_bo、大黄 dai_huang、川芎 chuan_xiong…）。
      **内置引用完整性自检**：0 悬空 base、0 重复 id；仅 3 条解析不出基名（炮穿山甲 / 酒萸肉 / 飞金），已记入 `issues` 字段，不阻塞。
- [x] **阶段 2：物品注册**（10 件）
      原生 7 味：桂枝 芍药 生姜 大枣 甘草 蜂蜜 **生附子**
      炮制 2 味：炙甘草（←甘草）、制附子（←生附子）
      成药 1 件：桂枝汤药剂
      另注册 2 个模组自定义状态效果：`xinmai_bizu`（心脉痹阻，生附子中毒标记的表现）、`harmonize_exterior`（表气调和，阶段 5 接营卫值）
      占位美术：程序化生成的 16×16 物品贴图、18×18 效果图标、物品模型、中英 lang 文件
      **自检通过**：毒性标记 生附子=有毒 / 制附子=无毒；全部 ID 均在 `bingzhengmod:` 下

### 已完成（垂直切片 · 阶段 3–5）

- [x] **阶段 3：修合台方块 + 5×5 GUI**
      方块 `bingzhengmod:xiuhe_table`（+ 同名 BlockItem），右键打开界面。
      `XiuheTableScreenHandler`：25 输入格 + 1 产物格 + 36 玩家格；关闭界面时药材**原样退还**玩家（与原版工作台同构，因此不需要方块实体）。
      客户端 `XiuheTableScreen` + 程序化生成的 176×222 GUI 占位贴图（槽位坐标与代码常量严格对应）。
      **模型/贴图零加载错误**已验证。
- [x] **阶段 4：独立配方引擎 + 自检**
      `XiuheRecipe / XiuheIngredient / XiuheMatch / XiuheRecipes`，**完全独立于原版 RecipeManager**，不注册任何 recipe_type、不读数据包。
      判定＝无序多重集：每味药的「格数」与「每格数量」都要对，且不允许任何多余药材。
      3 条配方：桂枝汤（6/6/5/5/3＝25 格）、甘草+蜂蜜→炙甘草、生附子→制附子。
      **启动自检 7/7 通过**（3 正例 + 4 反例，含用户指定的 3 条反例）。
- [x] **阶段 5：表气调和 + 营卫值持久化闭环**
      `bingzhengmod:yingwei` —— 用 `AttachmentRegistry.createPersistent(id, Codec.INT)` 注册，**不占原版任何字段**，随存档持久化（区间 0–100，默认 50）。
      `HarmonizeExteriorEffect`：每 20 tick 恢复 1 点营卫（服务端裁决），每涨 10 点给一次 action bar 提示。
      `/bingzhengmod yingwei` 查询、`/bingzhengmod yingwei set <0-100>` 设置，供人工验收。

### 已完成的全部 ID 清单（全部 `bingzhengmod:` 命名空间）

| 类别 | ID |
|---|---|
| 方块 | `xiuhe_table` |
| 原生药材 | `gui_zhi` `shao_yao` `sheng_jiang` `da_zao` `gan_cao` `feng_mi` `sheng_fu_zi`(有毒) |
| 炮制药材 | `zhi_gan_cao`(←gan_cao) `zhi_fu_zi`(←sheng_fu_zi) |
| 成药 | `gui_zhi_tang` |
| 状态效果 | `xinmai_bizu`(心脉痹阻) `harmonize_exterior`(表气调和) |
| 界面 | `xiuhe_table` (ScreenHandlerType) |
| 附属数据 | `yingwei` |
| 物品栏 | `herbs` |
| 命令 | `/bingzhengmod yingwei` |

---

## 七、待人工验收清单（PCL2 实机）

自动化能验的都验了；下面这条**需要玩家交互**的链路必须人工跑一次。
（沙箱里的 AI 看不到游戏窗口、无法操作鼠标，所以这一段只能由人完成。）

**准备**
1. PCL2 新建 1.20.1 实例，Fabric Loader 选 **0.18.4**，装 **Fabric API 0.92.12+1.20.1**
2. 把 `build/libs/bingzhengmod-0.1.0.jar` 丢进该实例 `mods/` 目录
3. 启动游戏，进入任意世界，开创造模式

**验收步骤**
1. 打开创造物品栏，应能看到独立的 **「病证·药材」** 标签页，内含 10 件物品 + 修合台
2. 取出**修合台**放地上，右键 → 应打开 5×5 界面（灰色槽位 + 右侧产物格 + 箭头）
3. **正例**：在 25 格中依次放入 桂枝×6、芍药×6、生姜×5、大枣×5、炙甘草×3（每格 1 个）→ 产物格应出现**桂枝汤**，取出后输入格清空
4. **反例**：随意去掉几格 → 产物格应变空
5. **炮制**：放 甘草×1 + 蜂蜜×1 → 产物格出现**炙甘草**
6. **炮制**：放 生附子×1 → 产物格出现**制附子**
7. **毒性标记**：手持**生附子**右键 → 应出现「心脉痹阻」状态图标，并在聊天栏提示；**制附子**右键应无反应
8. 输入 `/bingzhengmod yingwei` → 应显示「营卫值：50 / 100」
9. 喝下**桂枝汤** → 获得「表气调和」状态 → 每约 10 点弹一次 action bar → 数值涨到 100
10. **持久化**：退出世界重进，`/bingzhengmod yingwei` 应仍是退出前的数值（**这一步是验证 Attachment 真正落盘的关键**）

若第 10 步数值归零，说明附件未落盘，需要改用在服务端 tick 时显式标记玩家数据已改动的方式。

---

## 八、实机验收报告（用户 PCL2 实测）与修复

**验收结果（2026-10-11）**

| # | 项目 | 结果 |
|---|---|---|
| 1 | 创造物品页「病证·药材」 | ✅ ok |
| 2 | 修合台 GUI 打开、5×5 槽位布局 | ⚠️ 坐标正确，但**背景贴图被放大** |
| 3-1 | 桂枝汤合成 | ✅ ok |
| 3-2 | 炙甘草合成 | ✅ ok |
| 3-3 | 生附子 → 制附子 | ✅ ok |
| 4-1 | 生附子食用 → 心脉痹阻 | ✅ ok |
| 4-2 | 制附子食用 → 无 debuff | ⚠️ **制附子无法右键使用** |
| 5 | `/bingzhengmod yingwei` → 50/100 | ✅ ok |
| 6 | 服桂枝汤 → 表气调和 → 营卫 100 | ✅ ok |
| 7 | **退出重进存档，营卫仍为 100** | ✅ **ok（Attachment 确实落盘了）** |

**已修复的两处 bug**

- **Bug1 修合台 GUI 贴图缩放异常**
  根因：`drawTexture` 7 参重载按 256×256 采样，而贴图是 176×222。
  修复：`assets2.cjs` 改为生成 **256×256** 贴图（内容仍绘在左上 176×222），代码里的简单 7 参调用不再需要改动。
- **Bug2 制附子无法右键食用**
  根因：`HerbItem.use()` 只对 `toxic=true` 的生药有反应，无毒的走 `pass`。
  修复：**所有药材统一可右键「入口尝药」**；有毒 → 心脉痹阻，无毒 → 提示「服下「%s」，气味平和，未觉异样。」
  这样「生用有毒 / 炮制去毒」的对比也变成了玩家可亲手验证的体验。

**修复后的自动化验证**：编译零错误、物品自检通过、配方自检 7/7、模型/贴图零加载错误。

**二次复验结果（用户确认）**

| # | 项目 | 结果 |
|---|---|---|
| 1 | 修合台 GUI 背景贴图比例 | ✅ ok |
| 2 | 制附子右键食用，提示正常、无 debuff | ✅ ok |
| 3 | 普通药材（桂枝、甘草）右键尝药 | ✅ ok |

**核心功能全部通过 → 用户接受当前版本作为「内核切片定稿」。**

---

## 九、内核切片定稿（v0.1.0-core-slice）

**定稿指纹（日后用于核对是否为同一份产物）**

| 项 | 值 |
|---|---|
| 产物 | `build/libs/bingzhengmod-0.1.0.jar` |
| 大小 | 59982 bytes (58.6 KB) |
| SHA-256 | `f284ad665ed9eab1307e733cbda49ac930b2f80d3b6107250176e86d8cd99853` |
| MD5 | `bc420cf1ac7c3717516d0750b9416739` |
| 构建时间 | 2026-10-11 00:00:30 |
| Java 目标 | release 17（class 版本 61） |
| Mixin 数量 | **0** |

> ⚠️ `build/` 会被 `gradlew clean` 清空。若要长期保存这份定稿产物，请把它复制到 `build/` 之外。
> ⚠️ 本仓库 **尚无任何提交**（`master` 分支为空，但 index 里已 stage 过一批文件）。定稿后建议做一次初始提交并打 tag，否则无法回溯。

---

## 十、后续迭代 TODO（用户指定，**暂不修改**）

### TODO-A 修合台箭头方向反了（美术）
- 现象：网格与产物格之间的箭头**指向左侧**，方向反了。
- 应为：MC 原版逻辑是「输入在左、输出在右」，箭头须**由左指向右**。
- 位置：`.tools/pinyin/assets2.cjs` 里绘制箭头的循环；相关槽位常量在 `XiuheTableScreenHandler`（`GRID_X + 90 + 4` ~ `RESULT_X`）。
- 影响：**纯视觉，不影响合成功能**。

### TODO-B GUI 美术质感与原版差距大（美术）
- 现象：箭头是手绘三角形；GUI 四角是**直角**，线条质感偏硬。
- 原版特征：容器 GUI 四角为**圆角**（角上 1–2 像素透明或斜切）；箭头是带渐变的 22×16 贴图，而非纯色三角。
- 建议：生成时对四角做圆角；箭头改为多段渐变的原版风格；可参考原版 `textures/gui/container/crafting_table.png` 的箭头比例。

### TODO-C 药材食用改用 FoodComponent（架构）
- 现状：`HerbItem` 覆写 `use()` 手写右键逻辑。
- 目标：尽量复用原版 `Item.Settings.food(FoodComponent)` 食物组件，而不是自造一套右键逻辑。
- 收益：获得原版进食动画/音效/时长；与其它模组的「食物」判定天然兼容。
- 注意点：
  - `FoodComponent` 的 `statusEffect` 挂的是 `StatusEffectInstance`，而我们的自定义效果本身就是 `StatusEffect` —— **可以直接挂心脉痹阻**，「生用有毒」能原样表达。
  - `FoodComponent` 会附带饱食度/饱和度。这**不违反约束 1**（那是给「我们自己的物品」设属性，不是改写玩家的原版字段），但要确认是否符合你想要的游戏手感。
  - 需要先定：**哪些药材算「食物」**？生附子/制附子作为「尝药」入口合理；桂枝、甘草是否也给饱食度，需你拍板。
- 建议与 TODO-A/B 一并放到下一轮迭代。

---

## 十一、下一阶段候选（未排期）

1. `herbs_temp.json` 的 320 条收口为官方 62 味原生药材清单（含 3 条未解析的 base）
2. 陶制药锅 / 紫铜药炉（汤剂正式设备）+ 文武火机制
3. 石磨（磨药粉）—— 文档规定丸散膏必须先磨粉
4. 感冒病症本体（风寒/风热）与「患病→辨病→寻药→制药→治病」主循环
5. 23 个拓展的空开关钩子
6. 配方数据外置到配置文件（清掉代码里的 `//TODO 后续迁移配置文件`）

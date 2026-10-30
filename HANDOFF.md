# 《病证》MC 模组 · 会话交接总览（给下一个 AI）

> 用途：接续开发。写于 2026-10-24，覆盖 0.1.0 → 0.9.10 全程。
> 项目根：`E:\bingzheng`｜模组 id：`bingzhengmod`｜最新产物：`dist/bingzhengmod-0.9.10.jar`

---

## 0. 一句话现状

内核（修合台/配方/营卫）、情志（七情+神耗体耗+HUD+睡眠/日光）、五脏（气血+虚实+行为驱动）、命令层（三组收口）均已实现，
最新 **v0.9.11**（323530 bytes，SHA-256 `132c98a83779de74850c333788e2aa66ae0e68992d5e70fa6fb08694122cf30f`），
自测 **118 项全通过**（配方 7 / 情志 **53** / 五脏 23 / 行为 17 / 指令 18）+ 冒烟 exit 0。
**v0.9.10**：未睡计债按本夜在线时长分档（≥7 游戏小时 +3 / 4–7 +1 / <4 豁免，挂机整夜仍算通宵）；
日照抵债 **3000 tick 定案**（原「10 现实分钟」是用户换算失误 —— MC 白天总共只有 10 现实分钟）。
**文档已升 v1.08**（`《病证》MC_模组_v1.08.docx`，2026-10-24 落地）：情志拓展新增「五、五志行为驱动」「六、神耗·体耗 HUD」「七、睡眠、睡眠债与日光」，并修好 v1.07 里漏了标题样式的「四、时辰节律波动」。
**待办**：0.9.x 实机验收、git 提交（须用户点头）。

---

## 1. 不可更改的技术栈（用户锁定）

| 项 | 版本 |
|---|---|
| Minecraft | 1.20.1 |
| Fabric Loader | 0.18.4 |
| Fabric API | 0.92.12+1.20.1 |
| Yarn | 1.20.1+build.10 |
| Gradle | 9.7.1 |
| Loom | 1.10.5 |
| Java | 17（`release = 17`） |

产物必须能在 **PCL2 2.12.1**（`D:\PCL 正式版 2.12.1\`）上以 Java 17 运行。

---

## 2. 版本履历（每版一句话）

| 版本 | 内容 |
|---|---|
| 0.1.0 | 内核：修合台 5×5 GUI、自有配方引擎、营卫 Attachment、11 件物品 / 9 味药材 |
| 0.2.0 | 情志拓展：七情浮点 + 神耗/体耗 + `emotion(_debug)` 命令；调试命令正式版默认关闭 |
| 0.2.1 | 心脉痹阻 / 表气调和接线 |
| 0.3.0 | 气机扰动（神扰/神安）、静坐坐垫 |
| 0.3.1 | 神扰退出时的极端二次爆发 |
| 0.4.x | 五脏拓展：气血 5+5、虚实、20 buff |
| 0.5.x | 五脏行为驱动 v1（强度点）+ 恢复曲线按偏离距离成比例 |
| 0.6.0 | 五脏时辰节律波动（子午流注，振幅 ±4，变速到达 + 抖动） |
| 0.7.0 | 情志行为驱动 v2 + 七情时辰波动（振幅 ±2、抖动 ±0.2） |
| 0.7.1 | 迟滞裁定：**70 触发 / 65 解除** |
| 0.8.0 | 七情行为驱动量化 v1（五志：怒/喜/思/悲/恐 + 忧惊联动） |
| 0.8.1 | 崩溃修复：ENTITY_UNLOAD 内查方块 → 同步加载区块 → CME |
| 0.9.0 | 神耗/体耗 HUD 双行条（HudRenderCallback + 自定义 S2C） |
| 0.9.1 | HUD 布局 v2：模式守卫、动态布局（不重叠）、独立格子、主动推送 |
| 0.9.2 | 量化 v2：饥饿度为主源、GUI 交互为主源、持久化（去掉 JOIN 清零） |
| 0.9.3 | 疲劳专用调试命令 `fatigue` / `fatigue_debug` |
| 0.9.4 | 调试命令去属性门禁（OP 2 级可用）、疲劳周期打印独立 tick、HUD 聊天框上重绘 |
| 0.9.5 | 游泳判定重写、睡眠恢复梯度、睡眠债、日光调摄、emotion 现值附注 |
| 0.9.6 | 修复睡眠梯度不生效（自然睡醒被误判为中途下床）+ 日光改为 1/3 游戏小时阈值 |
| **0.9.7** | **修复每 tick 崩溃**（去掉 `currentScreenHandler` 读数，GUI 计点改走方块交互事件） |

---

## 3. 目录与关键文件

```
E:\bingzheng\
├─ src/main/java/com/bingzheng/
│  ├─ BingzhengMod.java            主入口（MOD_ID / LOGGER / id()）
│  ├─ emotion/                     情志拓展
│  │  ├─ EmotionState / EmotionAttachments / EmotionConfig / EmotionRules
│  │  ├─ EmotionService            tick 调度（单写入者）
│  │  ├─ EmotionBehaviorRules/Points/Events   五志行为驱动
│  │  ├─ EmotionFatigueRules/Fatigue          疲劳量化引擎（饥饿/方块/距离/夜晚/静止）
│  │  ├─ EmotionSleepRules/Sleep              睡眠梯度 + 睡眠债 + 日光
│  │  ├─ EmotionHudRules/HudSync               HUD 纯规则 + S2C 同步
│  │  ├─ EmotionFatigueCommands / EmotionCommands
│  │  └─ EmotionSelfCheck          51 项自检
│  ├─ wuzang/                      五脏拓展（WuzangService/Rules/Shichen/Rhythm/SelfCheck…）
│  ├─ selfcheck/                   内核 & 指令自检
│  └─ mixin/                       全项目唯一 1 处 Mixin（牛奶过滤，defaultRequire:0）
├─ src/client/java/com/bingzheng/emotion/client/
│  ├─ EmotionHudClient.java        双行条渲染 + 聊天框上重绘
│  └─ EmotionHudConfig.java        config/bingzhengmod-emotion-hud.properties（默认开）
├─ src/main/resources/assets/bingzhengmod/lang/{zh_cn,en_us}.json   各 98+ 键
├─ src/main/resources/assets/bingzhengmod/textures/gui/hud/{spirit_bars,fatigue_bars}.png  27×4
├─ PROGRESS.md                     唯一事实来源（2400+ 行，三十四章）
├─ dist/                           正式产物归档（18+ jar + README 指纹表）
├─ .tools/build.bat                构建包装：设 GRADLE_USER_HOME/TEMP，输出 gout.log（GBK）
└─ gradle.properties               mod_version=0.9.7
```

**架构纪律**
- 五脏对情志**零编译期依赖**（反射软链接 `EmotionAuthority`）；情志对五脏零依赖。
- 所有事件监听都是**后置/加性**，绝不取消原版行为。
- 全部 ID 走 `bingzhengmod:` 命名空间；魔数集中到 `*Config` 并标 `//TODO 后续迁移外部配置`。
- 全项目**只有 1 处 Mixin**（新增必须慎之又慎）。

---

## 4. 已拍板、不可擅改的裁定

1. `POINT_RATE_SCALE = 75`（五脏行为强度换算，用户定案）。
2. 气机扰动迟滞 **70 触发 / 65 解除**。
3. 「压力→神耗升」「过劳→体耗升」**刻意不实现**（会构成自激环）。
4. 五志恢复用「每时辰 −1」而非 2%/40t（否则行为 +1 会被瞬间抹平）。
5. 文档现任版本 = **《病证》MC_模组_v1.08**（2026-10-24 由 v1.07 升级）；PROGRESS 新引用注 v1.08，历史章节里对 v1.07 的引用保留原文。
6. 神耗/体耗：**满格 = 疲劳满**；HUD 上行神耗（月牙）、下行体耗（汗滴）。
7. 睡眠/日光（v0.9.5–0.9.6 口径）：恢复率 ≥7h 100% / 6.5h 87.5% / 6h 75% / <6h 封顶 75%；
   债：通宵 +3、≥8h −2、≥7h −1、6–7h 0、<6h +1，上限 6；神耗涨速 ×(1+0.25×债) 封顶 ×2.5；通宵次日体耗 ×1.5；
   日光：白天+头顶可见天空，**累计晒满 1 游戏小时（1000 tick）才开始减速**，晴 ×0.7 / 阴雨 ×0.85，
   **满 3 游戏小时（3000 tick）抵 1 债**（每天一次）。
8. 游戏时刻换算：**刻 = (小时 − 6 + 24) % 24 × 1000**（23:00 = 17000 刻；0 刻 = 06:00）。

---

## 5. 踩过的坑（照这个避雷）

1. **实体生命周期回调内禁止世界查询**：`ENTITY_LOAD/UNLOAD`、`AFTER_DEATH`、`ALLOW_DAMAGE` 里查 `getBlockState` 会同步加载区块 → 改实体管理器 → `ConcurrentModificationException`（0.8.0 实机崩溃）。正确做法：回调只入队，出到 `END_SERVER_TICK` 再查，且先 `isChunkLoaded`。
2. **禁止每 tick 读 `player.currentScreenHandler`**：会触发 `ScreenHandlerType.create` → `UnsupportedOperationException: Unable to construct this menu by type`，把整条 `tickPlayer` 打断（0.9.5 实机「什么都没生效」的真因）。GUI 计点必须走方块交互事件。
3. `PacketByteBufs` 不在 `net.minecraft.network` → 用 `new PacketByteBuf(Unpooled.buffer())`。
4. 1.20.1 **没有** `AdvancementEntry` → 用 `var entry` + `entry.getId()`；`DamageTypeTags` 在 `net.minecraft.registry.tag`。
5. `ScreenHandlerType.PLAYER` 不存在 → 用 `instanceof PlayerScreenHandler`。
6. Fabric API 0.92 **没有** `ScreenEvents.AFTER_RENDER` → 用 `ScreenEvents.AFTER_INIT` + `ScreenEvents.afterRender(screen)`。
7. `isSwimming()` 只在「冲刺入水」瞬间为 true；浅水 `isOnGround()` 会误判 ⇒ 游泳判定要「在水中」+ 只取 x/z 水平位移。
8. `now - Long.MIN_VALUE` 会 long 溢出成负数（首次交互永不计点）⇒ 先判 `last <= 0`。
9. 自检断言也会写错（05:00 = MC 刻 **23000**，不是 5000；定点积分差 1e-13 恰落在档位线下；叠加档应求和）。写断言先自证换算。
10. `build/libs` 只在 `clean` 时清；增量 `build` 下版本号一变就是新文件名 ⇒ 旧 jar 累积。**Loom 的正式包在 `build/libs`，开发态在 `build/devlibs`（`-dev.jar`）**，`gradle jar` 只产 dev jar，正式包要 `remapJar`（完整 `build` 才触发）。
11. 用模板字符串写含反引号的 markdown 会解析失败 ⇒ 改用单引号行数组 join。

---

## 6. 用户的交付规范（必须遵守）

**每次交付固定四段式**：① 变更清单 ② 全部自测结果 ③ 产物信息（文件名 + 版本 + SHA-256）④ PROGRESS.md 更新摘要。
**绝不**只丢一个 jar。诚实交代能力边界（自检验的是规则层/装配层，实体与渲染需用户实机验收）。
**git 未提交**（仓库仅有 `v0.1.0-core-slice` 的 `e826d2e`），提交/打 tag 前必须问。

**构建与重试纪律（用户明确要求）**
- 机制调试只跑**增量**编译（`gradlew compileJava` / `compileClientJava`），**发版才 `clean build`**；
- 长编译放**后台任务 + 轮询日志**，不阻塞；
- **小步提交式**：一个机制改完立刻编译验证，再进下一个；
- 失败后**等 5–10 秒**再重试，禁止连打。

**构建配方**：`MSYS_NO_PATHCONV=1 cmd.exe /c "E:\bingzheng\.tools\build.bat <task>"`，日志在 `.tools/gout.log`（**GBK 编码**）。
冒烟：`build.bat runClient -PbzSmoketest=20`，期望 `配方 7/7、情志 52/52、五脏 23/23、行为 17/17、指令 18/18` + `BUILD SUCCESSFUL`。

**一键脚本（v0.9.10 起）**：`pwsh -File tools\build.ps1 -Task all`（编译+冒烟+解析自检，一次批准跑完）、`pwsh -File tools\release.ps1 -Version x.y.z`（clean build + 归档 dist + 算 SHA-256）。详见 `tools/README.md`。
环境坑：Gradle 守护进程在沙箱内**无法 fork**（`--no-daemon` 也 fork）；只能用一次性的 `danger-full-access`（pwsh 后台）把它拉起来。

---

## 7. 下一位接手请按这个顺序做

1. ~~**指令重构**~~ —— **v0.9.8 已完成**：三组 `emotion` / `fatigue` / `wuzang`；`*_debug` 降为各组 `debug`；`emotion spirit|body` 已挪进 `fatigue set spirit|body`；新增 `fatigue reset`。顶层 6 → 3（`yingwei` 属内核未动）。`CommandSelfCheck` 路径表已同步（16 → 17 项，并加旧写法反向探针）。
2. ~~**toggleDebug 容错**~~ —— **已完成**：`emotion debug` / `wuzang debug` 整段包 `try/catch`，异常转中文回显 + WARN 日志。
3. ~~**疲劳 debug 行格式**~~ —— **已完成**：新增睡眠债 / 日照 `x/3000` tick / 昨夜通宵；疾跑、游泳改 `37/100 格` 读法。
4. **文档累积升 v1.08**（含 0.8.0 / 0.8.1 / 0.9.0–**0.9.8**）。
5. 建议实机验收清单：崩溃日志消失、`fatigue debug` 每 20 tick 刷一行、23:00 睡到天亮 80→20、日光 1h/3h 阈值、开工作台 +1 / 附魔台 +2 / 10 秒防刷、HUD 在护甲之上且聊天框不遮挡、大退重进数值保留、**旧写法 `emotion_debug` 报未知命令**。

---

## 8. 已知未完成 / 待办

- ~~指令重构~~（v0.9.8 完成）
- ~~`toggleDebug` 容错~~（v0.9.8 完成）
- ~~疲劳 debug 行格式~~（v0.9.8 完成）
- 文档 v1.08（唯一阻塞的文档项）
- git 提交与 tag（用户未同意前不动）
- 五脏拆包（pack-splitting）、第三方奶制品过滤、忧（瘟疫槽）接线、惊（温湿）增强、郁证/脏躁/癫狂等钩子
- 用户尚未做**完整实机验收**（只有 0.1.0 阶段验过；0.9.x 的验收项列在第 7 节第 5 条）

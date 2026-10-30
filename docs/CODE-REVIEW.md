# 代码走查（开源前审计）

> 走查范围：`src/` 全部 92 个 Java 文件 + 资源 + 构建脚本 + 现有文档。以下结论均基于实际文件，未做推测。

## 一、规模与结构

| 项 | 数值 |
|---|---|
| Java 文件 | 92 个（main 85 / client 7），共约 575 KB |
| 包分布 | emotion 33 / wuzang 27 / registry 5 / recipe 4 / 其余各 1–2 |
| Mixin | 1 处（`MilkBucketItemMixin`） |
| 依赖 | 仅 Fabric API + Minecraft，**无第三方模组依赖** |
| 测试 | 无 JUnit；用运行时自检（118 项）替代 |

分层是清楚的：`emotion` 与 `wuzang` 各自成包、互不 import（五脏侧通过 `behavior/EmotionAuthority` 反射访问情志）。

## 二、值得肯定的地方

1. **纯函数抽离得干净**：`EmotionSleepRules`、`WuzangRules`、`WuzangShichen`、`FatigueLedger`、`FatigueText` 全部无 MC 依赖，可直接跑断言，这也是 118 项自检能落地的前提。
2. **自检覆盖到「解析层」**：`CommandSelfCheck` 不是跑命令，而是构造 `CommandDispatcher` + `ParseResults` 做解析级断言，连「中文参数必然被拒」这种死分支都验了。
3. **数值集中**：`EmotionConfig`（22 KB）、`WuzangConfig`（16 KB）把常量收拢并标了迁移 TODO，没有散落的魔法数字。
4. **踩坑有记录**：两个最危险的坑（实体生命周期回调里查世界导致 CME、每 tick 读 `currentScreenHandler` 抛 UnsupportedOperationException）都在代码注释与 `PROGRESS.md` 里写清了，不容易被后来者重犯。

## 三、需要注意的地方（真实存在）

1. **自检 ≠ 测试**：118 项验的是规则层与装配层，**渲染、HUD 位置、实体交互一律没覆盖**。0.9.x 全部版本从未实机验收，这是最大风险。
2. **占位代码仍在**：`WuzangHooks` 是「全空实现的预留钩子层」，`EmotionHooks` 有「失眠 debuff 尚未实现」的注释——这些是**有意为之**（为拆包预留插槽），不是烂尾，但接手者要知道它们不做事。
3. **英文是半成品**：`lang/en_us.json` 与中文对齐了，但调试输出（`FatigueText`）直接拼中文字符串，英文玩家会看到中文。
4. **美术占位**：HUD 图标是 27×4 的极简几何图，物品贴图是程序生成风格的色块。
5. **无 CI**：没有 GitHub Actions。建议接手后先加一个「`gradlew build` + 冒烟」的工作流。
6. **`dist/` 里有 27 个历史 jar**（约 7 MB）。保留可用于回滚，但会让 clone 变慢；如嫌大可只留最近 3 版。
7. **`PROGRESS.md` 171 KB**：对继任者是宝库，但 GitHub 网页渲染会很慢，建议后续拆成 wiki。

## 四、开源前已处理事项

| 项 | 处理 |
|---|---|
| `fabric.mod.json` license | `All-Rights-Reserved` → `GPL-3.0-only` |
| 作者信息 | `bingzhengmod dev` → `Botzhanshen`，补 homepage/sources |
| LICENSE | 已放入 GPL-3.0 全文（35 KB） |
| 设计文档 | 只放 PDF（`docs/`），docx 不入库 |
| 本机敏感文件 | 删除 PCL 日志、authlib 日志、崩溃报告、`latest.log`、`启动脚本.bat`、启动器版本 JSON |
| `.gitignore` | 追加 `*.log`、`PCL 启动器日志.txt`、`游戏崩溃前的输出.txt`、`crash-*.txt`、`启动脚本.bat`、`*.docx`、`open-source/` |

## 五、给接手者的三个「先做这个」

1. 跑一遍 `./gradlew runClient -PbzSmoketest=20`，确认 118 项自检在你机器上也全绿；
2. 按 `HANDOFF.md` 第 7 节做一次实机验收，把不通过的记成 Issue；
3. 加 CI：一个 `build` + 一个冒烟工作流，从此不再靠人手验证。
# 病证（Bingzheng）· Minecraft 中医生存模组

> 一句话：把中医的「辨证论治」搬进 Minecraft——情志会致病、五脏有气血虚实、昼夜有节律，治病要靠修合台按君臣佐使配药。

[![Minecraft](https://img.shields.io/badge/Minecraft-1.20.1-62B47A)](https://www.minecraft.net/)
[![Fabric](https://img.shields.io/badge/Loader-Fabric%200.18.4-DBB69C)](https://fabricmc.net/)
[![License](https://img.shields.io/badge/License-GPL--3.0-blue)](LICENSE)

**状态：v0.9.11 可运行，118 项内置自检全通过；尚未完成完整实机验收。**
**作者已不再主动维护。代码完整可用，随取随用，欢迎 fork 或接手；没人接也没关系。**

设计文档（23 个拓展的完整设定）：[`docs/病证-设计文档-v1.08.pdf`](docs/病证-设计文档-v1.08.pdf)

---

## 这是什么

《病证》是一个 Fabric 模组，用中医理法（七情、五脏、气血、时辰、营卫）组织 Minecraft 的生存循环。

它不是「加几个药水」那种模组：数值全部隐形运行，玩家看到的是症状（心脉痹阻、神扰、脏躁），
要自己按「看证 → 对证 → 配方 → 炮制 → 服药」的流程去解。

## 核心特色

- **修合台 5×5 制药**：自研配方引擎，按君臣佐使摆位；药材需先炮制去毒，生附子直入心脉痹阻。
- **情志系统**：七情浮点 + 神耗/体耗双轨；气机扰动带迟滞（70 触发 / 65 解除），神扰会二次爆发。
- **五志行为驱动**：怒喜思悲恐按行为累积强度点，折算成脏腑气血升降；忧、惊只作联动（「忧惊不补」）。
- **五脏气血 + 虚实**：气血各 5 项、基值 80；<40 为虚、>90 为亢；20 个表现层 buff。
- **时辰节律**：五脏按子午流注波动（±4），七情另有一套更小的波动（±2）；**判定只认稳定值**。
- **睡眠与日光**：睡眠梯度、睡眠债（上限 6）、日光抵债；只认「本人上床睡到天亮」。
- **工程纪律**：全项目**只有 1 处 Mixin**（牛奶过滤，`defaultRequire:0` 可降级）；五脏对情志零编译期依赖（反射软链接）。

## 当前进度

已实现（v0.9.11，内置自检 118 项：配方 7 / 情志 53 / 五脏 23 / 行为 17 / 指令 18）：

| 模块 | 内容 |
|---|---|
| 内核 | 修合台 GUI、配方引擎、营卫 Attachment、11 件物品 / 9 味药材、心脉痹阻与表气调和 |
| 情志 | 七情、神耗/体耗、气机扰动、神扰/神安、静坐坐垫、时辰波动、五志驱动、HUD 双行条、睡眠债与日光 |
| 五脏 | 气血、虚实迟滞、20 buff、时辰波动、行为驱动量化 v2 |
| 命令层 | `emotion` / `fatigue` / `wuzang` 三组；调试输出三行排版；写操作 OP 2 级 |

**未完成（欢迎接手）**：

- 完整实机验收（只有 0.1.0 阶段验过，0.9.x 全部待验）
- 美术：物品/方块贴图与 HUD 图标基本是占位
- 英文化：调试输出仍是中文
- 五脏拆包（pack-splitting）
- 设计文档里另有 20+ 拓展只有设定、没有实现（瘟疫、天癸、经络、骨节……）

## 如何运行

**环境**：JDK 17 · Gradle 9.7.1 · Fabric Loader 0.18.4 · Fabric API 0.92.12+1.20.1 · Yarn 1.20.1+build.10 · Loom 1.10.5

```bash
./gradlew build          # 产物在 build/libs/bingzhengmod-<version>.jar
./gradlew runClient      # 直接启动开发客户端
```

无人值守冒烟（进主菜单 20 秒后自动退出，并打印自检结果）：

```bash
./gradlew runClient -PbzSmoketest=20
```

Windows 一键脚本（`tools/`）：

```powershell
pwsh -File tools\build.ps1 -Task all      # 编译 + 冒烟 + 解析自检
pwsh -File tools\release.ps1 -Version 0.9.12
```

## 文件结构

```
src/main/java/com/bingzheng/
  BingzhengMod.java            入口
  recipe/ screen/ block/       修合台与配方引擎（内核，已冻结）
  herb/ item/ effect/          药材与效果
  emotion/                     情志拓展（七情、神耗体耗、睡眠、HUD 规则）
  wuzang/                      五脏拓展（气血、虚实、时辰、行为驱动）
  selfcheck/ command/ debug/   自检与命令
  mixin/                       全项目唯一 Mixin（牛奶过滤）
src/client/java/               客户端渲染（HUD）
src/main/resources/            资源、语言文件（zh_cn / en_us）、HUD 贴图
docs/                          设计文档（PDF）
dist/                          历次发布产物 + SHA-256 指纹表
tools/                         一键构建 / 发版 / 日志解析脚本
PROGRESS.md                    开发进度的事实来源（40 章，含踩坑记录）
HANDOFF.md                     给接手者的总览
CONTRIBUTING.md                如何参与
```

## 接力与贡献

这个项目是一个人的业余产物，作者已不再继续维护。**代码是能跑的，架构是清楚的；能不能接着走，看有没有人正好感兴趣——没有也正常。**

1. **接过去继续做**——开 Issue 说一声，或直接 fork；
2. **挑一块做**——实机验收、美术、英文化、五脏拆包，都是独立可做的；
3. **只提意见**——玩一遍，把「哪里不像中医」「哪里不方便」写进 Issue，也非常有价值。

接手前请读 [`PROGRESS.md`](PROGRESS.md)（踩过的坑都在里面）与 [`HANDOFF.md`](HANDOFF.md)、[`CONTRIBUTING.md`](CONTRIBUTING.md)。

## 许可证

[GPL-3.0](LICENSE)。任何再分发（含修改版）必须同样以 GPL-3.0 开源。
如需允许闭源商用，请联系原作者协商改用其它许可。

---

*个人非商业作品，与 Mojang / Microsoft 无关联。*
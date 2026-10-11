# dist/ —— 已验收的发布产物归档

本目录存放**经人工实机验收通过**的定稿产物，不受 `gradlew clean` 影响。

| 项 | 值 |
|---|---|
| 产物 | `bingzhengmod-0.1.0.jar` |
| 里程碑 | `v0.1.0-core-slice`（内核切片定稿） |
| 大小 | 59982 bytes |
| SHA-256 | `f284ad665ed9eab1307e733cbda49ac930b2f80d3b6107250176e86d8cd99853` |
| 目标环境 | Minecraft 1.20.1 + Fabric Loader 0.18.4 + Fabric API 0.92.12+1.20.1 |
| Java | 17（class 版本 61） |
| Mixin | 0 |

## 实机验收结论（用户 PCL2 实测）

10 项全部通过，2 处已修 bug 复验通过：

- 创造物品页「病证·药材」、修合台 GUI、桂枝汤/炙甘草/制附子三种合成
- 生附子食用触发「心脉痹阻」、制附子食用无 debuff
- `/bingzhengmod yingwei` 返回 50/100，服桂枝汤后涨至 100
- **退出重进存档后营卫值仍为 100**（持久化 Attachment 确认落盘）

## 已知的非阻断问题

见仓库根的 `PROGRESS.md` →「十、后续迭代 TODO」：
修合台箭头方向反了、GUI 四角与箭头美术质感、药材食用应改用 `FoodComponent`。

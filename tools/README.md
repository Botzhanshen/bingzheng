# tools/ —— 一键脚本

沙箱在本机不可用，每条 shell 命令都要单独审批，所以把常用流程压成脚本：**一次批准跑完一整轮**。

## build.ps1 —— 编译 / 冒烟 / 解析日志

```
pwsh -File tools\build.ps1 -Task all         # 编译 → 解析 → 冒烟 → 解析（默认）
pwsh -File tools\build.ps1 -Task compile     # 只增量编译
pwsh -File tools\build.ps1 -Task smoke       # 只冒烟（默认 20 秒，可 -SmokeSeconds 30）
pwsh -File tools\build.ps1 -Task parse       # 只解析现有的 .tools\gout.log，不跑 Gradle
```

- 输出：各模块自检通过项、合计、FAIL 行、异常采样、BUILD 行；全绿 exit 0，否则 exit 1。
- `-LogPath <文件>` 可指定解析别的日志（支持 GBK 与 UTF-8 两种编码）。

## release.ps1 —— 发版

```
pwsh -File tools\release.ps1 -Version 0.9.11              # clean build → 归档 dist → 算 SHA-256
pwsh -File tools\release.ps1 -Version 0.9.11 -WithSmoke   # 先冒烟再发版
```

- 步骤：改 `gradle.properties` 的 `mod_version` →（可选冒烟）→ `clean build` → 复制 jar 到 `dist/` → 打印指纹。
- **不碰 git、不改 PROGRESS.md / dist/README.md** —— 那两处按交付规范手工回写，脚本会把该填的数值直接打出来。

## 约定

- 脚本都用 `cmd.exe /c .tools\build.bat` 走 Gradle（那张 bat 已经设好 `GRADLE_USER_HOME` 与 `TEMP`）。
- 日志固定写到 `.tools\gout.log`（GBK）。
- PowerShell 变量名**不区分大小写**，`$LogPath` 与 `$logPath` 是同一个变量 —— 脚本里参数与内部变量别撞名。

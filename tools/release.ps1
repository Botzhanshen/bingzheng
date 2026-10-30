<#
.SYNOPSIS
  《病证》一键发版：改版本号 → clean build → 归档到 dist → 算 SHA-256 → 打印可粘贴的报告块。
.EXAMPLE
  pwsh -File tools\release.ps1 -Version 0.9.11
  pwsh -File tools\release.ps1 -Version 0.9.11 -WithSmoke
.NOTES
  只做「构建 + 归档 + 指纹」，不碰 git、不改 PROGRESS.md / dist/README.md ——
  那两个文件按交付规范手工回写（脚本会把要写进去的数值直接打出来）。
#>
[CmdletBinding()]
param(
    [Parameter(Mandatory = $true)]
    [string]$Version,
    [switch]$WithSmoke,
    [int]$SmokeSeconds = 20
)

$ErrorActionPreference = "Stop"
$root = Split-Path -Parent $PSScriptRoot
Set-Location $root

if ($Version -notmatch "^\d+\.\d+\.\d+$") {
    Write-Host "版本号格式不对：$Version（要 0.9.11 这种）" -ForegroundColor Red
    exit 1
}

$props = Join-Path $root "gradle.properties"
$old = ([IO.File]::ReadAllText($props)) -match "mod_version=(\S+)"
$oldVer = $Matches[1]
$text = [IO.File]::ReadAllText($props)
$text = [Text.RegularExpressions.Regex]::Replace($text, "mod_version=\S+", "mod_version=$Version")
[IO.File]::WriteAllText($props, $text)
Write-Host "版本号 $oldVer -> $Version" -ForegroundColor Cyan

function Run-Gradle([string]$gradleArgs) {
    Write-Host ""
    Write-Host "==> gradle $gradleArgs" -ForegroundColor Cyan
    cmd.exe /c "$root\.tools\build.bat $gradleArgs" | Out-Null
}

if ($WithSmoke) {
    Run-Gradle "runClient -PbzSmoketest=$SmokeSeconds"
    $log = [IO.File]::ReadAllLines((Join-Path $root ".tools\gout.log"), [Text.Encoding]::GetEncoding(936))
    $bad = @($log | Where-Object { $_ -match "\[FAIL\]|存在 \d+ 项失败" })
    if ($bad.Count -gt 0 -or -not ($log | Where-Object { $_ -match "BUILD SUCCESSFUL" })) {
        Write-Host "冒烟没过，终止发版。" -ForegroundColor Red
        exit 1
    }
    Write-Host "冒烟通过。" -ForegroundColor Green
}

Run-Gradle "clean build"

$jar = Join-Path $root ("build\libs\bingzhengmod-" + $Version + ".jar")
if (-not (Test-Path $jar)) {
    Write-Host "没找到产物：$jar" -ForegroundColor Red
    Write-Host "build\libs 里现有：" -ForegroundColor Yellow
    Get-ChildItem (Join-Path $root "build\libs") -ErrorAction SilentlyContinue | ForEach-Object { Write-Host ("  " + $_.Name) }
    exit 1
}

$dist = Join-Path $root "dist"
if (-not (Test-Path $dist)) { New-Item -ItemType Directory -Path $dist | Out-Null }
Copy-Item $jar (Join-Path $dist (Split-Path $jar -Leaf)) -Force

$item = Get-Item $jar
$hash = (Get-FileHash $jar -Algorithm SHA256).Hash.ToLower()
$kb = [Math]::Round($item.Length / 1024, 1)

Write-Host ""
Write-Host "================ 可粘贴的报告块 ================" -ForegroundColor Cyan
Write-Host ("产物：dist/bingzhengmod-{0}.jar" -f $Version)
Write-Host ("大小：{0} bytes（{1} KB）" -f $item.Length, $kb)
Write-Host ("SHA-256：{0}" -f $hash)
Write-Host ("dist/README.md 表格行：| `bingzhengmod-{0}.jar` | <里程碑一句话> | {1} KB | `{2}` |" -f $Version, $kb, $hash)
Write-Host "================================================" -ForegroundColor Cyan
Write-Host ""
Write-Host "还差三步（照交付规范）：" -ForegroundColor Yellow
Write-Host "  1. PROGRESS.md 补章节（变更清单 + 自测数字 + 上面对指纹）"
Write-Host "  2. dist/README.md 加一行并改「当前最新」标记"
Write-Host "  3. git 提交须用户点头"
exit 0
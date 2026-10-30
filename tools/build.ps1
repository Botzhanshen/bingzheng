<#
.SYNOPSIS
  《病证》一键构建 / 冒烟 / 日志解析。
.DESCRIPTION
  把「编译 → 冒烟 → 读 GBK 日志 → 汇总自检数字」压成一条命令，
  一次审批跑完一整轮验证，不用逐条点。

  -Task parse    只解析现有 .tools\gout.log（不跑 Gradle，适合复盘上一次构建）
  -Task compile  增量编译（compileJava）后解析
  -Task smoke    冒烟（runClient，默认 20 秒）后解析
  -Task all      编译 → 解析 → 冒烟 → 解析（默认）
.EXAMPLE
  pwsh -File tools\build.ps1 -Task all
  pwsh -File tools\build.ps1 -Task compile
  pwsh -File tools\build.ps1 -Task smoke -SmokeSeconds 30
#>
[CmdletBinding()]
param(
    [ValidateSet("compile", "smoke", "all", "parse")]
    [string]$Task = "all",
    [int]$SmokeSeconds = 20,
    # 可选：指定要解析的日志文件（默认 .tools\gout.log）
    [string]$LogPath = ""
)

$ErrorActionPreference = "Stop"
$root = Split-Path -Parent $PSScriptRoot
Set-Location $root
# 注意：PowerShell 变量名不区分大小写，$LogPath（参数）与 $logPath 是同一个变量，
# 所以内部一律用 $logFile，别和参数撞名。
$logFile = Join-Path $root ".tools\gout.log"
if ($LogPath) {
    $logFile = if ([IO.Path]::IsPathRooted($LogPath)) { $LogPath } else { Join-Path $root $LogPath }
}

function Invoke-GradleTask([string]$gradleArgs) {
    Write-Host ""
    Write-Host "==> gradle $gradleArgs" -ForegroundColor Cyan
    $sw = [Diagnostics.Stopwatch]::StartNew()
    cmd.exe /c "$root\.tools\build.bat $gradleArgs" | Out-Null
    $sw.Stop()
    Write-Host ("==> 用时 {0:N0}s" -f $sw.Elapsed.TotalSeconds) -ForegroundColor DarkGray
}

function Read-AnyLog {
    # gradle 日志是 GBK，但自造的样例日志可能是 UTF-8：先按 UTF-8 严格解码，
    # 解不开（GBK 字节在 UTF-8 下必然非法）再回落到 936。
    if (-not (Test-Path $logFile)) { return @() }
    $bytes = [IO.File]::ReadAllBytes($logFile)
    try {
        $strict = New-Object System.Text.UTF8Encoding($false, $true)
        $text = $strict.GetString($bytes)
    } catch {
        $text = [Text.Encoding]::GetEncoding(936).GetString($bytes)
    }
    return @($text -split "?
")
}

function Show-Summary {
    Write-Host ("日志：" + $logFile) -ForegroundColor DarkGray
    $lines = Read-AnyLog
    if (-not $lines -or $lines.Count -eq 0) {
        Write-Host "（找不到 .tools\gout.log）" -ForegroundColor Yellow
        return $false
    }

    $selfCheck = @($lines | Where-Object { $_ -match "完成：通过 \d+ 项" })
    $failures = @($lines | Where-Object { $_ -match "\[FAIL\]|存在 \d+ 项失败" })
    $buildLine = @($lines | Where-Object { $_ -match "BUILD (SUCCESSFUL|FAILED)" } | Select-Object -Last 1)
    $exceptions = @($lines | Where-Object { $_ -match "Exception|UnsupportedOperation|挂载失败" } | Select-Object -First 5)

    Write-Host ""
    Write-Host "---- 自检 ----" -ForegroundColor Cyan
    $total = 0
    if ($selfCheck.Count -eq 0) {
        Write-Host "（日志里没有自检汇总行）" -ForegroundColor DarkGray
    } else {
        foreach ($l in $selfCheck) {
            if ($l -match "【病证·(.+?)自检】完成：通过 (\d+) 项") {
                Write-Host ("  {0,-10} {1,3} 项" -f $matches[1], [int]$matches[2]) -ForegroundColor Green
                $total += [int]$matches[2]
            } else {
                Write-Host ("  " + $l.Trim()) -ForegroundColor DarkGray
            }
        }
        Write-Host ("  {0,-10} {1,3} 项" -f "合计", $total) -ForegroundColor Green
    }

    if ($failures.Count -gt 0) {
        Write-Host "---- 失败 ----" -ForegroundColor Red
        $failures | ForEach-Object { Write-Host "  $_" -ForegroundColor Red }
    } else {
        Write-Host "  无 FAIL 行" -ForegroundColor Green
    }

    if ($exceptions.Count -gt 0) {
        Write-Host "---- 异常采样 ----" -ForegroundColor Yellow
        foreach ($e in $exceptions) {
            $s = if ($e.Length -gt 160) { $e.Substring(0, 160) } else { $e }
            Write-Host ("  " + $s) -ForegroundColor Yellow
        }
    }

    Write-Host ""
    if ($buildLine) {
        $okBuild = $buildLine -match "BUILD SUCCESSFUL"
        if ($okBuild) { Write-Host ("---- " + $buildLine.Trim()) -ForegroundColor Green }
        else { Write-Host ("---- " + $buildLine.Trim()) -ForegroundColor Red }
        return ($okBuild -and $failures.Count -eq 0)
    }
    Write-Host "---- 日志里没有 BUILD SUCCESSFUL / FAILED ----" -ForegroundColor Yellow
    return $false
}

switch ($Task) {
    "parse"   { $ok = Show-Summary }
    "compile" { Invoke-GradleTask "compileJava"; $ok = Show-Summary }
    "smoke"   { Invoke-GradleTask "runClient -PbzSmoketest=$SmokeSeconds"; $ok = Show-Summary }
    "all" {
        Invoke-GradleTask "compileJava"
        $ok = Show-Summary
        if ($ok) {
            Invoke-GradleTask "runClient -PbzSmoketest=$SmokeSeconds"
            $ok = Show-Summary
        } else {
            Write-Host "编译没过，跳过冒烟。" -ForegroundColor Red
        }
    }
}

if ($ok) { Write-Host "" ; Write-Host "结论：通过" -ForegroundColor Green; exit 0 }
else     { Write-Host "" ; Write-Host "结论：未通过（见上）" -ForegroundColor Red; exit 1 }

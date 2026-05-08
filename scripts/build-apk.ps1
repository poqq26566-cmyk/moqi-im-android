<#
.SYNOPSIS
    在仓库根目录调用 Gradle 编译 app APK。

.PARAMETER Variant
    debug 对应 assembleDebug；release 对应 assembleRelease。

.PARAMETER Clean
    若指定，先执行 clean 再 assemble。
#>
param(
    [ValidateSet("debug", "release")]
    [string]$Variant = "debug",
    [switch]$Clean
)

$ErrorActionPreference = "Stop"

$RepoRoot = Split-Path -Parent (Split-Path -Parent $MyInvocation.MyCommand.Path)
$Gradlew = Join-Path $RepoRoot "gradlew.bat"
if (-not (Test-Path -LiteralPath $Gradlew)) {
    throw "找不到 gradlew.bat: $Gradlew"
}

Push-Location $RepoRoot
try {
    if ($Clean) {
        Write-Host "执行: gradlew.bat clean" -ForegroundColor Cyan
        & $Gradlew clean --no-daemon
        if ($LASTEXITCODE -ne 0) { throw "gradlew clean 失败，退出码: $LASTEXITCODE" }
    }
    $task = if ($Variant -eq "release") { "assembleRelease" } else { "assembleDebug" }
    Write-Host "执行: gradlew.bat $task" -ForegroundColor Cyan
    & $Gradlew $task --no-daemon
    if ($LASTEXITCODE -ne 0) { throw "gradlew $task 失败，退出码: $LASTEXITCODE" }
}
finally {
    Pop-Location
}

$apkDir = if ($Variant -eq "release") { "release" } else { "debug" }
$apkName = if ($Variant -eq "release") { "app-release.apk" } else { "app-debug.apk" }
$apkPath = Join-Path $RepoRoot "app\build\outputs\apk\$apkDir\$apkName"
Write-Host "构建完成: $apkPath" -ForegroundColor Green

<#
.SYNOPSIS
    列出 ADB 已连接设备并将 APK 安装到手机。

.DESCRIPTION
    依赖 PATH 中的 adb（Android SDK platform-tools）。
    未指定 -ApkPath 时，默认使用仓库内 app-debug.apk（需先执行 assembleDebug）。

.PARAMETER ApkPath
    要安装的 APK 绝对或相对路径。留空则使用默认 debug 输出路径。

.PARAMETER Serial
    目标设备序列号（adb devices 第一列）。多设备时默认选第一台，可用本参数指定。

.PARAMETER Variant
    未传 -ApkPath 时，用 debug 或 release 对应默认输出文件名与目录。
#>
param(
    [string]$ApkPath = "",
    [string]$Serial = "",
    [ValidateSet("debug", "release")]
    [string]$Variant = "debug"
)

$ErrorActionPreference = "Stop"

function Get-AdbPath {
    $cmd = Get-Command adb -ErrorAction SilentlyContinue
    if (-not $cmd) {
        throw "未在 PATH 中找到 adb。请安装 Android SDK platform-tools 并加入 PATH。"
    }
    return $cmd.Source
}

function Get-AdbDevices {
    $output = & $AdbExe devices 2>&1
    if ($LASTEXITCODE -ne 0) {
        throw "adb devices 失败: $output"
    }
    $serials = New-Object System.Collections.Generic.List[string]
    foreach ($line in $output) {
        if ($line -match "^\s*(\S+)\s+device\s*$") {
            $serials.Add($Matches[1])
        }
    }
    return ,$serials.ToArray()
}

$AdbExe = Get-AdbPath
$RepoRoot = Split-Path -Parent (Split-Path -Parent $MyInvocation.MyCommand.Path)

if ([string]::IsNullOrWhiteSpace($ApkPath)) {
    $apkDir = if ($Variant -eq "release") { "release" } else { "debug" }
    $apkName = if ($Variant -eq "release") { "app-release.apk" } else { "app-debug.apk" }
    $ApkPath = Join-Path $RepoRoot "app\build\outputs\apk\$apkDir\$apkName"
}

$ApkPath = $ExecutionContext.SessionState.Path.GetUnresolvedProviderPathFromPSPath($ApkPath)
if (-not (Test-Path -LiteralPath $ApkPath)) {
    throw "找不到 APK: $ApkPath。请先构建（例如在项目根目录执行 .\gradlew.bat assembleDebug），或使用 -ApkPath 指定文件。"
}

$devices = Get-AdbDevices
if ($devices.Count -eq 0) {
    throw "没有处于 device 状态的设备。请连接手机、打开 USB 调试，并执行 adb devices 确认已授权。"
}

$targetSerial = $Serial
if ([string]::IsNullOrWhiteSpace($targetSerial)) {
    if ($devices.Count -gt 1) {
        Write-Host "检测到多台设备，将使用第一台。可用 -Serial 指定：" -ForegroundColor Yellow
        foreach ($d in $devices) { Write-Host "  $d" }
        $targetSerial = $devices[0]
    }
    else {
        $targetSerial = $devices[0]
    }
}
elseif ($devices -notcontains $targetSerial) {
    throw "指定的序列号不在当前已连接设备列表中: $targetSerial"
}

Write-Host "设备: $targetSerial" -ForegroundColor Cyan
Write-Host "安装: $ApkPath" -ForegroundColor Cyan
Write-Host "若手机弹出安装或权限确认，请解锁屏幕并完成操作。" -ForegroundColor DarkGray

# 不要对 adb 输出管道，否则会丢失 $LASTEXITCODE。
# adb 35+ 的 install 不接受 GNU 式「--」；写「install -r -- 路径」会把「--」误当成长选项前缀，报 Unable to open file: -S。
$installOut = @(& $AdbExe "-s" $targetSerial "install" "-r" $ApkPath 2>&1)
$installExit = $LASTEXITCODE
foreach ($line in $installOut) {
    Write-Host $line
}

if ($installExit -ne 0) {
    $blob = ($installOut | ForEach-Object { "$_" }) -join "`n"
    $hint = ""
    if ($blob -match "INSTALL_FAILED_ABORTED|User rejected permissions") {
        $hint = "`n`n提示：请在手机上确认「安装」/权限弹窗（需解锁屏幕）；拒绝会导致本错误。"
    }
    elseif ($blob -match "INSTALL_FAILED_VERSION_DOWNGRADE") {
        $hint = "`n`n提示：新版本号低于已安装应用，可先卸载手机上的旧包再装，或使用带 -d 的安装命令。"
    }
    elseif ($blob -match "INSTALL_FAILED_UPDATE_INCOMPATIBLE|signatures do not match") {
        $hint = "`n`n提示：签名与已安装版本不一致，需先卸载旧应用再安装。"
    }
    throw "adb install 失败 (退出码 $installExit)。`n$blob$hint"
}
Write-Host "安装完成。" -ForegroundColor Green

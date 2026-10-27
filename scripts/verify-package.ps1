param(
    [string]$Executable = 'build/jpackage/image-v2/SE Tetris/SE Tetris.exe',
    [ValidateRange(1, [long]::MaxValue)][long]$MaxInstalledBytes = 500000000
)
$ErrorActionPreference = 'Stop'
$workspace = [IO.Path]::GetFullPath((Join-Path $PSScriptRoot '..'))
$exePath = [IO.Path]::GetFullPath((Join-Path $workspace $Executable))
if (-not $exePath.StartsWith($workspace + [IO.Path]::DirectorySeparatorChar, [StringComparison]::OrdinalIgnoreCase)) {
    throw 'Executable must be inside this workspace.'
}
if (-not (Test-Path -LiteralPath $exePath -PathType Leaf)) { throw "Executable missing: $exePath" }
$appDirectory = [IO.Path]::GetDirectoryName($exePath)
$installedBytes = [long](Get-ChildItem -LiteralPath $appDirectory -File -Recurse |
    Measure-Object -Property Length -Sum).Sum
if ($installedBytes -gt $MaxInstalledBytes) {
    throw "6-4: Installed package exceeds disk budget: $installedBytes > $MaxInstalledBytes bytes."
}
$outputDir = Join-Path $workspace 'build/verification'
$profileDir = Join-Path $outputDir 'package-smoke-profile'
New-Item -ItemType Directory -Path $profileDir -Force | Out-Null
$start = [Diagnostics.ProcessStartInfo]::new()
$start.FileName = $exePath
$start.WorkingDirectory = [IO.Path]::GetDirectoryName($exePath)
$start.UseShellExecute = $false
$start.CreateNoWindow = $true
# GUI 창 생성·종료를 확인하는 전경 앱 테스트이므로 창을 표시한다.
$start.WindowStyle = [Diagnostics.ProcessWindowStyle]::Normal
# 기존 사용자 설정·기록과 격리한다.
$start.Environment['APPDATA'] = $profileDir
$launched = [Diagnostics.Process]::Start($start)
$windowProcess = $launched
try {
    $deadline = [DateTime]::UtcNow.AddSeconds(20)
    do {
        $launched.Refresh()
        if ($launched.HasExited) { throw "Package exited before opening its window: $($launched.ExitCode)" }
        if ($launched.MainWindowHandle -ne [IntPtr]::Zero) { break }
        # jpackage 런처는 같은 EXE의 자식 프로세스에 실제 Swing 창을 만들 수 있다.
        $children = Get-CimInstance Win32_Process -Filter "ParentProcessId = $($launched.Id)"
        foreach ($child in $children) {
            if ($child.ExecutablePath -ne $exePath) { continue }
            $candidate = [Diagnostics.Process]::GetProcessById($child.ProcessId)
            if ($candidate.MainWindowHandle -ne [IntPtr]::Zero) { $windowProcess = $candidate; break }
            $candidate.Dispose()
        }
        if ($windowProcess.MainWindowHandle -ne [IntPtr]::Zero) { break }
        Start-Sleep -Milliseconds 100
    } while ([DateTime]::UtcNow -lt $deadline)
    if ($windowProcess.MainWindowHandle -eq [IntPtr]::Zero) { throw 'Package did not open a window within 20 seconds.' }
    $result = [ordered]@{
        checkedAtUtc = [DateTime]::UtcNow.ToString('o')
        executable = $exePath
        sha256 = (Get-FileHash -LiteralPath $exePath -Algorithm SHA256).Hash
        processId = $launched.Id
        windowProcessId = $windowProcess.Id
        windowTitle = $windowProcess.MainWindowTitle
        windowCreated = $true
        installedBytes = $installedBytes
        maxInstalledBytes = $MaxInstalledBytes
        observedWindowWorkingSetBytes = $windowProcess.WorkingSet64
        observedWindowPeakWorkingSetBytes = $windowProcess.PeakWorkingSet64
        operatingSystem = [Environment]::OSVersion.VersionString
        isolatedAppData = $profileDir
        scope = 'Native EXE startup/exit and installed disk budget only. Observed startup memory is not a 1GB RAM / 1.2GHz hardware certification. Manual IME/play testing is separate.'
    }
    if (-not $windowProcess.CloseMainWindow()) { throw 'Package window rejected the close request.' }
    if (-not $windowProcess.WaitForExit(10000)) { throw 'Package GUI process did not exit after window close.' }
    if (-not $launched.WaitForExit(10000)) { throw 'Package did not exit after window close.' }
    if ($launched.ExitCode -ne 0) { throw "Package exit code: $($launched.ExitCode)" }
    $result['exitCode'] = $launched.ExitCode
    $result | ConvertTo-Json | Set-Content -LiteralPath (Join-Path $outputDir 'package-smoke.json') -Encoding utf8
    $result | ConvertTo-Json
} finally {
    # 이 스크립트가 생성한 프로세스만 정리한다.
    if (-not $launched.HasExited) { $launched.Kill($true); $launched.WaitForExit(5000) | Out-Null }
    if ($windowProcess.Id -ne $launched.Id) { $windowProcess.Dispose() }
    $launched.Dispose()
}

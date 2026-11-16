# S11-B1: guiAcceptance 결과 XML에서 대전 GUI 인수 클래스가 실제로 실행됐는지 검사하고 요약 JSON을 남긴다.
# 필수 클래스가 없거나, 실행 테스트가 0개이거나, 실패·오류가 있으면 종료 코드 1.
param(
    [string]$ResultsDir = "build/test-results/guiAcceptance",
    [string]$Out = "build/verification/gui-acceptance-summary.json",
    [string[]]$Required = @(
        "tetris.ui.BattleMainFrameTest",
        "tetris.versus.ui.BattleLifecycleTest",
        "tetris.versus.ui.BattleDemoAcceptanceTest",
        "tetris.versus.ui.VersusAcceptanceGuiTest",
        "tetris.versus.ui.LoopbackLatencyAcceptanceTest"
    )
)
$ErrorActionPreference = "Stop"
[Console]::OutputEncoding = [System.Text.Encoding]::UTF8

$suites = @{}
if (Test-Path $ResultsDir) {
    foreach ($file in Get-ChildItem -Path $ResultsDir -Filter "TEST-*.xml") {
        [xml]$xml = Get-Content -Raw -Encoding UTF8 $file.FullName
        $s = $xml.testsuite
        $suites[$s.name] = [ordered]@{
            tests    = [int]$s.tests
            skipped  = [int]$s.skipped
            failures = [int]$s.failures
            errors   = [int]$s.errors
            executed = [int]$s.tests - [int]$s.skipped
        }
    }
}

$problems = @()
foreach ($name in $Required) {
    if (-not $suites.ContainsKey($name)) { $problems += "$name : not selected by guiAcceptance"; continue }
    $s = $suites[$name]
    if ($s.executed -le 0) { $problems += "$name : 0 tests executed (all skipped?)" }
    if ($s.failures + $s.errors -gt 0) { $problems += "$name : $($s.failures) failures, $($s.errors) errors" }
}
foreach ($name in $suites.Keys) {
    $s = $suites[$name]
    if ($s.failures + $s.errors -gt 0 -and $Required -notcontains $name) { $problems += "$name : $($s.failures) failures, $($s.errors) errors" }
}

$commit = "unknown"
try { $commit = (git rev-parse HEAD 2>$null).Trim() } catch { }
$summary = [ordered]@{
    commit      = $commit
    generatedAt = (Get-Date).ToUniversalTime().ToString("o")
    resultsDir  = $ResultsDir
    required    = $Required
    suites      = $suites
    totals      = [ordered]@{
        tests    = ($suites.Values | Measure-Object -Property tests -Sum).Sum
        skipped  = ($suites.Values | Measure-Object -Property skipped -Sum).Sum
        failures = ($suites.Values | Measure-Object -Property failures -Sum).Sum
        errors   = ($suites.Values | Measure-Object -Property errors -Sum).Sum
    }
    problems    = $problems
    passed      = ($problems.Count -eq 0)
}
New-Item -ItemType Directory -Force -Path (Split-Path $Out) | Out-Null
$summary | ConvertTo-Json -Depth 5 | Set-Content -Encoding UTF8 $Out

foreach ($name in ($suites.Keys | Sort-Object)) {
    $s = $suites[$name]
    Write-Output ("{0}: {1} tests, {2} skipped, {3} failures, {4} errors" -f $name, $s.tests, $s.skipped, $s.failures, $s.errors)
}
if ($problems.Count -gt 0) {
    $problems | ForEach-Object { Write-Output "PROBLEM $_" }
    exit 1
}
Write-Output "guiAcceptance OK: all required battle GUI suites ran -> $Out"

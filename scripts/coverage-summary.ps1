param(
    [string]$ResultsDir = 'build/test-results/test',
    [string]$Jacoco = 'build/reports/jacoco/test/jacocoTestReport.xml',
    [string]$Out = 'build/verification/test-summary.json',
    [double]$MinLine = 0.70
)
# CI 증거 요약 (S11-C2, R3-COVERAGE).
# JUnit XML과 JaCoCo XML을 읽어 테스트 수·실패·생략·라인 커버리지를 JSON 한 파일로 남긴다.
# XML이 없으면 0으로 채우지 않고 실패로 끝낸다. 임의 제외로 수치를 올리지 않는다: JaCoCo가 낸 값을 그대로 옮긴다.
$ErrorActionPreference = 'Stop'
$workspace = [IO.Path]::GetFullPath((Join-Path $PSScriptRoot '..'))
Set-Location -LiteralPath $workspace

function Resolve-InWorkspace([string]$relative) {
    $full = [IO.Path]::GetFullPath((Join-Path $workspace $relative))
    if (-not $full.StartsWith($workspace + [IO.Path]::DirectorySeparatorChar, [StringComparison]::OrdinalIgnoreCase)) {
        throw "Path must be inside this workspace: $relative"
    }
    return $full
}

$resultsPath = Resolve-InWorkspace $ResultsDir
$jacocoPath = Resolve-InWorkspace $Jacoco
$outPath = Resolve-InWorkspace $Out

if (-not (Test-Path -LiteralPath $resultsPath -PathType Container)) {
    Write-Error "테스트 결과 폴더가 없습니다: $resultsPath (먼저 .\gradlew.bat check 를 실행하세요)" -ErrorAction Continue
    exit 1
}
$xmlFiles = @(Get-ChildItem -LiteralPath $resultsPath -Filter 'TEST-*.xml' -File)
if ($xmlFiles.Count -eq 0) {
    Write-Error "테스트 결과 XML이 없습니다: $resultsPath (0으로 기록하지 않고 실패합니다)" -ErrorAction Continue
    exit 1
}
if (-not (Test-Path -LiteralPath $jacocoPath -PathType Leaf)) {
    Write-Error "JaCoCo XML이 없습니다: $jacocoPath (먼저 .\gradlew.bat check 를 실행하세요)" -ErrorAction Continue
    exit 1
}

$total = 0; $failed = 0; $skipped = 0; $suites = 0
foreach ($file in $xmlFiles) {
    [xml]$suite = Get-Content -LiteralPath $file.FullName -Raw -Encoding UTF8
    $root = $suite.testsuite
    if ($null -eq $root) { continue }
    $suites++
    $total += [int]$root.tests
    $failed += [int]$root.failures + [int]$root.errors
    $skipped += [int]$root.skipped
}

[xml]$coverage = Get-Content -LiteralPath $jacocoPath -Raw -Encoding UTF8
$lineCounter = @($coverage.report.counter | Where-Object { $_.type -eq 'LINE' })
if ($lineCounter.Count -eq 0) {
    Write-Error "JaCoCo XML에 LINE 카운터가 없습니다: $jacocoPath" -ErrorAction Continue
    exit 1
}
$lineCovered = [long]$lineCounter[0].covered
$lineMissed = [long]$lineCounter[0].missed
$lineTotal = $lineCovered + $lineMissed
$lineRatio = if ($lineTotal -gt 0) { [Math]::Round($lineCovered / $lineTotal, 4) } else { 0.0 }

$commit = 'unknown'
try {
    $commit = (& git rev-parse HEAD 2>$null)
    if ($LASTEXITCODE -ne 0 -or [string]::IsNullOrWhiteSpace($commit)) { $commit = 'unknown' }
} catch {
    $commit = 'unknown'
}
$commit = ([string]$commit).Trim()

$gatePassed = ($failed -eq 0 -and $lineRatio -ge $MinLine)
$summary = [ordered]@{
    commit       = $commit
    generatedAt  = [DateTime]::UtcNow.ToString('yyyy-MM-ddTHH:mm:ssZ')
    tests        = [ordered]@{
        total   = $total
        passed  = $total - $failed - $skipped
        failed  = $failed
        skipped = $skipped
        suites  = $suites
    }
    line         = [ordered]@{
        covered = $lineCovered
        total   = $lineTotal
        ratio   = $lineRatio
    }
    gateMinLine  = $MinLine
    gatePassed   = $gatePassed
    # 실제 두 PC·최소 사양 장비 검증은 CI로 대신할 수 없다. 통과와 별개로 남긴다
    notVerified  = @('실제 두 PC LAN 200ms', '최소 사양 장비(6-4·6-5)')
}

$outParent = [IO.Path]::GetDirectoryName($outPath)
if ($outParent -and -not (Test-Path -LiteralPath $outParent)) {
    New-Item -ItemType Directory -Path $outParent -Force | Out-Null
}
# BOM 없는 UTF-8로 쓴다. BOM이 있으면 JSON 소비자(Node·Python 기본 디코더)가 첫 글자에서 실패한다
$json = $summary | ConvertTo-Json -Depth 5
[IO.File]::WriteAllText($outPath, $json + [Environment]::NewLine, [Text.UTF8Encoding]::new($false))

Write-Host ("테스트 {0}개 (통과 {1}, 실패 {2}, 생략 {3}, 스위트 {4})" -f `
    $total, ($total - $failed - $skipped), $failed, $skipped, $suites)
Write-Host ("라인 커버리지 {0}/{1} = {2:P2} (게이트 {3:P0}, {4})" -f `
    $lineCovered, $lineTotal, $lineRatio, $MinLine, $(if ($gatePassed) { '통과' } else { '실패' }))
Write-Host "요약 파일: $outPath"
Write-Host "미검증: 실제 두 PC LAN 200ms, 최소 사양 장비(6-4·6-5)"

if (-not $gatePassed) {
    if ($failed -gt 0) { Write-Host "테스트 실패 $failed 건으로 종료 코드 1" }
    if ($lineRatio -lt $MinLine) { Write-Host "라인 커버리지가 게이트 미만이라 종료 코드 1" }
    exit 1
}
exit 0

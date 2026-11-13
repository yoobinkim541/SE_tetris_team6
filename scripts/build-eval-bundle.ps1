param(
    [switch]$SkipBuild,
    [switch]$AllowDirty,
    [string]$OutDir = 'build/eval-bundle'
)
# 평가 빌드 번들 (S12-C2, R3-COVERAGE·R3-DEMO).
# 고정 커밋의 ZIP/JAR·체크섬·검증 요약·실행 안내를 한 폴더로 묶고, 같은 폴더를 zip으로 압축한다.
# 커밋되지 않은 변경이 있으면 -AllowDirty 없이는 실행하지 않는다. challenge 브랜치에서는 실행하지 않는다.
$ErrorActionPreference = 'Stop'
$workspace = [IO.Path]::GetFullPath((Join-Path $PSScriptRoot '..'))
Set-Location -LiteralPath $workspace

function Fail([string]$message) {
    Write-Host "평가 번들 실패: $message"
    exit 1
}

# ── 1) 실행 조건 확인 ────────────────────────────────────────────────
$branch = (& git rev-parse --abbrev-ref HEAD 2>$null)
if ($LASTEXITCODE -ne 0) { Fail 'git 브랜치를 읽지 못했습니다. 저장소 루트에서 실행하세요.' }
$branch = ([string]$branch).Trim()
if ($branch -like 'challenge*' -or $branch -like '*/challenge*') {
    Fail "challenge 브랜치에서는 평가 번들을 만들지 않습니다: $branch"
}
$commit = ([string](& git rev-parse HEAD)).Trim()
if ($LASTEXITCODE -ne 0 -or $commit.Length -lt 7) { Fail 'git 커밋 해시를 읽지 못했습니다.' }
$shortCommit = $commit.Substring(0, 7)

$status = (& git status --porcelain)
if ($LASTEXITCODE -ne 0) { Fail 'git status를 읽지 못했습니다.' }
$dirty = -not [string]::IsNullOrWhiteSpace(($status -join ''))
if ($dirty -and -not $AllowDirty) {
    Write-Host '커밋되지 않은 변경이 있습니다. 고정 커밋 번들은 깨끗한 트리에서만 만듭니다.'
    $status | ForEach-Object { Write-Host "  $_" }
    Fail '변경을 커밋하거나, 의도한 것이면 -AllowDirty를 붙여 다시 실행하세요.'
}
if ($dirty) { Write-Host '경고: 커밋되지 않은 변경이 있는 상태로 번들을 만듭니다 (-AllowDirty). build-info.json에 dirty=true로 기록합니다.' }

# ── 2) 빌드 (필요하면) ───────────────────────────────────────────────
$zipPath = Join-Path $workspace 'build/distributions/SE-Tetris-windows.zip'
$jarPath = Join-Path $workspace 'build/libs/SE-Tetris.jar'
$summaryPath = Join-Path $workspace 'build/verification/test-summary.json'

if (-not $SkipBuild) {
    Write-Host '빌드: .\gradlew.bat check guiAcceptance releaseJar packageApp --no-daemon'
    & (Join-Path $workspace 'gradlew.bat') check guiAcceptance releaseJar packageApp --no-daemon
    if ($LASTEXITCODE -ne 0) { Fail "Gradle 빌드가 실패했습니다 (종료 코드 $LASTEXITCODE)." }
    Write-Host '검증 요약: scripts/coverage-summary.ps1'
    & powershell -NoProfile -File (Join-Path $workspace 'scripts/coverage-summary.ps1')
    if ($LASTEXITCODE -ne 0) { Fail "검증 요약이 실패했습니다 (종료 코드 $LASTEXITCODE). 테스트 실패나 커버리지 미달을 확인하세요." }
}

foreach ($artifact in @($zipPath, $jarPath, $summaryPath)) {
    if (-not (Test-Path -LiteralPath $artifact -PathType Leaf)) {
        Fail "산출물이 없습니다: $artifact (빌드 단계를 실행하거나 -SkipBuild를 빼고 다시 실행하세요)"
    }
}

# ── 3) 출력 폴더 ────────────────────────────────────────────────────
$outRoot = [IO.Path]::GetFullPath((Join-Path $workspace $OutDir))
if (-not $outRoot.StartsWith($workspace + [IO.Path]::DirectorySeparatorChar, [StringComparison]::OrdinalIgnoreCase)) {
    Fail "OutDir는 이 저장소 안이어야 합니다: $OutDir"
}
$folderName = (Get-Date).ToString('yyyyMMdd') + '-' + $shortCommit
$bundleDir = Join-Path $outRoot $folderName
New-Item -ItemType Directory -Path $bundleDir -Force | Out-Null

Copy-Item -LiteralPath $zipPath -Destination (Join-Path $bundleDir 'SE-Tetris-windows.zip') -Force
Copy-Item -LiteralPath $jarPath -Destination (Join-Path $bundleDir 'SE-Tetris.jar') -Force
Copy-Item -LiteralPath $summaryPath -Destination (Join-Path $bundleDir 'test-summary.json') -Force

# ── 4) 체크섬 ───────────────────────────────────────────────────────
$hashLines = foreach ($name in @('SE-Tetris-windows.zip', 'SE-Tetris.jar')) {
    $hash = Get-FileHash -LiteralPath (Join-Path $bundleDir $name) -Algorithm SHA256
    "$($hash.Hash.ToLowerInvariant())  $name"
}
[IO.File]::WriteAllText(
    (Join-Path $bundleDir 'SHA256SUMS.txt'),
    ($hashLines -join [Environment]::NewLine) + [Environment]::NewLine,
    [Text.UTF8Encoding]::new($false))

# ── 5) build-info.json ──────────────────────────────────────────────
$javaLine = 'unknown'
try {
    # `java -version`는 표준 오류로 쓴다. PowerShell이 네이티브 stderr를 오류로 승격하거나 삼키므로
    # 임시 파일로 리다이렉션해 첫 줄만 읽는다
    $versionFile = Join-Path ([IO.Path]::GetTempPath()) ('se-tetris-java-' + [Guid]::NewGuid().ToString('N') + '.txt')
    & cmd /c ("java -version > `"" + $versionFile + "`" 2>&1") | Out-Null
    if (Test-Path -LiteralPath $versionFile) {
        $first = (Get-Content -LiteralPath $versionFile -TotalCount 3 |
            Where-Object { $_.Trim() } | Select-Object -First 1)
        if ($first) { $javaLine = ([string]$first).Trim() }
        Remove-Item -LiteralPath $versionFile -Force -ErrorAction SilentlyContinue
    }
} catch {
    $javaLine = 'unknown'
}
$gradleVersion = 'unknown'
try {
    $match = Select-String -LiteralPath (Join-Path $workspace 'gradle/wrapper/gradle-wrapper.properties') `
        -Pattern 'distributionUrl=.*gradle-([0-9.]+)-' -ErrorAction Stop | Select-Object -First 1
    if ($match -and $match.Matches.Count -gt 0) { $gradleVersion = $match.Matches[0].Groups[1].Value }
} catch {
    $gradleVersion = 'unknown'
}

$buildInfo = [ordered]@{
    commit      = $commit
    shortCommit = $shortCommit
    branch      = $branch
    dirty       = $dirty
    javaVersion = $javaLine
    gradle      = $gradleVersion
    generatedAt = [DateTime]::UtcNow.ToString('yyyy-MM-ddTHH:mm:ssZ')
}
$buildInfoJson = $buildInfo | ConvertTo-Json -Depth 3
# BOM 없는 UTF-8. BOM이 있으면 Node·Python 기본 디코더가 첫 글자에서 실패한다
[IO.File]::WriteAllText(
    (Join-Path $bundleDir 'build-info.json'),
    $buildInfoJson + [Environment]::NewLine,
    [Text.UTF8Encoding]::new($false))

# ── 6) README.txt ──────────────────────────────────────────────────
$readme = @(
    "SE Tetris 평가 빌드 $shortCommit ($branch)",
    '1. SE-Tetris-windows.zip 압축을 풀고 "SE Tetris\SE Tetris.exe"를 실행합니다 (JRE 포함, Java 설치 불필요).',
    '2. 두 PC를 같은 서브넷에 두고 호스트가 대전 대기를 열면, 클라이언트가 호스트 IP로 접속합니다.',
    '3. 네트워크 절차·장애 대응·방화벽 예외는 docs/req3/evaluation-network-guide.md 를 따릅니다.',
    '',
    "체크섬: SHA256SUMS.txt (Get-FileHash -Algorithm SHA256 로 재계산해 비교)",
    "빌드 정보: build-info.json / 검증 요약: test-summary.json",
    '이 번들은 테스트·커버리지·패키지 산출물 증거이며, 실제 두 PC LAN 200ms 측정을 대신하지 않습니다.'
)
[IO.File]::WriteAllText(
    (Join-Path $bundleDir 'README.txt'),
    ($readme -join [Environment]::NewLine) + [Environment]::NewLine,
    [Text.UTF8Encoding]::new($false))

# ── 7) 폴더 zip ────────────────────────────────────────────────────
$zipOut = Join-Path $outRoot ($folderName + '.zip')
if (Test-Path -LiteralPath $zipOut) { Remove-Item -LiteralPath $zipOut -Force }
try {
    # Compress-Archive는 cmdlet이라 $LASTEXITCODE를 갱신하지 않는다. 실패는 예외로만 판정한다
    Compress-Archive -LiteralPath (Get-ChildItem -LiteralPath $bundleDir -File | ForEach-Object { $_.FullName }) `
        -DestinationPath $zipOut -CompressionLevel Optimal -ErrorAction Stop
} catch {
    Fail "번들 zip 압축에 실패했습니다: $($_.Exception.Message)"
}
if (-not (Test-Path -LiteralPath $zipOut -PathType Leaf)) { Fail "번들 zip이 만들어지지 않았습니다: $zipOut" }

Write-Host ''
Write-Host "평가 번들 폴더: $bundleDir"
Get-ChildItem -LiteralPath $bundleDir -File | Sort-Object Name | ForEach-Object {
    Write-Host ("  {0}  {1:N0} bytes" -f $_.Name, $_.Length)
}
Write-Host "번들 zip: $zipOut"
Write-Host "미검증: 실제 두 PC LAN 200ms, 최소 사양 장비(6-4·6-5), 패키지 두 PC 실행"
exit 0

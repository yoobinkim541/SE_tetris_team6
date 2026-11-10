param(
    [ValidateSet('Add', 'Remove', 'Show')][string]$Action = 'Add',
    [ValidateRange(0, 65535)][int]$Port = 47123,
    [string]$Program = '',
    [switch]$Apply
)
# 평가장 P2P 인바운드 예외 (S12-C1, R3-DEMO).
# 기본 동작은 **실행할 명령을 출력만** 하는 dry run이다. 실제 규칙 변경은 -Apply를 줄 때만 일어난다.
# 방화벽 전체를 끄는 명령은 이 스크립트 어디에도 없다. 필요한 TCP 포트(또는 앱) 하나만 LocalSubnet 범위로 연다.
$ErrorActionPreference = 'Stop'

if ($Port -lt 1) {
    Write-Error "포트는 1~65535여야 합니다: $Port" -ErrorAction Continue
    exit 1
}

$displayName = "SE Tetris P2P (TCP $Port)"
$profiles = 'Private'
$remoteAddress = 'LocalSubnet'
$allowedProgram = $null
if ($Program) {
    if (-not (Test-Path -LiteralPath $Program -PathType Leaf)) {
        Write-Error "앱 경로가 없습니다: $Program" -ErrorAction Continue
        exit 1
    }
    $allowedProgram = [IO.Path]::GetFullPath($Program)
}

function Quote([string]$value) {
    return "'" + ($value -replace "'", "''") + "'"
}

$plan = [System.Collections.Generic.List[string]]::new()
switch ($Action) {
    'Add' {
        $command = "New-NetFirewallRule -DisplayName $(Quote $displayName)" +
            " -Direction Inbound -Protocol TCP -LocalPort $Port" +
            " -Profile $profiles -RemoteAddress $remoteAddress -Action Allow"
        if ($allowedProgram) { $command += " -Program $(Quote $allowedProgram)" }
        $plan.Add($command)
    }
    'Remove' {
        $plan.Add("Remove-NetFirewallRule -DisplayName $(Quote $displayName) -ErrorAction SilentlyContinue")
    }
    'Show' {
        $plan.Add("Get-NetFirewallRule -DisplayName $(Quote $displayName) | Select-Object DisplayName,Enabled,Direction,Action")
    }
}

Write-Host "== SE Tetris 평가장 방화벽 예외 (S12-C1) =="
Write-Host "대상 규칙: $displayName"
Write-Host "범위: 인바운드 TCP $Port, 프로필 $profiles, 원격 주소 $remoteAddress (로컬 서브넷만)"
if ($allowedProgram) { Write-Host "앱: $allowedProgram" }
Write-Host "주의: 방화벽 전체 해제(Disable-NetFirewallProfile 등)는 사용하지 않습니다. 필요한 포트/앱만 엽니다."
Write-Host ""

if (-not $Apply) {
    Write-Host "다음 명령을 실행할 예정입니다 (dry run, 실제 변경 없음):"
    foreach ($line in $plan) { Write-Host "  $line" }
    Write-Host ""
    Write-Host "실제로 적용하려면 관리자 PowerShell에서 -Apply를 붙여 다시 실행하세요:"
    $programHint = if ($Program) { ' -Program "' + $Program + '"' } else { '' }
    Write-Host "  powershell -File scripts/firewall-rule.ps1 -Action $Action -Port $Port$programHint -Apply"
    exit 0
}

# -Apply 이후에만 실행된다
$identity = [Security.Principal.WindowsIdentity]::GetCurrent()
$isAdmin = ([Security.Principal.WindowsPrincipal]::new($identity)).IsInRole(
    [Security.Principal.WindowsBuiltInRole]::Administrator)
if (-not $isAdmin) {
    Write-Error "관리자 권한이 필요합니다. 관리자 PowerShell에서 다시 실행하세요." -ErrorAction Continue
    exit 1
}
if (-not (Get-Command New-NetFirewallRule -ErrorAction SilentlyContinue)) {
    Write-Error "NetSecurity 모듈을 찾지 못했습니다. Windows PowerShell에서 실행하세요." -ErrorAction Continue
    exit 1
}

try {
    switch ($Action) {
        'Add' {
            $existing = Get-NetFirewallRule -DisplayName $displayName -ErrorAction SilentlyContinue
            if ($existing) {
                Write-Host "같은 이름의 규칙이 이미 있습니다. 새로 만들지 않습니다: $displayName"
            } else {
                $params = @{
                    DisplayName   = $displayName
                    Direction     = 'Inbound'
                    Protocol      = 'TCP'
                    LocalPort     = $Port
                    Profile       = $profiles
                    RemoteAddress = $remoteAddress
                    Action        = 'Allow'
                    Description   = 'SE Tetris P2P: 로컬 서브넷만 허용 (평가장)'
                }
                if ($allowedProgram) { $params['Program'] = $allowedProgram }
                New-NetFirewallRule @params | Out-Null
                Write-Host "규칙을 추가했습니다: $displayName"
            }
        }
        'Remove' {
            Remove-NetFirewallRule -DisplayName $displayName -ErrorAction SilentlyContinue
            Write-Host "규칙을 제거했습니다(없으면 그대로): $displayName"
        }
        'Show' {
            $found = @(Get-NetFirewallRule -DisplayName $displayName -ErrorAction SilentlyContinue)
            if ($found.Count -eq 0) {
                Write-Host "규칙이 없습니다: $displayName"
            } else {
                $found | Select-Object DisplayName, Enabled, Direction, Action | Format-Table -AutoSize | Out-String | Write-Host
            }
        }
    }
} catch {
    Write-Error "방화벽 규칙 처리에 실패했습니다: $($_.Exception.Message)" -ErrorAction Continue
    exit 1
}
exit 0

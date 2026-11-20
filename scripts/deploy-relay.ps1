param(
    [string]$Target = 'opc@158.179.174.70',
    [string]$Key = (Join-Path $HOME '.ssh\tetris_relay'),
    [ValidateRange(1, 65535)][int]$Port = 47123,
    [switch]$SkipBuild
)
# challenge: 인터넷 대전 중계 서버를 Oracle Cloud VM에 설치·갱신한다.
# 1) releaseJar 빌드와 Linux용 Java 21 JRE(Temurin) 받기  2) JAR·JRE·systemd 유닛 업로드
# 3) VM 준비(스왑, JRE 풀기, 전용 사용자)와 서비스 재시작
# 메모리 약 500MB VM에서는 dnf로 Java를 깔다가 VM이 멈췄다. 그래서 JRE 압축 파일을 이 PC에서 받아 올리고 풀기만 한다.
# 4) 이 PC에서 "HOST v1"을 보내 "ROOM 코드"가 오는지 확인한다.
# VM 방화벽(firewall-cmd)과 Oracle 보안 목록의 TCP $Port 허용은 한 번만 하는 설정이라 여기서 바꾸지 않는다.
$ErrorActionPreference = 'Stop'
$root = Split-Path -Parent $PSScriptRoot
$jar = Join-Path $root 'build\libs\SE-Tetris.jar'
$unit = Join-Path $PSScriptRoot 'tetris-relay.service'
$jre = Join-Path $root 'build\relay-jre\jre21-linux-x64.tar.gz'
$jreUrl = 'https://api.adoptium.net/v3/binary/latest/21/ga/linux/x64/jre/hotspot/normal/eclipse'
$jreInfoUrl = 'https://api.adoptium.net/v3/assets/latest/21/hotspot?architecture=x64&image_type=jre&os=linux&vendor=eclipse'
$sshOptions = @('-i', $Key, '-o', 'BatchMode=yes', '-o', 'ConnectTimeout=30', '-o', 'ServerAliveInterval=15')

if (-not (Test-Path -LiteralPath $Key)) {
    Write-Error "SSH 키가 없습니다: $Key" -ErrorAction Continue
    exit 1
}
if (-not $SkipBuild) {
    Write-Host '== 1/4 releaseJar 빌드 =='
    Push-Location $root
    try {
        & (Join-Path $root 'gradlew.bat') releaseJar --no-daemon -q
        if ($LASTEXITCODE -ne 0) { throw "releaseJar 실패 (exit $LASTEXITCODE)" }
    } finally {
        Pop-Location
    }
}
if (-not (Test-Path -LiteralPath $jar)) {
    Write-Error "JAR가 없습니다: $jar" -ErrorAction Continue
    exit 1
}

if (-not (Test-Path -LiteralPath $jre)) {
    Write-Host '== Linux용 Java 21 JRE 받기 (한 번만) =='
    New-Item -ItemType Directory -Force -Path (Split-Path -Parent $jre) | Out-Null
    [Net.ServicePointManager]::SecurityProtocol = [Net.SecurityProtocolType]::Tls12
    $expected = (Invoke-RestMethod -Uri $jreInfoUrl)[0].binary.package.checksum
    Invoke-WebRequest -Uri $jreUrl -OutFile $jre -UseBasicParsing
    $actual = (Get-FileHash -LiteralPath $jre -Algorithm SHA256).Hash.ToLower()
    if ($actual -ne $expected) {
        Remove-Item -LiteralPath $jre
        Write-Error "JRE 체크섬이 다릅니다: $actual (기대 $expected)" -ErrorAction Continue
        exit 1
    }
}

Write-Host "== 2/4 업로드: $Target =="
& scp @sshOptions $jar $unit $jre "${Target}:/tmp/"
if ($LASTEXITCODE -ne 0) { Write-Error "업로드 실패 (exit $LASTEXITCODE)" -ErrorAction Continue; exit 1 }

Write-Host '== 3/4 VM 준비와 서비스 재시작 =='
# 원격 셸 스크립트는 ASCII만 쓴다. 하는 일: 스왑이 없으면 1GB 추가,
# 메모리를 많이 쓰는 백그라운드 작업 끄기(dnf 캐시 자동 갱신 타이머와 PCP 성능 기록. 부팅 약 1시간 뒤 dnf 캐시 갱신 때 VM이 멈췄다.
# 손으로 하는 dnf 설치와 Oracle 모니터링 에이전트는 그대로 둔다), JRE 풀기(새 폴더에 푼 뒤 바꿔 끼움),
# 전용 사용자, 예전 SSH 역방향 터널(sshd)이 포트를 잡고 있으면 정리, JAR·유닛 설치(Windows 체크아웃의 CRLF 제거), 재시작, 포트 확인
$remote = @'
set -euo pipefail
if ! swapon --show | grep -q .; then
  sudo fallocate -l 1G /swapfile
  sudo chmod 600 /swapfile
  sudo mkswap /swapfile >/dev/null
  sudo swapon /swapfile
  grep -q '^/swapfile ' /etc/fstab || echo '/swapfile none swap sw 0 0' | sudo tee -a /etc/fstab >/dev/null
  echo "swap: added 1G"
fi
for unit in dnf-makecache.timer pmcd pmlogger pmie pmproxy pmie_check.timer pmie_farm_check.timer \
            pmlogger_check.timer pmlogger_farm_check.timer pmie_daily.timer pmlogger_daily.timer; do
  sudo systemctl disable --now "$unit" >/dev/null 2>&1 || true
done
sudo rm -rf /opt/tetris-relay/jre.new
sudo mkdir -p /opt/tetris-relay/jre.new
sudo tar -xzf /tmp/jre21-linux-x64.tar.gz -C /opt/tetris-relay/jre.new --strip-components=1
sudo /opt/tetris-relay/jre.new/bin/java -version 2>&1 | head -1
sudo rm -rf /opt/tetris-relay/jre.old
if [ -d /opt/tetris-relay/jre ]; then sudo mv /opt/tetris-relay/jre /opt/tetris-relay/jre.old; fi
sudo mv /opt/tetris-relay/jre.new /opt/tetris-relay/jre
sudo rm -rf /opt/tetris-relay/jre.old
id tetris-relay >/dev/null 2>&1 || sudo useradd --system --no-create-home --shell /sbin/nologin tetris-relay
for pid in $(sudo ss -ltnpH "( sport = :__PORT__ )" | grep -o 'sshd[^)]*pid=[0-9]*' | grep -o 'pid=[0-9]*' | cut -d= -f2 | sort -u); do
  sudo kill "$pid" || true
done
sudo install -D -m 0644 /tmp/SE-Tetris.jar /opt/tetris-relay/relay.jar
sed -i 's/\r$//' /tmp/tetris-relay.service
sudo install -m 0644 /tmp/tetris-relay.service /etc/systemd/system/tetris-relay.service
rm -f /tmp/SE-Tetris.jar /tmp/tetris-relay.service /tmp/jre21-linux-x64.tar.gz
sudo systemctl daemon-reload
sudo systemctl enable tetris-relay >/dev/null 2>&1
sudo systemctl restart tetris-relay
for i in $(seq 1 30); do
  if sudo ss -ltnH "( sport = :__PORT__ )" | grep -q .; then break; fi
  sleep 1
done
systemctl is-active tetris-relay
sudo ss -ltnH "( sport = :__PORT__ )"
'@
$remote = ($remote -replace '__PORT__', $Port) -replace "`r", ''
# 파이프 대신 base64로 넘겨 줄 끝 문자(CR) 섞임을 막는다
$encoded = [Convert]::ToBase64String([Text.Encoding]::ASCII.GetBytes($remote))
& ssh @sshOptions $Target "echo $encoded | base64 -d | bash"
if ($LASTEXITCODE -ne 0) { Write-Error "VM 준비 실패 (exit $LASTEXITCODE)" -ErrorAction Continue; exit 1 }

Write-Host '== 4/4 이 PC에서 방 만들기 확인 =='
$address = ($Target -split '@')[-1]
$client = [Net.Sockets.TcpClient]::new()
try {
    if (-not $client.ConnectAsync($address, $Port).Wait(10000)) { throw "접속 시간 초과: ${address}:$Port" }
    $stream = $client.GetStream()
    $stream.ReadTimeout = 10000
    $request = [Text.Encoding]::UTF8.GetBytes("HOST v1`n")
    $stream.Write($request, 0, $request.Length)
    $reply = [IO.StreamReader]::new($stream, [Text.Encoding]::UTF8).ReadLine()
    if ($reply -notmatch '^ROOM [A-Z2-9]{6}$') { throw "예상과 다른 응답: $reply" }
    Write-Host "확인 완료: ${address}:$Port 가 '$reply' 로 응답했습니다. (이 방은 연결을 닫으면 바로 사라집니다)"
} catch {
    Write-Error "중계 서버 확인 실패: $($_.Exception.Message)" -ErrorAction Continue
    exit 1
} finally {
    $client.Close()
}
exit 0

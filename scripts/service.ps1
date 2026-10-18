# core-java と ai-python を Windows サービス（NSSM）として常時起動する。
# PC 起動時に自動で立ち上がり、落ちたら 10 秒後に再起動する。ログインしていなくても動く。
#
#   .\scripts\service.ps1 install    # jar をビルドしてサービス登録 → 起動（要: 管理者）
#   .\scripts\service.ps1 update     # コードを変えたあと: 停止 → jar を再ビルド → 起動（要: 管理者）
#   .\scripts\service.ps1 restart    # 再起動（学習し直したモデルを ai-python に読ませるときなど）（要: 管理者）
#   .\scripts\service.ps1 stop | start
#   .\scripts\service.ps1 status
#   .\scripts\service.ps1 uninstall  # 停止してサービス登録を消す（要: 管理者）
param(
    [Parameter(Mandatory = $true)]
    [ValidateSet('install', 'update', 'start', 'stop', 'restart', 'status', 'uninstall')]
    [string]$Action
)

$ErrorActionPreference = 'Stop'

$RepoDir = Split-Path -Parent $PSScriptRoot
$CoreDir = Join-Path $RepoDir 'core-java'
$AiDir = Join-Path $RepoDir 'ai-python'
$LogDir = Join-Path $RepoDir 'logs'
# bootJar の出力をそのまま起動すると、実行中はロックされて再ビルドできないので別名でコピーして使う
$ServiceJar = Join-Path $CoreDir 'build\service\core-java.jar'

$AiService = 'vase-ai-python'
$CoreService = 'vase-core-java'
# 起動は AI → core の順、停止は core → AI の順
$Services = @($AiService, $CoreService)

function Find-Nssm {
    $packages = Join-Path $env:LOCALAPPDATA 'Microsoft\WinGet\Packages'
    $exe = Get-ChildItem $packages -Filter 'nssm.exe' -Recurse -ErrorAction SilentlyContinue |
        Where-Object { $_.FullName -match '\\win64\\' } | Select-Object -First 1
    if ($exe) { return $exe.FullName }
    $cmd = Get-Command nssm -ErrorAction SilentlyContinue
    if ($cmd) { return $cmd.Source }
    throw 'nssm が見つからない。winget install NSSM.NSSM でインストールする'
}

function Assert-Admin {
    $principal = [Security.Principal.WindowsPrincipal][Security.Principal.WindowsIdentity]::GetCurrent()
    if (-not $principal.IsInRole([Security.Principal.WindowsBuiltInRole]::Administrator)) {
        throw "$Action は管理者権限が必要。PowerShell を「管理者として実行」で開き直す"
    }
}

function Test-Installed([string]$name) {
    return [bool](Get-Service $name -ErrorAction SilentlyContinue)
}

function Build-Jar {
    Push-Location $CoreDir
    try {
        & .\gradlew.bat bootJar -q
        if ($LASTEXITCODE -ne 0) { throw 'core-java のビルドに失敗' }
    } finally {
        Pop-Location
    }
    $jar = Get-ChildItem (Join-Path $CoreDir 'build\libs') -Filter '*.jar' |
        Where-Object { $_.Name -notlike '*-plain.jar' } | Select-Object -First 1
    New-Item -ItemType Directory -Force (Split-Path $ServiceJar) | Out-Null
    Copy-Item $jar.FullName $ServiceJar -Force
}

function Set-Common([string]$nssm, [string]$name, [string]$description, [string]$logName) {
    & $nssm set $name Description $description | Out-Null
    # ネットワークが上がってから起動する
    & $nssm set $name Start SERVICE_DELAYED_AUTO_START | Out-Null
    & $nssm set $name AppExit Default Restart | Out-Null
    & $nssm set $name AppRestartDelay 10000 | Out-Null
    & $nssm set $name AppStdout (Join-Path $LogDir "$logName.out.log") | Out-Null
    & $nssm set $name AppStderr (Join-Path $LogDir "$logName.err.log") | Out-Null
    & $nssm set $name AppRotateFiles 1 | Out-Null
    & $nssm set $name AppRotateOnline 1 | Out-Null
    & $nssm set $name AppRotateBytes 10485760 | Out-Null
}

function Install-Services {
    $nssm = Find-Nssm
    $python = Join-Path $AiDir '.venv\Scripts\python.exe'
    if (-not (Test-Path $python)) { throw "$python がない。ai-python/README.md のセットアップを先に行う" }
    $java = (Get-Command java -ErrorAction SilentlyContinue).Source
    if (-not $java) { throw 'java が見つからない（JDK 21 をインストールする）' }
    if (-not (Test-Path (Join-Path $RepoDir '.env'))) { throw '.env がない。.env.example をコピーして作る' }

    New-Item -ItemType Directory -Force $LogDir | Out-Null
    Build-Jar

    if (-not (Test-Installed $AiService)) {
        & $nssm install $AiService $python '-m uvicorn app.main:app --host 127.0.0.1 --port 8000' | Out-Null
    }
    & $nssm set $AiService AppDirectory $AiDir | Out-Null
    Set-Common $nssm $AiService 'vase-premium: 反発確率の推論 API（ai-python）' 'ai-python'

    if (-not (Test-Installed $CoreService)) {
        & $nssm install $CoreService $java "-jar `"$ServiceJar`"" | Out-Null
    }
    # .env や DB の相対パス（../.env, ../data/trade.db）は core-java/ を基準に解決される
    & $nssm set $CoreService AppDirectory $CoreDir | Out-Null
    Set-Common $nssm $CoreService 'vase-premium: 足の取得・ルール検知・Discord 通知（core-java）' 'core-java'

    Start-All
}

function Start-All {
    foreach ($s in $Services) { Start-Service $s }
}

function Stop-All {
    $reversed = $Services.Clone()
    [array]::Reverse($reversed)
    foreach ($s in $reversed) {
        if (Test-Installed $s) { Stop-Service $s -ErrorAction SilentlyContinue }
    }
}

function Uninstall-Services {
    $nssm = Find-Nssm
    Stop-All
    foreach ($s in $Services) {
        if (Test-Installed $s) { & $nssm remove $s confirm | Out-Null }
    }
}

function Show-Status {
    foreach ($s in $Services) {
        $svc = Get-Service $s -ErrorAction SilentlyContinue
        if ($svc) { '{0,-16} {1}' -f $s, $svc.Status } else { '{0,-16} 未登録' -f $s }
    }
    try {
        $health = Invoke-RestMethod 'http://127.0.0.1:8000/health' -TimeoutSec 3
        "ai-python /health: $($health | ConvertTo-Json -Compress)"
    } catch {
        'ai-python /health: 応答なし'
    }
}

if ($Action -ne 'status') { Assert-Admin }

switch ($Action) {
    'install' { Install-Services }
    'update' { Stop-All; Build-Jar; Start-All }
    'start' { Start-All }
    'stop' { Stop-All }
    'restart' { Stop-All; Start-All }
    'uninstall' { Uninstall-Services }
}
Show-Status

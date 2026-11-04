[CmdletBinding()]
param(
    [ValidateSet('Dev', 'Server', 'Client', 'Infra', 'Check', 'Docker', 'Sync')]
    [string]$Mode = 'Dev'
)

$ErrorActionPreference = 'Stop'
$projectRoot = Split-Path -Parent $PSScriptRoot
$toolsDirectory = Join-Path $projectRoot '.tools'
$rootEnvironmentPath = Join-Path $projectRoot '.env'
$previousEnvironment = @{}

function Set-ProcessSetting([string]$Name, [AllowNull()][string]$Value) {
    if (-not $previousEnvironment.ContainsKey($Name)) {
        $previousEnvironment[$Name] = [Environment]::GetEnvironmentVariable($Name, 'Process')
    }
    [Environment]::SetEnvironmentVariable($Name, $Value, 'Process')
}

function Read-LiteralEnvironment([string]$Path) {
    if (-not (Test-Path -LiteralPath $Path -PathType Leaf)) {
        throw 'Missing root .env. Copy .env.example to .env and fill in the local settings first.'
    }
    $settings = @{}
    foreach ($line in [System.IO.File]::ReadAllLines($Path)) {
        if ([string]::IsNullOrWhiteSpace($line) -or $line.TrimStart().StartsWith('#')) { continue }
        if ($line -notmatch '^\s*([A-Za-z_][A-Za-z0-9_]*)\s*=(.*)$') {
            throw 'Invalid root .env entry. Use literal KEY=value entries, one per line.'
        }
        $settingName = $Matches[1]
        $settingValue = $Matches[2]
        if ($settings.ContainsKey($settingName)) { throw "Duplicate root .env key: $settingName." }
        if ($settingValue.Length -ge 2) {
            $firstCharacter = $settingValue.Substring(0, 1)
            $lastCharacter = $settingValue.Substring($settingValue.Length - 1, 1)
            if (($firstCharacter -eq '"' -and $lastCharacter -eq '"') -or
                ($firstCharacter -eq "'" -and $lastCharacter -eq "'")) {
                $settingValue = $settingValue.Substring(1, $settingValue.Length - 2)
            }
        }
        # Values stay literal, including $, #, =, semicolons and backticks.
        $settings[$settingName] = $settingValue
    }
    return $settings
}

function Get-Setting([hashtable]$Settings, [string]$Name, [string]$Default) {
    if ($Settings.ContainsKey($Name) -and -not [string]::IsNullOrWhiteSpace($Settings[$Name])) {
        return [string]$Settings[$Name]
    }
    return $Default
}

function Get-PortSetting([hashtable]$Settings, [string]$Name, [int]$Default) {
    $portText = Get-Setting $Settings $Name ([string]$Default)
    if ($portText -notmatch '^[0-9]{1,5}$' -or [int]$portText -lt 1 -or [int]$portText -gt 65535) {
        throw "$Name must be a port from 1 to 65535 in root .env."
    }
    return [int]$portText
}

function Require-Success([string]$Operation, [int]$ExitCode = $LASTEXITCODE) {
    if ($ExitCode -ne 0) { throw "$Operation failed (exit code $ExitCode)." }
}

function Get-ToolCandidates([string]$PreferredRelativePath, [string]$DirectoryPattern,
    [string]$RelativeExecutable, [string[]]$CommandNames, [string[]]$AdditionalPaths = @()) {
    $candidates = [System.Collections.Generic.List[string]]::new()
    $candidates.Add((Join-Path $toolsDirectory $PreferredRelativePath))
    if (Test-Path -LiteralPath $toolsDirectory -PathType Container) {
        foreach ($directory in Get-ChildItem -LiteralPath $toolsDirectory -Directory | Where-Object Name -Like $DirectoryPattern) {
            $candidates.Add((Join-Path $directory.FullName $RelativeExecutable))
        }
    }
    foreach ($candidatePath in $AdditionalPaths) {
        if (-not [string]::IsNullOrWhiteSpace($candidatePath)) { $candidates.Add($candidatePath) }
    }
    foreach ($commandName in $CommandNames) {
        foreach ($command in @(Get-Command $commandName -CommandType Application -All -ErrorAction SilentlyContinue)) {
            $candidates.Add($command.Source)
        }
    }
    return @($candidates | Select-Object -Unique)
}

function Select-VersionedTool([string]$Label, [string[]]$Candidates, [string]$VersionPattern) {
    foreach ($candidatePath in $Candidates) {
        if (-not (Test-Path -LiteralPath $candidatePath -PathType Leaf)) { continue }
        try {
            $versionLines = @(& $candidatePath --version 2>&1)
            $versionExitCode = $LASTEXITCODE
            if ($versionExitCode -eq 0 -and (($versionLines -join "`n") -match $VersionPattern)) {
                return [pscustomobject]@{ Path = $candidatePath; VersionLines = $versionLines }
            }
        } catch { }
    }
    throw "$Label was not found with a supported version. Install it on PATH, or place its toolchain in .tools."
}

function Initialize-LocalToolchain {
    $javaHomeCandidate = if ($env:JAVA_HOME) { Join-Path $env:JAVA_HOME 'bin/java.exe' } else { $null }
    $javaCandidates = Get-ToolCandidates 'jdk-21/bin/java.exe' 'jdk-21*' 'bin/java.exe' @('java.exe', 'java') @($javaHomeCandidate)
    $script:javaTool = Select-VersionedTool 'JDK 21' $javaCandidates '(?m)^(?:openjdk|java) 21(?:\.|\s)'
    $jdkDirectory = Split-Path -Parent (Split-Path -Parent $javaTool.Path)
    if (-not (Test-Path -LiteralPath (Join-Path $jdkDirectory 'bin/javac.exe') -PathType Leaf) -and
        -not (Test-Path -LiteralPath (Join-Path $jdkDirectory 'bin/javac') -PathType Leaf)) {
        throw 'The selected Java executable is not in a JDK directory. Set JAVA_HOME to an installed JDK 21.'
    }
    Set-ProcessSetting 'JAVA_HOME' $jdkDirectory
    Set-ProcessSetting 'PATH' "$(Split-Path -Parent $javaTool.Path);$env:PATH"
    $nodeCandidates = Get-ToolCandidates 'node-v21.7.3-win-x64/node.exe' 'node-v21.7*-win-x64' 'node.exe' @('node.exe', 'node')
    $script:nodeTool = Select-VersionedTool 'Node.js 21.7.x' $nodeCandidates '^v21\.7\.'
    $nodeDirectory = Split-Path -Parent $nodeTool.Path
    $script:npmCliPath = Join-Path $nodeDirectory 'node_modules/npm/bin/npm-cli.js'
    if (-not (Test-Path -LiteralPath $npmCliPath -PathType Leaf)) {
        throw 'The selected Node.js installation must include its bundled npm package.'
    }
    Set-ProcessSetting 'PATH' "$nodeDirectory;$env:PATH"
    Set-ProcessSetting 'npm_config_prefix' $nodeDirectory
    $mavenCandidates = Get-ToolCandidates 'apache-maven-3.9.11/bin/mvn.cmd' 'apache-maven-*' 'bin/mvn.cmd' @('mvn.cmd', 'mvn')
    $script:mavenTool = Select-VersionedTool 'Maven 3.9.x' $mavenCandidates '(?m)^Apache Maven 3\.9\.'
    Set-ProcessSetting 'PATH' "$(Split-Path -Parent $mavenTool.Path);$env:PATH"
}

function Invoke-Npm([string[]]$Arguments, [string]$Operation) {
    & $nodeTool.Path $npmCliPath @Arguments
    $npmExitCode = $LASTEXITCODE
    Require-Success $Operation $npmExitCode
}

function Assert-Docker {
    $dockerCommands = @(Get-Command docker -CommandType Application -ErrorAction SilentlyContinue)
    if ($dockerCommands.Count -eq 0) { throw 'Docker is missing from PATH. Start Docker Desktop with Linux containers.' }
    $script:dockerExecutable = $dockerCommands[0].Source
    $dockerOperatingSystems = @(& $dockerExecutable info --format '{{.OSType}}')
    $dockerInfoExitCode = $LASTEXITCODE
    Require-Success 'Connect to Docker Desktop' $dockerInfoExitCode
    if ($dockerOperatingSystems.Count -eq 0 -or $dockerOperatingSystems[0].Trim() -ne 'linux') {
        throw 'Switch Docker Desktop to Linux containers before launching.'
    }
}

function Get-ServiceState([string]$ServiceName) {
    # Capture native output completely before indexing: never cut a CLI pipeline short.
    $containerIds = @(& $dockerExecutable compose --env-file $rootEnvironmentPath ps -aq $ServiceName)
    $composeInspectionExitCode = $LASTEXITCODE
    Require-Success "Inspect Compose service $ServiceName" $composeInspectionExitCode
    if ($containerIds.Count -eq 0) { return $null }
    $containerId = ([string]$containerIds[0]).Trim()
    if (-not $containerId) { return $null }
    $stateJson = & $dockerExecutable inspect --format '{{json .State}}' $containerId
    $containerInspectionExitCode = $LASTEXITCODE
    Require-Success "Inspect container state for $ServiceName" $containerInspectionExitCode
    return ($stateJson | ConvertFrom-Json)
}

function Start-Infrastructure {
    Assert-Docker
    & $dockerExecutable compose --env-file $rootEnvironmentPath up -d sqlserver-init mongodb
    $composeStartExitCode = $LASTEXITCODE
    Require-Success 'Start project SQL Server, database initialization and MongoDB' $composeStartExitCode
    $readinessDeadline = (Get-Date).AddSeconds(180)
    while ((Get-Date) -lt $readinessDeadline) {
        $sqlState = Get-ServiceState 'sqlserver'
        $initializationState = Get-ServiceState 'sqlserver-init'
        $mongoState = Get-ServiceState 'mongodb'
        if ($initializationState -and $initializationState.Status -eq 'exited' -and $initializationState.ExitCode -ne 0) {
            throw 'Project SQL database initialization failed. Check docker compose logs sqlserver-init.'
        }
        if ($sqlState -and $sqlState.Health.Status -eq 'healthy' -and
            $initializationState -and $initializationState.Status -eq 'exited' -and $initializationState.ExitCode -eq 0 -and
            $mongoState -and $mongoState.Health.Status -eq 'healthy') {
            Write-Host "Infrastructure ready: project SQL Server on localhost:$sqlPort, MongoDB on localhost:$mongoPort."
            return
        }
        Start-Sleep -Seconds 2
    }
    throw 'Project databases were not ready within 180 seconds. Check docker compose ps -a and database logs.'
}

function Show-HttpHealth([string]$Label, [string]$Url) {
    try {
        $result = Invoke-RestMethod -Uri $Url -TimeoutSec 10
        $healthStatus = if ($result.data.status) { $result.data.status } elseif ($result.status) { $result.status } else { 'reachable' }
        Write-Host "$Label`: $healthStatus ($Url)"
    } catch { Write-Host "$Label`: unavailable ($Url)" }
}

function Write-EnvironmentFileAtomically([string]$RelativePath, [string[]]$SettingNames) {
    if ($RelativePath -notin @('server/.env', 'client/.env')) { throw 'Environment sync target is outside the supported project files.' }
    $resolvedProjectRoot = [System.IO.Path]::GetFullPath($projectRoot).TrimEnd([char[]]@('\', '/'))
    $targetPath = [System.IO.Path]::GetFullPath((Join-Path $resolvedProjectRoot $RelativePath))
    if (-not $targetPath.StartsWith($resolvedProjectRoot + [System.IO.Path]::DirectorySeparatorChar,
        [System.StringComparison]::OrdinalIgnoreCase)) { throw 'Environment sync target is outside the project directory.' }
    $syncIdentifier = [guid]::NewGuid().ToString('N')
    $temporaryPath = "$targetPath.$syncIdentifier.tmp.local"
    $backupPath = "$targetPath.$syncIdentifier.local"
    $replacementSucceeded = $false
    $lines = [System.Collections.Generic.List[string]]::new()
    $lines.Add('# Generated from root .env by scripts/dev.ps1 -Mode Sync. Update root .env and sync again.')
    foreach ($settingName in $SettingNames) {
        $settingValue = [Environment]::GetEnvironmentVariable($settingName, 'Process')
        if ($null -eq $settingValue) { $settingValue = '' }
        if ($settingValue.IndexOfAny([char[]]@([char]0, [char]10, [char]13)) -ge 0) {
            throw "Cannot sync multiline or null characters in $settingName."
        }
        if (-not $settingValue.Contains("'")) {
            $encodedValue = "'$settingValue'"
        } else {
            $encodedValue = '"' + $settingValue.Replace('\', '\\').Replace('"', '\"') + '"'
        }
        $lines.Add("$settingName=$encodedValue")
    }
    try {
        [System.IO.File]::WriteAllLines($temporaryPath, $lines, [System.Text.UTF8Encoding]::new($false))
        if (Test-Path -LiteralPath $targetPath -PathType Leaf) {
            # PowerShell 5 binds a null string as an empty backup path. Use a real,
            # unique backup in the same project directory to retain atomic replacement.
            [System.IO.File]::Replace($temporaryPath, $targetPath, $backupPath)
        } else { [System.IO.File]::Move($temporaryPath, $targetPath) }
        $replacementSucceeded = $true
    } finally {
        if ([System.IO.File]::Exists($temporaryPath)) { [System.IO.File]::Delete($temporaryPath) }
        if ($replacementSucceeded -and [System.IO.File]::Exists($backupPath)) { [System.IO.File]::Delete($backupPath) }
    }
}

function Sync-LocalEnvironmentFiles {
    $serverSettingNames = @('SERVER_PORT', 'SPRING_PROFILES_ACTIVE', 'SQLSERVER_URL', 'SQLSERVER_USERNAME',
        'SQLSERVER_PASSWORD', 'MONGODB_URI', 'ALLOWED_ORIGINS', 'JWT_SECRET', 'AUTH_COOKIE_SECURE',
        'SMTP_HOST', 'SMTP_PORT', 'SMTP_USERNAME', 'SMTP_PASSWORD', 'SMTP_AUTH', 'SMTP_STARTTLS',
        'MAIL_FROM', 'MAIL_FROM_NAME', 'MAIL_TEMPLATE_CACHE')
    Write-EnvironmentFileAtomically 'server/.env' $serverSettingNames
    Write-EnvironmentFileAtomically 'client/.env' @('VITE_API_URL', 'VITE_DEV_PORT')
    Write-Host 'Synced server/.env and client/.env from root .env.'
}

Push-Location $projectRoot
try {
    $rootSettings = Read-LiteralEnvironment $rootEnvironmentPath
    foreach ($settingName in $rootSettings.Keys) { Set-ProcessSetting $settingName $rootSettings[$settingName] }
    $frontendPort = Get-PortSetting $rootSettings 'FRONTEND_PORT' 15173
    $backendPort = Get-PortSetting $rootSettings 'BACKEND_PORT' 18080
    $sqlPort = Get-PortSetting $rootSettings 'SQLSERVER_PORT' 31433
    $mongoPort = Get-PortSetting $rootSettings 'MONGO_PORT' 37017
    foreach ($portSetting in @(@('FRONTEND_PORT', $frontendPort), @('BACKEND_PORT', $backendPort),
        @('SQLSERVER_PORT', $sqlPort), @('MONGO_PORT', $mongoPort))) {
        Set-ProcessSetting $portSetting[0] ([string]$portSetting[1])
    }
    if ([string]::IsNullOrWhiteSpace($env:SQLSERVER_PASSWORD)) { throw 'Set SQLSERVER_PASSWORD in root .env.' }
    if ([string]::IsNullOrWhiteSpace($env:JWT_SECRET)) { throw 'Set JWT_SECRET in root .env to keep local authentication stable.' }
    try { $jwtBytes = [Convert]::FromBase64String($env:JWT_SECRET) }
    catch { throw 'JWT_SECRET in root .env must be Base64.' }
    if ($jwtBytes.Length -lt 32) { throw 'JWT_SECRET in root .env must contain at least 32 random bytes.' }
    Set-ProcessSetting 'SERVER_PORT' ([string]$backendPort)
    Set-ProcessSetting 'SPRING_PROFILES_ACTIVE' (Get-Setting $rootSettings 'BACKEND_PROFILES' 'dev,demo')
    Set-ProcessSetting 'SQLSERVER_URL' "jdbc:sqlserver://localhost:$sqlPort;databaseName=group6_project_db;encrypt=true;trustServerCertificate=true"
    Set-ProcessSetting 'SQLSERVER_USERNAME' 'sa'
    Set-ProcessSetting 'MONGODB_URI' "mongodb://localhost:$mongoPort/group6_project_db"
    Set-ProcessSetting 'ALLOWED_ORIGINS' (Get-Setting $rootSettings 'ALLOWED_ORIGINS' "http://localhost:$frontendPort,http://127.0.0.1:$frontendPort")
    Set-ProcessSetting 'AUTH_COOKIE_SECURE' (Get-Setting $rootSettings 'AUTH_COOKIE_SECURE' 'false')
    Set-ProcessSetting 'MAIL_TEMPLATE_CACHE' (Get-Setting $rootSettings 'MAIL_TEMPLATE_CACHE' 'false')
    Set-ProcessSetting 'VITE_API_URL' "http://localhost:$backendPort/api/v1"
    Set-ProcessSetting 'VITE_DEV_PORT' ([string]$frontendPort)
    if ($Mode -eq 'Sync') { Sync-LocalEnvironmentFiles; return }
    if ($Mode -eq 'Docker') {
        Assert-Docker
        & $dockerExecutable compose --env-file $rootEnvironmentPath up -d --wait --build
        $composeDockerExitCode = $LASTEXITCODE
        Require-Success 'Build and start the Docker stack' $composeDockerExitCode
        return
    }
    if ($Mode -eq 'Infra') { Start-Infrastructure; return }
    Initialize-LocalToolchain
    if ($Mode -eq 'Check') {
        Write-Host "Java: $($javaTool.VersionLines[0])"
        Write-Host "Node.js: $($nodeTool.VersionLines[0])"
        Write-Host "Maven: $($mavenTool.VersionLines[0])"
        Invoke-Npm @('--version') 'Check npm'
        Assert-Docker
        & $dockerExecutable compose --env-file $rootEnvironmentPath ps -a sqlserver sqlserver-init mongodb
        $composeStatusExitCode = $LASTEXITCODE
        Require-Success 'Check project database containers' $composeStatusExitCode
        foreach ($serviceName in @('sqlserver', 'sqlserver-init', 'mongodb')) {
            $serviceState = Get-ServiceState $serviceName
            $serviceStatus = if (-not $serviceState) { 'not started' }
                elseif ($serviceName -eq 'sqlserver-init') { "$($serviceState.Status), exit $($serviceState.ExitCode)" }
                elseif ($serviceState.Health) { "$($serviceState.Status), $($serviceState.Health.Status)" }
                else { $serviceState.Status }
            Write-Host "$serviceName`: $serviceStatus"
        }
        Show-HttpHealth 'API health' "http://localhost:$backendPort/api/v1/health"
        Show-HttpHealth 'Actuator health (SQL, Mongo, SMTP)' "http://localhost:$backendPort/actuator/health"
        return
    }
    if ($Mode -in @('Dev', 'Server')) { Start-Infrastructure }
    if (-not (Test-Path -LiteralPath (Join-Path $projectRoot 'node_modules') -PathType Container) -or
        -not (Test-Path -LiteralPath (Join-Path $projectRoot 'client/node_modules') -PathType Container)) {
        Invoke-Npm @('run', 'install:all') 'Install locked npm dependencies'
    }
    if ($Mode -eq 'Client') {
        Invoke-Npm @('run', 'dev', '--prefix', 'client', '--', '--port', [string]$frontendPort, '--strictPort') 'Run frontend'
    } else {
        $npmScript = if ($Mode -eq 'Server') { 'dev:server' } else { 'dev' }
        Write-Host "Starting ${Mode}: API localhost:$backendPort, frontend localhost:$frontendPort. Press Ctrl+C to stop."
        Invoke-Npm @('run', $npmScript) "Run $Mode"
    }
} finally {
    foreach ($settingName in $previousEnvironment.Keys) {
        [Environment]::SetEnvironmentVariable($settingName, $previousEnvironment[$settingName], 'Process')
    }
    Pop-Location
}

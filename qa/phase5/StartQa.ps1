$ErrorActionPreference = 'Stop'
$backendDir = (Resolve-Path (Join-Path $PSScriptRoot '../..')).Path
$mobileDir = (Resolve-Path (Join-Path $backendDir '../gymvip-mobile')).Path
$pidFile = Join-Path $backendDir 'target/qa-server.pid'
if (Test-Path -LiteralPath $pidFile) {
    $qaProcessId = [int]([IO.File]::ReadAllText($pidFile).Trim())
    $previous = Get-CimInstance Win32_Process -Filter "ProcessId = $qaProcessId" -ErrorAction SilentlyContinue
    if ($previous) {
        if ($previous.CommandLine -notlike '*QaBackend.java*') { throw 'El PID guardado no pertenece a QaBackend.' }
        Stop-Process -Id $qaProcessId
    }
}
$db = (docker inspect gymvip-sprint1-jorge-db | ConvertFrom-Json)[0]
if ($LASTEXITCODE -ne 0) { throw 'No se encontró PostgreSQL local.' }
$cpPath = Join-Path $backendDir 'target/qa-classpath.txt'
if (!(Test-Path -LiteralPath $cpPath)) { throw 'Ejecuta dependency:build-classpath con includeScope=test primero.' }
$env:TEST_DB_JDBC_URL = 'jdbc:postgresql://127.0.0.1:15432/gymvip_checks_jorge'
$env:TEST_DB_SCHEMA = 'gymvip_test_jorge'
$env:TEST_DB_USERNAME = ($db.Config.Env | Where-Object { $_.StartsWith('POSTGRES_USER=') }) -replace '^POSTGRES_USER=', ''
$env:TEST_DB_PASSWORD = ($db.Config.Env | Where-Object { $_.StartsWith('POSTGRES_PASSWORD=') }) -replace '^POSTGRES_PASSWORD=', ''
try {
    $classpath = (Join-Path $backendDir 'target/classes') + ';' + [IO.File]::ReadAllText($cpPath).Trim()
    $source = Join-Path $PSScriptRoot 'QaBackend.java'
    $data = Join-Path $mobileDir 'qa/phase5/capturas/datos-qa.json'
    New-Item -ItemType Directory -Path (Split-Path $data) -Force | Out-Null
    $process = Start-Process -FilePath (Join-Path $env:JAVA_HOME 'bin/java.exe') -WorkingDirectory $backendDir `
        -ArgumentList @('-cp', ('"' + $classpath + '"'), ('"' + $source + '"'), ('"' + $data + '"')) `
        -WindowStyle Hidden -PassThru -RedirectStandardOutput (Join-Path $mobileDir 'phase5-qa-server.log') `
        -RedirectStandardError (Join-Path $mobileDir 'phase5-qa-server-error.log')
    [IO.File]::WriteAllText($pidFile, $process.Id.ToString())
    Write-Output "Backend QA iniciado: PID $($process.Id), puerto 8082, base aislada."
} finally {
    Remove-Item Env:TEST_DB_JDBC_URL, Env:TEST_DB_SCHEMA, Env:TEST_DB_USERNAME, Env:TEST_DB_PASSWORD -ErrorAction SilentlyContinue
}

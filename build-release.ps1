$ErrorActionPreference = "Stop"

$SCRIPT_DIR = Split-Path -Parent $MyInvocation.MyCommand.Path
Set-Location $SCRIPT_DIR

# Tool paths
$JAVA_HOME = "C:\Users\somh\.vscode\extensions\redhat.java-1.56.0-win32-x64\jre\21.0.12.1-win32-x86_64"
$GRADLE_BAT = "C:\Users\somh\.gradle\wrapper\dists\gradle-8.9-bin\90cnw93cvbtalezasaz0blq0a\gradle-8.9\bin\gradle.bat"
$ANDROID_SDK = "C:\Users\somh\AppData\Local\Android\Sdk"

$env:JAVA_HOME = $JAVA_HOME
$env:ANDROID_HOME = $ANDROID_SDK
$env:Path = "$JAVA_HOME\bin;$env:Path"

Write-Host "==========================================" -ForegroundColor Cyan
Write-Host "   Building ADB TV Remote App (Release APK)" -ForegroundColor Cyan
Write-Host "==========================================" -ForegroundColor Cyan

if (Test-Path $GRADLE_BAT) {
    & $GRADLE_BAT assembleRelease --no-daemon
} else {
    gradle assembleRelease --no-daemon
}

if ($LASTEXITCODE -eq 0) {
    $apk = Get-ChildItem -Path "$SCRIPT_DIR\app\build\outputs\apk\release\*.apk" -ErrorAction SilentlyContinue | Select-Object -First 1
    if ($apk) {
        $sizeMb = [math]::Round($apk.Length / 1MB, 2)
        Write-Host "`n[SUCCESS] Signed Release APK created at: $($apk.FullName) ($sizeMb MB)" -ForegroundColor Green
    }
}

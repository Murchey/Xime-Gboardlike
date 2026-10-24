@echo off
setlocal EnableExtensions
cd /d "%~dp0"

echo ============================================
echo  Xime Gboard - Build signed APKs by ABI
echo ============================================
echo.

if not exist "gradlew.bat" (
    echo [ERROR] gradlew.bat not found. Run this script from the repository root.
    if not defined CI pause
    exit /b 1
)

if not exist "local.properties" if not defined ANDROID_HOME if not defined ANDROID_SDK_ROOT (
    echo [ERROR] Android SDK not configured.
    echo         Create local.properties with sdk.dir=... or set ANDROID_HOME.
    if not defined CI pause
    exit /b 1
)

if not exist "app\keystore.properties" (
    echo [WARN] app\keystore.properties not found; release APKs may be unsigned.
)
if not exist "app\xime-gboard.jks" (
    echo [WARN] app\xime-gboard.jks not found; release APKs may be unsigned.
)

echo [1/2] Building release APKs...
call gradlew.bat assembleRelease --console=plain
if errorlevel 1 (
    echo.
    echo [ERROR] Build failed. See the Gradle output above.
    if not defined CI pause
    exit /b 1
)

set "OUT_DIR=dist"
if exist "%OUT_DIR%" rmdir /s /q "%OUT_DIR%"
mkdir "%OUT_DIR%"

set "FOUND=0"
for %%F in ("app\build\outputs\apk\release\*.apk") do (
    if exist "%%~fF" (
        copy /y "%%~fF" "%OUT_DIR%" >nul
        set "FOUND=1"
    )
)

if "%FOUND%"=="0" (
    echo [ERROR] No release APK was produced.
    if not defined CI pause
    exit /b 1
)

echo.
echo [2/2] Done. APKs in %CD%\%OUT_DIR%:
for %%F in ("%OUT_DIR%\*.apk") do echo   - %%~nxF
echo.
echo Install the APK matching your device ABI:
echo   arm64-v8a  - most modern phones
echo   armeabi-v7a - older 32-bit ARM phones
echo   x86_64 / x86 - emulators and x86 tablets
echo   universal  - works everywhere, larger
echo.
echo Note: these APKs use the public community certificate in app\keystore.properties.
echo       Installable locally, but not suitable for app-store publishing.
echo.
if not defined CI pause
endlocal

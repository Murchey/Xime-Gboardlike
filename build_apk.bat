@echo off
chcp 65001 >nul
setlocal EnableExtensions
cd /d "%~dp0"

echo ============================================
echo  XimeGboard - Build signed release APKs by ABI
echo    dist\ : 按 ABI 分开的 release APK（4 个架构）
echo            内置语法模型 gram；联想 ONNX 模型不随包
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

if exist "dist" rmdir /s /q "dist"
mkdir "dist"

rem ---------------------------------------------------------------
rem 联想 ONNX 模型不再打进 APK，只构建一次（不再有 full / lite 两套包）。
rem 语法模型 assets/gram/XimeGboard.gram 仍在 main assets 里，随包分发。
rem ---------------------------------------------------------------
echo [1/3] Building release APKs (no bundled predictive model)...
call :build_release RELEASE
if errorlevel 1 exit /b 1
call :collect
if errorlevel 1 exit /b 1

echo.
echo [2/3] Done. APKs:
for %%F in ("dist\*.apk") do echo     - %%~nxF

echo.
echo [3/3] Which file to install:
echo   arm64-v8a  - most modern phones
echo   armeabi-v7a - older 32-bit ARM phones
echo   x86_64 / x86 - emulators and x86 tablets
echo   universal 已取消：四个 ABI 已覆盖真机与模拟器
echo.
echo Note: 联想模型不再随包；装好后在 设置 -^> 智能 -^> 智能联想 里下载或导入模型。
echo       语法模型（gram）已内置，安装即生效。
echo.
echo Note: these APKs use the public community certificate in app\keystore.properties.
echo       Installable locally, but not suitable for app-store publishing.
echo.
if not defined CI pause
endlocal
exit /b 0

rem ---------------------------------------------------------------
rem :build_release <标签>
rem   AGP 的增量打包(state 卡住)偶发失败：清理 release 的打包中间产物后重试一次。
rem ---------------------------------------------------------------
:build_release
call gradlew.bat assembleRelease --console=plain
if not errorlevel 1 exit /b 0
echo.
echo [WARN] %~1 首次打包失败，清理增量产物后重试...
if exist "app\build\intermediates\apk\release" rmdir /s /q "app\build\intermediates\apk\release"
if exist "app\build\outputs\apk\release" rmdir /s /q "app\build\outputs\apk\release"
call gradlew.bat assembleRelease --console=plain
if errorlevel 1 (
    echo.
    echo [ERROR] %~1 build failed. See the Gradle output above.
    if not defined CI pause
    exit /b 1
)
exit /b 0

rem ---------------------------------------------------------------
rem :collect - 把 release 产物拷进 dist\，保留旧版 Xime- 前缀兼容
rem ---------------------------------------------------------------
:collect
set "FOUND=0"
for %%F in ("app\build\outputs\apk\release\*.apk") do (
    if exist "%%~fF" (
        set "SRC=%%~fF"
        set "NAME=%%~nxF"
        setlocal EnableDelayedExpansion
        rem Gradle outputs XimeGboard-<version>-<abi>.apk; keep legacy Xime- prefix compatibility
        if /i "!NAME:~0,5!"=="Xime-" (
            set "NEWNAME=XimeGboardlike-!NAME:~5!"
        ) else (
            set "NEWNAME=!NAME!"
        )
        copy /y "!SRC!" "dist\!NEWNAME!" >nul
        endlocal
        set "FOUND=1"
    )
)
if "%FOUND%"=="0" (
    echo [ERROR] No release APK was produced.
    exit /b 1
)
exit /b 0

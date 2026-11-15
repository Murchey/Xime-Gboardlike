@echo off
chcp 65001 >nul
setlocal EnableExtensions
cd /d "%~dp0"

echo ============================================
echo  XimeGboard - Build signed APKs by ABI
echo    dist\full : 内置联想模型（开箱即用，体积大）
echo    dist\lite : 不含模型权重，用户自己装
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
mkdir "dist\full"
mkdir "dist\lite"

rem ---------------------------------------------------------------
rem full：打进 app\src\modelAssets 下的联想模型（-PbundleModel=true）
rem ---------------------------------------------------------------
echo [1/4] Building FULL release APKs (bundled predictive model)...
call :build_release true FULL
if errorlevel 1 exit /b 1
call :collect full
if errorlevel 1 exit /b 1

rem ---------------------------------------------------------------
rem lite：不带模型权重（-PbundleModel=false），只保留模型架构与导入入口
rem 两次构建的资产集合不同，Gradle 会重新打包（不会命中 up-to-date）
rem ---------------------------------------------------------------
echo.
echo [2/4] Building LITE release APKs (no bundled model)...
call :build_release false LITE
if errorlevel 1 exit /b 1
call :collect lite
if errorlevel 1 exit /b 1

echo.
echo [3/4] Done. APKs:
for %%D in (full lite) do (
    echo   dist\%%D\
    for %%F in ("dist\%%D\*.apk") do echo     - %%~nxF
)

echo.
echo [4/4] Which file to install:
echo   dist\full - 内置模型，开箱即有联想
echo   dist\lite - 不含模型，安装后在「设置 -^> 智能 -^> 智能联想」里导入自己的模型
echo.
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
exit /b 0

rem ---------------------------------------------------------------
rem :build_release <true^|false> <标签>
rem   AGP 的增量打包(state 卡住)偶发失败：清理 release 的打包中间产物后重试一次。
rem ---------------------------------------------------------------
:build_release
call gradlew.bat assembleRelease -PbundleModel=%~1 --console=plain
if not errorlevel 1 exit /b 0
echo.
echo [WARN] %~2 首次打包失败，清理增量产物后重试...
if exist "app\build\intermediates\apk\release" rmdir /s /q "app\build\intermediates\apk\release"
if exist "app\build\outputs\apk\release" rmdir /s /q "app\build\outputs\apk\release"
call gradlew.bat assembleRelease -PbundleModel=%~1 --console=plain
if errorlevel 1 (
    echo.
    echo [ERROR] %~2 build failed. See the Gradle output above.
    if not defined CI pause
    exit /b 1
)
exit /b 0

rem ---------------------------------------------------------------
rem :collect <full^|lite>  - 把 release 产物拷进 dist\<flavor>，保留旧命名规则
rem ---------------------------------------------------------------
:collect
set "FLAVOR=%~1"
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
        copy /y "!SRC!" "dist\%FLAVOR%\!NEWNAME!" >nul
        endlocal
        set "FOUND=1"
    )
)
if "%FOUND%"=="0" (
    echo [ERROR] No release APK was produced for %FLAVOR%.
    exit /b 1
)
exit /b 0
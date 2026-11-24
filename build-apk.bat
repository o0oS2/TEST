@echo off
setlocal
cd /d "%~dp0"
echo === Build APK Auto Mes OQC - Xem du lieu ===

rem 1) Android SDK
set "SDK=%ANDROID_HOME%"
if "%SDK%"=="" set "SDK=%ANDROID_SDK_ROOT%"
if "%SDK%"=="" if exist "%LOCALAPPDATA%\Android\Sdk" set "SDK=%LOCALAPPDATA%\Android\Sdk"
if "%SDK%"=="" goto no_sdk
set "SDK_FWD=%SDK:\=/%"
> local.properties echo sdk.dir=%SDK_FWD%

rem 2) Gradle (gradlew neu co, khong thi gradle trong PATH)
set "G=gradle"
if exist "gradlew.bat" set "G=gradlew.bat"
where %G% >nul 2>nul
if errorlevel 1 goto no_gradle

call %G% --no-daemon assembleRelease
if errorlevel 1 goto fail

if not exist "dist" mkdir "dist"
copy /y "app\build\outputs\apk\release\app-release.apk" "dist\AutoMesOQC_Xem.apk" >nul
echo.
echo Xong: dist\AutoMesOQC_Xem.apk  - chep sang dien thoai de cai.
pause
exit /b 0

:no_sdk
echo Khong tim thay Android SDK. Cai Android Studio (se tu cai SDK) hoac dat bien ANDROID_HOME.
pause
exit /b 1

:no_gradle
echo Khong tim thay Gradle. Cach de nhat: mo thu muc nay bang Android Studio -^> Build -^> Build APK(s).
pause
exit /b 1

:fail
echo Build LOI. Xem thong bao o tren.
pause
exit /b 1

@echo off
setlocal

REM Fabiodalez Music — Android Studio preparation launcher for Windows
REM Run this from the Monochrome web app folder after installing the wrapper.

where bash >nul 2>nul
if errorlevel 1 (
    echo Git Bash was not found in PATH.
    echo Install Git for Windows, then try again.
    pause
    exit /b 1
)

bash build-android-windows.sh --android-studio

if errorlevel 1 (
    echo.
    echo Android Studio preparation failed.
    pause
    exit /b 1
)

echo.
echo Android Studio project prepared.
echo Open this folder in Android Studio:
echo %CD%\android
echo.
pause

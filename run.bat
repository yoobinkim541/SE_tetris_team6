@echo off
setlocal
cd /d "%~dp0"

REM 빌드된 독립 실행 EXE가 있으면 바로 실행하고, 없으면 Gradle run으로 실행합니다.
if exist "build\jpackage\image-v2\SE Tetris\SE Tetris.exe" (
    start "" "build\jpackage\image-v2\SE Tetris\SE Tetris.exe"
    exit /b 0
)

call .\gradlew.bat run

@echo off
setlocal EnableExtensions EnableDelayedExpansion
set "SOURCE=%~dp0\..\.."
set "DEST=%~1"
if "%DEST%"=="" (
  echo Usage: copy-to-spring-project.cmd C:\path\to\existing-spring-project
  exit /b 2
)
if not exist "%DEST%" mkdir "%DEST%" || exit /b 3
for %%I in ("%DEST%") do set "DEST=%%~fI"
for %%I in ("%SOURCE%") do set "SOURCE=%%~fI"
if /I "%SOURCE%"=="%DEST%" (
  echo ERROR: The extracted package and existing Spring project must be different folders.
  exit /b 4
)
if not exist "%SOURCE%\java\service\RagSqlAgentService.java" (
  echo ERROR: This is not the complete HiddenAuto package root.
  exit /b 5
)
set "STAMP="
for /f %%I in ('powershell -NoProfile -Command "Get-Date -Format yyyyMMdd_HHmmss" 2^>nul') do set "STAMP=%%I"
if "%STAMP%"=="" set "STAMP=%RANDOM%_%RANDOM%"
set "BACKUP=%DEST%\.hiddenauto-replace-backup\%STAMP%"
mkdir "%BACKUP%" >nul 2>nul

call :copydir "%SOURCE%\java" "%DEST%\src\main\java\com\dev\HiddenBATHAuto\rag" "src\main\java\com\dev\HiddenBATHAuto\rag" || exit /b 10
call :copydir "%SOURCE%\static" "%DEST%\src\main\resources\static" "src\main\resources\static" || exit /b 11
call :copydir "%SOURCE%\templates" "%DEST%\src\main\resources\templates" "src\main\resources\templates" || exit /b 12
call :copydir "%SOURCE%\db" "%DEST%\db" "db" || exit /b 13
call :copydir "%SOURCE%\scripts" "%DEST%\scripts" "scripts" || exit /b 14
call :copydir "%SOURCE%\docs" "%DEST%\docs" "docs" || exit /b 15

for %%F in (docker-compose.yml docker-compose.local.yml docker-compose.server-dev.yml docker-compose.server-prod.yml .env.example .env.local.example .env.server-dev.example .env.server-prod.example .gitignore README.md APPLY_FIRST_KO.txt COPY_TARGET_MAP_KO.txt CHANGED_FILES_KO.txt PACKAGE_CONTENTS.txt VALIDATION_SUMMARY.txt MANIFEST.sha256) do (
  if exist "%SOURCE%\%%F" (
    if exist "%DEST%\%%F" copy /Y "%DEST%\%%F" "%BACKUP%\%%F" >nul || exit /b 16
    copy /Y "%SOURCE%\%%F" "%DEST%\%%F" >nul || exit /b 17
  )
)

echo Complete package mapped into: %DEST%
echo Existing files backup: %BACKUP%
echo The actual .env was not copied. Check git status and git diff.
exit /b 0

:copydir
set "SRC=%~1"
set "DST=%~2"
set "REL=%~3"
if not exist "%SRC%" exit /b 0
if exist "%DST%" (
  robocopy "%DST%" "%BACKUP%\%REL%" /E /COPY:DAT /DCOPY:DAT /R:2 /W:1 /NFL /NDL /NP >nul
  if errorlevel 8 exit /b 1
)
robocopy "%SRC%" "%DST%" /E /COPY:DAT /DCOPY:DAT /R:2 /W:1 /NFL /NDL /NP >nul
if errorlevel 8 exit /b 1
exit /b 0

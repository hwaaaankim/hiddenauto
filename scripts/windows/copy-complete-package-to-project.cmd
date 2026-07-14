@echo off
setlocal EnableExtensions EnableDelayedExpansion
set "SOURCE=%~dp0\..\.."
set "DEST=%~1"
if "%DEST%"=="" (
  echo Usage: copy-complete-package-to-project.cmd C:\path\to\existing-project
  exit /b 2
)
if not exist "%DEST%" mkdir "%DEST%" || exit /b 3
for %%I in ("%DEST%") do set "DEST=%%~fI"
for %%I in ("%SOURCE%") do set "SOURCE=%%~fI"
if /I "%SOURCE%"=="%DEST%" (
  echo Already running from the destination root: %DEST%
  exit /b 0
)
set "STAMP="
for /f %%I in ('powershell -NoProfile -Command "Get-Date -Format yyyyMMdd_HHmmss" 2^>nul') do set "STAMP=%%I"
if "%STAMP%"=="" set "STAMP=%RANDOM%_%RANDOM%"
set "BACKUP=%DEST%\.hiddenauto-replace-backup\%STAMP%"
mkdir "%BACKUP%" >nul 2>nul

for %%D in (java static templates db scripts docs) do (
  if exist "%SOURCE%\%%D" (
    if exist "%DEST%\%%D" (
      echo Backing up %%D\ ...
      robocopy "%DEST%\%%D" "%BACKUP%\%%D" /E /COPY:DAT /DCOPY:DAT /R:2 /W:1 /NFL /NDL /NP >nul
      if errorlevel 8 exit /b 10
    )
    echo Copying %%D\ ...
    robocopy "%SOURCE%\%%D" "%DEST%\%%D" /E /COPY:DAT /DCOPY:DAT /R:2 /W:1 /NFL /NDL /NP >nul
    if errorlevel 8 exit /b 11
  )
)
for %%F in (docker-compose.yml docker-compose.local.yml docker-compose.server-dev.yml docker-compose.server-prod.yml .env.example .env.local.example .env.server-dev.example .env.server-prod.example .gitignore README.md APPLY_FIRST_KO.txt COPY_TARGET_MAP_KO.txt CHANGED_FILES_KO.txt PACKAGE_CONTENTS.txt VALIDATION_SUMMARY.txt MANIFEST.sha256) do (
  if exist "%SOURCE%\%%F" (
    if exist "%DEST%\%%F" (
      if not exist "%BACKUP%" mkdir "%BACKUP%" >nul 2>nul
      copy /Y "%DEST%\%%F" "%BACKUP%\%%F" >nul || exit /b 12
    )
    copy /Y "%SOURCE%\%%F" "%DEST%\%%F" >nul || exit /b 13
  )
)
echo Complete package copied to %DEST%
echo Existing files backup: %BACKUP%
echo The actual .env was not copied. Destination-only files were not deleted. Check git status and git diff.
exit /b 0

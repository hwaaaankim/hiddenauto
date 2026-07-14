@echo off
setlocal EnableExtensions
set "ROOT=%~dp0\..\.."
if "%PGHOST%"=="" set "PGHOST=127.0.0.1"
if "%PGPORT%"=="" set "PGPORT=15433"
if "%PGUSER%"=="" set "PGUSER=ax_admin"
if "%PGDATABASE%"=="" set "PGDATABASE=ax_rag_dev"
if "%PGPASSWORD%"=="" (echo ERROR: PGPASSWORD is required.& exit /b 2)
where pg_dump >nul 2>nul || (echo ERROR: pg_dump.exe is not on PATH.& exit /b 3)
if not exist "%ROOT%\backups" mkdir "%ROOT%\backups" || exit /b 4
set "STAMP="
for /f %%I in ('powershell -NoProfile -Command "Get-Date -Format yyyyMMdd_HHmmss" 2^>nul') do set "STAMP=%%I"
if "%STAMP%"=="" set "STAMP=%RANDOM%_%RANDOM%"
set "OUT=%ROOT%\backups\%PGDATABASE%_%STAMP%.dump"
echo Creating backup: %OUT%
pg_dump -Fc -h "%PGHOST%" -p "%PGPORT%" -U "%PGUSER%" -d "%PGDATABASE%" -f "%OUT%" || exit /b 10
for %%F in ("%OUT%") do if %%~zF LEQ 0 (echo ERROR: Backup file is empty.& exit /b 11)
echo Backup completed: %OUT%
exit /b 0

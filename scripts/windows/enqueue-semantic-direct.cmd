@echo off
setlocal EnableExtensions EnableDelayedExpansion
set "TARGET=%~1"
set "ROOT=%~dp0\..\.."
if "%TARGET%"=="" (echo Usage: enqueue-semantic-direct.cmd local^|dev^|prod& exit /b 64)
if "%PGHOST%"=="" set "PGHOST=127.0.0.1"
if "%PGUSER%"=="" set "PGUSER=ax_admin"
if "%PGPASSWORD%"=="" (echo ERROR: PGPASSWORD is required.& exit /b 2)
if "%PGPORT%"=="" (echo ERROR: PGPORT is required.& exit /b 2)
if "%PGDATABASE%"=="" (echo ERROR: PGDATABASE is required.& exit /b 2)
where psql >nul 2>nul || (echo ERROR: psql.exe is not on PATH.& exit /b 3)
if /I "%TARGET%"=="prod" (
  set /p "ANSWER=PROD semantic rebuild queue registration. Type ENQUEUE-PROD exactly: "
  if not "!ANSWER!"=="ENQUEUE-PROD" (echo Cancelled.& exit /b 4)
)
psql -X -v ON_ERROR_STOP=1 -h "%PGHOST%" -p "%PGPORT%" -U "%PGUSER%" -d "%PGDATABASE%" -f "%ROOT%\db\enqueue_semantic_rebuild_all.sql" || exit /b 10
echo %TARGET% semantic queue registration completed.
exit /b 0
